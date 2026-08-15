package dev.odeon.android.ui.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride

/// Como se manda o player tocar **outra** faixa.
///
/// ## Por que as duas moram no `:core` — 12/08/2026
///
/// Elas vieram de `ui/player/Faixas.kt` e `ui/player/TelaDoPlayer.kt` do `:app`
/// quando a TV nasceu, e a régua do módulo aguenta: **elas não desenham**. Não
/// há `Composable`, `Modifier` nem cor aqui — o que há é `Player`, que é do
/// Media3 e já era dependência do `:core` por causa do `ModeloDoPlayer`.
///
/// O que forçou a mudança é que a alternativa era pior de um jeito específico:
/// as duas são **sutis**, e cada uma carrega um defeito medido no comentário.
/// Reescrevê-las no `:tv` não seria copiar vinte linhas — seria copiar vinte
/// linhas *e perder o porquê*, e aí o `:tv` reintroduziria calado exatamente os
/// dois defeitos que o `:app` já pagou pra descobrir.

/// ## ⚠️ Em `direct_play`, a faixa escolhida precisa ser reaplicada
///
/// Pedir a faixa 1 num arquivo direto refaz o plano, e o plano devolve a
/// **mesma** URL — é o mesmo arquivo, com as duas faixas dentro. O player
/// recarrega e escolhe a primeira por conta própria, então sem esta chamada o
/// menu mudaria o rótulo e não mudaria uma nota do que se ouve.
///
/// Em HLS ela não faz nada, e é de propósito: lá a playlist tem uma faixa só, o
/// `getOrNull(indice)` não acha grupo nenhum e a função devolve sem tocar em
/// nada. Uma condição a menos pra alguém errar depois.
///
/// ⚠️ **A ordem é a do contêiner nas duas pontas.** O `index` do servidor é o
/// `N` de `-map 0:a:N`, e os grupos de áudio do ExoPlayer chegam na ordem em que
/// o contêiner os declara. É a mesma fila, contada do mesmo lugar.
fun escolherAudio(player: Player?, indice: Int) {
    val p = player ?: return
    val grupo = p.currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_AUDIO }
        .getOrNull(indice) ?: return

    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        .setOverrideForType(TrackSelectionOverride(grupo.mediaTrackGroup, 0))
        .build()
}

/// Liga uma legenda pelo rótulo, ou desliga todas com `null`.
///
/// ⚠️ **O casamento é pelo `label`, e não pelo índice.** As legendas entram como
/// faixas de fora (`MediaItem.SubtitleConfiguration`), e a ordem em que o player
/// as expõe não é garantidamente a ordem em que foram declaradas — ela depende
/// de quando cada `.vtt` termina de carregar. Um índice aqui acertaria quase
/// sempre e erraria justamente quando a rede está lenta.
///
/// O rótulo vem pronto do servidor (ver `LegendaOferecida`), então é a mesma
/// string dos dois lados.
///
/// ⚠️ E desligar precisa das **duas** linhas: `setTrackTypeDisabled` sozinho
/// deixa a sobreposição de volta assim que a fonte trocar, porque o override
/// anterior continua guardado nos parâmetros. É o `clearOverridesOfType` que o
/// esquece de verdade.
fun escolherLegenda(player: Player?, rotulo: String?) {
    val p = player ?: return

    if (rotulo == null) {
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        return
    }

    val grupo = p.currentTracks.groups
        .firstOrNull { g ->
            g.type == C.TRACK_TYPE_TEXT &&
                (0 until g.length).any { g.getTrackFormat(it).label == rotulo }
        } ?: return

    val faixa = (0 until grupo.length).first { grupo.getTrackFormat(it).label == rotulo }

    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .setOverrideForType(TrackSelectionOverride(grupo.mediaTrackGroup, faixa))
        .build()
}
