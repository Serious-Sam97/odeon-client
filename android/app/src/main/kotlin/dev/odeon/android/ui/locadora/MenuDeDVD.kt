package dev.odeon.android.ui.locadora

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.odeon.android.dados.Cena
import dev.odeon.android.dados.MenuDoDisco
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.Tipo
import dev.odeon.android.ui.corDeHex
import kotlinx.coroutines.delay

/// O menu de DVD — **o último item de v1**, e ele abre só pela locadora.
///
/// ## Por que só aqui, e só em disco
///
/// «A fita não tem menu, tem rebobinar» (§14.4). E o `▸ assistir` da biblioteca,
/// da busca e da ficha continua indo **direto pro filme**: o menu é o objeto
/// encenando o que ele é, não um pedágio no caminho de quem só quer assistir.
///
/// ## O que existe aqui, e o que ficou de fora **por decisão anterior**
///
/// | a web tem | aqui |
/// |---|---|
/// | vinheta de 2,5s, e qualquer tecla pula | ✅ e qualquer toque pula |
/// | doze climas, um por estante | ✅ **em cor e forma** |
/// | a trilha sintetizada | ❌ **vetada na §3 da espec** — é Web Audio, e o equivalente é reescrever o sintetizador em `AudioTrack` |
/// | o filme rodando de fundo | ◐ o backdrop com deriva lenta; a cena viva abriria uma sessão de HLS só pro menu |
/// | os itens como janelas para o filme | ◐ mesma razão: sem vídeo, não há recorte de quadro |
/// | `Continuar` · `Do começo` · `Capítulos` · `Legendas` | ✅ os quatro |
/// | a grade de capítulos, com a origem dita | ✅ e com **doze molduras vazias** enquanto carrega |
///
/// A trilha é a única coisa **vetada**; as outras duas são adiadas por custarem
/// uma sessão de transcodificação aberta pra desenhar um fundo — o que, num
/// servidor de casa que atende três pessoas, é caro pra enfeite.
@Composable
fun MenuDeDVD(
    disco: MenuDoDisco,
    cenas: List<Cena>,
    arte: (String?) -> String?,
    aoTocar: (segundos: Double) -> Unit,
    aoFechar: () -> Unit,
) {
    /// ⚠️ **`rememberSaveable`, e foi a rotação que mostrou por quê.**
    ///
    /// Com `remember`, girar o aparelho recria a atividade, perde o estado e a
    /// vinheta **roda de novo** — quem estava escolhendo um capítulo volta pro
    /// começo do menu e espera 2,5s outra vez. A vinheta é «toda vez que se põe
    /// o disco», não «toda vez que se vira o telefone».
    var passouAVinheta by rememberSaveable { mutableStateOf(false) }
    var nosCapitulos by rememberSaveable { mutableStateOf(false) }
    val clima = climaDe(disco.clima)

    /// A vinheta roda **toda vez**, e é da web. Ela é a lombada do menu: o que
    /// separa «abri um arquivo» de «pus um disco».
    LaunchedEffect(Unit) {
        delay(2_500)
        passouAVinheta = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Cores.fundoAfundado)
            .clickable { if (!passouAVinheta) passouAVinheta = true },
    ) {
        /// O fundo: o backdrop com uma deriva lenta.
        ///
        /// A web põe o **filme rodando** aqui, e é melhor. Mas ela já tem uma
        /// sessão de HLS aberta na página; aqui abrir uma só pra o fundo do menu
        /// é pôr um ffmpeg no ar por uma decoração — e o `serious-server` atende
        /// três pessoas com o Postgres do lado.
        arte(disco.backdrop)?.let { fundo ->
            val deriva by animateFloatAsState(
                targetValue = if (passouAVinheta) 1f else 0f,
                animationSpec = tween(24_000, easing = LinearEasing),
                label = "deriva do fundo",
            )
            AsyncImage(
                model = fundo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayerDeriva(deriva),
            )
            /// A lavagem, e ela é **pesada de propósito**.
            ///
            /// A primeira versão deixava o meio a 34% e o screenshot mostrou o
            /// resultado: o pôster do Juno passava por cima do menu inteiro, com
            /// «Tocar» disputando espaço com uma camiseta vermelha. É o mesmo
            /// defeito que a ficha já tinha corrigido — «com o meio
            /// transparente, um backdrop claro vira uma faixa branca gritando».
            ///
            /// Num menu de disco o fundo é **ambiente**: ele existe pra dizer de
            /// que filme é o menu, não pra ser visto. Duas camadas, então — um
            /// véu escuro parelho e a tinta do clima por cima dele.
            Box(Modifier.fillMaxSize().background(Cores.fundoAfundado.copy(alpha = 0.80f)))
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            clima.tinta.copy(alpha = 0.22f),
                            Cores.fundoAfundado.copy(alpha = 0.70f),
                        ),
                    ),
                ),
            )
        }

        if (!passouAVinheta) {
            Vinheta(clima = clima, nome = disco.climaNome)
            return@Box
        }

        if (nosCapitulos) {
            Capitulos(
                disco = disco,
                cenas = cenas,
                arte = arte,
                clima = clima,
                aoEscolher = aoTocar,
                aoVoltar = { nosCapitulos = false },
            )
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = disco.titulo,
                style = MaterialTheme.typography.headlineSmall,
                color = Cores.texto,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            /// O ano e o clima, na linha de baixo. O nome do clima vem do
            /// servidor — é o nome da estante que reivindicou o filme, e é o que
            /// liga o menu à prateleira de onde a caixa saiu.
            Text(
                text = listOfNotNull(disco.ano?.toString(), disco.climaNome.takeIf { it.isNotBlank() })
                    .joinToString(" · "),
                style = Tipo.pilula,
                color = clima.tinta,
            )

            Box(Modifier.height(20.dp))

            /// ⚠️ **`Continuar` só existe quando há de onde continuar** (§24).
            /// Um «continuar» que começa do zero é um «do começo» com outro nome.
            if (disco.temComoContinuar) {
                ItemDoMenu(
                    rotulo = "Continuar",
                    detalhe = disco.ponteiro,
                    clima = clima,
                    aoTocar = { aoTocar(disco.posicao ?: 0.0) },
                )
            }
            ItemDoMenu(
                rotulo = if (disco.temComoContinuar) "Do começo" else "Tocar",
                clima = clima,
                aoTocar = { aoTocar(0.0) },
            )
            /// Capítulos só existe se houver capítulo — e há disco sem nenhum.
            if (disco.capitulos.isNotEmpty() || cenas.isNotEmpty()) {
                ItemDoMenu(
                    rotulo = "Capítulos",
                    detalhe = "${maxOf(disco.capitulos.size, cenas.size)}",
                    clima = clima,
                    aoTocar = { nosCapitulos = true },
                )
            }
            /// **Legendas é ficha, não ação** — a web diz isso com todas as
            /// letras. Trocar de legenda é no player, com o filme na tela; aqui
            /// é a informação de que elas existem, que é o que um encarte diz.
            if (disco.legendas.isNotEmpty()) {
                ItemDoMenu(
                    rotulo = "Legendas",
                    detalhe = disco.legendas.joinToString(" · "),
                    clima = clima,
                    aoTocar = null,
                )
            }

            Box(Modifier.height(12.dp))
            /// ## ⚠️ A saída em `texto`, e não em `textoApagado` — 17/08/2026
            ///
            /// Medido nos pixels desta tela: o «‹ guardar o disco» dava **3,67:1**
            /// contra o fundo atrás dele, abaixo dos 4,5:1 que o tamanho dele
            /// pede. O resto do menu passava folgado — título 12,97:1, «Tocar»
            /// 11,98:1, o clima 5,73:1 — porque tudo isso é branco ou tinta viva.
            ///
            /// A causa é o **backdrop**: `textoApagado` (#8B8D9A) rende ~7:1 sobre
            /// o preto da casa, e este é o único texto do app desenhado sobre uma
            /// área lavada de arte, que aqui chegou a (71, 49, 39). O cinza que
            /// serve em toda tela não serve nesta.
            ///
            /// ⚠️ E o que está ilegível é **a saída**: fora o gesto do sistema,
            /// esta é a única porta de uma tela cheia. Um menu de disco de onde
            /// não se enxerga como sair é a §8b na peça mais cara de errar.
            ///
            /// `Cores.texto` resolve sem mexer na lavagem — ele continua pequeno e
            /// sem cromo de botão, que é o que o mantém secundário.
            TextButton(onClick = aoFechar) {
                Text("‹ guardar o disco", color = Cores.texto)
            }
        }
    }
}

/// A vinheta: 2,5s, e qualquer toque pula.
///
/// Quatro formas para doze climas — «doze animações distintas seriam doze coisas
/// pra manter», diz a folha da web, e a conta é a mesma aqui.
@Composable
private fun Vinheta(clima: Clima, nome: String) {
    val avanco by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(2_500, easing = LinearEasing),
        label = "vinheta",
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val centro = Offset(size.width / 2f, size.height / 2f)
            val maior = size.minDimension
            when (clima.vinheta) {
                /// **Risco** — terror, guerra, ação: uma lâmina que atravessa.
                FormaDaVinheta.Risco -> drawLine(
                    color = clima.tinta,
                    start = Offset(0f, centro.y),
                    end = Offset(size.width * avanco, centro.y),
                    strokeWidth = 3f,
                )
                /// **Íris** — animação, infantil, comédia: o círculo que abre,
                /// como o fecha-íris dos desenhos antigos.
                FormaDaVinheta.Iris -> drawCircle(
                    color = clima.tinta,
                    radius = maior * 0.5f * avanco,
                    center = centro,
                    style = Stroke(width = 2.5f),
                )
                /// **Onda** — faroeste, crime, drama: círculos concêntricos que
                /// se afastam.
                FormaDaVinheta.Onda -> repeat(3) { i ->
                    val fase = (avanco + i * 0.33f) % 1f
                    drawCircle(
                        color = clima.tinta.copy(alpha = (1f - fase) * 0.8f),
                        radius = maior * 0.5f * fase,
                        center = centro,
                        style = Stroke(width = 2f),
                    )
                }
                /// **Brilho** — documentário, sci-fi, romance: um halo que
                /// acende e assenta.
                FormaDaVinheta.Brilho -> drawCircle(
                    brush = Brush.radialGradient(
                        listOf(clima.tinta.copy(alpha = 0.55f * avanco), Color.Transparent),
                        center = centro,
                        radius = maior * 0.5f,
                    ),
                    radius = maior * 0.5f,
                    center = centro,
                )
            }
        }
        Text(
            text = nome.uppercase(),
            style = Tipo.rotulo,
            color = clima.tinta,
        )
    }
}

/// Um item do menu.
///
/// `aoTocar` nulo é **ficha, não ação** — o caso das legendas. Ele não fica
/// desabilitado com cara de botão quebrado: fica sem o traço e sem o toque, que
/// é como uma linha de encarte se parece.
@Composable
private fun ItemDoMenu(
    rotulo: String,
    clima: Clima,
    detalhe: String? = null,
    aoTocar: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (aoTocar == null) Modifier else Modifier.clickable(onClick = aoTocar))
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        /// O traço de seleção, à esquerda — o cursor de um menu de disco, que
        /// nunca foi um retângulo com fundo.
        Box(
            Modifier
                .width(if (aoTocar == null) 2.dp else 14.dp)
                .height(2.dp)
                .background(if (aoTocar == null) Cores.linha else clima.tinta),
        )
        Text(
            text = rotulo,
            style = MaterialTheme.typography.titleMedium,
            color = if (aoTocar == null) Cores.textoApagado else Cores.texto,
        )
        detalhe?.let {
            Text(text = it, style = Tipo.pilula, color = Cores.textoApagado, maxLines = 1)
        }
    }
}

/// A grade de capítulos.
///
/// ## As doze molduras vazias
///
/// Elas são §15 — «moldura vazia em vez de carregando» — e o motivo aqui é o
/// mesmo das prateleiras da locadora: as miniaturas vêm de outra rota, e sem as
/// molduras a grade nasce com zero de altura e **empurra a tela pra cima** quando
/// as imagens chegam.
@Composable
private fun Capitulos(
    disco: MenuDoDisco,
    cenas: List<Cena>,
    arte: (String?) -> String?,
    clima: Clima,
    aoEscolher: (Double) -> Unit,
    aoVoltar: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = aoVoltar) {
            Text("‹ menu", color = clima.tinta)
        }

        /// A legenda diz **de onde vieram** os capítulos, e é a diferença entre
        /// um dado do disco e uma conta do app. §18: o app não deixa parecer que
        /// dividiu o filme quando foi o autor do disco quem dividiu — nem o
        /// contrário.
        val doDisco = cenas.any { it.origem == "capitulo" } || disco.capitulos.isNotEmpty()
        Text(
            text = if (doDisco) "nos cortes do disco" else "divididos pelo relógio",
            style = Tipo.pilula,
            color = Cores.textoApagado,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 132.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (cenas.isEmpty()) {
                items(List(12) { it }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Cores.fundoElevado),
                    )
                }
            } else {
                items(cenas, key = { it.segundos }) { cena ->
                    Column(
                        modifier = Modifier.clickable { aoEscolher(cena.segundos) },
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Cores.fundoElevado),
                        ) {
                            arte(cena.imagem)?.let {
                                AsyncImage(
                                    model = it,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Text(
                            text = relogioDaCena(cena.segundos),
                            style = Tipo.pilula,
                            color = Cores.textoApagado,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/// `1:23:45` — o timecode de uma cena.
internal fun relogioDaCena(segundos: Double): String {
    val total = segundos.toLong()
    return if (total >= 3600) {
        "%d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
    } else {
        "%d:%02d".format(total / 60, total % 60)
    }
}

/// A deriva do fundo: um passeio lento de 4% sobre a arte.
///
/// Separado num modificador porque é o único enfeite desta tela que precisa de
/// `graphicsLayer`, e enterrá-lo no meio do `AsyncImage` esconderia o motivo: a
/// arte é desenhada maior que a moldura pra ter folga, senão a borda apareceria
/// quando ela anda.
private fun Modifier.graphicsLayerDeriva(deriva: Float): Modifier =
    this.graphicsLayer {
        scaleX = 1.06f
        scaleY = 1.06f
        translationX = -deriva * 24f
        translationY = deriva * 10f
    }

/// O clima, em cor e forma.
///
/// ## ⚠️ O índice **é** o contrato
///
/// Ele vem do servidor e é a posição na lista `ESTANTES` da locadora. Mexer na
/// ordem de lá sem mexer aqui troca o clima de todo mundo — o comentário é da
/// web, e vale igual.
///
/// ## A paleta daqui é exceção deliberada
///
/// O §12 fechou a paleta do app, e esta tela sai dela de propósito. A decisão do
/// `IDEIAS.md` §3.7 é explícita: «o estilo sai da temática do filme — comédia e
/// terror não ganham o mesmo menu». Um menu de disco não é cromo do produto; é a
/// arte da edição especial, e ela nunca combinou com o resto da estante.
///
/// ## O que sobrou da tabela da web, e o que não veio
///
/// Lá cada clima tem escala, raiz, andamento, timbre e corte de filtro — os
/// cinco campos do **sintetizador**, que a §3 da espec vetou nesta versão. Aqui
/// ficaram os dois que sobrevivem sem som: a **tinta** e a **forma da vinheta**.
/// Os nomes e a ordem são os mesmos, pra o dia em que o som entrar não precisar
/// reescrever a tabela.
internal data class Clima(val tinta: Color, val vinheta: FormaDaVinheta)

internal enum class FormaDaVinheta { Risco, Iris, Onda, Brilho }

private val CLIMAS: List<Clima> = listOf(
    Clima(corDeHex("#8c1c1c")!!, FormaDaVinheta.Risco), // 0 · Terror
    Clima(corDeHex("#c08a3e")!!, FormaDaVinheta.Onda), // 1 · Faroeste
    Clima(corDeHex("#6f7a52")!!, FormaDaVinheta.Risco), // 2 · Guerra
    Clima(corDeHex("#5b7f95")!!, FormaDaVinheta.Brilho), // 3 · Documentário
    Clima(corDeHex("#d97ab0")!!, FormaDaVinheta.Iris), // 4 · Animação
    Clima(corDeHex("#e0b04a")!!, FormaDaVinheta.Iris), // 5 · Infantil
    Clima(corDeHex("#4fb3c8")!!, FormaDaVinheta.Brilho), // 6 · Ficção científica
    Clima(corDeHex("#d9762b")!!, FormaDaVinheta.Risco), // 7 · Ação e aventura
    Clima(corDeHex("#4a5f9e")!!, FormaDaVinheta.Onda), // 8 · Crime e suspense
    Clima(corDeHex("#e08a5a")!!, FormaDaVinheta.Iris), // 9 · Comédia
    Clima(corDeHex("#c4708c")!!, FormaDaVinheta.Brilho), // 10 · Romance
    Clima(corDeHex("#a08258")!!, FormaDaVinheta.Onda), // 11 · Drama
)

/// O clima de um índice. Fora da faixa cai no **11 · Drama**, que é o sumidouro
/// da web pelo mesmo motivo: é o clima que menos afirma coisa alguma.
internal fun climaDe(indice: Int?): Clima = CLIMAS.getOrElse(indice ?: 11) { CLIMAS[11] }
