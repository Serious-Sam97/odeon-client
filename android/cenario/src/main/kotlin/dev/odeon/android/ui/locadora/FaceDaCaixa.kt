package dev.odeon.android.ui.locadora

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.odeon.android.ui.Texto
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.Serifada
import dev.odeon.android.ui.chega
import kotlin.math.sin

/// O desenho de cada lado de uma caixa da vitrine.
///
/// Separado do `CaixaEm3D` porque aquele é geometria e este é arte: o mesmo
/// projetor serve pra caixa da estante, pra caixa na mão e — quando ela existir
/// — pra qualquer outra coisa que tenha lados.
///
/// ## Os dois materiais — «o 3D de ambos tá absurdamente feio»
///
/// A queixa do dono tinha um diagnóstico embaixo: a caixa não era **de** nada.
/// As faces menores eram degradês de cinza de interface, iguais pra qualquer
/// obra e qualquer era — e um objeto sem material é um polígono.
///
/// Agora o corte VHS × DVD escolhe o material, e tudo o mais sai dele:
///
/// | | DVD | VHS |
/// |---|---|---|
/// | casco | plástico preto | papelão tingido da cor da obra |
/// | capa | arte sangrada | arte sangrada |
/// | brilho | estreito e forte — verniz | largo e fraco — fosco |
///
/// A capa do DVD teve moldura de plástico em volta do encarte (o keep case
/// real tem) e o dono cortou em 07/08 — na tela ela lia como borda preta
/// defeituosa. O corte de formato ficou com a espessura, a lombada e o brilho.
@Composable
fun FaceDaCaixa(
    lado: Lado,
    luz: Float,
    /// A pose do quadro. É dela que o brilho sabe **onde** estar — um reflexo
    /// que não anda com o giro é um adesivo branco colado na capa.
    pose: Pose,
    titulo: String,
    arte: String?,
    cor: Color?,
    /// Fita ou disco — o `ultimo_ano_vhs` chegando ao objeto.
    ehVhs: Boolean = false,
    /// O ano, pra lombada. As lombadas da pilha de referência dizem o que a
    /// caixa é sem ela sair da estante — e o ano é dado que o acervo tem.
    ano: Int? = null,
    /// O id da obra. O código de catálogo `OD-XXXXX` que ele gerava **saiu da
    /// lombada** junto com a versão de linha única que o dono reprovou — hoje o
    /// derivado do id que sobrevive é o código de barras do verso. O parâmetro
    /// fica: é a identidade da caixa, e a próxima face que precisar dela não
    /// deveria ter que reabrir os call sites.
    id: String? = null,
    /// Quantas temporadas, quando a caixa é de **coleção**. Zero em tudo o mais,
    /// e aí a faixa não nasce — ver a `Lado.Capa`.
    temporadas: Int = 0,
    verso: (@Composable BoxScope.() -> Unit)? = null,
) {
    /// O casco: a cor do objeto por baixo de qualquer impressão.
    ///
    /// ⚠️ O papelão **é tingido da cor dominante** e o plástico não — papelão de
    /// locadora era impresso na cor do filme, e plástico de keep case era preto
    /// em qualquer filme. Sem `dominant_color`, o cinza elevado da casa (§18:
    /// nunca uma cor sorteada).
    val casco = if (ehVhs) {
        lerp(Color.Black, cor ?: Cores.fundoElevado, 0.30f)
    } else {
        Color(0xFF101014)
    }

    when (lado) {
        Lado.Capa -> BoxWithConstraints(
            /// Os cantos arredondados são do objeto, não do estilo: keep case e
            /// estojo de fita têm canto vivo de menos de 2mm — 3% aqui. O que
            /// aparece por trás do canto recortado é a face de trás da própria
            /// caixa, que é exatamente o que um canto arredondado mostra.
            ///
            /// `BoxWithConstraints` porque o selo de formato e a banda escalam
            /// com a face: a mesma capa mora na estante (102dp) e no palco
            /// (~370dp), e um selo de tamanho fixo estaria errado num dos dois.
            Modifier.fillMaxSize().clip(RoundedCornerShape(percent = 3)).background(casco),
            contentAlignment = Alignment.Center,
        ) {
            val larguraDaFace = maxWidth
            /// ⚠️ **A moldura do DVD existiu e saiu** — 07/08/2026. O encarte
            /// parava a 3,5% da borda e o plástico aparecia em volta, como no
            /// keep case real; na tela o dono leu defeito, não material: «os
            /// DVDs estão com essa borda preta na capa». A arte sangra nos dois
            /// formatos agora — quem diferencia DVD de VHS é a espessura, a
            /// lombada e o brilho, que já fazem isso sem roubar capa.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (arte != null) {
                    /// ## O corte da arte, por formato — e a conta que o decide
                    ///
                    /// O pôster é 2:3 (0,667). A face do DVD é 0,711 — mais
                    /// larga — e o `Crop` centrado comia ~6% na vertical,
                    /// metade em cima: o topo do «ONZE HOMENS» foi embora, e o
                    /// dono viu. A do VHS é 0,551 — bem mais estreita — e o
                    /// corte era ~17% **nas laterais**: o «Sapo» do Ichabod.
                    ///
                    /// | | conserto |
                    /// |---|---|
                    /// | DVD | o corte fica (é pequeno) mas ancora no **topo**: o que se perde é o pé, onde moram créditos — nunca o título |
                    /// | VHS | **meio corte, meio espremida** — ver abaixo |
                    ///
                    /// ## ⚠️ O VHS teve banda de papelão embaixo, e durou um build
                    ///
                    /// A primeira resposta ao corte foi arte inteira em 2:3 com a
                    /// sobra virando papelão tingido — de época, mas em obra de
                    /// cor escura a banda lia como defeito: «tá com uma borda
                    /// preta embaixo, deixe full». Full de verdade custa 21% em
                    /// algum lugar; dividido ao meio, são ~9% cortados das
                    /// laterais e ~9% de compressão horizontal — cada metade
                    /// pequena demais pra ser vista sozinha, e o «Sapo» da borda
                    /// sobrevive quase inteiro.
                    if (ehVhs) {
                        AsyncImage(
                            model = arte,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .requiredWidth(larguraDaFace * 1.10f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    scaleX = 1f / 1.10f
                                },
                        )
                    } else {
                        AsyncImage(
                            model = arte,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    Box(
                        Modifier.fillMaxSize().background(cor ?: Cores.fundoElevado),
                        contentAlignment = Alignment.Center,
                    ) {
                        Texto(
                            text = titulo,
                            /// ⚠️ **Estes três números eram o `labelSmall` do
                            /// Material**, e viraram literais na T0 — não há
                            /// `MaterialTheme` neste módulo, e não pode haver
                            /// (ver `Texto.kt`). São o valor que estava valendo,
                            /// copiado, e não um tamanho novo.
                            ///
                            /// ⚠️ E ele é o **único** texto desta face que não
                            /// escala com ela: os vizinhos todos derivam de
                            /// `larguraDaFace`, porque a mesma capa mora na
                            /// estante (102dp) e no palco (~370dp). Este fica em
                            /// 11sp nos dois. Não é conserto desta leva — é a
                            /// capa sem arte, que é o caso raro — mas é
                            /// exatamente o defeito que o selo do nível cobrou
                            /// na oitava rodada, e está anotado pra T3.
                            style = TextStyle(
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                letterSpacing = 0.5.sp,
                            ),
                            color = Cores.texto,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                }
            }

            /// **A faixa de temporadas** — a última dívida do §8 do
            /// `PARIDADE-ANDROID.md`.
            ///
            /// ## Ela existe porque uma série é **uma** caixa
            ///
            /// A locadora não expõe 21 fitas de Breaking Bad: expõe uma caixa de
            /// coleção. Sem a faixa, ela é indistinguível de um filme — e o que
            /// a pessoa pega na mão tem vinte horas dentro, não duas.
            ///
            /// ## A tinta é a da própria obra, escurecida
            ///
            /// `color-mix(in oklab, var(--cor) 68%, #000)` na folha (`:4499`).
            /// A faixa é a **cinta impressa na capa**, então ela tem que ser da
            /// caixa — e escurecida porque branco sobre a cor pura da arte não
            /// se lê em metade do acervo. Sem `dominant_color`, a linha da casa:
            /// nunca uma cor sorteada (§18).
            ///
            /// ⚠️ **7px na web, 8sp aqui, e a proporção foi quebrada de
            /// propósito.** Lá a caixa tem 130px de largura e a letra 7px — 5,4%.
            /// Os mesmos 5,4% nos 96dp desta caixa dariam **5,2sp**, que não é
            /// tamanho de texto, é textura. É o mesmo erro que o selo do nível
            /// cobrou na oitava rodada: mesma proporção, caixas diferentes,
            /// resultados diferentes na tela.
            if (temporadas > 0) {
                Texto(
                    text = "$temporadas ${if (temporadas == 1) "TEMPORADA" else "TEMPORADAS"}",
                    /// Era `Tipo.rotulo.copy(...)`, e do rótulo ele só herdava o **peso** —
                    /// corpo e espaçamento já vinham sobrescritos aqui. O
                    /// `Tipo` ficou no `:app` (§3.3), e o que ele emprestava
                    /// está escrito abaixo.
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        letterSpacing = 0.14.em,
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.lerp(
                                Color.Black,
                                cor ?: Cores.destaque,
                                0.68f,
                            ),
                        )
                        .padding(vertical = 3.dp),
                )
            }

            /// ## O selo do formato na capa — «ter um icon de DVD em algum lugar»
            ///
            /// Toda capa da pilha de referência tem o logotipo do formato num
            /// canto. É desenhado (§15, zero bytes) e escala com a face. No VHS o
            /// equivalente de época é a **banda da distribuidora** no topo — e a
            /// distribuidora desta locadora é honesta: é o Odeon.
            if (ehVhs) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(vertical = larguraDaFace * 0.016f)
                        .align(Alignment.TopCenter),
                    contentAlignment = Alignment.Center,
                ) {
                    Texto(
                        text = "ODEON · VIDEO",
                        style = TextStyle(
                            fontFamily = Serifada,
                            fontSize = (larguraDaFace.value * 0.052f).sp,
                            letterSpacing = 0.3.em,
                            color = Cores.destaque,
                        ),
                        maxLines = 1,
                    )
                }
            } else {
                SeloDVD(
                    largura = larguraDaFace * 0.17f,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = larguraDaFace * 0.055f,
                            /// Sobe quando a cinta de temporadas ocupa o pé.
                            bottom = larguraDaFace * 0.05f +
                                if (temporadas > 0) larguraDaFace * 0.09f else 0.dp,
                        ),
                )
            }

            BrilhoQueCorre(pose = pose, ehVhs = ehVhs)
            VeuDeLuz(luz)
        }

        /// ## A lombada de duas tintas — a «opção C», escolhida em 06/08/2026
        ///
        /// Cinco redesenhos foram desenhados e o dono escolheu a de gráfica de
        /// estúdio: **um bloco de cor em cima, outro mais escuro embaixo, o fio
        /// dourado na divisa, a miniatura da capa emoldurada no bloco de baixo**
        /// — a fórmula da lombada do VHS d'*O Rei Leão* que ele mandou de
        /// referência. As duas tintas saem da própria capa (`tintasDaCapa`), e
        /// enquanto a paleta não chega vale a `dominant_color`, como antes.
        ///
        /// A versão anterior (ano · título · catálogo · formato numa linha) foi
        /// vista e reprovada: «essa lateral ainda tá muito merda». O catálogo
        /// `OD-XXXXX` saiu da lombada de propósito — a linha única era o que a
        /// entulhava — e continua vivo no código de barras do verso.
        Lado.Lombada -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val tintas = tintasDaCapa(arte, cor)
            val larguraDaLombada = maxWidth
            val alturaDaLombada = maxHeight
            /// Lombada fina não pendura miniatura nem quebra linha: nos 11dp do
            /// keep case da estante, uma moldura de 8dp seria um cisco e duas
            /// linhas de texto não cabem uma ao lado da outra.
            val larga = larguraDaLombada >= 16.dp

            Canvas(Modifier.fillMaxSize()) {
                val divisa = size.height * 0.575f
                val fio = size.height * 0.022f
                drawRect(color = tintas.cima, size = Size(size.width, divisa))
                drawRect(
                    color = Cores.destaque,
                    topLeft = Offset(0f, divisa),
                    size = Size(size.width, fio),
                )
                drawRect(
                    color = tintas.baixo,
                    topLeft = Offset(0f, divisa + fio),
                    size = Size(size.width, size.height - divisa - fio),
                )
            }

            /// ## O corpo do texto sai da largura da lombada, e ela é a **altura
            /// da linha**
            ///
            /// ⚠️ **0,40 com folga de 12 a 26sp, e era 0,17 preso entre 7 e
            /// 13,5.** O texto é deitado, então a largura da lombada não limita o
            /// comprimento dele: limita a **altura**. Com 0,17 ele ocupava 16–19%
            /// do que tinha — e os dois formatos erravam por lados opostos: o
            /// DVD (37,7dp de lombada no palco) batia no **piso** de 7sp, o VHS
            /// (84,8dp) batia no **teto** de 13,5. Nenhum dos dois chegava perto
            /// do que a lombada comportava, e o dono viu: «o texto da lateral tá
            /// pequeno, foto também».
            ///
            /// 0,40 é a proporção da caixa impressa de verdade — numa lombada de
            /// DVD (14mm) o título tem ~6mm. Dá 15sp no keep case e 26sp na fita.
            /// O teto continua existindo porque lombada de fita é gorda: sem ele,
            /// 0,40 de 85dp daria 34sp, e aí o título vira placa.
            ///
            /// ## ⚠️ E o **piso continua 7**, que é a estante e não o palco
            ///
            /// A primeira versão desta mudança subiu o piso pra 12sp junto com o
            /// resto — e teria quebrado a vitrine. As quatro lombadas do app não
            /// têm a mesma escala:
            ///
            /// | | largura | 0,40× |
            /// |---|---|---|
            /// | estante, DVD | 11dp | 4,4sp |
            /// | estante, VHS | 19dp | 7,6sp |
            /// | palco, DVD | 37,7dp | 15,1sp |
            /// | palco, VHS | 84,8dp | 33,9sp → teto |
            ///
            /// Com piso 12, os 11dp da estante receberiam 12sp de texto deitado —
            /// **mais alto que a própria lombada**, vazando por cima da capa. O
            /// piso existe pro caso pequeno; o teto, pro grande. Mexer num
            /// pensando no outro é o erro que esta tabela existe pra impedir.
            val corpo = (larguraDaLombada.value * 0.40f).coerceIn(7f, 26f).sp

            /// O título, no bloco de cima.
            ///
            /// ## ⚠️ Título grande **quebra a linha**, não vira reticência
            ///
            /// Pedido do dono, com todas as letras: «caso o nome seja grande
            /// demais, não coloque "…" — faça com que caiba na linha de baixo,
            /// mas que fique bonito». Deitado, a segunda linha corre **ao lado**
            /// da primeira, como as lombadas de título empilhado da pilha de
            /// referência. O `textAlign = Center` é o que equilibra as duas. A
            /// reticência só sobrevive como última defesa da terceira linha —
            /// e na lombada fina, onde fisicamente só há chão pra uma.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(alturaDaLombada * 0.55f),
                contentAlignment = Alignment.Center,
            ) {
                Texto(
                    text = titulo,
                    style = TextStyle(
                        fontFamily = Serifada,
                        fontSize = corpo,
                        lineHeight = corpo * 1.18f,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFCF8EE),
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.5f),
                            blurRadius = 3f,
                        ),
                        letterSpacing = 0.04.em,
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = if (larga) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .requiredWidth(alturaDaLombada * 0.50f)
                        .graphicsLayer { rotationZ = 90f },
                )
            }

            /// A miniatura da capa, emoldurada no fio dourado — a arte aparece
            /// na lombada como **selo impresso**, não como papel esticado (a
            /// versão esticada foi mockada e reprovada: «a frente fica zoada»).
            /// ⚠️ **0,86 da lombada, e eram 0,68** — o mesmo pedido do texto.
            /// A moldura dourada fica (é ela que amarra a lombada com as cenas
            /// emolduradas do verso); o que cresce é a foto dentro dela. Numa
            /// lombada de keep case isso ainda dá ~32dp de largura, que é limite
            /// **físico** e não de desenho: é o que 14mm de plástico comportam.
            val alturaDaMiniatura = larguraDaLombada * 0.86f * 1.45f
            if (larga && arte != null) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = alturaDaLombada * 0.65f)
                        .border(1.dp, Cores.destaque.copy(alpha = 0.85f))
                        .padding(1.5.dp),
                ) {
                    AsyncImage(
                        model = arte,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(larguraDaLombada * 0.86f)
                            .height(alturaDaMiniatura),
                    )
                }
            }

            /// `1949 · VHS`, abaixo da miniatura — e cada pedaço só entra se
            /// existir (§24).
            val teto = if (larga && arte != null) {
                alturaDaLombada * 0.65f + alturaDaMiniatura + 6.dp
            } else {
                alturaDaLombada * 0.62f
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = teto)
                    .height(alturaDaLombada - teto),
                contentAlignment = Alignment.Center,
            ) {
                Texto(
                    text = listOfNotNull(ano?.toString(), if (ehVhs) "VHS" else "DVD")
                        .joinToString(" · "),
                    /// Idem: do `Tipo.rotulo` esta chamada não herdava nada —
                    /// ela já sobrescrevia corpo, espaçamento **e** peso.
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = corpo * 0.72f,
                        letterSpacing = 0.12.em,
                    ),
                    color = Color(0xFFF2E8D5).copy(alpha = 0.85f),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .requiredWidth(alturaDaLombada * 0.3f)
                        .graphicsLayer { rotationZ = 90f },
                )
            }
            VeuDeLuz(luz)
        }

        /// O topo e a base: o casco de perfil. O degradê dá a aresta — sem ele
        /// são duas tarjas, e o olho não fecha o volume.
        ///
        /// A base é mais escura que o topo de propósito: ela é a face que
        /// encosta na prateleira, e nenhuma luz de loja chega ali.
        Lado.Topo -> Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(lerp(casco, Color.White, 0.10f), casco)),
            ),
        ) {
            VeuDeLuz(luz)
        }

        Lado.Base -> Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(casco, Color.Black)),
            ),
        ) {
            VeuDeLuz(luz)
        }

        /// A **lateral da abertura** — o lado por onde a caixa abre.
        ///
        /// Ela não é lisa como a lombada: numa caixa de verdade é aqui que as
        /// duas metades se encontram, e o que se vê é uma **fresta** no meio da
        /// espessura. É o detalhe que diz de que lado a caixa abre mesmo antes de
        /// alguém tocar nela — e o gesto de abrir é justamente deste lado.
        Lado.LateralDireita -> Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(lerp(casco, Color.Black, 0.45f), casco, lerp(casco, Color.Black, 0.45f)),
                ),
            ),
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color.Black.copy(alpha = 0.65f)),
            )
            VeuDeLuz(luz)
        }

        Lado.Contracapa -> Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(percent = 3)).background(casco),
        ) {
            verso?.invoke(this)
            VeuDeLuz(luz)
        }
    }
}

/// O logotipo do formato, desenhado — o «DVD» em itálico pesado sobre a elipse
/// com «VIDEO», que toda capa dos anos 2000 carrega num canto.
///
/// É aproximação e não cópia: a marca registrada tem traços próprios, e este é o
/// selo **da locadora** — perto o bastante pra memória reconhecer, desenhado com
/// dois textos e uma elipse (§15, zero bytes).
@Composable
private fun SeloDVD(largura: Dp, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(largura),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Texto(
            text = "DVD",
            style = TextStyle(
                fontSize = (largura.value * 0.44f).sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = (-0.02).em,
                color = Color.White.copy(alpha = 0.92f),
                shadow = Shadow(color = Color.Black.copy(alpha = 0.85f), blurRadius = 4f),
            ),
            maxLines = 1,
        )
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.width(largura * 0.86f).height(largura * 0.24f)) {
                drawOval(
                    color = Color.Black.copy(alpha = 0.45f),
                )
                drawOval(
                    color = Color.White.copy(alpha = 0.92f),
                    style = Stroke(width = size.height * 0.13f),
                )
            }
            Texto(
                text = "VIDEO",
                style = TextStyle(
                    fontSize = (largura.value * 0.14f).sp,
                    letterSpacing = 0.24.em,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.92f),
                ),
                maxLines = 1,
            )
        }
    }
}

/// O brilho que corre pela capa — o que faz o giro ser **visto** na superfície.
///
/// ## Por que ele existe
///
/// O verniz era um gradiente fixo, sempre no mesmo canto — e um reflexo parado é
/// um adesivo. Num objeto de verdade o reflexo é da **luz**, não da caixa: girar
/// a caixa faz ele varrer a superfície. É o sinal número um de volume, e era o
/// que faltava pro 3D «fazer sentido».
///
/// A posição sai do giro: no repouso (22°) ele mora no terço esquerdo, e cada
/// grau de giro o desloca — a conta de espelho de verdade seria o dobro do
/// ângulo, mas aqui a régua é a tela: 120° de giro varrem a capa inteira, que é
/// o alcance do gesto entre uma face e outra.
///
/// ## E o material decide a forma dele
///
/// | | faixa | pico |
/// |---|---|---|
/// | DVD (plástico) | estreita, 17% da largura | 0,20 — verniz |
/// | VHS (papelão) | larga, 55% | 0,07 — fosco |
///
/// É a mesma física do brilho da corda do varal: especular concentrado é
/// superfície dura, especular espalhado é fibra.
///
/// ## As arestas vêm de graça
///
/// O fio de luz na borda vertical que está mais perto de quem olha — esquerda
/// com a lombada avançando, direita com a abertura. É 1,5dp de branco com alfa
/// proporcional ao seno do giro: de frente não há aresta, de quase-perfil ela é
/// o desenho inteiro.

@Composable
private fun BoxScope.BrilhoQueCorre(pose: Pose, ehVhs: Boolean) {
    Canvas(Modifier.matchParentSize()) {
        val giro = Pose.daVolta(pose.giroY)
        val centro = size.width * (0.5f - giro / 120f)
        val meiaFaixa = size.width * (if (ehVhs) 0.55f else 0.17f)
        val pico = if (ehVhs) 0.07f else 0.20f

        /// A diagonal (o `end` desce 45% da altura) é herança do verniz da
        /// folha: reflexo de vitrine nunca é vertical, porque a lâmpada da loja
        /// está acima do olho.
        drawRect(
            brush = Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = pico), Color.Transparent),
                start = Offset(centro - meiaFaixa, 0f),
                end = Offset(centro + meiaFaixa, size.height * 0.45f),
            ),
        )

        val perto = sin(Math.toRadians(giro.toDouble())).toFloat()
        if (perto > 0.05f) {
            drawRect(
                color = Color.White.copy(alpha = 0.28f * perto),
                size = Size(1.5.dp.toPx(), size.height),
            )
        } else if (perto < -0.05f) {
            drawRect(
                color = Color.White.copy(alpha = 0.28f * -perto),
                topLeft = Offset(size.width - 1.5.dp.toPx(), 0f),
                size = Size(1.5.dp.toPx(), size.height),
            )
        }
    }
}
