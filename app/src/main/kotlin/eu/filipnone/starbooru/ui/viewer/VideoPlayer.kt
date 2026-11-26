package eu.filipnone.starbooru.ui.viewer

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberMuteButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.ui.common.clock
import eu.filipnone.starbooru.ui.frame.LocalGlassSource
import eu.filipnone.starbooru.ui.frame.glass
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import kotlinx.coroutines.delay

/** Videos up to a minute long loop unless told otherwise, as on the web. */
private const val LOOP_UNDER_MS = 60_000L

/** The controls rest after this long without a touch. */
private const val REST_AFTER_MS = 2_800L

/**
 * A video in the web's player: the picture, a glass pill along its foot
 * with play, the time, the seek bar, the length, mute and repeat, and a big
 * play button before it starts. It pauses whenever the app is hidden.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    post: Post,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {}
) {
    val context = LocalContext.current
    val player =
        remember(post.id) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(post.fileUrl))
                repeatMode = if ((post.durationMs ?: 0L) in 1..LOOP_UNDER_MS) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                prepare()
            }
        }
    var position by rememberSaveable(post.id) { mutableStateOf(0L) }
    var playing by rememberSaveable(post.id) { mutableStateOf(false) }
    LaunchedEffect(player) {
        if (position > 0) player.seekTo(position)
        player.playWhenReady = playing
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) player.pause()
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            position = player.currentPosition
            playing = player.playWhenReady
            player.release()
        }
    }
    var shown by remember { mutableStateOf(true) }
    var touched by remember { mutableIntStateOf(0) }
    val playPause = rememberPlayPauseButtonState(player)
    LaunchedEffect(touched, playPause.showPlay) {
        shown = true
        if (!playPause.showPlay) {
            delay(REST_AFTER_MS)
            shown = false
        }
    }
    Box(
        modifier.clickable(interactionSource = null, indication = null) {
            touched++
            onTap()
        }
    ) {
        val glass = rememberHazeState()
        PlayerSurface(player = player, surfaceType = SURFACE_TYPE_TEXTURE_VIEW, modifier = Modifier.fillMaxSize().hazeSource(glass))
        CompositionLocalProvider(LocalGlassSource provides glass) {
            val started = player.currentPosition > 0 || !playPause.showPlay
            AnimatedVisibility(
                playPause.showPlay && !started,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .glass(CircleShape, StarBooruTheme.roles)
                        .clickable { playPause.onClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.ic_play), stringResource(R.string.player_play), modifier = Modifier.size(32.dp))
                }
            }
            AnimatedVisibility(shown, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                Bar(player) { touched++ }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun BoxScope.Bar(
    player: Player,
    onTouch: () -> Unit
) {
    val roles = StarBooruTheme.roles
    val playPause = rememberPlayPauseButtonState(player)
    val mute = rememberMuteButtonState(player)
    val progress = rememberProgressStateWithTickInterval(player, 250L)
    var scrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(0f) }
    var repeat by remember { mutableStateOf(player.repeatMode == Player.REPEAT_MODE_ONE) }
    val duration = progress.durationMs.coerceAtLeast(0L)
    val at = if (scrubbing) (scrub * duration).toLong() else progress.currentPositionMs
    Row(
        Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .height(48.dp)
            .glass(CircleShape, roles)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            playPause.onClick()
            onTouch()
        }, enabled = playPause.isEnabled) {
            Icon(
                painterResource(if (playPause.showPlay) R.drawable.ic_play else R.drawable.ic_pause),
                stringResource(if (playPause.showPlay) R.string.player_play else R.string.player_pause)
            )
        }
        Text(clock(at), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Slider(
            value = if (duration > 0) at.toFloat() / duration else 0f,
            onValueChange = {
                scrubbing = true
                scrub = it
                onTouch()
            },
            onValueChangeFinished = {
                player.seekTo((scrub * duration).toLong())
                scrubbing = false
            },
            colors = SliderDefaults.colors(inactiveTrackColor = roles.glassLine),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
        )
        Text(clock(duration), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = {
            mute.onClick()
            onTouch()
        }, enabled = mute.isEnabled) {
            Icon(
                painterResource(if (mute.showMuted) R.drawable.ic_volume_mute else R.drawable.ic_volume),
                stringResource(if (mute.showMuted) R.string.player_unmute else R.string.player_mute)
            )
        }
        IconButton(onClick = {
            repeat = !repeat
            player.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            onTouch()
        }) {
            Icon(
                painterResource(R.drawable.ic_repeat),
                stringResource(R.string.player_loop),
                tint = if (repeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .then(if (repeat) Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape) else Modifier)
                        .padding(6.dp)
            )
        }
    }
}
