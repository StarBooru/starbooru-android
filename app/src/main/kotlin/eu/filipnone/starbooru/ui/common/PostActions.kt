package eu.filipnone.starbooru.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.ContextCompat
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.core.ScoreKind
import eu.filipnone.starbooru.data.Account
import eu.filipnone.starbooru.data.AccountState
import eu.filipnone.starbooru.data.Files
import eu.filipnone.starbooru.ui.viewer.Tally
import eu.filipnone.starbooru.ui.viewer.Votes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What a post's buttons and menus do, the same from the viewer and from a
 * tile: a vote shows at once and goes back if the server turns it down, a
 * deletion offers Undo, a download offers to open the file.
 */
class PostActions internal constructor(
    private val account: Account,
    private val context: Context,
    private val res: Resources,
    private val notices: Notices?,
    private val scope: CoroutineScope,
    private val onChanged: (Post) -> Unit,
    private val askPermission: (() -> Unit) -> Unit,
) {
    val guest: Boolean get() = account.state.value is AccountState.Guest

    val stars: Boolean get() = account.info.value?.scoreKind == ScoreKind.STARS

    val admin: Boolean get() = (account.state.value as? AccountState.SignedIn)?.user?.admin == true

    private val server: String get() = account.api.value?.server().orEmpty()

    private fun failed(
        error: Throwable,
        words: Int,
    ) {
        notices?.say(res.getString(words, describe(error, res, server)))
    }

    /** A guest is asked to sign in first; false when that happened instead. */
    private fun signedIn(): Boolean {
        if (!guest) return true
        notices?.offer(res.getString(R.string.guest_vote_why), res.getString(R.string.guest_sign_in)) {
            scope.launch { account.toSignIn() }
        }
        return false
    }

    /** Up (1), down (-1), or the same again to take it back. */
    fun vote(
        post: Post,
        pressed: Int,
    ) {
        if (!signedIn()) return
        val before = Tally.of(post)
        val like = Votes.nextVote(before.myVote, pressed)
        onChanged(before.liked(like).into(post))
        scope.launch {
            try {
                onChanged(account.call { it.vote(post.id, like.toShort()) })
            } catch (e: CoreException) {
                onChanged(before.into(post))
                failed(e, R.string.vote_failed)
            }
        }
    }

    /** Star [pressed] of five, or the same again to take it back. */
    fun star(
        post: Post,
        pressed: Int,
    ) {
        if (!signedIn()) return
        val before = Tally.of(post)
        val stars = Votes.nextStars(before.myStars, pressed)
        onChanged(before.starred(stars).into(post))
        scope.launch {
            try {
                onChanged(account.call { it.voteStars(post.id, stars.toUByte()) })
            } catch (e: CoreException) {
                onChanged(before.into(post))
                failed(e, R.string.vote_failed)
            }
        }
    }

    /** The menu's like: a like, or five stars where the server counts stars. */
    fun like(post: Post) {
        if (stars) star(post, Votes.MAX_STARS) else vote(post, 1)
    }

    fun liked(post: Post): Boolean = if (stars) post.myStars.toInt() == Votes.MAX_STARS else post.myVote > 0

    /** Moves [post] to the recycle bin, with Undo. */
    fun delete(
        post: Post,
        onGone: () -> Unit,
        onBack: (Post) -> Unit,
    ) {
        scope.launch {
            try {
                account.call { it.deletePost(post.id) }
                onGone()
                notices?.offer(res.getString(R.string.press_deleted), res.getString(R.string.press_undo)) {
                    scope.launch {
                        try {
                            onBack(account.call { it.restorePost(post.id) })
                        } catch (e: CoreException) {
                            notices?.say(res.getString(R.string.trash_restore_failed, post.id.toString(), describe(e, res, server)))
                        }
                    }
                }
            } catch (e: CoreException) {
                failed(e, R.string.delete_failed)
            }
        }
    }

    /** Brings [post] back from the recycle bin. */
    fun restore(post: Post) {
        scope.launch {
            try {
                onChanged(account.call { it.restorePost(post.id) })
            } catch (e: CoreException) {
                notices?.say(res.getString(R.string.trash_restore_failed, post.id.toString(), describe(e, res, server)))
            }
        }
    }

    /** Saves the originals to the gallery; the notice opens the last one. */
    fun download(posts: List<Post>) {
        askPermission {
            scope.launch {
                var last: Pair<android.net.Uri, String>? = null
                for (post in posts) {
                    try {
                        val api = account.api.value ?: return@launch
                        last = Files.saveToGallery(context, api, post) to post.mime
                    } catch (e: CoreException) {
                        notices?.say(describe(e, res, server))
                        return@launch
                    } catch (_: java.io.IOException) {
                        notices?.say(res.getString(R.string.download_failed))
                        return@launch
                    }
                }
                val (uri, mime) = last ?: return@launch
                val text =
                    if (posts.size == 1) {
                        res.getString(R.string.download_saved_one)
                    } else {
                        res.getQuantityString(R.plurals.download_saved_many, posts.size, posts.size)
                    }
                notices?.offer(text, res.getString(R.string.press_open)) { Files.open(context, uri, mime) }
            }
        }
    }

    fun share(posts: List<Post>) {
        scope.launch {
            try {
                val api = account.api.value ?: return@launch
                Files.share(context, api, posts)
            } catch (e: CoreException) {
                notices?.say(describe(e, res, server))
            } catch (_: java.io.IOException) {
                notices?.say(res.getString(R.string.download_failed))
            }
        }
    }
}

/** [PostActions] for a screen; [onChanged] takes in a post as it changes. */
@Composable
fun rememberPostActions(
    account: Account,
    onChanged: (Post) -> Unit,
): PostActions {
    val context = LocalContext.current
    val res = LocalResources.current
    val notices = LocalNotices.current
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChanged)
    var waiting by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) waiting?.invoke()
            waiting = null
        }
    return remember(account, notices) {
        PostActions(
            account = account,
            context = context.applicationContext,
            res = res,
            notices = notices,
            scope = scope,
            onChanged = { changed(it) },
            askPermission = { then ->
                val granted =
                    !Files.needsPermission ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED
                if (granted) {
                    then()
                } else {
                    waiting = then
                    permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            },
        )
    }
}
