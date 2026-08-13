package dev.odeon.android.ui

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
///
/// ## Por que ela mora no `:core`, que é o módulo que não desenha — 12/08/2026
///
/// Ela veio do `ui/Tema.kt` do `:app` quando a TV nasceu, e a régua do `:core`
/// aguenta a mudança sem esticar: **uma cor não desenha**. `Color` é um valor —
/// um `inline class` sobre um `ULong` —, do mesmo naipe que um `ItemDaBiblioteca`.
/// Quem desenha é o `Modifier.background` que a recebe, e esse ficou lá.
///
/// O que forçou a mudança foi a alternativa. Com a paleta no `:app`, o `:tv`
/// teria de copiá-la — e o comentário logo acima já diz o que isso custa,
/// escrito antes de haver TV pra provar: «um dourado diferente no celular faria
/// parecer outro app do mesmo dono». Numa TV é pior, porque é onde as duas
/// telas ficam **lado a lado** na mesma sala.
///
/// A cópia também não avisa quando envelhece. O dia em que alguém acertar o
/// `--accent` na `styles.css` e nos dois clientes menos num, esse um continua
/// compilando.
///
/// O que o `:tv` **não** herda é a escala tipográfica: `Tipo` e o `Typography`
/// continuam cada um no seu módulo, porque 11sp de rótulo a três metros de
/// distância não é rótulo, é nada. Mesma tinta, outro tamanho de letra.
///
/// ⚠️ **A `Serifada` desceu depois, e não desmente o parágrafo acima** — ela
/// mora em `Serifada.kt`, ao lado deste arquivo, desde a T0 do
/// `docs/REDESENHO-TV.md` (12/08/2026). A linha que separa as duas é a mesma que
/// separa tinta de tamanho: uma **família de fonte** é a mesma a trinta
/// centímetros e a três metros, e um **corpo de 11sp** não é. Desceu a família;
/// os corpos ficaram.
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

    /// ## O papel, e as duas tintas que vivem sobre superfície clara
    ///
    /// Este app é escuro do começo ao fim, e estas três cores são a exceção —
    /// elas existem só onde a tela desenha **um objeto de papel**: a foto de cena
    /// pendurada no varal da ficha, e o bilhete do «continuar».
    ///
    /// ⚠️ **Não são «o tema claro».** Não há tema claro aqui, e nem deve haver:
    /// a sala é escura por decisão, e clarear a interface inteira desfaria o
    /// facho, a cortina e a lâmpada do plano de uma vez. O que estas cores dizem
    /// é outra coisa — que naquele retângulo há papel, e papel não é preto.
    ///
    /// O branco é sujo de propósito (`F2ECE0`, não `FFFFFF`): foto revelada e
    /// ingresso de bilheteria amarelam, e um branco puro no meio de uma tela
    /// escura vira um buraco de luz em vez de um objeto.
    val papel = Color(0xFFF2ECE0)
    val tintaDoPapel = Color(0xFF5C5548)
    val tintaDoBilhete = Color(0xFF241A09)

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
