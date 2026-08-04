package dev.odeon.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
}

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
        content = conteudo,
    )
}
