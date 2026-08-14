package dev.odeon.android.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import dev.odeon.android.ui.Cores

/// O foco, que na sala faz o trabalho que o dedo faz no celular.
///
/// ## A pergunta que este arquivo responde
///
/// «Onde eu estou?» — e ela não existe num celular. Lá a pessoa olha pro próprio
/// dedo; o app não precisa dizer nada. Aqui não há cursor, não há dedo, e a
/// única resposta possível é o que a tela faz de diferente com **um** item.
///
/// Se essa resposta for fraca, o app não é feio: ele é inutilizável. Apertar
/// seta e não saber o que se moveu é o defeito que faz alguém desistir de um app
/// de TV nos primeiros dez segundos.
///
/// ## Três coisas ao mesmo tempo, e cada uma cobre a falha da outra
///
/// | | o que ela resolve | quando ela falha sozinha |
/// |---|---|---|
/// | **escala** | vê-se de longe qual item cresceu | dois itens vizinhos de tamanhos diferentes já são naturalmente desiguais |
/// | **borda dourada** | diz exatamente onde termina o item | some sobre arte clara com muita borda branca |
/// | **texto que acende** | o título do focado fica legível, o resto apaga | sozinho é sutil demais a 3 m |
///
/// Usar só uma é o erro comum. A escala sozinha é o mais comum de todos, e ela
/// falha justamente na fileira mais importante: a de "continuar assistindo", em
/// que os cartões são todos do mesmo tamanho e o crescimento de 12% vira o
/// **único** sinal, competindo com a arte do filme por atenção.
///
/// ## A animação é curta de propósito
///
/// 120ms. O D-pad permite segurar a seta e correr a fileira inteira, e uma
/// animação longa faz o foco chegar depois do dedo — a tela mostra o item que já
/// não é mais o escolhido. Curto o bastante pra não atrasar, longo o bastante
/// pra não piscar.

/// Uma coisa que se pode escolher com o controle.
///
/// ## Por que `clickable` e não `focusable` + `onKeyEvent`
///
/// A escrita à mão é a que aparece em todo exemplo de app de TV:
///
///     Modifier.focusable().onKeyEvent { if (it.key == Key.DirectionCenter) … }
///
/// e ela tem dois defeitos silenciosos. O primeiro é a ordem: eventos de tecla
/// sobem do nó focado pra fora, então o `onKeyEvent` precisa vir **antes** do
/// `focusable` na cadeia pra ser o pai dele. Escrito na ordem inversa — que é
/// como quase todo exemplo escreve — o bloco simplesmente não roda.
///
/// O segundo é a lista de teclas. `DirectionCenter` não é a única que significa
/// "escolher": há `Enter` (teclado bluetooth), `NumPadEnter` e `Spacebar`, e um
/// controle de Google TV manda uma ou outra conforme o fabricante e conforme
/// haver teclado pareado.
///
/// O `clickable` do `foundation` já faz as duas coisas certas — ele torna
/// focável e trata a família inteira de teclas de clique —, e ainda dá a repetição
/// de tecla de graça. O que ele traz de errado pra cá é o **ripple**, que é
/// resposta ao toque; `indication = null` o desliga, e quem responde é o desenho
/// abaixo.
@Composable
fun Focavel(
    aoEscolher: () -> Unit,
    modifier: Modifier = Modifier,
    forma: Shape = RoundedCornerShape(10.dp),
    /// Quem cresce ao receber o foco. `false` quando quem escala é um contêiner
    /// **por fora** — é o caso do `Cartaz`, onde crescer só a arte a fazia
    /// invadir o título que vem abaixo. Ver o comentário lá.
    escalar: Boolean = true,
    /// Avisa o chamador que o foco entrou ou saiu, pra quem precisa desenhar
    /// algo fora desta caixa.
    aoFocar: (Boolean) -> Unit = {},
    /// Quando `false`, o item aparece mas não recebe foco — o D-pad passa por
    /// cima dele. É o que um cartaz sem arquivo pra tocar faz: ele existe no
    /// acervo, e escolher não levaria a lugar nenhum.
    escolhivel: Boolean = true,
    /// ⚠️ Desliga o anel de foco desenhado por aqui, pra quem precisa desenhar
    /// o dele.
    ///
    /// Existe por causa de **uma** peça: o `BotaoDeTocar` do `:cenario` é um
    /// disco de 60dp dentro de um halo de 124dp, e o halo é conteúdo — ele faz
    /// parte do desenho do botão. O anel daqui abraça o conteúdo inteiro, então
    /// ele saía com o dobro do diâmetro do disco: «esse círculo do select do
    /// play tá muito grande».
    ///
    /// A saída não é encolher o halo (ele é a peça) nem recortar o conteúdo (o
    /// halo sumiria): é deixar quem sabe onde o disco está desenhar o anel em
    /// volta **dele**. O `focado` já chega no `conteudo`, então quem desliga
    /// isto tem tudo o que precisa.
    anel: Boolean = true,
    conteudo: @Composable BoxScope.(focado: Boolean) -> Unit,
) {
    var focado by remember { mutableStateOf(false) }
    val interacao = remember { MutableInteractionSource() }
    val escala by animateFloatAsState(
        targetValue = if (focado && escalar) Sala.ESCALA_DO_FOCO else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "escala do foco",
    )

    Box(
        modifier
            .scale(escala)
            .onFocusChanged {
                focado = it.isFocused
                aoFocar(it.isFocused)
            }
            .clickable(
                interactionSource = interacao,
                indication = null,
                enabled = escolhivel,
                onClick = aoEscolher,
            )
            .clip(forma)
            .border(
                width = if (focado && anel) 3.dp else 0.dp,
                color = if (focado && anel) Cores.destaqueQuente else Color.Transparent,
                shape = forma,
            ),
    ) {
        conteudo(focado)
    }
}

/// Um botão da sala: retângulo com rótulo, que acende ao receber o foco.
///
/// ⚠️ **Ele acende invertendo**, e não pondo uma borda. Um botão de TV focado
/// tem que ser óbvio a três metros, e o contorno dourado que basta pra um cartaz
/// de 200dp não basta pra um botão de 48dp de altura — a área é pequena demais
/// pra a borda pesar. Focado, o fundo vira o dourado e a letra vira o fundo.
///
/// É o oposto do que o celular faz (lá o botão primário já é dourado e o toque
/// só o afunda), e é de propósito: no celular o botão que importa é o que está
/// debaixo do dedo, aqui é o que está debaixo do foco.
@Composable
fun BotaoDaSala(
    rotulo: String,
    aoEscolher: () -> Unit,
    modifier: Modifier = Modifier,
    principal: Boolean = false,
    habilitado: Boolean = true,
) {
    /// ## ⚠️ O botão é **luz**, e não uma cápsula pintada
    ///
    /// Ele já foi um retângulo de cantos moles, e depois uma pílula dourada
    /// chapada com versalete espaçado. O dono reprovou as duas — «simples e feio»
    /// e depois «feio pra caralho» — e nas duas vezes o problema era o mesmo: um
    /// preenchimento sólido não pertence a esta casa.
    ///
    /// Tudo o que importa aqui se anuncia com **luz**: o facho da cabine, as
    /// lâmpadas da marquise, a lente do trilho, o anel do foco. O botão principal
    /// passou a ser a mesma coisa — um halo quente que nasce atrás dele e cresce
    /// quando ele é escolhido, com a pílula por cima.
    ///
    /// ⚠️ E o rótulo voltou à **caixa baixa**. Esta casa escreve em minúscula em
    /// toda parte: `assistir`, `sintonizar`, `me avise`. O versalete é da voz dos
    /// **rótulos de seção**, e emprestá-lo pro botão fez ele gritar sem ganhar
    /// clareza — além de esticar a palavra até expulsar a vizinha da fileira.
    val forma = RoundedCornerShape(50)
    Focavel(
        aoEscolher = aoEscolher,
        modifier = modifier,
        forma = forma,
        escolhivel = habilitado,
        anel = false,
    ) { focado ->
        val aceso = focado || principal
        Box(contentAlignment = Alignment.Center) {
            /// O halo. Ele é desenhado **fora** da pílula, e é o que faz o botão
            /// parecer aceso em vez de pintado.
            if (aceso) {
                Canvas(Modifier.matchParentSize()) {
                    val forca = if (focado) 1f else 0.45f
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.00f to Cores.destaqueQuente.copy(alpha = 0.34f * forca),
                                0.55f to Cores.destaque.copy(alpha = 0.16f * forca),
                                1.00f to androidx.compose.ui.graphics.Color.Transparent,
                            ),
                            center = center,
                            radius = size.width * 0.78f,
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height),
                    )
                }
            }
            Box(
                Modifier
                    .padding(6.dp)
                    .background(
                        when {
                            focado -> Cores.destaqueQuente
                            principal -> Cores.destaque
                            else -> androidx.compose.ui.graphics.Color.Transparent
                        },
                        forma,
                    )
                    .then(
                        if (!aceso) {
                            Modifier.border(2.dp, Cores.destaqueApagado.copy(alpha = 0.6f), forma)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                RotuloDoBotao(
                    rotulo = rotulo,
                    tinta = when {
                        aceso -> Cores.fundoAfundado
                        habilitado -> Cores.destaque
                        else -> Cores.textoApagado
                    },
                )
            }
        }
    }
}
