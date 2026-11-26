package eu.filipnone.starbooru.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.MediaKind
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.data.Hand
import eu.filipnone.starbooru.ui.common.LocalReducedMotion
import eu.filipnone.starbooru.ui.common.LocalViewerFocus
import eu.filipnone.starbooru.ui.common.Media
import eu.filipnone.starbooru.ui.common.describe
import eu.filipnone.starbooru.ui.common.sharedPost
import eu.filipnone.starbooru.ui.common.tintColor
import eu.filipnone.starbooru.ui.frame.LocalBars
import eu.filipnone.starbooru.ui.frame.LocalFrame
import eu.filipnone.starbooru.ui.frame.LocalGlassSource
import eu.filipnone.starbooru.ui.frame.LocalPlainGlass
import eu.filipnone.starbooru.ui.frame.WidthClass
import eu.filipnone.starbooru.ui.frame.glass
import eu.filipnone.starbooru.ui.gestures.PullToClose
import eu.filipnone.starbooru.ui.gestures.StageDrag
import eu.filipnone.starbooru.ui.gestures.TrailPoint
import eu.filipnone.starbooru.ui.gestures.Turn
import eu.filipnone.starbooru.ui.gestures.ViewerSwipe
import eu.filipnone.starbooru.ui.gestures.speedX
import eu.filipnone.starbooru.ui.gestures.speedY
import eu.filipnone.starbooru.ui.gestures.trailWith
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import eu.filipnone.starbooru.ui.theme.StarEasing
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The space between two pictures as they slide past each other. */
private val SLIDE_GAP = 24.dp

/** Two taps this close together in time make a double tap. */
private const val DOUBLE_TAP_MS = 300L

/** A single tap waits this long for a second one before it acts. */
private const val SINGLE_TAP_WAIT_MS = 280L

/** The detail panel's width beside the picture, from 840 dp. */
private val PANEL = 360.dp

/**
 * A post: its picture on the stage, with the cards under it, or beside it on
 * the hand's side from 840 dp. The stage takes swipes to the previous and
 * next post, following the finger, pinches and double taps to zoom, and a
 * pull down to go back to the gallery. A tap goes fullscreen.
 */
@Composable
fun ViewerScreen(
    vm: ViewerViewModel,
    server: String,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
) {
    val flow by vm.state.collectAsStateWithLifecycle()
    // What the stage shows, set at once when a swipe turns, so the next
    // picture takes the middle in the same frame the slide ends.
    var shown by remember { mutableStateOf(flow) }
    LaunchedEffect(flow) { shown = flow }
    val frame = LocalFrame.current
    val focus = LocalViewerFocus.current
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val bars = LocalBars.current
    LaunchedEffect(shown.id) { focus.value = shown.id }
    DisposableEffect(fullscreen) {
        bars.value = !fullscreen
        onDispose { bars.value = true }
    }
    SystemBars(hidden = fullscreen)
    BackHandler(enabled = fullscreen) { fullscreen = false }

    val post = shown.post
    val error = shown.error
    val wide = frame.width == WidthClass.Expanded && !fullscreen
    val scroll = rememberScrollState()
    var pull by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(if (fullscreen) StarBooruTheme.roles.stage else MaterialTheme.colorScheme.background),
    ) {
        val pad = if (fullscreen) androidx.compose.foundation.layout.PaddingValues(0.dp) else frame.padding
        val stage =
            @Composable { modifier: Modifier ->
                Stage(
                    vm = vm,
                    shown = shown,
                    onShown = { shown = it },
                    fullscreen = fullscreen,
                    onFullscreen = { fullscreen = it },
                    pullable = fullscreen || wide || scroll.value == 0,
                    onPull = { pull = it },
                    onClose = onBack,
                    hand = frame.hand,
                    arrows = frame.width != WidthClass.Compact,
                    modifier = modifier,
                )
            }
        val details =
            @Composable { modifier: Modifier ->
                Column(modifier.graphicsLayer { alpha = 1f - pull }) {
                    when {
                        post != null -> PostDetails(post, onSearch)
                        error != null -> Problem(describe(error, server), vm::retry)
                    }
                }
            }
        if (fullscreen) {
            stage(Modifier.fillMaxSize())
        } else if (wide) {
            val panelFirst = frame.hand == Hand.Left
            Row(
                Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (panelFirst) details(Modifier.width(PANEL).fillMaxHeight().verticalScroll(scroll))
                stage(Modifier.weight(1f).fillMaxHeight())
                if (!panelFirst) details(Modifier.width(PANEL).fillMaxHeight().verticalScroll(scroll))
            }
        } else {
            val stageHeight = (maxHeight * 0.58f).coerceIn(240.dp, 760.dp)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(pad)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                stage(Modifier.fillMaxWidth().height(stageHeight))
                details(Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(bottom = 16.dp))
            }
        }
    }
}

@Composable
private fun Stage(
    vm: ViewerViewModel,
    shown: ViewerState,
    onShown: (ViewerState) -> Unit,
    fullscreen: Boolean,
    onFullscreen: (Boolean) -> Unit,
    pullable: Boolean,
    onPull: (Float) -> Unit,
    onClose: () -> Unit,
    hand: Hand,
    arrows: Boolean,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    var zoom by remember(shown.id) { mutableStateOf(Zoom()) }
    var slide by remember { mutableFloatStateOf(0f) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var settling by remember { mutableStateOf<Job?>(null) }
    var single by remember { mutableStateOf<Job?>(null) }

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(if (fullscreen) 0.dp else 28.dp))
            .clipToBounds(),
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val d = density.density
        val gap = with(density) { SLIDE_GAP.toPx() }
        val post = shown.post
        val fit = fitted(post, w, h)
        val pullAmount = PullToClose.pull(drag.y / d, h / d)
        LaunchedEffect(pullAmount) { onPull(pullAmount) }

        val now by rememberUpdatedState(shown)
        val canPull by rememberUpdatedState(pullable)
        val zoomNow by rememberUpdatedState(zoom)
        val fitNow by rememberUpdatedState(fit)

        fun turn(to: Turn) {
            val target = if (to == Turn.Next) -(w + gap) else (w + gap)
            settling?.cancel()
            settling =
                scope.launch {
                    animate(slide, target, animationSpec = tween(if (reduced) 1 else 250, easing = StarEasing)) { v, _ -> slide = v }
                    if (vm.go(if (to == Turn.Next) Side.Next else Side.Prev)) onShown(vm.state.value)
                    slide = 0f
                }
        }

        fun neighbour(to: Turn): Post? = if (to == Turn.Next) now.next else now.prev

        val stageGlass = rememberHazeState()
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(stageGlass)
                .pointerInput(w, h) {
                    var lastTap = 0L
                    var lastTapAt = Offset.Zero
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // A finger on a picture still settling catches it there.
                        settling?.cancel()
                        val slideBase = slide
                        val dragBase = drag
                        var trail = listOf(TrailPoint(down.uptimeMillis, down.position.x / d, down.position.y / d))
                        var mode = StageDrag.Undecided
                        var pinched = false
                        var last = down
                        var consumedUp = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                consumedUp = event.changes.any { it.isConsumed }
                                last = event.changes.firstOrNull() ?: last
                                break
                            }
                            if (pressed.size >= 2) {
                                pinched = true
                                val centre = event.calculateCentroid() - Offset(w / 2f, h / 2f)
                                zoom = zoomNow.zoomAt(event.calculateZoom(), centre, fitNow).pan(event.calculatePan(), fitNow)
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            val change = pressed.first()
                            last = change
                            if (pinched) {
                                if (zoomNow.isZoomed) {
                                    zoom = zoomNow.pan(change.positionChange(), fitNow)
                                    change.consume()
                                }
                                continue
                            }
                            trail = trailWith(trail, TrailPoint(change.uptimeMillis, change.position.x / d, change.position.y / d))
                            val dx = (change.position.x - down.position.x) / d
                            val dy = (change.position.y - down.position.y) / d
                            if (mode == StageDrag.Undecided) {
                                mode = ViewerSwipe.drag(dx, dy, zoomNow.isZoomed, canPull)
                                // Up, or down where the page can scroll: the page's.
                                if (mode == StageDrag.None && !zoomNow.isZoomed) return@awaitEachGesture
                            }
                            when (mode) {
                                StageDrag.Sideways -> {
                                    val toward = if (dx < 0f) Turn.Next else Turn.Previous
                                    slide = slideBase + ViewerSwipe.resist(dx * d, neighbour(toward) == null)
                                    change.consume()
                                }

                                StageDrag.Down -> {
                                    drag = PullToClose.follow(dx, dy, dragBase / d) * d
                                    change.consume()
                                }

                                StageDrag.None -> {
                                    zoom = zoomNow.pan(change.positionChange(), fitNow)
                                    change.consume()
                                }

                                StageDrag.Undecided -> {}
                            }
                        }
                        val dx = (last.position.x - down.position.x) / d
                        val dy = (last.position.y - down.position.y) / d
                        when (mode) {
                            StageDrag.Sideways -> {
                                val to = ViewerSwipe.turn(slide / d, speedX(trail), w / d)
                                if (to != null && neighbour(to) != null) {
                                    turn(to)
                                } else {
                                    settling =
                                        scope.launch {
                                            animate(slide, 0f, animationSpec = tween(250, easing = StarEasing)) { v, _ -> slide = v }
                                        }
                                }
                            }

                            StageDrag.Down -> {
                                if (PullToClose.closes(dy, speedY(trail), h / d)) {
                                    if (fullscreen) {
                                        onFullscreen(false)
                                        drag = Offset.Zero
                                    } else {
                                        onClose()
                                    }
                                } else {
                                    val from = drag
                                    settling =
                                        scope.launch {
                                            animate(0f, 1f, animationSpec = tween(250, easing = StarEasing)) { t, _ ->
                                                drag =
                                                    from * (1f - t)
                                            }
                                        }
                                }
                            }

                            StageDrag.Undecided -> {
                                val quick = last.uptimeMillis - down.uptimeMillis < DOUBLE_TAP_MS
                                if (!pinched && quick && !consumedUp && hypot(dx, dy) < ViewerSwipe.SLOP) {
                                    val at = down.position
                                    val again = down.uptimeMillis - lastTap < DOUBLE_TAP_MS && (at - lastTapAt).getDistance() / d < 30f
                                    if (again && now.post?.kind != MediaKind.VIDEO) {
                                        single?.cancel()
                                        zoom = zoomNow.toggleAt(at - Offset(w / 2f, h / 2f), fitNow)
                                        lastTap = 0L
                                    } else {
                                        lastTap = down.uptimeMillis
                                        lastTapAt = at
                                        single?.cancel()
                                        if (now.post?.kind != MediaKind.VIDEO) {
                                            single =
                                                scope.launch {
                                                    delay(SINGLE_TAP_WAIT_MS)
                                                    onFullscreen(!fullscreen)
                                                }
                                        }
                                    }
                                }
                            }

                            StageDrag.None -> {}
                        }
                    }
                },
        ) {
            if (!fullscreen) post?.let { Glow(it) }
            // The previous, current and next pictures side by side, moved
            // together by the slide.
            listOf(-1 to shown.prev, 0 to post, 1 to shown.next).forEach { (place, p) ->
                if (p == null) return@forEach
                androidx.compose.runtime.key(p.id) {
                    val slotFit = fitted(p, w, h)
                    val current = place == 0
                    val scale = if (current) PullToClose.scale(pullAmount) else 1f
                    Box(
                        Modifier
                            .fillMaxSize()
                            .offset {
                                val x = slide + place * (w + gap) + if (current) drag.x else 0f
                                IntOffset(x.roundToInt(), if (current) drag.y.roundToInt() else 0)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Picture(
                            post = p,
                            fit = Size(slotFit.width * scale, slotFit.height * scale),
                            zoom = if (current) zoom else Zoom(),
                            playing = current && p.kind == MediaKind.VIDEO,
                            corner = if (fullscreen) 0.dp else 28.dp,
                        )
                    }
                }
            }
            if (post == null) {
                if (shown.error == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }

        CompositionLocalProvider(LocalGlassSource provides stageGlass) {
            // The stage's controls, fading as the picture is pulled away.
            val controlsAlpha = (1f - 2f * pullAmount).coerceIn(0f, 1f)
            val toolsRight = hand != Hand.Left
            Controls(
                visible = !fullscreen,
                alpha = controlsAlpha,
                toolsRight = toolsRight,
                zoom = zoom,
                onBack = onClose,
                onZoom = { factor -> zoom = zoom.zoomAt(factor, Offset.Zero, fit) },
                onFit = { zoom = Zoom() },
                onFullscreen = { onFullscreen(true) },
            )
            if (arrows && !fullscreen) {
                if (shown.neighbors?.prev != null) {
                    Arrow(R.drawable.ic_chevron_left, stringResource(R.string.post_prev), Modifier.align(Alignment.CenterStart)) {
                        if (shown.prev != null) turn(Turn.Previous)
                    }
                }
                if (shown.neighbors?.next != null) {
                    Arrow(R.drawable.ic_chevron_right, stringResource(R.string.post_next), Modifier.align(Alignment.CenterEnd)) {
                        if (shown.next != null) turn(Turn.Next)
                    }
                }
            }
        }
    }
}

/**
 * The picture's colours spread behind it, as the web's glow: its thumbnail
 * blurred to nothing but colour, fading out towards the bottom. Blur needs
 * Android 12, so older versions go without.
 */
@Composable
private fun Glow(post: Post) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || LocalPlainGlass.current) return
    val context = LocalContext.current
    val request = remember(post.id) { Media.thumb(context, post) } ?: return
    val alpha = if (StarBooruTheme.roles.canvas.luminance() > 0.5f) 0.42f else 0.34f
    Crossfade(request, animationSpec = tween(640), label = "glow") { r ->
        AsyncImage(
            model = r,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.alpha = alpha
                        compositingStrategy = CompositingStrategy.Offscreen
                    }.blur(64.dp, BlurredEdgeTreatment.Unbounded)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(0.4f to Color.Black, 1f to Color.Transparent),
                            blendMode = BlendMode.DstIn,
                        )
                    },
        )
    }
}

/** The size a post's picture takes at fit on a stage [w] by [h] pixels. */
private fun fitted(
    post: Post?,
    w: Float,
    h: Float,
): Size {
    val pw = post?.width?.toFloat()?.takeIf { it > 0f } ?: return Size(w, h)
    val ph = post.height?.toFloat()?.takeIf { it > 0f } ?: return Size(w, h)
    val k = min(w / pw, h / ph)
    return Size(pw * k, ph * k)
}

@Composable
private fun Picture(
    post: Post,
    fit: Size,
    zoom: Zoom,
    playing: Boolean,
    corner: Dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val roles = StarBooruTheme.roles
    val width = with(density) { fit.width.toDp() }
    val height = with(density) { fit.height.toDp() }
    Box(
        Modifier
            .size(width, height)
            .sharedPost(post.id, corner)
            .graphicsLayer {
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offset.x
                translationY = zoom.offset.y
            }.clip(RoundedCornerShape(if (zoom.isZoomed) 0.dp else corner))
            .background(post.tintColor() ?: roles.surface2),
    ) {
        if (post.kind == MediaKind.VIDEO && playing) {
            VideoPlayer(post, Modifier.fillMaxSize())
        } else {
            val thumbKey = "thumb:${post.sha256}"
            val sampleKey = "sample:${post.sha256}"
            // The original once zoomed in, else the smaller copy; whatever
            // showed before stands in while the next one loads.
            val original = zoom.isZoomed || post.kind == MediaKind.ANIMATION || post.sampleUrl == null
            val url = if (post.kind ==
                MediaKind.VIDEO
            ) {
                post.thumbUrl ?: post.fileUrl
            } else if (original) {
                post.fileUrl
            } else {
                post.sampleUrl ?: post.fileUrl
            }
            val key =
                when {
                    post.kind == MediaKind.VIDEO -> thumbKey
                    original -> "original:${post.sha256}"
                    else -> sampleKey
                }
            val request =
                remember(url, key) {
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .diskCacheKey(key)
                        .memoryCacheKey(key)
                        .placeholderMemoryCacheKey(
                            if (original && post.sampleUrl != null &&
                                post.kind == MediaKind.IMAGE
                            ) {
                                sampleKey
                            } else {
                                thumbKey
                            },
                        )
                        .build()
                }
            AsyncImage(
                model = request,
                contentDescription = stringResource(R.string.post_alt, post.id.toString()),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (post.kind == MediaKind.VIDEO) {
                Icon(
                    painterResource(R.drawable.ic_play),
                    null,
                    tint = roles.onScrim,
                    modifier = Modifier.align(Alignment.Center).size(48.dp),
                )
            }
        }
    }
}

@Composable
private fun Controls(
    visible: Boolean,
    alpha: Float,
    toolsRight: Boolean,
    zoom: Zoom,
    onBack: () -> Unit,
    onZoom: (Float) -> Unit,
    onFit: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val roles = StarBooruTheme.roles
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(12.dp).graphicsLayer { this.alpha = alpha }) {
            Box(
                Modifier
                    .align(if (toolsRight) AbsoluteAlignment.TopLeft else AbsoluteAlignment.TopRight)
                    .size(44.dp)
                    .glass(CircleShape, roles)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.post_back))
            }
            Row(
                Modifier
                    .align(if (toolsRight) AbsoluteAlignment.TopRight else AbsoluteAlignment.TopLeft)
                    .glass(CircleShape, roles)
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onZoom(1f / Zoom.STEP) }, enabled = zoom.isZoomed, modifier = Modifier.size(40.dp)) {
                    Icon(painterResource(R.drawable.ic_zoom_out), stringResource(R.string.zoom_out), Modifier.size(20.dp))
                }
                Box(
                    Modifier
                        .width(52.dp)
                        .height(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onFit),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (zoom.isZoomed) "${zoom.percent}%" else stringResource(R.string.zoom_fit),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(onClick = { onZoom(Zoom.STEP) }, enabled = zoom.scale < Zoom.MAX_SCALE, modifier = Modifier.size(40.dp)) {
                    Icon(painterResource(R.drawable.ic_zoom_in), stringResource(R.string.zoom_in), Modifier.size(20.dp))
                }
                IconButton(onClick = onFullscreen, modifier = Modifier.size(40.dp)) {
                    Icon(painterResource(R.drawable.ic_fullscreen), stringResource(R.string.fullscreen), Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun Arrow(
    icon: Int,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .padding(12.dp)
            .size(52.dp)
            .glass(CircleShape, StarBooruTheme.roles)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), label, Modifier.size(32.dp))
    }
}

@Composable
private fun Problem(
    text: String,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

/** Hides the status and navigation bars while [hidden]; a swipe from an edge shows them for a moment. */
@Composable
private fun SystemBars(hidden: Boolean) {
    val view = LocalView.current
    val window = (view.context.findActivity())?.window ?: return
    DisposableEffect(hidden, window) {
        val controller = WindowCompat.getInsetsController(window, view)
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
