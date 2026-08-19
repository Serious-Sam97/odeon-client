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

    /// As versões deste filme — os rips que o servidor agrupou numa entrada só.
    ///
    /// ## ⚠️ Ela chega **vazia** no caso normal, e isso é o contrato
    ///
    /// O servidor **omite a chave** quando o filme tem uma versão só, que é o
    /// caso de 8.230 das 8.273 entradas. Mandá-la com um item em toda linha seria
    /// peso por nada — e faz a regra da tela ser a mais simples que existe: *há
    /// versões, há escolha*.
    ///
    /// São **43 grupos no acervo inteiro** (medido pelo servidor em 14/08/2026:
    /// 8.316 entradas viraram 8.273). O improvement é pequeno em alcance e
    /// grande em incômodo — dois dos 43 são os 007 que o dono assiste.
    ///
    /// ⚠️ A chave do agrupamento é `external_ids->>'tmdb'`: a **identificação**,
    /// e nunca título+ano. Só `kind='movie'`, e só o que já foi identificado —
    /// dois `unmatched` não têm chave, e chutar neles seria o §18 ao contrário.
    /// **Série não ganha versões**: o tmdb é compartilhado pelos episódios, e
    /// agrupar por ele juntaria a série inteira num cartão.
    ///
    /// ⚠️ E `?versions=flat` devolve as 8.316 sem agrupar. A revisão do acervo
    /// precisa enxergar rip por rip, e agrupamento sem escape esconde
    /// exatamente de quem precisa ver.
    @SerialName("versions") val versoes: List<VersaoDaObra> = emptyList(),

    /// Repetido em toda linha: é o total de entradas do filtro atual.
    ///
    /// Vem de um `count(*) OVER ()` no servidor, então "300 de 17.498" não custa
    /// uma segunda requisição.
    ///
    /// ⚠️ **Ele conta grupos desde 14/08/2026**, e não rips. Foi pedido assim de
    /// propósito: o cabeçalho da grade escreve `carregadas / total` e a
    /// paginação para em `quantosNaTela < total`. Se o total contasse 8.316
    /// enquanto a grade desenha 8.273 cartões, os dois números passariam a falar
    /// de coisas diferentes e a grade pediria uma página que não existe.
    val total: Int = 0,
) {
    /// As versões entre as quais dá pra escolher de verdade.
    ///
    /// ⚠️ **Versão sem `id` é descartada aqui**, e não mais adiante: sem ele a
    /// modal não tem pra onde mandar ninguém. Ver `VersaoDaObra.id` pro porquê
    /// de o campo ser tolerante em vez de obrigatório.
    val versoesEscolhiveis: List<VersaoDaObra> get() = versoes.filter { it.id.isNotBlank() }

    /// Há escolha a fazer?
    ///
    /// ⚠️ **Uma versão só não abre modal.** Um menu com uma opção é uma pergunta
    /// que não tem resposta alternativa — é o §24 aplicado a um menu, e é a mesma
    /// régua do `estado.faixasDeAudio.size > 1` no menu de faixas do player.
    val temEscolhaDeVersao: Boolean get() = versoesEscolhiveis.size > 1
}

/// Uma versão de um filme — um dos rips que o servidor agrupou.
///
/// ## Por que ela existe
///
/// O dono baixou o mesmo filme duas vezes, um em pt-BR e outro em inglês, porque
/// não achou dual audio. A grade mostrava os dois cartões, e a escolha entre eles
/// é de **idioma** — que era justamente o que a listagem não mandava. O pedido
/// inteiro está no §2.1 do `docs/PEDIDOS-AO-SERVIDOR.md`.
///
/// ## ⚠️ Ela **não** é o mesmo que [ArquivoDeMidia], e a diferença é o produto
///
/// `ArquivoDeMidia` é um arquivo dentro de **uma** obra, e a ficha já sabe
/// escolher entre eles (`ModeloDaObra.escolherArquivo`). Esta aqui é uma **obra
/// inteira**: id próprio, progresso próprio, ficha própria.
///
/// Confundir as duas é o caminho mais curto pra **fundir** o que só devia ser
/// **agrupado** — e fundir apagaria o `position_seconds` de uma delas, que é
/// exatamente a objeção que segurou este pedido desde 04/08/2026.
@Serializable
data class VersaoDaObra(
    /// O id da **obra**, e é por ele que a ficha abre.
    ///
    /// ⚠️ Tem `""` como padrão pelo mesmo motivo do `label` da [FaixaDeAudio], e
    /// aqui o preço de errar é maior: campo obrigatório transformaria uma
    /// renomeação de JSON em **biblioteca que não carrega**. Com o padrão, a
    /// versão sem id cai fora em `versoesEscolhiveis`, a modal não abre, e o
    /// cartão volta a abrir a obra representante — o comportamento de antes deste
    /// improvement. Degrada, não morre.
    val id: String = "",
    @SerialName("media_file_id") val arquivoId: String? = null,
    val height: Int? = null,
    @SerialName("size_bytes") val tamanhoEmBytes: Long? = null,
    @SerialName("duration_seconds") val duracaoEmSegundos: Double? = null,

    /// Os idiomas do áudio, em ISO 639 — `["por"]`.
    ///
    /// ## ⚠️ Ela vem **vazia** quando o arquivo não declara, e é o caso do dono
    ///
    /// O 007 em inglês deste acervo sai com `[]`: a faixa aac dele não traz
    /// idioma no contêiner. O servidor **recusa** mandar `und` — que em ISO 639 é
    /// *undetermined*, o contêiner dizendo que não sabe — e a recusa é a certa:
    /// escrever «und» como idioma faria a modal oferecer «und» como escolha.
    ///
    /// É a mesma leitura que o `rotuloDaFaixa` do player já fazia desde
    /// 06/08/2026, agora feita do lado de lá.
    ///
    /// ⚠️ **A consequência é de produto, e nenhum código daqui a resolve**: a
    /// modal consegue escrever «Português» e **não** consegue escrever «Inglês».
    /// Nomear o que não foi declarado é inventar metadado. Quem conserta é o
    /// acervo — aquele arquivo precisa declarar o idioma da faixa.
    @SerialName("audio_langs") val idiomasDeAudio: List<String> = emptyList(),

    /// Onde **este** usuário parou nesta versão.
    ///
    /// ⚠️ É o campo que salva a modal. Com um dos lados sem idioma declarado,
    /// «parou em 1:22» é o que de fato distingue as duas pra quem está
    /// escolhendo — e é a única linha da tela que responde «qual delas eu estava
    /// vendo». Sem ele a modal seria `818p` contra `816p`, que é uma escolha
    /// entre dois números com dois pixels de diferença.
    @SerialName("position_seconds") val ondeParou: Double? = null,

    val finished: Boolean = false,
)

/// Uma obra **plana** — `WorkListItem` na web, e o que `/api/works` devolve.
///
/// ## Ela é o episódio, e a `ItemDaBiblioteca` é a série
///
/// As duas rotas existem porque a pergunta é outra em cada lugar. Fora de
/// coleção, «o que tem no acervo» tem que responder *Breaking Bad*, e não 62
/// linhas de *Breaking Bad*. Dentro dela, a pergunta virou «quais episódios», e
/// aí o agrupamento seria a resposta errada.
///
/// ⚠️ **Ela não traz `total`.** A `ItemDaBiblioteca` traz, porque o servidor põe
/// um `count(*) OVER ()` em toda linha da listagem agrupada; aqui não há. O
/// denominador de dentro da série vem do `work_count` da própria série, que a
/// grade já tinha na mão quando alguém tocou nela — ver `ModeloDaBiblioteca`.
@Serializable
data class ObraDaLista(
    val id: String,
    val kind: String = "",
    val title: String,
    val year: Int? = null,
    @SerialName("season_number") val temporada: Int? = null,
    @SerialName("episode_number") val episodio: Int? = null,
    /// A sinopse do episódio — chegou em 18/08/2026, e era o campo que a lista de
    /// temporada mais sentia falta.
    ///
    /// ⚠️ Existe em **7.628 dos 14.844** episódios. Quem não tem manda nulo, e
    /// aí a linha simplesmente não é desenhada — não se inventa sinopse a partir
    /// do título (§18).
    val overview: String? = null,
    @SerialName("match_state") val estadoDaIdentificacao: String? = null,
    @SerialName("dominant_color") val corDominante: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    /// O quadro **deste** episódio. É o que o cartão usa — com o pôster da
    /// série, 21 episódios viram 21 cópias da mesma imagem (§4 da referência).
    val still: String? = null,
    @SerialName("series_title") val tituloDaSerie: String? = null,
    @SerialName("media_file_id") val arquivoId: String? = null,
    @SerialName("duration_seconds") val duracaoEmSegundos: Double? = null,
    val height: Int? = null,
    @SerialName("size_bytes") val tamanhoEmBytes: Long? = null,
    @SerialName("position_seconds") val ondeParou: Double? = null,
    val finished: Boolean? = null,
) {
    /// A arte do cartão, da mais específica pra menos — a mesma régua do
    /// `ItemPraContinuar`.
    val arte: String? get() = still ?: backdrop ?: poster

    /// `S02E05`, ou `null` quando não há numeração — e aí o cartão não escreve
    /// nada no lugar (§24). Episódio sem número existe: é o que a revisão ainda
    /// não numerou.
    val codigo: String?
        get() = when {
            temporada != null && episodio != null ->
                "S%02dE%02d".format(temporada, episodio)
            episodio != null -> "ep $episodio"
            else -> null
        }
}

/// Uma etiqueta do acervo, com quantas obras ela tem — `Tag` na web.
///
/// ⚠️ **`work_count` conta só o que foi identificado**, e a web escreve o porquê
/// no `FilterBar.tsx`: os contadores filtram pela tag `format:`, que só a
/// identificação escreve. Com 4.415 obras `unmatched` hoje, a soma dos chips
/// **não** dá o tamanho do acervo — e a web pendura um atalho «N sem
/// identificar →» no grupo `format` justamente pra a diferença não parecer
/// defeito. O app não tem tela de revisão pra onde mandar, então não oferece o
/// atalho (§53) — mas o número continua sendo o que é, e quem ler esta linha
/// sabe disso.
@Serializable
data class EtiquetaDoAcervo(
    val id: String = "",
    val namespace: String = "",
    val value: String = "",
    val color: String? = null,
    @SerialName("work_count") val quantasObras: Int = 0,
) {
    /// `genre:Terror` — a chave que viaja pro servidor e identifica o chip.
    val chave: String get() = "$namespace:$value"
}

/// O grupo de etiquetas: `genre` vira «Gênero», e a ordem é do servidor.
@Serializable
data class EspacoDeEtiqueta(
    val namespace: String = "",
    val label: String = "",
    val color: String? = null,
    val position: Int = 0,
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
    /// As coleções a que esta obra pertence — a **temporada**, quando é
    /// episódio, e por ela a série (o `parent_id` da temporada).
    ///
    /// ⚠️ O servidor sempre mandou isto; o modelo é que descartava, e é por
    /// causa dele que «quando um episódio acaba, acaba» era verdade nos quatro
    /// clientes. Com a temporada na mão, o próximo episódio é uma consulta que
    /// a casa já sabe fazer (`obras(colecao = …)`).
    val collections: List<Colecao> = emptyList(),
)

/// De onde continuar — ou **zero**, quando não há de onde.
///
/// ## ⚠️ O defeito que ela conserta deixava o filme impossível de reabrir
///
/// > «tentei começar o família de aluguel que tu tinha terminado, o filme abre
/// > no final dele e dps aparece essa mensagem»
///
/// `position_seconds` de um filme visto até o fim **é** o fim, e o app retomava
/// lá. Em `direct_play` isso já era esquisito — dois segundos de crédito e
/// acabou. Em HLS era impeditivo: a sessão nascia com quase nada pela frente, e
/// o primeiro segmento faltando derrubava a reprodução com «este trecho não está
/// na sessão de transcodificação». Um filme terminado ficava, na prática,
/// impossível de reassistir.
///
/// ## As três condições, e de onde cada uma vem
///
/// A forma é a do `Details.tsx` da web:
///
/// ```js
/// const retomando = work.position_seconds > 30 && !work.finished && restam > 60;
/// ```
///
/// | | por quê |
/// |---|---|
/// | `> 5` | **decisão do dono, 06/08/2026** — ver abaixo |
/// | `!finished` | o veredito é do **servidor**, e é o mesmo que tira a obra da fileira de continuar |
/// | `restam > 60` | pega o filme parado a 99% que o servidor ainda não marcou |
///
/// ## ⚠️ O piso era 30s, como o da web, e o dono o derrubou
///
/// > «Ao iniciar um filme a pessoa pode assistir um teco e voltar, isso já deve
/// > salvar o progresso dela.»
///
/// Com 30s, um teco de quinze segundos era salvo no servidor e **ignorado pelo
/// botão**: a ficha dizia `assistir` e recomeçava do zero. A regra dele é que
/// teco conta. Os 5s que sobram separam só o toque acidental — abrir e fechar —
/// de assistir de verdade, e retomar aos 4s é indistinguível de começar do zero
/// de qualquer jeito.
///
/// ⚠️ **Isso diverge da web e do `/api/continue` de propósito**, e fica
/// registrado: a fileira de continuar do servidor ainda considera "começou" a
/// partir de 30s, então um teco de 15s retoma pela ficha mas não aparece na
/// fileira. Alinhar os dois é pedido pro `serious-server`.
fun ondeContinuar(ondeParou: Double, duracaoEmSegundos: Double?, finished: Boolean): Double {
    if (finished) return 0.0
    if (ondeParou <= 5) return 0.0
    /// Sem duração não dá pra saber quanto falta — e aí a decisão é retomar, que
    /// é o que o app fazia antes desta função existir. Recusar por falta de
    /// informação jogaria fora a posição de quem está no meio de um filme cuja
    /// duração o servidor não mediu.
    ///
    /// ⚠️ **E `0` conta como ausente**, o que o teste cobrou: `duration_seconds`
    /// chega zerado em arquivo sem probe guardada, e sem o `takeIf` a conta vira
    /// `0 − 3401 ≤ 60` — verdadeira — mandando pro começo **todo** filme de
    /// arquivo não medido. É a mesma leitura que o `duracaoConhecidaMs` do player
    /// já faz: zero ali é "o servidor não sabe", nunca "o filme dura zero".
    val total = duracaoEmSegundos?.takeIf { it > 0 } ?: return ondeParou
    if (total - ondeParou <= 60) return 0.0
    return ondeParou
}

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
) {
    /// O qualificador em português — `country` vira «país».
    ///
    /// ## ⚠️ Ele existe porque a tela estava mostrando **chave de banco**
    ///
    /// Visto na ficha de «Sr. Ninguém»: nove pílulas, e quatro delas começando
    /// com a palavra `country`. Também `format filme`, `genre Drama`,
    /// `lang inglês`. O desenho da [dev.odeon.android.ui.PilulaDeEtiqueta] está
    /// certo e vem da web — o namespace apagado diz *de que tipo* é a etiqueta —,
    /// mas ele assumia que o namespace chegava em português, como chegava quando
    /// aquela folha foi escrita (`genero/Crime`, `pais/Estados Unidos`).
    ///
    /// Não chega mais. E dá pra ver de onde vem: no painel de filtros o servidor
    /// manda `FORMATO` e `GÊNERO` traduzidos, e **`COUNTRY` cru** — ele tem
    /// rótulo pra uns namespaces e não pra outros. A ficha, que não recebe esses
    /// rótulos, mostrava a chave em todos.
    ///
    /// Traduzir código em nome é **desenho**, e mora no cliente por decisão já
    /// escrita — ver [dev.odeon.android.ui.nomeDoIdioma] e `Revista.rotuloDoEixo`,
    /// que existem pelo mesmo motivo.
    ///
    /// ⚠️ Namespace desconhecido devolve `null`, e a pílula **omite o
    /// qualificador** em vez de imprimir a chave. Mostrar `collection` numa
    /// pílula é mostrar à pessoa um nome de coluna com cara de categoria (§18) —
    /// e o valor sozinho continua legível, que é o pior caso aceitável.
    val rotulo: String?
        get() = when (namespace.lowercase()) {
            "country", "pais", "país" -> "país"
            "genre", "genero", "gênero" -> "gênero"
            "format", "formato", "tipo" -> "formato"
            "lang", "language", "idioma" -> "idioma"
            "decade", "decada", "década" -> "década"
            "director", "diretor" -> "direção"
            "studio", "estudio", "estúdio" -> "estúdio"
            "collection", "saga" -> "saga"
            else -> null
        }
}

/// Uma caixa **exposta na vitrine** — `CaixaExposta` na web.
///
/// Diferente da [Emprestada]: aquela é uma fita que saiu da estante; esta é uma
/// que está **nela**, à mostra, esperando ser pega.
@Serializable
data class CaixaExposta(
    val id: String,
    val serie: Boolean = false,
    val titulo: String,
    val ano: Int? = null,
    val poster: String? = null,
    @SerialName("dominant_color") val corDominante: String? = null,
    val temporadas: Int = 0,
    @SerialName("media_file_id") val arquivoId: String? = null,
    @SerialName("position_seconds") val ondeParou: Double? = null,
    val estante: Int = 0,
    val total: Int = 0,
)

/// Uma estante, com nome e placa.
@Serializable
data class EstanteExposta(
    val nome: String,
    /// ⚠️ Quantas caixas esta estante tem **no acervo**, e não quantas estão à
    /// vista. É o que faz a placa dizer "16 de 113" em vez de "16" — e o
    /// comentário da web insiste nisso porque a diferença é a promessa: a
    /// vitrine é uma amostra, não o estoque.
    val total: Int = 0,
    val caixas: List<CaixaExposta> = emptyList(),
)

/// `GET /api/locadora/estantes` — a **loja**, e ela é uma vitrine que gira.
///
/// ## O app não pedia esta rota, e ela é a locadora inteira
///
/// Até aqui a tela da locadora mostrava só o que **saiu** da estante — os seus
/// empréstimos e os dos outros. O acervo exposto, que é a loja em si, nunca foi
/// pedido: `/api/locadora/estantes` existe no `web/src/api.ts:1783` e nenhuma
/// linha deste app a chamava.
///
/// É o mesmo padrão de `height`, `size_bytes` e `tags`: o servidor já dava, e o
/// cliente não pegava. A diferença é o tamanho — aqui não era um campo, era uma
/// tela.
///
/// ## `vira_em` é o que torna a vitrine promessa
///
/// O comentário da web é a explicação: «quando a vitrine vira. É o que torna a
/// rotação **promessa, não sorteio**». Uma seleção que muda sem data anunciada é
/// aleatoriedade; com data, é programação — e é a diferença entre um acervo
/// embaralhado e uma locadora que troca a vitrine na segunda.
@Serializable
data class Loja(
    val estantes: List<EstanteExposta> = emptyList(),
    @SerialName("no_acervo") val noAcervo: Int = 0,
    @SerialName("semana_de") val semanaDe: String? = null,
    @SerialName("vira_em") val viraEm: String? = null,
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
/// Uma faixa de áudio que o **arquivo** tem — e não necessariamente a playlist.
///
/// ## ⚠️ Ela existe porque perguntar ao player sempre responde «uma»
///
/// O app tentou primeiro ler as faixas do `Player.currentTracks`, e a medida de
/// 06/08/2026 mostrou por que isso não serve: em *Família de Aluguel*
/// (`ac3:por | aac:eng`) o menu abria com **uma** faixa, `pt`. O player oferece o
/// que está na playlist, e em transcodificação o `ffmpeg` do servidor põe uma só.
///
/// Ou seja: o dual audio sumia exatamente nos arquivos que o têm — porque o
/// PT-BR ser ac3/eac3 é o que **força** a transcodificação neste cliente. O
/// servidor mediu **3.469 arquivos** do acervo com duas ou mais faixas, e o
/// formato recorrente é esse.
///
/// Agora a lista vem do `plan`, tirada da probe do arquivo. É a única fonte que
/// sabe o que existe antes de alguém decidir o que entra na playlist.
///
/// O `label` vem **pronto do servidor**, pela mesma regra do `FaixaDeLegenda` e
/// do `reasons`: montar «Português - AC3 5.1» aqui seria a terceira redação da
/// mesma frase entre web, Android e servidor.
@Serializable
data class FaixaDeAudio(
    /// Relativo ao **áudio**, não ao arquivo: é o `N` de `-map 0:a:N`. Índice
    /// inválido o servidor recusa com 400, e é decisão registrada dele — uma
    /// sessão muda seria errada e invisível ao mesmo tempo.
    val index: Int,
    val codec: String = "",
    val language: String? = null,
    val title: String? = null,
    val channels: Int? = null,
    /// ⚠️ Tem `""` como padrão de propósito, e não é frouxidão: se o servidor
    /// mudar o nome deste campo, o plano continua parseando e a tela cai no
    /// rótulo posicional. A alternativa — campo obrigatório — transformaria uma
    /// renomeação de JSON em **filme que não abre**.
    val label: String = "",
)

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
    /// Todas as faixas de áudio do arquivo. Vazia em arquivo sem probe guardada
    /// — e o servidor trata esse caso caindo pro codec do banco, com teste.
    @SerialName("audio_tracks") val faixasDeAudio: List<FaixaDeAudio> = emptyList(),
    /// A duração do arquivo, **medida pelo servidor** — 17/08/2026.
    ///
    /// ## ⚠️ Ela é a resposta ao pedido da playlist `VOD`, e é melhor que ele
    ///
    /// O pedido era declarar a playlist como `VOD` pra a barra nascer certa. A
    /// resposta do servidor recusou com medida: declarar `VOD` exige saber o
    /// tamanho de cada segmento antes de produzi-lo, e no caminho `video=copy`
    /// isso são os keyframes da fonte — **1m33s por arquivo** contra 25s de
    /// espera da playlist. Metade dos filmes ficaria certa e metade errada, sem
    /// ninguém saber qual.
    ///
    /// Em vez disso veio o número, aqui e na sessão. É o suficiente: quem desenha
    /// a barra e o «faltam» é o cliente, e o que faltava era só o denominador.
    @SerialName("duration_seconds") val duracaoEmSegundos: Double? = null,
    /// **Qual** faixa este plano está falando. Sem pedido, o servidor usa a 0 —
    /// e não a marcada `default`, que mudaria em silêncio o que toca em milhares
    /// de arquivos que ninguém pediu pra mudar.
    ///
    /// ⚠️ E é isto que faz o selo não mentir: o `mode` passou a ser decidido pelo
    /// codec **da faixa escolhida**, não pelo primeiro áudio do arquivo. Num
    /// `ac3:por | aac:eng`, pedir a faixa 1 remove o único motivo de
    /// transcodificar — e o veredito vira `direct_play` de verdade.
    @SerialName("audio_track") val faixaDeAudio: Int? = null,
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
    /// **Todos** os ids que abrem esta caixa — 17/08/2026.
    ///
    /// ## ⚠️ Ela é a peça que devolve o «pegar a fita» ao app
    ///
    /// O botão não existia porque o 403 era imprevisível, e o §53 proíbe oferecer
    /// o que a validação vai negar. A causa do 403 saiu na investigação do
    /// servidor, e não era permissão: **o mesmo filme existe duas vezes** neste
    /// acervo (44 casos). Desde a R47 a biblioteca desenha **um** cartão pro
    /// grupo; a locadora trancava por `work_id`. Daí as duas metades: a
    /// prateleira dizia o id do rip que estava fora e o cartão conhecia o outro,
    /// então o app não tinha como prever — e a escassez não escasseava, porque
    /// duas pessoas podiam levar «o mesmo filme».
    ///
    /// Com a lista, a conta fica trivial: se o id que estou olhando está em
    /// `caixaIds` de algum empréstimo, aquela caixa **está fora** — e o `meu` diz
    /// se está comigo. Ver `EstadoDaLocadora.situacaoDaCaixa`.
    @SerialName("caixa_ids") val caixaIds: List<String> = emptyList(),
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
    @SerialName("devolvido_em") val devolvidoEm: String? = null,
    @SerialName("devolvido_como") val devolvidoComo: String? = null,
    /// `membro` — devolveu; `prazo` — **venceu e voltou sozinha**.
    ///
    /// A diferença muda a frase inteira: uma é ação («fulano devolveu»), a
    /// outra é o relógio («venceu na mão de fulano»). Dizer «devolveu» sobre uma
    /// fita que o prazo trouxe de volta seria dar crédito por algo que não
    /// aconteceu.
    @SerialName("devolvido_por") val devolvidoPor: String = "membro",
    val atrasada: Boolean = false,
)

/// Alguém que frequenta a loja — o chip do balcão.
///
/// ## A fama sobrevive à devolução, e é o item inteiro
///
/// O comentário da web é a regra: «*as pessoas saberem quem devolveu zoado* não
/// funciona se o número só existir enquanto a pessoa está com alguma coisa na
/// mão — a fama tem que sobreviver à devolução, senão ninguém carrega nada».
///
/// Por isso o chip aparece pra quem tem fita **ou** pra quem tem fama, e some só
/// quando as quatro contagens são zero.
///
/// ⚠️ **São as pessoas do servidor**, não de um grupo: com estoque único, quem
/// te barra pode ser qualquer morador.
@Serializable
data class PessoaNaLoja(
    val id: String = "",
    @SerialName("display_name") val nome: String = "",
    @SerialName("na_mao") val naMao: Int = 0,
    /// Quantas fitas dela **alguém teve que rebobinar**. Cada unidade é uma vez
    /// em que outra pessoa gastou os segundos por causa dela.
    val zoadas: Int = 0,
    /// E quantas ela rebobinou dos outros. ⚠️ **O outro lado precisa existir**:
    /// um placar que só conta o defeito faz de todo mundo réu.
    val rebobinou: Int = 0,
    /// Fitas que ela deixou no meio **agora**. Estado, não histórico — some no
    /// instante em que alguém rebobina, e é a única das três que dá pra
    /// consertar sozinha.
    @SerialName("no_meio") val noMeio: Int = 0,
) {
    /// Aparece no balcão? Quem não tem fita nem fama não é notícia (§24).
    val temOQueDizer: Boolean get() = naMao > 0 || zoadas > 0 || rebobinou > 0 || noMeio > 0
}

/// `GET /api/locadora/prateleira` — o que está fora da estante.
///
/// Ela **não** devolve o estado das 746 caixas: devolve as poucas que estão em
/// mãos. Quem cruza com a estante é a tela.
@Serializable
data class Prateleira(
    val opcoes: OpcoesDaLocadora = OpcoesDaLocadora(),
    /// Quem frequenta a loja — os chips do balcão. Chegava na resposta desde
    /// sempre e ninguém desenhava; era a quinta dívida do §8 do
    /// `PARIDADE-ANDROID.md`.
    val pessoas: List<PessoaNaLoja> = emptyList(),
    val emprestadas: List<Emprestada> = emptyList(),
    val devolvidas: List<Devolvida> = emptyList(),
    @SerialName("posso_pegar") val possoPegar: Int = 0,
    @SerialName("ultimo_ano_vhs") val ultimoAnoVhs: Int = 0,
)

/// Um capítulo do disco.
@Serializable
data class Capitulo(
    val inicio: Double = 0.0,
    val fim: Double = 0.0,
    /// ⚠️ `null` quando o "título" é vazio, `Chapter 01` ou o próprio timecode —
    /// **o caso de 98,4% dos filmes deste acervo**, medido pela web. Exibir um
    /// timecode como nome de capítulo seria mentir com cara de metadado (§18).
    val titulo: String? = null,
)

/// Uma cena — a miniatura de um ponto do filme.
@Serializable
data class Cena(
    val segundos: Double = 0.0,
    val imagem: String = "",
    /// `capitulo` quando o disco disse onde a cena começa, `regular` quando foi
    /// o relógio que dividiu. A tela usa isto pra escrever a legenda certa, não
    /// pra mudar o desenho — e é a diferença entre «nos cortes do disco» e
    /// «divididos pelo relógio».
    val origem: String = "regular",
)

/// `GET /api/works/{obra}/menu` — o menu do disco.
///
/// ## Ele existe porque um DVD **não é um arquivo**
///
/// A biblioteca toca; o disco tem menu. É a diferença que a locadora inteira
/// existe pra encenar, e é por isso que este menu abre **só por ela** e **só em
/// DVD** (§14.4): a fita não tem menu, tem rebobinar.
@Serializable
data class MenuDoDisco(
    @SerialName("work_id") val obraId: String = "",
    @SerialName("media_file_id") val arquivoId: String = "",
    val titulo: String = "",
    val ano: Int? = null,
    val cor: String? = null,
    val backdrop: String? = null,
    val duracao: Double? = null,
    /// `null` quando não há de onde continuar — e aí **o item nem existe** no
    /// menu (§24). Um «continuar» que começa do zero é um «do começo» com outro
    /// nome.
    val posicao: Double? = null,
    val terminado: Boolean = false,
    val capitulos: List<Capitulo> = emptyList(),
    /// Idiomas distintos, na ordem das faixas do disco.
    val legendas: List<String> = emptyList(),
    /// Onde a cena de fundo começa. **Sorteada a cada abertura**, no miolo do
    /// filme — abrir o mesmo disco duas vezes dá dois planos diferentes.
    @SerialName("cena_de_fundo") val cenaDeFundo: Double = 0.0,
    /// O clima: o índice da estante que reivindicaria este filme na locadora.
    ///
    /// ⚠️ **O índice é o contrato.** Ele é a posição na lista `ESTANTES` do
    /// servidor, e a tabela de climas do app é indexada por ele — mexer na ordem
    /// de lá sem mexer aqui troca o clima de todo mundo.
    val clima: Int = 11,
    @SerialName("clima_nome") val climaNome: String = "",
) {
    /// `1:23:45` de onde parou, pro rótulo do «continuar».
    val ponteiro: String
        get() {
            val total = (posicao ?: 0.0).toLong()
            return "%d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
        }

    /// Dá pra continuar? Só com posição, não terminado, e passando de um minuto
    /// — a mesma régua da ficha.
    val temComoContinuar: Boolean
        get() = !terminado && (posicao ?: 0.0) > 60
}

/// `GET /api/locadora/fita/{obra}` — onde esta fita parou, e quem a deixou assim.
///
/// ## É o que torna a locadora um lugar com outras pessoas dentro
///
/// O `minha` é o campo que decide tudo, e o comentário da web é a regra inteira:
/// «pausar o próprio filme e voltar é continuar; encontrar a fita de outra
/// pessoa no minuto 47 é outra coisa».
///
/// ⚠️ `vhs` vem do servidor, do corte `ultimo_ano_vhs`. **DVD não rebobina** —
/// ele lembra onde parou (§35), e oferecer o gesto nele seria a mesma família do
/// botão que dizia «ver a série» numa obra avulsa.
@Serializable
data class Fita(
    @SerialName("posicao_segundos") val posicaoEmSegundos: Double = 0.0,
    @SerialName("duracao_segundos") val duracaoEmSegundos: Double? = null,
    /// Quem deixou assim. `null` quando ninguém tocou — ou quando a conta sumiu,
    /// porque a fita sobrevive ao dono.
    @SerialName("deixada_por") val deixadaPor: String? = null,
    @SerialName("deixada_em") val deixadaEm: String? = null,
    val minha: Boolean = false,
    val vhs: Boolean = false,
) {
    /// Quanto da fita já rodou, de 0 a 1. `0` quando não se sabe a duração — e
    /// aí o carretel desenha uma fita no começo, que é o que ela provavelmente
    /// é (§18: sem dado, não invente um meio).
    val andado: Float
        get() {
            val duracao = duracaoEmSegundos?.takeIf { it > 0 } ?: return 0f
            return (posicaoEmSegundos / duracao).toFloat().coerceIn(0f, 1f)
        }

    /// Precisa rebobinar? Só fita, só se alguém andou com ela, e **só se não foi
    /// você**.
    val precisaRebobinar: Boolean
        get() = vhs && !minha && posicaoEmSegundos > 60

    /// `1:23:45`, o ponteiro da fita.
    val ponteiro: String
        get() {
            val total = posicaoEmSegundos.toLong()
            return "%d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
        }
}

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
    /// A duração do arquivo — ver a folha do mesmo campo em `PlanoDeReproducao`.
    ///
    /// ⚠️ Ela vem **nas duas** respostas de propósito: o caminho direto não abre
    /// sessão, e o caminho de sessão pode ser retomado sem passar pelo plano.
    @SerialName("duration_seconds") val duracaoEmSegundos: Double? = null,
)

// ------------------------------------------------------------------ o mural
//
// `GET /api/feed`. Conferido contra `Mural` e `Acontecimento` do
// `web/src/api.ts:2076`.

/// Uma coisa que aconteceu na casa.
///
/// ## `tipo` é lista fechada, e o que a tela não sabe **some**
///
/// O comentário da web é a regra e ela vale aqui igual: «`terminou` | `pegou` |
/// `devolveu` | `pediu` | `avaliou`. Lista fechada: um tipo que a tela não sabe
/// dizer não vira linha muda, some.»
///
/// É o §18 aplicado a um feed: melhor uma linha a menos que uma linha que diz
/// «alguém fez algo com alguma coisa».
@Serializable
data class Acontecimento(
    val tipo: String,
    val quando: String,
    val quem: String,
    @SerialName("quem_id") val quemId: String,
    val meu: Boolean = false,
    val titulo: String,
    @SerialName("obra_id") val obraId: String? = null,
    val poster: String? = null,
    val detalhe: String? = null,
) {
    /// A frase, montada aqui porque ela é **desenho** e não dado.
    ///
    /// O servidor manda o verbo em código (`pegou`) e os sujeitos; a frase em
    /// português é da tela. Fazer o servidor mandar texto pronto amarraria o
    /// idioma da API ao idioma do cliente — e o `detalhe` já vem pronto porque
    /// aquele **é** conteúdo (uma nota, um recado).
    ///
    /// `null` quando o tipo é desconhecido, e aí a linha não desenha.
    val frase: String?
        get() = when (tipo) {
            "terminou" -> "terminou"
            "pegou" -> "pegou a fita de"
            "devolveu" -> "devolveu"
            "pediu" -> "pediu de volta"
            "avaliou" -> "avaliou"
            else -> null
        }
}

@Serializable
data class Mural(
    val acontecimentos: List<Acontecimento> = emptyList(),
    /// Quantas pessoas apareceram no mural.
    ///
    /// ⚠️ O comentário da web explica por que este número existe e é desenhado:
    /// «um mural com um nome só não é uma conversa — e a tela diz isso em vez de
    /// parecer completa». É o §8b numa métrica.
    val vozes: Int = 0,
    /// Quantas poderiam aparecer: você mais os seus.
    val pessoas: Int = 0,
)

// ------------------------------------------------------------------- o guia
//
// `GET /api/guia`. Conferido contra `GuiaEixos` do `web/src/api.ts:841`.

/// Uma pessoa do guia — direção, elenco ou trilha.
@Serializable
data class PessoaDoGuia(
    val id: String,
    val name: String,
    @SerialName("image_path") val imagem: String? = null,
    @SerialName("known_for") val conhecidaPor: String? = null,
    val obras: Int = 0,
    val terminadas: Int = 0,
    val comecadas: Int = 0,
    val posters: List<String>? = null,
    val total: Int = 0,
)

/// Um eixo que não é pessoa: gênero, década ou país.
@Serializable
data class FaixaDoGuia(
    val rotulo: String,
    /// O que iria pro filtro da biblioteca — `genre:Terror`, ou o ano da década.
    /// Declarado mesmo sem tela que filtre ainda: é ele que liga o guia ao
    /// acervo, e é a próxima coisa óbvia a fazer aqui.
    val chave: String,
    val obras: Int = 0,
    val posters: List<String>? = null,
)

/// `GET /api/guia` — os eixos pelos quais o acervo pode ser olhado.
///
/// ⚠️ Ele é o **índice**, não a capa. A capa é a [Revista], logo abaixo — e
/// durante uma sessão inteira o app teve só esta metade.
@Serializable
data class GuiaDeEixos(
    val direcao: List<PessoaDoGuia> = emptyList(),
    val elenco: List<PessoaDoGuia> = emptyList(),
    val trilha: List<PessoaDoGuia> = emptyList(),
    val generos: List<FaixaDoGuia> = emptyList(),
    val decadas: List<FaixaDoGuia> = emptyList(),
    val paises: List<FaixaDoGuia> = emptyList(),
    /// Quantos filmes **não** são dos Estados Unidos.
    ///
    /// O comentário da web diz por que ele vem junto: «sem ele o eixo diz
    /// "Estados Unidos 491" e o resto vira rodapé. Este é o número que faz a
    /// região valer uma seção».
    @SerialName("fora_de_hollywood") val foraDeHollywood: Int = 0,
)

// --------------------------------------------------------- a revista da semana
//
// `GET /api/guia/revista`. Conferido contra `Revista` do `web/src/api.ts:826`.

/// Um filme da capa.
@Serializable
data class FilmeDaCapa(
    val id: String,
    val titulo: String,
    val ano: Int? = null,
    val poster: String? = null,
    val diretor: String? = null,
    /// «A única coisa da capa que é sua» — o comentário é da web, e é a regra de
    /// desenho junto: a capa é igual pra todo mundo, então o que é seu entra
    /// como **marca discreta**, não como selo.
    val visto: Boolean = false,
)

/// O que está em cartaz esta semana: uma obra ou uma saga.
///
/// É o que amarra a revista ao resto — participar dá XP e conquista, e quem
/// participou aparece pra todo mundo, que é o ponto de ser coletivo.
@Serializable
data class EventoDaSemana(
    /// `"obra"` ou `"saga"`. Só a obra tem ficha pra abrir: o `id` de uma saga é
    /// de coleção, e mandar isso pra tela da obra daria 404.
    val tipo: String = "obra",
    val id: String,
    val titulo: String,
    val poster: String? = null,
    val obras: Int = 0,
    val suas: Int = 0,
    val participou: Boolean = false,
    val participantes: List<String> = emptyList(),
)

/// `GET /api/guia/revista` — a capa do guia.
///
/// ## Ela é uma revista semanal, e não um índice
///
/// Um tema sorteado do acervo, os filmes que se encaixam, o ensaio e o evento em
/// cartaz. **Igual pra todo mundo**, e virando na mesma segunda-feira que a
/// vitrine da locadora — é o que dá assunto em comum (`IDEIAS.md` §2.4). Os
/// desafios são o oposto: sorteados por pessoa.
@Serializable
data class Revista(
    @SerialName("semana_de") val semanaDe: String = "",
    /// Quando vira. A mesma segunda da vitrine — ver `Loja.viraEm`.
    @SerialName("vira_em") val viraEm: String = "",
    /// `"genero"`, `"decada"`, `"pais"`, `"diretor"` ou `"saga"`.
    val eixo: String = "",
    /// O tema: "Romance". É o letreiro da capa.
    val tema: String = "",
    val filmes: List<FilmeDaCapa> = emptyList(),
    /// ⚠️ O comentário da web **é regra**: «`null` quando não há chave do LLM ou
    /// o texto ainda não foi gerado. A tela **omite a seção** — não mostra
    /// "carregando" nem inventa prosa (§18, §24).»
    val ensaio: String? = null,
    /// ⚠️ E este também: «O selo. Quem lê tem direito de saber que aquele
    /// parágrafo **não foi escrito por gente** — a mesma regra do crédito
    /// `WIKIPÉDIA` das curiosidades (§32).»
    ///
    /// Texto de máquina sai sempre creditado. Não é enfeite: é obrigação
    /// editorial do projeto.
    @SerialName("ensaio_por") val ensaioPor: String? = null,
    val evento: EventoDaSemana? = null,
) {
    /// «gênero da semana», «década da semana».
    ///
    /// Montado aqui pelo mesmo motivo do `Acontecimento.frase`: o servidor manda
    /// o eixo em código e a frase em português é do cliente. `null` num eixo que
    /// este app não conhece — e aí o rótulo não desenha, em vez de escrever
    /// «saga da semana» sobre um eixo que talvez não seja isso (§18).
    val rotuloDoEixo: String?
        get() = when (eixo) {
            "genero" -> "gênero da semana"
            "decada" -> "década da semana"
            "pais" -> "país da semana"
            "diretor" -> "diretor da semana"
            "saga" -> "saga da semana"
            else -> null
        }

    /// Os parágrafos do ensaio, já limpos. Vazia quando não há ensaio — e aí a
    /// seção inteira some.
    val paragrafos: List<String>
        get() = ensaio.orEmpty().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
}

// ------------------------------------------------------------------- o perfil
//
// `GET /api/perfil`. Conferido campo a campo contra `PerfilCompleto` do
// `web/src/api.ts:2296`.
//
// ## O que **não** foi mapeado, e por quê
//
// A resposta traz mais três listas — `rostos`, `capas`, `molduras` — e mais dois
// catálogos, `titulos_disponiveis` e `tags_disponiveis`. Os cinco existem pro
// **editor** do perfil (§10.2 da referência), que este app não tem: eles são a
// lista do que dá pra escolher, e escolher é o que falta.
//
// Declará-los aqui seria contrato que ninguém confere — é a régua que o próprio
// `ObraDetalhada` aplicou às `tags` até a R3, e o `ignoreUnknownKeys` do `Rede`
// já os descarta em silêncio, que é o comportamento certo pra campo sem tela.

/// A cor, o rosto ou a capa que a pessoa escolheu — `EnfeiteNaTela` na web.
///
/// O servidor **já resolve** a escolha: manda o caminho da arte e o hex da cor
/// prontos. Traduzir a chave (`rosto:bogart`) em arte aqui seria a mesma tabela
/// escrita duas vezes, e a segunda cópia envelheceria sozinha.
@Serializable
data class EnfeiteEscolhido(
    val chave: String = "",
    val rotulo: String = "",
    /// Caminho servível em `/artwork/…`. `null` na cor, que não tem arte.
    val arte: String? = null,
    /// O hex, quando é cor de moldura.
    val cor: String? = null,
    val aberto: Boolean = true,
)

/// Nível, XP e conquistas — `ProgressoDoUsuario` na web.
///
/// ⚠️ `xp_do_nivel` e `xp_do_proximo` vêm **do servidor**, e o comentário da web
/// diz por quê: «a curva é regra, e regra mora num lugar só». Recalcular a curva
/// aqui é como o app e a web passariam a discordar sobre quanto falta pro nível
/// 7 — em silêncio, e só pra quem tem os dois abertos lado a lado.
@Serializable
data class ProgressoNoPerfil(
    val xp: Int = 0,
    val nivel: Int = 1,
    @SerialName("xp_do_nivel") val xpDoNivel: Int = 0,
    @SerialName("xp_do_proximo") val xpDoProximo: Int = 0,
    val desbloqueadas: Int = 0,
    val total: Int = 0,
)

/// Uma obra na vitrine. **A ordem é o conteúdo** (§10.2) — por isso é lista.
@Serializable
data class NaVitrine(
    val id: String,
    val titulo: String,
    val ano: Int? = null,
    val poster: String? = null,
)

/// Uma conquista, aberta ou trancada.
///
/// ⚠️ `em` é `null` enquanto trancada, e é o único jeito de saber. A trancada
/// mostra nome e descrição **mas não os pontos** (§10.5): ponto de conquista que
/// não se tem é promessa, e o projeto não promete número.
@Serializable
data class ConquistaNaTela(
    val chave: String = "",
    val nome: String = "",
    val descricao: String = "",
    /// `facil` · `media` · `dificil` · `impossivel` · `nivel` · `saga`.
    val camada: String = "",
    val pontos: Int = 0,
    val em: String? = null,
) {
    val aberta: Boolean get() = em != null
}

/// Uma linha do placar — `AmigoNoPlacar` na web.
@Serializable
data class AmigoNoPlacar(
    val id: String = "",
    @SerialName("display_name") val nome: String = "",
    val nivel: Int = 1,
    val xp: Int = 0,
    val titulo: String? = null,
    val eu: Boolean = false,
)

/// `GET /api/perfil` — quem você é dentro da casa.
///
/// ## Ele entrou pela insígnia, e não pela tela
///
/// O pedido foi o canto superior direito: o rosto redondo e o nível, como na
/// web. Só que a insígnia da web (`App.tsx:400`) sai **desta** rota — nível,
/// fatia do nível, rosto e a cor da moldura vêm todos de uma chamada só, feita
/// uma vez na montagem. Ou seja: pra desenhar 38dp de canto o app teve que
/// passar a falar o perfil inteiro.
///
/// E aí a tela veio junto de graça: o que a rota devolve já é quase tudo o que
/// o §10 desenha. O que ficou de fora ficou por não estar aqui — os desafios
/// (`/api/desafios`) e a retrospectiva (`/api/retrospectiva`) são outras rotas,
/// e o editor é um `PUT` que este app ainda não manda.
@Serializable
data class Perfil(
    @SerialName("user_id") val id: String = "",
    val username: String = "",
    @SerialName("display_name") val nome: String = "",
    /// É o seu? A rota serve os dois casos (`/api/perfil/{id}` traz o de outra
    /// pessoa), e a diferença decide o que a tela oferece.
    val meu: Boolean = true,
    val progresso: ProgressoNoPerfil = ProgressoNoPerfil(),
    @SerialName("titulo_nome") val tituloNome: String? = null,
    val tags: List<String> = emptyList(),
    val bio: String? = null,
    val vitrine: List<NaVitrine> = emptyList(),
    val conquistas: List<ConquistaNaTela> = emptyList(),
    val amigos: List<AmigoNoPlacar> = emptyList(),
    val avatar: EnfeiteEscolhido? = null,
    val capa: EnfeiteEscolhido? = null,
    /// A cor da moldura, em hex, já traduzida pelo servidor. `null` é o normal
    /// de quem não escolheu — e aí quem desenha cai no dourado da casa, nunca
    /// numa cor sorteada.
    val moldura: String? = null,
) {
    /// Quanto do nível atual já foi andado, de 0 a 1.
    ///
    /// É a conta do `App.tsx:244`, copiada com o `max(1, …)` junto: a faixa vira
    /// denominador, e um nível de faixa zero — que o servidor pode mandar no
    /// último nível da curva — dividiria por zero e pintaria o anel de `NaN`.
    val fatiaDoNivel: Float
        get() {
            val faixa = maxOf(1, progresso.xpDoProximo - progresso.xpDoNivel)
            return ((progresso.xp - progresso.xpDoNivel).toFloat() / faixa).coerceIn(0f, 1f)
        }

    /// Quanto falta pro próximo nível. `null` quando não há próximo — e aí a
    /// frase não escreve «faltam 0», que seria dizer que o nível vira já (§24).
    val faltamPraSubir: Int?
        get() = (progresso.xpDoProximo - progresso.xp).takeIf { it > 0 }
}

// ─────────────────────────────────────────────────────────────────────────────
// O ao vivo — os canais, a grade, e a grade que o próprio Odeon programa.
//
// ⚠️ **Esta parte não existe no `:app`.** Ela nasceu direto no `:tv`, e é a
// primeira vez neste projeto que a TV chega antes do celular. O motivo é o
// argumento do dono, e ele é bom: um canal ao vivo é uma coisa de **sala**. Zapear
// com o polegar num ônibus é uma lista; zapear com o controle no sofá é televisão.
//
// Os modelos ficam no `:core` mesmo assim, e pela régua de sempre: eles não
// desenham. Quando o celular quiser o ao vivo, ele acha tudo pronto aqui.
// ─────────────────────────────────────────────────────────────────────────────

/// Um canal com o que está no ar nele **agora**.
///
/// ⚠️ Quase tudo é anulável, e isso não é frouxidão do contrato: um canal de M3U
/// pode não ter EPG nenhum. Quando `titulo` é `null`, o canal existe e ninguém
/// sabe o que está passando — que é diferente de o canal não existir, e a tela
/// tem que saber dizer as duas coisas (§18).
@Serializable
data class CanalNoAr(
    val id: String,
    val name: String,
    val number: String? = null,
    @SerialName("logo_url") val logo: String? = null,
    val grupo: String? = null,
    val titulo: String? = null,
    @SerialName("sub_titulo") val subtitulo: String? = null,
    val comeca: String? = null,
    val termina: String? = null,
    @SerialName("a_seguir") val aSeguir: String? = null,
    @SerialName("programme_id") val programaId: Int? = null,
    /// Arte da obra ligada ao programa — só quando o casamento foi seguro.
    val arte: String? = null,
    /// A obra e o arquivo dela: é o que «ver desde o início» toca.
    @SerialName("work_id") val obraId: String? = null,
    @SerialName("media_file_id") val arquivoId: String? = null,
    /// A **série**, quando o que está no ar é um episódio. Ver
    /// `ProgramaDoGuia.colecaoId`.
    @SerialName("collection_id") val colecaoId: String? = null,
)

/// Um programa na grade.
@Serializable
data class ProgramaDoGuia(
    val id: Int,
    @SerialName("channel_id") val canalId: String,
    @SerialName("starts_at") val comeca: String,
    @SerialName("ends_at") val termina: String,
    val title: String,
    @SerialName("sub_title") val subtitulo: String? = null,
    val description: String? = null,
    val year: Int? = null,
    val categoria: String? = null,
    val arte: String? = null,
    @SerialName("work_id") val obraId: String? = null,
    @SerialName("media_file_id") val arquivoId: String? = null,
    /// A série, quando o programa é um **episódio**.
    ///
    /// ## ⚠️ Ele e o `work_id` são **mutuamente exclusivos**
    ///
    /// Medido no aparelho em 19/08/2026, lendo o corpo cru antes do parse — o
    /// `ignoreUnknownKeys` desta casa faz o app engolir campo novo em silêncio,
    /// então o modelo não servia de prova:
    ///
    /// | | |
    /// |---|---|
    /// | `/api/live/guide` | 255 programas · 96 só obra · **59 só coleção** · **0 com os dois** |
    /// | `/api/live/channels` | 18 canais · 13 com obra · 1 com coleção |
    /// | `/api/live/odeon` | **não tem** — a grade da casa toca arquivo, e episódio ali é obra |
    ///
    /// ⚠️ Era por isto que um episódio no ar aparecia como «programa sem nada
    /// atrás», igual a um canal sem EPG: o app procurava `work_id`, achava
    /// `null`, e concluía que não havia o que abrir. É o mesmo id que a ficha da
    /// série consome (`ModeloDaSerie(serieId)`).
    @SerialName("collection_id") val colecaoId: String? = null,
    val lembrete: Boolean = false,
)

/// A grade.
///
/// ⚠️ **Ela traz o relógio do servidor**, e o comentário da web explica por quê
/// com uma frase que vale copiar: «a agulha do "agora" tem que ser desenhada
/// contra o mesmo relógio que produziu a grade».
///
/// Desenhar a linha vermelha contra o relógio da TV é como ela fica minutos fora
/// do lugar — e numa TCL cujo relógio ninguém acertou, muito mais que minutos.
@Serializable
data class Guia(
    val agora: String,
    val ate: String,
    val programas: List<ProgramaDoGuia> = emptyList(),
)

/// Um lembrete marcado — `GET /api/live/reminders`.
///
/// ⚠️ O app só precisa do `programa_id`: é ele que casa com o
/// `ProgramaDoGuia.id` e diz quais blocos da grade desenham a estrela. Os outros
/// campos vêm e não são lidos, e isso é de propósito — a grade já tem título e
/// horário; repetir aqui seria manter a mesma verdade em dois lugares.
@Serializable
data class LembreteDoGuia(
    @SerialName("programme_id") val programaId: Int,
    val title: String = "",
)

@Serializable
data class LembreteMarcado(
    val ok: Boolean = false,
    @SerialName("starts_at") val comeca: String = "",
)

/// Um canal que o **próprio Odeon** programa, do acervo da casa.
@Serializable
data class CanalDoOdeon(
    val slug: String,
    val nome: String,
    val numero: String,
)

/// Um programa da grade do Odeon.
///
/// ⚠️ Ele **não** é um `ProgramaDoGuia`: aquele vem de EPG de terceiro e tem
/// `channel_id`; este é calculado do acervo e traz a obra junto. Fundir os dois
/// num modelo só com metade dos campos anuláveis seria a segunda contabilidade
/// do §43.
@Serializable
data class ProgramaDoOdeon(
    val id: String,
    val canal: String,
    @SerialName("canal_nome") val canalNome: String,
    val numero: String,
    @SerialName("work_id") val obraId: String,
    @SerialName("media_file_id") val arquivoId: String? = null,
    val title: String,
    val year: Int? = null,
    val arte: String? = null,
    val categoria: String? = null,
    @SerialName("starts_at") val comeca: String,
    @SerialName("ends_at") val termina: String,
)

/// A grade que o Odeon programa.
///
/// ⚠️ «Calculada, não guardada: duas chamadas no mesmo dia devolvem a mesma
/// programação.» É o comentário da web, e ele importa pra TV: dá pra pedir de
/// novo sem medo de a grade mudar debaixo de quem está lendo.
@Serializable
data class GradeDoOdeon(
    val agora: String,
    val ate: String,
    val canais: List<CanalDoOdeon> = emptyList(),
    val programas: List<ProgramaDoOdeon> = emptyList(),
)

/// O canal aberto pra tocar — a sessão de HLS.
@Serializable
data class CanalAberto(
    val channel: CanalMinimo,
    @SerialName("session_id") val sessaoId: String,
    @SerialName("playlist_url") val urlDaPlaylist: String,
    val mode: String? = null,
    val reasons: List<String> = emptyList(),
)

@Serializable
data class CanalMinimo(val id: String, val name: String)

/// Uma **coleção** — a série, e cada temporada dela.
///
/// ## ⚠️ Ela chegou em 18/08/2026, e substitui um monte de reserva
///
/// Até esta tarde a ficha da série montava tudo dos episódios: a arte de
/// temporada era o `still` do primeiro, a sinopse não existia e o pano de fundo
/// era o backdrop de um episódio. Eram reservas declaradas, escritas pra sumir —
/// e o `PEDIDOS-AO-SERVIDOR.md, «já entregue» 10` foi atendido:
///
/// | | |
/// |---|---|
/// | pôster de temporada | **461 de 473**, buscados do TMDB |
/// | sinopse de temporada | 232 |
/// | nome próprio de temporada | 26 |
/// | sinopse e backdrop da série | 115 e 118 das 120 |
///
/// ⚠️ As 12 temporadas sem pôster são as que o TMDB não descreve — a temporada 0
/// de especiais, na maioria. A reserva **fica no lugar** por causa delas: quem
/// não tem pôster continua usando o `still` do primeiro episódio.
@Serializable
data class Colecao(
    val id: String,
    val kind: String = "",
    val title: String = "",
    val year: Int? = null,
    val overview: String? = null,
    /// O número da temporada. ⚠️ É `position`, e não um campo `season_number`:
    /// numa coleção genérica ele é a ordem, e numa temporada ele **é** o número.
    val position: Int? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    @SerialName("dominant_color") val corDominante: String? = null,
    @SerialName("item_count") val quantosItens: Int = 0,
    @SerialName("finished_count") val quantosVistos: Int = 0,
)

/// A resposta de `GET /api/collections/{id}`: a série e as temporadas dela.
@Serializable
data class ColecaoComFilhos(
    val collection: Colecao,
    /// As temporadas, cada uma com `position`, `poster`, `overview` e contagens.
    val children: List<Colecao> = emptyList(),
)
