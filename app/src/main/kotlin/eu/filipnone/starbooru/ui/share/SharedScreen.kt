package eu.filipnone.starbooru.ui.share

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.Api
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.MediaKind
import eu.filipnone.starbooru.core.SharedPost
import eu.filipnone.starbooru.core.SharedView
import eu.filipnone.starbooru.ui.common.LocalReducedMotion
import eu.filipnone.starbooru.ui.common.clock
import eu.filipnone.starbooru.ui.common.describe
import eu.filipnone.starbooru.ui.frame.LocalFrame
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import eu.filipnone.starbooru.ui.viewer.VideoPlayer
import eu.filipnone.starbooru.ui.viewer.Zoom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Loaded {
    data object Waiting : Loaded

    data object Missing : Loaded

    data class Failed(val error: Throwable) : Loaded

    data class Ready(val view: SharedView) : Loaded
}

/**
 * What a share link shows, opened in the app: one post on a stage with
 * zoom, or several as tiles that open a lightbox. It asks the link's own
 * server, with no account there.
 */
@Composable
fun SharedScreen(
    server: String,
    token: String,
    onBack: () -> Unit,
    padding: PaddingValues = LocalFrame.current.padding,
) {
    var loaded by remember(server, token) { mutableStateOf<Loaded>(Loaded.Waiting) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(server, token, attempt) {
        loaded = Loaded.Waiting
        loaded =
            try {
                Loaded.Ready(withContext(Dispatchers.IO) { Api(server, null).use { it.shared(token) } })
            } catch (e: CoreException) {
                if (SharedPage.missing(e)) Loaded.Missing else Loaded.Failed(e)
            }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val l = loaded) {
            Loaded.Waiting -> {
                Back(onBack)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            Loaded.Missing -> {
                Back(onBack)
                Missing()
            }

            is Loaded.Failed -> {
                Back(onBack)
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(describe(l.error, server), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    FilledTonalButton(onClick = { attempt++ }, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            is Loaded.Ready -> Shown(l.view, onBack)
        }
    }
}

@Composable
private fun Back(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.page_back))
    }
}

@Composable
private fun Missing() {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(R.drawable.ic_link), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(
            stringResource(R.string.shared_missing_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            stringResource(R.string.shared_missing_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ColumnScope.Shown(
    view: SharedView,
    onBack: () -> Unit,
) {
    val count = view.posts.size
    Row(verticalAlignment = Alignment.Top) {
        Back(onBack)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                SharedPage.title(view.title) ?: pluralStringResource(R.plurals.share_posts, count, count),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val until =
                view.expiresAtMs
                    ?.let {
                        val day = Instant.ofEpochMilli(
                            it,
                        ).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                        " · " + stringResource(R.string.shared_until, day)
                    }.orEmpty()
            Text(stringResource(R.string.shared_from, view.server) + until, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    when (SharedPage.layout(count)) {
        SharedLayout.Empty -> Text(stringResource(R.string.shared_empty), modifier = Modifier.padding(16.dp))

        SharedLayout.One ->
            view.posts.firstOrNull()?.let { post ->
                Stage(
                    post = post,
                    key = "shared:0",
                    active = true,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(StarBooruTheme.roles.stage),
                )
            }

        SharedLayout.Many -> {
            var open by rememberSaveable { mutableStateOf<Int?>(null) }
            Grid(view.posts, onOpen = { open = it }, modifier = Modifier.weight(1f))
            open?.let { at -> Lightbox(view.posts, at, onClose = { open = null }) }
        }
    }
}

/** The posts as tiles in the link's order; the first screenful comes in one after another, as in the gallery. */
@Composable
private fun Grid(
    posts: List<SharedPost>,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
) {
    val roles = StarBooruTheme.roles
    val reduced = LocalReducedMotion.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        itemsIndexed(posts) { i, post ->
            val shown = remember { Animatable(if (reduced) 1f else 0f) }
            LaunchedEffect(Unit) {
                if (shown.value < 1f) {
                    delay(i.coerceAtMost(40) * 16L)
                    shown.animateTo(1f, tween(240))
                }
            }
            val label = stringResource(R.string.shared_open, (i + 1).toString())
            Box(
                Modifier
                    .aspectRatio(1f)
                    .graphicsLayer {
                        alpha = shown.value
                        scaleX = 0.96f + 0.04f * shown.value
                        scaleY = scaleX
                    }.clip(RoundedCornerShape(16.dp))
                    .background(roles.surface2)
                    .semantics { contentDescription = label }
                    .clickable { onOpen(i) },
            ) {
                val thumb = post.thumbUrl ?: post.sampleUrl.takeIf { post.kind != MediaKind.VIDEO }
                if (thumb != null) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.ic_gallery),
                        null,
                        tint = roles.onSurface2,
                        modifier = Modifier.align(Alignment.Center).size(40.dp),
                    )
                }
                Badge(post, Modifier.align(Alignment.BottomEnd))
            }
        }
    }
}

@Composable
private fun Badge(
    post: SharedPost,
    modifier: Modifier,
) {
    val roles = StarBooruTheme.roles
    val text =
        when (post.kind) {
            MediaKind.VIDEO -> post.durationMs?.let { clock(it) }.orEmpty()
            MediaKind.ANIMATION -> stringResource(R.string.badge_gif)
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

/** One post at a time over the whole window, with previous, next and the count; Back closes it. */
@Composable
private fun Lightbox(
    posts: List<SharedPost>,
    start: Int,
    onClose: () -> Unit,
) {
    val roles = StarBooruTheme.roles
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val pager = rememberPagerState(initialPage = start.coerceIn(0, posts.lastIndex.coerceAtLeast(0))) { posts.size }
        var zoomed by remember { mutableStateOf(false) }
        // It grows out of the tile's place in the grid, roughly: a quick zoom in.
        val enter = remember { Animatable(if (reduced) 1f else 0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, tween(220)) }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = enter.value }
                .background(roles.stage),
        ) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed,
                pageSpacing = 16.dp,
                modifier =
                    Modifier.fillMaxSize().graphicsLayer {
                        val s = 0.94f + 0.06f * enter.value
                        scaleX = s
                        scaleY = s
                    },
            ) { i ->
                posts.getOrNull(i)?.let { post ->
                    Stage(
                        post = post,
                        key = "shared:$i",
                        active = i == pager.currentPage,
                        onZoomed = { if (i == pager.currentPage) zoomed = it },
                        // A video's controls stay clear of the system's bars.
                        modifier = Modifier.fillMaxSize().then(
                            if (post.kind ==
                                MediaKind.VIDEO
                            ) {
                                Modifier.safeDrawingPadding()
                            } else {
                                Modifier
                            },
                        ),
                    )
                }
            }
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                Round(R.drawable.ic_close, stringResource(R.string.share_close), Modifier.align(Alignment.TopEnd), onClose)
                val at = pager.currentPage
                SharedPage.step(at, posts.size, forward = false)?.let { to ->
                    Round(R.drawable.ic_chevron_left, stringResource(R.string.shared_previous), Modifier.align(Alignment.CenterStart)) {
                        scope.launch { if (reduced) pager.scrollToPage(to) else pager.animateScrollToPage(to) }
                    }
                }
                SharedPage.step(at, posts.size, forward = true)?.let { to ->
                    Round(R.drawable.ic_chevron_right, stringResource(R.string.shared_next), Modifier.align(Alignment.CenterEnd)) {
                        scope.launch { if (reduced) pager.scrollToPage(to) else pager.animateScrollToPage(to) }
                    }
                }
                // Up out of the way of a video's controls.
                val video = posts.getOrNull(at)?.kind == MediaKind.VIDEO
                Text(
                    "${at + 1} / ${posts.size}",
                    color = roles.onScrim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier =
                        Modifier
                            .align(if (video) Alignment.TopCenter else Alignment.BottomCenter)
                            .clip(CircleShape)
                            .background(roles.scrim)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Round(
    icon: Int,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val roles = StarBooruTheme.roles
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(roles.scrim)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), label, tint = roles.onScrim)
    }
}

/**
 * A post at fit, with pinch and double-tap zoom as in the viewer; zoomed in,
 * the original comes. A video plays in the app's player while [active].
 */
@Composable
private fun Stage(
    post: SharedPost,
    key: String,
    active: Boolean,
    modifier: Modifier,
    onZoomed: (Boolean) -> Unit = {},
) {
    val roles = StarBooruTheme.roles
    if (post.kind == MediaKind.VIDEO && active) {
        VideoPlayer(key, post.fileUrl, post.durationMs, modifier)
        return
    }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val fit = SharedPage.fitted(post, w, h)
        var zoom by remember(key) { mutableStateOf(Zoom()) }
        val zoomNow by rememberUpdatedState(zoom)
        LaunchedEffect(zoom.isZoomed) { onZoomed(zoom.isZoomed) }
        // Not active any more: back to fit for when it comes round again.
        LaunchedEffect(active) { if (!active) zoom = Zoom() }
        val video = post.kind == MediaKind.VIDEO
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(w, h, video) {
                    if (video) return@pointerInput
                    detectTapGestures(onDoubleTap = { at -> zoom = zoomNow.toggleAt(at - Offset(w / 2f, h / 2f), fit) })
                }.pointerInput(w, h, video) {
                    if (video) return@pointerInput
                    // Two fingers zoom; one pans once zoomed in, and otherwise
                    // goes on to the lightbox's swipe.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            if (fingers >= 2 || (fingers == 1 && zoomNow.isZoomed)) {
                                val centre = event.calculateCentroid() - Offset(w / 2f, h / 2f)
                                zoom = zoomNow.zoomAt(event.calculateZoom(), centre, fit).pan(event.calculatePan(), fit)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val width = with(density) { fit.width.toDp() }
            val height = with(density) { fit.height.toDp() }
            AsyncImage(
                model = if (video) post.thumbUrl ?: post.fileUrl else SharedPage.shown(post, original = zoom.isZoomed),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .size(width, height)
                        .graphicsLayer {
                            scaleX = zoom.scale
                            scaleY = zoom.scale
                            translationX = zoom.offset.x
                            translationY = zoom.offset.y
                        },
            )
            if (video) Icon(painterResource(R.drawable.ic_play), null, tint = roles.onScrim, modifier = Modifier.size(56.dp))
        }
    }
}
