package dev.odeon.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.odeon.android.R

/// A paleta, e ela é a mesma da web.
///
/// Os valores saem letra por letra do `web/src/styles.css`. Não é preciosismo:
/// o Odeon é um produto só visto de dois lugares, e um dourado diferente no
/// celular faria parecer outro app do mesmo dono.
///
/// | | |
/// |---|---|
/// | `fundo` | `--bg` |
/// | `fundoElevado` | `--bg-raised` — o que sobe: cartões, folhas, diálogos |
/// | `fundoAfundado` | `--bg-sunken` — o que desce: trilhos, poços, o fundo do fundo |
/// | `linha` | `--line` |
/// | `texto` | `--fg` |
/// | `textoApagado` | `--fg-muted` |
/// | `destaque` | `--accent` — o filamento aceso |
/// | `destaqueQuente` | `--accent-hot` — o topo da luz: toque e foco |
/// | `destaqueApagado` | `--accent-dim` — o filamento apagado: réguas, bordas |
/// | `perigo` | `--danger` |
/// | `certo` | `--ok` |
object Cores {
    val fundo = Color(0xFF0A0A0C)
    val fundoElevado = Color(0xFF131318)
    val fundoAfundado = Color(0xFF050507)
    val linha = Color(0xFF23232C)
    val texto = Color(0xFFECEEF4)
    val textoApagado = Color(0xFF8B8D9A)
    val destaque = Color(0xFFE0B062)
    val destaqueQuente = Color(0xFFFFD98A)
    val destaqueApagado = Color(0xFF8A6A3A)
    val perigo = Color(0xFFFF6B6B)
    val certo = Color(0xFF4ADE80)

    /// ⚠️ **O vermelho da cortina é matéria, não significado.**
    ///
    /// O app já tem um vermelho — o `perigo` —, e ele é **semântico**: quer
    /// dizer «isto deu errado» ou «isto vence». O pano do cinema é a primeira
    /// cor deste app que não quer dizer nada: ela é a cor de uma coisa, como a
    /// madeira da prateleira da locadora.
    ///
    /// Por isso são dois nomes próprios e não um `perigo.copy(...)`: no dia em
    /// que alguém mexer no vermelho de erro, a cortina não pode mudar de tom
    /// junto. E no dia em que alguém procurar «que vermelho é este», o nome
    /// responde.
    ///
    /// Os dois tons são a prega: o claro é onde a luz da marquise bate, o fundo
    /// é a dobra. Pano de uma cor só lê como retângulo.
    val cortina = Color(0xFF84252A)
    val cortinaFunda = Color(0xFF4A1114)
}

/// A serifada de display, e por que ela viaja dentro do APK.
///
/// A web declara `--font-display: ui-serif, Georgia, "Noto Serif", "Times New
/// Roman", serif` e a usa em **53 lugares** — título de obra, título do player,
/// o número da afinidade, o relógio do "ao vivo". O app era sem serifa em 100%
/// da tela, e é essa a razão de as duas telas parecerem de produtos diferentes
/// mesmo carregando a mesma paleta: na web um título é letreiro de cinema, aqui
/// era item de lista.
///
/// ## Por que embutida, e não a serifa do sistema
///
/// `ui-serif` não existe no Android, e havia três caminhos:
///
/// | | por que não |
/// |---|---|
/// | `FontFamily.Serif` | existe e custa 0 KB, mas resolve pra coisa diferente em cada fabricante — e um letreiro que muda de aparelho pra aparelho torna o screenshot, que é a régua deste projeto, uma prova fraca |
/// | Downloadable Fonts | 0 KB no APK, mas exige Play Services **e rede**. Num app cujo argumento é a fase 6, o título do filme baixado apareceria sem serifa justamente offline |
/// | embutir | ⬅️ determinística, e funciona sem rede |
///
/// **Noto Serif** porque ela está nomeada na própria `--font-display` da web —
/// ou seja, é o que boa parte dos navegadores já resolve lá — e porque a
/// altura-de-x grande dela é o que segura título sobre arte de pôster.
///
/// ## O tamanho, medido e não estimado
///
/// O `.ttf` estático (peso 600, Latino+Grego+Cirílico, hinted) tem **739 KB** —
/// bem acima dos ~200 KB que o `docs/REDESENHO.md` chutou. O que importa é o
/// APK, e esse número está no README, medido antes e depois.
///
/// Um peso só, de propósito: cada peso adicional é outro arquivo inteiro, e a
/// fonte variável resolveria isso em um só — mas `FontVariation` é de API 26,
/// exatamente o `minSdk` deste app, e nascer colado no piso da versão é onde
/// mora o defeito que só aparece no aparelho mais velho.
///
/// A licença (OFL) viaja em `assets/OFL-NotoSerif.txt`. Ela exige que o texto
/// acompanhe a distribuição, e este repositório é público.
val Serifada = FontFamily(
    Font(R.font.noto_serif_semibold, FontWeight.SemiBold),
)

/// Os papéis que o Material 3 não tem nome pra dar.
///
/// O `Typography` abaixo cobre o que cai nos vãos do Material — display,
/// headline, body. O que sobra é o **rótulo de seção**, que não tem slot
/// equivalente porque não é uma escala de tamanho: é uma forma.
object Tipo {
    /// O versalete espaçado que encabeça seção.
    ///
    /// Medido no `.strip h2` da web (`styles.css:2049`): 11px, peso 700,
    /// `letter-spacing: 0.28em`, caixa alta, cor `--accent`.
    ///
    /// ⚠️ A caixa alta é do **chamador**, não daqui. `TextStyle` não tem
    /// `text-transform`, e fazer o `RotuloDeSecao` aplicar `.uppercase()` é o
    /// que garante que o rótulo seja escrito em minúscula no código — como todo
    /// o resto deste app — e desenhado em caixa alta.
    ///
    /// Sem serifa, e é a web que decide: o `.strip h2` não declara
    /// `--font-display`. Serifa em 11px espaçado a 0.28em vira ruído, porque a
    /// serifa é justamente o que liga uma letra à seguinte.
    val rotulo = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.28.em,
    )

    /// O texto dentro de uma pílula.
    ///
    /// Medido no `.chip` (`styles.css:1101`) e no `.cartaz-chip` (`:3954`): os
    /// dois declaram **12px** e mais nada — sem `font-weight`, sem
    /// `letter-spacing`. Ou seja, peso e espaçamento normais, e só o tamanho é
    /// escolha.
    ///
    /// A tentação é reusar o `rotulo` acima, que já é o versalete da casa. Não
    /// serve, e a web concorda: `letter-spacing` soma o espaço **depois** de
    /// cada letra, inclusive da última, então 0.28em dentro de uma pílula
    /// empurraria a palavra contra a borda direita.
    val pilula = TextStyle(fontSize = 12.sp)
}

/// A escala tipográfica, e o que ela troca de serifa.
///
/// Só **dois** slots do Material viram serifada, e a escolha não é de gosto —
/// é o que a web faz, conferido classe por classe:
///
/// | slot | quem usa hoje | web |
/// |---|---|---|
/// | `displaySmall` | a marca "Odeon" no login | — |
/// | `headlineSmall` | os 4 títulos de tela **e** o título da obra na ficha | `.hero-title`, `.player-title` |
/// | `bodySmall` | o título no cartaz da grade | `.poster-title`: **sem serifa**, 19px/700 |
///
/// A última linha é a que evita o erro fácil. Parece que "todo título de obra é
/// serifado", e não é: na web o título dentro do cartaz da grade é sem serifa.
/// Serifa ali seria serifa em 12sp multiplicada por 8.316 entradas — o oposto
/// de letreiro.
///
/// ⚠️ Uma coisa fica fora e é decisão do dono: a marca do login. A `.brand-name`
/// da web (`styles.css:195`) **não** é serifada — ela é sem serifa, 700, caixa
/// alta, `letter-spacing: 0.28em`, ou seja o mesmo tratamento do `Tipo.rotulo`.
/// Aqui ela ficou serifada porque `displaySmall` é slot de display e o login não
/// tem equivalente na web pra copiar. Alinhá-la ao versalete da web é uma linha,
/// e é pergunta, não conserto.
///
/// Os números vêm da `styles.css`, não de lembrança:
///
/// - `.hero-title`: `clamp(30px, 4vw, 58px)`, entrelinha **1.02**, tracking
///   **-0.01em**. Num celular de ~411dp, `4vw` dá ~16px e o `clamp` trava no
///   piso — ou seja, **30sp** é o tamanho que a web mostra no celular, não o
///   mínimo teórico dela.
/// - `.player-title`: `clamp(22px, 2.4vw, 34px)`, entrelinha 1.05.
///
/// O tracking negativo importa mais do que parece: serifa em corpo grande
/// precisa fechar, senão o título se espalha e deixa de ser uma coisa só.
private val Padrao = Typography()

private val TipografiaOdeon = Padrao.copy(
    displaySmall = Padrao.displaySmall.copy(
        fontFamily = Serifada,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.01).em,
    ),
    headlineSmall = Padrao.headlineSmall.copy(
        fontFamily = Serifada,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.01).em,
    ),
)

/// O esquema de cores do Material 3, traduzido da paleta acima.
///
/// ## Por que não há esquema claro
///
/// Porque não há tema claro. O Odeon é escuro e sempre foi — a web não tem
/// alternância, e um app de cinema com fundo branco é uma lanterna na cara de
/// quem está assistindo à noite. `isSystemInDarkTheme()` não é consultado aqui,
/// e é decisão, não esquecimento.
///
/// ## E a cor dinâmica (Material You)?
///
/// Fica de fora, e a espec já disse por quê (§4): ela é de API 31, e o caminho
/// pra quem está abaixo é «cai na paleta da casa, que já existe». Só que aqui
/// ela ficaria de fora **mesmo acima de 31** — deixar o papel de parede da
/// pessoa repintar o Odeon apagaria a única coisa que o dourado faz, que é ser
/// o mesmo dourado em toda tela.
///
/// A cor que **vai** variar por obra é outra: a `dominant_color` que o servidor
/// já extraiu de 9.332 pôsteres. Essa tinge a tela com o filme que se está
/// olhando, e é a que a espec (§4b) chama de "a cor da tela sai do pôster".
private val EsquemaEscuro = darkColorScheme(
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

    outline = Cores.linha,
    outlineVariant = Cores.linha,
)

/// Envolve a árvore inteira. Toda tela do app nasce dentro deste.
///
/// Não consulta `isSystemInDarkTheme()`, e a resposta do porquê está no
/// comentário do `EsquemaEscuro` acima: não há tema claro pra escolher.
@Composable
fun TemaOdeon(conteudo: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaEscuro,
        typography = TipografiaOdeon,
        content = conteudo,
    )
}
