package dev.odeon.app

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** Mesmo heartbeat do cliente web — o backend espera essa cadência. */
private const val HEARTBEAT_MS = 10_000L

@OptIn(UnstableApi::class)
@Composable
actual fun VideoPlayer(
    url: String,
    startPositionSeconds: Double,
    modifier: Modifier,
    onProgress: (positionSeconds: Double, durationSeconds: Double, eventType: String) -> Unit,
) {
    val context = LocalContext.current

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            // Retoma só se valeu a pena, igual ao web.
            if (startPositionSeconds > 30) seekTo((startPositionSeconds * 1000).toLong())
            prepare()
            playWhenReady = true
        }
    }

    fun snapshot(eventType: String) {
        val duration = player.duration
        if (duration <= 0) return
        onProgress(player.currentPosition / 1000.0, duration / 1000.0, eventType)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                snapshot(if (isPlaying) "start" else "pause")
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) snapshot("finish")
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) snapshot("seek")
            }
        }
        player.addListener(listener)

        onDispose {
            snapshot("abandon")
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(HEARTBEAT_MS)
            if (player.isPlaying) snapshot("progress")
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        modifier = modifier,
    )
}
