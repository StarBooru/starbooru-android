package eu.filipnone.starbooru.ui.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.GalleryView
import eu.filipnone.starbooru.core.MediaKind
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.core.TileSize
import eu.filipnone.starbooru.core.currentSort
import eu.filipnone.starbooru.core.galleryColumnLimits
import eu.filipnone.starbooru.core.galleryColumns
import eu.filipnone.starbooru.core.galleryLayout
import eu.filipnone.starbooru.core.mostlyHidden
import eu.filipnone.starbooru.core.searchTitle
import eu.filipnone.starbooru.core.sorted
import eu.filipnone.starbooru.core.sorts
import eu.filipnone.starbooru.core.toMiddle
import eu.filipnone.starbooru.data.Settings
import eu.filipnone.starbooru.ui.common.LocalReducedMotion
import eu.filipnone.starbooru.ui.common.LocalViewerFocus
import eu.filipnone.starbooru.ui.common.Media
import eu.filipnone.starbooru.ui.common.clock
import eu.filipnone.starbooru.ui.common.describe
import eu.filipnone.starbooru.ui.common.sharedPost
import eu.filipnone.starbooru.ui.common.tintColor
import eu.filipnone.starbooru.ui.frame.LocalFrame
import eu.filipnone.starbooru.ui.frame.LocalGlassSource
import eu.filipnone.starbooru.ui.frame.WidthClass
import eu.filipnone.starbooru.ui.frame.glass
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import eu.filipnone.starbooru.ui.theme.StarSpringEasing
import kotlin.math.floor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val GAP_DP = 8.0

@Composable
fun GalleryScreen(
    vm: GalleryViewModel,
    server: String,
    settings: Settings,
    onSettings: ((Settings) -> Settings) -> Unit,
    onOpen: (Post) -> Unit,
    onSearch: (String) -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val frame = LocalFrame.current
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = LocalViewerFocus.current
    val reduced = LocalReducedMotion.current
    val side = if (frame.width == WidthClass.Compact) 8.dp else 12.dp

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportPx = constraints.maxHeight.toFloat()
        val widthDp = (
            maxWidth - side * 2 - frame.padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) -
                frame.padding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
            ).value.toDouble()
        val gap = if (settings.flush) 0.0 else GAP_DP
        val columns =
            galleryColumns(widthDp, gap, settings.galleryView, settings.columns?.coerceIn(1, 255)?.toUByte())
        val layout =
            remember(state.posts, columns, widthDp, settings.galleryView, gap) {
                galleryLayout(
                    settings.galleryView,
                    state.posts.map { TileSize(it.width, it.height) },
                    columns,
                    widthDp,
                    gap
                )
            }
        val pxPerDp = density.density
        val topPadPx = frame.padding.calculateTopPadding().value * pxPerDp
        // Where the grid starts within the scrolling page, in pixels.
        var gridTop by remember { mutableFloatStateOf(0f) }

        // The tiles within a screen and a half above and two and a half below.
        val window by remember(layout, viewportPx) {
            androidx.compose.runtime.derivedStateOf {
                val quarter = (viewportPx / 4f).coerceAtLeast(1f)
                val top = floor((scroll.value - gridTop) / quarter) * quarter
                (top - 1.5f * viewportPx) / pxPerDp to (top + 2.5f * viewportPx) / pxPerDp
            }
        }
        val visible =
            remember(layout, window) {
                val (from, to) = window
                layout.tiles.indices.filter { i ->
                    val t = layout.tiles[i]
                    t.y + t.height >= from && t.y <= to
                }
            }

        // Pages come as the end nears, a screen and a half ahead.
        LaunchedEffect(layout, viewportPx) {
            snapshotFlow { scroll.value + viewportPx * 2.5f >= gridTop + layout.height.toFloat() * pxPerDp }
                .distinctUntilChanged()
                .collect { near -> if (near) vm.loadMore() }
        }

        // The post at the top of the screen, under the bar, to come back to
        // after a resize or the system closing the app. None while the page
        // is at its top, which is where it comes back to then.
        LaunchedEffect(layout) {
            snapshotFlow { scroll.value }.collect { y ->
                val line = (y + topPadPx - gridTop) / pxPerDp
                val i = if (line <= 0f) -1 else layout.tiles.indexOfFirst { it.y + it.height > line }
                vm.anchor = state.posts.getOrNull(i)?.id
            }
        }
        val shape = Triple(columns, widthDp, settings.galleryView)
        var placedShape by rememberSaveable { mutableStateOf<String?>(null) }
        LaunchedEffect(shape, state.loaded) {
            val key = shape.toString()
            if (!state.loaded || state.posts.isEmpty()) return@LaunchedEffect
            val moved = placedShape != null && placedShape != key
            val restored = placedShape == null && scroll.value == 0
            if (moved || restored) {
                val i = state.posts.indexOfFirst { it.id == vm.anchor }
                val tile = layout.tiles.getOrNull(i)
                scroll.scrollTo(if (tile == null) 0 else (gridTop + tile.y.toFloat() * pxPerDp - topPadPx).toInt())
            }
            placedShape = key
        }
        // Back from the viewer on another post: its tile comes into sight.
        LaunchedEffect(focus.value, layout) {
            val id = focus.value ?: return@LaunchedEffect
            val i = state.posts.indexOfFirst { it.id == id }
            val t = layout.tiles.getOrNull(i) ?: return@LaunchedEffect
            val top = gridTop + t.y.toFloat() * pxPerDp
            val h = t.height.toFloat() * pxPerDp
            val from = scroll.value + frame.padding.calculateTopPadding().value * pxPerDp
            val to = scroll.value + viewportPx - frame.padding.calculateBottomPadding().value * pxPerDp
            if (mostlyHidden(top.toDouble(), h.toDouble(), from.toDouble(), to.toDouble())) {
                scroll.scrollBy(toMiddle(top.toDouble(), h.toDouble(), from.toDouble(), to.toDouble()).toFloat())
            }
            focus.value = null
        }

        val glass = rememberHazeState()
        Column(
            Modifier
                .fillMaxSize()
                .hazeSource(glass)
                .verticalScroll(scroll)
                .padding(frame.padding)
                .padding(horizontal = side)
        ) {
            Header(
                query = vm.query,
                settings = settings,
                widthDp = widthDp,
                onSort = { onSearch(sorted(vm.query, it)) },
                onSettings = onSettings
            )
            if (state.corrected.isNotEmpty()) {
                Text(
                    stringResource(R.string.search_corrected, state.corrected.joinToString { "${it.from} → ${it.to}" }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(layout.height.toFloat().dp)
                    .onPlaced { gridTop = it.positionInParent().y + topPadPx }
            ) {
                for (i in visible) {
                    val post = state.posts.getOrNull(i) ?: continue
                    val rect = layout.tiles.getOrNull(i) ?: continue
                    androidx.compose.runtime.key(post.id) {
                        Tile(
                            post = post,
                            index = i,
                            flush = settings.flush,
                            animate = !reduced,
                            onClick = { onOpen(post) },
                            modifier =
                                Modifier
                                    .offset { IntOffset((rect.x * pxPerDp).toInt(), (rect.y * pxPerDp).toInt()) }
                                    .size(rect.width.toFloat().dp, rect.height.toFloat().dp)
                        )
                    }
                }
            }
            Footer(state, server, onRetry = vm::loadMore, onReload = vm::reload, query = vm.query)
        }

        val showTop by remember { androidx.compose.runtime.derivedStateOf { scroll.value > viewportPx } }
        val topAlign = if (frame.rightHanded ||
            frame.hand == eu.filipnone.starbooru.data.Hand.Either
        ) {
            AbsoluteAlignment.BottomRight
        } else {
            AbsoluteAlignment.BottomLeft
        }
        CompositionLocalProvider(LocalGlassSource provides glass) {
            AnimatedVisibility(
                visible = showTop,
                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                exit = fadeOut() + scaleOut(targetScale = 0.6f),
                modifier =
                    Modifier
                        .align(topAlign)
                        .padding(frame.padding)
                        .padding(16.dp)
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .glass(CircleShape, StarBooruTheme.roles)
                        .clickable { scope.launch { scroll.animateScrollTo(0) } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.gallery_to_top))
                }
            }
        }
    }
}

@Composable
private fun Header(
    query: String,
    settings: Settings,
    widthDp: Double,
    onSort: (String) -> Unit,
    onSettings: ((Settings) -> Settings) -> Unit
) {
    val title = searchTitle(query)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title.ifEmpty { stringResource(R.string.nav_gallery) },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        SortMenu(query, onSort)
        ViewMenu(settings, widthDp, onSettings)
    }
}

@Composable
private fun SortMenu(
    query: String,
    onSort: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val current = currentSort(query)
    Box {
        TextButton(onClick = { open = true }) {
            Text(sortLabel(current))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(28.dp)) {
            for (value in sorts()) {
                DropdownMenuItem(
                    text = { Text(sortLabel(value), fontWeight = if (value == current) FontWeight.Bold else null) },
                    leadingIcon = {
                        if (value == current) Icon(painterResource(R.drawable.ic_check), null) else Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        open = false
                        if (value != current) onSort(value)
                    }
                )
            }
        }
    }
}

@Composable
private fun sortLabel(value: String): String =
    stringResource(
        when (value) {
            "old" -> R.string.sort_old
            "score" -> R.string.sort_score
            "updated" -> R.string.sort_updated
            "size" -> R.string.sort_size
            "size_asc" -> R.string.sort_size_asc
            "pixels" -> R.string.sort_pixels
            "longside" -> R.string.sort_longside
            "tags" -> R.string.sort_tags
            "tags_asc" -> R.string.sort_tags_asc
            "random" -> R.string.sort_random
            else -> R.string.sort_new
        }
    )

@Composable
private fun ViewMenu(
    settings: Settings,
    widthDp: Double,
    onSettings: ((Settings) -> Settings) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        androidx.compose.material3.IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_layout), stringResource(R.string.view_title))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).width(300.dp)) {
                Text(stringResource(R.string.view_shape), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                val staggered =
                    settings.galleryView in
                        setOf(GalleryView.STAGGERED_PORTRAIT, GalleryView.STAGGERED_LANDSCAPE, GalleryView.STAGGERED_ALL)
                Segmented(
                    options =
                        listOf(
                            GalleryView.SQUARE to R.string.view_square,
                            GalleryView.LANDSCAPE to R.string.view_landscape,
                            GalleryView.PORTRAIT to R.string.view_portrait,
                            GalleryView.STAGGERED_PORTRAIT to R.string.view_staggered
                        ),
                    selected = if (staggered) GalleryView.STAGGERED_PORTRAIT else settings.galleryView
                ) { v -> onSettings { it.copy(galleryView = if (v == GalleryView.STAGGERED_PORTRAIT && staggered) it.galleryView else v) } }
                if (staggered) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.view_staggered_by), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Segmented(
                        options =
                            listOf(
                                GalleryView.STAGGERED_PORTRAIT to R.string.view_portrait,
                                GalleryView.STAGGERED_LANDSCAPE to R.string.view_landscape,
                                GalleryView.STAGGERED_ALL to R.string.view_everything
                            ),
                        selected = settings.galleryView
                    ) { v -> onSettings { it.copy(galleryView = v) } }
                }
                Spacer(Modifier.height(12.dp))
                val limits = galleryColumnLimits(widthDp, GAP_DP, settings.galleryView)
                val fewest = limits.fewest.toInt()
                val most = limits.most.toInt()
                val auto = galleryColumns(widthDp, GAP_DP, settings.galleryView, null).toInt()
                val chosen = settings.columns?.coerceIn(fewest, most)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (chosen == null) {
                            stringResource(R.string.view_columns_auto)
                        } else {
                            pluralStringResource(R.plurals.view_columns, chosen, chosen)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (chosen != null) {
                        TextButton(onClick = {
                            onSettings { it.copy(columns = null) }
                        }) { Text(stringResource(R.string.view_columns_auto)) }
                    }
                }
                if (most > fewest) {
                    androidx.compose.material3.Slider(
                        value = (chosen ?: auto).toFloat(),
                        onValueChange = { v -> onSettings { it.copy(columns = v.toInt()) } },
                        valueRange = fewest.toFloat()..most.toFloat(),
                        steps = (most - fewest - 1).coerceAtLeast(0)
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSettings { it.copy(flush = !it.flush) } }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.view_flush), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = settings.flush, onCheckedChange = { f ->
                        onSettings { it.copy(flush = f) }
                    })
                }
            }
        }
    }
}

@Composable
private fun <T> Segmented(
    options: List<Pair<T, Int>>,
    selected: T,
    onPick: (T) -> Unit
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onPick(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {}
            ) { Text(stringResource(label), maxLines = 1, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun Tile(
    post: Post,
    index: Int,
    flush: Boolean,
    animate: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
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
    val description = stringResource(R.string.post_alt, post.id.toString())
    Box(
        modifier
            .graphicsLayer {
                val e = entrance.value
                alpha = e.coerceIn(0f, 1f)
                scaleX = 0.85f + 0.15f * e
                scaleY = 0.85f + 0.15f * e
            }.semantics { contentDescription = description }
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .sharedPost(post.id, corner)
                .clip(shape)
                .background(post.tintColor() ?: roles.surface2)
        ) {
            val request = Media.thumb(context, post)
            if (request != null) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (post.kind == MediaKind.VIDEO) {
                Icon(
                    painterResource(R.drawable.ic_play),
                    null,
                    tint = roles.onScrim,
                    modifier = Modifier.align(Alignment.Center).size(40.dp)
                )
            }
        }
        val badge =
            when (post.kind) {
                MediaKind.VIDEO -> post.durationMs?.let { clock(it) } ?: ""
                MediaKind.ANIMATION -> "GIF"
                MediaKind.IMAGE -> null
            }
        if (badge != null) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .heightIn(min = 24.dp)
                    .clip(CircleShape)
                    .background(roles.scrim)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (post.kind ==
                    MediaKind.VIDEO
                ) {
                    Icon(painterResource(R.drawable.ic_play), null, tint = roles.onScrim, modifier = Modifier.size(16.dp))
                }
                if (badge.isNotEmpty()) Text(badge, color = roles.onScrim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Footer(
    state: GalleryState,
    server: String,
    query: String,
    onRetry: () -> Unit,
    onReload: () -> Unit
) {
    Box(Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        val error = state.error
        when {
            error != null ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        describe(error, server),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = if (state.posts.isEmpty()) onReload else onRetry) { Text(stringResource(R.string.retry)) }
                }

            state.loading -> CircularProgressIndicator()

            state.loaded && state.posts.isEmpty() ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.gallery_empty), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(if (query.isBlank()) R.string.gallery_empty_body else R.string.gallery_try_fewer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            state.hasMore -> FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.load_more)) }
        }
    }
}
