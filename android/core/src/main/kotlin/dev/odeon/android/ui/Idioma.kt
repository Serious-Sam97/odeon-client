package dev.odeon.android.ui

/// O nome de um idioma em português, a partir do código do contêiner.
///
/// ## Por que esta tabela existe aqui, se o projeto manda não compor rótulo
///
/// A regra escrita em três lugares (`FaixaDeLegenda.label`, `FaixaDeAudio.label`,
/// `PlanoDeReproducao.reasons`) é que **rótulo composto vem pronto do servidor** —
/// montar «Português - AC3 5.1» aqui seria a terceira redação da mesma frase.
///
/// Isto é outra coisa. O `audio_langs` da [dev.odeon.android.dados.VersaoDaObra]
/// vem como **código** — `["por"]` —, e de propósito: o servidor não compõe frase
/// ali, e recusa até mandar `und`. Traduzir um código em nome é **desenho**, e é
/// exatamente o mesmo caso do `Acontecimento.frase` («o servidor manda o verbo em
/// código e a frase em português é da tela») e do `Revista.rotuloDoEixo`. Os dois
/// moram no cliente por decisão escrita, e este mora pelo mesmo motivo.
///
/// ## ⚠️ Código desconhecido devolve `null`, e a tela **omite**
///
/// Não devolve o código cru. Mostrar `hun` numa modal de escolha é mostrar à
/// pessoa um dado de contêiner com cara de idioma — é o §18, e é o mesmo erro que
/// o `und` cometia no menu de faixas antes de 06/08/2026.
///
/// A lista cobre o que este acervo tem; o resto cai no `null` e some. Crescer a
/// tabela é barato, e é onde crescer.
private val NOMES = mapOf(
    // ISO 639-2/B (o que o ffprobe escreve) e 639-1, porque os dois aparecem.
    "por" to "Português", "pt" to "Português",
    "eng" to "Inglês", "en" to "Inglês",
    "spa" to "Espanhol", "es" to "Espanhol",
    "fra" to "Francês", "fre" to "Francês", "fr" to "Francês",
    "deu" to "Alemão", "ger" to "Alemão", "de" to "Alemão",
    "ita" to "Italiano", "it" to "Italiano",
    "jpn" to "Japonês", "ja" to "Japonês",
    "kor" to "Coreano", "ko" to "Coreano",
    "zho" to "Chinês", "chi" to "Chinês", "zh" to "Chinês",
    "rus" to "Russo", "ru" to "Russo",
    "ara" to "Árabe", "ar" to "Árabe",
    "nld" to "Holandês", "dut" to "Holandês", "nl" to "Holandês",
    "swe" to "Sueco", "sv" to "Sueco",
    "dan" to "Dinamarquês", "da" to "Dinamarquês",
    "nor" to "Norueguês", "no" to "Norueguês",
    "fin" to "Finlandês", "fi" to "Finlandês",
    "pol" to "Polonês", "pl" to "Polonês",
    "tur" to "Turco", "tr" to "Turco",
    "hin" to "Hindi", "hi" to "Hindi",
)

/// `por` → `Português`. `null` quando não se sabe — ver o cabeçalho.
///
/// ⚠️ **`und` cai no `null` de propósito**, e a guarda é redundante hoje: o
/// servidor já o recusa antes de mandar. Ela fica porque a redundância é barata e
/// porque o `rotuloDaFaixa` aprendeu essa lição do jeito caro — o menu de faixas
/// abriu com uma faixa chamada «und» na cara do dono em 06/08/2026.
fun idiomaEmPortugues(codigo: String): String? =
    NOMES[codigo.trim().lowercase()]

/// Os idiomas de uma faixa, prontos pra linha da modal.
///
/// `["por"]` → `Português`; `["por", "eng"]` → `Português e Inglês`.
///
/// ⚠️ **`null` quando nada é reconhecido**, inclusive pra lista vazia — que é o
/// caso do 007 em inglês deste acervo, cuja faixa aac não declara idioma. A tela
/// não escreve «idioma desconhecido» nem deixa a linha em branco reservada: ela
/// mostra o que sabe (a resolução, o tamanho, onde parou) e cala sobre o que não
/// sabe. É o §24.
///
/// ⚠️ E os desconhecidos são **descartados**, não somados como reticências: uma
/// faixa `por + hun` vira «Português», e não «Português e …». A segunda forma
/// prometeria uma informação que a tela não tem.
fun idiomasEmPortugues(codigos: List<String>): String? {
    val nomes = codigos.mapNotNull(::idiomaEmPortugues).distinct()
    return when (nomes.size) {
        0 -> null
        1 -> nomes[0]
        /// Dois idiomas numa versão só é **dual audio** — o que o dono procurou e
        /// não achou. Vale escrever por extenso: é a versão que dispensa a
        /// escolha, e quem a vê na modal entende na hora por que ela é diferente.
        else -> nomes.dropLast(1).joinToString(", ") + " e " + nomes.last()
    }
}
