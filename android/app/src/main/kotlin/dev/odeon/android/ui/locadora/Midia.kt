package dev.odeon.android.ui.locadora

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.odeon.android.ui.Cores
import kotlin.math.cos
import kotlin.math.sin

/// O disco e a fita — a mídia que sai de dentro da caixa.
///
/// ## Eles são **desenhados**, e não imagens
///
/// É a régua de «zero bytes» do §15, a mesma que rendeu o avatar por hash e a
/// marquise: um disco é um círculo com anéis e um reflexo, e uma fita é um
/// retângulo com dois carretéis. Desenhados, eles escalam em qualquer tamanho,
/// giram sem custo e não precisam de arte que alguém tenha que produzir pra
/// 17.930 obras.
///
/// E, ao contrário do pôster, eles são **iguais pra todo mundo** — um DVD é um
/// DVD. O que muda de obra pra obra é a cor que o disco reflete, e ela sai da
/// `dominant_color` que o servidor já extraiu.

/// O disco, visto de frente e inclinado pela pose.
///
/// ## A inclinação é o que faz ele parecer disco
///
/// Um círculo chapado é uma moeda de desenho animado. O que o olho reconhece
/// como disco é a **elipse** — o círculo visto de esguelha — mais o
/// brilho que corre pela superfície quando ele gira. Aqui a elipse vem de
/// achatar a altura pelo cosseno da inclinação, que é a projeção de um círculo
/// num plano girado, sem precisar de matriz nenhuma.
@Composable
fun Disco(
    tamanho: Dp,
    cor: Color,
    pose: Pose,
    modifier: Modifier = Modifier,
    /// Ele gira sozinho? Só quando está tocando — um disco parado no ar é um
    /// objeto; um disco girando é uma máquina em funcionamento.
    girando: Boolean = false,
) {
    /// ⚠️ **A animação só existe quando o disco gira**, e não é economia
    /// teórica.
    ///
    /// A primeira versão criava a `rememberInfiniteTransition` sempre e só usava
    /// o valor quando `girando` — ou seja, o Compose redesenhava a tela 60 vezes
    /// por segundo pra pintar um disco parado. O sintoma apareceu no emulador de
    /// um jeito inesperado: o `screencap` passou a devolver quadros pretos, que é
    /// o que uma superfície em animação contínua faz numa captura.
    ///
    /// Num aparelho de verdade o sintoma seria outro e pior: bateria indo embora
    /// com a caixa aberta na tela.
    val volta = if (!girando) {
        0f
    } else {
        val transicao = rememberInfiniteTransition(label = "disco girando")
        val animado by transicao.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "volta do disco",
        )
        animado
    }

    Box(modifier.size(tamanho)) {
        Canvas(Modifier.fillMaxSize()) {
            /// O achatamento: cosseno da inclinação vertical, com um piso pra o
            /// disco nunca virar uma linha — de perfil ele sumiria, e sumir no
            /// meio de um gesto parece defeito.
            val achatado = cos(Math.toRadians(pose.giroX.toDouble())).toFloat().coerceIn(0.35f, 1f)
            val giroNaTela = pose.giroY * 0.35f + volta

            val raio = size.minDimension / 2f
            val centro = Offset(size.width / 2f, size.height / 2f)

            rotate(degrees = giroNaTela, pivot = centro) {
                desenharDisco(centro, raio, achatado, cor)
            }
        }
    }
}

/// O desenho em si, separado pra caber num `Canvas` de qualquer lugar.
private fun DrawScope.desenharDisco(centro: Offset, raio: Float, achatado: Float, cor: Color) {
    val altura = raio * 2f * achatado

    /// O corpo: prateado com um leve tom da obra. A cor da obra entra **na
    /// reflexão**, não no plástico — §15, «a cor da obra só toca arte».
    drawOval(
        brush = Brush.linearGradient(
            0.0f to Color(0xFFB9BDC7),
            0.35f to Color(0xFFEDEFF3),
            0.5f to cor.copy(alpha = 0.55f),
            0.65f to Color(0xFFEDEFF3),
            1.0f to Color(0xFF8E939D),
        ),
        topLeft = Offset(centro.x - raio, centro.y - altura / 2f),
        size = Size(raio * 2f, altura),
    )

    /// O anel de dados — a borda mais escura que todo disco tem.
    drawOval(
        color = Color.Black.copy(alpha = 0.18f),
        topLeft = Offset(centro.x - raio * 0.96f, centro.y - altura * 0.48f),
        size = Size(raio * 1.92f, altura * 0.96f),
        style = Stroke(width = raio * 0.06f),
    )

    /// O miolo: o anel de plástico transparente e o furo.
    drawOval(
        color = Color(0xFFD8DCE4),
        topLeft = Offset(centro.x - raio * 0.34f, centro.y - altura * 0.17f),
        size = Size(raio * 0.68f, altura * 0.34f),
    )
    drawOval(
        color = Cores.fundoAfundado,
        topLeft = Offset(centro.x - raio * 0.15f, centro.y - altura * 0.075f),
        size = Size(raio * 0.3f, altura * 0.15f),
    )

    /// O arco-íris do policarbonato: duas faixas curtas, opostas, bem
    /// transparentes. É o detalhe que faz o olho dizer «disco» antes de ler
    /// qualquer coisa.
    listOf(0f, 180f).forEach { comeco ->
        drawArc(
            brush = Brush.sweepGradient(
                listOf(
                    Color.Transparent,
                    Color(0x5533CCFF),
                    Color(0x55FF66AA),
                    Color(0x5566FF99),
                    Color.Transparent,
                ),
                center = centro,
            ),
            startAngle = comeco,
            sweepAngle = 52f,
            useCenter = false,
            topLeft = Offset(centro.x - raio * 0.8f, centro.y - altura * 0.4f),
            size = Size(raio * 1.6f, altura * 0.8f),
            style = Stroke(width = raio * 0.5f),
        )
    }
}

/// A fita VHS: a carcaça, a janela e os dois carretéis.
///
/// ## O carretel é o mostrador, e é isso que a fita tem de especial
///
/// Num DVD não dá pra ver onde o filme parou. Numa fita **dá**: o rolo da
/// esquerda engorda conforme ela roda, e o da direita emagrece. É a mesma
/// informação de uma barra de progresso, só que dita pelo objeto — e é o que faz
/// «rebobinar» ser uma coisa que se entende sem explicação.
@Composable
fun FitaVHS(
    largura: Dp,
    cor: Color,
    modifier: Modifier = Modifier,
    /// Quanto da fita já rodou, de 0 a 1. É o que decide a grossura dos rolos.
    andado: Float = 0f,
    /// O quanto os carretéis já giraram, em voltas. Quem anima é quem chama —
    /// aqui é só desenho, e é o que deixa a tela de rebobinar controlar a
    /// velocidade do jeito dela.
    voltas: Float = 0f,
) {
    Box(modifier.size(largura, largura * 0.58f)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.height * 0.30f
            val esquerda = Offset(size.width * 0.31f, size.height * 0.5f)
            val direita = Offset(size.width * 0.69f, size.height * 0.5f)

            /// A carcaça preta, com o topo levemente mais claro — a fita é
            /// plástico fosco, e plástico fosco pega luz de cima.
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF2A2A2E), Color(0xFF141416)),
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.06f),
            )

            /// A janela: o retângulo transparente por onde se veem os rolos.
            drawRoundRect(
                color = Color(0xFF0B0B0D),
                topLeft = Offset(size.width * 0.12f, size.height * 0.16f),
                size = Size(size.width * 0.76f, size.height * 0.68f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.04f),
            )

            /// Os dois rolos. O da esquerda cresce com o que já rodou; o da
            /// direita é o complemento — a fita tem comprimento fixo, e o que
            /// sai de um lado entra no outro.
            desenharCarretel(esquerda, r, 0.34f + 0.62f * andado, voltas, cor)
            desenharCarretel(direita, r, 0.34f + 0.62f * (1f - andado), -voltas, cor)

            /// A etiqueta de papel, na parte de baixo — é onde, numa fita de
            /// verdade, alguém escreveu o nome do filme a caneta.
            drawRoundRect(
                color = Color(0xFFE8E2D2).copy(alpha = 0.9f),
                topLeft = Offset(size.width * 0.16f, size.height * 0.80f),
                size = Size(size.width * 0.68f, size.height * 0.14f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
            )
        }
    }
}

/// Um carretel: o rolo de fita enrolado e os dentes do eixo.
private fun DrawScope.desenharCarretel(
    centro: Offset,
    raioMaximo: Float,
    quanto: Float,
    voltas: Float,
    cor: Color,
) {
    val raio = raioMaximo * quanto.coerceIn(0.2f, 1f)

    /// O rolo de fita: marrom escuro, com um brilho fraco da cor da obra em
    /// cima — é a luz da sala batendo no filme, e é o que impede o rolo de ser
    /// um disco preto.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFF3A2A22), Color(0xFF1E1512)),
            center = centro,
            radius = raio,
        ),
        radius = raio,
        center = centro,
    )
    drawCircle(
        color = cor.copy(alpha = 0.10f),
        radius = raio,
        center = centro,
    )

    /// O eixo branco e os dentes, que são o que **mostra o giro**: sem eles, um
    /// rolo redondo girando é um rolo redondo parado.
    drawCircle(color = Color(0xFFD5D5D8), radius = raioMaximo * 0.28f, center = centro)
    val angulo = voltas * 360f
    repeat(6) { i ->
        val a = Math.toRadians((angulo + i * 60f).toDouble())
        val ponta = Offset(
            centro.x + (raioMaximo * 0.26f * cos(a)).toFloat(),
            centro.y + (raioMaximo * 0.26f * sin(a)).toFloat(),
        )
        drawLine(
            color = Color(0xFF6E6E74),
            start = centro,
            end = ponta,
            strokeWidth = raioMaximo * 0.09f,
        )
    }
    drawCircle(color = Color(0xFF17171A), radius = raioMaximo * 0.10f, center = centro)
}
