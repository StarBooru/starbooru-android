package eu.filipnone.starbooru.data.upload

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.StarBooruApp
import eu.filipnone.starbooru.core.BadTag
import eu.filipnone.starbooru.core.Rating
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the Upload page says once, as a notice. */
sealed interface UploadNote {
    /** Files left out, as they aren't images or videos. */
    data class Skipped(val names: List<String>) : UploadNote

    data object NothingToPaste : UploadNote

    /** Everything sent has finished. */
    data class Finished(val tally: Tally) : UploadNote
}

/** Files being copied in: [done] of [total]. */
data class Adding(val done: Int, val total: Int)

/**
 * The upload list for the whole app: the Upload page shows it and
 * [UploadWorker] sends it. It's kept in [dir] with a copy of each file, so
 * it's still there after the app closes.
 */
class UploadQueue internal constructor(
    private val context: Context,
    /** The copies, each named by its item's id, and the list itself. */
    val dir: File,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    /** The server's upload limit in bytes, 0 when unknown. */
    internal val limit: () -> Long,
    /** Whether the server imports links. */
    internal val urlImport: () -> Boolean,
    /** Has the worker go through the list. */
    private val schedule: () -> Unit,
) {
    private val store = UploadStore(File(dir, LIST))
    private val _state: MutableStateFlow<UploadState>
    val state: StateFlow<UploadState>

    private val _adding = MutableStateFlow<Adding?>(null)

    /** Files being copied in, or `null`. */
    val adding: StateFlow<Adding?> = _adding.asStateFlow()

    private val _notes = MutableSharedFlow<UploadNote>(extraBufferCapacity = 8)

    /** Notices for whoever shows the page; dropped while nobody does. */
    val notes: SharedFlow<UploadNote> = _notes.asSharedFlow()

    /** Tells the worker what it sends may have changed: a cancel, a retry, a new batch. */
    internal val wake = Channel<Unit>(Channel.CONFLATED)

    private val intake = Mutex()

    init {
        val kept = store.load()
        // What was on its way when the app died goes again from the start.
        _state = MutableStateFlow((kept ?: UploadState()).requeue())
        state = _state.asStateFlow()
        if (kept != null) sweep(kept)
        scope.launch(io) {
            _state
                .map { it.forDisk() }
                .distinctUntilChanged()
                .conflate()
                .collect { store.save(it) }
        }
        if (_state.value.items.any { it.status == UploadStatus.Waiting }) schedule()
    }

    /** Where [id]'s copy is. */
    fun fileOf(id: String): File = File(dir, id)

    /** Copies left behind: those of items no longer on the list, and halves of copies. */
    private fun sweep(kept: UploadState) {
        val ids = kept.items.mapTo(HashSet()) { it.id }
        dir.listFiles()?.forEach { f ->
            if (f.name != LIST && f.name != "$LIST.tmp" && f.name !in ids) f.delete()
        }
    }

    private inline fun <R> change(block: (UploadState) -> Pair<UploadState, R>): R {
        while (true) {
            val before = _state.value
            val (after, result) = block(before)
            if (_state.compareAndSet(before, after)) return result
        }
    }

    private inline fun update(block: (UploadState) -> UploadState) = change { block(it) to Unit }

    private fun forget(items: List<UploadItem>) {
        val files = items.filter { it.isFile }
        if (files.isEmpty()) return
        scope.launch(io) { files.forEach { fileOf(it.id).delete() } }
    }

    /** Puts what came in on the list as drafts, and says which files were left out. */
    fun add(incoming: List<IncomingItem>) {
        val res = context.resources
        val folders = _state.value.folderTags
        val links = urlImport()
        val items =
            incoming.mapNotNull { got ->
                when (got) {
                    is IncomingItem.Copied -> {
                        val tag = if (folders) Accept.folderTag(got.path).orEmpty() else ""
                        UploadItem(got.id, UploadSource.Local(got.name, got.size, got.mime, got.path), tags = tag)
                    }

                    is IncomingItem.Link -> if (links) UploadItem(newId(), UploadSource.Link(got.url)) else null

                    is IncomingItem.TooBig ->
                        UploadItem(
                            newId(),
                            UploadSource.Local(got.name, got.size, got.mime, got.path),
                            status = UploadStatus.Failed(Words.tooBig(res, limit())),
                        )

                    is IncomingItem.Unreadable ->
                        UploadItem(
                            newId(),
                            UploadSource.Local(got.name, 0, "", ""),
                            status = UploadStatus.Failed(res.getString(R.string.upload_unreadable, got.reason)),
                        )

                    is IncomingItem.Skipped -> null
                }
            }
        forget(change { it.add(items) })
        val skipped = incoming.filterIsInstance<IncomingItem.Skipped>().map { it.name }
        if (skipped.isNotEmpty()) _notes.tryEmit(UploadNote.Skipped(skipped))
    }

    /**
     * Takes what another app shares through [intent], copying its files in
     * the background. Answers whether it was a share at all, so the caller
     * can open the Upload page.
     */
    fun receive(intent: Intent): Boolean {
        if (!Incoming.isShare(intent)) return false
        // Read now: the activity's intent may be replaced while the files copy.
        val copy = Intent(intent)
        take { progress -> Incoming.fromIntent(context, copy, dir, limit(), urlImport(), progress) }
        return true
    }

    /** Files from the photo picker or the file picker. */
    fun takeUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        take { progress -> Incoming.fromUris(context, uris, dir, limit(), progress) }
    }

    /** Every image and video in a picked folder. */
    fun takeTree(tree: Uri) = take { progress -> Incoming.fromTree(context, tree, dir, limit(), progress) }

    /** What the clipboard holds: files, pictures and links. */
    fun takeClip(clip: ClipData?) {
        if (clip == null) {
            _notes.tryEmit(UploadNote.NothingToPaste)
            return
        }
        take(nothing = UploadNote.NothingToPaste) { progress -> Incoming.fromClip(context, clip, dir, limit(), urlImport(), progress) }
    }

    /** The links in [text], as drafts. Says whether there was one. */
    fun addLinks(text: String): Boolean {
        val found = Accept.parseUrls(text)
        if (found.isEmpty()) return false
        add(found.map { IncomingItem.Link(it) })
        return true
    }

    private fun take(
        nothing: UploadNote? = null,
        read: (Incoming.Progress) -> List<IncomingItem>,
    ) {
        scope.launch(io) {
            intake.withLock {
                val got =
                    try {
                        read { done, total -> _adding.value = if (total > 1 && done < total) Adding(done, total) else null }
                    } finally {
                        _adding.value = null
                    }
                if (got.isEmpty() && nothing != null) _notes.tryEmit(nothing) else add(got)
            }
        }
    }

    fun setTags(
        id: String,
        tags: String,
    ) = update { it.setTags(id, tags) }

    fun setRating(
        id: String,
        rating: Rating?,
    ) = update { it.setRating(id, rating) }

    fun setBatch(batch: Batch) = update { it.copy(batch = batch) }

    fun setFolderTags(on: Boolean) = update { it.copy(folderTags = on) }

    fun remove(id: String) {
        val gone = change { it.remove(id) } ?: return
        forget(listOf(gone))
    }

    /** Empties the list, but for what is on its way. */
    fun clear() = forget(change { it.clear() })

    /** Takes off what went up or was there already. */
    fun clearFinished() = forget(change { it.clearFinished() })

    /**
     * Sends the drafts with the batch's tags, rating and source. Gives the
     * first word that can't be a tag instead, and sends nothing then.
     */
    fun start(): BadTag? {
        _state.value.problem()?.let { return it }
        val res = context.resources
        update { it.start(explain = { bad -> Words.badTag(res, bad) }) }
        go()
        return null
    }

    /**
     * Signing out: what's waiting or on its way stops, and the list and its
     * copies go, so nothing is sent as the next account.
     */
    fun signedOut() {
        _state.value.items.forEach { update { st -> st.cancel(it.id) } }
        wake.trySend(Unit)
        forget(change { it.clear() })
    }

    fun cancel(id: String) {
        update { it.cancel(id) }
        wake.trySend(Unit)
    }

    fun retry(id: String) {
        update { it.retry(id) }
        go()
    }

    private fun go() {
        schedule()
        wake.trySend(Unit)
    }

    internal fun claim(
        limit: Int,
        except: Set<String>,
    ): List<UploadItem> = change { it.claim(limit, except) }

    internal fun sent(
        id: String,
        attempt: Int,
        sent: Long,
        total: Long,
        speed: Long,
    ) = update { it.sent(id, attempt, sent, total, speed) }

    /** Ends attempt [attempt]; a finished file's copy goes. Says whether it still counted. */
    internal fun finish(
        id: String,
        attempt: Int,
        outcome: Outcome,
    ): Boolean {
        val counted = change { it.finish(id, attempt, outcome) }
        if (counted && (outcome is Outcome.Posted || outcome is Outcome.Duplicate)) {
            _state.value.get(id)?.let { forget(listOf(it)) }
        }
        wake.trySend(Unit)
        return counted
    }

    internal fun stopPending() = update { it.stopPending() }

    internal fun requeue() = update { it.requeue() }

    internal fun finished(tally: Tally) {
        if (!tally.empty) _notes.tryEmit(UploadNote.Finished(tally))
    }

    companion object {
        private const val LIST = "list.json"

        // It holds the application's context, which lives as long as the app.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var made: UploadQueue? = null

        /** The app's upload list. */
        fun get(context: Context): UploadQueue =
            made ?: synchronized(this) {
                made ?: create(context.applicationContext).also { made = it }
            }

        private fun create(app: Context): UploadQueue {
            val account = (app as? StarBooruApp)?.graph?.account
            return UploadQueue(
                context = app,
                dir = File(app.filesDir, "uploads"),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                io = Dispatchers.IO,
                limit = { account?.info?.value?.maxUploadBytes?.let { limitOf(it) } ?: 0L },
                urlImport = { account?.info?.value?.urlImport ?: true },
                schedule = { UploadWorker.schedule(app) },
            )
        }

        internal fun limitOf(bytes: ULong): Long = bytes.coerceAtMost(Long.MAX_VALUE.toULong()).toLong()

        internal fun newId(): String = UUID.randomUUID().toString()
    }
}
