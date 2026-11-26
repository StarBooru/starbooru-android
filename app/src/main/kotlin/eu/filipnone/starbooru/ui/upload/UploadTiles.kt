package eu.filipnone.starbooru.ui.upload

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.data.upload.Accept
import eu.filipnone.starbooru.data.upload.Thumb
import eu.filipnone.starbooru.data.upload.UploadItem
import eu.filipnone.starbooru.data.upload.UploadSource
import eu.filipnone.starbooru.data.upload.UploadStatus
import eu.filipnone.starbooru.data.upload.percentOf
import eu.filipnone.starbooru.ui.common.fileSize
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import eu.filipnone.starbooru.ui.theme.StarRoles
import eu.filipnone.starbooru.ui.theme.StarSpringEasing
import java.io.File

/** The rounding of the tiles, as the gallery's. */
internal val TileShape = RoundedCornerShape(16.dp)

/** What a tile does when pressed and with its corner buttons. */
internal class TileActions(
    val open: (UploadItem) -> Unit,
    val remove: (UploadItem) -> Unit,
    val cancel: (UploadItem) -> Unit,
    val retry: (UploadItem) -> Unit,
)

/** The request for a picked file's own picture, or a video's first frame. */
internal fun localPicture(
    context: Context,
    item: UploadItem,
    file: File,
    sizePx: Int,
): ImageRequest {
    val source = item.source as? UploadSource.Local
    val video = source != null && Accept.isVideo(source.name, source.mime)
    return ImageRequest
        .Builder(context)
        .data(file)
        .size(sizePx)
        .memoryCacheKey(localKey(item.id))
        .diskCachePolicy(CachePolicy.DISABLED)
        .apply { if (video) decoderFactory(VideoFrameDecoder.Factory()) }
        .build()
}

private fun localKey(id: String) = "upload:$id"

/** The post's thumbnail, cached as the gallery caches it, over the file's own picture until it comes. */
private fun postPicture(
    context: Context,
    item: UploadItem,
    thumb: Thumb,
): ImageRequest =
    ImageRequest
        .Builder(context)
        .data(thumb.url)
        .diskCacheKey("thumb:${thumb.sha256}")
        .memoryCacheKey("thumb:${thumb.sha256}")
        .placeholderMemoryCacheKey(localKey(item.id))
        .build()

/** The words a tile says of what happened to it, or `null` for a draft. */
@Composable
private fun stateWords(item: UploadItem): String? =
    when (val s = item.status) {
        UploadStatus.Draft -> null

        UploadStatus.Waiting -> stringResource(R.string.upload_queued)

        is UploadStatus.Sending ->
            percentOf(s.sent, s.total)?.takeIf { item.isFile }?.let { "$it%" }
                ?: stringResource(if (item.isFile) R.string.upload_uploading else R.string.upload_job_running)

        is UploadStatus.Done -> stringResource(if (item.isFile) R.string.upload_uploaded else R.string.upload_done)

        is UploadStatus.Duplicate ->
            when {
                s.postId == null -> stringResource(R.string.upload_known)
                s.inRecycleBin -> stringResource(R.string.upload_known_post_bin, s.postId.toString())
                else -> stringResource(R.string.upload_known_post, s.postId.toString())
            }

        is UploadStatus.Failed -> s.message

        UploadStatus.Stopped -> stringResource(R.string.upload_stopped)
    }

/** What pressing the tile does, for TalkBack. */
@Composable
private fun pressWords(item: UploadItem): String? =
    when (val s = item.status) {
        UploadStatus.Draft -> stringResource(R.string.upload_open, item.name)
        is UploadStatus.Done -> stringResource(R.string.upload_open_post, s.postId.toString())
        is UploadStatus.Duplicate -> s.postId?.let { stringResource(R.string.upload_open_post, it.toString()) }
        is UploadStatus.Failed -> stringResource(R.string.upload_failed) + ": " + s.message
        else -> null
    }

/**
 * One file or link: its picture, and over it how it's getting on. A draft
 * opens its sheet, a posted one its post, and a failed one says why.
 */
@Composable
internal fun UploadTile(
    item: UploadItem,
    file: File,
    actions: TileActions,
    modifier: Modifier = Modifier,
) {
    val roles = StarBooruTheme.roles
    val context = LocalContext.current
    val status = item.status
    val words = stateWords(item)
    val press = pressWords(item)
    val busy = status == UploadStatus.Waiting || status is UploadStatus.Sending
    // Darkened while it waits, clearing as it goes, clear once it's up.
    val dim by animateFloatAsState(
        when (status) {
            UploadStatus.Waiting -> 0.8f
            is UploadStatus.Sending -> 0.8f * (1f - (percentOf(status.sent, status.total) ?: 0) / 100f)
            UploadStatus.Stopped, is UploadStatus.Failed -> 0.8f
            else -> 0f
        },
        tween(300),
        label = "dim",
    )
    Box(
        modifier
            .aspectRatio(1f)
            .clip(TileShape)
            .background(roles.surface2)
            .then(
                if (press != null) {
                    Modifier
                        .clickable { actions.open(item) }
                        .semantics {
                            contentDescription = press
                            role = Role.Button
                        }
                } else {
                    Modifier.semantics { contentDescription = listOfNotNull(item.name, words).joinToString(", ") }
                },
            ),
    ) {
        Face(item, file, Modifier.fillMaxSize(), context)
        if (dim > 0.01f) {
            Box(Modifier.fillMaxSize().background(roles.scrim.copy(alpha = roles.scrim.alpha * dim)))
        }
        if (status is UploadStatus.Failed) {
            Box(Modifier.fillMaxSize().border(2.dp, roles.danger, TileShape))
        }
        Overlay(item, words, busy, actions)
    }
}

/** The file's picture, the post's once it's up, or an icon and its name. */
@Composable
private fun Face(
    item: UploadItem,
    file: File,
    modifier: Modifier,
    context: Context,
) {
    val roles = StarBooruTheme.roles
    val status = item.status
    val thumb =
        when (status) {
            is UploadStatus.Done -> status.thumb
            is UploadStatus.Duplicate -> status.thumb
            else -> null
        }
    val request =
        remember(item.id, thumb, item.isFile) {
            when {
                thumb != null -> postPicture(context, item, thumb)
                item.isFile -> localPicture(context, item, file, TILE_PX)
                else -> null
            }
        }
    Box(modifier, contentAlignment = Alignment.Center) {
        // Under the picture, for a link or a file that can't be drawn.
        Column(
            Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val link = item.source as? UploadSource.Link
            val icon = if (link != null && !Accept.isPictureUrl(link.url)) R.drawable.ic_link else R.drawable.ic_gallery
            Icon(painterResource(icon), null, tint = roles.onSurface2, modifier = Modifier.size(28.dp))
            Text(
                if (link != null) Accept.hostOf(link.url) else item.name,
                style = MaterialTheme.typography.labelSmall,
                color = roles.onSurface2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (request != null) {
            AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun BoxScope.Overlay(
    item: UploadItem,
    words: String?,
    busy: Boolean,
    actions: TileActions,
) {
    val roles = StarBooruTheme.roles
    val status = item.status
    // The mark of how it ended, top left, popping in when it lands.
    AnimatedContent(
        targetState = markOf(status),
        transitionSpec = { (fadeIn(tween(200)) + scaleIn(tween(420, easing = StarSpringEasing), 0.4f)) togetherWith fadeOut(tween(120)) },
        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        label = "mark",
    ) { mark ->
        if (mark != null) {
            val (icon, colour) = mark
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(colour.container(roles)),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(icon), null, tint = colour.ink(roles), modifier = Modifier.size(16.dp)) }
        }
    }
    if (status is UploadStatus.Sending) {
        val percent = percentOf(status.sent, status.total)
        Box(Modifier.align(Alignment.Center).size(52.dp).clip(CircleShape).background(roles.scrim), contentAlignment = Alignment.Center) {
            if (percent != null && item.isFile) {
                val shown by animateFloatAsState(percent / 100f, tween(300), label = "sent")
                CircularProgressIndicator(progress = {
                    shown
                }, modifier = Modifier.size(40.dp), color = roles.onScrim, trackColor = Color.Transparent)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(40.dp), color = roles.onScrim)
            }
        }
    }
    val own = item.tags.split(' ').count { it.isNotBlank() }
    if (status == UploadStatus.Draft && own > 0) {
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(CircleShape)
                .background(roles.primary)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(painterResource(R.drawable.ic_label), null, tint = roles.onPrimary, modifier = Modifier.size(14.dp))
            Text(own.toString(), color = roles.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    if (words != null) {
        Text(
            words,
            color = roles.onScrim,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(roles.scrim)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
    // The corner buttons: the cross, and Retry beside it.
    Row(Modifier.align(Alignment.TopEnd)) {
        if (item.retryable) {
            CornerButton(R.drawable.ic_restore, stringResource(R.string.upload_retry, item.name)) { actions.retry(item) }
        }
        when {
            busy -> CornerButton(R.drawable.ic_close, stringResource(R.string.upload_cancel, item.name)) { actions.cancel(item) }

            item.removable && !item.finished -> CornerButton(R.drawable.ic_close, stringResource(R.string.upload_remove, item.name)) {
                actions.remove(item)
            }
        }
    }
}

/** The colour a mark stands in, as the web's `.up-mark`. */
private enum class Tone {
    Ok,
    Known,
    Bad,
    ;

    fun container(roles: StarRoles): Color =
        when (this) {
            Ok -> roles.primary
            Known -> roles.scrim
            Bad -> roles.danger
        }

    fun ink(roles: StarRoles): Color =
        when (this) {
            Ok, Bad -> roles.onPrimary
            Known -> roles.onScrim
        }
}

private fun markOf(status: UploadStatus): Pair<Int, Tone>? =
    when (status) {
        is UploadStatus.Done -> R.drawable.ic_check to Tone.Ok
        is UploadStatus.Duplicate -> R.drawable.ic_history to Tone.Known
        is UploadStatus.Failed -> R.drawable.ic_close to Tone.Bad
        else -> null
    }

/** A round button in a tile's corner, small to the eye and full size to the finger. */
@Composable
private fun CornerButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    val roles = StarBooruTheme.roles
    IconButton(onClick = onClick) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(roles.scrim), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), label, tint = roles.onScrim, modifier = Modifier.size(18.dp))
        }
    }
}

/** The tile that adds more: the photo picker, as the empty page's big button. */
@Composable
internal fun AddTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val roles = StarBooruTheme.roles
    val label = stringResource(R.string.upload_photos)
    Column(
        modifier
            .aspectRatio(1f)
            .clip(TileShape)
            .border(2.dp, roles.outline.copy(alpha = 0.5f), TileShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.ic_add), null, tint = roles.primary, modifier = Modifier.size(32.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = roles.primary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** A file's size and type, as a draft's caption in its sheet. */
@Composable
internal fun sizeOf(item: UploadItem): String? = item.size?.takeIf { it > 0 }?.let { fileSize(it) }

/** The size tiles are decoded at, in pixels. */
private const val TILE_PX = 360
