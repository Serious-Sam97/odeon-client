package dev.odeon.android.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/// O grão de película — o experimento da R6.
///
/// > «A pergunta honesta: grão em cima de pôster pode virar sujeira. Proponho
/// > entrar atrás de uma chave, medir com screenshot em três pôsteres (claro,
/// > escuro, ilustrado), e só ficar se sobreviver aos três.»
///
/// A chave é [LIGADO], logo abaixo. **Ele reprovou no teste**, e o comentário
/// dela tem o veredito e o porquê.
///
/// ## Como ele é feito, e por que não é ruído por quadro
///
/// Uma folha de 96×96 de cinza aleatório, gerada **uma vez** e repetida com
/// `TileMode.Repeated`. As duas alternativas são piores:
///
/// | | por quê não |
/// |---|---|
/// | sortear pixel por quadro | grão que **cintila** é chuvisco de TV, não película; e é trabalho de CPU a 60Hz |
/// | um `drawPoints` com N pontos | mesma conta, e some no `Canvas` a cada recomposição |
///
/// Repetida, a textura é um `Shader` que a GPU desenha de graça, e o `remember`
/// garante que o bitmap nasça uma vez por tela e não por quadro.
///
/// ## A semente é fixa, e isso é de propósito
///
/// Com semente aleatória, dois screenshots do mesmo pôster teriam grãos
/// diferentes — e o screenshot é a régua deste projeto. Comparar "antes e
/// depois" exige que o depois seja reproduzível.
object Grao {

    /// ⚠️ **Desligado — ele reprovou**, e o teste foi o que a R6 mandou fazer.
    ///
    /// > «Proponho entrar atrás de uma chave, medir com screenshot em três
    /// > pôsteres (claro, escuro, ilustrado), e só ficar se sobreviver aos três.»
    ///
    /// Feito em 04/08/2026, e não em três telas separadas: a própria grade
    /// mostra os três casos lado a lado. Dois screenshots do **mesmo** recorte,
    /// com e sem a camada, ampliados 1,8×:
    ///
    /// | pôster | o que aconteceu |
    /// |---|---|
    /// | **claro** — 007: A Serviço Secreto, neve branca | **reprovou.** A neve fica manchada. É a "sujeira" que a própria R6 previu |
    /// | **escuro** — Cassino Royale | some no preto. Nem ajuda nem atrapalha |
    /// | **ilustrado** — 007: A Serviço Secreto, metade de baixo | embarra de leve; a trama da ilustração e a do grão brigam |
    ///
    /// Um de três é claramente pior, um é neutro, e nenhum é melhor. O critério
    /// que a fase escreveu era sobreviver aos três.
    ///
    /// **E o caso que reprova é o mais comum do acervo.** 8.598 das 17.930 obras
    /// (48%) não têm pôster e caem no fundo da cor dominante — uma superfície
    /// chapada, que é onde grão mais aparece e menos tem o que texturizar.
    ///
    /// ⚠️ Isto é **julgamento visual sobre um A/B**, não um número. Não há régua
    /// numérica pra "parece sujo", e inventar uma seria pior que assumir o
    /// julgamento. Os dois recortes estão no relato da sessão.
    ///
    /// Fica escrito e desligado em vez de apagado: a decisão é do dono, e o custo
    /// de reavaliar é trocar `false` por `true` e pôr a `Camada` de volta sobre
    /// alguma arte. O que **não** dá é ele entrar sozinho por parecer bonito num
    /// pôster escolhido a dedo.
    const val LIGADO = false

    /// A folha de ruído, gerada uma vez.
    ///
    /// 96 é o lado, e ele importa: pequeno demais (16, 32) e o olho enxerga o
    /// **padrão** se repetindo, que é pior que não ter grão; grande demais e o
    /// bitmap começa a pesar por nada — 96×96 ARGB são 36 KB.
    private fun folha(lado: Int = 96, semente: Int = 7): Bitmap {
        val rnd = Random(semente)
        val pixels = IntArray(lado * lado) {
            val v = rnd.nextInt(96, 208)
            /// Alfa 0x14 (8%) fixo no próprio pixel, e não no `alpha` do
            /// desenho: assim a intensidade é parte da textura e não some se
            /// alguém empilhar outra camada por cima.
            (0x14 shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(pixels, lado, lado, Bitmap.Config.ARGB_8888)
    }

    /// A camada, pra ser posta por cima da arte.
    ///
    /// Não emite nada quando [LIGADO] é `false` — nem um `Box` vazio. Uma camada
    /// invisível ainda mede, ainda desenha e ainda entra na árvore.
    @Composable
    fun Camada(modifier: Modifier = Modifier) {
        if (!LIGADO) return
        val pincel = remember {
            ShaderBrush(
                ImageShader(folha().asImageBitmap(), TileMode.Repeated, TileMode.Repeated),
            )
        }
        androidx.compose.foundation.layout.Box(
            modifier.fillMaxSize().background(pincel),
        )
    }
}
