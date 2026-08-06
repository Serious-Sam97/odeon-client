package dev.odeon.android.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.odeon.android.dados.FolhaDeSprites
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.MarcaDoNome

/// A timeline como **película**.
///
/// ## A folha de sprites já estava paga, e era usada por três segundos
///
/// O servidor gera uma grade com o filme inteiro em miniaturas — uma imagem só,
/// baixada uma vez. Até aqui ela servia a **um** propósito: um balãozinho que
/// aparecia durante o arrasto e sumia ao soltar. O resto do tempo, uma imagem
/// com 1h37 de filme dentro ficava na memória sem desenhar nada.
///
/// Aqui ela vira a barra. Você não arrasta até um tempo — arrasta até uma
/// **imagem**, e a imagem está lá antes de você tocar.
///
/// ## O que faz isso parecer película e não uma fileira de fotos
///
/// | | |
/// |---|---|
/// | **as perfurações** | duas fileiras, em cima e embaixo, com passo fixo em dp |
/// | **o já visto revelado** | o que passou tem cor cheia; o que vem está a 34% |
/// | **a lente** | o cabeçote do projetor no ponto atual, com o halo do `Facho` |
///
/// A terceira é a que amarra no resto do app: é a mesma lente que vive na barra
/// de navegação, aqui fazendo a única coisa que uma lente de projetor faz.
///
/// ## ⚠️ Sem folha, ela vira barra
///
/// Nem todo arquivo tem sprites — a rota devolve 404 pra quem o servidor não
/// varreu ainda. Sem folha não há filme pra desenhar, e inventar retângulos
/// coloridos no lugar seria a tela fingindo ter dado que não tem (§18). Aí a
/// [Tira] cai numa barra fina, que é o que havia antes.
@Composable
internal fun Tira(
    fracao: Float,
    duracaoMs: Long,
    folha: FolhaDeSprites?,
    urlDaFolha: String?,
    /// As doze cenas do filme, quando a folha de sprites não existe. Ver
    /// `EstadoDoPlayer.cenas`.
    cenas: List<dev.odeon.android.dados.Cena> = emptyList(),
    /// Monta a URL de uma imagem de cena.
    arteDaCena: (String) -> String? = { null },
    /// ## Deitado a tira **cresce**, e não encolhe
    ///
    /// A primeira reação a «o cromo come metade da tela deitado» foi encolher
    /// tudo, tira inclusive — pra 22dp. O dono corrigiu: «aumente o tamanho da
    /// timeline, está muito pequeno».
    ///
    /// E ele está certo pela forma da tela. Deitado sobra **largura** (914dp
    /// contra 411) e falta altura; a tira é a única peça do cromo que usa a
    /// largura inteira, então é a que mais ganha e a que menos custa. O que
    /// devolveu o espaço foram a tarja preta que saiu e as três fileiras que
    /// viraram duas — não o encolhimento da película.
    emPaisagem: Boolean = false,
    /// Quem mais está neste filme agora — ver `ModeloDoPlayer.naSala`.
    naSala: Map<String, dev.odeon.android.ui.player.NaSala> = emptyMap(),
    aoComecarArrasto: () -> Unit,
    aoArrastar: (Float) -> Unit,
    aoSoltar: () -> Unit,
) {
    var largura by remember { mutableFloatStateOf(1f) }
    val haptico = LocalHapticFeedback.current
    var casaAnterior by remember { mutableIntStateOf(Int.MIN_VALUE) }

    /// As casas do detente — a mesma conta da `Linha` que esta peça substitui:
    /// um tique a cada passo de no máximo 10min, com teto de 200 casas.
    val casas = if (duracaoMs > 0) {
        val passoMs = minOf(10 * 60 * 1000L, duracaoMs / 20)
        (duracaoMs / passoMs.coerceAtLeast(1L)).toInt().coerceIn(1, 200)
    } else {
        0
    }

    fun tiquear(f: Float): Float {
        if (casas > 0) {
            val casa = (f * casas).toInt()
            if (casa != casaAnterior) {
                if (casaAnterior != Int.MIN_VALUE) {
                    haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                casaAnterior = casa
            }
        }
        return f
    }

    val fracaoSegura = fracao.coerceIn(0f, 1f)

    /// A altura de desenho, e a largura de cada quadro. Deitado os dois crescem
    /// **juntos**: subir só a altura deixaria a célula quase quadrada, e recortar
    /// um quadro 16:9 num quadrado joga fora metade da cena.
    ///
    /// 62 por 38 (a altura menos o respiro das perfurações) dá 1,63 — perto o
    /// bastante de 16:9 pra o recorte tirar só as beiradas.
    val altura = if (emPaisagem) ALTURA_PAISAGEM else ALTURA
    val larguraDoQuadro = if (emPaisagem) LARGURA_DO_QUADRO_PAISAGEM else LARGURA_DO_QUADRO
    val larguraDaJanela = if (emPaisagem) LARGURA_DA_JANELA_PAISAGEM else LARGURA_DA_JANELA

    Box(
        modifier = Modifier
            .fillMaxWidth()
            /// A tira tem [ALTURA] de desenho, e o alvo é maior que ela pelo
            /// mesmo motivo que a barra fina tinha 24dp pra 3dp: dedo não tem a
            /// precisão do traço.
            .height(ALTURA + 12.dp)
            /// ## ⚠️ Tocar pula — e isto **faltava**
            ///
            /// A tira só escutava arrasto, e `detectHorizontalDragGestures` não
            /// dispara em toque parado: o dedo tem que **andar**. Na prática,
            /// encostar num ponto da timeline não fazia nada — o dono cobrou, e
            /// a `Linha` que ela substituiu tinha o mesmo buraco desde sempre.
            ///
            /// Dois `pointerInput` e não um: toque e arrasto são detectores
            /// diferentes, e empilhá-los no mesmo bloco faria um engolir o
            /// outro. Em blocos separados o Compose entrega o evento aos dois, e
            /// quem reconhecer primeiro fica com ele.
            .pointerInput(Unit) {
                largura = size.width.toFloat()
                detectTapGestures { toque ->
                    val onde = (toque.x / largura).coerceIn(0f, 1f)
                    aoComecarArrasto()
                    aoArrastar(onde)
                    aoSoltar()
                }
            }
            .pointerInput(Unit) {
                largura = size.width.toFloat()
                detectHorizontalDragGestures(
                    onDragStart = { toque: Offset ->
                        aoComecarArrasto()
                        casaAnterior = Int.MIN_VALUE
                        aoArrastar(tiquear((toque.x / largura).coerceIn(0f, 1f)))
                    },
                    onDragEnd = { aoSoltar() },
                    onDragCancel = { aoSoltar() },
                    onHorizontalDrag = { mudanca, _ ->
                        aoArrastar(tiquear((mudanca.position.x / largura).coerceIn(0f, 1f)))
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        /// ⚠️ **A tira existe mesmo sem folha de sprites** — 05/08/2026
        ///
        /// A primeira versão caía numa barra fina quando o servidor não tinha
        /// gerado os quadros, e o dono viu o resultado: o redesenho inteiro
        /// sumia, porque **nenhum arquivo deste acervo tem folha**.
        ///
        /// O erro foi de projeto. Uma tira de filme sem os fotogramas revelados
        /// **continua sendo uma tira de filme**: tem perfuração, tem célula, tem
        /// a janela do projetor passando por ela. O que falta é a imagem — e a
        /// imagem chega quando o servidor gerar, sem esta tela mudar de forma.
        ///
        /// E não é inventar dado (§18): célula escura é película **não
        /// revelada**, que é literalmente o estado do arquivo. Desenhar
        /// retângulos coloridos aleatórios no lugar dos quadros, aí sim, seria
        /// afirmar cena que não se sabe.
        ///
        /// Sem duração conhecida ainda não há o que dividir em células — e isso
        /// acontece de verdade em HLS, onde a duração só chega com o plano.
        if (duracaoMs <= 0) {
            BarraSimples(fracaoSegura)
            return@Box
        }

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(altura)
                .clip(RoundedCornerShape(2.dp))
                .background(Cores.fundoAfundado),
        ) {
            /// Quantos quadros cabem, e é a conta que decide se isto é uma tira
            /// ou uma tarja borrada.
            ///
            /// Cada quadro sai com [LARGURA_DO_QUADRO]; o que sobra na divisão é
            /// distribuído porque um resto de 40px no fim leria como quadro
            /// cortado — e quadro cortado numa tira é defeito de projeção.
            val quantos = (maxWidth / larguraDoQuadro).toInt().coerceIn(1, 40)
            val larguraReal = maxWidth / quantos

            Row(Modifier.fillMaxSize()) {
                repeat(quantos) { i ->
                    /// O instante do meio da faixa, e não o do começo: um quadro
                    /// que representa 8 minutos de filme deve mostrar o miolo
                    /// desses 8 minutos.
                    val emQue = ((i + 0.5f) / quantos) * (duracaoMs / 1000.0)
                    /// Já passou? A comparação é com a borda **direita** da
                    /// faixa: um quadro só conta como visto quando a lente
                    /// terminou de atravessá-lo.
                    val visto = (i + 1f) / quantos <= fracaoSegura
                    /// A cena mais próxima deste instante, quando não há folha.
                    ///
                    /// ⚠️ **Mais próxima, e não a anterior.** Com doze cenas num
                    /// filme de 1h37, cada uma cobre ~11 minutos; pegar sempre a
                    /// anterior faria a última célula de cada bloco mostrar uma
                    /// imagem de onze minutos atrás. A mais próxima erra por no
                    /// máximo metade disso, e erra pros dois lados.
                    val cena = if (folha == null) {
                        cenas.minByOrNull { kotlin.math.abs(it.segundos - emQue) }
                    } else {
                        null
                    }
                    QuadroDaTira(
                        folha = folha,
                        url = urlDaFolha,
                        urlDaCena = cena?.let { arteDaCena(it.imagem) },
                        segundo = emQue.toInt(),
                        largura = larguraReal,
                        visto = visto,
                    )
                }
            }

            /// ## A lavagem quente sobre o trecho já visto
            ///
            /// ⚠️ **Ela era fraca demais, e a queixa foi «a timeline é um negócio
            /// só cinza».** Com 0,06→0,22 de alfa sobre células escuras, e sem
            /// os fotogramas pra dar cor, não havia contraste nenhum entre o que
            /// passou e o que falta — a tira inteira lia como uma barra cinza.
            ///
            /// A função primária de uma timeline é **dizer onde você está**, e
            /// isso tem que sobreviver à ausência dos quadros. Agora a lavagem
            /// começa em 0,20 e termina em 0,52, e ganha uma **aresta acesa** na
            /// ponta: mesmo sem imagem nenhuma, dá pra ver de longe quanto do
            /// filme já correu.
            Box(
                Modifier
                    .fillMaxWidth(fracaoSegura)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Cores.destaque.copy(alpha = 0.20f),
                                Cores.destaque.copy(alpha = 0.52f),
                            ),
                        ),
                    ),
            )

            Perfuracoes(Modifier.align(Alignment.TopCenter))
            Perfuracoes(Modifier.align(Alignment.BottomCenter))
        }

        /// ## As marcas da casa
        ///
        /// Quem mais está neste filme agora, no ponto em que está. É o campo
        /// `quem_nome` que o servidor passou a mandar no evento `progress` em
        /// 05/08/2026 — e é a única coisa em qualquer player que faz uma casa de
        /// três pessoas parecer uma casa.
        ///
        /// ⚠️ **Elas ficam acima da tira, e não dentro.** Dentro, disputariam
        /// espaço com os fotogramas e cobririam justamente o que a tira existe
        /// pra mostrar. Acima, com a hastezinha descendo até a película, elas
        /// apontam sem tapar.
        naSala.forEach { (id, quem) ->
            val onde = (quem.posicaoEmSegundos * 1000.0 / duracaoMs).toFloat().coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth(onde).fillMaxHeight(),
                contentAlignment = Alignment.TopEnd,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(x = 9.dp, y = (-18).dp),
                ) {
                    /// A marca do nome — o **mesmo hash** da insígnia do canto,
                    /// do avatar do perfil e dos chips do balcão. Uma pessoa tem
                    /// a mesma cor em todo lugar do app, e é o que faz um disco
                    /// de 18dp identificar alguém sem escrever o nome.
                    MarcaDoNome(nome = quem.nome, tamanho = 18.dp)
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(7.dp)
                            .background(Color.White.copy(alpha = 0.45f)),
                    )
                }
            }
        }

        /// ## A janela do projetor
        ///
        /// Ela era um traço de 2dp. Virou **janela** por pedido do dono, e a
        /// mudança não é decorativa: um traço marca *uma posição*; uma janela
        /// com o quadro dentro diz que a película **está passando por ali**. É a
        /// peça que faz a tira parecer um projetor lendo filme, em vez de uma
        /// barra com uma marca.
        ///
        /// ⚠️ **Posicionada por dp medido, e não por `fillMaxWidth(fração)`.**
        /// A primeira versão aninhava uma caixa de largura fracionária com um
        /// `offset` pra centralizar, e a foto mostrou o resultado: dois traços
        /// finos no lugar da moldura. Fração encadeada com deslocamento é
        /// exatamente o defeito que a cortina já tinha cobrado uma vez — num
        /// `Row`, `fillMaxWidth(0.5f)` mede sobre o que sobrou.
        ///
        /// Com a largura na mão, a conta é uma linha: o centro da janela fica em
        /// `largura × fração`, e ela recua metade de si mesma.
        BoxWithConstraints(Modifier.fillMaxWidth().height(altura + 12.dp)) {
            val centro = maxWidth * fracaoSegura
            val esquerda = (centro - larguraDaJanela / 2)
                .coerceIn(0.dp, (maxWidth - larguraDaJanela).coerceAtLeast(0.dp))

            /// O derrame, num `Canvas` porque `Modifier.shadow` do Compose é
            /// sombra e não luz — ele escurece, e aqui é o contrário.
            /// ⚠️ **`align` antes de `offset`, nos três.** A primeira versão
            /// misturou as ordens e a foto mostrou o halo e a lente na ponta
            /// esquerda enquanto a moldura estava no meio: `offset` antes de
            /// `align` desloca e **depois** alinha, então o alinhamento come o
            /// deslocamento. Alinhar no início e deslocar em seguida faz o `x`
            /// ser absoluto a partir da borda esquerda, que é o que a conta
            /// acima calcula.
            Canvas(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = esquerda - larguraDaJanela)
                    .size(width = larguraDaJanela * 3, height = altura + 12.dp),
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Cores.destaqueQuente.copy(alpha = 0.30f),
                            Cores.destaqueQuente.copy(alpha = 0.07f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.minDimension * 1.1f,
                    ),
                    radius = size.minDimension * 1.1f,
                )
            }

            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = esquerda)
                    .width(larguraDaJanela)
                    .height(altura + 6.dp)
                    .border(2.dp, Cores.destaqueQuente, RoundedCornerShape(4.dp)),
            )

            /// A lente, no pé da janela — a mesma do `BarraDoFacho`.
            ///
            /// Ela mora **abaixo** da tira porque é de lá que a luz vem: o cone
            /// sobe da lente e atravessa a película, que é o que uma janela de
            /// projeção faz.
            Canvas(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = centro - 15.dp)
                    .size(width = 30.dp, height = 8.dp),
            ) {
                drawOval(
                    color = Cores.destaqueQuente,
                    topLeft = Offset(size.width * 0.30f, size.height * 0.25f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.40f, size.height * 0.5f),
                )
            }
        }
    }
}

/// O filtro de cor dos fotogramas.
///
/// ## ⚠️ A cor **já estava lá** — ela só não aparecia
///
/// O dono mandou uma referência com a tira colorida e perguntou se dava pra
/// chegar nela. A tentação é sortear uma paleta por célula; isso seria a tela
/// afirmando cor de cena que ela não conhece (§18).
///
/// Não precisa: os quadros são fotogramas **do filme**. Estavam apagados porque a
/// célula por ver desenhava a 34% de alfa sobre fundo escuro. Subir o alfa e
/// saturar mostra a cor que já está no arquivo — é revelar, e não pintar.
///
/// 1,45 e 70% são a intensidade «média» das três que foram propostas. A forte
/// (1,8 e 88%) ficava bonita e **matava a função primária**: com tudo brilhando,
/// a lavagem quente do trecho visto some no meio da cor e a tira deixa de
/// responder «onde eu estou».
///
/// ⚠️ **O efeito real depende do filme**, e isso não é defeito: *007* de 1969 tem
/// paleta lavada e vai saturar menos vistoso que um filme moderno. A referência
/// tinha cores muito separadas porque eram cenas de filmes diferentes; num filme
/// só, a tira é mais harmônica — que é o correto.
private fun filtroDeCor(): ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(SATURACAO) })

/// Um quadro da tira, recortado da folha.
/// Um quadro da tira, recortado da folha.
///
/// O recorte é o mesmo do `Miniatura`: a folha inteira entra numa caixa do
/// tamanho de **um** quadro, escalada por colunas e linhas, e empurrada até o
/// quadro certo cair na janela. Uma imagem, nenhuma requisição.
@Composable
private fun QuadroDaTira(
    folha: FolhaDeSprites?,
    url: String?,
    urlDaCena: String?,
    segundo: Int,
    largura: androidx.compose.ui.unit.Dp,
    visto: Boolean,
) {
    if (folha == null || url == null) {
        /// Sem folha, mas com cena: a imagem inteira na célula.
        ///
        /// `Crop` e não `FillBounds`: a cena é 16:9 e a célula é quase
        /// quadrada — esticar deformaria rosto, e um rosto achatado numa tira
        /// de 30dp lê como defeito de imagem.
        if (urlDaCena != null) {
            Box(
                Modifier
                    .width(largura)
                    .fillMaxHeight()
                    .padding(vertical = 5.dp, horizontal = 0.5.dp)
                    .background(Cores.fundoAfundado),
            ) {
                coil3.compose.AsyncImage(
                    model = urlDaCena,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = if (visto) 1f else ALFA_POR_VER,
                    colorFilter = filtroDeCor(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            return
        }

        /// A célula sem quadro: película virgem. A borda de cima e de baixo é a
        /// emulsão pegando a luz de raspão — sem ela a célula é um buraco preto,
        /// e uma tira de buracos não parece filme.
        Box(
            Modifier
                .width(largura)
                .fillMaxHeight()
                .padding(vertical = 5.dp, horizontal = 0.5.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (visto) 0.16f else 0.05f),
                            Color.White.copy(alpha = if (visto) 0.05f else 0.014f),
                            Color.White.copy(alpha = if (visto) 0.13f else 0.04f),
                        ),
                    ),
                ),
        )
        return
    }

    val indice = (segundo / folha.intervaloSegundos)
        .toInt()
        .coerceIn(0, (folha.quantosQuadros - 1).coerceAtLeast(0))
    val coluna = indice % folha.columns
    val linha = indice / folha.columns

    Box(
        Modifier
            .width(largura)
            .fillMaxHeight()
            /// O respiro vertical é onde as perfurações moram. Sem ele o quadro
            /// encostaria nelas e a tira viraria uma faixa de fotos.
            .padding(vertical = 5.dp)
            .background(Cores.fundoAfundado),
    ) {
        coil3.compose.AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            /// ⚠️ O alfa vive **aqui**, e não numa camada por cima.
            ///
            /// Um véu escuro sobre o não-visto apagaria também a lavagem quente
            /// do que já passou, porque as duas se somam na mesma pilha. Baixar
            /// o alfa do próprio quadro deixa o fundo afundado aparecer por
            /// trás — que é exatamente «película ainda não revelada».
            alpha = if (visto) 1f else ALFA_POR_VER,
            colorFilter = filtroDeCor(),
            modifier = Modifier
                .fillMaxSize()
                .layout { medivel, restricoes ->
                    val larguraTotal = restricoes.maxWidth * folha.columns
                    val alturaTotal = restricoes.maxHeight * folha.rows
                    val posto = medivel.measure(Constraints.fixed(larguraTotal, alturaTotal))
                    layout(restricoes.maxWidth, restricoes.maxHeight) {
                        posto.place(
                            x = -coluna * restricoes.maxWidth,
                            y = -linha * restricoes.maxHeight,
                        )
                    }
                },
        )
    }
}

/// Uma fileira de perfurações.
///
/// O passo é fixo em dp e não uma fração da largura: furo de película tem
/// tamanho de fabricação, e espaçá-lo proporcionalmente faria a tira parecer
/// mais «grossa» num tablet que num celular — que é o oposto do que material
/// físico faz.
@Composable
private fun Perfuracoes(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 1.5.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        repeat(28) {
            Box(
                Modifier
                    .size(width = 5.dp, height = 2.5.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White.copy(alpha = 0.30f)),
            )
        }
    }
}

/// O que havia antes, e continua servindo quem não tem folha de sprites.
@Composable
private fun BarraSimples(fracao: Float) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                .background(Cores.linha),
        )
        Box(
            Modifier.fillMaxWidth(fracao).height(3.dp)
                .clip(RoundedCornerShape(2.dp)).background(Cores.destaque),
        )
    }
}

/// ## 30dp, e o número sai de uma conta
///
/// A tira precisa ser alta o bastante pra um quadro 16:9 ser reconhecível e
/// baixa o bastante pra não comer filme. Com [LARGURA_DO_QUADRO] em 34dp, um
/// quadro 16:9 pede ~19dp de altura; mais os 5dp de respiro em cima e embaixo
/// (onde moram as perfurações) dá 29. Ficaram 30.
private val ALTURA = 30.dp

/// A tira deitada — ver o parâmetro `emPaisagem`.
private val ALTURA_PAISAGEM = 48.dp

/// Largura de cada quadro. Num celular deitado de 411dp isso dá **12 quadros**;
/// em pé, 11. Menos que isso e os quadros somem; mais e a tira vira mosaico.
private val LARGURA_DO_QUADRO = 34.dp

/// A largura da janela do projetor — um quadro e um respiro, pra a moldura não
/// encostar na imagem que ela emoldura.
private val LARGURA_DA_JANELA = 40.dp
private val LARGURA_DO_QUADRO_PAISAGEM = 62.dp
private val LARGURA_DA_JANELA_PAISAGEM = 70.dp

/// O alfa do que ainda não passou, e a saturação de tudo. Ver `filtroDeCor`.
private const val ALFA_POR_VER = 0.70f
private const val SATURACAO = 1.45f
