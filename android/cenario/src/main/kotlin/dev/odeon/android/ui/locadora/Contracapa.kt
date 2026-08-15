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
import dev.odeon.android.ui.Texto
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    Box(Modifier.fillMaxSize().background(Cores.fundoAfundado)) {
        /// ## O papel de fundo — o encarte impresso não tem breu
        ///
        /// A foto cobrou um vão de meia caixa entre a advertência e o rodapé:
        /// sinopse curta numa caixa alta deixa papel vazio, e papel de encarte
        /// **nunca é preto liso** — a referência imprime a arte do filme
        /// esmaecida por trás do texto inteiro. É o backdrop da própria obra a
        /// 10%, com um véu por cima pra o texto continuar mandando.
        arte(obra?.artwork?.get("backdrop"))?.let { fundo ->
            AsyncImage(
                model = fundo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                /// ⚠️ **0,50, calibrado em duas rodadas com o dono** — a
                /// primeira versão usou 0,10 («tô conseguindo ver beeemm pouco,
                /// tá quase preto»), a segunda 0,34 («aumente mais»). Somado ao
                /// véu e ao `VeuDeLuz` da face, o número visto na tela é bem
                /// menor que o escrito aqui — não «corrigir» pra baixo sem foto.
                alpha = 0.50f,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Cores.fundoAfundado.copy(alpha = 0.08f),
                            Cores.fundoAfundado.copy(alpha = 0.32f),
                        ),
                    ),
                ),
            )
        }

        Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Texto(
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
        Texto(
            text = listOfNotNull(
                obra?.year?.toString(),
                obra?.kind?.let { tipoEmPortugues(it) },
                if (ehVhs) "VHS" else "DVD",
            ).joinToString(" · ").uppercase(),
            style = TextStyle(fontSize = 9.sp, letterSpacing = 0.18.em, color = cor),
            maxLines = 1,
        )

        /// ## Sinopse e cenas lado a lado — «falta vida, mais EXPERIÊNCIA»
        ///
        /// O verso da referência (o Rei Leão que o dono mandou) não empilha: o
        /// texto corre à esquerda e as cenas ficam **emolduradas** à direita,
        /// como selos. Era exatamente o que faltava — a cena única de 16:9
        /// embaixo da sinopse era um banner, não um encarte.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            obra?.overview?.takeIf { it.isNotBlank() }?.let { sinopse ->
                Texto(
                    text = sinopse,
                    /// ⚠️ 11,5sp e **quatorze** linhas — a primeira versão usou
                    /// 10sp e oito, e a foto mostrou meia caixa de breu entre a
                    /// advertência e o rodapé. O verso de uma fita de verdade é
                    /// **cheio**: a da referência não deixa um dedo de papel sem
                    /// tinta. O que não couber some com reticência.
                    style = TextStyle(fontSize = 11.5.sp, lineHeight = 16.5.sp, color = Cores.texto),
                    maxLines = 14,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.35f),
                )
            }

            /// As molduras: até duas cenas reais (`backdrop` e `still`), cada
            /// uma com o filete dourado que o encarte da referência usa. Sem
            /// arte nenhuma, a coluna não nasce (§24).
            val cenas = listOfNotNull(
                arte(obra?.artwork?.get("backdrop")),
                arte(obra?.artwork?.get("still")),
            ).distinct()
            if (cenas.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    cenas.forEach { cena ->
                        AsyncImage(
                            model = cena,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 10f)
                                .border(1.dp, Cores.destaqueApagado.copy(alpha = 0.7f))
                                .padding(2.dp),
                        )
                    }
                }
            }
        }

        /// O respiro fica **aqui**, entre a leitura e a impressão do pé — não
        /// depois da advertência. Sinopse e cenas descem do topo;
        /// especificações, advertência e rodapé sobem do pé, que é onde toda
        /// gráfica os põe. O meio que sobrar é papel com arte, não breu.
        Box(Modifier.weight(1f))

        /// ## As especificações em cores — a barra que dá vida ao pé do verso
        ///
        /// É a fileira de abas coloridas do encarte da referência («IDIOMAS ·
        /// LEGENDAS · SOM…»), com uma diferença que é regra da casa: **cada aba
        /// só diz o que o arquivo de verdade tem** (§18). Nada de selo Dolby num
        /// AAC, nada de aba de legendas que ninguém mediu — duração, vídeo, som
        /// e tamanho, que são os quatro que o servidor afirma.
        obra?.files?.firstOrNull()?.let { arquivo ->
            val abas = listOfNotNull(
                arquivo.duracaoEmSegundos?.takeIf { it > 0 }
                    ?.let { "DURAÇÃO" to duracaoCompacta(it).uppercase() },
                arquivo.height?.let { altura ->
                    "VÍDEO" to listOfNotNull("${altura}p", arquivo.codecDeVideo?.uppercase())
                        .joinToString(" ")
                },
                arquivo.codecDeAudio?.uppercase()?.let { codec ->
                    "SOM" to (arquivo.canaisDeAudio?.let { "$codec ${canais(it)}" } ?: codec)
                },
                arquivo.tamanhoEmBytes?.let { "TAMANHO" to tamanhoCompacto(it) },
            )
            if (abas.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Texto(
                        text = "ESPECIFICAÇÕES TÉCNICAS DO FILME",
                        style = TextStyle(
                            fontSize = 6.5.sp,
                            letterSpacing = 0.22.em,
                            fontWeight = FontWeight.Bold,
                            color = Cores.destaqueApagado,
                        ),
                        maxLines = 1,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        /// As cores das abas são as do encarte impresso —
                        /// amarelo, verde, azul, roxo — fixas por posição, não
                        /// sorteadas: o verso da mesma caixa é sempre igual.
                        val tintas = listOf(
                            Color(0xFFE3C14E),
                            Color(0xFF8FBF5A),
                            Color(0xFF5FA8CF),
                            Color(0xFFB07FC7),
                        )
                        abas.forEachIndexed { i, (rotulo, valor) ->
                            AbaDeSpec(
                                rotulo = rotulo,
                                valor = valor,
                                tinta = tintas[i % tintas.size],
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        /// A advertência, em letra de bula — e ela é **verdadeira**: são as
        /// regras desta locadora, no tom do juridiquês que toda capa carregava.
        /// A da fita manda rebobinar porque rebobinar existe aqui de fato; a do
        /// disco lembra do menu. Vida sem mentira: o §18 continua de pé.
        Texto(
            text = if (ehVhs) {
                "ADVERTÊNCIA: esta cópia pertence ao acervo da casa e é servida pelo " +
                    "Odeon para exibição doméstica. A fita é um objeto só — o ponto onde " +
                    "ela parar chega junto a quem a pegar depois. Rebobine antes de devolver."
            } else {
                "ADVERTÊNCIA: esta cópia pertence ao acervo da casa e é servida pelo " +
                    "Odeon para exibição doméstica. O disco dispensa rebobinar — o menu " +
                    "lembra as cenas. A caixa volta sozinha à estante na devolução."
            },
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 7.5.sp,
                lineHeight = 10.5.sp,
                color = Cores.textoApagado.copy(alpha = 0.8f),
            ),
            textAlign = TextAlign.Justify,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            CodigoDeBarras(
                semente = obra?.id ?: titulo,
                modifier = Modifier.width(96.dp).height(34.dp),
            )

            /// A marca da casa, entre o código e o botão — o «Home
            /// Entertainment» do encarte, só que desta locadora.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Texto(
                    text = "ODEON",
                    style = TextStyle(
                        fontFamily = Serifada,
                        fontSize = 10.sp,
                        letterSpacing = 0.3.em,
                        color = Cores.destaque.copy(alpha = 0.85f),
                    ),
                    maxLines = 1,
                )
                Texto(
                    text = if (ehVhs) "HI-FI · VIDEO CASSETE" else "HOME VIDEO",
                    style = TextStyle(
                        fontSize = 5.5.sp,
                        letterSpacing = 0.24.em,
                        color = Cores.textoApagado,
                    ),
                    maxLines = 1,
                )
            }

            Box(Modifier.weight(1f))

            /// O botão. **Ele toca o filme** — não abre a ficha.
            ///
            /// Era o quarto pedido: «quando clicar em play, tem que cair no
            /// filme, não nos detalhes». A ficha é o caminho da biblioteca; aqui
            /// a pessoa já está com a caixa na mão, já leu a sinopse e já viu a
            /// ficha técnica — tudo o que a tela de detalhes teria pra dizer está
            /// impresso nesta mesma face.
            if (aoAssistir != null) {
                Texto(
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
}

/// Uma aba de especificação: o rótulo em cor de impressão e o valor embaixo.
///
/// A forma é a das abas do encarte da referência — cabeçalho colorido com texto
/// escuro, valor em fonte de máquina. O dado é sempre real; a cor é só tinta.
@Composable
private fun AbaDeSpec(rotulo: String, valor: String, tinta: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(2.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Texto(
            text = rotulo,
            style = TextStyle(
                fontSize = 7.sp,
                letterSpacing = 0.1.em,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1708),
            ),
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .background(tinta)
                .padding(vertical = 2.dp),
            textAlign = TextAlign.Center,
        )
        Texto(
            text = valor,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = Cores.texto.copy(alpha = 0.9f),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.06f))
                .padding(vertical = 2.dp),
            textAlign = TextAlign.Center,
        )
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
