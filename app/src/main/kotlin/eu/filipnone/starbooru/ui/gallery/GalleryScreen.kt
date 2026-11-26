package eu.filipnone.starbooru.ui.gallery

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
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
import eu.filipnone.starbooru.core.BulkEdit
import eu.filipnone.starbooru.core.BulkResult
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.GalleryView
import eu.filipnone.starbooru.core.MediaKind
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.core.SavedSearch
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
import eu.filipnone.starbooru.data.Account
import eu.filipnone.starbooru.data.SavedSearches
import eu.filipnone.starbooru.data.Settings
import eu.filipnone.starbooru.ui.common.LocalNotices
import eu.filipnone.starbooru.ui.common.LocalReducedMotion
import eu.filipnone.starbooru.ui.common.LocalViewerFocus
import eu.filipnone.starbooru.ui.common.Media
import eu.filipnone.starbooru.ui.common.clock
import eu.filipnone.starbooru.ui.common.describe
import eu.filipnone.starbooru.ui.common.rememberPostActions
import eu.filipnone.starbooru.ui.common.sharedPost
import eu.filipnone.starbooru.ui.common.tintColor
import eu.filipnone.starbooru.ui.frame.LocalFrame
import eu.filipnone.starbooru.ui.frame.LocalGlassSource
import eu.filipnone.starbooru.ui.frame.WidthClass
import eu.filipnone.starbooru.ui.frame.glass
import eu.filipnone.starbooru.ui.gestures.Side
import eu.filipnone.starbooru.ui.gestures.SwipeAction
import eu.filipnone.starbooru.ui.gestures.TileSwipe
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import eu.filipnone.starbooru.ui.theme.StarSpringEasing
import kotlin.math.floor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val GAP_DP = 8.0

@Composable
fun GalleryScreen(
    vm: GalleryViewModel,
    account: Account,
    savedSearches: SavedSearches,
    server: String,
    settings: Settings,
    onSettings: ((Settings) -> Settings) -> Unit,
    onOpen: (Post) -> Unit,
    onSearch: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val selecting by vm.selecting.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val actions = rememberPostActions(account) { vm.replace(it) }

    // What a tile's menu or a swipe does to one post.
    fun run(
        choice: TileChoice,
        post: Post,
    ) {
        when (choice) {
            TileChoice.Open -> onOpen(post)

            TileChoice.Like -> actions.like(post)

            TileChoice.Select -> {
                vm.select(true)
                vm.setSelection(vm.selection.value.toggle(post.id))
            }

            TileChoice.Share -> actions.share(listOf(post))

            TileChoice.Download -> actions.download(listOf(post))

            TileChoice.Delete -> {
                var gone = emptyList<Pair<Int, Post>>()
                actions.delete(post, onGone = { gone = vm.remove(setOf(post.id)) }, onBack = { back ->
                    vm.putBack(
                        gone.map {
                            it.first to
                                back
                        },
                    )
                })
            }
        }
    }
    val haptics = LocalHapticFeedback.current
    var menuFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val touch = remember { GridTouch() }
    val res = LocalResources.current
    val notices = LocalNotices.current
    val frame = LocalFrame.current
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = LocalViewerFocus.current
    val reduced = LocalReducedMotion.current
    val side = if (frame.width == WidthClass.Compact) 8.dp else 12.dp
    val savedList by savedSearches.list.collectAsStateWithLifecycle()
    // The bookmark saves the search, or forgets it once saved.
    val savedSearch = remember(savedList, vm.query) { savedSearches.find(vm.query) }
    val bookmark: (() -> Unit)? =
        if (vm.query.isBlank() || actions.guest) {
            null
        } else {
            {
                scope.launch {
                    try {
                        val was = savedSearch
                        if (was != null) {
                            savedSearches.delete(was)
                            notices?.say(res.getString(R.string.saved_removed, was.name))
                        } else {
                            val made = savedSearches.save(vm.query)
                            notices?.say(res.getString(R.string.saved_added, made.name))
                        }
                    } catch (e: CoreException) {
                        notices?.say(res.getString(R.string.search_save_failed, describe(e, res, server)))
                    }
                }
            }
        }

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
                    gap,
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

        val visibleNow by rememberUpdatedState(visible)
        val layoutNow by rememberUpdatedState(layout)
        val postsNow by rememberUpdatedState(state.posts)
        val selectingNow by rememberUpdatedState(selecting)
        val settingsNow by rememberUpdatedState(settings)
        val host =
            remember {
                object : GridHost {
                    override fun tileAt(at: Offset): Pair<Long, Float>? {
                        val x = at.x / pxPerDp
                        val y = at.y / pxPerDp
                        val tiles = layoutNow.tiles
                        val i =
                            visibleNow.firstOrNull { i ->
                                val t = tiles.getOrNull(i) ?: return@firstOrNull false
                                x >= t.x && x < t.x + t.width && y >= t.y && y < t.y + t.height
                            } ?: return null
                        val id = postsNow.getOrNull(i)?.id ?: return null
                        return id to (tiles.getOrNull(i)?.width?.toFloat() ?: 0f)
                    }

                    override val selecting: Boolean get() = selectingNow

                    override fun canSwipe(side: Side): Boolean =
                        TileSwipe.action(side, settingsNow.swipeLeft, settingsNow.swipeRight) != SwipeAction.Off

                    override fun tap(id: Long) {
                        if (selectingNow) {
                            vm.setSelection(vm.selection.value.toggle(id))
                        } else {
                            postsNow.firstOrNull { it.id == id }?.let(onOpen)
                        }
                    }

                    override fun hold(id: Long) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuFor = id
                    }

                    override fun swiped(
                        id: Long,
                        side: Side,
                    ) {
                        val post = postsNow.firstOrNull { it.id == id } ?: return
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        when (TileSwipe.action(side, settingsNow.swipeLeft, settingsNow.swipeRight)) {
                            SwipeAction.Like -> run(TileChoice.Like, post)
                            SwipeAction.Select -> run(TileChoice.Select, post)
                            SwipeAction.Share -> run(TileChoice.Share, post)
                            SwipeAction.Download -> run(TileChoice.Download, post)
                            SwipeAction.Delete -> run(TileChoice.Delete, post)
                            SwipeAction.Off -> {}
                        }
                    }

                    private var dragging: DragPick? = null

                    override fun drag(
                        from: Long,
                        to: Long,
                    ) {
                        val pick = dragging?.copy(to = to) ?: DragPick(vm.selection.value, from, to)
                        dragging = pick
                        pick.selection(vm.ids())?.let { vm.setSelection(it) }
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }

                    override fun dragEnded() {
                        dragging = null
                    }

                    override fun edgeSpeed(at: Offset): Float {
                        val y = at.y + gridTop - scroll.value
                        return DragPick.edgeSpeed(y, topPadPx, viewportPx - frame.padding.calculateBottomPadding().value * pxPerDp)
                    }

                    override fun scrollBy(px: Float): Float = scroll.dispatchRawDelta(px)
                }
            }

        // Pages come as the end nears, a screen and a half ahead.
        LaunchedEffect(layout, viewportPx) {
            snapshotFlow { scroll.value + viewportPx * 2.5f >= gridTop + layout.height.toFloat() * pxPerDp }
                .distinctUntilChanged()
                .collect { near -> if (near) vm.loadMore() }
        }

        var placedShape by rememberSaveable { mutableStateOf<String?>(null) }
        // The post at the top of the screen, under the bar, to come back to
        // after a resize or the system closing the app. None while the page
        // is at its top, which is where it comes back to then.
        LaunchedEffect(layout) {
            snapshotFlow { scroll.value }.collect { y ->
                // Not before the page has gone back to where it was.
                if (placedShape == null) return@collect
                val line = (y + topPadPx - gridTop) / pxPerDp
                val i = if (line <= 0f) -1 else layout.tiles.indexOfFirst { it.y + it.height > line }
                vm.anchor = state.posts.getOrNull(i)?.id
            }
        }
        val shape = Triple(columns, widthDp, settings.galleryView)
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
                .padding(horizontal = side),
        ) {
            Header(
                query = vm.query,
                settings = settings,
                widthDp = widthDp,
                selecting = selecting,
                onSelect = vm::select,
                savedSearch = savedSearch,
                onBookmark = bookmark,
                onSort = { onSearch(sorted(vm.query, it)) },
                onSettings = onSettings,
            )
            if (state.corrected.isNotEmpty()) {
                Text(
                    stringResource(R.string.search_corrected, state.corrected.joinToString { "${it.from} → ${it.to}" }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(layout.height.toFloat().dp)
                    .onPlaced { gridTop = it.positionInParent().y + topPadPx }
                    .gridGestures(host, touch, scope, pxPerDp),
            ) {
                for (i in visible) {
                    val post = state.posts.getOrNull(i) ?: continue
                    val rect = layout.tiles.getOrNull(i) ?: continue
                    androidx.compose.runtime.key(post.id) {
                        val swiping = touch.swiped == post.id
                        val slide = if (swiping) touch.slide else 0f
                        Tile(
                            post = post,
                            index = i,
                            flush = settings.flush,
                            animate = !reduced,
                            pressed = touch.pressed == post.id,
                            slide = slide,
                            swipe = if (slide > 0f) settings.swipeRight else settings.swipeLeft,
                            armed = swiping && touch.armed,
                            selecting = selecting,
                            picked = post.id in selection,
                            menu = menuFor == post.id,
                            liked = actions.liked(post),
                            canDelete = !actions.guest,
                            onMenu = { choice ->
                                menuFor = null
                                if (choice != null) run(choice, post) else menuFor = post.id
                            },
                            onOpen = { onOpen(post) },
                            onToggle = { vm.setSelection(selection.toggle(post.id)) },
                            modifier =
                                Modifier
                                    .offset { IntOffset((rect.x * pxPerDp).toInt(), (rect.y * pxPerDp).toInt()) }
                                    .size(rect.width.toFloat().dp, rect.height.toFloat().dp),
                        )
                    }
                }
            }
            Footer(state, server, onRetry = vm::loadMore, onReload = vm::reload, query = vm.query)
        }

        val showTop by remember { androidx.compose.runtime.derivedStateOf { scroll.value > viewportPx && !vm.selecting.value } }
        val topAlign = if (frame.rightHanded ||
            frame.hand == eu.filipnone.starbooru.data.Hand.Either
        ) {
            AbsoluteAlignment.BottomRight
        } else {
            AbsoluteAlignment.BottomLeft
        }
        CompositionLocalProvider(LocalGlassSource provides glass) {
            AnimatedVisibility(
                visible = selecting,
                enter = fadeIn() + androidx.compose.animation.slideInVertically { it },
                exit = fadeOut() + androidx.compose.animation.slideOutVertically { it },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = frame.padding.calculateBottomPadding() + 8.dp, start = 16.dp, end = 16.dp),
            ) {
                val ids = state.posts.map { it.id }
                BulkBar(
                    count = selection.size,
                    full = selection.isFull(ids),
                    canDelete = !actions.guest,
                    onClose = { vm.select(false) },
                    onAll = { vm.setSelection(selection.toggleAll(ids)) },
                    onEdit = { editing = true },
                    onShare = { actions.share(picked(state.posts, selection)) },
                    onDownload = { actions.download(picked(state.posts, selection)) },
                    onDelete = {
                        scope.launch {
                            val chosen = selection.inOrder(ids)
                            try {
                                val result = account.call { it.bulkEdit(BulkEdit(ids = chosen, delete = true)) }
                                val gone = vm.remove(chosen.toSet())
                                vm.select(false)
                                notices?.offer(bulkReport(res, result, deleting = true), res.getString(R.string.press_undo)) {
                                    scope.launch {
                                        val back = mutableListOf<Pair<Int, Post>>()
                                        var failed = 0
                                        var why: Throwable? = null
                                        for ((index, post) in gone) {
                                            try {
                                                back += index to account.call { it.restorePost(post.id) }
                                            } catch (e: CoreException) {
                                                // A post that was never moved can't come back; that's no news.
                                                if ((e as? CoreException.Api)?.status?.toInt() != 403) {
                                                    failed++
                                                    why = e
                                                }
                                            }
                                        }
                                        vm.putBack(back)
                                        why?.let {
                                            notices.say(
                                                res.getQuantityString(
                                                    R.plurals.bulk_restore_failed,
                                                    failed,
                                                    failed,
                                                    describe(it, res, server),
                                                ),
                                            )
                                        }
                                    }
                                }
                            } catch (e: CoreException) {
                                notices?.say(res.getString(R.string.bulk_delete_failed, describe(e, res, server)))
                            }
                        }
                    },
                )
            }
            AnimatedVisibility(
                visible = showTop,
                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                exit = fadeOut() + scaleOut(targetScale = 0.6f),
                modifier =
                    Modifier
                        .align(topAlign)
                        .padding(frame.padding)
                        .padding(16.dp),
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .glass(CircleShape, StarBooruTheme.roles)
                        .clickable { scope.launch { scroll.animateScrollTo(0) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.gallery_to_top))
                }
            }
        }
    }
    BackHandler(enabled = selecting) { vm.select(false) }
    if (editing) {
        BulkSheet(
            count = selection.size,
            onDismiss = { editing = false },
            onApply = { change ->
                editing = false
                scope.launch {
                    try {
                        val result =
                            account.call {
                                it.bulkEdit(
                                    BulkEdit(
                                        ids = selection.inOrder(vm.ids()),
                                        addTags = change.add,
                                        removeTags = change.remove,
                                        rating = change.rating,
                                    ),
                                )
                            }
                        vm.select(false)
                        notices?.say(bulkReport(res, result, deleting = false))
                    } catch (e: CoreException) {
                        notices?.say(describe(e, res, server))
                    }
                }
            },
        )
    }
}

/** The picked posts, in the gallery's order. */
private fun picked(
    posts: List<Post>,
    selection: Selection,
): List<Post> = posts.filter { it.id in selection }

/** What a mass edit did, in a sentence. */
internal fun bulkReport(
    res: android.content.res.Resources,
    result: BulkResult,
    deleting: Boolean,
): String {
    val done = result.updated.toInt()
    val skipped = result.skipped.toInt()
    val did = res.getQuantityString(if (deleting) R.plurals.bulk_moved else R.plurals.bulk_updated, done, done)
    if (skipped == 0) return res.getString(R.string.bulk_done, did)
    val left = res.getQuantityString(if (deleting) R.plurals.bulk_skipped_undeletable else R.plurals.bulk_skipped, skipped, skipped)
    return res.getString(R.string.bulk_done_skipped, did, left)
}

@Composable
private fun Header(
    query: String,
    settings: Settings,
    widthDp: Double,
    selecting: Boolean,
    onSelect: (Boolean) -> Unit,
    savedSearch: SavedSearch?,
    onBookmark: (() -> Unit)?,
    onSort: (String) -> Unit,
    onSettings: ((Settings) -> Settings) -> Unit,
) {
    val title = searchTitle(query)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.ifEmpty { stringResource(R.string.nav_gallery) },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        if (onBookmark != null) {
            androidx.compose.material3.IconButton(onClick = onBookmark) {
                Icon(
                    painterResource(if (savedSearch != null) R.drawable.ic_bookmark else R.drawable.ic_bookmark_add),
                    stringResource(if (savedSearch != null) R.string.saved_remove else R.string.search_save),
                    tint = if (savedSearch != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SortMenu(query, onSort)
        ViewMenu(settings, widthDp, onSettings)
        androidx.compose.material3.IconButton(onClick = { onSelect(!selecting) }) {
            Icon(
                painterResource(if (selecting) R.drawable.ic_close else R.drawable.ic_check),
                stringResource(if (selecting) R.string.gallery_stop_selecting else R.string.gallery_select),
            )
        }
    }
}

@Composable
private fun SortMenu(
    query: String,
    onSort: (String) -> Unit,
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
                    },
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
        },
    )

@Composable
private fun ViewMenu(
    settings: Settings,
    widthDp: Double,
    onSettings: ((Settings) -> Settings) -> Unit,
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
                            GalleryView.STAGGERED_PORTRAIT to R.string.view_staggered,
                        ),
                    selected = if (staggered) GalleryView.STAGGERED_PORTRAIT else settings.galleryView,
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
                                GalleryView.STAGGERED_ALL to R.string.view_everything,
                            ),
                        selected = settings.galleryView,
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
                        modifier = Modifier.weight(1f),
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
                        steps = (most - fewest - 1).coerceAtLeast(0),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSettings { it.copy(flush = !it.flush) } }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
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
    onPick: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onPick(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {},
            ) { Text(stringResource(label), maxLines = 1, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun Footer(
    state: GalleryState,
    server: String,
    query: String,
    onRetry: () -> Unit,
    onReload: () -> Unit,
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
                        modifier = Modifier.padding(horizontal = 16.dp),
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            state.hasMore -> FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.load_more)) }
        }
    }
}
