package dev.odeon.android.ui.locadora

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.odeon.android.dados.ObraDetalhada
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.duracaoCompacta
import dev.odeon.android.ui.tamanhoCompacto
import dev.odeon.android.ui.Serifada
import kotlin.math.abs

/// A contracapa — **o verso da caixa**, como numa caixa de DVD de verdade.
///
/// ## O pedido, e a referência
///
/// > «quando você move com o dedo, faça dar pra ver o verso também… acabei de
/// > mandar uma foto do verso de um DVD.»
///
/// A foto é o verso da web, e ele tem seis coisas nesta ordem, de cima pra
/// baixo: **título**, a linha `ano · tipo · mídia`, a **sinopse**, uma **cena**,
/// a **ficha técnica** em fonte de máquina, e o rodapé com o **código de barras**
/// e o botão.
///
/// A ordem não é decorativa — é a de uma capa de aluguel de verdade: o que a
/// obra é, do que ela trata, como ela se parece, e o que você leva pra casa.
///
/// ## O código de barras sai do uuid
///
/// «A mesma caixa tem sempre o mesmo código», diz a web. Ele não é dado nenhum:
/// é o que faz o verso parecer um produto e não uma ficha — e, sendo derivado,
/// não custa byte nem inventa número (§18: ele não se parece com um dado que
/// alguém possa querer ler).
@Composable
fun Contracapa(
    titulo: String,
    obra: ObraDetalhada?,
    /// `true` quando a caixa é fita — muda uma palavra na linha do topo, e é a
    /// mesma verdade que decide se ela rebobina.
    ehVhs: Boolean,
    cor: Color,
    arte: (String?) -> String?,
    aoAssistir: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cores.fundoAfundado)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = obra?.title ?: titulo,
            style = TextStyle(
                fontFamily = Serifada,
                fontSize = 19.sp,
                lineHeight = 22.sp,
                color = Cores.texto,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        /// `2007 · FILME · DVD` — a linha de identidade, em versalete espaçado.
        /// É a única do verso que leva a cor da obra, e é por ela que o olho sabe
        /// que ainda está no mesmo objeto que tem o pôster do outro lado.
        Text(
            text = listOfNotNull(
                obra?.year?.toString(),
                obra?.kind?.let { tipoEmPortugues(it) },
                if (ehVhs) "VHS" else "DVD",
            ).joinToString(" · ").uppercase(),
            style = TextStyle(fontSize = 9.sp, letterSpacing = 0.18.em, color = cor),
            maxLines = 1,
        )

        obra?.overview?.takeIf { it.isNotBlank() }?.let { sinopse ->
            Text(
                text = sinopse,
                style = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, color = Cores.texto),
                /// Quatro linhas, e é o que cabe: o verso de uma caixa também
                /// corta a sinopse, e por isso a última linha some com reticência
                /// em vez de espremer a fonte.
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

        /// A cena. Uma só — a web mostra até três, e aqui a caixa é do tamanho de
        /// uma mão: três tiras de 16:9 numa largura de 280dp viram três selos
        /// ilegíveis.
        arte(obra?.artwork?.get("backdrop") ?: obra?.artwork?.get("still"))?.let { cena ->
            AsyncImage(
                model = cena,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(2.dp)),
            )
        }

        /// A ficha técnica, em fonte de máquina.
        ///
        /// Monoespaçada de propósito: ela é a única coisa do verso que se **lê
        /// como impressão**, e é o que a caixa de verdade tem escrito em cinza no
        /// pé. Cada pedaço só entra se existir (§24) — metade do acervo não tem
        /// arquivo casado, e uma linha com quatro pontos e nada entre eles seria
        /// pior que linha nenhuma.
        obra?.files?.firstOrNull()?.let { arquivo ->
            val ficha = listOfNotNull(
                arquivo.duracaoEmSegundos?.takeIf { it > 0 }?.let { duracaoCompacta(it).uppercase() },
                arquivo.height?.let { "${it}p" },
                arquivo.codecDeVideo?.uppercase(),
                arquivo.codecDeAudio?.uppercase()?.let { codec ->
                    arquivo.canaisDeAudio?.let { "$codec ${canais(it)}" } ?: codec
                },
                arquivo.tamanhoEmBytes?.let { tamanhoCompacto(it) },
                arquivo.container?.uppercase(),
            )
            if (ficha.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Cores.linha))
                Text(
                    text = ficha.joinToString("  ·  "),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        letterSpacing = 0.04.em,
                        color = Cores.textoApagado,
                    ),
                    maxLines = 2,
                )
            }
        }

        /// O rodapé empurra pra baixo: numa caixa, o código de barras e o preço
        /// ficam no pé, não flutuando no meio.
        Box(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            CodigoDeBarras(
                semente = obra?.id ?: titulo,
                modifier = Modifier.width(96.dp).height(34.dp),
            )

            Box(Modifier.weight(1f))

            /// O botão. **Ele toca o filme** — não abre a ficha.
            ///
            /// Era o quarto pedido: «quando clicar em play, tem que cair no
            /// filme, não nos detalhes». A ficha é o caminho da biblioteca; aqui
            /// a pessoa já está com a caixa na mão, já leu a sinopse e já viu a
            /// ficha técnica — tudo o que a tela de detalhes teria pra dizer está
            /// impresso nesta mesma face.
            if (aoAssistir != null) {
                Text(
                    text = if ((obra?.ondeParou ?: 0.0) > 60) "▸ CONTINUAR" else "▸ ASSISTIR",
                    style = TextStyle(
                        fontSize = 10.sp,
                        letterSpacing = 0.12.em,
                        fontWeight = FontWeight.Medium,
                        color = Cores.fundoAfundado,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(cor)
                        .clickable(onClick = aoAssistir)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/// O código de barras, derivado da semente.
///
/// Não é um EAN de verdade e não tenta ser: são 32 barras de larguras variadas,
/// sempre as mesmas pra a mesma obra. Um código legível por leitor seria um dado
/// falso — este é ornamento honesto, e o comentário do `Contracapa` explica por
/// que ele está aqui.
@Composable
private fun CodigoDeBarras(semente: String, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.White).padding(3.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            var hash = semente.fold(7) { acc, c -> acc * 31 + c.code }
            val quantas = 32
            val passo = size.width / quantas
            repeat(quantas) { i ->
                hash = hash * 1103515245 + 12345
                val largura = passo * (0.2f + (abs(hash / 65536) % 3) * 0.26f)
                if ((abs(hash / 256) % 5) != 0) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(i * passo, 0f),
                        size = androidx.compose.ui.geometry.Size(largura, size.height),
                    )
                }
            }
        }
    }
}

/// `filme` · `episódio` · `stand-up`… O `kind` do servidor em português.
///
/// Um tipo desconhecido volta **como veio**, e não vira «desconhecido»: o
/// servidor pode ganhar um `kind` novo, e o nome cru dele diz mais que uma
/// palavra genérica.
internal fun tipoEmPortugues(kind: String): String = when (kind) {
    "movie" -> "filme"
    "episode" -> "episódio"
    "standup" -> "stand-up"
    "documentary" -> "documentário"
    "concert" -> "show"
    else -> kind
}

/// ⚠️ A `duracaoCompacta` e a `tamanhoCompacto` **moravam aqui** e foram pra
/// `ui/Medida.kt` em 05/08/2026, quando a tela de baixados passou a precisar das
/// duas. O porquê da mudança de casa está lá.
///
/// O `1H36` em caixa alta continua sendo desta tela: o verso da caixa é encarte
/// impresso, e o `.uppercase()` acontece na chamada. A função devolve `1h36`.

/// `5.1` · `2.0` — os canais como um encarte escreve, e não «6 canais».
internal fun canais(quantos: Int): String = when (quantos) {
    1 -> "1.0"
    2 -> "2.0"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$quantos.0"
}
