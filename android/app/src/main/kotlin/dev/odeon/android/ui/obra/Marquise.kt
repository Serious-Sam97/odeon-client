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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
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

/// Quantas fotos **cabem na tela** de uma vez.
///
/// ⚠️ **Não é mais quantas existem.** A primeira versão pendurava três das doze
/// que a rota devolve, e o motivo escrito era bom: doze prendedores em 411dp
/// dariam 34dp por foto, miniatura de miniatura. O que mudou não foi o número —
/// foi o varal ter virado **arrastável**, e as outras nove deixarem de precisar
/// caber ao mesmo tempo.
///
/// Então este número continua mandando no **tamanho** — cada foto mede
/// `(411 − 24) ÷ 3` ≈ 129dp, exatamente o que media antes — e parou de mandar na
/// quantidade. É de propósito que ele não divide exato: a quarta foto fica
/// mordida na borda direita, e é ela que conta que há mais varal do que cabe.
/// Sem esse pedaço cortado, a tela volta a parecer a fileira parada de antes e
/// ninguém descobre o arrasto.
private const val FOTOS_NA_TELA = 3

/// Abaixo disto não vale pendurar nada.
///
/// §24 e §53: um fio com uma foto só prometeria uma navegação que não existe. E
/// arrastar um varal que não sai do lugar é pior que não poder arrastar.
private const val CENAS_MINIMAS = 3

/// O vão entre duas fotos, e a margem nas duas pontas do conteúdo.
private val ESPACO_ENTRE_FOTOS = 12.dp

/// Onde os dois pregos mordem a corda, contado da borda da tela.
///
/// ⚠️ **10dp porque é o raio do canto da marquise.** O letreiro é um
/// `RoundedCornerShape(10.dp)`, então o canto de baixo dele não está em `x = 0`:
/// está a 10dp pra dentro. Prego na borda seria prego pendurado no ar ao lado do
/// letreiro, e a costura que sustenta a fachada inteira se perderia por 10
/// pixels.
private val RECUO_DO_PREGO = 10.dp

/// Meia espessura da corda.
///
/// ⚠️ **1,4dp, e a primeira tentativa foi 1,7 — que na foto virou zíper.** A
/// conta que eu tinha errado: a espessura que se vê **não** é `2 × raio`. A fibra
/// cruza a corda na diagonal, e a largura do traço dela (`strokeWidth`) se soma
/// de lado. Com 1,7 de raio e traço de 2,4dp a corda apareceu com quase 6dp — o
/// triplo do fio antigo, grossa como cabo de aço.
///
/// Aqui a corda inteira dá ~4dp vistos: 2,8 de miolo mais o traço deitado. É
/// mais que os 2dp de antes de propósito — fibra atravessada precisa de largura
/// pra ser vista atravessando — e menos que varal de estender roupa.
private val RAIO_DA_CORDA = 1.4.dp

/// O passo da torção: de quanto em quanto uma fibra cruza a corda.
///
/// ⚠️ **Da ordem da espessura, e é isso que faz a corda parecer cheia.** Passo
/// muito maior que a espessura abre vão entre as fibras e a corda vira escada;
/// muito menor empilha fibra sobre fibra e vira borrão. Encostadas, elas formam
/// superfície — que é o que corda trançada é.
private val PASSO_DA_TORCAO = 3.dp

/// A altura da caixa toda.
///
/// ⚠️ **172dp foi medido na foto** quando o varal era parado, e a conta ainda
/// fecha com o varal andando: a foto desce no máximo até a barriga da corda
/// (6dp do prego + 26dp de flecha + 10dp que a tração afunda = 42dp) e mede
/// ~125dp da cabeça do prendedor à legenda. 42 + 125 = 167dp, com 5dp de folga
/// pro giro. É folga apertada, e é a primeira coisa a conferir na tela — foi
/// justamente uma sobra de 60dp aqui que a primeira versão do varal errou.
private val ALTURA_DO_VARAL = 172.dp

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

/// A corda, resolvida em pixels, num quadro.
///
/// Ela existe como objeto porque **duas partes do desenho precisam da mesma
/// curva**: o `Canvas`, que a pinta, e cada foto, que precisa saber a que altura
/// o fio passa debaixo do próprio prendedor. Recalcular a curva em dois lugares
/// é o jeito garantido de as fotos flutuarem 2dp fora do fio quando alguém mexer
/// num dos dois — e isso é exatamente o defeito que o screenshot pega e o
/// compilador não.
private class Corda(
    val xEsquerdo: Float,
    val xDireito: Float,
    /// A altura dos pregos. Fora deles a corda segue nesta altura até sumir.
    val y: Float,
    /// O ponto de controle da Bézier quadrática entre os dois pregos.
    val controleX: Float,
    val controleY: Float,
) {
    /// A que altura o fio passa em `x`.
    ///
    /// ⚠️ **O `t` é aproximado pelo x, e não resolvido.** Numa Bézier quadrática
    /// o x não anda linear com o t quando o ponto de controle sai do meio — e ele
    /// sai, é justamente isso que a tração faz. Resolver o t exato pede uma
    /// raiz quadrada por foto por quadro. A aproximação erra fração de dp com o
    /// desvio que a gente usa (40dp no máximo, sobre um vão de ~390dp), e essa
    /// fração é menor que a espessura do próprio fio.
    fun alturaEm(x: Float): Float {
        if (x <= xEsquerdo || x >= xDireito) return y
        val t = (x - xEsquerdo) / (xDireito - xEsquerdo)
        val u = 1f - t
        return u * u * y + 2f * u * t * controleY + t * t * y
    }

    /// A inclinação do fio em `x`, em dy/dx.
    ///
    /// É o que faz cada foto **balançar sozinha ao passar**: pendurada por um
    /// ponto só, ela se alinha com a corda onde o prendedor mordeu. Na descida da
    /// barriga o fio tomba pra um lado, no fundo ele está plano, na subida tomba
    /// pro outro. Sem isso as doze atravessariam a tela rígidas, como se
    /// estivessem coladas num trilho — e um trilho é o que a gente não quis.
    fun inclinacaoEm(x: Float): Float {
        if (x <= xEsquerdo || x >= xDireito) return 0f
        val vao = xDireito - xEsquerdo
        val t = (x - xEsquerdo) / vao
        /// A derivada da Bézier quadrática em t, dividida pelo vão pra virar
        /// inclinação de tela e não de parâmetro.
        return (2f * (1f - t) * (controleY - y) + 2f * t * (y - controleY)) / vao
    }
}

/// Desenha a corda trançada, fibra por fibra, com a torção correndo.
///
/// ## ⚠️ A primeira versão era um tracejado, e a foto reprovou
///
/// A corda nasceu com `PathEffect.dashPathEffect` e a fase andando com o arrasto.
/// Custava um `drawPath`, foi medida (19,1% de quadros atrasados contra 21,4% da
/// biblioteca no mesmo emulador — ou seja, de graça), e **andava mesmo**: dá pra
/// ver a fase mudar comparando quadros da gravação.
///
/// Só que ela não lia como corda. `dashPathEffect` corta a linha **ao longo** do
/// caminho, então o que aparece são tracinhos deitados na direção da corda — e
/// isso é linha pontilhada de formulário, não trança. Torção é fibra
/// **atravessada**, e atravessar não é coisa que efeito de traço saiba fazer.
///
/// > «deixe o mais foda possível, não ligue pra custo»
///
/// ## Como ela é feita agora
///
/// O `PathMeasure` anda pela curva de [PASSO_DA_TORCAO] em [PASSO_DA_TORCAO], e
/// em cada parada pega **posição e tangente**. Da tangente sai a normal, e da
/// normal sai a fibra: um risco que cruza a corda de um bordo ao outro, inclinado
/// porque avança meio passo enquanto atravessa. Fibra encostando em fibra, isso é
/// o cordão de barbeiro que toda corda trançada tem.
///
/// ## E a fibra é redonda, não chapada
///
/// Cada risco sai em **três pedaços** — claro, médio, escuro — do bordo de cima
/// pro de baixo. É o que dá volume: corda é cilindro, e cilindro iluminado de
/// cima tem brilho em cima e sombra embaixo. Um risco de cor só daria uma fita
/// listrada.
///
/// ⚠️ O escuro **é** o `Cores.linha` que o comentário antigo daqui proibia por
/// «desaparecer sobre o preto da ficha». A proibição continua certa pra um fio
/// sozinho e está errada aqui: ele não está sobre o preto, está encostado no
/// claro da fibra vizinha. É a diferença entre cor de traço e cor de sombra.
///
/// ## O claro é o mesmo creme do papel das fotos
///
/// [Cores.papel] é a cor das polaroides penduradas. Dar à corda o creme delas é o
/// que faz o varal ser **um objeto** — algodão segurando papel — em vez de um
/// arame cinza de sistema segurando cartões. Foi a mesma lógica que costurou o
/// fio na marquise.
///
/// ## O custo, medido — e a armadilha de medir cedo demais
///
/// São ~130 fibras por quadro e três riscos cada: **~390 `drawLine` por quadro**,
/// contra o `drawPath` único do tracejado. Foi decidido pagar isso.
///
/// ⚠️ **A primeira medição deu 44,4% de quadros atrasados e quase condenou a
/// corda.** Ela foi tirada nos primeiros arrastos **depois de abrir o app** — e
/// ali ainda estão entrando as imagens das cenas e o código ainda não foi
/// compilado de verdade pela máquina virtual. Repetida em regime, no mesmo
/// emulador e na mesma sessão: **15,9%** no varal contra **16,9%** da rolagem da
/// biblioteca. Empatado.
///
/// A mediana continua mais alta que a da biblioteca (44ms contra 31ms), e isso é
/// o preço honesto dos 390 riscos. O que a medição em regime desmente é o susto:
/// as fibras não dobram o custo de nada.
///
/// A lição, que vale pra próxima: **medir quadro logo depois de abrir o app mede
/// o app abrindo**, não o desenho.
private fun DrawScope.torcerACorda(caminho: Path, deslocamento: Float) {
    val medidor = PathMeasure().apply { setPath(caminho, false) }
    val comprimento = medidor.length
    if (comprimento <= 0f) return

    val raio = RAIO_DA_CORDA.toPx()
    val passo = PASSO_DA_TORCAO.toPx()

    /// O miolo, por baixo de tudo.
    ///
    /// As fibras se encostam, mas encostam com antisserrilhado — e entre duas
    /// bordas suavizadas sobra uma costura de fundo preto que, repetida 130
    /// vezes, lê como corda rachada. O miolo tapa isso e não aparece em lugar
    /// nenhum além das costuras.
    drawPath(
        path = caminho,
        color = Cores.papel.copy(alpha = 0.45f),
        style = Stroke(width = raio * 2f, cap = StrokeCap.Round),
    )

    /// A fase: é ela que faz a torção **viajar** com o dedo.
    ///
    /// ⚠️ **O sinal é negativo, e ele estava trocado.** `deslocamento` cresce
    /// quando o conteúdo anda pra **esquerda**, então uma fase que cresce junto
    /// empurra a fibra pra direita — a corda torcendo contra as fotos que ela
    /// segura. Não dá pra ver isso num screenshot parado, e nenhum teste pega:
    /// é conta, e só bate olhando o sentido dos dois movimentos juntos.
    ///
    /// ⚠️ `mod` e não `%`: em Kotlin o `%` de um número negativo devolve negativo,
    /// e aqui o número **é** negativo. Com `%` a primeira fibra cairia fora do
    /// começo da corda e a torção daria um pulo a cada passo. `mod` sempre volta
    /// no intervalo [0, passo).
    var d = (-deslocamento).mod(passo)

    while (d < comprimento) {
        val centro = medidor.getPosition(d)
        val tangente = medidor.getTangent(d)
        /// A normal é a tangente girada em 90°. Com a corda indo pra direita ela
        /// aponta pra **baixo** — e é o que faz o claro sair em cima e a sombra
        /// embaixo sem ninguém precisar decidir de que lado vem a luz.
        val normalX = -tangente.y
        val normalY = tangente.x

        /// O meio passo de avanço enquanto a fibra atravessa. É ele que inclina o
        /// risco: sem avanço a fibra sairia perpendicular, e corda perpendicular
        /// não é trança, é costela.
        val avanco = passo / 2f
        val altoX = centro.x - normalX * raio - tangente.x * avanco
        val altoY = centro.y - normalY * raio - tangente.y * avanco
        val baixoX = centro.x + normalX * raio + tangente.x * avanco
        val baixoY = centro.y + normalY * raio + tangente.y * avanco

        /// Os três pedaços se **sobrepõem** de propósito (0,40 / 0,32–0,72 /
        /// 0,64): encostados exatos deixariam duas linhas de costura no meio da
        /// fibra, que é o mesmo defeito que o miolo tapa nas pontas.
        fun pedaco(de: Float, ate: Float, cor: Color) {
            drawLine(
                color = cor,
                start = Offset(
                    altoX + (baixoX - altoX) * de,
                    altoY + (baixoY - altoY) * de,
                ),
                end = Offset(
                    altoX + (baixoX - altoX) * ate,
                    altoY + (baixoY - altoY) * ate,
                ),
                /// ⚠️ **0,78 do passo, e 0,58 deixou vão.** A fibra cruza a corda
                /// a ~47° (ela anda um passo no comprimento enquanto atravessa
                /// 2×raio), então a pegada dela **ao longo** da corda não é o
                /// traço: é o traço dividido por `sen(43°)` ≈ 0,68. Com 0,58 a
                /// pegada dava 0,85 do passo e sobravam 15% de preto entre uma
                /// fibra e a seguinte — na foto virou um colar de losangos. Com
                /// 0,78 elas se cobrem em ~15% e formam superfície.
                strokeWidth = passo * 0.78f,
                /// ⚠️ **`Butt` e não `Round`, e a foto mandou.** Com ponta
                /// redonda cada um dos três pedaços ganhava um bolo nas duas
                /// extremidades — 390 bolos por quadro, e a corda saiu parecendo
                /// uma corrente de elos, não uma trança. Ponta reta encosta em
                /// ponta reta, e o miolo tapa a costura.
                cap = StrokeCap.Butt,
            )
        }

        /// ## As três luzes saem todas de [Cores.papel]
        ///
        /// ⚠️ **A primeira versão usou `textoApagado` no meio e `linha` na
        /// sombra, e a corda saiu azulada.** Os dois são cinzas de interface
        /// (#8B8D9A e #23232C) e puxam pro frio; encostados no creme do papel
        /// deram aparência de metal escovado — a foto mostrou um zíper pendurado
        /// na marquise.
        ///
        /// Três alfas do **mesmo** creme resolvem: a cor não muda, só a luz que
        /// bate nela. É o que acontece com um cilindro de algodão iluminado de
        /// cima, e de quebra amarra a corda ao papel das fotos que ela segura.
        /// ⚠️ **E os três alfas subiram: 0,85/0,42/0,14 sumia com dois deles.**
        /// Alfa é mistura com o que está **atrás**, e atrás daqui é o preto da
        /// ficha (#0A0A0C) — 0,42 de creme sobre preto dá um cinza que some, e
        /// 0,14 é preto. Na foto sobrava só o brilho de cada fibra, solto: um
        /// colar de losangos claros em vez de uma corda. O erro foi pensar os três
        /// números como «claro, médio, escuro» numa corda iluminada, e não como o
        /// que eles são aqui — três misturas com o fundo.
        pedaco(0f, 0.40f, Cores.papel.copy(alpha = 0.95f))
        pedaco(0.32f, 0.72f, Cores.papel.copy(alpha = 0.66f))
        pedaco(0.64f, 1f, Cores.papel.copy(alpha = 0.34f))

        d += passo
    }
}

/// O varal de cenas, pendurado na marquise — **e ele se arrasta**.
///
/// ## ⚠️ O fio verga, e é o que faz ele ser um fio
///
/// Uma reta horizontal com fotos penduradas é uma barra de miniaturas. O que
/// muda a leitura é a **curva de Bézier**: o fio sai alto nas pontas, cede no
/// meio, e as fotos acompanham a altura da corda onde cada prendedor mordeu.
/// Sem isso o desenho vira uma fileira, e a metáfora não sobrevive.
///
/// ## Os dois pregos, e por que eles existem
///
/// > «gostei dos pregos, vamos com essa»
///
/// A corda **corre** com o dedo, e correndo ela não pode ter ponta fixa: uma
/// corda que rola é uma linha que entra por fora da tela e sai por fora da tela.
/// Só que o desenho inteiro desta ficha se justifica por o fio **nascer nos
/// cantos de baixo do letreiro** — é por isso que não existe margem entre a
/// marquise e o varal. Uma corda solta passando por baixo transformaria a fachada
/// em duas coisas encostadas: um letreiro, e embaixo dele uma tira de fotos que
/// veio de outro lugar.
///
/// Os pregos resolvem os dois: a corda viaja, e continua sendo o letreiro que a
/// segura — como varal de verdade, que corre apoiado em dois pontos.
///
/// ## ⚠️ E uma corda lisa que anda é idêntica a uma corda lisa parada
///
/// Isto foi visto **antes** de escrever, e teria custado uma rodada inteira: o
/// fio era um traço liso de 2dp. Deslize um traço liso 300px e cada pixel dele
/// continua da cor que era — não há como enxergar que ele andou. O movimento
/// apareceria só nas fotos, e a corda pareceria a barra parada de sempre.
///
/// Por isso ela ganhou **torção**, desenhada fibra por fibra — ver
/// [torcerACorda], que também guarda por que a primeira tentativa (um tracejado)
/// andava e mesmo assim não passava por corda.
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
    if (cenas.size < CENAS_MINIMAS) return

    val escala = escalaDeAnimacao()
    val arrasto = rememberScrollState()

    /// A tração na corda: quanto o dedo está puxando, com sinal, entre -1 e 1.
    ///
    /// ⚠️ **Ela é filtrada, e não lida crua.** O delta de scroll por quadro pula
    /// muito — um quadro perdido vira um pico, e o pico faria a barriga da corda
    /// dar um tranco. A média corrida de 0,6/0,4 é o mínimo que tira o tranco sem
    /// atrasar a resposta a ponto de a corda parecer molenga.
    val tracao = remember { Animatable(0f) }

    LaunchedEffect(arrasto, escala) {
        /// Com animação desligada no sistema a corda não ganha barriga variável
        /// nem volta de mola. O **arrasto continua**, e isto é §15 lido direito:
        /// o que a preferência desliga é movimento que a tela faz sozinha, não a
        /// tela seguir o dedo de quem está arrastando agora.
        if (escala <= 0f) return@LaunchedEffect
        var anterior = arrasto.value
        snapshotFlow { arrasto.value }.collect { atual ->
            /// 40px por quadro é o que um arrasto firme faz; acima disso já é
            /// arremesso, e a corda satura em vez de virar borracha.
            val alvo = ((atual - anterior) / 40f).coerceIn(-1f, 1f)
            anterior = atual
            tracao.snapTo(tracao.value * 0.6f + alvo * 0.4f)
        }
    }

    /// Soltou o dedo, a corda volta.
    ///
    /// ⚠️ `snapTo` no laço de cima e `animateTo` aqui não brigam porque só um dos
    /// dois roda por vez: enquanto há arrasto o `collect` manda, e quando ele
    /// para de emitir é que esta mola entra. Foi a razão de separar em dois
    /// efeitos em vez de tentar resolver os dois casos dentro do `collect`.
    LaunchedEffect(arrasto.isScrollInProgress) {
        if (escala <= 0f || arrasto.isScrollInProgress) return@LaunchedEffect
        tracao.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 90f))
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(ALTURA_DO_VARAL)) {
        /// A largura da foto sai de quantas **cabem**, não de quantas existem —
        /// ver [FOTOS_NA_TELA]. Os 24dp são as duas margens do conteúdo.
        val larguraDaFoto = (maxWidth - 24.dp) / FOTOS_NA_TELA

        /// A corda deste quadro, montada uma vez e usada pelo `Canvas` e pelas
        /// doze fotos.
        val corda: Density.() -> Corda = {
            val recuo = RECUO_DO_PREGO.toPx()
            val larguraPx = maxWidth.toPx()
            val puxao = tracao.value
            Corda(
                xEsquerdo = recuo,
                xDireito = larguraPx - recuo,
                y = 6.dp.toPx(),
                /// A barriga escorrega **contra** o arrasto: puxou pra esquerda,
                /// ela fica pra trás. É o que uma corda com peso faz, e é o que
                /// separa «a corda respondeu» de «a corda é um desenho parado
                /// atrás de fotos que andam».
                controleX = larguraPx / 2f - puxao * 40.dp.toPx(),
                /// 58dp de controle é a flecha de 26dp que já existia — numa
                /// Bézier quadrática o meio da curva fica a um quarto de
                /// `(y0 + 2·controle + y1)`, e (6 + 2·58 + 6)/4 dá 32dp, ou seja
                /// 26dp abaixo do prego. A tração afunda mais 10dp: puxar corda
                /// esticada é ver ela ceder.
                controleY = 58.dp.toPx() + kotlin.math.abs(puxao) * 10.dp.toPx(),
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            val fio = corda()
            val caminho = Path().apply {
                /// O pedaço de fora do prego esquerdo: é ele que promete que há
                /// mais varal do que cabe na tela.
                moveTo(0f, fio.y)
                lineTo(fio.xEsquerdo, fio.y)
                quadraticTo(fio.controleX, fio.controleY, fio.xDireito, fio.y)
                lineTo(size.width, fio.y)
            }

            torcerACorda(caminho, arrasto.value.toFloat())

            /// Os dois pregos, por cima da corda — quem segura é quem fica na
            /// frente.
            listOf(fio.xEsquerdo, fio.xDireito).forEach { x ->
                drawCircle(Cores.destaque, radius = 3.5.dp.toPx(), center = Offset(x, fio.y))
                drawCircle(
                    Cores.destaqueApagado,
                    radius = 1.6.dp.toPx(),
                    center = Offset(x, fio.y),
                )
            }
        }

        Row(
            /// ⚠️ `fillMaxSize` e não `fillMaxWidth`: o `horizontalScroll` recorta
            /// nos limites da própria `Row`, e sem a altura cheia ele cortaria as
            /// fotos que descem até a barriga da corda — `translationY` é desenho,
            /// não layout, e o recorte não sabe disso.
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(arrasto)
                /// Depois do `horizontalScroll`, então esta margem **anda junto**
                /// com o conteúdo em vez de ficar parada na moldura.
                .padding(horizontal = ESPACO_ENTRE_FOTOS),
            horizontalArrangement = Arrangement.spacedBy(ESPACO_ENTRE_FOTOS),
        ) {
            /// ⚠️ **`Row` com `horizontalScroll`, e não `LazyRow`.** Doze itens
            /// não pedem reciclagem, e a corda precisa do deslocamento em pixels
            /// a cada quadro: no `LazyRow` esse número chega partido em índice
            /// mais offset do primeiro visível, e remontá-lo dá um valor que
            /// pula de item pra item — a curva sairia tremida.
            cenas.forEachIndexed { indice, cena ->
                /// A inclinação de repouso de cada foto. Fixa por posição, e não
                /// sorteada: um ângulo aleatório mudaria a cada recomposição, e a
                /// foto ficaria tremendo enquanto alguém rola a tela.
                val angulo = when (indice % 3) {
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
                        indice + 1,
                    ),
                    angulo = angulo,
                    /// ⚠️ Só as que **começam na tela** têm queda escalonada. Dar
                    /// atraso às doze faria a décima segunda cair 990ms depois de
                    /// aberta a ficha — fora da tela, onde ninguém vê, e ainda
                    /// chegando pendurada torta se alguém arrastasse até lá antes.
                    atraso = if (indice < FOTOS_NA_TELA) indice * 90L else 0L,
                    escala = escala,
                    largura = larguraDaFoto,
                    /// Onde esta foto está no conteúdo, do canto esquerdo dele. É
                    /// com isto que ela descobre, a cada quadro, sob que pedaço da
                    /// corda o prendedor dela está passando.
                    posicaoNoVaral = { (larguraDaFoto + ESPACO_ENTRE_FOTOS).toPx() * indice },
                    deslocamento = { arrasto.value.toFloat() },
                    corda = corda,
                    aoTocar = { aoTocarNaCena(cena) },
                )
            }
        }
    }
}

/// Uma foto de cena, presa no fio por um prendedor.
///
/// ## ⚠️ Ela lê o arrasto **dentro do `graphicsLayer`**, e isso não é estilo
///
/// A altura e o tombo desta foto mudam a cada quadro em que o dedo anda. Ler o
/// deslocamento no corpo do `@Composable` marcaria as doze fotos como sujas a
/// cada quadro de arrasto — doze recomposições por quadro, num aparelho, pra
/// mudar dois números que só o desenho usa. Dentro do bloco do `graphicsLayer` a
/// leitura acontece na fase de **desenho**: nada recompõe, nada remede.
///
/// É por isso que `posicaoNoVaral`, `deslocamento` e `corda` chegam como função e
/// não como valor. Um `Float` pronto no parâmetro já teria sido lido lá fora, e o
/// estrago estaria feito antes de esta função começar.
@Composable
private fun FotoPendurada(
    url: String?,
    legenda: String,
    angulo: Float,
    atraso: Long,
    escala: Float,
    largura: androidx.compose.ui.unit.Dp,
    /// Onde o prendedor desta foto está no conteúdo do varal, em px, contando do
    /// começo da primeira foto.
    posicaoNoVaral: Density.() -> Float,
    /// Quanto o varal já andou, em px.
    deslocamento: () -> Float,
    /// A corda deste quadro — a mesma que o `Canvas` pinta.
    corda: Density.() -> Corda,
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
        modifier = Modifier
            .width(largura)
            .graphicsLayer {
                /// A rotação nasce **no prendedor**, e não no centro da foto: é
                /// por ali que ela está presa, e girar pelo meio faria a foto
                /// pivotar no ar sem tocar no fio.
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.44f, 0f)

                /// Onde o prendedor desta foto está **na tela** agora. O 0,44 é o
                /// mesmo do `transformOrigin`: é o eixo em que ela está presa, e
                /// perguntar a altura da corda em qualquer outro ponto penduraria
                /// a foto num lugar onde o prendedor não está.
                val fio = corda()
                val x = ESPACO_ENTRE_FOTOS.toPx() + posicaoNoVaral() +
                    largura.toPx() * 0.44f - deslocamento()

                /// Os 4dp pra cima são o prendedor **mordendo** o fio em vez de
                /// encostar nele: a boca do prendedor passa por cima da corda,
                /// que é como um prendedor segura qualquer coisa.
                translationY = fio.alturaEm(x) - 4.dp.toPx() - queda.value * 70.dp.toPx()

                rotationZ = angulo +
                    balanco.value * 9f +
                    /// O tombo da corda onde ela está presa. Na descida da
                    /// barriga o fio pende pra um lado, no fundo está plano, na
                    /// subida pende pro outro — e a foto acompanha, porque está
                    /// presa por um ponto só. É o que faz as doze balançarem ao
                    /// atravessar a tela em vez de correrem num trilho.
                    ///
                    /// 26 converte dy/dx em grau na faixa que a corda usa: a
                    /// inclinação máxima aqui é ~0,27, e 0,27 × 26 ≈ 7°.
                    fio.inclinacaoEm(x) * 26f
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
