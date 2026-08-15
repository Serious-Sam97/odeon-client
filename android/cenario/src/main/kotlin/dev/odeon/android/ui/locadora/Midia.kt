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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import dev.odeon.android.ui.Texto
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.Serifada
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
    /// ## A arte do rótulo — «tenta colocar o CD real»
    ///
    /// A queixa do dono era exata: o disco prateado genérico é um **DVD-R
    /// queimado em casa**, e uma locadora não aluga DVD-R. Disco prensado tem a
    /// arte da obra impressa de furo a borda — é a foto dos discos da Disney que
    /// ele mandou de referência.
    ///
    /// A arte é a mesma capa que a caixa já carrega, e `null` degrada pro
    /// prateado de antes (§24): metade do acervo não tem pôster, e nesses o
    /// disco sem impressão é a verdade.
    arte: String? = null,
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

    /// O achatamento: cosseno da inclinação vertical, com um piso pra o
    /// disco nunca virar uma linha — de perfil ele sumiria, e sumir no
    /// meio de um gesto parece defeito.
    val achatado = cos(Math.toRadians(pose.giroX.toDouble())).toFloat().coerceIn(0.35f, 1f)
    val giroNaTela = pose.giroY * 0.35f + volta

    /// ⚠️ O achatamento saiu do desenho e virou `graphicsLayer` do conjunto:
    /// com a arte impressa, achatar oval por oval deixaria a imagem redonda
    /// sobre anéis achatados — a elipse tem que ser do **disco inteiro**.
    Box(
        modifier
            .size(tamanho)
            .graphicsLayer { scaleY = achatado },
    ) {
        if (arte != null) {
            AsyncImage(
                model = arte,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize(0.96f)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    /// A arte gira **com o disco**; o brilho, não — reflexo é da
                    /// luz da sala, e a luz não roda junto com o rótulo.
                    .graphicsLayer { rotationZ = giroNaTela },
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val raio = size.minDimension / 2f
            val centro = Offset(size.width / 2f, size.height / 2f)

            rotate(degrees = giroNaTela, pivot = centro) {
                desenharDisco(centro, raio, cor, temArte = arte != null)
            }
            /// O verniz: a luz varrendo o policarbonato por cima da impressão.
            /// Fora do `rotate` — ver o comentário da arte.
            desenharVerniz(centro, raio)
        }
    }
}

/// O desenho em si, separado pra caber num `Canvas` de qualquer lugar.
///
/// Com arte, o corpo não é pintado — a impressão está por baixo e o que o
/// desenho põe são as partes que **nenhuma impressão cobre** num disco de
/// verdade: a borda transparente, o espelho do cubo, o furo. Sem arte, o corpo
/// prateado de antes continua sendo a verdade (§24).
private fun DrawScope.desenharDisco(centro: Offset, raio: Float, cor: Color, temArte: Boolean) {
    if (!temArte) {
        /// O corpo: prateado com um leve tom da obra. A cor da obra entra **na
        /// reflexão**, não no plástico — §15, «a cor da obra só toca arte».
        drawCircle(
            brush = Brush.linearGradient(
                0.0f to Color(0xFFB9BDC7),
                0.35f to Color(0xFFEDEFF3),
                0.5f to cor.copy(alpha = 0.55f),
                0.65f to Color(0xFFEDEFF3),
                1.0f to Color(0xFF8E939D),
            ),
            radius = raio,
            center = centro,
        )
    }

    /// A borda de leitura: o anel fino de policarbonato sem impressão, com o
    /// vinco escuro onde os dados acabam. É o que separa «círculo com imagem»
    /// de «disco impresso» — todo prensado tem esse aro nu.
    drawCircle(
        color = Color(0xFFC9CDD6).copy(alpha = if (temArte) 0.9f else 0.5f),
        radius = raio * 0.98f,
        center = centro,
        style = Stroke(width = raio * 0.05f),
    )
    drawCircle(
        color = Color.Black.copy(alpha = 0.22f),
        radius = raio * 0.945f,
        center = centro,
        style = Stroke(width = raio * 0.018f),
    )

    /// O cubo: o anel-espelho em volta do furo — a parte prateada que sobra em
    /// qualquer rótulo —, o plástico translúcido e o furo em si.
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(
                Color(0xFFD8DCE4),
                Color(0xFFAEB3BE),
                Color(0xFFEDEFF3),
                Color(0xFFB9BDC7),
                Color(0xFFD8DCE4),
            ),
            center = centro,
        ),
        radius = raio * 0.34f,
        center = centro,
    )
    drawCircle(color = Color(0xFFE4E7ED), radius = raio * 0.22f, center = centro)
    drawCircle(color = Cores.fundoAfundado, radius = raio * 0.13f, center = centro)
    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = raio * 0.135f,
        center = centro,
        style = Stroke(width = raio * 0.015f),
    )
}

/// A luz sobre o policarbonato: o facho diagonal e o arco-íris da difração.
///
/// Separados do disco porque **não giram com ele** — reflexo é da lâmpada da
/// sala, e a lâmpada fica parada enquanto o rótulo roda.
private fun DrawScope.desenharVerniz(centro: Offset, raio: Float) {
    /// O facho: um quarto de volta de brilho branco, na diagonal de cima —
    /// a mesma lâmpada do `luzNoLado`, batendo no plástico.
    drawArc(
        brush = Brush.sweepGradient(
            listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.30f),
                Color.White.copy(alpha = 0.05f),
                Color.Transparent,
            ),
            center = centro,
        ),
        startAngle = 205f,
        sweepAngle = 70f,
        useCenter = false,
        topLeft = Offset(centro.x - raio * 0.66f, centro.y - raio * 0.66f),
        size = Size(raio * 1.32f, raio * 1.32f),
        style = Stroke(width = raio * 0.6f),
    )

    /// O arco-íris do policarbonato: duas faixas curtas, opostas, bem
    /// transparentes. É o detalhe que faz o olho dizer «disco» antes de ler
    /// qualquer coisa.
    listOf(20f, 200f).forEach { comeco ->
        drawArc(
            brush = Brush.sweepGradient(
                listOf(
                    Color.Transparent,
                    Color(0x4433CCFF),
                    Color(0x44FF66AA),
                    Color(0x4466FF99),
                    Color.Transparent,
                ),
                center = centro,
            ),
            startAngle = comeco,
            sweepAngle = 52f,
            useCenter = false,
            topLeft = Offset(centro.x - raio * 0.8f, centro.y - raio * 0.8f),
            size = Size(raio * 1.6f, raio * 1.6f),
            style = Stroke(width = raio * 0.36f),
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
    /// ## O título no rótulo — «a original tinha detalhes do filme»
    ///
    /// A referência do dono é a fita d'*O Rei Leão*: o rótulo de papel dela é
    /// **impresso**, com o nome do filme — não um retângulo bege mudo. `null`
    /// deixa o papel em branco, que é o que uma fita sem identificação tem.
    titulo: String? = null,
) {
    val alturaDaFita = largura * 0.58f
    Box(modifier.size(largura, alturaDaFita)) {
        Canvas(Modifier.fillMaxSize()) {
            /// ⚠️ Os rolos moram **dentro da janela**, e a foto cobrou: com o
            /// centro no meio da carcaça (0,5) e raio de 0,30, o rolo cheio
            /// vazava o vidro e invadia o rótulo. O centro é o centro da janela
            /// (0,46) e o raio máximo é o que cabe nela com 1% de folga.
            val r = size.height * 0.27f
            val esquerda = Offset(size.width * 0.31f, size.height * 0.46f)
            val direita = Offset(size.width * 0.69f, size.height * 0.46f)

            /// A carcaça preta, com o topo levemente mais claro — a fita é
            /// plástico fosco, e plástico fosco pega luz de cima.
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF2A2A2E), Color(0xFF141416)),
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.06f),
            )

            /// Os frisos de pega — as linhas em relevo que toda carcaça tem nas
            /// beiradas. Detalhe barato que tira o «retângulo liso» da fita.
            listOf(0.045f, 0.075f, 0.925f, 0.955f).forEach { x ->
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(size.width * x, size.height * 0.12f),
                    end = Offset(size.width * x, size.height * 0.88f),
                    strokeWidth = size.width * 0.008f,
                )
            }

            /// A janela: o retângulo transparente por onde se veem os rolos —
            /// com a moldura clara do acrílico, que a primeira versão não tinha.
            drawRoundRect(
                color = Color(0xFF0B0B0D),
                topLeft = Offset(size.width * 0.12f, size.height * 0.16f),
                size = Size(size.width * 0.76f, size.height * 0.60f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.04f),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.10f),
                topLeft = Offset(size.width * 0.12f, size.height * 0.16f),
                size = Size(size.width * 0.76f, size.height * 0.60f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.04f),
                style = Stroke(width = size.height * 0.012f),
            )

            /// Os dois rolos. O da esquerda cresce com o que já rodou; o da
            /// direita é o complemento — a fita tem comprimento fixo, e o que
            /// sai de um lado entra no outro.
            desenharCarretel(esquerda, r, 0.34f + 0.62f * andado, voltas, cor)
            desenharCarretel(direita, r, 0.34f + 0.62f * (1f - andado), -voltas, cor)

            /// Os quatro parafusos dos cantos — a carcaça é aparafusada, e são
            /// eles que dizem «objeto montado» em vez de «desenho de fita».
            listOf(
                Offset(size.width * 0.035f, size.height * 0.07f),
                Offset(size.width * 0.965f, size.height * 0.07f),
                Offset(size.width * 0.035f, size.height * 0.93f),
                Offset(size.width * 0.965f, size.height * 0.93f),
            ).forEach { onde ->
                drawCircle(Color(0xFF0A0A0C), radius = size.width * 0.012f, center = onde)
                drawCircle(
                    Color.White.copy(alpha = 0.18f),
                    radius = size.width * 0.012f,
                    center = onde,
                    style = Stroke(width = size.width * 0.004f),
                )
            }

            /// A etiqueta de papel, na parte de baixo, com a **cinta** na cor da
            /// obra — as fitas impressas levavam uma faixa colorida no rótulo, e
            /// a cor é a `dominant_color` que o servidor já extraiu.
            drawRoundRect(
                color = Color(0xFFE8E2D2).copy(alpha = 0.92f),
                topLeft = Offset(size.width * 0.16f, size.height * 0.79f),
                size = Size(size.width * 0.68f, size.height * 0.16f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
            )
            drawRect(
                color = cor.copy(alpha = 0.85f),
                topLeft = Offset(size.width * 0.16f, size.height * 0.79f),
                size = Size(size.width * 0.68f, size.height * 0.025f),
            )
        }

        /// O título, impresso no papel. É `Text` e não desenho porque é texto de
        /// verdade — elipsado, medido, no corpo que a largura da fita permitir.
        if (titulo != null) {
            Texto(
                text = titulo,
                style = TextStyle(
                    fontFamily = Serifada,
                    fontSize = (largura.value * 0.032f).sp,
                    letterSpacing = 0.06.em,
                    color = Color(0xFF3A3428),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.62f)
                    .padding(bottom = alturaDaFita * 0.048f),
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
