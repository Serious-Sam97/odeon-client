package dev.odeon.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * O único pedaço de UI que **não** dá pra compartilhar.
 *
 * Android usa Media3/ExoPlayer, iOS usa AVPlayer. Isolar aqui é o que permite
 * o resto das telas serem um código só — foi exatamente a aposta do M4.
 *
 * @param onProgress chamado periodicamente e nas transições, pra o repositório
 *        reportar ao servidor (e o sync do M3 funcionar entre aparelhos).
 */
@Composable
expect fun VideoPlayer(
    url: String,
    startPositionSeconds: Double,
    modifier: Modifier = Modifier,
    onProgress: (positionSeconds: Double, durationSeconds: Double, eventType: String) -> Unit,
)
