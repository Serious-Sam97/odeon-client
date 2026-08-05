package dev.odeon.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/// A altura da **fileira** — ícone, rótulo e respiro.
///
/// ## Ela é constante de propósito, e é um contrato
///
/// A barra passou a **flutuar por cima do conteúdo** (ver `BarraDoFacho`), e
/// quem rola atrás dela precisa saber onde parar de desenhar. Medir a fileira em
/// tempo de layout e devolver o número pra cima daria o mesmo valor com três
/// peças a mais; uma constante que as duas pontas leem é o contrato mais curto
/// que resolve.
///
/// ## 54dp, e ela encolheu **duas vezes** em 05/08/2026
///
/// Eram 72: 12 de respiro, 24 de ícone, 5 de vão, ~15 de rótulo e **16 embaixo**.
/// A queixa do dono foi «o menu de baixo está com uma faixa preta muito grande» —
/// e medindo, a barra ocupava 72dp de fileira **mais** os ~25dp do inset do
/// gesto: **97dp**, contra os 80 que o Material reserva pra barra inteira, insets
/// incluídos.
///
/// | | fileira | com o inset |
/// |---|---|---|
/// | antes | 72dp | 97dp |
/// | primeiro corte | 64dp | 89dp |
/// | **agora** | **54dp** | **79dp** |
///
/// Ficou `6 + 22 + 3 + ~17 + 5 = 53`, e as três folgas que sobravam eram: os 16dp
/// de baixo, que existiam pra afastar o rótulo da borda da tela — trabalho que é
/// do inset do sistema, logo abaixo —, o respiro de cima, e o ícone de 24dp, que
/// é o tamanho de fábrica do `Icon` e não uma escolha desta barra.
///
/// **79dp é o piso desta forma.** Abaixo disso o rótulo teria de sair, e aí a
/// barra deixa de dizer o que cada aba é — outra conversa, não um ajuste de
/// número.
///
/// ⚠️ **Mas o que a foto mostrava não era só altura.** Ver o cabeçalho do
/// `BarraDoFacho`: o fundo da barra parava na fileira e o inset ficava preto
/// chapado, sem o degradê nem a luz — uma tarja entre a barra acesa e a borda do
/// aparelho. Encolher tira dp; o degradê descer até a borda tira a tarja.
val ALTURA_DA_FILEIRA = 54.dp

/// A faixa **só de luz**, acima da fileira.
///
/// Nenhum conteúdo mora aqui: é o espaço que o cone precisa pra terminar por
/// conta própria em vez de ser cortado. Ver o cabeçalho do `BarraDoFacho`.
///
/// ## O número sai de uma conta, e a primeira tentativa errou
///
/// O cone tem raio de `2,6 × ALTURA_DA_FILEIRA` e nasce colado na aresta de
/// baixo da fileira. Pra ele **fechar dentro da caixa**, sobra `raio − fileira`.
/// A primeira versão pôs 68dp «de olho», e o screenshot mostrou a luz batendo no
/// teto outra vez: uma aresta mais fraca que a antiga, mas reta do mesmo jeito.
///
/// ⚠️ **Este número é derivado, e por isso ele encolheu junto** — duas vezes, em
/// 05/08/2026. Com a fileira em 54dp o raio virou `54 × 2,6 = 140`, e sobra
/// `140 − 54 = 86`. Ficaram **89**, a mesma folga de 3dp que os 118 tinham sobre
/// os 115 da conta original.
///
/// Mexer na fileira sem mexer aqui é como a aresta reta volta — e ela é um
/// defeito que uma rodada anterior já consertou uma vez, com foto.
///
/// ⚠️ **Ela não come toque.** A faixa não tem `pointerInput` nenhum, e no Compose
/// quem não pede evento não recebe: o dedo atravessa pro cartaz que está atrás.
/// Só a fileira, lá embaixo, é clicável.
val ALTURA_DA_LUZ = 89.dp

/// Um destino da barra, do jeito que o facho precisa saber dele.
data class DestinoDoFacho(
    val rotulo: String,
    val icone: Painter,
    val selecionado: Boolean,
    val aoTocar: () -> Unit,
)

/// A barra inferior como **facho de projetor** — e não como fileira de abas.
///
/// ## Por que a `NavigationBar` do Material saiu
///
/// Ela não deixa desenhar **atrás** dos itens. O facho é uma luz que nasce na
/// aresta de baixo e abre sobre o item escolhido, atravessando a barra inteira —
/// e o indicador do Material é uma cápsula presa a um item, não uma luz que
/// varre a barra.
///
/// Junto com ela saiu um defeito: a cápsula era pintada com `secondaryContainer`,
/// que **nunca foi definido** no `EsquemaEscuro`. Ela caía no lilás de fábrica do
/// Material 3 — `#4A4458`, uma cor que não existe na paleta do Odeon. O menu
/// inferior era a única peça do app pintada por outra pessoa.
///
/// ## O que a luz faz, e por que cada pedaço existe
///
/// | | o que é |
/// |---|---|
/// | **a lente** | o ponto quente colado na aresta de baixo. É de onde a luz sai — sem ele o facho é um degradê, não um facho |
/// | **o cone** | o radial que abre pra cima a partir da lente |
/// | **a poeira** | os pontos suspensos dentro do cone. É o que faz a luz ter **ar** dentro |
///
/// A poeira não é elemento: são círculos desenhados no mesmo `Canvas`, com o
/// alfa caindo conforme a distância ao eixo do facho. Nenhum nó entra na árvore.
///
/// ## A piscada
///
/// Trocar de destino **acende a lâmpada de novo**: o facho pisca como um
/// projetor firmando o arco antes de estabilizar. Os tempos abaixo não são
/// aleatórios — é a curva de uma lâmpada de arco: apaga quase tudo, dá um pico
/// **acima** do normal, cai, e assenta.
///
/// ## ⚠️ Ela é longa, e eu tinha argumentado o contrário
///
/// A primeira versão durava **300ms**, e o comentário aqui dizia: «piscada longa
/// em barra de navegação deixa de ser cinema e vira defeito de renderização — a
/// pessoa acha que a tela travou». O dono pediu bem mais lenta, e olhando de
/// novo o receio estava exagerado.
///
/// O motivo é que **nada da interface apaga**: a piscada multiplica só a luz —
/// o cone, a poeira e a lente. O ícone e o rótulo do destino escolhido ficam em
/// `destaqueQuente` o tempo todo, e o conteúdo da tela troca na hora. O que
/// demora é a lâmpada firmando, não o app respondendo.
///
/// Se durasse o mesmo tempo **e** apagasse o rótulo, aí o receio valeria.
///
/// ## ⚠️ A aresta reta no topo, e por que ela sumiu
///
/// A barra era um **retângulo opaco** com o facho desenhado dentro dele. Duas
/// coisas paravam na mesma linha: o escuro da barra, que começava de repente, e
/// o cone, que era recortado pela borda do `Canvas`.
///
/// E o corte era grande: o radial tem raio de **2,6× a altura**, ou seja mais da
/// metade da luz ia pro lixo. O resultado era uma faixa clara com um risco reto
/// em cima — «o limite de cima deixa um pouco feio», nas palavras do dono.
///
/// Agora são três mudanças que só fazem sentido juntas:
///
/// | | |
/// |---|---|
/// | a barra ganhou `ALTURA_DA_LUZ` **só de luz** | é o espaço pro cone acabar sozinho |
/// | o fundo virou **degradê**, transparente em cima | sem aresta, porque não há borda: o escuro entra |
/// | ela **flutua** sobre o conteúdo | e por isso a luz sobe pelos cartazes, que foi o que o dono aprovou |
///
/// O topo visível passa a ser **a curva do próprio radial** — a cúpula que a luz
/// faz. Os itens não se moveram um pixel: quem cresceu foi a área de desenho.
///
/// ## A curva de uma lenta não é a de uma curta esticada
///
/// Esticar os seis quadros-chave de 300ms para 1200ms daria um pulso lento e
/// regular — que lê como respiração, não como acendimento. Lâmpada de arco
/// **oscila**, e a oscilação **decai**: cada repique chega mais perto de 1 que o
/// anterior, até assentar. São nove quadros abaixo, com a amplitude caindo de
/// 0,35 para 0,03.
///
/// E ela some sozinha pra quem desligou animação: `Animatable` e
/// `animateFloatAsState` leem o `MotionDurationScale`, que no Android vem do
/// `ANIMATOR_DURATION_SCALE`. Com a preferência em zero o facho salta pro lugar
/// aceso, sem pulsar.
@Composable
fun BarraDoFacho(
    destinos: List<DestinoDoFacho>,
    modifier: Modifier = Modifier,
) {
    if (destinos.isEmpty()) return
    val quantos = destinos.size
    val escolhido = destinos.indexOfFirst { it.selecionado }.coerceAtLeast(0)

    /// A panorâmica: o facho **desliza** até o destino novo em vez de saltar.
    /// A curva é a mesma da chegada das caixas — sai rápido e assenta.
    val posicao by animateFloatAsState(
        targetValue = escolhido.toFloat(),
        animationSpec = tween(380, easing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)),
        label = "panorâmica do facho",
    )

    val brilho = remember { Animatable(1f) }
    LaunchedEffect(escolhido) {
        brilho.snapTo(0.12f)
        brilho.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = 1200
                /// A lâmpada firmando o arco. Cada linha é um repique, e a
                /// distância até 1 cai a cada um: 0,92 · 0,35 · 0,78 · 0,15 ·
                /// 0,60 · 0,08 · 0,32 · 0,03. É o decaimento que faz o olho ler
                /// "acendendo" em vez de "piscando".
                ///
                /// O primeiro pico passa de 1 de propósito — é o estouro do
                /// arco, e é ele que faz parecer que a luz **nasceu** em vez de
                /// aparecer.
                0.08f at 0
                1.35f at 90
                0.22f at 200
                1.15f at 320
                0.40f at 450
                1.08f at 600
                0.68f at 780
                1.03f at 950
                0.90f at 1080
                1f at 1200
            },
        )
    }

    /// ## ⚠️ O inset do gesto entra **dentro** da barra — 05/08/2026
    ///
    /// Antes quem aplicava esta margem era o `AppOdeon`, por fora: a barra
    /// inteira subia, e o que ficava entre ela e a borda do aparelho era o fundo
    /// da tela, preto e chapado. O resultado na foto era uma **tarja** de ~25dp
    /// separando a barra acesa da borda — e foi ela, mais que a altura, que o
    /// dono viu.
    ///
    /// Trazendo o inset pra cá, o degradê e o cone passam a ser desenhados até a
    /// borda, e **só a fileira** recua. A barra do gesto do sistema flutua sobre
    /// a luz, que é o que um app de tela cheia faz.
    ///
    /// O `Canvas` cresce junto, e a lente desce com ele até a borda do aparelho —
    /// ver o comentário em cima dele.
    val insetBaixo = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()

    Box(
        modifier
            .fillMaxWidth()
            .height(ALTURA_DA_LUZ + ALTURA_DA_FILEIRA + insetBaixo)
            /// O escuro **entra** em vez de começar.
            ///
            /// Transparente no topo, `Cores.fundo` quando a fileira começa, e
            /// opaco daí pra baixo — a fileira precisa de fundo sólido pra o
            /// rótulo ser legível sobre qualquer cartaz que passe atrás.
            ///
            /// As paradas intermediárias existem contra o mesmo banding que o
            /// cone enfrenta: num fundo quase preto, uma rampa de alfa em duas
            /// paradas mostra o degrau de 8 bits, e o olho lê degrau como borda —
            /// que é justamente o que esta mudança veio tirar.
            /// ⚠️ As paradas são calculadas a partir da **fileira**, e não
            /// repartidas na caixa.
            ///
            /// Com frações fixas, crescer a faixa de luz espalharia o escuro por
            /// ela toda — e o degradê passaria a cobrir cartaz que ninguém pediu
            /// pra cobrir. O escuro tem um trabalho só: dar chão ao rótulo. Ele
            /// começa 46dp antes da fileira e termina nela.
            .background(
                Brush.verticalGradient(
                    colorStops = run {
                        /// O inset entra no **total**, e não nas frações: as duas
                        /// paradas continuam medindo da luz, então o escuro fecha
                        /// onde a fileira começa e segue sólido daí até a borda.
                        val total = ALTURA_DA_LUZ.value + ALTURA_DA_FILEIRA.value + insetBaixo.value
                        val comeco = (ALTURA_DA_LUZ.value - 46f) / total
                        val fim = ALTURA_DA_LUZ.value / total
                        arrayOf(
                            0.00f to Color.Transparent,
                            comeco to Color.Transparent,
                            comeco + (fim - comeco) * 0.45f to Cores.fundo.copy(alpha = 0.42f),
                            comeco + (fim - comeco) * 0.75f to Cores.fundo.copy(alpha = 0.82f),
                            fim to Cores.fundo,
                            1.00f to Cores.fundo,
                        )
                    },
                ),
            ),
    ) {
        /// ⚠️ **O `Canvas` cobre a caixa inteira, inset incluído — e eu tinha
        /// escrito o contrário aqui uma hora antes.**
        ///
        /// A primeira versão prendeu o desenho à altura da luz mais a fileira,
        /// com o argumento de que «a luz nasce na aresta da fileira, não na do
        /// aparelho». A foto desmentiu: o cone terminava numa **linha horizontal
        /// visível** na base da fileira, com a faixa do inset preta embaixo — a
        /// mesma tarja que esta rodada veio tirar, só que 25dp mais curta.
        ///
        /// E o argumento certo já estava escrito três linhas abaixo, desde que
        /// esta barra nasceu: «a lente fica **abaixo** da borda: a luz entra na
        /// barra vinda de fora dela, que é o que uma janela de projeção faz». Com
        /// o `Canvas` inteiro, a lente cai na borda do aparelho e o cone preenche
        /// o inset — não sobra aresta pra ver.
        Canvas(Modifier.matchParentSize()) {
            val larguraDaAba = size.width / quantos
            val eixo = larguraDaAba * (posicao + 0.5f)
            /// A lente fica **abaixo** da borda: a luz entra na barra vinda de
            /// fora dela, que é o que uma janela de projeção faz.
            val base = size.height + 4.dp.toPx()
            /// ⚠️ O raio agora se mede pela **fileira**, e não pela caixa.
            ///
            /// A caixa cresceu com a faixa de luz; se o raio crescesse junto, o
            /// facho abriria proporcionalmente e ficaria com a mesma silhueta de
            /// antes — só que maior. O que o dono pediu é a curva **aparecer**, e
            /// pra isso a luz tem que caber dentro da caixa nova: raio preso à
            /// fileira, altura sobrando pra ela fechar.
            val raioDoCone = ALTURA_DA_FILEIRA.toPx() * 2.6f
            val forca = brilho.value

            /// ⚠️ **Sete paradas e raio grande, contra o banding.**
            ///
            /// A primeira versão tinha três paradas e raio de 1,45×. O
            /// screenshot mostrou exatamente o risco que a proposta previu: uma
            /// **aresta vertical** no lado esquerdo do cone. Não era o desenho —
            /// era a queda de alfa cruzando o degrau de 8 bits num fundo quase
            /// preto, e o olho lê isso como borda.
            ///
            /// Duas medidas juntas: mais paradas fazem a queda ser gradual em
            /// vez de linear por trecho, e o raio maior joga o fim do degradê
            /// **pra fora** da barra — a aresta continua existindo, só que onde
            /// não há pixel pra mostrá-la. A poeira ajuda de brinde: ruído sobre
            /// degradê é o remédio clássico de banding.
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to Cores.destaque.copy(alpha = 0.42f * forca),
                        0.18f to Cores.destaque.copy(alpha = 0.30f * forca),
                        0.34f to Cores.destaque.copy(alpha = 0.19f * forca),
                        0.50f to Cores.destaque.copy(alpha = 0.11f * forca),
                        0.66f to Cores.destaque.copy(alpha = 0.055f * forca),
                        0.82f to Cores.destaque.copy(alpha = 0.02f * forca),
                        1.00f to Color.Transparent,
                    ),
                    center = Offset(eixo, base),
                    radius = raioDoCone,
                ),
            )

            /// A poeira suspensa. O alfa cai com a distância ao eixo **e** com a
            /// altura — pó no ar só brilha onde a luz passa.
            ///
            /// ⚠️ **Cada grão sai do lugar, e o screenshot é que exigiu.** A
            /// primeira versão punha os pontos numa grade de 19×13 exatos, e o
            /// resultado lia como **trama**, não como pó: o olho acha a repetição
            /// antes de achar a luz.
            ///
            /// O deslocamento vem de uma função de espalhamento sobre as
            /// coordenadas — determinística, sem `Random`. Isso importa por dois
            /// motivos: sorteio por quadro faria a poeira **cintilar** (que é
            /// chuvisco de TV, não poeira em suspensão), e sorteio por
            /// composição mudaria o desenho a cada recomposição, tirando o
            /// screenshot de comparação.
            val passoX = 19.dp.toPx()
            val passoY = 13.dp.toPx()
            var linha = 0
            var y = passoY / 2
            while (y < size.height) {
                var coluna = 0
                var x = passoX / 2
                while (x < size.width) {
                    /// Espalhamento barato: dois primos, o resto vira fração.
                    val semente = (coluna * 73856093) xor (linha * 19349663)
                    val rx = ((semente shr 3) and 0xFF) / 255f - 0.5f
                    val ry = ((semente shr 11) and 0xFF) / 255f - 0.5f
                    val px = x + rx * passoX * 0.85f
                    val py = y + ry * passoY * 0.85f

                    val dist = abs(px - eixo) / (size.width / quantos)
                    /// A poeira também mede pela fileira: com a caixa inteira, os
                    /// grãos de cima ficariam com alfa alto **fora** do cone, e
                    /// pó brilhando onde não há luz é sujeira na lente, não
                    /// poeira em suspensão.
                    val altura = (1f - (py - (size.height - raioDoCone)) / raioDoCone)
                        .coerceIn(0f, 1f)
                    /// O tamanho também varia: grão de pó não tem calibre.
                    val calibre = 0.6f + (((semente shr 19) and 0x3F) / 63f) * 0.7f
                    val alfa = ((1f - dist) * altura * 0.5f * forca).coerceIn(0f, 1f)
                    if (alfa > 0.02f) {
                        drawCircle(
                            color = Cores.destaqueQuente,
                            radius = calibre.dp.toPx(),
                            center = Offset(px, py),
                            alpha = alfa,
                        )
                    }
                    x += passoX
                    coluna++
                }
                y += passoY
                linha++
            }

            /// A lente.
            val largura = 26.dp.toPx()
            val altura = 5.dp.toPx()
            drawOval(
                color = Cores.destaqueQuente,
                topLeft = Offset(eixo - largura / 2, size.height - altura / 2),
                size = Size(largura, altura),
                alpha = forca.coerceAtMost(1f),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                /// ⚠️ **A ordem destes dois importa.** `padding` antes de
                /// `height` envolve: o nó fica com `fileira + inset` e o conteúdo
                /// com a fileira inteira. Invertidos, o `height` fixaria 64dp no
                /// total e o inset comeria o rótulo por dentro.
                .padding(bottom = insetBaixo)
                .height(ALTURA_DA_FILEIRA),
        ) {
            destinos.forEach { destino ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = destino.selecionado,
                            role = Role.Tab,
                            /// Sem ondulação: ela desenharia um círculo cinza
                            /// por cima da luz, que é o oposto do que a barra
                            /// inteira está tentando fazer.
                            indication = null,
                            interactionSource = remember {
                                androidx.compose.foundation.interaction.MutableInteractionSource()
                            },
                            onClick = destino.aoTocar,
                        )
                        /// Os 16dp de baixo viraram 5: eles afastavam o rótulo da
                        /// borda da tela, e quem faz isso é o inset do sistema,
                        /// que agora mora logo abaixo desta fileira.
                        .padding(top = 6.dp, bottom = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = destino.icone,
                        contentDescription = null,
                        tint = if (destino.selecionado) Cores.destaqueQuente else Cores.destaqueApagado,
                        /// ⚠️ **22dp, e o padrão do `Icon` é 24.** É o último dp
                        /// que dava pra tirar sem mexer no rótulo — e os ícones
                        /// desta barra são desenhos de duas ou três formas
                        /// cheias, que aguentam a redução sem virar borrão do
                        /// jeito que um ícone de traço fino viraria.
                        modifier = Modifier.size(22.dp),
                    )
                    Box(Modifier.height(3.dp))
                    Text(
                        text = destino.rotulo,
                        style = Tipo.pilula,
                        color = if (destino.selecionado) Cores.destaqueQuente else Cores.textoApagado,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
