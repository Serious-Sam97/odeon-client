package dev.odeon.android.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.Serifada
import androidx.tv.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import dev.odeon.android.ui.LocalLetraDoHospedeiro

/// A sala: as medidas, a tipografia e o tema de quem está a três metros.
///
/// ## O que muda de um celular pra uma TV, e o que não muda
///
/// **Não muda a tinta.** A `Cores` vem do `:core` inteira, sem um tom
/// redefinido — é o mesmo dourado da web e do celular, e o comentário dela
/// explica por que isso não é negociável.
///
/// **Muda tudo o mais**, e a régua é uma só: no celular a tela está a 30 cm dos
/// olhos e se toca nela; aqui está a 3 metros e se aponta pra ela com um
/// controle. Os dois números que saem disso governam este arquivo:
///
/// | | celular | sala |
/// |---|---|---|
/// | distância | ~30 cm | ~3 m — **dez vezes** |
/// | como se escolhe | dedo, em qualquer ponto | D-pad, um salto por vez |
///
/// A segunda linha é a que mais surpreende quem vem do celular. Num celular
/// **todo** elemento é alcançável em um gesto, e por isso a ordem dos elementos
/// na tela quase não importa. Aqui, chegar num botão custa N apertos de seta —
/// e o que está longe demais é, na prática, o que não existe.
object Sala {

    /// ## O overscan, que é a única margem que não é gosto
    ///
    /// Uma TV não mostra a imagem inteira. A moldura física come alguns por
    /// cento de cada lado, e o quanto muda de aparelho pra aparelho — é herança
    /// da transmissão analógica que nunca saiu do hardware. Conteúdo colado na
    /// borda simplesmente some, e some **só na TV de quem reclamou**, o que
    /// torna o defeito caro de reproduzir.
    ///
    /// 48dp na horizontal e 27dp na vertical é a recomendação do Android TV, e
    /// ela não é redonda por acaso: é 5% de 960dp e de 540dp, que é a tela de TV
    /// em dp na densidade padrão.
    ///
    /// ⚠️ **A margem é do conteúdo, não da tela.** Fundo, arte de fundo e o
    /// facho do player vão borda a borda; o que respeita o overscan é texto,
    /// cartaz e qualquer coisa que se possa perder. Empurrar o fundo pra dentro
    /// deixaria uma tarja preta em volta do app.
    /// ⚠️ Caiu de 48dp pra 24dp, e a diferença voltou **só do lado direito**.
    ///
    /// 48dp é a regra dos 5% de overscan, e ela vale pra borda da tela. À
    /// esquerda não há borda: há o trilho, que já é margem — somar os dois punha
    /// 88dp entre a tela e a primeira coisa visível, e foi a segunda vez que o
    /// dono reclamou do mesmo espaço.
    ///
    /// Quem devolve os 24dp que faltam à direita é o `TelaInicialDaTv`, num lugar
    /// só. Trocar isto nas trinta e seis chamadas espalhadas seria trinta e seis
    /// chances de errar uma.
    val overscanH = 24.dp
    val overscanV = 27.dp

    /// O quanto um cartaz cresce ao receber o foco.
    ///
    /// Num celular não há equivalente: o dedo **é** o cursor, e a pessoa sabe
    /// onde vai tocar porque está olhando pro próprio dedo. Aqui não há cursor
    /// nenhum, e a única resposta à pergunta "onde eu estou?" é o que a tela
    /// fizer de diferente com o item focado.
    ///
    /// ## ⚠️ **1,0 — o crescer saiu, a pedido do dono** — 13/08/2026
    ///
    /// > «ao usar o dpad para percorrer os filmes tanto na biblioteca quanto
    /// > locadora tu colocou um efeito meio estranho que mexe tudo na tela, tire
    /// > ele, quero só ir percorrendo os filmes sem muito estresse ou
    /// > movimentos»
    ///
    /// O comentário que estava aqui defendia 1,12 com um argumento que continua
    /// válido em teoria — «o item focado passa **por cima** do vizinho em vez de
    /// empurrá-lo (…) empurrar faria a fileira inteira andar a cada seta, que é
    /// o efeito de enjoo dos apps de TV mal feitos».
    ///
    /// Ele errava por um degrau: eu me preocupei com o vizinho **andar** e não
    /// com o item **inchar**. Numa grade de oitenta cartazes, cada seta faz um
    /// deles pular 12% e o anterior desinchar — e a três metros isso lê como a
    /// tela inteira respirando a cada aperto, que é o «mexe tudo» do relato.
    ///
    /// ⚠️ **O foco continua legível**, e é por isso que dá pra tirar: o
    /// `Focavel` desenha um anel dourado de 3dp em volta do alvo. A pergunta «onde
    /// eu estou?» tinha **duas** respostas, e uma delas bastava.
    ///
    /// A constante fica em vez de sumir: ela é o lugar onde este número mora, e
    /// zerá-la é uma linha se alguém quiser o efeito de volta.
    const val ESCALA_DO_FOCO = 1f

    /// A medida de um cartaz em pé — pôster de filme, 2:3.
    ///
    /// ⚠️ **160x240dp, e o número anterior era 200x300 — medido e corrigido na
    /// TCL em 12/08/2026.**
    ///
    /// O comentário aqui prometia «quatro e um pedaço por fileira». Na TV saíram
    /// **três**, e a conta de guardanapo é simples: 960dp de tela útil, menos 96
    /// do trilho fechado, menos 96 de overscan, sobram 768 — e a 220dp por
    /// cartaz (200 + 20 de vão) isso é 3,5.
    ///
    /// A 160dp o mesmo espaço dá `768 / 180 = 4,2`: quatro e um pedaço, que é o
    /// que a frase dizia desde o começo. O pedaço continua sendo de propósito —
    /// uma fileira que termina exata na borda parece lista completa, e ninguém
    /// tenta ir pra direita.
    ///
    /// Três colunas não era feio, era **caro**: com 8.316 obras, cada coluna a
    /// menos é um terço a mais de rolagem no D-pad.
    /// ## ⚠️ **Tudo encolheu, a pedido do dono** — 13/08/2026
    ///
    /// > «as capas dos filmes estão muito grandes o que faz caber bem menos na
    /// > tela, deixe em um tamanho médio, para que caiba uns 6 filmes a cada
    /// > linha ou algo assim. também nos outros menus as coisas estão muito
    /// > grande, então diminua tudo 20%»
    ///
    /// O pedido tem uma regra (20%) e um alvo (seis por fileira), e os dois não
    /// dão o mesmo número. Vale o **alvo**, porque é ele que a pessoa vê:
    ///
    /// ```
    /// 768dp úteis   (960 de tela − 96 do trilho − 96 de overscan)
    /// ÷ 6 filmes
    /// = 128dp por vaga, gasto entre o cartaz e o vão
    /// ```
    ///
    /// Com o vão em 16, sobra **112dp** de cartaz — que é 30% abaixo dos 160, e
    /// não 20. Os outros números desta tela levaram os 20% pedidos.
    ///
    /// ⚠️ E o comentário anterior aqui já tinha errado nesta mesma conta uma vez,
    /// em sentido contrário: ele prometia «quatro e um pedaço» com 200dp e a TCL
    /// mostrou três. A lição foi a mesma das duas vezes — **contar a fileira na
    /// tela**, não estimar a largura do cartaz.
    val cartazL = 112.dp
    val cartazA = 168.dp

    /// ## ⚠️ A locadora **não** encolheu, e é pedido explícito
    ///
    /// > «então diminua tudo 20% (menos a locadora, esse eu vejo melhor depois)»
    ///
    /// A estante ficou com as medidas de antes. Isso é de propósito e não
    /// esquecimento: a locadora tem prateleira de madeira, plaquinha de papel e
    /// etiqueta colada, e essas três não escalam com o cartaz — encolher só o
    /// filme deixaria a moldura da estante desproporcional ao que ela guarda.
    ///
    /// ⚠️ **Duas medidas para a mesma coisa é dívida**, e ela está aqui com nome
    /// e prazo: quando o dono olhar a locadora, ou os dois números viram um, ou
    /// este ganha o comentário que explica por que a estante mede diferente.
    val cartazLdaEstante = 160.dp
    val cartazAdaEstante = 240.dp

    /// A medida de um cartaz deitado — arte 16:9, pro "continuar assistindo" e
    /// pro mural.
    ///
    /// Deitado porque o que ele mostra é um **quadro do filme**, não a capa: a
    /// pergunta ali não é "que filme é este" (quem parou no meio já sabe) e sim
    /// "onde eu estava".
    /// −20%, como pedido. Ele é o cartaz **deitado** do continuar e do mural.
    val quadroL = 240.dp
    val quadroA = 135.dp

    /// ⚠️ O vão entra na conta das seis vagas — ver o `cartazL`. 16 é o que
    /// sobra depois de dar 112 ao cartaz, e é também os 20% de desconto do
    /// número antigo.
    val vaoEntreCartazes = 16.dp
    val vaoEntreFileiras = 29.dp
}

/// ⚠️ **A `SerifadaDaSala` não existe mais.** Use a `Serifada`, do `:core`.
///
/// Ela era a mesma família do celular, declarada aqui uma segunda vez sobre o
/// mesmo `R.font` do `:core` — e o comentário que morava nesta linha já sabia o
/// risco, com todas as letras: «o `.ttf` mora lá justamente pra não haver dois
/// (…) o grande é o dia em que alguém trocar a fonte num módulo só».
///
/// Ele estava certo sobre o arquivo e escapou sobre a declaração: o `.ttf` era
/// um, e os `FontFamily` sobre ele eram **dois**. Trocar a fonte no `:core` e
/// esquecer esta linha daria exatamente o defeito previsto, só que uma camada
/// acima. A T0 do `docs/REDESENHO-TV.md` (§3.3) juntou as duas quando a
/// `Contracapa` virou o terceiro consumidor.
///
/// O `TipoDaSala` abaixo **não** foi junto, e a diferença é o argumento inteiro:
/// uma família de fonte é a mesma a três metros, e um corpo de 11sp não é.

/// Os papéis que o Material não tem nome pra dar, do tamanho da sala.
object TipoDaSala {
    /// O versalete espaçado que encabeça fileira.
    ///
    /// ⚠️ **14sp, e não os 11sp do celular.** É o mesmo desenho — peso 700,
    /// `letter-spacing: 0.28em`, caixa alta — na escala daqui. Copiar os 11sp do
    /// `Tipo.rotulo` seria copiar o número em vez do papel: 11sp a três metros
    /// não é um rótulo discreto, é um rótulo ilegível.
    ///
    /// A caixa alta continua sendo do chamador, como no celular: `TextStyle` não
    /// tem `text-transform`, e o rótulo se escreve em minúscula no código.
    val rotulo = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.28.em,
    )

    /// O texto dentro de uma pílula. 15sp — o `.chip` da web é 12px, e vale aqui
    /// o mesmo argumento do rótulo.
    ///
    /// Sem `letter-spacing`, e é a web que decide: o `.chip` não declara nenhum.
    /// O espaçamento soma depois da **última** letra também, e dentro de uma
    /// pílula isso empurra a palavra contra a borda direita.
    val pilula = TextStyle(fontSize = 15.sp)
}

/// A escala tipográfica da sala.
///
/// ## Ela não é a do celular multiplicada por um número
///
/// A tentação é pegar o `Typography` do `:app` e escalar tudo por 1,5. Não
/// funciona, e o motivo é que os dois eixos — distância e forma de escolher —
/// puxam para lados diferentes:
///
/// - **corpo de texto** precisa crescer muito, porque ler a 3 m é o problema
/// - **título** cresce menos do que se pensa, porque a tela também é maior: um
///   `headlineSmall` de 30sp no celular ocupa 1/13 da largura; aqui, esticado
///   proporcionalmente, viraria um letreiro que não deixa nada mais caber
///
/// Os números abaixo saem da recomendação de leitura do Android TV — corpo em
/// **18sp no mínimo**, nunca abaixo de 16 — cruzada com o que a `styles.css`
/// diz de cada papel. O `.hero-title` da web é `clamp(30px, 4vw, 58px)`: no
/// celular o `clamp` trava no piso (30), e numa tela de 1920 ele bate no **teto**
/// (58). Ou seja, os 44sp do `headlineLarge` daqui não são invenção — são a web
/// em tela grande, com o desconto de a TV estar mais longe que um monitor.
///
/// A serifa entra nos mesmos dois lugares que no celular, e pela mesma razão: os
/// títulos são letreiro. O `.poster-title` continua **sem** serifa — o título
/// dentro do cartaz é item de grade, e serifa em corpo pequeno multiplicada por
/// uma fileira inteira é ruído.
private val Padrao = Typography()

private val TipografiaDaSala = Padrao.copy(
    displayMedium = Padrao.displayMedium.copy(
        fontFamily = Serifada,
        fontWeight = FontWeight.SemiBold,
        fontSize = 56.sp,
        lineHeight = 58.sp,
        letterSpacing = (-0.01).em,
    ),
    headlineLarge = Padrao.headlineLarge.copy(
        fontFamily = Serifada,
        fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.01).em,
    ),
    headlineMedium = Padrao.headlineMedium.copy(
        fontFamily = Serifada,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.01).em,
    ),
    /// O corpo. **20sp**, e o piso do Android TV é 18 — o Odeon escreve
    /// parágrafo de sinopse e de ensaio da revista, que é texto pra ler e não
    /// pra bater o olho.
    bodyLarge = Padrao.bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
    bodyMedium = Padrao.bodyMedium.copy(fontSize = 18.sp, lineHeight = 25.sp),
    /// O título dentro do cartaz. Sem serifa, pelo motivo do comentário acima.
    bodySmall = Padrao.bodySmall.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    labelLarge = Padrao.labelLarge.copy(fontSize = 17.sp),
    labelMedium = Padrao.labelMedium.copy(fontSize = 15.sp),
)

/// O esquema de cores, traduzido da `Cores` do `:core`.
///
/// ## ⚠️ Este `darkColorScheme` **não** é o do celular, e o compilador não avisa
///
/// Ele vem de `androidx.tv.material3`, não de `androidx.compose.material3`. Os
/// dois têm o mesmo nome, aceitam quase os mesmos parâmetros, e trocá-los
/// compila — o que sai é uma tela sem nenhum estado de foco, invisível pro
/// D-pad.
///
/// É a "briga constante" que a espec (§4) previu, e a razão de este módulo
/// existir separado: aqui o `androidx.compose.material3` **não está no
/// classpath**, então não há o que confundir. Ver `tv/build.gradle.kts`.
///
/// Não há esquema claro, pela mesma razão de sempre: o Odeon é escuro, a web não
/// tem alternância, e uma TV clara à noite é uma luminária.
private val EsquemaDaSala = darkColorScheme(
    primary = Cores.destaque,
    onPrimary = Cores.fundoAfundado,
    primaryContainer = Cores.destaqueApagado,
    onPrimaryContainer = Cores.texto,

    secondary = Cores.destaqueQuente,
    onSecondary = Cores.fundoAfundado,

    background = Cores.fundo,
    onBackground = Cores.texto,

    surface = Cores.fundo,
    onSurface = Cores.texto,
    surfaceVariant = Cores.fundoElevado,
    onSurfaceVariant = Cores.textoApagado,

    error = Cores.perigo,
    onError = Cores.fundoAfundado,
)

/// Envolve a árvore inteira. Toda tela da sala nasce dentro deste.
@Composable
fun TemaDaSala(conteudo: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaDaSala,
        typography = TipografiaDaSala,
    ) {
        /// ⚠️ **A mesma linha que o `TemaOdeon` do `:app` tem, e pelo mesmo
        /// motivo.**
        ///
        /// As peças do `:cenario` — a caixa, a contracapa, a película, a
        /// cortina — desenham texto com o `Texto` de lá, que não pode ler
        /// `LocalTextStyle` porque `LocalTextStyle` é do Material: **deste**
        /// Material aqui, e do de celular lá. Depender de um dos dois é o que
        /// faria o módulo compartilhado deixar de compilar de um lado.
        ///
        /// Então cada hospedeiro empresta o seu. Aqui o que atravessa é o
        /// `bodyLarge` da `TipografiaDaSala` — o corpo de quem lê a três metros
        /// —, e não os 16sp do celular.
        ///
        /// ⚠️ Sem esta linha as peças caem em `TextStyle.Default` e todo campo
        /// que uma chamada não escreve muda de valor. No celular isso deslocou a
        /// lombada da caixa em 13px, medido. Aqui ninguém mediu ainda, porque
        /// nenhuma peça do `:cenario` desenha nesta tela até a T2.
        CompositionLocalProvider(
            LocalLetraDoHospedeiro provides LocalTextStyle.current,
            content = conteudo,
        )
    }
}
