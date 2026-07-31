package dev.odeon.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.seekToTime
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL

private const val HEARTBEAT_MS = 10_000L

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayer(
    url: String,
    startPositionSeconds: Double,
    modifier: Modifier,
    onProgress: (positionSeconds: Double, durationSeconds: Double, eventType: String) -> Unit,
) {
    val player = remember(url) {
        AVPlayer(uRL = NSURL(string = url)).apply {
            if (startPositionSeconds > 30) {
                // preferredTimescale 1 basta: o seek de retomada é em segundos
                seekToTime(CMTimeMakeWithSeconds(startPositionSeconds, 1))
            }
            play()
        }
    }

    fun snapshot(eventType: String) {
        val item = player.currentItem ?: return
        val duration = CMTimeGetSeconds(item.duration)
        if (duration.isNaN() || duration <= 0.0) return
        val position = CMTimeGetSeconds(player.currentTime())
        if (position.isNaN()) return
        onProgress(position, duration, eventType)
    }

    DisposableEffect(player) {
        onDispose {
            snapshot("abandon")
            player.pause()
        }
    }

    LaunchedEffect(player) {
        snapshot("start")
        while (true) {
            delay(HEARTBEAT_MS)
            // rate != 0 é "está tocando" no AVPlayer
            if (player.rate != 0f) snapshot("progress")
        }
    }

    UIKitView(
        factory = {
            AVPlayerViewController().also { controller ->
                controller.player = player
                controller.showsPlaybackControls = true
            }.view
        },
        modifier = modifier,
    )
}
