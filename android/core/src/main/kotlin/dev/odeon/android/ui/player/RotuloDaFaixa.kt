package dev.odeon.android.ui.player

import dev.odeon.android.dados.FaixaDeAudio

/// O rótulo de uma faixa de áudio, com as quedas na ordem certa.
///
/// ⚠️ **O `label` vem pronto do servidor** — codec, idioma, canais e o resto já
/// vêm compostos por quem tem a probe. Montar «Português - AC3 5.1» aqui seria a
/// terceira redação da mesma frase entre web, Android e servidor, que é a mesma
/// regra do `label` das legendas e do `reasons` do plano.
///
/// As quedas existem pro caso de ele vir vazio, e cada uma é fato e não chute:
///
/// | | |
/// |---|---|
/// | `title` | o que quem ripou escreveu na faixa |
/// | `language` | o código do contêiner |
/// | `faixa N` | a posição, que é o que sempre se sabe |
///
/// ⚠️ **`und` conta como ausente**, e a foto de 06/08/2026 é que cobrou: o menu
/// abriu com uma faixa chamada `und`. Não é idioma — em ISO 639 quer dizer
/// *undetermined*, o contêiner declarando que **não sabe**. Repassar isso é a
/// tela mostrando um código que significa «sem informação»; `faixa 1` diz o
/// mesmo, e diz em português.
///
/// ## Por que ela mora no `:core`, e por que ela é `public` — 12/08/2026
///
/// Ela estava em `ui/player/Faixas.kt` do `:app`, `internal`, e a extração do
/// `:core` a denunciou pelo lado mais estranho possível: o
/// `FaixaDeAudioTest.kt` mora no pacote `dados`, veio junto com os modelos que
/// ele constrói, e aí não achou mais a função que ele testa.
///
/// O teste estava certo e o lugar é que estava errado. Isto não desenha nada —
/// é uma queda de quatro campos de um modelo do `:core` pra uma `String`. E a
/// prova de que o lugar é este chegou junto: a TV precisa **exatamente** do
/// mesmo rótulo no menu de faixas dela. Tivesse ficado `internal` no `:app`, o
/// `:tv` teria escrito a quarta redação da frase — justamente a que o aviso lá
/// em cima manda não escrever.
///
/// O pacote continua `dev.odeon.android.ui.player`, de propósito: `Faixas.kt` e
/// `TelaDoPlayer.kt` estão nele e chamavam a função sem `import`. `public` é o
/// que faz isso continuar valendo através da fronteira de módulo — `internal`
/// não atravessa.
fun rotuloDaFaixa(faixa: FaixaDeAudio): String =
    faixa.label.ifBlank { null }
        ?: faixa.title?.ifBlank { null }
        ?: faixa.language?.ifBlank { null }?.takeIf { it != "und" }
        ?: "faixa ${faixa.index + 1}"
