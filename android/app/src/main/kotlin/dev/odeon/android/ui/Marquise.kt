package dev.odeon.android.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/// As lâmpadas da marquise, com a luz correndo por elas.
///
/// ## ⚠️ Isto NÃO é o que a R6 pediu, e a diferença é uma medição
///
/// A R6 do `docs/REDESENHO.md` diz: «entra a perfuração de película nas bordas
/// do cartão de destaque do "para você", **como na web**». Fui buscar a
/// perfuração na folha pra copiar os números, e ela não existe — o `.pick-art`
/// (`styles.css:2317`) é arte com uma lavagem radial e mais nada. Não há
/// `repeating-radial-gradient` de furo em lugar nenhum do `styles.css`.
///
/// O que o herói do "para você" tem — e o `ForYou.tsx:369` põe exatamente ali —
/// é a `.bulbs` (`styles.css:2104`), e o comentário dela na folha diz o que ela
/// vale:
///
/// > «Elas existem desde a R1, e desde a R1 estavam apagadas — uma fileira de
/// > pontos parados. Agora a luz **corre** por elas, que é o que uma marquise de
/// > cinema faz e é **o efeito mais da casa que este projeto tem**.»
///
/// Então este arquivo desenha a marquise, não a perfuração. **É substituição, e
/// é vetável:** a perfuração continua sendo uma ideia legítima, e são umas 20
/// linhas — ela só não é «como na web», porque na web ela não está.
///
/// ## O truque, que é o mesmo da folha
///
/// > «O truque é não animar bulbo por bulbo: os pontos são um gradiente
/// > repetido, não elementos. Custo: uma propriedade animada, nenhum nó a mais.»
///
/// Aqui é um `Canvas` só e **um** `Float` animado. Os ~20 círculos são
/// redesenhados por quadro, o que é trabalho de GPU sem passar por composição
/// nem por layout — as duas fases caras. Nenhum nó entra na árvore.
///
/// ## Ela é infinita, e isso tem duas consequências que valem dizer
///
/// **A preferência do sistema é respeitada de graça.** O
/// `rememberInfiniteTransition` lê o `MotionDurationScale` do contexto, que no
/// Android sai do `Settings.Global.ANIMATOR_DURATION_SCALE`. Com animação
/// desligada nos ajustes, a luz para de correr e as lâmpadas ficam acesas
/// paradas — que é o que a §R8 do redesenho exige e aqui não custou linha.
///
/// **E ela só roda enquanto está composta.** Sair da aba mata a animação junto
/// com o `remember`; não há laço vivo em segundo plano.
///
/// ## Os números saem da folha, não do olho
///
/// | | `.bulbs` | aqui |
/// |---|---|---|
/// | passo entre bulbos | `background-size: 22px` | 22dp |
/// | raio | `0 1.5px` no `radial-gradient` | 1.5dp |
/// | altura da faixa | `height: 3px` | 3dp |
/// | apagado | `opacity: 0.42` | alfa 0.42 |
/// | volta | `3.4s linear infinite` | 3400ms, linear |
/// | largura da banda acesa | máscara de 11% sobre 220% ≈ **5%** | 0.055 |
@Composable
fun LampadasDaMarquise(modifier: Modifier = Modifier) {
    val transicao = rememberInfiniteTransition(label = "marquise")

    /// De −0,6 a 1,6 e não de 0 a 1: a banda tem que **entrar** pela esquerda e
    /// **sair** pela direita. Indo de 0 a 1 ela nasceria acesa no primeiro bulbo
    /// e morreria no último, que lê como um pisca-pisca em vez de luz correndo.
    /// É o mesmo motivo do `mask-position: -60% → 160%` da folha.
    val faixa by transicao.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing)),
        label = "faixa de luz",
    )

    Canvas(modifier.fillMaxWidth().height(3.dp)) {
        val passo = 22.dp.toPx()
        val raio = 1.5.dp.toPx()
        val quantos = (size.width / passo).toInt()

        for (i in 0..quantos) {
            val x = i * passo + passo / 2f
            val posicao = x / size.width
            /// O quanto **este** bulbo está dentro da banda, de 0 a 1.
            val aceso = (1f - abs(posicao - faixa) / 0.055f).coerceIn(0f, 1f)

            drawCircle(
                color = Cores.destaqueQuente,
                /// O bulbo aceso incha 15%. É pouco de propósito: uma lâmpada
                /// que dobra de tamanho lê como bolha, não como filamento.
                radius = raio * (1f + aceso * 0.15f),
                center = Offset(x, size.height / 2f),
                alpha = 0.42f + aceso * 0.58f,
            )
        }
    }
}
