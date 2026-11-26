package eu.filipnone.starbooru.ui.settings

import android.os.Build
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.filipnone.starbooru.BuildConfig
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.coreVersion
import eu.filipnone.starbooru.core.shownServer
import eu.filipnone.starbooru.data.AccountState
import eu.filipnone.starbooru.data.Hand
import eu.filipnone.starbooru.data.MediaCache
import eu.filipnone.starbooru.data.Settings
import eu.filipnone.starbooru.data.ThemeMode
import eu.filipnone.starbooru.ui.common.fileSize
import eu.filipnone.starbooru.ui.frame.LocalFrame
import eu.filipnone.starbooru.ui.gallery.actionIcon
import eu.filipnone.starbooru.ui.gestures.SwipeAction
import eu.filipnone.starbooru.ui.theme.SWATCHES
import eu.filipnone.starbooru.ui.theme.Seed
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import eu.filipnone.starbooru.ui.theme.StarSpringEasing
import eu.filipnone.starbooru.ui.theme.swatchArgb
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: Settings,
    account: AccountState,
    serverVersion: String?,
    onChange: ((Settings) -> Settings) -> Unit,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    onTrash: () -> Unit,
) {
    val frame = LocalFrame.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(frame.padding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Card(stringResource(R.string.settings_appearance)) {
                Label(stringResource(R.string.settings_theme))
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
                Label(stringResource(R.string.settings_accent))
                Accents(settings, onChange)
            }
            Card(stringResource(R.string.settings_hand)) {
                Choice(
                    listOf(
                        Hand.Either to R.string.hand_either,
                        Hand.Left to R.string.hand_left,
                        Hand.Right to R.string.hand_right,
                    ),
                    settings.hand,
                ) { h -> onChange { it.copy(hand = h) } }
                Text(
                    stringResource(R.string.settings_hand_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Card(stringResource(R.string.settings_gestures)) {
                SwipeChoice(stringResource(R.string.settings_swipe_left), settings.swipeLeft) { a -> onChange { it.copy(swipeLeft = a) } }
                SwipeChoice(stringResource(R.string.settings_swipe_right), settings.swipeRight) { a ->
                    onChange { it.copy(swipeRight = a) }
                }
            }
            if (account is AccountState.SignedIn) {
                Card(stringResource(R.string.settings_library)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onTrash)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text(stringResource(R.string.trash_title), style = MaterialTheme.typography.bodyLarge)
                            Hint(stringResource(R.string.settings_trash_note))
                        }
                        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            CacheCard(settings, onChange)
            Card(stringResource(R.string.settings_account)) {
                when (account) {
                    is AccountState.SignedIn -> {
                        Text(stringResource(R.string.settings_signed_in_on, account.user.username, shownServer(account.server)))
                        serverVersion?.let { Hint(stringResource(R.string.settings_server_version) + " " + it) }
                        FilledTonalButton(onClick = onSignOut) {
                            Icon(painterResource(R.drawable.ic_logout), null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.settings_sign_out))
                        }
                    }

                    is AccountState.Guest -> {
                        Text(stringResource(R.string.settings_guest_on, shownServer(account.server)))
                        FilledTonalButton(onClick = onSignIn) { Text(stringResource(R.string.guest_sign_in)) }
                    }

                    else -> {}
                }
            }
            Card(stringResource(R.string.settings_about)) {
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
                Hint("core ${coreVersion()}")
            }
        }
    }
}

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
