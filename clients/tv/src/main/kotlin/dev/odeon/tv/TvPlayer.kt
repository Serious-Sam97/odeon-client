package dev.odeon.tv

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.odeon.shared.OdeonRepository
import dev.odeon.shared.WorkListItem
import kotlinx.coroutines.delay

private const val HEARTBEAT_MS = 10_000L
private const val SKIP_MS = 10_000L
private const val OVERLAY_HIDE_MS = 3_000L

private fun clock(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}

/**
 * Player da TV.
 *
 * Os controles nativos do Media3 (`PlayerView`) são feitos pra toque; no D-pad
 * eles viram um labirinto de foco. Aqui o `PlayerView` fica só com a superfície
 * de vídeo e o controle é a própria tecla: OK pausa, ←/→ pulam, VOLTAR sai.
 */
@OptIn(UnstableApi::class)
@Composable
fun TvPlayer(
    repository: OdeonRepository,
    work: WorkListItem,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val mediaFileId = work.mediaFileId ?: return
    val focus = remember { FocusRequester() }

    val player = remember(mediaFileId) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(repository.streamUrl(mediaFileId)))
            val resume = work.positionSeconds ?: 0.0
            if (resume > 30) seekTo((resume * 1000).toLong())
            prepare()
            playWhenReady = true
        }
    }

    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var overlayUntil by remember { mutableLongStateOf(Long.MAX_VALUE) }
    var now by remember { mutableLongStateOf(0L) }

    fun report(eventType: String) {
        val total = player.duration
        if (total <= 0) return
        repository.reportProgress(
            work.id,
            player.currentPosition / 1000.0,
            total / 1000.0,
            mediaFileId,
            eventType,
        )
    }

    fun wake() {
        overlayUntil = now + OVERLAY_HIDE_MS
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                report(if (isPlaying) "start" else "pause")
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    report("finish")
                    onExit()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            report("abandon")
            player.removeListener(listener)
            player.release()
        }
    }

    // Um laço só cuida do relógio da UI e do heartbeat — dois timers separados
    // sairiam de fase e o overlay piscaria.
    LaunchedEffect(player) {
        var sinceHeartbeat = 0L
        while (true) {
            delay(500)
            now += 500
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            sinceHeartbeat += 500
            if (sinceHeartbeat >= HEARTBEAT_MS) {
                sinceHeartbeat = 0
                if (player.isPlaying) report("progress")
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        overlayUntil = OVERLAY_HIDE_MS
    }

    val overlayVisible = now < overlayUntil || !playing

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                wake()
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                        if (player.isPlaying) player.pause() else player.play()
                        true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        player.seekTo(player.currentPosition + SKIP_MS)
                        report("seek")
                        true
                    }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        player.seekTo((player.currentPosition - SKIP_MS).coerceAtLeast(0))
                        report("seek")
                        true
                    }
                    Key.Back -> {
                        onExit()
                        true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    // O controle é o D-pad; os botões do Media3 só roubariam foco.
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (overlayVisible) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .padding(horizontal = 48.dp, vertical = 27.dp),
            ) {
                work.seriesTitle?.let {
                    Text(it.uppercase(), color = Accent, fontSize = 13.sp)
                }
                Text(
                    work.title,
                    color = Color(0xFFECEEF4),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = {
                        if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    },
                    color = Accent,
                    trackColor = Color(0xFF33333F),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )

                Spacer(Modifier.height(10.dp))

                Row {
                    Text(
                        "${clock(position)} / ${clock(duration)}",
                        color = Color(0xFFECEEF4),
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.width(24.dp))
                    Text(
                        if (playing) "OK pausa · ←/→ 10s · VOLTAR sai"
                        else "pausado · OK retoma",
                        color = Color(0xFF8B8D9A),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
