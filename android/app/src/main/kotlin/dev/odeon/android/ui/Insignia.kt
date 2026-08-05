package dev.odeon.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlin.math.abs

/// A insígnia: o rosto, o anel do nível e o selo com o número.
///
/// ## Três coisas no mesmo lugar, e o comentário da web é a especificação
///
/// > «Três coisas no mesmo lugar de 38px: o rosto no miolo, o arco contando
/// > quanto falta pro próximo nível, e o número num selo — a fase 5 na barra,
/// > sem ocupar uma entrada.»
///
/// E o motivo de serem duas marcas e não uma está na folha da web
/// (`styles.css:415`): «o anel de fora continua dizendo *quanto falta*; o selo
/// diz *onde você está*, e são duas perguntas diferentes».
///
/// ## O arco é `drawArc`, e não um gradiente cônico
///
/// A web usa `conic-gradient` porque é o que o CSS tem. Aqui um arco desenhado
/// é mais barato e mais exato: nada de cor de fundo vazando na emenda dos
/// 360º, e a espessura é a mesma em toda a volta.
///
/// ## A cor é a moldura escolhida, e o dourado é o padrão
///
/// A R43 da web faz a moldura tingir o perfil inteiro; a insígnia leva essa
/// escolha pra toda tela. Sem moldura escolhida — que é o caso de quem nunca
/// abriu o perfil —, o anel é o dourado da casa. **Nunca uma cor sorteada**: a
/// cor sorteada é da marca do nome, e ali ela identifica; aqui ela decoraria.
@Composable
fun Insignia(
    nome: String,
    /// A URL do rosto escolhido, já com o token de mídia. `null` cai na marca
    /// desenhada — que é o padrão de quem não escolheu, e não um buraco (§10.6).
    rosto: String?,
    /// `null` enquanto o perfil não chegou. Aí o selo mostra `·`, como a web —
    /// um número que ainda não se sabe não vira `0`, que seria afirmar (§18).
    nivel: Int?,
    /// Quanto do nível atual já foi andado, de 0 a 1.
    fatia: Float,
    /// Primeiro dos opcionais porque o lint cobra — e ele cobra com razão: quem
    /// lê a chamada espera o `modifier` no mesmo lugar em que todo composable do
    /// Android o põe.
    modifier: Modifier = Modifier,
    cor: Color = Cores.destaque,
    tamanho: Dp = 36.dp,
) {
    Box(modifier = modifier.size(tamanho), contentAlignment = Alignment.Center) {
        /// O arco começa às 12 horas e anda no sentido do relógio, que é como
        /// todo medidor circular que as pessoas já viram funciona — relógio,
        /// velocímetro, o anel de atividade do celular delas.
        Canvas(Modifier.fillMaxSize()) {
            val traco = size.minDimension * 0.075f
            val meio = traco / 2f
            val caixa = Size(size.width - traco, size.height - traco)

            drawArc(
                color = Cores.linha,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(meio, meio),
                size = caixa,
                style = Stroke(width = traco),
            )
            /// Fatia zero **não desenha nada**. Um traço mínimo "pra mostrar que
            /// existe" seria dizer que já se andou alguma coisa neste nível.
            if (fatia > 0f) {
                drawArc(
                    color = cor,
                    startAngle = -90f,
                    sweepAngle = 360f * fatia.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(meio, meio),
                    size = caixa,
                    style = Stroke(width = traco),
                )
            }
        }

        /// O miolo: o rosto escolhido, ou a marca do nome.
        ///
        /// O recuo de 12% é o que faz o anel ser anel e não disco — é o
        /// `inset: 2.5px` da folha, escrito em proporção pra a insígnia poder
        /// mudar de tamanho sem o miolo comer o arco.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(tamanho * 0.12f)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                rosto != null -> AsyncImage(
                    model = rosto,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                /// ⚠️ **Nome em branco não vira marca.**
                ///
                /// A marca é derivada do nome; sem nome ela seria um `?` numa
                /// cor derivada de nada — e o nome só chega junto com o perfil.
                /// Desenhar o disco liso durante essa fração de segundo é dizer
                /// «ainda não sei», que é o certo. A alternativa era esconder a
                /// gaveta até a resposta, e aí o **sair** apareceria e sumiria
                /// sozinho na frente de quem estava indo tocá-lo.
                nome.isBlank() -> Box(Modifier.fillMaxSize().background(Cores.fundoElevado))

                else -> MarcaDoNome(nome = nome, tamanho = tamanho * 0.76f)
            }
        }

        SeloDoNivel(nivel = nivel, cor = cor, tamanho = tamanho)
    }
}

/// O selo, no canto de baixo à direita — o contador de uma notificação.
@Composable
private fun BoxScope.SeloDoNivel(nivel: Int?, cor: Color, tamanho: Dp) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            /// 38% e não 46%: o screenshot mostrou o selo comendo um quarto do
            /// rosto. Na web ele tem 18px sobre uma insígnia de 38 — 47% —, mas
            /// lá ele **transborda** a insígnia (`right: -2px`), e aqui ele
            /// encosta por dentro. Mesma proporção em caixas diferentes dá
            /// tamanhos diferentes na tela.
            .size(tamanho * 0.38f)
            /// A borda da cor do fundo, e ela não é enfeite: o selo encosta no
            /// rosto, e rosto é foto. Sem o vão escuro em volta, um selo dourado
            /// sobre uma pele clara desaparece.
            .clip(CircleShape)
            .background(Cores.fundo)
            .padding(tamanho * 0.035f)
            .clip(CircleShape)
            .background(cor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = nivel?.toString() ?: "·",
            color = Cores.fundo,
            fontSize = (tamanho.value * 0.24f).sp,
            /// Sem folga de linha, senão o algarismo nasce descentralizado
            /// dentro de um círculo de 13dp — e o defeito só aparece em foto.
            lineHeight = (tamanho.value * 0.24f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/// A marca desenhada de uma pessoa — o §10.6 da referência, em Compose.
///
/// ## Ela é derivada do nome, e é o padrão de quem não escolheu
///
/// Zero bytes: cor + uma de quatro figuras + a inicial, tudo saindo do mesmo
/// hash do nome. Não é espaço reservado esperando arte — é o retrato de quem
/// ainda não escolheu um, e continua existindo depois que a escolha chegar.
///
/// ## ⚠️ O hash é **o mesmo da web**, e isso não é detalhe
///
/// `hueFromTitle` (`api.ts:2462`) faz `hash = (hash << 5) - hash + código`, que
/// é `hash * 31 + código` com estouro de 32 bits — e o `hash |= 0` do JavaScript
/// é exatamente o que o `Int` do Kotlin faz sozinho.
///
/// Se as duas contas divergissem, a mesma pessoa teria uma cor no navegador e
/// outra no celular. Uma marca que muda de cor entre clientes não identifica
/// ninguém: ela vira enfeite, e aí não valia os 30 pixels.
@Composable
fun MarcaDoNome(nome: String, tamanho: Dp) {
    val matiz = matizDoNome(nome).toFloat()
    val fundo = corOklch(0.32f, 0.06f, matiz)
    val marca = corOklch(0.52f, 0.09f, matiz)
    val letra = corOklch(0.94f, 0.02f, matiz)
    val figura = matizDoNome(nome + nome) % 4

    Box(modifier = Modifier.size(tamanho), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val centro = Offset(size.width / 2f, size.height / 2f)
            drawCircle(color = fundo, radius = r, center = centro)

            /// A figura é **textura, não desenho**: 42% de opacidade, atrás da
            /// letra. Uma figura que compete com a inicial deixa a lista mais
            /// difícil de ler, que é o oposto do que um retrato faz numa lista.
            val lado = r * 1.1f
            when (figura) {
                0 -> drawCircle(color = marca, radius = r * 0.55f, center = centro, alpha = 0.42f)
                1 -> drawRect(
                    color = marca,
                    topLeft = Offset(centro.x - lado / 2f, centro.y - lado / 2f),
                    size = Size(lado, lado),
                    alpha = 0.42f,
                )
                2 -> drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(centro.x, centro.y - r * 0.65f)
                        lineTo(centro.x + r * 0.65f, centro.y + r * 0.5f)
                        lineTo(centro.x - r * 0.65f, centro.y + r * 0.5f)
                        close()
                    },
                    color = marca,
                    alpha = 0.42f,
                )
                else -> drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(centro.x, centro.y - r * 0.7f)
                        lineTo(centro.x + r * 0.7f, centro.y)
                        lineTo(centro.x, centro.y + r * 0.7f)
                        lineTo(centro.x - r * 0.7f, centro.y)
                        close()
                    },
                    color = marca,
                    alpha = 0.42f,
                )
            }
        }
        Text(
            text = inicialDe(nome),
            color = letra,
            fontSize = (tamanho.value * 0.44f).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/// O matiz, de 0 a 359 — a mesma conta do `hueFromTitle` da web.
///
/// O `abs` tem uma armadilha e ela está tratada: `abs(Int.MIN_VALUE)` continua
/// negativo em complemento de dois, e um índice negativo escolheria a figura
/// `-2`. Em JavaScript o problema não existe porque `Math.abs` devolve `double`.
internal fun matizDoNome(nome: String): Int {
    var hash = 0
    for (c in nome) hash = hash * 31 + c.code
    if (hash == Int.MIN_VALUE) return 0
    return abs(hash) % 360
}

/// A inicial, ou `?` pra nome em branco — que o servidor não manda, mas que uma
/// resposta truncada produziria, e aí a marca desenharia uma letra vazia.
internal fun inicialDe(nome: String): String =
    nome.trim().firstOrNull()?.uppercase() ?: "?"
