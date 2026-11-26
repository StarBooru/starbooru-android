package eu.filipnone.starbooru.ui.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.filipnone.starbooru.BuildConfig
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.coreVersion
import eu.filipnone.starbooru.core.shownServer
import eu.filipnone.starbooru.data.AccountState
import eu.filipnone.starbooru.data.Effects
import eu.filipnone.starbooru.data.Hand
import eu.filipnone.starbooru.data.MediaCache
import eu.filipnone.starbooru.data.Saver
import eu.filipnone.starbooru.data.Settings
import eu.filipnone.starbooru.data.ThemeMode
import eu.filipnone.starbooru.ui.common.fileSize
import eu.filipnone.starbooru.ui.frame.FrameInfo
import eu.filipnone.starbooru.ui.frame.LocalFrame
import eu.filipnone.starbooru.ui.frame.WidthClass
import eu.filipnone.starbooru.ui.gallery.actionIcon
import eu.filipnone.starbooru.ui.gestures.SwipeAction
import eu.filipnone.starbooru.ui.theme.SWATCHES
import eu.filipnone.starbooru.ui.theme.Seed
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import eu.filipnone.starbooru.ui.theme.StarSpringEasing
import eu.filipnone.starbooru.ui.theme.swatchArgb
import kotlinx.coroutines.launch

/** What Settings needs from outside: the session and where its links lead. */
class SettingsActions(
    val onChange: ((Settings) -> Settings) -> Unit,
    val onSignOut: () -> Unit,
    val onSignIn: () -> Unit,
    val onTrash: () -> Unit,
    val onHelp: () -> Unit,
    val onPrivacy: () -> Unit,
    val onTerms: () -> Unit,
)

/**
 * Settings in the web's seven groups. A phone shows the list, then a group
 * on a page of its own; from 840 dp the list stays beside the group open.
 * The top bar's search narrows Settings to the cards that match.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    account: AccountState,
    serverVersion: String?,
    search: String,
    actions: SettingsActions,
) {
    val frame = LocalFrame.current
    val guest = account !is AccountState.SignedIn
    val groups = Group.shown(guest)
    val wide = frame.width == WidthClass.Expanded
    var open by rememberSaveable { mutableStateOf<Group?>(null) }
    val res = LocalResources.current
    val words = SettingsIndex.words(search)
    val found = remember(search, guest) { SettingsIndex.find(words, groups, guest) { res.getString(it) } }
    BackHandler(enabled = open != null && !wide && words.isEmpty()) { open = null }
    val body =
        @Composable { group: Group, cards: Set<String>? ->
            GroupBody(group, cards, settings, account, serverVersion, actions)
        }
    if (words.isNotEmpty()) {
        // What the search found, every group's cards in one page.
        Page(frame) {
            if (found.isEmpty()) {
                Hint(stringResource(R.string.settings_search_none))
            }
            found.forEach { f ->
                GroupTitle(f.group)
                body(f.group, f.cards)
            }
        }
        return
    }
    if (wide) {
        val shown = open ?: Group.Appearance
        val dir = LocalLayoutDirection.current
        Row(
            Modifier
                .fillMaxSize()
                .padding(
                    start = frame.padding.calculateStartPadding(dir),
                    end = frame.padding.calculateEndPadding(dir),
                ),
        ) {
            Column(
                Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(top = frame.padding.calculateTopPadding(), bottom = frame.padding.calculateBottomPadding())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.nav_settings),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                groups.forEach { g -> GroupLink(g, selected = g == shown, account) { open = g } }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(top = frame.padding.calculateTopPadding(), bottom = frame.padding.calculateBottomPadding())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GroupTitle(shown)
                body(shown, null)
            }
        }
        return
    }
    AnimatedContent(open, label = "settings") { g ->
        Page(frame) {
            if (g == null) {
                Text(
                    stringResource(R.string.nav_settings),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    groups.forEach { group -> GroupLink(group, selected = false, account) { open = group } }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { open = null }) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.page_back))
                    }
                    GroupTitle(g)
                }
                body(g, null)
            }
        }
    }
}

@Composable
private fun Page(
    frame: FrameInfo,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(frame.padding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
    }
}

@Composable
private fun GroupTitle(group: Group) {
    Text(
        stringResource(group.title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() },
    )
}

/** A group in the list: its icon, name and what's in it. */
@Composable
private fun GroupLink(
    group: Group,
    selected: Boolean,
    account: AccountState,
    onClick: () -> Unit,
) {
    // The account's group names who's signed in, where the others say what they hold.
    val note =
        when {
            group != Group.Account -> stringResource(group.note)

            account is AccountState.SignedIn -> stringResource(
                R.string.settings_signed_in_on,
                account.user.username,
                shownServer(account.server),
            )

            account is AccountState.Guest -> stringResource(R.string.settings_guest_on, shownServer(account.server))

            else -> stringResource(group.note)
        }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else StarBooruTheme.roles.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(group.icon), null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(stringResource(group.title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A group's cards; [cards] narrows them to the ones a search found. */
@Composable
private fun GroupBody(
    group: Group,
    cards: Set<String>?,
    settings: Settings,
    account: AccountState,
    serverVersion: String?,
    actions: SettingsActions,
) {
    fun has(id: String) = cards == null || id in cards
    val onChange = actions.onChange
    val signedIn = account is AccountState.SignedIn
    when (group) {
        Group.Account -> {
            if (has("account") || has("guest")) {
                Card(stringResource(R.string.settings_account)) {
                    when (account) {
                        is AccountState.SignedIn -> {
                            Text(stringResource(R.string.settings_signed_in_on, account.user.username, shownServer(account.server)))
                            serverVersion?.let { Hint(stringResource(R.string.settings_server_version) + " " + it) }
                            FilledTonalButton(onClick = actions.onSignOut) {
                                Icon(painterResource(R.drawable.ic_logout), null, Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.settings_sign_out))
                            }
                        }

                        is AccountState.Guest -> {
                            Text(stringResource(R.string.settings_guest_on, shownServer(account.server)))
                            Hint(stringResource(R.string.guest_account_why))
                            FilledTonalButton(onClick = actions.onSignIn) { Text(stringResource(R.string.guest_sign_in)) }
                        }

                        else -> {}
                    }
                }
            }
        }

        Group.Appearance -> {
            if (has("theme")) {
                Card(stringResource(R.string.settings_theme)) {
                    Choice(
                        listOf(
                            ThemeMode.System to R.string.theme_system,
                            ThemeMode.Light to R.string.theme_light,
                            ThemeMode.Dark to R.string.theme_dark,
                        ),
                        settings.theme,
                    ) { t -> onChange { it.copy(theme = t) } }
                    SwitchRow(
                        stringResource(R.string.settings_oled),
                        stringResource(R.string.settings_oled_hint),
                        settings.oled,
                    ) { o -> onChange { it.copy(oled = o) } }
                }
            }
            if (has("accent")) {
                Card(stringResource(R.string.settings_accent)) { Accents(settings, onChange) }
            }
            if (has("language")) LanguageCard()
        }

        Group.Viewing -> {
            if (has("thumbnails")) {
                Card(stringResource(R.string.settings_tiles)) {
                    Choice(
                        listOf(false to R.string.settings_tiles_spaced, true to R.string.settings_tiles_flush),
                        settings.flush,
                    ) { f -> onChange { it.copy(flush = f) } }
                    Hint(stringResource(R.string.settings_hold_hint))
                }
            }
            if (has("viewer")) {
                Card(stringResource(R.string.settings_viewer)) {
                    SwitchRow(
                        stringResource(R.string.settings_originals),
                        stringResource(R.string.settings_originals_note),
                        settings.originals,
                    ) { o -> onChange { it.copy(originals = o) } }
                }
            }
            if (signedIn && has("gestures")) {
                Card(stringResource(R.string.settings_gestures)) {
                    SwipeChoice(stringResource(R.string.settings_swipe_left), settings.swipeLeft) { a ->
                        onChange { it.copy(swipeLeft = a) }
                    }
                    SwipeChoice(stringResource(R.string.settings_swipe_right), settings.swipeRight) { a ->
                        onChange { it.copy(swipeRight = a) }
                    }
                }
            }
            if (has("hand")) {
                Card(stringResource(R.string.settings_hand)) {
                    Choice(
                        listOf(Hand.Either to R.string.hand_either, Hand.Left to R.string.hand_left, Hand.Right to R.string.hand_right),
                        settings.hand,
                    ) { h -> onChange { it.copy(hand = h) } }
                    Hint(stringResource(R.string.settings_hand_hint))
                }
            }
        }

        Group.Battery -> {
            if (has("battery-saver")) {
                Card(stringResource(R.string.settings_saver)) {
                    Choice(
                        listOf(
                            Saver.Auto to R.string.saver_auto,
                            Saver.Always to R.string.saver_always,
                            Saver.Never to R.string.saver_never,
                        ),
                        settings.saver,
                    ) { v -> onChange { it.copy(saver = v) } }
                    Hint(stringResource(R.string.settings_saver_auto))
                    Label(stringResource(R.string.settings_saver_does))
                    Bullets(listOf(R.string.saver_does_videos, R.string.saver_does_preload, R.string.saver_does_motion))
                }
            }
            if (has("effects")) {
                Card(stringResource(R.string.settings_effects)) {
                    Choice(
                        listOf(
                            Effects.Auto to R.string.effects_auto,
                            Effects.Full to R.string.effects_full,
                            Effects.Lite to R.string.effects_lite,
                        ),
                        settings.effects,
                    ) { v -> onChange { it.copy(effects = v) } }
                    Hint(stringResource(R.string.settings_effects_auto))
                    Label(stringResource(R.string.settings_effects_does))
                    Bullets(listOf(R.string.effects_does_glass, R.string.effects_does_thumbs, R.string.effects_does_glow))
                }
            }
            if (has("cache")) CacheCard(settings, onChange)
        }

        Group.Library -> {
            if (has("library")) {
                Card(stringResource(R.string.settings_library)) {
                    Link(
                        R.drawable.ic_delete,
                        stringResource(R.string.trash_title),
                        stringResource(R.string.settings_trash_note),
                        actions.onTrash,
                    )
                }
            }
        }

        Group.Sharing -> {
            Card(stringResource(R.string.settings_group_sharing)) { Hint(stringResource(R.string.soon_body)) }
        }

        Group.About -> {
            if (has("help")) {
                Card(stringResource(R.string.settings_help_legal)) {
                    Link(
                        R.drawable.ic_help,
                        stringResource(R.string.help_title),
                        stringResource(R.string.settings_help_note),
                        actions.onHelp,
                    )
                    Link(
                        R.drawable.ic_lock,
                        stringResource(R.string.privacy_title),
                        stringResource(R.string.settings_privacy_note),
                        actions.onPrivacy,
                    )
                    Link(
                        R.drawable.ic_article,
                        stringResource(R.string.terms_title),
                        stringResource(R.string.settings_terms_note),
                        actions.onTerms,
                    )
                }
            }
            if (has("about")) AboutCard()
        }
    }
}

@Composable
private fun Bullets(items: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach {
            Text(
                "•  " + stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A row that opens another page. */
@Composable
private fun Link(
    icon: Int,
    title: String,
    note: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Hint(note)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The app's language: Android keeps it per app from Android 13, and the system's below. */
@Composable
private fun LanguageCard() {
    val tags = listOf("" to R.string.settings_language_system, "en" to R.string.lang_english, "it" to R.string.lang_italian)
    val current = AppCompatLocales.current()
    Card(stringResource(R.string.settings_language)) {
        if (AppCompatLocales.supported) {
            Choice(tags, current) { tag -> AppCompatLocales.set(tag) }
        } else {
            Hint(stringResource(R.string.settings_language_system))
        }
    }
}

@Composable
private fun AboutCard() {
    val uri = LocalUriHandler.current
    Card(stringResource(R.string.about_title)) {
        Row {
            Text(stringResource(R.string.about_version), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${BuildConfig.VERSION_NAME} · core ${coreVersion()}")
        }
        Row {
            Text(stringResource(R.string.about_made_by), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.about_made_by_name))
        }
        Row {
            Text(stringResource(R.string.about_icons), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.about_icons_names))
        }
        Row {
            Text(stringResource(R.string.about_lettering), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.about_lettering_name))
        }
        FilledTonalButton(onClick = { runCatching { uri.openUri(REPOSITORY) } }) {
            Icon(painterResource(R.drawable.ic_github), null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.about_github))
        }
    }
}

private const val REPOSITORY = "https://github.com/StarBooru/starbooru-android"

/** What swiping a tile one way does: its icon and name, as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwipeChoice(
    title: String,
    chosen: SwipeAction,
    onPick: (SwipeAction) -> Unit,
) {
    Label(title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwipeAction.entries.forEach { action ->
            FilterChip(
                selected = chosen == action,
                onClick = { onPick(action) },
                leadingIcon = { Icon(painterResource(actionIcon(action)), null, Modifier.size(18.dp)) },
                label = {
                    Text(
                        stringResource(
                            when (action) {
                                SwipeAction.Off -> R.string.swipe_off
                                SwipeAction.Like -> R.string.swipe_like
                                SwipeAction.Select -> R.string.swipe_select
                                SwipeAction.Share -> R.string.share_button
                                SwipeAction.Download -> R.string.post_download
                                SwipeAction.Delete -> R.string.swipe_delete
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun CacheCard(
    settings: Settings,
    onChange: ((Settings) -> Settings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var used by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { used = MediaCache.size(context) }
    Card(stringResource(R.string.cache_title)) {
        Hint(stringResource(R.string.cache_hint))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.cache_used, used?.let { fileSize(it) } ?: "…"),
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        MediaCache.clear(context)
                        used = MediaCache.size(context)
                    }
                },
                enabled = (used ?: 0L) > 0L,
            ) { Text(stringResource(R.string.cache_clear)) }
        }
        Label(stringResource(R.string.cache_limit))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Settings.CACHE_CHOICES_MB.forEach { mb ->
                FilterChip(
                    selected = settings.cacheMb == mb,
                    onClick = { onChange { it.copy(cacheMb = mb) } },
                    label = {
                        Text(
                            if (mb == 0) {
                                stringResource(R.string.cache_off)
                            } else {
                                // Whole numbers: the choices are round.
                                if (mb < 1024) {
                                    stringResource(R.string.size_mb, mb.toString())
                                } else {
                                    stringResource(R.string.size_gb, (mb / 1024).toString())
                                }
                            },
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Accents(
    settings: Settings,
    onChange: ((Settings) -> Settings) -> Unit,
) {
    val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (dynamic) {
            Swatch(
                colour = MaterialTheme.colorScheme.primary,
                label = stringResource(R.string.accent_system),
                on = settings.wallpaperColours,
                wallpaper = true,
            ) { onChange { it.copy(wallpaperColours = true) } }
        }
        SWATCHES.zip(ACCENT_NAMES).forEach { (hue, name) ->
            Swatch(
                colour = Color(swatchArgb(hue)),
                label = stringResource(name),
                on = (!settings.wallpaperColours || !dynamic) && settings.seed == Seed.preset(hue),
            ) { onChange { it.copy(wallpaperColours = false, seed = Seed.preset(hue)) } }
        }
    }
}

private val ACCENT_NAMES =
    listOf(
        R.string.accent_violet,
        R.string.accent_pink,
        R.string.accent_coral,
        R.string.accent_amber,
        R.string.accent_green,
        R.string.accent_teal,
        R.string.accent_blue,
        R.string.accent_red,
    )

@Composable
private fun Swatch(
    colour: Color,
    label: String,
    on: Boolean,
    wallpaper: Boolean = false,
    onClick: () -> Unit,
) {
    val ring = MaterialTheme.colorScheme.onSurface
    val pop by animateFloatAsState(if (on) 1f else 0f, tween(420, easing = StarSpringEasing), label = "swatch")
    Box(
        Modifier
            .size(48.dp)
            .semantics {
                contentDescription = label
                selected = on
            }.clip(CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .then(if (on) Modifier.border(2.dp, ring, CircleShape) else Modifier)
            .padding(if (on) 5.dp else 4.dp)
            .clip(CircleShape)
            .background(colour),
        contentAlignment = Alignment.Center,
    ) {
        if (wallpaper) {
            Icon(painterResource(R.drawable.ic_palette), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
        } else if (pop > 0.01f) {
            Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.size(20.dp).scale(pop))
        }
    }
}

@Composable
private fun Card(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(StarBooruTheme.roles.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun <T> Choice(
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
            ) { Text(stringResource(label), maxLines = 1) }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Hint(hint)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
