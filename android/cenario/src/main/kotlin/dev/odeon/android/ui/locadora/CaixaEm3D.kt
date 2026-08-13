package dev.odeon.android.ui.locadora

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
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
import kotlin.math.abs
import kotlin.math.sin

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
    /// O interior é de **disco** (cubo no fundo, travas de encarte na tampa) ou
    /// de **fita** (berço do cassete, frisos de molde)? Só importa com a caixa
    /// aberta — fechada, interior não existe. É o único pedaço de "arte" que a
    /// geometria carrega, porque o forro é desenhado aqui e não nas faces.
    interiorDeDisco: Boolean = true,
    /// A cor do casco, pro **lábio** da tampa (a meia-lateral que abre junto).
    /// Ele é a mesma matéria das faces — plástico preto no disco, papelão
    /// tingido na fita —, e quem sabe essa cor é quem desenha as faces.
    corDoCasco: Color = Color(0xFF101014),
    aoTocar: (() -> Unit)? = null,
    /// Tocar na **metade direita** — a aresta oposta à dobradiça. É o gesto de
    /// abrir, e ele é separado do `aoTocar` porque são coisas diferentes: um
    /// pega a caixa, o outro a abre.
    aoTocarNaAbertura: (() -> Unit)? = null,
    /// O conteúdo de cada lado. Recebe o quanto aquele lado pega de luz e a
    /// pose do quadro — a luz escurece por igual; a pose é o que deixa um
    /// brilho **correr** pela face enquanto a caixa gira, que é coisa que um
    /// número por face não conta.
    lado: @Composable (Lado, Float, Pose) -> Unit,
) {
    /// ## O giro com inércia — «melhore o movimento», depois «quero livre»
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
    ///    solta** e para onde o atrito a deixar.
    ///
    /// ⚠️ **Havia um passo 3** — assentar na face mais próxima, pra caixa nunca
    /// parar de perfil — e ele foi removido em 07/08/2026 a pedido do dono:
    /// «quero mover livremente e parar onde quiser». O perfil deixou de ser
    /// defeito e virou escolha da mão; a história do encaixe está no
    /// `onDragEnd`.
    val giroYAnim = remember { androidx.compose.animation.core.Animatable(Pose.POSE_DE_REPOUSO_Y) }
    val giroXAnim = remember { androidx.compose.animation.core.Animatable(Pose.POSE_DE_REPOUSO_X) }
    val escopo = androidx.compose.runtime.rememberCoroutineScope()

    val poseDesenhada = poseControlada ?: Pose(giroYAnim.value, giroXAnim.value)

    val densidade = LocalDensity.current
    val medidas = with(densidade) {
        Medidas(largura.toPx(), altura.toPx(), espessura.toPx())
    }
    /// ⚠️ **Seis larguras, e eram oito.** A câmera a oito larguras dava uma
    /// perspectiva quase isométrica — a caixa parecia recortada de papelão, e
    /// foi metade da queixa «o 3D tá sem sentido». Seis é a faixa do
    /// `perspective()` que a web usa em caixas desse tamanho; menos que isso a
    /// aresta próxima começa a inchar como grande-angular.
    val distancia = medidas.largura * 6f

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
                                /// ## O giro é livre, e a caixa para onde a mão
                                /// largou — 07/08/2026
                                ///
                                /// > «quero mover livremente e parar onde
                                /// > quiser — hoje parece que ele tem 2 faces
                                /// > pré-definidas que se movem sozinhas quando
                                /// > eu solto.»
                                ///
                                /// O encaixe de faces (`faceAlvo` — que custou
                                /// três versões pra acertar) foi **removido por
                                /// decisão do dono**. O que fica do gesto é a
                                /// inércia: um arremesso continua girando e
                                /// morre por atrito, como um objeto de verdade —
                                /// e onde morrer, ficou. Inclusive de perfil,
                                /// que era o ângulo que o encaixe existia pra
                                /// evitar; a escolha agora é da mão.
                                val vx = rastreador.calculateVelocity().x *
                                    Pose.GRAU_POR_PIXEL_HORIZONTAL
                                escopo.launch {
                                    giroYAnim.animateDecay(
                                        initialVelocity = vx,
                                        animationSpec = exponentialDecay(frictionMultiplier = 1.8f),
                                    )
                                    /// A forma canônica continua: quem gira dez
                                    /// voltas não acumula 3.600°.
                                    giroYAnim.snapTo(Pose.daVolta(giroYAnim.value))
                                }
                                /// O eixo vertical também fica onde largou — ele
                                /// já vem preso ao teto de ±42° no arrasto.
                            },
                            onDragCancel = {
                                escopo.launch { giroYAnim.snapTo(Pose.daVolta(giroYAnim.value)) }
                            },
                            onDrag = { mudanca, arrasto ->
                                mudanca.consume()
                                rastreador.addPosition(mudanca.uptimeMillis, mudanca.position)
                                /// O tique de **cruzar o perfil** — o instante em
                                /// que a caixa deixa de ser capa e passa a ser
                                /// verso. É o único ponto do giro em que existe
                                /// um evento, e é onde a mão espera sentir algo.
                                val antes = Pose.daVolta(giroYAnim.value)
                                val depois =
                                    Pose.daVolta(antes + arrasto.x * Pose.GRAU_POR_PIXEL_HORIZONTAL)
                                if ((abs(antes) < 90f) != (abs(depois) < 90f)) {
                                    haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
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
                            /// ## O alvo é a **caixa projetada**, não o retângulo
                            ///
                            /// O alvo era «a metade direita do componente» — e a
                            /// caixa girada não mora ali: a projeção desloca, e o
                            /// dedo em cima da abertura visível caía fora da
                            /// conta («apertar na abertura deveria abrir também,
                            /// não somente no canto do frontal»). Agora o toque
                            /// é testado contra os **mesmos cantos que o desenho
                            /// usa**: a lateral da abertura inteira, e a metade
                            /// direita da capa. A dobradiça segue não fazendo
                            /// nada de propósito — um gesto que funciona nos
                            /// dois lados não ensina de que lado a caixa abre.
                            ///
                            /// E **de costas nenhum lado abre** — vista do
                            /// verso, a direita da tela é a dobradiça espelhada,
                            /// e abrir dali é abrir através da caixa.
                            val poseDoToque =
                                poseControlada ?: Pose(giroYAnim.value, giroXAnim.value)
                            if (poseDoToque.mostrandoOVerso) return@detectTapGestures

                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val lateral = cantosNaTela(
                                lado = Lado.LateralDireita,
                                pose = poseDoToque,
                                m = medidas,
                                distancia = distancia,
                                centroX = cx,
                                centroY = cy,
                            )
                            val capa = cantosNaTela(
                                lado = Lado.Capa,
                                pose = poseDoToque,
                                m = medidas,
                                distancia = distancia,
                                centroX = cx,
                                centroY = cy,
                            )
                            /// A metade direita da capa: os cantos esquerdos do
                            /// quadrilátero viram os pontos médios das arestas
                            /// de cima e de baixo.
                            val meiaCapa = floatArrayOf(
                                (capa[0] + capa[2]) / 2f, (capa[1] + capa[3]) / 2f,
                                capa[2], capa[3],
                                capa[4], capa[5],
                                (capa[6] + capa[4]) / 2f, (capa[7] + capa[5]) / 2f,
                            )
                            if (dentroDoQuad(lateral, onde.x, onde.y) ||
                                dentroDoQuad(meiaCapa, onde.x, onde.y)
                            ) {
                                aoTocarNaAbertura()
                            }
                        }
                    }
                },
            ),
    ) {
        /// ## A sombra de contato — o chão que a caixa não tinha
        ///
        /// Sem ela a caixa flutua no breu, e um objeto sem peso não é objeto. É
        /// uma elipse de gradiente radial no pé da caixa, e ela **responde à
        /// pose**: girando, a pegada muda — a caixa de frente apoia a largura,
        /// de perfil apoia a espessura — e o centro escorrega pro lado que a
        /// lombada avançou. Desenhada antes das faces, porque sombra mora atrás
        /// de quem a projeta.
        Canvas(Modifier.fillMaxSize()) {
            val giroRad = Math.toRadians(poseDesenhada.giroY.toDouble())
            val deitada = abs(sin(giroRad)).toFloat()
            val larguraDaSombra =
                medidas.largura * (1.04f - 0.34f * deitada) + medidas.espessura * deitada
            val alturaDaSombra = medidas.espessura * 0.8f + 12.dp.toPx()
            val centro = Offset(
                x = size.width / 2f + sin(giroRad).toFloat() * medidas.espessura * 0.4f,
                y = size.height - 1.dp.toPx(),
            )
            /// O gradiente radial é redondo; a sombra é uma elipse. Achatar o
            /// canvas antes de desenhar o círculo é o que dá a borda macia nos
            /// dois eixos — desenhar uma oval com pincel radial daria borda dura
            /// em cima e embaixo, onde a oval corta o gradiente no meio.
            withTransform({
                scale(1f, alturaDaSombra / larguraDaSombra, pivot = centro)
            }) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent),
                        center = centro,
                        radius = larguraDaSombra / 2f,
                    ),
                    radius = larguraDaSombra / 2f,
                    center = centro,
                )
            }
        }

        /// ## O forro — o interior que a tampa aberta revela
        ///
        /// ⚠️ **A primeira abertura mostrou um buraco, e a foto cobrou.** As
        /// faces só são desenhadas de frente (o recorte de costas do
        /// `Projecao.kt`), o que é exato numa caixa fechada — ela é sólida, o
        /// interior não existe. Com a tampa a 118° o olho passa a ver **por
        /// dentro**, e o que havia lá era o fundo do palco: a caixa aberta
        /// virava uma lombada flutuando no nada — o mesmo «cenário de teatro
        /// visto de trás» que o dono já tinha pego uma vez, voltando pela porta
        /// da tampa.
        ///
        /// O forro são as faces de costas desenhadas como **quadriláteros
        /// chapados** — sem homografia, sem conteúdo, só o polígono projetado na
        /// cor do plástico interno. É o que um estojo aberto mostra de verdade:
        /// o lado de dentro não é impresso.
        ///
        /// Fechada (`abertura` zero), nada disto roda — o custo do forro só
        /// existe enquanto há interior pra ver.
        if (abertura > 0.5f) {
            Canvas(Modifier.fillMaxSize()) {
                Lado.entries
                    .filter { !deFrente(it, poseDesenhada, medidas, abertura) }
                    .sortedBy { profundidade(it, poseDesenhada, medidas, abertura) }
                    .forEach { qual ->
                        val pontos = cantosNaTela(
                            lado = qual,
                            pose = poseDesenhada,
                            m = medidas,
                            distancia = distancia,
                            abertura = abertura,
                            centroX = size.width / 2f,
                            centroY = size.height / 2f,
                        )
                        val caminho = Path().apply {
                            moveTo(pontos[0], pontos[1])
                            lineTo(pontos[2], pontos[3])
                            lineTo(pontos[4], pontos[5])
                            lineTo(pontos[6], pontos[7])
                            close()
                        }
                        /// Três tons, do que a luz alcança: a **tampa por
                        /// dentro** é o forro mais claro — ela está virada pra
                        /// cima, de cara pro facho do palco, e é o que faz a
                        /// tampa aberta ser vista como tampa em vez de sumir de
                        /// gume no escuro. O fundo do estojo pega a luz que
                        /// entra pela abertura; as paredes ficam na própria
                        /// sombra.
                        drawPath(
                            path = caminho,
                            color = when (qual) {
                                Lado.Capa -> Color(0xFF26262E)
                                Lado.Contracapa -> Color(0xFF1C1C22)
                                else -> Color(0xFF121217)
                            },
                        )

                        /// ## As peças do interior — «parece uma textura morta»
                        ///
                        /// Um estojo aberto não é liso por dentro: o keep case
                        /// tem o **cubo** que prende o disco no fundo e as
                        /// travas do encarte na tampa; o clamshell tem o
                        /// **berço** onde o cassete assenta. As peças são
                        /// desenhadas por interpolação bilinear dos mesmos
                        /// quatro cantos do quadrilátero — a perspectiva vem de
                        /// graça da própria face, sem matriz nova.
                        fun p(u: Float, v: Float): Offset {
                            val topoX = pontos[0] + (pontos[2] - pontos[0]) * u
                            val topoY = pontos[1] + (pontos[3] - pontos[1]) * u
                            val peX = pontos[6] + (pontos[4] - pontos[6]) * u
                            val peY = pontos[7] + (pontos[5] - pontos[7]) * u
                            return Offset(topoX + (peX - topoX) * v, topoY + (peY - topoY) * v)
                        }
                        fun raioEm(u: Float, v: Float, fracao: Float): Float {
                            val a = p(u - fracao, v)
                            val b = p(u + fracao, v)
                            return kotlin.math.hypot(b.x - a.x, b.y - a.y) / 2f
                        }
                        val claro = Color.White.copy(alpha = 0.17f)
                        val fundo2 = Color.Black.copy(alpha = 0.42f)

                        if (qual == Lado.Contracapa) {
                            if (interiorDeDisco) {
                                /// O cubo do disco: o anel do encaixe e as
                                /// pétalas da trava central.
                                val c = p(0.5f, 0.5f)
                                drawCircle(fundo2, raioEm(0.5f, 0.5f, 0.30f), c)
                                drawCircle(claro, raioEm(0.5f, 0.5f, 0.28f), c,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                                drawCircle(claro, raioEm(0.5f, 0.5f, 0.10f), c,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                                repeat(6) { k ->
                                    val ang = Math.toRadians(k * 60.0)
                                    val r = raioEm(0.5f, 0.5f, 0.085f)
                                    drawLine(claro,
                                        start = c,
                                        end = Offset(c.x + (r * kotlin.math.cos(ang)).toFloat(),
                                            c.y + (r * kotlin.math.sin(ang)).toFloat()),
                                        strokeWidth = 1.5.dp.toPx())
                                }
                            } else {
                                /// O berço do cassete: a cavidade retangular e
                                /// os dois assentos dos carretéis.
                                val berco = Path().apply {
                                    moveTo(p(0.14f, 0.2f).x, p(0.14f, 0.2f).y)
                                    lineTo(p(0.86f, 0.2f).x, p(0.86f, 0.2f).y)
                                    lineTo(p(0.86f, 0.8f).x, p(0.86f, 0.8f).y)
                                    lineTo(p(0.14f, 0.8f).x, p(0.14f, 0.8f).y)
                                    close()
                                }
                                drawPath(berco, fundo2)
                                drawPath(berco, claro,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                                listOf(0.33f, 0.67f).forEach { u ->
                                    drawCircle(claro, raioEm(u, 0.5f, 0.11f), p(u, 0.5f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                                }
                            }
                        }

                        if (qual == Lado.Capa) {
                            if (interiorDeDisco) {
                                /// As quatro travas do encarte, nos cantos da
                                /// tampa do keep case.
                                listOf(0.08f to 0.06f, 0.92f to 0.06f, 0.08f to 0.94f, 0.92f to 0.94f)
                                    .forEach { (u, v) ->
                                        val a = p((u - 0.06f).coerceIn(0f, 1f), v)
                                        val b = p((u + 0.06f).coerceIn(0f, 1f), v)
                                        drawLine(claro, a, b, strokeWidth = 2.5.dp.toPx())
                                    }
                            } else {
                                /// Os frisos de molde do clamshell — as linhas
                                /// que toda tampa de plástico soprado tem.
                                listOf(0.25f, 0.5f, 0.75f).forEach { v ->
                                    drawLine(Color.Black.copy(alpha = 0.18f),
                                        p(0.06f, v), p(0.94f, v),
                                        strokeWidth = 1.5.dp.toPx())
                                }
                            }
                            /// O rebordo da tampa: o fio de luz na borda, que é
                            /// o que separa «parede» de «buraco preto».
                            drawPath(caminho, claro,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                        }
                    }

            }
        }

        /// Do mais fundo pro mais próximo — a ordem do pintor. Ver
        /// `ladosVisiveis`.
        ///
        /// ## ⚠️ O `key(qual)` é o que impede a capa de **piscar** no giro
        ///
        /// A lista de lados visíveis muda de tamanho e de ordem a cada quadro do
        /// giro. Sem chave, o Compose casa os composables **por posição**: quando
        /// a lombada sai da lista, a capa herda o slot que era dela — e a
        /// `AsyncImage` do slot vê o `model` trocar e recarrega, num pisca que o
        /// dono viu a olho («girar a capa faz ela dar uma piscada»). Com a chave,
        /// cada face é dona da própria composição, em qualquer ordem.
        ladosVisiveis(poseDesenhada, medidas, abertura).forEach { qual ->
            androidx.compose.runtime.key(qual) {
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
                lado(qual, luz, poseDesenhada)
            }
            }
        }

        /// O **lábio da concha da frente** — a metade da lateral da abertura que
        /// viaja com a tampa. Ver [cantosDaMeiaLateral]: sem ele a tampa era uma
        /// placa colada na frente de uma caixa maciça, porque a lateral ficava
        /// inteira e parada.
        ///
        /// ## ⚠️ Ele vem **depois** das faces, e a primeira versão errou isso
        ///
        /// Nasceu junto do forro, antes do laço — e não apareceu em foto
        /// nenhuma: ali ele fica atrás de **todas** as faces, e o que o esconde é
        /// justamente a tampa a que ele está grudado. O lábio é a quina da concha
        /// que está aberta na direção de quem olha; com a caixa aberta, ele é a
        /// coisa mais à frente que existe. Por isso é pintado por último.
        ///
        /// Desenhado em `Canvas` e não como face composta: ele não tem conteúdo
        /// nenhum — é plástico — e uma entrada nova no `Lado` obrigaria a
        /// `FaceDaCaixa` inteira a saber de uma peça que só existe aberta.
        if (abertura > 0.5f) {
            Canvas(Modifier.fillMaxSize()) {
                val labio = cantosDaMeiaLateral(medidas, abertura).map { canto ->
                    val (px, py) = projetado(girado(canto, poseDesenhada), distancia)
                    Offset(px + size.width / 2f, py + size.height / 2f)
                }
                val caminho = Path().apply {
                    moveTo(labio[0].x, labio[0].y)
                    labio.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(caminho, corDoCasco)
                /// O fio de luz na quina: é ele que separa o lábio da tampa a
                /// que está grudado — sem a aresta, os dois viram uma mancha só.
                drawPath(
                    caminho,
                    Color.White.copy(alpha = 0.16f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                )
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
