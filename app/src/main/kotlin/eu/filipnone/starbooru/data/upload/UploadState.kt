package eu.filipnone.starbooru.data.upload

import eu.filipnone.starbooru.core.BadTag
import eu.filipnone.starbooru.core.Rating
import eu.filipnone.starbooru.core.TagCheck
import eu.filipnone.starbooru.core.checkTags
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** How many files go at once, as on the web. */
const val AT_ONCE = 2

/** How many times a file is sent again by itself after the server couldn't be reached. */
const val TRIES = 4

/** A rating on disk, by name; one this version doesn't know reads as safe. */
object RatingAsName : KSerializer<Rating> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Rating", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Rating,
    ) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): Rating {
        val name = decoder.decodeString()
        return Rating.entries.firstOrNull { it.name == name } ?: Rating.SAFE
    }
}

@Serializable
sealed interface UploadSource {
    /**
     * A file on the phone, copied into the queue's folder when it was added.
     * [path] is where it was in a picked folder (`Summer 2024/a.jpg`), empty
     * for a file picked alone.
     */
    @Serializable
    @SerialName("file")
    data class Local(val name: String, val size: Long, val mime: String = "", val path: String = "") : UploadSource

    /** An address the server downloads. */
    @Serializable
    @SerialName("link")
    data class Link(val url: String) : UploadSource
}

/** A post's thumbnail, cached under its file's SHA-256 as the gallery does. */
@Serializable
data class Thumb(val url: String, val sha256: String)

@Serializable
sealed interface UploadStatus {
    /** Waiting for Upload; its tags can still change. */
    @Serializable
    @SerialName("draft")
    data object Draft : UploadStatus

    /** Waiting its turn to be sent. */
    @Serializable
    @SerialName("waiting")
    data object Waiting : UploadStatus

    /** On its way: [sent] of [total] bytes, at [speed] bytes a second. */
    @Serializable
    @SerialName("sending")
    data class Sending(val sent: Long = 0, val total: Long = 0, val speed: Long = 0) : UploadStatus

    @Serializable
    @SerialName("done")
    data class Done(val postId: Long, val thumb: Thumb? = null) : UploadStatus

    /** Already a post: that one, when the server said which. */
    @Serializable
    @SerialName("duplicate")
    data class Duplicate(val postId: Long? = null, val inRecycleBin: Boolean = false, val thumb: Thumb? = null) : UploadStatus

    @Serializable
    @SerialName("failed")
    data class Failed(val message: String) : UploadStatus

    /** Cancelled on its way. */
    @Serializable
    @SerialName("stopped")
    data object Stopped : UploadStatus
}

/** What an item is sent with, fixed when Upload is pressed. */
@Serializable
data class Plan(
    val tags: List<String>,
    @Serializable(with = RatingAsName::class) val rating: Rating,
    val source: String? = null,
    val autotag: Boolean? = null,
)

@Serializable
data class UploadItem(
    /** Also the name of its copy in the queue's folder. */
    val id: String,
    val source: UploadSource,
    /** Tags for this item only, on top of the shared ones. */
    val tags: String = "",
    /** Its own rating; `null` goes with the rest. */
    @Serializable(with = RatingAsName::class) val rating: Rating? = null,
    val status: UploadStatus = UploadStatus.Draft,
    val plan: Plan? = null,
    /** Which time it is being sent, so an attempt that ends late changes nothing. */
    val attempt: Int = 0,
    /** Sent again by itself after the server couldn't be reached, since Upload or Retry. */
    val tries: Int = 0,
) {
    val name: String
        get() =
            when (source) {
                is UploadSource.Local -> source.name
                is UploadSource.Link -> source.url
            }

    /** The file's size; a link has none. */
    val size: Long?
        get() = (source as? UploadSource.Local)?.size

    val isFile: Boolean get() = source is UploadSource.Local

    /** Sent for and not finished. */
    val pending: Boolean get() = status == UploadStatus.Waiting || status is UploadStatus.Sending

    /** Anything but what is on its way can be taken off the list. */
    val removable: Boolean get() = status !is UploadStatus.Sending

    val retryable: Boolean get() = (status is UploadStatus.Failed || status == UploadStatus.Stopped) && plan != null

    val finished: Boolean get() = status is UploadStatus.Done || status is UploadStatus.Duplicate
}

/** What goes on everything sent together, from the bar at the foot of the page. */
@Serializable
data class Batch(
    val tags: String = "",
    @Serializable(with = RatingAsName::class) val rating: Rating = Rating.SAFE,
    /** Where the files come from; links are their own source. */
    val source: String = "",
)

/** How sending an item ended. */
sealed interface Outcome {
    data class Posted(val postId: Long, val thumb: Thumb? = null) : Outcome

    data class Duplicate(val postId: Long?, val inRecycleBin: Boolean = false, val thumb: Thumb? = null) : Outcome

    data class Failed(val message: String) : Outcome

    /** The server couldn't be reached: it waits its turn again, for a later run. */
    data object Again : Outcome
}

/** How far the items sent together have got: [finished] of [files], and their bytes. */
data class Overall(val finished: Int, val files: Int, val sent: Long, val total: Long, val speed: Long) {
    /** 0 to 100, or `null` while nothing has a size. */
    val percent: Int? get() = percentOf(sent, total)
}

/** How many of a run's items went up, were there already or failed. */
data class Tally(val done: Int = 0, val duplicate: Int = 0, val failed: Int = 0) {
    val empty: Boolean get() = done == 0 && duplicate == 0 && failed == 0

    fun plus(outcome: Outcome): Tally =
        when (outcome) {
            is Outcome.Posted -> copy(done = done + 1)
            is Outcome.Duplicate -> copy(duplicate = duplicate + 1)
            is Outcome.Failed -> copy(failed = failed + 1)
            Outcome.Again -> this
        }
}

/**
 * The upload list as plain data, as the web's `logic/queue.rs`. An item is
 * a draft until Upload is pressed. It then waits its turn, is sent, and ends
 * done, a duplicate or failed; a failed or cancelled one can go again. Each
 * change gives a new state.
 */
@Serializable
data class UploadState(
    val items: List<UploadItem> = emptyList(),
    val batch: Batch = Batch(),
    /** A folder's files get its name as a tag. */
    val folderTags: Boolean = true,
) {
    fun get(id: String): UploadItem? = items.firstOrNull { it.id == id }

    private fun change(
        id: String,
        block: (UploadItem) -> UploadItem?,
    ): UploadState = copy(items = items.map { if (it.id == id) block(it) ?: it else it })

    /** Nothing waits for Upload, so what's on the list belongs to a batch sent already. */
    val startsBatch: Boolean get() = items.none { it.status == UploadStatus.Draft }

    val drafts: Int get() = items.count { it.status == UploadStatus.Draft }

    val pending: Int get() = items.count { it.pending }

    /**
     * Adds [new] at the end. The first of a new batch clears away what the
     * last one left: finished uploads and failures. Gives what went.
     */
    fun add(new: List<UploadItem>): Pair<UploadState, List<UploadItem>> {
        if (new.isEmpty()) return this to emptyList()
        val (kept, gone) = if (startsBatch) items.partition { it.pending } else items to emptyList()
        return copy(items = kept + new) to gone
    }

    fun setTags(
        id: String,
        tags: String,
    ): UploadState = change(id) { if (it.status == UploadStatus.Draft) it.copy(tags = tags) else null }

    fun setRating(
        id: String,
        rating: Rating?,
    ): UploadState = change(id) { if (it.status == UploadStatus.Draft) it.copy(rating = rating) else null }

    /** Takes an item off the list, unless it is on its way. Gives it, when it went. */
    fun remove(id: String): Pair<UploadState, UploadItem?> {
        val item = get(id)?.takeIf { it.removable } ?: return this to null
        return copy(items = items - item) to item
    }

    /** Empties the list, keeping what is on its way. */
    fun clear(): Pair<UploadState, List<UploadItem>> {
        val (kept, gone) = items.partition { it.status is UploadStatus.Sending }
        return copy(items = kept) to gone
    }

    /** Takes off the items that went up or were there already. */
    fun clearFinished(): Pair<UploadState, List<UploadItem>> {
        val (gone, kept) = items.partition { it.finished }
        return copy(items = kept) to gone
    }

    /** What's wrong with the tags of the drafts, if anything: each gets the shared ones and its own. */
    fun problem(check: (String) -> TagCheck = ::checkTags): BadTag? =
        items
            .filter { it.status == UploadStatus.Draft }
            .firstNotNullOfOrNull { check("${batch.tags} ${it.tags}").bad }

    /**
     * Sends the drafts with what the batch puts on them: each waits its turn.
     * A draft whose tags can't be tags fails, in [explain]'s words.
     */
    fun start(
        explain: (BadTag) -> String,
        check: (String) -> TagCheck = ::checkTags,
    ): UploadState {
        val source = batch.source.trim().ifEmpty { null }
        return copy(
            items =
                items.map { item ->
                    if (item.status != UploadStatus.Draft) return@map item
                    val read = check("${batch.tags} ${item.tags}")
                    val bad = read.bad
                    if (bad != null) return@map item.copy(status = UploadStatus.Failed(explain(bad)))
                    // The server downloads a link that comes with a source
                    // as it is, without looking it up on its site.
                    val plan = Plan(read.tags, item.rating ?: batch.rating, source.takeIf { item.isFile })
                    item.copy(status = UploadStatus.Waiting, plan = plan, tries = 0)
                },
        )
    }

    /**
     * Items to send now, so that no more than [limit] are on their way, but
     * for those in [except]. Each is marked as sent, its attempt counted.
     */
    fun claim(
        limit: Int,
        except: Set<String> = emptySet(),
    ): Pair<UploadState, List<UploadItem>> {
        var room = limit - items.count { it.status is UploadStatus.Sending }
        val claimed = ArrayList<UploadItem>()
        val next =
            items.map { item ->
                if (room <= 0 || item.status != UploadStatus.Waiting || item.id in except) return@map item
                room--
                item.copy(status = UploadStatus.Sending(total = item.size ?: 0), attempt = item.attempt + 1).also { claimed += it }
            }
        return copy(items = next) to claimed
    }

    /** How much of attempt [attempt] the server has taken. */
    fun sent(
        id: String,
        attempt: Int,
        sent: Long,
        total: Long,
        speed: Long,
    ): UploadState =
        change(id) { item ->
            if (item.status is UploadStatus.Sending && item.attempt == attempt) {
                item.copy(status = UploadStatus.Sending(sent, total, speed))
            } else {
                null
            }
        }

    /** How attempt [attempt] ended. Says whether it still counted: one cancelled, or taken off, changes nothing. */
    fun finish(
        id: String,
        attempt: Int,
        outcome: Outcome,
    ): Pair<UploadState, Boolean> {
        val item = get(id) ?: return this to false
        if (item.status !is UploadStatus.Sending || item.attempt != attempt) return this to false
        val ended =
            when (outcome) {
                is Outcome.Posted -> item.copy(status = UploadStatus.Done(outcome.postId, outcome.thumb))
                is Outcome.Duplicate -> item.copy(status = UploadStatus.Duplicate(outcome.postId, outcome.inRecycleBin, outcome.thumb))
                is Outcome.Failed -> item.copy(status = UploadStatus.Failed(outcome.message))
                Outcome.Again -> item.copy(status = UploadStatus.Waiting, tries = item.tries + 1)
            }
        return change(id) { ended } to true
    }

    /** Stops an item on its way or waiting its turn. */
    fun cancel(id: String): UploadState = change(id) { if (it.pending) it.copy(status = UploadStatus.Stopped) else null }

    /** Sends a failed or cancelled item again, with what it was sent with. */
    fun retry(id: String): UploadState = change(id) { if (it.retryable) it.copy(status = UploadStatus.Waiting, tries = 0) else null }

    /** Stops everything sent for: Stop in the notification. */
    fun stopPending(): UploadState = copy(items = items.map { if (it.pending) it.copy(status = UploadStatus.Stopped) else it })

    /** What was on its way waits its turn again: the system stopped the work, or the app died. */
    fun requeue(): UploadState =
        copy(items = items.map { if (it.status is UploadStatus.Sending) it.copy(status = UploadStatus.Waiting) else it })

    /**
     * The items sent together so far: how many are finished, and the bytes
     * done of all the files. `null` once none is left to go.
     */
    fun overall(): Overall? {
        val sent = items.filter { it.plan != null && it.status != UploadStatus.Stopped }
        if (sent.none { it.pending }) return null
        var finished = 0
        var total = 0L
        var done = 0L
        var speed = 0L
        for (item in sent) {
            val size = item.size ?: 0L
            when (val s = item.status) {
                is UploadStatus.Done, is UploadStatus.Duplicate -> {
                    finished++
                    total += size
                    done += size
                }

                is UploadStatus.Failed -> finished++

                is UploadStatus.Sending -> {
                    total += size
                    done += s.sent.coerceIn(0L, size)
                    speed += s.speed
                }

                else -> total += size
            }
        }
        return Overall(finished, sent.size, done, total, speed)
    }

    /** The list as it's kept on disk, without the bytes sent so far. */
    fun forDisk(): UploadState =
        copy(
            items = items.map {
                if (it.status is UploadStatus.Sending) it.copy(status = UploadStatus.Sending(total = it.size ?: 0)) else it
            },
        )
}

/** 0 to 100, or `null` without a total. */
fun percentOf(
    done: Long,
    total: Long,
): Int? = if (total <= 0) null else (done.coerceIn(0, total) * 100 / total).toInt()
