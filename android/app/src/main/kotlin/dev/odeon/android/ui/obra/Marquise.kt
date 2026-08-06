package dev.odeon.android.ui.obra

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.odeon.android.dados.Cena
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.escalaDeAnimacao
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/// A fachada do cinema, e as fotos de cena penduradas nela.
///
/// ## O que esta tela era, e por que mudou
///
/// > «Quero um redesign dessa tela, tá absurdamente simples, feia. Gosto de
/// > coisas experimentais, com animação, luzes, algo que lembre odeon»
///
/// A ficha era pôster à esquerda, quatro linhas de metadado à direita, sinopse,
/// pílulas e um botão cheio — a única tela do app que não sabia que o app é um
/// cinema. O player tinha cortina, película e facho; a locadora tinha caixa,
/// cinta e estante; a ficha tinha um formulário.
///
/// ## As duas metáforas viraram uma, e a costura não é decorativa
///
/// Foram desenhadas e escolhidas duas: a **marquise** (o letreiro de lâmpadas da
/// fachada) e o **varal** (as fotos de cena penduradas). Elas não ficaram
/// empilhadas — **o fio sai dos cantos de baixo do letreiro** e verga com o peso
/// das fotos.
///
/// Isso é o que os cinemas de rua faziam de verdade: o letreiro em cima, as
/// fotos de cena na vitrine embaixo. Empilhar as duas seria pôr dois enfeites na
/// mesma tela; pendurar uma na outra é uma fachada.
///
/// ## E o varal é navegação, não enfeite
///
/// Cada foto é uma **cena real** do filme, vinda de `GET /api/works/{obra}/cenas`
/// — a mesma rota que enche a tira do player. Tocar numa abre o filme **naquele
/// minuto**. Uma fileira de imagens bonitas que não fizesse nada seria a tela
/// mentindo sobre a própria densidade.

/// Quantas fotos penduram no fio.
///
/// ⚠️ **Três, e não as doze que a rota devolve.** Doze prendedores numa largura
/// de 411dp dariam 34dp por foto — miniatura de miniatura, ilegível, e um fio
/// tão carregado que o varal viraria uma barra. Três respiram, cabem inclinadas
/// sem se cobrir, e é o bastante pra dizer «há cenas aqui, toque numa».
private const val FOTOS_NO_VARAL = 3

/// A marquise: o letreiro emoldurado de lâmpadas.
///
/// ⚠️ **As lâmpadas acendem em sequência, e a curva é a mesma da cortina.** Não é
/// uma rampa: é `keyframes` com quedas e picos, a piscada de lâmpada de arco que
/// o `CortinaDeAbertura` já usa — e que existe lá porque o dono cobrou duas vezes
/// que uma subida lisa «não se lê como as luzes piscam, se lê como um fade».
/// Reaproveitar a curva é o que faz a fachada da ficha e a cortina do player
/// parecerem o mesmo prédio.
///
/// Depois de acesas elas **respiram**: um `infiniteRepeatable` lento e de pouca
/// amplitude. Piscar forte em repouso seria a tela pedindo atenção enquanto
/// alguém lê a sinopse.
@Composable
internal fun Marquise(
    titulo: String,
    /// `Rental Family · 2025 · 1h49`, já montado por quem tem os pedaços — §24
    /// aplicado antes de chegar aqui: o que não existe não vira separador solto.
    linhaDeBaixo: String,
    modifier: Modifier = Modifier,
    /// O selo do plano, quando há. `null` some — não vira "—".
    plano: (@Composable () -> Unit)? = null,
) {
    val escala = escalaDeAnimacao()
    val acesa = remember { Animatable(if (escala <= 0f) 1f else 0f) }

    LaunchedEffect(Unit) {
        /// Sem animação no sistema, nada de coreografia — o §15 manda, e a régua
        /// está no `escalaDeAnimacao`. Aqui ela decide **entrar ou não**: com a
        /// escala em zero o `tween` terminaria no primeiro quadro de qualquer
        /// jeito, mas entrar na coreografia pra sair dela é gasto sem efeito.
        if (escala <= 0f) return@LaunchedEffect
        acesa.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = 900
                0.00f at 0
                0.55f at 90
                0.12f at 150
                0.85f at 230
                0.28f at 300
                1.00f at 400
                0.60f at 470
                0.95f at 560
                0.80f at 640
                1.00f at 760
            },
        )
    }

    /// O respiro de repouso. `rememberInfiniteTransition` já consulta o
    /// `MotionDurationScale` sozinho — por isso ele não passa pelo
    /// `escalaDeAnimacao`, e a distinção está escrita lá.
    val respiro = rememberInfiniteTransition(label = "respiro")
    val brilho by respiro.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "brilho",
    )

    val luz = acesa.value * brilho

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(listOf(Cores.fundoElevado, Cores.fundo)),
            )
            .border(1.dp, Cores.destaque.copy(alpha = 0.55f * luz), RoundedCornerShape(10.dp)),
    ) {
        Column(
            /// ## ⚠️ O respiro é do texto, e não do cartão
            ///
            /// A primeira versão pôs o `padding` no `Box`. Parecia o mesmo, e não
            /// é: padding encolhe a **área de conteúdo**, e é dentro dela que o
            /// `align` das lâmpadas mira. Os bulbos deixavam de morar na borda do
            /// cartão e passavam a morar onde o texto está — a foto ampliada
            /// mostrou o selo do plano por cima de oito deles.
            ///
            /// Com o respiro aqui, o `Box` continua do tamanho do cartão: as
            /// lâmpadas encostam na borda de verdade, e o texto se afasta delas.
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontSize = 27.sp,
                ),
                color = Cores.texto,
                textAlign = TextAlign.Center,
            )
            Text(
                text = linhaDeBaixo,
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                color = Cores.textoApagado,
                textAlign = TextAlign.Center,
            )
            plano?.invoke()
        }

        /// As duas fileiras de bulbos, desenhadas **por cima** da moldura.
        ///
        /// ⚠️ A ordem importa e já custou uma rodada no player: num `Box` quem
        /// vem depois fica na frente, e a primeira versão da cortina pintou as
        /// lâmpadas antes do pano — elas existiam e eram cobertas no mesmo
        /// quadro. Aqui elas vêm por último, que é também a física: a marquise
        /// tem as lâmpadas na frente.
        /// 5dp da borda: encostadas, sem encavalar no traço dourado da moldura.
        FileiraDeBulbos(luz, Modifier.align(Alignment.TopCenter).padding(top = 5.dp))
        FileiraDeBulbos(luz, Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp))
    }
}

/// Uma fileira de bulbos, encostada na borda.
///
/// ⚠️ **Quatorze**, e o número saiu de uma medida do player: a cortina tentou
/// dezesseis e a foto mostrou o último cortado — 16 caixas de 33dp dão 528dp
/// numa tela de 411. Aqui a régua é a mesma conta, com a largura da ficha.
///
/// O halo é o que separa uma lâmpada de um adesivo amarelo — a mesma frase que a
/// cortina registrou quando os bulbos de 6dp «não existiam» na foto.
@Composable
private fun FileiraDeBulbos(luz: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        repeat(14) { i ->
            /// Cada bulbo tem o próprio atraso na piscada — um letreiro em que
            /// todas as lâmpadas fazem a mesma coisa ao mesmo tempo é um
            /// retângulo que pisca, não uma marquise.
            val desvio = 0.72f + 0.28f * kotlin.math.sin((i * 1.7f) + luz * 6f)
            val forca = (luz * desvio).coerceIn(0f, 1f)
            Canvas(Modifier.size(9.dp)) {
                val centro = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Cores.destaqueQuente.copy(alpha = 0.55f * forca),
                            Color.Transparent,
                        ),
                        center = centro,
                        radius = size.minDimension * 1.6f,
                    ),
                    radius = size.minDimension * 1.6f,
                    center = centro,
                )
                drawCircle(
                    color = Cores.destaqueQuente.copy(alpha = (0.35f + 0.65f * forca)),
                    radius = size.minDimension / 2f,
                    center = centro,
                )
            }
        }
    }
}

/// O varal de cenas, pendurado na marquise.
///
/// ## ⚠️ O fio verga, e é o que faz ele ser um fio
///
/// Uma reta horizontal com fotos penduradas é uma barra de miniaturas. O que
/// muda a leitura é a **curva de Bézier**: o fio sai alto nas pontas, cede no
/// meio, e as fotos acompanham a altura da corda onde cada prendedor mordeu.
/// Sem isso o desenho vira uma fileira, e a metáfora não sobrevive.
///
/// ## A entrada
///
/// As fotos **caem** uma a uma e balançam até parar. É `spring` e não `tween`:
/// balanço é matéria com inércia, e mola é o que devolve peso. O amortecimento
/// é baixo de propósito — alto demais e a foto só desce, sem oscilar.
///
/// ⚠️ Cada uma tem o próprio atraso. Três caindo juntas seriam um bloco descendo.
@Composable
internal fun Varal(
    cenas: List<Cena>,
    /// Monta a URL da imagem da cena. Vem de fora porque quem sabe montar URL de
    /// mídia é o repositório, e esta tela não fala com ele.
    urlDaCena: (Cena) -> String?,
    /// Tocar numa foto abre o filme naquele segundo.
    aoTocarNaCena: (Cena) -> Unit,
    modifier: Modifier = Modifier,
) {
    /// ⚠️ **Sem cenas, sem varal** — nem fio, nem prendedor, nem espaço vazio.
    /// §24 e §53 juntos: um fio pendurado sem fotos prometeria uma navegação que
    /// não existe, e ainda deixaria um buraco na fachada.
    if (cenas.size < FOTOS_NO_VARAL) return

    /// As três escolhidas, espalhadas pelo filme.
    ///
    /// ⚠️ **Não são as três primeiras.** As doze cenas cobrem o filme inteiro, e
    /// pegar as primeiras mostraria três quadros dos primeiros minutos — que num
    /// filme é sempre a mesma coisa: logo de distribuidora, plano de
    /// estabelecimento, primeira fala. Espalhadas, elas contam o filme.
    val escolhidas = remember(cenas) {
        val passo = cenas.size / (FOTOS_NO_VARAL + 1)
        (1..FOTOS_NO_VARAL).map { cenas[(it * passo).coerceIn(0, cenas.lastIndex)] }
    }

    val escala = escalaDeAnimacao()

    /// ⚠️ **172dp, medido na foto e não estimado.** A primeira versão reservou
    /// 196 e sobrou um vão morto de quase 60dp entre a última foto e a sinopse —
    /// a conta é a foto mais alta (a largura dividida por três, em 4:3, mais a
    /// legenda) somada à descida do prendedor do meio, e nada além disso.
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(172.dp)) {
        val larguraTotal = maxWidth

        /// O fio. Nasce nos cantos de cima (onde a marquise acaba) e cede 26dp no
        /// meio — a mesma flecha que a corda faria com três fotos leves.
        Canvas(Modifier.fillMaxSize()) {
            val caminho = Path().apply {
                moveTo(0f, 6.dp.toPx())
                quadraticTo(size.width / 2f, 58.dp.toPx(), size.width, 6.dp.toPx())
            }
            /// ⚠️ Não é `Cores.linha`: aquele é o cinza de divisória (#23232C) e
            /// sobre o preto da ficha ele **desaparece** — na foto o varal
            /// parecia ter fotos flutuando sem corda. Um fio precisa ser visto
            /// pra o prendedor fazer sentido.
            drawPath(
                path = caminho,
                color = Cores.textoApagado.copy(alpha = 0.5f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            escolhidas.forEachIndexed { indice, cena ->
                /// A altura em que o prendedor mordeu o fio: as das pontas ficam
                /// altas, a do meio desce junto com a curva.
                val descida = if (indice == 1) 42.dp else 22.dp
                /// A inclinação de cada foto. Fixa por posição, e não sorteada:
                /// um ângulo aleatório mudaria a cada recomposição, e a foto
                /// ficaria tremendo enquanto alguém rola a tela.
                val angulo = when (indice) {
                    0 -> -4f
                    1 -> 1.8f
                    else -> 4f
                }
                FotoPendurada(
                    url = urlDaCena(cena),
                    /// `Locale.ROOT` e não o do aparelho: `%02d` com um locale
                    /// de dígitos próprios (árabe, birmanês) escreveria o número
                    /// da cena em outro alfabeto no meio de um rótulo latino.
                    legenda = "CENA " + String.format(
                        java.util.Locale.ROOT,
                        "%02d",
                        cenas.indexOf(cena) + 1,
                    ),
                    angulo = angulo,
                    atraso = indice * 90L,
                    escala = escala,
                    largura = (larguraTotal - 24.dp) / FOTOS_NO_VARAL,
                    modifier = Modifier.padding(top = descida),
                    aoTocar = { aoTocarNaCena(cena) },
                )
            }
        }
    }
}

/// Uma foto de cena, presa no fio por um prendedor.
@Composable
private fun FotoPendurada(
    url: String?,
    legenda: String,
    angulo: Float,
    atraso: Long,
    escala: Float,
    largura: androidx.compose.ui.unit.Dp,
    modifier: Modifier,
    aoTocar: () -> Unit,
) {
    /// `0` é pendurada e parada; `1` é ainda no ar, acima do fio.
    val queda = remember { Animatable(if (escala <= 0f) 0f else 1f) }
    val balanco = remember { Animatable(if (escala <= 0f) 0f else 1f) }

    LaunchedEffect(Unit) {
        if (escala <= 0f) return@LaunchedEffect
        /// ⚠️ O `delay` **é** multiplicado pela escala e o `spring` **não** — a
        /// distinção que a `Animacao.kt` existe pra escrever uma vez só: `tween`
        /// e `spring` dentro de `animateTo` já são descontados pelo
        /// `MotionDurationScale`, e multiplicar de novo aplicaria o desconto duas
        /// vezes. `delay` de corrotina não é descontado por ninguém.
        delay((atraso * escala).toLong())
        this@LaunchedEffect.launch {
            queda.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 220f))
        }
        /// O balanço amortece mais devagar que a queda — a foto assenta na
        /// vertical depois de já ter chegado na altura certa, que é o que
        /// acontece com qualquer coisa pendurada por um ponto só.
        balanco.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 140f))
    }

    Column(
        modifier = modifier
            .width(largura)
            .graphicsLayer {
                /// A rotação nasce **no prendedor**, e não no centro da foto: é
                /// por ali que ela está presa, e girar pelo meio faria a foto
                /// pivotar no ar sem tocar no fio.
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.44f, 0f)
                rotationZ = angulo + balanco.value * 9f
                translationY = -queda.value * 70.dp.toPx()
                alpha = 1f - queda.value * 0.4f
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        /// O prendedor.
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .size(width = 11.dp, height = 17.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.verticalGradient(listOf(Cores.destaque, Cores.destaqueApagado))),
        )

        /// O papel. Margem larga embaixo, como toda foto revelada.
        Column(
            modifier = Modifier
                /// ⚠️ `offset`, e **não** `padding` negativo: o Compose recusa
                /// padding negativo em tempo de execução com «Padding must be
                /// non-negative». Compilou, passou nos 144 testes e no lint, e
                /// derrubou o app na primeira abertura da ficha — o tipo de coisa
                /// que só a tela cobra.
                .offset(y = (-3).dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(Cores.papel)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "abrir o filme nesta cena",
                    onClick = aoTocar,
                )
                .padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(Cores.fundoAfundado),
            ) {
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                text = legenda,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    letterSpacing = 1.6.sp,
                ),
                color = Cores.tintaDoPapel,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/// O bilhete: o «continuar» como ingresso picotado.
///
/// ## Por que ingresso, e não um botão
///
/// Era um `Button` de canto arredondado com o dourado chapado — o único botão
/// cheio do app e o mais importante, com a aparência de um botão de diálogo de
/// sistema. O ingresso resolve duas coisas de uma vez: dá **forma** ao gesto mais
/// importante da tela, e o canhoto abre um lugar natural pra dizer *de onde
/// parou* sem inventar uma linha de metadado ao lado.
///
/// ⚠️ **Os dois furos são recortes do fundo**, e não círculos escuros pintados
/// por cima: pintados, eles ficariam com a cor errada assim que a ficha ganhasse
/// um fundo tingido pela obra — e a cor dominante já chega do servidor pra
/// metade do acervo.
@Composable
internal fun Bilhete(
    /// `continuar · 1:04:51`, ou `assistir`. Quem monta é quem sabe se há de onde
    /// continuar — a mesma função que decide a posição, ver `ondeContinuar`.
    chamada: String,
    sobrelinha: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fundoDaTela = Cores.fundo
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Cores.destaqueQuente, Cores.destaque, Cores.destaqueApagado),
                ),
            )
            .clickable(onClickLabel = chamada, onClick = aoTocar)
            .drawBehind {
                /// O picote e os dois furos, na divisa do canhoto.
                val x = size.width - 74.dp.toPx()
                val raio = 8.dp.toPx()
                drawCircle(fundoDaTela, raio, Offset(x, 0f))
                drawCircle(fundoDaTela, raio, Offset(x, size.height))
                val tracinho = 5.dp.toPx()
                var y = raio + tracinho
                while (y < size.height - raio) {
                    drawLine(
                        color = Cores.tintaDoBilhete.copy(alpha = 0.45f),
                        start = Offset(x, y),
                        end = Offset(x, y + tracinho),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                    y += tracinho * 2
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 20.dp, top = 14.dp, bottom = 14.dp, end = 20.dp),
        ) {
            Text(
                text = sobrelinha,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    letterSpacing = 2.5.sp,
                ),
                color = Cores.tintaDoBilhete.copy(alpha = 0.75f),
            )
            Text(
                text = chamada,
                style = MaterialTheme.typography.titleMedium,
                color = Cores.tintaDoBilhete,
            )
        }
        Canvas(
            Modifier
                .width(74.dp)
                .height(22.dp),
        ) {
            /// O triângulo do canhoto — desenhado, como todo ícone daqui.
            val meio = size.height / 2f
            val largura = 15.dp.toPx()
            val altura = 17.dp.toPx()
            val x0 = (size.width - largura) / 2f
            val caminho = Path().apply {
                moveTo(x0, meio - altura / 2f)
                lineTo(x0 + largura, meio)
                lineTo(x0, meio + altura / 2f)
                close()
            }
            drawPath(caminho, Cores.tintaDoBilhete)
        }
    }
}

/// Os canhotos: as duas ações secundárias, como talões arrancados do bilhete.
///
/// ## ⚠️ Elas ficaram pra trás no redesenho, e o dono viu
///
/// > «Você esqueceu de atualizar esses dois tb»
///
/// A marquise, o varal e o bilhete entraram, e `baixar pra ver sem rede` e
/// `pegar a fita na locadora` continuaram dois `TextButton` de cinza apagado,
/// empilhados, sem nada em volta — exatamente a tela que o redesenho veio
/// desfazer, sobrevivendo no rodapé.
///
/// ## Por que canhoto, e não outro botão dourado
///
/// A regra que já estava escrita aqui continua valendo: **baixar é a exceção**, e
/// quem abriu a ficha quase sempre veio ver agora. Dar a estas duas o peso do
/// bilhete faria a tela perguntar uma coisa que já estava respondida.
///
/// Então elas são **vazadas** onde o bilhete é cheio: mesma família, metade da
/// voz. E o picote na borda de cima diz de onde vieram — são talões do mesmo
/// bloco, arrancados logo abaixo da entrada.
///
/// ⚠️ **Lado a lado, e não empilhadas.** Empilhadas elas viravam uma lista de
/// opções com a mesma hierarquia; na mesma fileira, lidas de uma vez, ficam
/// claramente o rodapé de uma tela cujo assunto é o botão de cima.
@Composable
internal fun Canhoto(
    rotulo: String,
    /// O glifo à esquerda, desenhado. O app não tem jogo de ícones — §15, «zero
    /// bytes» — e as duas formas aqui são as mesmas famílias que a locadora e o
    /// player já desenham à mão.
    glifo: DrawScope.() -> Unit,
    habilitado: Boolean,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tinta = if (habilitado) Cores.destaque else Cores.destaqueApagado
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Cores.destaque.copy(alpha = 0.05f))
            .border(1.dp, tinta.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clickable(enabled = habilitado, onClickLabel = rotulo, onClick = aoTocar)
            .drawBehind {
                /// O picote na borda de cima — o talão foi arrancado dali.
                val tracinho = 4.dp.toPx()
                var x = tracinho * 2
                while (x < size.width - tracinho) {
                    drawLine(
                        color = tinta.copy(alpha = 0.28f),
                        start = Offset(x, 3.dp.toPx()),
                        end = Offset(x + tracinho, 3.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                    x += tracinho * 2
                }
            }
            .padding(start = 12.dp, end = 12.dp, top = 13.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Canvas(Modifier.size(17.dp)) { glifo() }
        Text(
            text = rotulo,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
            color = tinta,
        )
    }
}

/// A seta que desce até um traço — baixar.
internal fun DrawScope.desenharBaixar() {
    val traco = 1.6.dp.toPx()
    val meio = size.width / 2f
    drawLine(
        Cores.destaque,
        Offset(meio, size.height * 0.10f),
        Offset(meio, size.height * 0.60f),
        strokeWidth = traco,
        cap = StrokeCap.Round,
    )
    /// A ponta, em duas retas — triângulo cheio em 17dp vira borrão, e é a mesma
    /// conta que o ícone de girar do player já pagou.
    drawLine(
        Cores.destaque,
        Offset(meio, size.height * 0.62f),
        Offset(meio - size.width * 0.22f, size.height * 0.40f),
        strokeWidth = traco,
        cap = StrokeCap.Round,
    )
    drawLine(
        Cores.destaque,
        Offset(meio, size.height * 0.62f),
        Offset(meio + size.width * 0.22f, size.height * 0.40f),
        strokeWidth = traco,
        cap = StrokeCap.Round,
    )
    /// O chão onde ela pousa: «fica aqui, no aparelho».
    drawLine(
        Cores.destaque,
        Offset(size.width * 0.14f, size.height * 0.88f),
        Offset(size.width * 0.86f, size.height * 0.88f),
        strokeWidth = traco,
        cap = StrokeCap.Round,
    )
}

/// A fita: a carcaça e os dois carretéis — o mesmo objeto que a locadora tira da
/// estante, reduzido ao que sobrevive em 17dp.
internal fun DrawScope.desenharFita(tinta: Color) {
    val traco = 1.6.dp.toPx()
    drawRoundRect(
        color = tinta,
        topLeft = Offset(traco / 2, size.height * 0.20f),
        size = Size(size.width - traco, size.height * 0.60f),
        cornerRadius = CornerRadius(2.dp.toPx()),
        style = Stroke(width = traco),
    )
    val y = size.height * 0.50f
    val raio = size.width * 0.13f
    drawCircle(tinta, raio, Offset(size.width * 0.33f, y), style = Stroke(width = traco))
    drawCircle(tinta, raio, Offset(size.width * 0.67f, y), style = Stroke(width = traco))
}
