package dev.odeon.android.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import dev.odeon.android.ui.Cores

/// As faixas: legenda e áudio, **os dois embaixo e lado a lado**.
///
/// ## Por que saíram do cabeçalho
///
/// > «O icon de legenda tu pode colocar em baixo (…) Tem que adicionar um icon
/// > do lado de legenda para o audio tb, alguns filmes tem dual audio»
///
/// E a razão que o pedido carrega é de arrumação: legenda e áudio respondem à
/// **mesma** pergunta — «em que língua eu vou ver isto?». Separá-los, um no alto
/// e outro em lugar nenhum, era o que fazia a legenda parecer prima do `voltar`
/// e do PiP, que são navegação. Embaixo, ao lado do transporte, os dois viram o
/// par que sempre foram.
///
/// ## ⚠️ `cc`, e é decisão do dono contra a regra 1
///
/// A primeira versão desenhou duas barras de texto **de propósito**, com o
/// argumento escrito: `CC` é sigla de inglês (*closed captions*) e este app é em
/// português inteiro.
///
/// O dono pediu `cc` mesmo assim, e é o pedido que vale. O contra-argumento dele
/// é bom e vale registrar: `cc` não se lê como palavra em lugar nenhum — se lê
/// como **símbolo**, do mesmo jeito que `▶` não é inglês. Está em todo controle
/// remoto e em todo player que a pessoa já usou, inclusive nos daqui.
///
/// ## E o áudio só aparece quando há o que escolher
///
/// «alguns filmes tem dual audio» é exatamente a regra: com uma faixa só, o
/// botão não nasce. É o §53 — o produto não oferece o que a validação vai negar
/// —, a mesma régua que já governava o botão de legenda.

/// O rótulo de uma faixa de áudio, com as quedas na ordem certa.
///
/// ⚠️ **O `label` vem pronto do servidor** — codec, idioma, canais e o resto já
/// vêm compostos por quem tem a probe. Montar «Português - AC3 5.1» aqui seria a
/// terceira redação da mesma frase entre web, Android e servidor, que é a mesma
/// regra do `label` das legendas e do `reasons` do plano.
///
/// As quedas existem pro caso de ele vir vazio, e cada uma é fato e não chute:
///
/// | | |
/// |---|---|
/// | `title` | o que quem ripou escreveu na faixa |
/// | `language` | o código do contêiner |
/// | `faixa N` | a posição, que é o que sempre se sabe |
///
/// ⚠️ **`und` conta como ausente**, e a foto de 06/08/2026 é que cobrou: o menu
/// abriu com uma faixa chamada `und`. Não é idioma — em ISO 639 quer dizer
/// *undetermined*, o contêiner declarando que **não sabe**. Repassar isso é a
/// tela mostrando um código que significa «sem informação»; `faixa 1` diz o
/// mesmo, e diz em português.
internal fun rotuloDaFaixa(faixa: dev.odeon.android.dados.FaixaDeAudio): String =
    faixa.label.ifBlank { null }
        ?: faixa.title?.ifBlank { null }
        ?: faixa.language?.ifBlank { null }?.takeIf { it != "und" }
        ?: "faixa ${faixa.index + 1}"

/// Manda o player tocar a **enésima** faixa de áudio do contêiner.
///
/// ## ⚠️ Só serve em `direct_play`, e é por isso que ela sobreviveu
///
/// Em transcodificação a outra faixa não está na playlist: quem troca é o
/// servidor, abrindo sessão nova — ver `ModeloDoPlayer.trocarFaixaDeAudio`.
///
/// Em `direct_play` é o contrário: o contêiner inteiro chega ao aparelho com
/// todas as faixas, o plano novo devolve a **mesma** URL, e o player recarrega
/// escolhendo a primeira faixa por conta própria. Sem esta chamada, pedir a
/// faixa 1 num arquivo direto não mudaria uma nota do que se ouve.
///
/// ⚠️ **A ordem é a do contêiner nas duas pontas.** O `index` do servidor é o
/// `N` de `-map 0:a:N`, e os grupos de áudio do ExoPlayer chegam na ordem em que
/// o contêiner os declara. É a mesma fila, contada do mesmo lugar.
internal fun escolherAudio(player: Player?, indice: Int) {
    val p = player ?: return
    val grupo = p.currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_AUDIO }
        .getOrNull(indice) ?: return

    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        .setOverrideForType(TrackSelectionOverride(grupo.mediaTrackGroup, 0))
        .build()
}

/// O botão de legenda: `cc` dentro de uma moldura.
@Composable
internal fun BotaoDeLegenda(ligado: Boolean, aoTocar: () -> Unit) {
    val medidor = rememberTextMeasurer()
    AlvoDeToque(rotulo = "legendas", aoTocar = aoTocar) {
        desenharCC(medidor, aceso = ligado)
    }
}

/// O botão de áudio: o cone e as duas ondas.
@Composable
internal fun BotaoDeAudio(aoTocar: () -> Unit) {
    AlvoDeToque(rotulo = "faixa de áudio", aoTocar = aoTocar) { desenharAudio() }
}

/// A lista de faixas, aberta acima do transporte.
///
/// ⚠️ Ela nasce **dentro** da coluna de baixo, e não flutuando com `align`: o
/// menu de legendas antigo ficava preso ao canto superior direito com um
/// `padding(top = 56.dp)` — um número que só valia enquanto o cabeçalho tivesse
/// aquela altura. Em fluxo, ele abre onde o botão está e acompanha o que muda em
/// volta.
@Composable
internal fun MenuDeFaixas(
    titulo: String,
    itens: List<Pair<String, Boolean>>,
    aoEscolher: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Cores.fundoAfundado.copy(alpha = 0.95f))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.labelSmall,
            color = Cores.textoApagado,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
        itens.forEachIndexed { indice, (rotulo, escolhido) ->
            Text(
                text = rotulo,
                style = MaterialTheme.typography.bodySmall,
                /// A escolhida em âmbar, e não com um ✓ na frente: o tique
                /// empurraria todas as outras linhas pra direita pra abrir
                /// espaço a uma coluna que fica vazia em quase todas.
                color = if (escolhido) Cores.destaque else Cores.texto,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { aoEscolher(indice) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/// A moldura com `cc` dentro.
///
/// ⚠️ **Apagado quando não há legenda no ar.** Um `cc` sempre branco não diz se
/// a legenda está ligada — e essa é a única pergunta que alguém faz olhando pra
/// ele no meio de um filme.
private fun DrawScope.desenharCC(medidor: TextMeasurer, aceso: Boolean) {
    val cor = if (aceso) Cores.destaque else Cores.texto
    val traco = 1.8.dp.toPx()
    drawRoundRect(
        color = cor,
        topLeft = Offset(traco / 2, size.height * 0.18f),
        size = Size(size.width - traco, size.height * 0.64f),
        cornerRadius = CornerRadius(3.dp.toPx()),
        style = Stroke(width = traco),
    )
    val texto = medidor.measure(
        text = "cc",
        style = TextStyle(color = cor, fontSize = 9.sp, letterSpacing = 0.5.sp),
    )
    drawText(
        textLayoutResult = texto,
        topLeft = Offset(
            (size.width - texto.size.width) / 2f,
            (size.height - texto.size.height) / 2f,
        ),
    )
}

/// O cone e as duas ondas.
///
/// ⚠️ **Não é o ícone de volume**, e a diferença importa porque o volume já tem
/// gesto próprio nesta tela (arrastar na metade direita). O que separa os dois é
/// o vizinho: ao lado do `cc`, um alto-falante lê como «em que língua eu ouço
/// isto». Sozinho num canto, leria como volume — e por isso ele **não** existe
/// sozinho: só nasce quando há mais de uma faixa, e aí o menu que abre é a
/// própria resposta.
private fun DrawScope.desenharAudio() {
    val traco = 1.8.dp.toPx()
    val cone = Path().apply {
        moveTo(size.width * 0.10f, size.height * 0.38f)
        lineTo(size.width * 0.26f, size.height * 0.38f)
        lineTo(size.width * 0.46f, size.height * 0.18f)
        lineTo(size.width * 0.46f, size.height * 0.82f)
        lineTo(size.width * 0.26f, size.height * 0.62f)
        lineTo(size.width * 0.10f, size.height * 0.62f)
        close()
    }
    drawPath(cone, Cores.texto, style = Stroke(width = traco, cap = StrokeCap.Round))
    /// Duas ondas pela mesma razão do cast: em 22dp, três arcos concêntricos
    /// ficam a menos de 2dp um do outro e viram mancha.
    listOf(0.20f, 0.36f).forEach { raio ->
        drawArc(
            color = Cores.texto,
            startAngle = -55f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(
                size.width * 0.52f - size.width * raio,
                size.height * 0.5f - size.width * raio,
            ),
            size = Size(size.width * raio * 2, size.width * raio * 2),
            style = Stroke(width = traco, cap = StrokeCap.Round),
        )
    }
}
