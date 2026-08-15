package dev.odeon.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider

/// ⚠️ **A paleta não está mais aqui.** Ela foi pro `:core` quando a TV nasceu,
/// em `core/.../ui/Cores.kt`, e o comentário de lá explica por quê — o resumo é
/// que uma cor não desenha, e a alternativa era o `:tv` copiar o dourado.
///
/// O pacote é o mesmo (`dev.odeon.android.ui`), então `Cores.destaque` continua
/// se escrevendo igual em toda tela deste módulo. Nenhum `import` mudou.
///
/// O que ficou aqui é o que **é** deste módulo: a escala tipográfica do celular
/// e o esquema do Material 3 de toque. A TV tem os dela.

/// ⚠️ **A `Serifada` também não está mais aqui.** Ela desceu pro `:core` na T0
/// do `docs/REDESENHO-TV.md` (§3.3), em `core/.../ui/Serifada.kt`, e o
/// comentário de lá explica por quê — o resumo é que uma família de fonte não
/// desenha, e o `:tv` já tinha uma segunda declaração dela sobre o mesmo `.ttf`.
///
/// O pacote é o mesmo (`dev.odeon.android.ui`), então `Serifada` continua se
/// escrevendo igual em toda tela deste módulo. Nenhum `import` mudou.
///
/// ⚠️ O `Tipo`, logo abaixo, **ficou** — e é decisão, não esquecimento. Ele e o
/// `TipoDaSala` do `:tv` são os mesmos papéis com números que têm que divergir:
/// 11sp de rótulo a três metros de distância não é um rótulo discreto, é um
/// rótulo ilegível. Uma fonte é a mesma às duas distâncias; um corpo não é.

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
    ) {
        /// ⚠️ **As peças do `:cenario` desenham texto e não têm tema.**
        ///
        /// Elas usam o `Texto` de lá, que não pode ler `LocalTextStyle` porque
        /// `LocalTextStyle` é do `material3` — e o `:cenario` não depende de
        /// nenhum dos dois Material, que é a régua que o faz compilar nos dois
        /// lados.
        ///
        /// Esta linha empresta o estilo daqui pra lá. Sem ela as peças caem em
        /// `TextStyle.Default`, e todo campo que uma chamada não escreve muda —
        /// a lombada da caixa, que declara corpo e peso e nunca declarou
        /// entrelinha, sai 13px fora do lugar.
        ///
        /// Emprestar é melhor que reconstruir: eu tentei reconstruir o
        /// `bodyLarge` à mão e as telas discordaram entre si. Ver o comentário
        /// do `LocalLetraDoHospedeiro`.
        CompositionLocalProvider(
            LocalLetraDoHospedeiro provides LocalTextStyle.current,
            content = conteudo,
        )
    }
}
