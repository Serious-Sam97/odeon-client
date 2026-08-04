package dev.odeon.android.dados

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/// O contrato com o servidor, escrito à mão.
///
/// ## Por que à mão, e não gerado
///
/// Porque é assim que as outras duas cópias são. `web/src/api.ts` descreve as
/// mesmas respostas em TypeScript escrito à mão, e o `clients/shared` em Kotlin.
/// Não há tipo compartilhado, não há código gerado, não há import cruzado — a
/// espec registrou isso como a dívida que a separação dos repositórios comprou
/// (§67 do DESIGN.md).
///
/// ## Todo campo opcional é opcional aqui também
///
/// Os `?` abaixo não são cautela: são o que o servidor devolve. Um `poster` nulo
/// é uma obra sem arte baixada — e são **8.598 de 17.930**, ou seja **48% do
/// acervo** (medido no banco em 04/08/2026). Declarar não-nulo faria a
/// biblioteca falhar em quase metade do que ela lista.
///
/// E o §18 manda o corolário: quando o dado não existe, a tela **omite**. Não
/// inventa, não escreve "—".

/// `POST /api/auth/login`
@Serializable
data class Credenciais(
    val username: String,
    val password: String,
    /// O nome do aparelho, e ele importa mais do que parece.
    ///
    /// A tela de aparelhos do admin lista as sessões por este rótulo. Sessão
    /// sem rótulo aparece anônima — e, pelo que o projeto já observou, sessão
    /// com `device_label` nulo é sinal de linha inserida à mão, não de login de
    /// verdade. Mandar o modelo do aparelho mantém essa distinção útil.
    @SerialName("device_label") val deviceLabel: String? = null,
)

/// A resposta do login. O `token` é o de **sessão**: 90 dias, `Bearer`.
@Serializable
data class RespostaDeLogin(
    val token: String,
    val user: Usuario,
)

@Serializable
data class Usuario(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    /// `"admin"` ou `"user"`. Não vira booleano aqui: o servidor manda o papel,
    /// e reduzir a "é admin?" perderia o dia em que houver um terceiro.
    val role: String,
    @SerialName("is_active") val ativo: Boolean = true,
) {
    val eAdmin: Boolean get() = role == "admin"
}

/// `GET /api/auth/status` — a única rota que responde sem sessão.
@Serializable
data class StatusDoServidor(
    @SerialName("needs_setup") val precisaConfigurar: Boolean,
)

/// `POST /api/auth/media-token`
///
/// Token curto (8h) que **só abre bytes**: pôster, stream, legenda. Ele existe
/// porque `<img src>` e o player não mandam header, então precisa viajar na
/// query — e URL vaza pra log de acesso.
///
/// ⚠️ **Emitir um novo aposenta o anterior** (§43). Renovar no meio de um filme
/// derruba o próprio player. Ver `Cofre` e `RepositorioOdeon.garantirTokenDeMidia`.
@Serializable
data class TokenDeMidia(val token: String)

/// `GET /api/library` — uma linha por **série** ou obra avulsa.
///
/// ## Não é `/api/works`, e a diferença é a tela inteira
///
/// `/api/works` devolve obra por obra: os 14.657 episódios do acervo viram
/// 14.657 cartões iguais. A web já passou por isso e a conclusão está escrita
/// no `api.ts`: «é listagem de arquivo e não biblioteca».
///
/// `/api/library` agrupa. Uma série é uma linha, com quantos episódios tem e
/// quantos foram vistos.
///
/// ## Conferido contra o servidor em 04/08/2026
///
/// Campo a campo contra `LibraryEntry` de `routes/works.rs:443`. Duas
/// diferenças, as duas de propósito:
///
/// **1. `height` e `size_bytes` não estão aqui.** O servidor manda; a grade não
/// usa. O `ignoreUnknownKeys` do `Rede` os descarta sem reclamar — que é o que
/// permite o servidor ganhar campo novo sem quebrar o app, e o que obriga esta
/// nota a existir, porque a omissão é silenciosa.
///
/// **2. As contagens são `Int` aqui e `i64` lá.** Estreitamento deliberado:
/// `work_count`, `season_count`, `finished_count` e `total` contam obras, e o
/// acervo tem **17.930** contra os 2.147.483.647 que um `Int` aguenta. Se um dia
/// estourar, o kotlinx-serialization lança em vez de truncar — falha barulhenta,
/// não número errado.
@Serializable
data class ItemDaBiblioteca(
    val id: String,
    @SerialName("is_series") val eSerie: Boolean,
    val title: String,
    val year: Int? = null,
    /// Caminho relativo, servido em `/artwork/…`. Nulo enquanto não identificado.
    val poster: String? = null,
    /// A cor extraída do pôster pelo servidor — **9.332 obras já têm**. É o que
    /// deixa a interface se tingir com a obra sem custar requisição nenhuma.
    @SerialName("dominant_color") val corDominante: String? = null,
    @SerialName("work_count") val quantasObras: Int = 0,
    @SerialName("season_count") val quantasTemporadas: Int = 0,
    @SerialName("finished_count") val quantasVistas: Int = 0,
    @SerialName("media_file_id") val arquivoId: String? = null,
    @SerialName("duration_seconds") val duracaoEmSegundos: Double? = null,

    /// A altura do vídeo em linhas, e o tamanho do arquivo em bytes.
    ///
    /// ## Eles sempre vieram na resposta, e o app jogava fora
    ///
    /// Estão no `LibraryEntry` do `web/src/api.ts` (`height`, `size_bytes`)
    /// desde antes deste app existir. A fase 1 mapeou o que a grade daquele dia
    /// desenhava e não voltou aqui — então `/api/library` mandava os dois em
    /// toda linha e o `ignoreUnknownKeys` do `Rede` os descartava, calado.
    ///
    /// É a terceira vez que ler a web economiza uma pergunta ao dono: a R4 do
    /// redesenho pede a linha `1969 · 816p · 2h22 · 2,3 GB`, e sem isto ela
    /// teria virado pedido de servidor por um dado que já estava chegando.
    ///
    /// **Nulos existem e são normais** — 8.598 obras não têm arquivo casado. Por
    /// isso a linha de metadados omite item por item, e não some inteira: §24.
    val height: Int? = null,
    @SerialName("size_bytes") val tamanhoEmBytes: Long? = null,

    val kind: String? = null,
    @SerialName("match_state") val estadoDaIdentificacao: String? = null,
    @SerialName("position_seconds") val ondeParou: Double? = null,
    /// Repetido em toda linha: é o total de entradas do filtro atual.
    ///
    /// Vem de um `count(*) OVER ()` no servidor, então "300 de 17.498" não custa
    /// uma segunda requisição.
    val total: Int = 0,
)

// --------------------------------------------------------------------- fase 2
//
// O que segue foi conferido contra `web/src/api.ts`, que é a outra cópia à mão
// do mesmo contrato e a que está em produção há mais tempo. Não contra o Rust:
// o `odeon-server` é da outra máquina (§1b do `CONTINUAR-ANDROID.md`), e a web
// mora neste repositório justamente por falar com a mesma API.

/// Um arquivo por trás da obra — `MediaFileSummary` na web.
///
/// ## Uma obra pode ter mais de um, e a ficha mostra todos
///
/// A grade já denuncia o caso: `007 Contra Goldfinger (1964)` aparece duas
/// vezes. Podem ser dublagens diferentes, legendagens diferentes, ou duas
/// qualidades — e esconder um deles seria o app decidir sozinho que um arquivo
/// do acervo não existe, que é o §18 ao contrário.
@Serializable
data class ArquivoDeMidia(
    val id: String,
    val filename: String,
    @SerialName("size_bytes") val tamanhoEmBytes: Long? = null,
    val container: String? = null,
    @SerialName("duration_seconds") val duracaoEmSegundos: Double? = null,
    @SerialName("video_codec") val codecDeVideo: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("audio_codec") val codecDeAudio: String? = null,
    @SerialName("audio_channels") val canaisDeAudio: Int? = null,
    @SerialName("subtitle_langs") val idiomasDeLegenda: List<String> = emptyList(),
)

/// `GET /api/works/{id}` — a ficha da obra.
///
/// É **projeção diferente** da listagem, e a web anota isso no mesmo lugar: lá
/// `tags` é lista de texto, aqui é objeto. Por isso não herda de
/// `ItemDaBiblioteca` — herdar faria parecer que os campos batem.
///
/// Os campos que nenhuma tela desenha (`collections`, `relations`, `credits`,
/// `external_ids`) ficam **de fora** de propósito: o `ignoreUnknownKeys` do
/// `Rede` os descarta, e declarar campo que ninguém lê é contrato que ninguém
/// confere.
///
/// `tags` **saiu** dessa lista na R3 do redesenho — a ficha passou a desenhá-las
/// como pílulas, então agora há tela que lê, e o contrato volta a valer a pena.
@Serializable
data class ObraDetalhada(
    val id: String,
    val kind: String,
    val title: String,
    @SerialName("original_title") val tituloOriginal: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    @SerialName("runtime_seconds") val duracaoEmSegundos: Double? = null,
    @SerialName("season_number") val temporada: Int? = null,
    @SerialName("episode_number") val episodio: Int? = null,
    @SerialName("dominant_color") val corDominante: String? = null,
    /// `{poster, backdrop, still}` — caminhos relativos servidos em `/artwork/`.
    val artwork: Map<String, String> = emptyMap(),
    val files: List<ArquivoDeMidia> = emptyList(),
    /// Onde **este** usuário parou, em segundos. `0` se nunca começou.
    @SerialName("position_seconds") val ondeParou: Double = 0.0,
    val finished: Boolean = false,
    val tags: List<Etiqueta> = emptyList(),
)

/// Uma etiqueta da obra — `WorkTag` na web.
///
/// ## `namespace` e `value` são coisas diferentes, e a tela mostra o segundo
///
/// O servidor guarda `genero/Crime`, `pais/Estados Unidos`, `tipo/filme`. A web
/// desenha só o `value`, e é o certo: numa pílula de 11sp, "genero: Crime" gasta
/// o dobro pra dizer o que a palavra "Crime" já diz.
///
/// O `namespace` fica declarado mesmo sem tela que o desenhe porque é ele que
/// permitiria agrupar ou filtrar depois — e porque sem ele o modelo afirmaria
/// que a etiqueta é só um texto solto, que não é.
///
/// ⚠️ `color` é do servidor e pode ser nulo. Quando for, a pílula usa a cor da
/// casa — **nunca** uma cor sorteada. Uma cor inventada por etiqueta pareceria
/// classificação vinda do acervo, que é o §18 na versão mais difícil de notar.
@Serializable
data class Etiqueta(
    val id: String,
    val namespace: String,
    val value: String,
    val color: String? = null,
    val source: String? = null,
)

/// `GET /api/locadora/liberadas` — o §66 em duas linhas.
///
/// ⚠️ `exige = false` vem com `works` **vazia**, e isso não quer dizer "nada
/// liberado": quer dizer que a escassez está desligada e tudo é liberado. A web
/// escreve o porquê de não mandar os 17.498 ids nesse caso — seria meio megabyte
/// pra dizer "sim".
@Serializable
data class Liberadas(
    val exige: Boolean = false,
    val works: List<String> = emptyList(),
)

/// Uma faixa de legenda que o plano ofereceu.
///
/// O `label` vem **pronto do servidor**, e a web deixa recado pra não
/// reimplementar aqui: montar "Português (forçada)" no cliente é a terceira
/// redação da mesma frase, e a terceira que diverge.
@Serializable
data class FaixaDeLegenda(
    val index: Int,
    val origem: String,
    val codec: String,
    val language: String? = null,
    val forced: Boolean = false,
    @SerialName("text_based") val baseadaEmTexto: Boolean = true,
    val label: String,
)

/// `GET /api/playback/{arquivo}/plan` — como este aparelho vai receber o filme.
///
/// ## O `mode` é o selo, e o `reasons` é o porquê
///
/// `direct_play` é o arquivo como está. `direct_stream` remuxa o contêiner sem
/// re-encodar. `transcode` re-encoda — o único que custa CPU no servidor.
///
/// `reasons` vem escrito pelo servidor e é o que a tela mostra quando alguém
/// pergunta "por que está transcodificando?". Ele existe porque a resposta
/// depende do que **este** aparelho declarou saber tocar, e sem isso o selo
/// viraria adivinhação.
@Serializable
data class PlanoDeReproducao(
    val mode: String,
    val video: String,
    val audio: String,
    @SerialName("target_height") val alturaAlvo: Int? = null,
    val reasons: List<String> = emptyList(),
    /// Preenchida só em `direct_play`. Nos outros dois o vídeo vem por HLS, e
    /// quem abre a sessão é o `POST …/session`.
    @SerialName("direct_url") val urlDireta: String? = null,
    val subtitles: List<FaixaDeLegenda> = emptyList(),
) {
    val eDireto: Boolean get() = mode == "direct_play"
}

// -------------------------------------------------------------------- fase 7
//
// "Para você". A espec (§5) chama de «recomendação com motivo, que é a tese do
// projeto numa tela só» — e a tese, na frase do README, é: **não é um catálogo
// de arquivos, é uma biblioteca que te conhece**.

/// Uma recomendação, com o **porquê** dela.
///
/// ## O `reasons` é a metade que importa
///
/// Um `score` sozinho é uma lista ordenada por um número que ninguém vê — e uma
/// lista assim é indistinguível de "os mais recentes". O que separa este app de
/// um catálogo é a frase: *por que este filme, pra mim, agora*.
///
/// Ela vem **pronta do servidor**, que é quem tem o perfil, os vetores e o
/// histórico. Reescrever aqui seria adivinhar o motivo de uma conta que foi
/// feita do outro lado — e é a terceira vez neste app que a regra aparece, junto
/// com o `label` das legendas e o `reasons` do plano.
@Serializable
data class Recomendacao(
    val id: String,
    val title: String,
    val year: Int? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    @SerialName("dominant_color") val corDominante: String? = null,
    @SerialName("media_file_id") val arquivoId: String? = null,
    @SerialName("duration_seconds") val duracaoEmSegundos: Double? = null,
    val score: Double = 0.0,
    val reasons: List<String> = emptyList(),
) {
    /// Só a primeira. A web mostra uma por cartão pelo mesmo motivo: três frases
    /// de justificativa viram parágrafo, e ninguém lê parágrafo escolhendo filme.
    val porque: String? get() = reasons.firstOrNull()
}

/// O perfil de gosto, medido pelo servidor.
@Serializable
data class PerfilDeGosto(
    @SerialName("works_touched") val obrasTocadas: Int = 0,
    val finished: Int = 0,
    @SerialName("has_taste_vector") val temVetor: Boolean = false,
)

/// `GET /api/curation/for-you`
///
/// ⚠️ `cold_start` é a diferença entre "não recomendo nada" e "ainda não te
/// conheço", e a tela **precisa** dizer qual dos dois. Uma lista fraca sem
/// aviso parece um algoritmo ruim; com o aviso, é um convite a assistir mais.
@Serializable
data class ParaVoce(
    val profile: PerfilDeGosto = PerfilDeGosto(),
    val items: List<Recomendacao> = emptyList(),
    @SerialName("cold_start") val aindaNaoTeConhece: Boolean = false,
)

// -------------------------------------------------------------------- fase 5
//
// A locadora. Conferido contra `web/src/api.ts`.

/// As regras da loja, ditas pelo servidor.
///
/// ⚠️ `ultimoAnoVhs` vem daqui e **não** é constante do app. O servidor usa o
/// mesmo número pra decidir se uma caixa rebobina; se os dois divergissem, uma
/// caixa desenhada como VHS recusaria o rebobinar. É o mesmo defeito que a web
/// registrou no §30 — um botão que dizia "ver as 644" e abria 1.424.
@Serializable
data class OpcoesDaLocadora(
    val estoque: Int = 0,
    @SerialName("prazo_dias") val prazoEmDias: Int = 0,
    @SerialName("limite_por_pessoa") val limitePorPessoa: Int = 0,
    val escassez: Boolean = false,
)

/// Uma caixa que está na mão de alguém.
@Serializable
data class Emprestada(
    val id: Int,
    /// O mesmo id que `/api/library` devolve — é por ele que a estante casa.
    @SerialName("caixa_id") val caixaId: String,
    val serie: Boolean = false,
    val titulo: String,
    @SerialName("quem_nome") val quemNome: String,
    val meu: Boolean = false,
    @SerialName("vence_em") val venceEm: String? = null,
    @SerialName("pedido_por_nome") val pedidoPorNome: String? = null,
    /// Se este empréstimo disputa a única cópia. Com a escassez desligada ele é
    /// `false` e a caixa continua exposta pra quem ainda pode pegá-la.
    val exclusivo: Boolean = false,
    /// A arte vem junto de propósito: a rotação semanal pode não expor a caixa
    /// que alguém levou, e **uma caixa invisível não tem como ser pedida de
    /// volta**.
    val poster: String? = null,
    @SerialName("dominant_color") val corDominante: String? = null,
    val ano: Int? = null,
)

@Serializable
data class Devolvida(
    @SerialName("caixa_id") val caixaId: String,
    val titulo: String,
    @SerialName("quem_nome") val quemNome: String,
    @SerialName("devolvido_como") val devolvidoComo: String? = null,
)

/// `GET /api/locadora/prateleira` — o que está fora da estante.
///
/// Ela **não** devolve o estado das 746 caixas: devolve as poucas que estão em
/// mãos. Quem cruza com a estante é a tela.
@Serializable
data class Prateleira(
    val opcoes: OpcoesDaLocadora = OpcoesDaLocadora(),
    val emprestadas: List<Emprestada> = emptyList(),
    val devolvidas: List<Devolvida> = emptyList(),
    @SerialName("posso_pegar") val possoPegar: Int = 0,
    @SerialName("ultimo_ano_vhs") val ultimoAnoVhs: Int = 0,
)

/// `POST /api/locadora/alugar` — pegar a fita.
///
/// ⚠️ **Escreve no acervo de todo mundo.** O §11 do `CONTINUAR-ANDROID.md` é
/// explícito: pegar uma fita emprestada é escrita em produção, e testar isso
/// pede conta descartável. Não é chamada em nenhum teste.
@Serializable
data class AlvoDaCaixa(@SerialName("work_id") val obraId: String)

@Serializable
data class RespostaDoAluguel(
    val id: Int = 0,
    val titulo: String = "",
    @SerialName("vence_em_dias") val venceEmDias: Int = 0,
)

/// `GET /api/continue` — de onde continuar.
///
/// ## Ela é a tese do projeto numa rota só
///
/// A espec (§5) põe "continuar de onde parou" como fase 3 e diz por quê: «é o
/// que o celular faz melhor que tudo — você parou na TV e continua no ônibus».
/// Quem sabe onde cada pessoa parou é o servidor, e ele já sabe: é o outro lado
/// do `POST …/progress` que a fase 2 passou a mandar.
///
/// ## A arte preferida aqui não é o pôster
///
/// `still` é o quadro **daquele episódio**, `backdrop` é a arte larga da obra, e
/// `poster` é a vertical da grade. Numa fileira de "continuar", a mais
/// específica ganha: quem parou no meio de um episódio reconhece o quadro dele
/// antes de reconhecer a capa da série.
@Serializable
data class ItemPraContinuar(
    val id: String,
    val title: String,
    val year: Int? = null,
    @SerialName("series_title") val tituloDaSerie: String? = null,
    @SerialName("season_number") val temporada: Int? = null,
    @SerialName("episode_number") val episodio: Int? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val still: String? = null,
    @SerialName("dominant_color") val corDominante: String? = null,
    @SerialName("media_file_id") val arquivoId: String? = null,
    @SerialName("duration_seconds") val duracaoEmSegundos: Double? = null,
    @SerialName("position_seconds") val ondeParou: Double? = null,
    val finished: Boolean? = null,
) {
    /// A arte da fileira, da mais específica pra menos. `null` quando não há
    /// nenhuma — e aí o cartão mostra o título sobre a cor da obra, como a
    /// grade faz.
    val arte: String? get() = still ?: backdrop ?: poster

    /// Quanto do filme já passou, de 0 a 1. `null` quando não dá pra saber, e
    /// aí a barrinha **não aparece** em vez de aparecer zerada (§24).
    val fracaoVista: Float?
        get() {
            val onde = ondeParou ?: return null
            val total = duracaoEmSegundos?.takeIf { it > 0 } ?: return null
            return (onde / total).toFloat().coerceIn(0f, 1f)
        }
}

/// `POST /api/works/{obra}/progress` — onde eu parei.
///
/// ## O `device_id` não é enfeite
///
/// A tese do projeto é continuar na TV o que começou no ônibus, e pra isso o
/// servidor precisa distinguir de onde veio cada marca. A web manda um id de
/// aparelho gerado por ela; aqui manda-se o mesmo formato, com o valor do
/// `Cofre` — ele nasce uma vez e vive enquanto o app estiver instalado.
@Serializable
data class MarcaDeProgresso(
    @SerialName("position_seconds") val posicaoEmSegundos: Double,
    @SerialName("duration_seconds") val duracaoEmSegundos: Double? = null,
    @SerialName("media_file_id") val arquivoId: String? = null,
    @SerialName("event_type") val tipo: String? = null,
    val client: String = "android",
    @SerialName("device_id") val aparelhoId: String? = null,
)

@Serializable
data class RespostaDeProgresso(
    val ok: Boolean = false,
    val finished: Boolean = false,
)

/// `GET /api/media/{arquivo}/scrub` — a folha de sprites do preview de seek.
///
/// ## Uma imagem por arquivo, e nenhuma requisição ao arrastar
///
/// O servidor gera uma grade de miniaturas espaçadas de `intervalSegundos`. O
/// player baixa **uma vez** e recorta o quadro certo enquanto o dedo anda — que
/// é o que faz o arrasto não pedir nada à rede.
///
/// ⚠️ Esta rota **exige header** e não aceita `?token=` na query. A web registra
/// o defeito que isso já causou: sem o header ela devolvia 401, o cliente lia
/// "não deu certo" e concluía "não há sprite" — em silêncio, pra todo arquivo. E
/// os sprites que existiam no banco nunca apareceram.
///
/// Aqui o header vem do `InterceptorDeSessao`, automático. Mas a lição vale pro
/// tratamento: **só o 404 vira "não há"**. Qualquer outro código é erro de
/// verdade, e mascará-lo é repetir o defeito.
@Serializable
data class FolhaDeSprites(
    @SerialName("media_file_id") val arquivoId: String,
    val path: String,
    @SerialName("interval_seconds") val intervaloSegundos: Double,
    val columns: Int,
    val rows: Int,
    @SerialName("thumb_width") val larguraDaMiniatura: Int,
    @SerialName("thumb_height") val alturaDaMiniatura: Int,
    @SerialName("frame_count") val quantosQuadros: Int,
)

/// `POST /api/playback/{arquivo}/session` — a sessão de HLS, quando não é direto.
@Serializable
data class SessaoDeTranscodificacao(
    val id: String,
    val mode: String,
    val reasons: List<String> = emptyList(),
    @SerialName("playlist_url") val urlDaPlaylist: String,
)
