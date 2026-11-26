package eu.filipnone.starbooru.ui.gallery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.MediaKind
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.ui.common.Media
import eu.filipnone.starbooru.ui.common.clock
import eu.filipnone.starbooru.ui.common.sharedPost
import eu.filipnone.starbooru.ui.common.tintColor
import eu.filipnone.starbooru.ui.gestures.SwipeAction
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import eu.filipnone.starbooru.ui.theme.StarSpringEasing
import kotlin.math.abs
import kotlin.math.roundToInt

/** What a tile's long press offers. */
enum class TileChoice { Open, Like, Select, Share, Download, Delete }

/**
 * One post in the grid. Gestures are the grid's (see `GalleryGestures`);
 * the tile shows their effect: sinking under a long press, sliding with a
 * swipe over the action it will run, a check while selecting.
 */
@Composable
fun Tile(
    post: Post,
    index: Int,
    flush: Boolean,
    animate: Boolean,
    pressed: Boolean,
    /** How far a swipe has moved it, in px; 0 at rest. */
    slide: Float,
    /** The action a swipe this way will run, and whether letting go now runs it. */
    swipe: SwipeAction,
    armed: Boolean,
    selecting: Boolean,
    picked: Boolean,
    menu: Boolean,
    liked: Boolean,
    canDelete: Boolean,
    onMenu: (TileChoice?) -> Unit,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val roles = StarBooruTheme.roles
    val corner = if (flush) 0.dp else 16.dp
    val shape = RoundedCornerShape(corner)
    // New tiles pop in one after another, a beat apart.
    val entrance = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (entrance.value < 1f) entrance.animateTo(1f, tween(420, delayMillis = (index % 60) * 16, easing = StarSpringEasing))
    }
    // A finger resting on a tile sinks it, a beat after it lands.
    val sink by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        if (pressed) tween(350, delayMillis = 100) else tween(180),
        label = "sink",
    )
    // A picked tile's picture draws in from its edges.
    val inset by animateDpAsState(if (picked) 10.dp else 0.dp, tween(250, easing = StarSpringEasing), label = "inset")
    val description = stringResource(R.string.post_alt, post.id.toString())
    val openLabel = stringResource(R.string.press_open)
    val selectLabel = stringResource(R.string.swipe_select)
    Box(
        modifier
            .graphicsLayer {
                val e = entrance.value
                alpha = e.coerceIn(0f, 1f)
                scaleX = (0.85f + 0.15f * e) * sink
                scaleY = (0.85f + 0.15f * e) * sink
            }.semantics {
                contentDescription = description
                if (selecting) selected = picked
                onClick(if (selecting) selectLabel else openLabel) {
                    if (selecting) onToggle() else onOpen()
                    true
                }
                onLongClick(description) {
                    onMenu(null)
                    true
                }
                customActions =
                    listOf(
                        CustomAccessibilityAction(selectLabel) {
                            onMenu(TileChoice.Select)
                            true
                        },
                    )
            },
    ) {
        if (slide != 0f) SwipeHint(slide, swipe, armed, shape)
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(slide.roundToInt(), 0) }
                .clip(shape)
                .background(if (picked) MaterialTheme.colorScheme.primaryContainer else post.tintColor() ?: roles.surface2),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    // The spring overshoots; padding can't go below zero.
                    .padding(inset.coerceAtLeast(0.dp))
                    .sharedPost(post.id, corner)
                    .clip(if (picked) RoundedCornerShape(12.dp) else shape)
                    .background(post.tintColor() ?: roles.surface2),
            ) {
                val request = Media.thumb(context, post)
                if (request != null) {
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (post.kind == MediaKind.VIDEO) {
                    Icon(
                        painterResource(R.drawable.ic_play),
                        null,
                        tint = roles.onScrim,
                        modifier = Modifier.align(Alignment.Center).size(40.dp),
                    )
                }
                Badge(post, Modifier.align(Alignment.BottomEnd))
            }
            if (selecting) Check(picked, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        if (menu) TileMenu(post, liked, canDelete, onMenu)
    }
}

@Composable
private fun Badge(
    post: Post,
    modifier: Modifier,
) {
    val roles = StarBooruTheme.roles
    val text =
        when (post.kind) {
            MediaKind.VIDEO -> post.durationMs?.let { clock(it) } ?: ""
            MediaKind.ANIMATION -> "GIF"
            MediaKind.IMAGE -> return
        }
    Row(
        modifier
            .padding(8.dp)
            .heightIn(min = 24.dp)
            .clip(CircleShape)
            .background(roles.scrim)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (post.kind ==
            MediaKind.VIDEO
        ) {
            Icon(painterResource(R.drawable.ic_play), null, tint = roles.onScrim, modifier = Modifier.size(16.dp))
        }
        if (text.isNotEmpty()) Text(text, color = roles.onScrim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** The round check of a tile while selecting; filled once picked. */
@Composable
private fun Check(
    picked: Boolean,
    modifier: Modifier,
) {
    val roles = StarBooruTheme.roles
    val pop by animateFloatAsState(if (picked) 1f else 0.6f, tween(250, easing = StarSpringEasing), label = "check")
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (picked) MaterialTheme.colorScheme.primary else roles.scrim)
            .border(2.dp, if (picked) MaterialTheme.colorScheme.primary else roles.onScrim, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (picked) {
            Icon(
                painterResource(R.drawable.ic_check),
                null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp).scale(pop),
            )
        }
    }
}

/** What a swipe reveals behind the tile: its action, which grows once letting go would run it. */
@Composable
private fun SwipeHint(
    slide: Float,
    action: SwipeAction,
    armed: Boolean,
    shape: RoundedCornerShape,
) {
    if (action == SwipeAction.Off) return
    val roles = StarBooruTheme.roles
    val danger = action == SwipeAction.Delete
    val grow by animateFloatAsState(if (armed) 1.3f else 1f, tween(250, easing = StarSpringEasing), label = "hint")
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(if (danger) roles.dangerContainer else MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = if (slide > 0f) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(0.5f), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(actionIcon(action)),
                null,
                tint = if (danger) roles.onDangerContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp).scale(grow).graphicsLayer { alpha = (abs(slide) / 120f).coerceIn(0.3f, 1f) },
            )
        }
    }
}

fun actionIcon(action: SwipeAction): Int =
    when (action) {
        SwipeAction.Like -> R.drawable.ic_thumb_up
        SwipeAction.Select -> R.drawable.ic_check
        SwipeAction.Share -> R.drawable.ic_share
        SwipeAction.Download -> R.drawable.ic_download
        SwipeAction.Delete -> R.drawable.ic_delete
        SwipeAction.Off -> R.drawable.ic_close
    }

/** A post's menu, from a long press on its tile. */
@Composable
private fun TileMenu(
    post: Post,
    liked: Boolean,
    canDelete: Boolean,
    onPick: (TileChoice?) -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = { onPick(null) }, shape = RoundedCornerShape(28.dp)) {
        Text(
            stringResource(R.string.post_alt, post.id.toString()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Item(R.drawable.ic_open_in_new, stringResource(R.string.press_open)) { onPick(TileChoice.Open) }
        Item(R.drawable.ic_thumb_up, stringResource(if (liked) R.string.press_unlike else R.string.swipe_like)) { onPick(TileChoice.Like) }
        Item(R.drawable.ic_check, stringResource(R.string.swipe_select)) { onPick(TileChoice.Select) }
        Item(R.drawable.ic_share, stringResource(R.string.share_button)) { onPick(TileChoice.Share) }
        Item(R.drawable.ic_download, stringResource(R.string.post_download)) { onPick(TileChoice.Download) }
        if (canDelete) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Item(R.drawable.ic_delete, stringResource(R.string.post_delete_title), danger = true) { onPick(TileChoice.Delete) }
        }
        Spacer(Modifier.size(4.dp))
    }
}

@Composable
private fun Item(
    icon: Int,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) StarBooruTheme.roles.danger else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(label, color = tint, fontWeight = FontWeight.SemiBold) },
        leadingIcon = { Icon(painterResource(icon), null, tint = tint) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
    )
}
