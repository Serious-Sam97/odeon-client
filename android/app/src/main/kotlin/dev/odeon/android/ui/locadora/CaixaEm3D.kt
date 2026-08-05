package dev.odeon.android.ui.locadora

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import dev.odeon.android.ui.Cores

/// A caixa em três dimensões, e **o dedo é o controle**.
///
/// ## Decidido em 05/08/2026
///
/// > «no modo locadora os itens vêm em 3D? a capa em 3D, o cd, o vhs a fita
/// > etc… igual no web. usar o touch para isso vai ser muito legal.»
///
/// O que existia antes eram duas camadas em pose fixa — bonito e imóvel, porque
/// as camadas não dividiam ponto de fuga. Toda a explicação está no
/// `Projecao.kt`; aqui é só o uso dela.
///
/// ## Cada lado continua sendo um `Composable`
///
/// E é o ganho de não ter ido pra OpenGL: a capa é o mesmo `AsyncImage` do Coil,
/// com o mesmo cache e o mesmo token de mídia. O que a matriz faz é desenhar
/// esse conteúdo **no plano da face**, com a perspectiva de todos os outros.
///
/// O `drawWithContent` é o que permite isso: ele deixa aplicar uma matriz 4×4
/// arbitrária em volta do desenho do filho, coisa que a `graphicsLayer` não
/// aceita — ela expõe `rotationX`/`rotationY` soltos, que é justamente o que não
/// compõe.
@Composable
fun CaixaEm3D(
    largura: Dp,
    altura: Dp,
    espessura: Dp,
    modifier: Modifier = Modifier,
    /// A pose de fora, quando alguém quer controlá-la (o palco anima a entrada).
    /// `null` deixa a caixa cuidar da própria pose.
    poseControlada: Pose? = null,
    /// Se o dedo pode girar. Na estante não pode: lá o arrasto é da fileira, e
    /// disputar o gesto faria a lista não rolar.
    giravel: Boolean = false,
    /// Quanto a tampa está aberta, em graus. Zero é a caixa fechada.
    abertura: Float = 0f,
    aoTocar: (() -> Unit)? = null,
    /// Tocar na **metade direita** — a aresta oposta à dobradiça. É o gesto de
    /// abrir, e ele é separado do `aoTocar` porque são coisas diferentes: um
    /// pega a caixa, o outro a abre.
    aoTocarNaAbertura: (() -> Unit)? = null,
    /// O conteúdo de cada lado. Recebe o quanto aquele lado pega de luz — quem
    /// desenha decide o que fazer com isso, e todos escurecem por igual.
    lado: @Composable (Lado, Float) -> Unit,
) {
    /// ## O giro com inércia — «melhore o movimento»
    ///
    /// A primeira versão era `animateFloatAsState` sobre um estado: o dedo
    /// empurrava, e ao soltar a caixa voltava ao repouso por uma mola. Faltava a
    /// metade que faz um objeto parecer objeto — **ele continua andando quando a
    /// mão sai**.
    ///
    /// Agora são dois `Animatable`:
    ///
    /// 1. enquanto o dedo está na tela, `snapTo` — o giro segue o dedo sem
    ///    atraso, porque uma mola no meio do arrasto é a caixa chegando depois
    ///    da mão;
    /// 2. ao soltar, `animateDecay` com a velocidade do gesto — a caixa **gira
    ///    solta**, e um empurrão forte dá a volta e mostra o verso;
    /// 3. quando a inércia acaba, ela **assenta na face mais próxima** (capa ou
    ///    verso), na pose de três quartos.
    ///
    /// O passo 3 é o que impede a caixa de parar de perfil, que é o único ângulo
    /// em que ela não é nada — nem capa, nem verso, uma linha.
    val giroYAnim = remember { androidx.compose.animation.core.Animatable(Pose.POSE_DE_REPOUSO_Y) }
    val giroXAnim = remember { androidx.compose.animation.core.Animatable(Pose.POSE_DE_REPOUSO_X) }
    val escopo = androidx.compose.runtime.rememberCoroutineScope()

    val poseDesenhada = poseControlada ?: Pose(giroYAnim.value, giroXAnim.value)

    val densidade = LocalDensity.current
    val medidas = with(densidade) {
        Medidas(largura.toPx(), altura.toPx(), espessura.toPx())
    }
    val distancia = medidas.largura * 8f

    val haptico = LocalHapticFeedback.current

    Box(
        modifier = modifier
            /// A caixa ocupa **a largura mais a espessura**: a lombada nasce à
            /// esquerda da capa e precisa de chão. Sem isso ela é recortada pela
            /// borda do próprio elemento, e o defeito parece um erro de arte.
            .width(largura + espessura)
            .height(altura)
            .then(
                if (!giravel) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        /// A velocidade do gesto, medida pelo próprio Compose —
                        /// é ela que vira inércia ao soltar.
                        val rastreador = androidx.compose.ui.input.pointer.util.VelocityTracker()

                        detectDragGestures(
                            onDragStart = { rastreador.resetTracking() },
                            onDragEnd = {
                                /// Graus por segundo, da velocidade em pixels.
                                val vx = rastreador.calculateVelocity().x *
                                    Pose.GRAU_POR_PIXEL_HORIZONTAL
                                escopo.launch {
                                    /// Pra onde o gesto apontava — ver `faceAlvo`.
                                    val destino = Pose.faceAlvo(giroYAnim.value, vx)
                                    giroYAnim.animateTo(
                                        targetValue = destino,
                                        animationSpec = spring(
                                            dampingRatio = 0.78f,
                                            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                                        ),
                                    )
                                    /// Chegando, o ângulo volta à forma canônica.
                                    /// É invisível — mesma pose — e é o que
                                    /// impede o número de crescer sem fim em quem
                                    /// fica girando a caixa.
                                    giroYAnim.snapTo(Pose.daVolta(giroYAnim.value))
                                }
                                escopo.launch {
                                    giroXAnim.animateTo(
                                        targetValue = Pose.POSE_DE_REPOUSO_X,
                                        animationSpec = spring(),
                                    )
                                }
                            },
                            onDragCancel = {
                                escopo.launch {
                                    giroYAnim.animateTo(Pose.faceAlvo(giroYAnim.value, 0f), spring())
                                }
                                escopo.launch { giroXAnim.animateTo(Pose.POSE_DE_REPOUSO_X, spring()) }
                            },
                            onDrag = { mudanca, arrasto ->
                                mudanca.consume()
                                rastreador.addPosition(mudanca.uptimeMillis, mudanca.position)
                                escopo.launch {
                                    /// ## ⚠️ A régua do sinal: **a superfície
                                    /// segue o dedo**
                                    ///
                                    /// «Parece que a caixa vai pro lado contrário
                                    /// do dedo», disse o dono. A primeira reação
                                    /// foi inverter a horizontal — e estava
                                    /// errada: quem estava invertido era o eixo
                                    /// **vertical**, e o horizontal já obedecia.
                                    ///
                                    /// A conta que decide não é o gosto, é a
                                    /// projeção. O centro da capa está em
                                    /// `(0, 0, e/2)`, e depois de girado ele cai
                                    /// em `x = (e/2)·sen(giroY)` e
                                    /// `y = (e/2)·sen(giroX)`. Ou seja: **giro
                                    /// positivo move a capa pra direita e pra
                                    /// baixo**. Pra ela seguir o dedo, os dois
                                    /// eixos somam o arrasto — e é só isso.
                                    ///
                                    /// O vertical vinha subtraindo, com um
                                    /// comentário que dizia «invertido de
                                    /// propósito». Era invertido por engano.
                                    ///
                                    /// Sem `daVolta` aqui: durante o arrasto o
                                    /// ângulo é **contínuo**, e é o que permite ao
                                    /// encaixe saber pra que lado a mão ia.
                                    giroYAnim.snapTo(
                                        giroYAnim.value + arrasto.x * Pose.GRAU_POR_PIXEL_HORIZONTAL,
                                    )
                                    giroXAnim.snapTo(
                                        (giroXAnim.value + arrasto.y * Pose.GRAU_POR_PIXEL_VERTICAL)
                                            .coerceIn(-Pose.TETO, Pose.TETO),
                                    )
                                }
                            },
                        )
                    }
                },
            )
            .then(
                if (aoTocar == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            /// O tique seco de mexer num objeto — o mesmo da
                            /// versão anterior, e continua sendo o leve: pegar a
                            /// fita é que leva a batida.
                            haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            aoTocar()
                        }
                    }
                },
            )
            .then(
                if (aoTocarNaAbertura == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { onde ->
                            /// A metade direita é a abertura; a esquerda é a
                            /// dobradiça. Tocar na dobradiça não faz nada de
                            /// propósito — um gesto que funciona nos dois lados
                            /// não ensina de que lado a caixa abre.
                            if (onde.x > size.width / 2f) aoTocarNaAbertura()
                        }
                    }
                },
            ),
    ) {
        /// Do mais fundo pro mais próximo — a ordem do pintor. Ver
        /// `ladosVisiveis`.
        /// Do mais fundo pro mais próximo — a ordem do pintor. Ver
        /// `ladosVisiveis`.
        ladosVisiveis(poseDesenhada, medidas, abertura).forEach { qual ->
            val (larguraDoLado, alturaDoLado) = tamanhoDoLado(qual, medidas)
            val luz = luzNoLado(qual, poseDesenhada, medidas, abertura)

            /// A homografia deste lado: os quatro cantos do retângulo do
            /// conteúdo, mapeados nos quatro cantos projetados.
            ///
            /// ⚠️ `setPolyToPoly` e não uma `Matrix` do Compose, e a primeira
            /// tentativa provou por quê: a conversão do Compose pra
            /// `android.graphics.Matrix` recusa componente em `z`, e a
            /// prateleira apareceu **vazia**. Ver `Projecao.kt`.
            val homografia = remember(qual, poseDesenhada, medidas, abertura) {
                val origem = floatArrayOf(
                    0f, 0f,
                    larguraDoLado, 0f,
                    larguraDoLado, alturaDoLado,
                    0f, alturaDoLado,
                )
                val destino = cantosNaTela(
                    lado = qual,
                    pose = poseDesenhada,
                    m = medidas,
                    distancia = distancia,
                    abertura = abertura,
                    centroX = larguraDoLado / 2f,
                    centroY = alturaDoLado / 2f,
                )
                android.graphics.Matrix().apply {
                    setPolyToPoly(origem, 0, destino, 0, 4)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredSize(
                        with(densidade) { larguraDoLado.toDp() },
                        with(densidade) { alturaDoLado.toDp() },
                    )
                    .drawWithContent {
                        drawIntoCanvas { it.nativeCanvas.save() }
                        drawIntoCanvas { it.nativeCanvas.concat(homografia) }
                        drawContent()
                        drawIntoCanvas { it.nativeCanvas.restore() }
                    },
            ) {
                lado(qual, luz)
            }
        }
    }
}

/// A sombra que a luz da face joga por cima do conteúdo.
///
/// Um véu preto com a opacidade do que **falta** de luz. Aplicado por cima de
/// qualquer conteúdo — pôster, papel da contracapa, madeira da lombada —, é o
/// que faz as quatro faces parecerem o mesmo objeto sob a mesma lâmpada, em vez
/// de quatro desenhos separados.
@Composable
fun BoxScope.VeuDeLuz(luz: Float) {
    if (luz >= 0.999f) return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = (1f - luz).coerceIn(0f, 0.6f))),
    )
}

/// A moldura padrão de um lado: fundo escuro e o véu por cima.
@Composable
fun LadoSimples(
    luz: Float,
    cor: Color = Cores.fundoElevado,
    conteudo: @Composable BoxScope.() -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(cor)) {
        conteudo()
        VeuDeLuz(luz)
    }
}
