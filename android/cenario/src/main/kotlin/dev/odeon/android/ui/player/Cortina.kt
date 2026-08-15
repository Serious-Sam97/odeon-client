package dev.odeon.android.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import dev.odeon.android.ui.Serifada
import dev.odeon.android.ui.Texto
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.escalaDeAnimacao
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/// A abertura da sessão: as lâmpadas piscam, a cortina aparece, e ela abre.
///
/// ## ⚠️ Ela **veste** uma espera que já existe — não soma uma nova
///
/// É a regra que o dono impôs junto do pedido: «coisa de segundos, não podemos
/// ser tão lerdos pra abrir o filme em si». E ela é o que separa isto de um
/// enfeite caro.
///
/// Abrir um filme neste app já custa tempo **hoje**: pedir o plano de
/// reprodução, montar a URL, e o Media3 encher o buffer até o primeiro quadro.
/// Esse tempo existe, e o que ele mostra hoje é tela preta. A cortina mora
/// dentro dele.
///
/// ```
/// plano · URL · buffer  ├──────────────────────────┤
/// lâmpadas · cortina    ├─────────────────┤
/// ```
///
/// ## ⚠️ A regra mudou depois de ver rodando
///
/// A primeira versão cortava a coreografia assim que o player chegasse a
/// `READY`, pra nunca atrasar o filme. Num Direct Play local isso acontece
/// **logo depois da piscada** — e o resultado foi o dono dizer, duas vezes, que
/// as luzes não existiam. Elas existiam; duravam um piscar de olhos.
///
/// Ele então afrouxou a restrição: «não precisa ser voando o início, pode levar
/// uns 2 segundos a mais». Agora a coreografia tem tempo **próprio**:
///
/// | | |
/// |---|---|
/// | **a piscada** | 900ms de lâmpada de arco firmando — quedas e picos, não uma rampa |
/// | **o pano no ar** | até [ABERTURA_MS], com o letreiro legível |
/// | **abre** | 700ms, curva de pano pesado |
/// | **o filme demorou** | teto de [TETO_MS], e passado ele a cortina abre mesmo assim |
/// | **qualquer toque** | pula tudo |
///
/// A penúltima regra é contraintuitiva e continua valendo: passado o teto, a
/// cortina **abre sobre um filme que ainda não começou**. Um pano parado finge
/// que está acontecendo alguma coisa; um buffer visível diz a verdade.
///
/// A última é precedente do próprio app: a vinheta do menu de disco já é «2,5s,
/// qualquer tecla pula».
///
/// ## ⚠️ Sem animação no sistema, sem cortina
///
/// O §15 é explícito: «alma não pode custar enjoo». Quem desligou animação nas
/// opções do desenvolvedor — ou ligou a redução de movimento — não ganha
/// coreografia nenhuma: [escalaDeAnimacao] devolve 0 e a cortina termina no
/// primeiro quadro. O filme abre direto, que é o que essa pessoa pediu ao
/// sistema.
@Composable
fun CortinaDeAbertura(
    titulo: String,
    /// O primeiro quadro já está pronto pra aparecer? Vem do player:
    /// `STATE_READY`.
    ///
    /// ⚠️ **Ele não encurta mais a coreografia** — ver o cabeçalho. Continua no
    /// contrato porque é o que o teto observa: se o filme já está pronto, a
    /// espera acabou de verdade quando o pano sair; se não está, o que aparece
    /// depois do teto é o buffer, e isso é intencional.
    @Suppress("UNUSED_PARAMETER") pronto: Boolean,
    aoTerminar: () -> Unit,
) {
    val escala = escalaDeAnimacao()
    val terminar by rememberUpdatedState(aoTerminar)

    /// 0 = apagadas · 1 = acesas. As lâmpadas revelam a cortina.
    val luz = remember { Animatable(0f) }

    /// 0 = fechada · 1 = aberta de todo. É a fração que cada metade do pano
    /// anda pra fora da tela.
    val abertura = remember { Animatable(0f) }

    var pulou by remember { mutableStateOf(false) }

    /// Um `Animatable` por lâmpada seriam seis animações pra uma piscada de
    /// 320ms. Uma fase fixa por lâmpada, lida da mesma [luz], dá o fora-de-fase
    /// de graça — é a mesma economia que a poeira do facho faz.
    val fases = remember {
        listOf(
            0.00f, 0.26f, 0.09f, 0.34f, 0.15f, 0.29f,
            0.04f, 0.21f, 0.31f, 0.11f, 0.24f, 0.06f,
        )
    }

    LaunchedEffect(pulou) {
        if (pulou) return@LaunchedEffect

        /// Sem animação no sistema: nada de coreografia.
        if (escala <= 0f) {
            terminar()
            return@LaunchedEffect
        }

        /// **Tempo 1 — as luzes piscam.**
        ///
        /// ⚠️ Não é uma rampa: é uma **lâmpada de arco firmando**, com quedas e
        /// picos acima do normal antes de assentar. A curva é a mesma família da
        /// piscada do `BarraDoFacho`, e o motivo de ela existir aqui é o que o
        /// dono cobrou duas vezes: uma rampa lisa de 320ms não se lê como «as
        /// luzes piscam» — se lê como um fade, que ninguém nota.
        launch {
            luz.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 900
                    0.00f at 0
                    0.62f at 90
                    0.10f at 150
                    0.88f at 230
                    0.24f at 300
                    1.00f at 400
                    0.55f at 470
                    0.95f at 560
                    0.78f at 640
                    1.00f at 760
                },
            )
        }

        /// **Tempo 2 — o pano fica no ar.**
        ///
        /// ⚠️ **A cortina não corta mais quando o filme fica pronto**, e é
        /// mudança de regra pedida: «não precisa ser voando o início, pode levar
        /// uns 2 segundos a mais». A versão anterior abria assim que o player
        /// chegava a `READY` — e num Direct Play local isso acontece logo depois
        /// da piscada, então as luzes apareciam por um piscar de olhos e o dono
        /// concluiu, com razão, que elas não existiam.
        ///
        /// Agora a coreografia tem tempo **próprio**: ela cumpre os [ABERTURA_MS]
        /// inteiros, com filme pronto ou não. O que o estado do player ainda
        /// decide é o **teto** — ver abaixo.
        delay((ABERTURA_MS * escala).toLong())

        /// **Tempo 3 — abre.** A curva é a de um pano pesado: começa devagar,
        /// pega velocidade e para sem quicar. `overshoot` aqui seria tecido
        /// elástico, e cortina de cinema não é elástica.
        abertura.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 700,
                easing = CubicBezierEasing(0.32f, 0f, 0.24f, 1f),
            ),
        )
        terminar()
    }

    /// O teto, pra quando o filme demora **mais** que a coreografia.
    ///
    /// Passado [TETO_MS] a cortina abre de qualquer jeito, mesmo sobre um filme
    /// que ainda não começou: um pano parado finge que está acontecendo alguma
    /// coisa, e um buffer visível diz a verdade.
    LaunchedEffect(Unit) {
        delay((TETO_MS * escala).toLong())
        if (!pulou && abertura.value < 1f) {
            abertura.animateTo(1f, tween(400))
            terminar()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            /// Qualquer toque pula — e o `pointerInput` fica **fora** de
            /// qualquer condição, senão a metade final da animação (quando o
            /// pano já saiu da tela) deixaria de ser tocável.
            .pointerInput(Unit) {
                detectTapGestures {
                    pulou = true
                    terminar()
                }
            },
    ) {
        /// ## O pano
        ///
        /// Duas metades que saem pelos lados. O degradê horizontal é o que faz
        /// pano parecer pano: a luz bate no meio da prega e some nas dobras.
        ///
        /// A sombra interna na aresta de dentro é a dobra central, e é ela que
        /// separa as duas metades quando estão fechadas — sem ela, cortina
        /// fechada é um retângulo vermelho.
        val fora = abertura.value
        Row(Modifier.fillMaxSize()) {
            /// ⚠️ **`weight(1f)` e não `fillMaxWidth(0.5f)`** — e foi a foto que
            /// pegou. Num `Row`, `fillMaxWidth` mede sobre o espaço **que
            /// sobrou**, não sobre o pai: a primeira metade comia 50%, e a
            /// segunda pegava 50% dos 50% restantes. O pano ficava com 75% da
            /// tela e a direita já nascia aberta.
            MetadeDoPano(Modifier.weight(1f), esquerda = true, deslocamento = fora, luz = luz.value)
            MetadeDoPano(Modifier.weight(1f), esquerda = false, deslocamento = fora, luz = luz.value)
        }

        /// ## ⚠️ O escuro que a luz **tira** — e sem ele não há revelação
        ///
        /// A primeira versão desenhava o pano em cor cheia desde o quadro zero e
        /// deixava as lâmpadas só somarem um brilho por cima. A foto mostrou o
        /// que isso é: uma cortina que já estava acesa, com lâmpadas piscando à
        /// toa em cima dela. O pedido era o contrário — «as luzes piscam
        /// **revelando** as cortinas fechadas».
        ///
        /// Este véu é o breu da sala antes de a luz bater no pano. Ele começa
        /// quase opaco e sai junto com a piscada, então o que o olho vê é o pano
        /// **aparecendo do escuro**, e não um brilho somado a um vermelho que já
        /// estava lá.
        ///
        /// Não vai a 1,0: um breu absoluto esconderia até a silhueta, e aí a
        /// primeira lâmpada acenderia sobre o nada. 0,94 deixa a dobra central
        /// insinuada — você sabe que há um pano ali antes de vê-lo.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (1f - luz.value) * 0.94f)),
        )

        /// ⚠️ **As lâmpadas vêm depois do pano na ordem de desenho.**
        ///
        /// Elas estavam antes, e a foto mostrou o resultado: cortina impecável,
        /// zero lâmpada. Num `Box` do Compose quem é declarado depois fica por
        /// cima, e o pano ocupa a tela inteira — as lâmpadas estavam sendo
        /// pintadas e cobertas no mesmo quadro.
        ///
        /// A ordem certa também é a física: a marquise fica **na frente** do
        /// pano, não atrás dele.
        /// As lâmpadas da marquise, no alto. Elas são as mesmas do herói da
        /// biblioteca — a marquise é a assinatura da **chegada**, e chegar num
        /// filme é a chegada mais literal que este app tem.
        /// ## ⚠️ Elas eram invisíveis, e o dono disse que eu tinha esquecido
        ///
        /// Estavam lá: seis pontos de **6dp** ocupando **42%** da largura, no
        /// alto. A foto provava que existiam e a experiência dizia que não —
        /// que dá no mesmo. Uma marquise que não se nota não é marquise.
        ///
        /// | | antes | agora |
        /// |---|---|---|
        /// | quantas | 6 | **12** |
        /// | tamanho | 6dp | **10dp** |
        /// | largura ocupada | 42% | **a tela inteira** |
        /// | halo | nenhum | um radial de ~3× o bulbo |
        ///
        /// ⚠️ **Doze, e não dezesseis** — a primeira tentativa pôs 16 e a foto
        /// mostrou a última lâmpada cortada na borda. A conta: 16 caixas de 33dp
        /// dão 528dp, e a tela em pé tem 411. Com 12 caixas de 28dp são 336, e
        /// sobram 35 pro `SpaceBetween` distribuir.
        ///
        /// O halo é o que faz a diferença entre um ponto amarelo e uma lâmpada:
        /// bulbo aceso derrama luz no que está em volta, e sem o derrame o olho
        /// lê adesivo.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 30.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            fases.forEach { fase ->
                /// A fase desloca a rampa: com `luz` em 0,3 e fase 0,26, esta
                /// lâmpada está em 0,05 — acendendo depois da vizinha. No fim
                /// todas chegam a 1 e a fileira fica firme.
                val brilho = ((luz.value - fase) / (1f - fase).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                Box(
                    Modifier.size(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    /// O derrame. Desenhado num `Canvas` porque um `background`
                    /// com degradê radial num `Box` de 33dp arredondaria nas
                    /// bordas e viraria um disco, não um halo.
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Cores.destaqueQuente.copy(alpha = 0.55f * brilho),
                                    Cores.destaqueQuente.copy(alpha = 0.14f * brilho),
                                    Color.Transparent,
                                ),
                                center = center,
                                radius = size.minDimension / 2f,
                            ),
                            radius = size.minDimension / 2f,
                        )
                    }
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                /// O miolo é mais branco que a borda: filamento
                                /// quente no meio, vidro ambarado em volta.
                                Brush.radialGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.30f + 0.70f * brilho),
                                        Cores.destaqueQuente.copy(alpha = 0.28f + 0.72f * brilho),
                                    ),
                                ),
                            ),
                    )
                }
            }
        }

        /// O letreiro, no pé do pano.
        ///
        /// Ele sai **junto com a cortina** e não antes: o nome do filme é o que
        /// está escrito no pano, e o pano leva o que está escrito nele embora.
        if (fora < 0.9f) {
            Texto(
                text = titulo.uppercase(),
                /// ⚠️ Era `MaterialTheme.typography.headlineSmall.copy(...)`, e
                /// do slot ela herdava **três** coisas: a `Serifada`, o peso, e
                /// a entrelinha de 31sp. Corpo e espaçamento já vinham
                /// sobrescritos aqui.
                ///
                /// Os três estão escritos abaixo com os valores que estavam
                /// valendo, tirados do `TipografiaOdeon` do `:app`. Não há
                /// `MaterialTheme` neste módulo — ver `Texto.kt`.
                ///
                /// E ele **não** vira parâmetro, ao contrário dos do `Palco`:
                /// isto é o nome do filme **impresso no pano**, não interface em
                /// volta de um objeto. Uma cortina de cinema tem o letreiro do
                /// tamanho da cortina, e numa TV a cortina é a tela inteira — o
                /// que muda é o pano, e o letreiro vai junto.
                style = TextStyle(
                    fontFamily = Serifada,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    lineHeight = 31.sp,
                    letterSpacing = 0.14.em,
                ),
                color = Color.White.copy(alpha = (luz.value * (1f - fora / 0.9f)).coerceIn(0f, 1f) * 0.92f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.8f),
            )
        }
    }
}

/// Uma metade do pano.
@Composable
private fun MetadeDoPano(modifier: Modifier, esquerda: Boolean, deslocamento: Float, luz: Float) {
    val cores = listOf(
        Cores.cortinaFunda,
        Cores.cortina,
        Cores.cortinaFunda,
    )
    Box(
        modifier
            .fillMaxHeight()
            /// O pano sai **pra fora da própria metade**, então o deslocamento é
            /// em fração da largura dela — e não da tela.
            .androidxOffsetFraction(if (esquerda) -deslocamento else deslocamento)
            .background(
                Brush.horizontalGradient(
                    colors = if (esquerda) cores else cores.reversed(),
                ),
            ),
    ) {
        /// A luz das lâmpadas caindo no pano, mais forte em cima.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Cores.destaqueQuente.copy(alpha = 0.16f * luz),
                    0.45f to Color.Transparent,
                ),
            ),
        )
        /// A dobra central — a sombra na aresta onde as duas metades se
        /// encontram. Sem ela o pano fechado é um retângulo.
        Box(
            Modifier
                .align(if (esquerda) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.14f)
                .background(
                    Brush.horizontalGradient(
                        if (esquerda) {
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                        } else {
                            listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                        },
                    ),
                ),
        )
    }
}

/// Empurra o conteúdo por uma fração da **própria largura**.
///
/// `Modifier.offset` pede dp, e aqui o deslocamento é relativo: cada metade sai
/// exatamente a largura dela, seja num celular de 411dp ou num tablet. Fazer a
/// conta com a largura da tela exigiria medi-la e passá-la pra baixo; o `layout`
/// já a tem na mão.
private fun Modifier.androidxOffsetFraction(fracao: Float): Modifier = layout { medivel, restricoes ->
    val posto = medivel.measure(restricoes)
    layout(posto.width, posto.height) {
        posto.place(x = (posto.width * fracao).toInt(), y = 0)
    }
}

/// Quanto tempo o pano fica no ar antes de começar a abrir.
///
/// ## O número mudou de dono
///
/// Ele era **520ms**, e a coreografia inteira 950 — desenhada pra caber dentro
/// da espera que o player já tinha. O dono viu rodando e afrouxou a regra: «não
/// precisa ser voando o início, pode levar uns 2 segundos a mais».
///
/// Com 1.500 aqui mais os 700 da abertura, a sessão leva **2,2s** — tempo pra a
/// piscada de 900ms acontecer inteira e ainda sobrar meio segundo de pano parado
/// com o letreiro, que é o instante em que se lê o nome do filme.
private const val ABERTURA_MS = 1_500L

/// O teto, e agora ele serve **só** ao filme lento.
///
/// Antes ele era o teto do disfarce inteiro, porque a cortina cortava assim que o
/// filme ficasse pronto. Agora a coreografia tem tempo próprio, e este número só
/// responde a uma pergunta: quanto tempo esperar por um filme que não chega.
///
/// 4s é o dobro da coreografia. Além disso, o que a pessoa quer ver é o que está
/// travando — e isso é o buffer, não o pano.
private const val TETO_MS = 4_000L
