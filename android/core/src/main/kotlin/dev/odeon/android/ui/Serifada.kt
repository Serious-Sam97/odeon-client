package dev.odeon.android.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.odeon.nucleo.R

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
///
/// ## Por que ela mora no `:core`, que é o módulo que não desenha — 12/08/2026
///
/// Ela veio do `ui/Tema.kt` do `:app` na T0 do `docs/REDESENHO-TV.md` (§3.3), e
/// a régua do `:core` aguenta pelo mesmo motivo que aguentou a paleta: **uma
/// família de fonte não desenha**. `FontFamily` é um valor, do mesmo naipe que
/// um `Color`; quem desenha é o `Text` que a recebe, e esse ficou lá.
///
/// ⚠️ O que forçou a mudança foi um **duplicado que já existia**. O `:tv` tinha
/// a `SerifadaDaSala`, com este corpo exato, lendo este mesmo `R.font` — dois
/// `FontFamily` sobre um `.ttf` só. O `.ttf` já morava aqui justamente pra não
/// haver duas cópias do arquivo, e a declaração tinha escapado da mesma régra.
///
/// Agora há uma, e a `Contracapa` do `:cenario` também a lê — que é o terceiro
/// consumidor, e o que tornou a duplicata insustentável.
///
/// ## ⚠️ O que **não** desceu junto, e é decisão
///
/// O `Tipo` — o versalete de rótulo e o texto de pílula — **ficou no `:app`**, e
/// o `TipoDaSala` continua no `:tv`. Eles não são a mesma coisa em tamanhos
/// diferentes: são papéis iguais com números que **têm** que divergir, e o
/// comentário do `TipoDaSala` já dizia por quê antes desta mudança existir —
/// «11sp a três metros não é um rótulo discreto, é um rótulo ilegível».
///
/// Uma fonte é a mesma a trinta centímetros e a três metros. Um corpo de 11sp
/// não é. É por isso que uma desceu e o outro não.
val Serifada = FontFamily(
    Font(R.font.noto_serif_semibold, FontWeight.SemiBold),
)
