package dev.odeon.android.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.odeon.android.ui.Cores

/// Os três botões do transporte, **desenhados**.
///
/// ## Por que desenhar, e não trazer um jogo de ícones
///
/// O app não tem biblioteca de ícones: os cinco das abas são vetores escritos à
/// mão, e o §15 chama isso de «zero bytes». Trazer o Material Icons inteiro por
/// três formas — duas barras, uma seta curva e um algarismo — inverteria a conta
/// que este projeto faz em todo lugar.
///
/// E há um motivo melhor: o salto **precisa dizer quantos segundos**. Um ícone de
/// biblioteca com «10» dentro não existe em tamanho que caiba bem; desenhando, o
/// número é parte da forma e não um texto empilhado por cima.
///
/// ## O que dá vida a eles
///
/// | | |
/// |---|---|
/// | **a seta curva gira** | ao tocar, o arco dá uma volta no sentido do salto |
/// | **o play afunda** | escala 0,92 enquanto o dedo está em cima |
/// | **o facho por trás** | um halo quente sob o play, que é a lâmpada do projetor |
///
/// O terceiro é o que amarra no app: a mesma luz da barra de navegação e da
/// lente da tira, aqui dizendo qual é o botão principal sem precisar ser maior
/// que os outros dois.

/// O disco de play/pause, com o facho por trás.
@Composable
/// ⚠️ Era `internal` e virou pública quando o `:tv` passou a usá-la — a T2.
/// `internal` é visibilidade de módulo, e o player da sala está do outro lado
/// da fronteira. A §3 já previa a viagem: 254 linhas, **zero** imports de
/// `material3`, «atravessa de graça».
fun BotaoDeTocar(tocando: Boolean, compacto: Boolean = false, aoTocar: () -> Unit) {
    val interacao = remember { MutableInteractionSource() }
    val pressionado by interacao.collectIsPressedAsState()
    /// `spring` e não `tween`: afundar botão é resposta a dedo, e mola é o que
    /// devolve a sensação de matéria. Amortecimento alto porque um play que
    /// quica parece brinquedo.
    val escala by animateFloatAsState(
        targetValue = if (pressionado) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.72f),
        label = "afundar",
    )

    Box(contentAlignment = Alignment.Center) {
        /// ## O facho por trás do play
        ///
        /// Um radial quente que vaza do disco. É a mesma luz do `BarraDoFacho` e
        /// da lente da [Tira] — e aqui ela responde uma pergunta de hierarquia
        /// sem gastar tamanho: dos três controles, **este é a lâmpada**.
        ///
        /// Ele fica **fora** do `clip` do disco, senão não há halo — luz
        /// recortada na borda do objeto que a emite é um círculo pintado.
        Canvas(Modifier.size(if (compacto) 96.dp else 124.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Cores.destaqueQuente.copy(alpha = 0.34f),
                        Cores.destaqueQuente.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = size.minDimension / 2f,
                ),
                radius = size.minDimension / 2f,
            )
        }

        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = escala; scaleY = escala }
                .size(if (compacto) 46.dp else 60.dp)
                .clip(CircleShape)
                .background(
                    /// Degradê e não cor chapada: o disco é uma peça de metal
                    /// pegando a luz de cima, como os botões de um projetor.
                    Brush.verticalGradient(
                        listOf(Cores.destaqueQuente, Cores.destaque),
                    ),
                )
                .clickable(
                    interactionSource = interacao,
                    indication = null,
                    onClick = aoTocar,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(if (compacto) 19.dp else 24.dp)) {
                if (tocando) {
                    /// Duas barras com canto arredondado. A largura é 30% do
                    /// vão e o intervalo 24% — as proporções de um símbolo de
                    /// pausa que não parece dois palitos.
                    val larguraDaBarra = size.width * 0.30f
                    val vao = size.width * 0.24f
                    val alturaDaBarra = size.height * 0.86f
                    val topo = (size.height - alturaDaBarra) / 2f
                    val esquerda = (size.width - (larguraDaBarra * 2 + vao)) / 2f
                    repeat(2) { i ->
                        drawRoundRect(
                            color = Cores.fundo,
                            topLeft = Offset(esquerda + i * (larguraDaBarra + vao), topo),
                            size = Size(larguraDaBarra, alturaDaBarra),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                larguraDaBarra * 0.28f,
                            ),
                        )
                    }
                } else {
                    /// O triângulo, **deslocado** pra direita.
                    ///
                    /// Um play centrado geometricamente parece torto pra
                    /// esquerda: o centro de massa de um triângulo está a um
                    /// terço da base, não na metade. 8% resolve, e é o mesmo
                    /// truque que todo botão de play do mundo usa sem dizer.
                    val caminho = Path().apply {
                        moveTo(size.width * 0.16f, 0f)
                        lineTo(size.width * 0.96f, size.height / 2f)
                        lineTo(size.width * 0.16f, size.height)
                        close()
                    }
                    drawPath(caminho, Cores.fundo)
                }
            }
        }
    }
}

/// O salto — uma seta curva com o número de segundos dentro.
///
/// ⚠️ **O arco gira ao tocar**, no sentido do salto: pra trás no −10, pra frente
/// no +30. É a diferença entre um ícone e um botão que responde — e é o que o
/// dono pediu ao falar em «dar mais vida».
@Composable
fun BotaoDeSalto(segundos: Int, paraTras: Boolean, aoTocar: () -> Unit) {
    val interacao = remember { MutableInteractionSource() }
    val pressionado by interacao.collectIsPressedAsState()
    val giro by animateFloatAsState(
        targetValue = if (pressionado) (if (paraTras) -300f else 300f) else 0f,
        animationSpec = spring(dampingRatio = 0.9f),
        label = "girar",
    )
    val medidor = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interacao, indication = null, onClick = aoTocar),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(34.dp)) {
            rotate(giro) { desenharSeta(paraTras) }
            desenharNumero(medidor, segundos)
        }
    }
}

/// O arco com a ponta de flecha.
///
/// ## ⚠️ As duas estavam trocadas, e a foto pegou
///
/// A primeira versão espelhava **o arco** e deixava a ponta seguir junto, e o
/// resultado foi o `10` apontando pra frente e o `30` pra trás — o oposto do que
/// os dois fazem.
///
/// A régua que resolve: **o arco é o mesmo nos dois**. Uma volta de 300° com a
/// abertura no alto, sempre igual. O que muda é só **de que lado da abertura a
/// ponta mora e pra onde ela olha**:
///
/// | | ponta | olha pra |
/// |---|---|---|
/// | voltar 10s | ponta esquerda da abertura (−120°) | a esquerda |
/// | avançar 30s | ponta direita da abertura (−60°) | a direita |
///
/// A abertura fica no topo porque uma circunferência fechada não tem pra onde
/// apontar: é o vão que faz a flecha significar direção.
private fun DrawScope.desenharSeta(paraTras: Boolean) {
    val traco = size.minDimension * 0.085f
    val raio = size.minDimension / 2f - traco
    val centro = Offset(size.width / 2f, size.height / 2f)

    drawArc(
        color = Color.White,
        startAngle = -60f,
        sweepAngle = 300f,
        useCenter = false,
        topLeft = Offset(centro.x - raio, centro.y - raio),
        size = Size(raio * 2, raio * 2),
        style = Stroke(width = traco, cap = androidx.compose.ui.graphics.StrokeCap.Round),
    )

    /// A ponta: um triângulo cheio na boca do arco. Desenhado à mão porque uma
    /// seta feita de dois traços curtos vira um «v» torto em 34dp.
    val grau = if (paraTras) -120.0 else -60.0
    val angulo = Math.toRadians(grau)
    val ponta = Offset(
        centro.x + (raio * kotlin.math.cos(angulo)).toFloat(),
        centro.y + (raio * kotlin.math.sin(angulo)).toFloat(),
    )
    val lado = traco * 2.6f
    val sinal = if (paraTras) -1f else 1f
    val cabeca = Path().apply {
        moveTo(ponta.x, ponta.y - lado * 0.9f)
        lineTo(ponta.x + lado * sinal, ponta.y)
        lineTo(ponta.x, ponta.y + lado * 0.9f)
        close()
    }
    drawPath(cabeca, Color.White)
}

/// O número, no miolo do arco.
private fun DrawScope.desenharNumero(medidor: TextMeasurer, segundos: Int) {
    val texto = medidor.measure(
        text = "$segundos",
        style = TextStyle(color = Color.White, fontSize = 11.sp),
    )
    drawText(
        textLayoutResult = texto,
        topLeft = Offset(
            (size.width - texto.size.width) / 2f,
            (size.height - texto.size.height) / 2f,
        ),
    )
}
