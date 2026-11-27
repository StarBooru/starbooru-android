package eu.filipnone.starbooru.ui.frame

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.SavedSearch
import eu.filipnone.starbooru.data.Hand
import eu.filipnone.starbooru.ui.common.LocalSharedTransition
import eu.filipnone.starbooru.ui.nav.Tab
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import eu.filipnone.starbooru.ui.theme.StarEasing
import eu.filipnone.starbooru.ui.theme.StarRoles
import eu.filipnone.starbooru.ui.theme.StarSpringEasing

private val BarHeight = 52.dp
private val BarItem = 52.dp
private val RailWidth = 76.dp
private val ShortRailWidth = 52.dp
private const val MOTION_SLOW = 460
private const val MOTION_QUICK = 240

/** One cell of the navigation. */
data class NavItem(
    val label: String,
    val icon: Int,
    val onClick: () -> Unit,
    /** Sign in, drawn in the primary colour. */
    val accent: Boolean = false,
)

/**
 * The app's frame: the search bar at the top, the navigation at the bottom
 * under 600 dp and as a floating rail on the hand's side from there, and
 * the screen behind them, edge to edge. Screens pad their scrolling content
 * by [LocalFrame]'s padding, so it passes under the bars.
 */
@Composable
fun Frame(
    tabs: List<Tab>,
    selected: Tab?,
    onSelect: (Tab) -> Unit,
    bars: Boolean,
    hand: Hand,
    search: String,
    onSearch: (String) -> Unit,
    onSubmit: (String) -> Unit,
    /** What the search box suggests, if anything; the frame draws it under the box. */
    hints: SearchHints?,
    savedSearches: List<SavedSearch>?,
    /** Opens the search cheat sheet. */
    onHelp: (() -> Unit)? = null,
    onBrand: () -> Unit,
    onSignIn: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val roles = StarBooruTheme.roles
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val width = WidthClass.of(maxWidth.value)
        val short = maxHeight < 500.dp
        val system = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
        val dir = LocalLayoutDirection.current
        val sysTop = system.calculateTopPadding()
        val sysBottom = system.calculateBottomPadding()
        val sysLeft = system.calculateLeftPadding(dir)
        val sysRight = system.calculateRightPadding(dir)
        val gap = if (width == WidthClass.Compact) 8.dp else 12.dp
        val railWidth = if (short) ShortRailWidth else RailWidth
        val right = hand == Hand.Right
        val railSpace = railWidth + 24.dp

        val padding =
            if (!bars) {
                system
            } else {
                val top = sysTop + gap + BarHeight + 8.dp
                when (width) {
                    WidthClass.Compact ->
                        PaddingValues(
                            start = if (dir == LayoutDirection.Ltr) sysLeft else sysRight,
                            end = if (dir == LayoutDirection.Ltr) sysRight else sysLeft,
                            top = top,
                            bottom = sysBottom + 12.dp + BarItem + 8.dp + 12.dp,
                        )

                    else -> {
                        val left = sysLeft + if (!right) railSpace else 0.dp
                        val rightPad = sysRight + if (right) railSpace else 0.dp
                        PaddingValues(
                            start = if (dir == LayoutDirection.Ltr) left else rightPad,
                            end = if (dir == LayoutDirection.Ltr) rightPad else left,
                            top = top,
                            bottom = sysBottom + 12.dp,
                        )
                    }
                }
            }

        val glassSource = rememberHazeState()
        CompositionLocalProvider(LocalFrame provides FrameInfo(width, hand, padding, short)) {
            Box(Modifier.fillMaxSize().hazeSource(glassSource)) { content() }
        }
        // A post's picture on its way between a tile and the viewer is drawn
        // above the pages: the bars go above it there too.
        val shared = LocalSharedTransition.current
        val onTop = shared?.run { Modifier.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f) } ?: Modifier
        // Keeps the status bar's icons readable over the pictures passing under it.
        if (bars && sysTop > 0.dp) {
            val canvas = MaterialTheme.colorScheme.background
            Box(
                onTop
                    .fillMaxWidth()
                    .height(sysTop + gap)
                    .background(Brush.verticalGradient(listOf(canvas.copy(alpha = 0.85f), canvas.copy(alpha = 0f)))),
            )
        }

        CompositionLocalProvider(LocalGlassSource provides glassSource) {
            val items =
                tabs.map { tab -> NavItem(stringResource(tab.label), tab.icon, { onSelect(tab) }) } +
                    listOfNotNull(
                        onSignIn?.let { NavItem(stringResource(R.string.guest_sign_in), R.drawable.ic_login, it, accent = true) },
                    )
            val selectedIndex = tabs.indexOf(selected)

            AnimatedVisibility(
                visible = bars,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250, easing = StarEasing)) { -it / 2 },
                exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 2 },
                modifier = onTop.align(Alignment.TopCenter),
            ) {
                TopBar(
                    width = width,
                    search = search,
                    onSearch = onSearch,
                    onSubmit = onSubmit,
                    hints = hints,
                    savedSearches = savedSearches,
                    onHelp = onHelp,
                    onBrand = onBrand,
                    modifier = Modifier.padding(top = sysTop + gap, start = sysLeft + gap, end = sysRight + gap),
                    roles = roles,
                )
            }

            if (width == WidthClass.Compact) {
                AnimatedVisibility(
                    visible = bars,
                    enter = fadeIn(tween(250)) + slideInVertically(tween(250, easing = StarEasing)) { it },
                    exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it },
                    modifier = onTop.align(Alignment.BottomCenter),
                ) {
                    NavSlider(
                        items = items,
                        selected = selectedIndex,
                        vertical = false,
                        labels = true,
                        roles = roles,
                        modifier =
                            Modifier
                                .padding(start = sysLeft + 12.dp, end = sysRight + 12.dp, bottom = sysBottom + 12.dp)
                                .widthIn(max = 480.dp)
                                .fillMaxWidth()
                                .height(BarItem + 8.dp),
                    )
                }
            } else {
                val railAlign = if (right) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft
                AnimatedVisibility(
                    visible = bars,
                    enter = fadeIn(tween(250)),
                    exit = fadeOut(tween(150)),
                    modifier = onTop.align(railAlign),
                ) {
                    val itemHeight = if (short) BarItem else RailWidth
                    NavSlider(
                        items = items,
                        selected = selectedIndex,
                        vertical = true,
                        labels = !short,
                        roles = roles,
                        modifier =
                            Modifier
                                .padding(
                                    top = sysTop + gap + BarHeight + gap,
                                    bottom = sysBottom + 12.dp,
                                ).padding(
                                    start = if (right) 0.dp else sysLeft + 12.dp,
                                    end = if (right) sysRight + 12.dp else 0.dp,
                                ).width(railWidth)
                                .height(itemHeight * items.size + 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    width: WidthClass,
    search: String,
    onSearch: (String) -> Unit,
    onSubmit: (String) -> Unit,
    hints: SearchHints?,
    savedSearches: List<SavedSearch>?,
    onHelp: (() -> Unit)?,
    onBrand: () -> Unit,
    roles: StarRoles,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        if (width != WidthClass.Compact) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .height(BarHeight)
                    .glass(CircleShape, roles)
                    .clickable(onClick = onBrand)
                    .padding(start = 12.dp, end = if (width == WidthClass.Expanded) 18.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_brand_mark_compact),
                    contentDescription = stringResource(R.string.app_name),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(30.dp).aspectRatio(512f / 436f),
                )
                if (width == WidthClass.Expanded) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painterResource(R.drawable.ic_brand_wordmark),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(19.dp).aspectRatio(648f / 103f),
                    )
                }
            }
        }
        val searchModifier =
            if (width == WidthClass.Compact) {
                Modifier.fillMaxWidth()
            } else {
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = if (width == WidthClass.Expanded) 200.dp else 64.dp)
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
            }
        SearchCapsule(
            text = search,
            onText = onSearch,
            onSubmit = onSubmit,
            hints = hints,
            savedSearches = savedSearches,
            onHelp = onHelp,
            leadingBrand = width == WidthClass.Compact,
            onBrand = onBrand,
            roles = roles,
            modifier = searchModifier,
        )
    }
}

@Composable
private fun SearchCapsule(
    text: String,
    onText: (String) -> Unit,
    onSubmit: (String) -> Unit,
    hints: SearchHints?,
    savedSearches: List<SavedSearch>?,
    onHelp: (() -> Unit)?,
    leadingBrand: Boolean,
    onBrand: () -> Unit,
    roles: StarRoles,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.search_label)
    val focus = LocalFocusManager.current
    // The caret is the box's own; the words come from outside, as pages change them.
    var value by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    if (value.text != text) value = TextFieldValue(text, TextRange(text.length))
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused, savedSearches) { hints?.typed(value, savedSearches, focused) }
    val submit = { q: String ->
        onSubmit(q)
        hints?.close()
        // The results take the screen the keyboard had.
        focus.clearFocus()
    }
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .glass(CircleShape, roles)
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingBrand) {
                IconButton(onClick = onBrand, modifier = Modifier.size(44.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_brand_mark_compact),
                        contentDescription = stringResource(R.string.app_name),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(28.dp).aspectRatio(512f / 436f),
                    )
                }
            } else {
                Icon(
                    painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp).size(24.dp),
                )
            }
            // The field takes the whole capsule, so a tap anywhere on it types.
            BasicTextField(
                value = value,
                onValueChange = {
                    value = it
                    if (it.text != text) onText(it.text)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { submit(value.text) }),
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onFocusChanged { focused = it.isFocused }
                        .semantics { contentDescription = label },
                decorationBox = { field ->
                    Box(Modifier.fillMaxSize().padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.text.isEmpty()) {
                            Text(
                                stringResource(R.string.search_placeholder),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                            )
                        }
                        field()
                    }
                },
            )
            if (value.text.isNotEmpty()) {
                IconButton(onClick = {
                    onText("")
                    submit("")
                }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.search_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            // The cheat sheet, where there's nothing to clear.
            if (value.text.isEmpty() && onHelp != null) {
                IconButton(onClick = onHelp, modifier = Modifier.size(44.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_help),
                        contentDescription = stringResource(R.string.help_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        if (hints != null) {
            SuggestionList(
                hints = hints.hints,
                onTag = { tag ->
                    hints.complete(value, tag.name)?.let {
                        value = it
                        onText(it.text)
                    }
                },
                onSaved = { saved ->
                    onText(saved.query)
                    submit(saved.query)
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * The navigation as a capsule of equal cells, with a thumb behind the open
 * one. Moving to another item, the thumb's leading edge goes first and its
 * trailing edge catches up, as on the web.
 */
@Composable
private fun NavSlider(
    items: List<NavItem>,
    selected: Int,
    vertical: Boolean,
    labels: Boolean,
    roles: StarRoles,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(if (vertical) 38.dp else 30.dp)
    BoxWithConstraints(modifier.glass(shape, roles).padding(4.dp)) {
        val count = items.size.coerceAtLeast(1)
        val length = if (vertical) maxHeight else maxWidth
        val cell = length / count
        var previous by remember { mutableIntStateOf(selected) }
        val forward = selected >= previous
        LaunchedEffect(selected) { previous = selected }
        val shown = selected >= 0
        val index = selected.coerceAtLeast(0)
        val start by animateDpAsState(cell * index, tween(if (forward) MOTION_SLOW else MOTION_QUICK, easing = StarEasing), label = "start")
        val end by animateDpAsState(
            cell * (index + 1),
            tween(if (forward) MOTION_QUICK else MOTION_SLOW, easing = StarEasing),
            label = "end",
        )
        val thumbAlpha by animateFloatAsState(if (shown) 1f else 0f, tween(250), label = "thumbAlpha")
        val thumbShape = RoundedCornerShape(if (vertical) 34.dp else 26.dp)
        Box(
            Modifier
                .graphicsLayer {
                    alpha = thumbAlpha
                    scaleX = 0.8f + 0.2f * thumbAlpha
                    scaleY = 0.8f + 0.2f * thumbAlpha
                }.then(
                    if (vertical) {
                        Modifier.offset(y = start).fillMaxWidth().height((end - start).coerceAtLeast(0.dp))
                    } else {
                        Modifier.offset(x = start).fillMaxHeight().width((end - start).coerceAtLeast(0.dp))
                    },
                ).shadow(2.dp, thumbShape)
                .clip(thumbShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.dp, roles.glassEdge, thumbShape),
        )
        val cells: @Composable (Modifier) -> Unit = { cellModifier ->
            items.forEachIndexed { i, item ->
                NavCell(item, i == selected, labels, cellModifier)
            }
        }
        if (vertical) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) { cells(Modifier.fillMaxWidth().height(cell)) }
        } else {
            Row(Modifier.fillMaxSize()) { cells(Modifier.fillMaxHeight().width(cell)) }
        }
    }
}

@Composable
private fun NavCell(
    item: NavItem,
    selected: Boolean,
    label: Boolean,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            pop.snapTo(0.6f)
            pop.animateTo(1f, tween(420, easing = StarSpringEasing))
        } else {
            // A pop cut short by the next tab must not leave the icon small.
            pop.snapTo(1f)
        }
    }
    val press by animateFloatAsState(if (pressed) 0.86f else 1f, tween(150), label = "press")
    val colour: Color =
        when {
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            item.accent -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Column(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = item.onClick)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(item.icon),
            contentDescription = if (label) null else item.label,
            tint = colour,
            modifier = Modifier.size(22.dp).scale(pop.value * press),
        )
        if (label) {
            Text(
                item.label,
                color = colour,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** The space a screen keeps clear for the frame, from [LocalFrame]. */
@Composable
fun framePadding(): PaddingValues = LocalFrame.current.padding
