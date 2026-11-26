package eu.filipnone.starbooru.ui.upload

import android.content.ClipData
import android.net.Uri
import androidx.lifecycle.ViewModel
import eu.filipnone.starbooru.core.BadTag
import eu.filipnone.starbooru.core.Rating
import eu.filipnone.starbooru.core.ServerInfo
import eu.filipnone.starbooru.data.Account
import eu.filipnone.starbooru.data.upload.Adding
import eu.filipnone.starbooru.data.upload.UploadNote
import eu.filipnone.starbooru.data.upload.UploadQueue
import eu.filipnone.starbooru.data.upload.UploadState
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Upload page over the app's [UploadQueue]: the list itself lives
 * there, with the worker that sends it, so it goes on when the page or the
 * app is gone.
 */
class UploadViewModel(private val queue: UploadQueue, account: Account) : ViewModel() {
    val state: StateFlow<UploadState> = queue.state

    /** Files being copied in. */
    val adding: StateFlow<Adding?> = queue.adding

    val notes: SharedFlow<UploadNote> = queue.notes

    val info: StateFlow<ServerInfo?> = account.info

    private val _problem = MutableStateFlow<BadTag?>(null)

    /** The shared or own tag that kept Upload from sending, until the tags change. */
    val problem: StateFlow<BadTag?> = _problem.asStateFlow()

    /** Whether the server imports links. Taken as yes while it hasn't said. */
    val links: Boolean get() = info.value?.urlImport ?: true

    /** Where an item's copy is, for its picture. */
    fun fileOf(id: String): File = queue.fileOf(id)

    fun takeUris(uris: List<Uri>) = queue.takeUris(uris)

    fun takeTree(tree: Uri) = queue.takeTree(tree)

    fun paste(clip: ClipData?) = queue.takeClip(clip)

    /** Adds the links in [text]; false when it has none. */
    fun addLinks(text: String): Boolean = queue.addLinks(text)

    fun setSharedTags(tags: String) {
        queue.setBatch(state.value.batch.copy(tags = tags))
        _problem.value = null
    }

    fun setSharedRating(rating: Rating) = queue.setBatch(state.value.batch.copy(rating = rating))

    fun setSource(source: String) = queue.setBatch(state.value.batch.copy(source = source))

    fun setFolderTags(on: Boolean) = queue.setFolderTags(on)

    fun setTags(
        id: String,
        tags: String,
    ) {
        queue.setTags(id, tags)
        _problem.value = null
    }

    fun setRating(
        id: String,
        rating: Rating?,
    ) = queue.setRating(id, rating)

    fun remove(id: String) = queue.remove(id)

    fun cancel(id: String) = queue.cancel(id)

    fun retry(id: String) = queue.retry(id)

    fun clear() = queue.clear()

    fun clearFinished() = queue.clearFinished()

    /** Sends the drafts. False, with [problem] set, when a tag can't be one. */
    fun start(): Boolean {
        val bad = queue.start()
        _problem.value = bad
        return bad == null
    }
}
