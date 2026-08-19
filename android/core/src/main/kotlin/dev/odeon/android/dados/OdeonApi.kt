package dev.odeon.android.dados

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/// As rotas que a fase 1 fala.
///
/// São **cinco de 113**. A espec (§5) escolheu a sequência por "o que só faz
/// sentido no celular", não por cobrir a API — e cobrir 113 rotas antes da
/// primeira tela é como um app fica dois anos sem sair.
///
/// O KMP em `clients/` fala dez, e provou que `auth/*` e a listagem bastam pra
/// uma tela de verdade.
interface OdeonApi {

    /// A única que responde sem sessão. Serve pra duas coisas: saber se o
    /// servidor ainda precisa de configuração inicial, e — de graça — descobrir
    /// se aquele endereço **é** um Odeon antes de mandar senha pra ele.
    @GET("api/auth/status")
    suspend fun status(): StatusDoServidor

    @POST("api/auth/login")
    suspend fun entrar(@Body credenciais: Credenciais): RespostaDeLogin

    @GET("api/auth/me")
    suspend fun quemSouEu(): Usuario

    @POST("api/auth/media-token")
    suspend fun tokenDeMidia(): TokenDeMidia

    /// A biblioteca, paginada.
    ///
    /// `limit` e `offset` e não um cursor: é o que o servidor tem, e cada linha
    /// já traz o `total` do filtro. Trocar por cursor seria mudar o servidor pra
    /// resolver um problema que a tela ainda não tem.
    /// ⚠️ **Nulo não vira parâmetro.** O Retrofit omite `@Query` nulo, e é isso
    /// que faz esta assinatura de doze campos mandar `?limit=60` quando não há
    /// filtro nenhum — em vez de `?state=&sort=&tags=`, que o servidor teria que
    /// aprender a ignorar.
    @GET("api/library")
    suspend fun biblioteca(
        @Query("limit") limite: Int = 60,
        @Query("offset") pulando: Int = 0,
        @Query("q") busca: String? = null,
        @Query("kind") tipo: String? = null,
        @Query("tags") etiquetas: String? = null,
        /// ⚠️ `any`, e sem opção de trocar — ver `Filtros.excluindo`.
        @Query("tags_not") excluindo: String? = null,
        @Query("tag_mode") modoDasEtiquetas: String? = null,
        @Query("year_from") anoDe: Int? = null,
        @Query("year_to") anoAte: Int? = null,
        @Query("min_minutes") minutosDe: Int? = null,
        @Query("max_minutes") minutosAte: Int? = null,
        @Query("state") estado: String? = null,
        @Query("person") pessoa: String? = null,
        @Query("sort") ordem: String? = null,
    ): List<ItemDaBiblioteca>

    /// A listagem **plana** — os episódios de uma série.
    ///
    /// Mesmos filtros da agrupada, e mais o `collection`, que é o que a torna
    /// plana na prática: dentro de uma série, agrupar seria devolver a série de
    /// volta. Ver `ObraDaLista`.
    @GET("api/works")
    suspend fun obras(
        @Query("limit") limite: Int = 60,
        @Query("offset") pulando: Int = 0,
        @Query("collection") colecao: String? = null,
        @Query("q") busca: String? = null,
        @Query("kind") tipo: String? = null,
        @Query("tags") etiquetas: String? = null,
        /// ⚠️ `any`, e sem opção de trocar — ver `Filtros.excluindo`.
        @Query("tags_not") excluindo: String? = null,
        @Query("tag_mode") modoDasEtiquetas: String? = null,
        @Query("year_from") anoDe: Int? = null,
        @Query("year_to") anoAte: Int? = null,
        @Query("min_minutes") minutosDe: Int? = null,
        @Query("max_minutes") minutosAte: Int? = null,
        @Query("state") estado: String? = null,
        @Query("person") pessoa: String? = null,
        @Query("sort") ordem: String? = null,
    ): List<ObraDaLista>

    /// A coleção e os filhos dela — pra uma série, as **temporadas**.
    ///
    /// ⚠️ Não é rota nova: ela já existia e agora devolve `poster`, `backdrop`,
    /// `dominant_color` e `finished_count`. Ver `Colecao`.
    @GET("api/collections/{id}")
    suspend fun colecao(@Path("id") id: String): ColecaoComFilhos

    /// As etiquetas do acervo, com a contagem de cada uma.
    ///
    /// Pedida **uma vez**, na primeira abertura do painel de filtros: são
    /// centenas de linhas que mudam quando a identificação roda, e não a cada
    /// toque num chip.
    @GET("api/tags")
    suspend fun etiquetas(): List<EtiquetaDoAcervo>

    /// Os grupos das etiquetas — é deles que sai o rótulo «Gênero» e a ordem em
    /// que os grupos aparecem. Sem eles a tela teria que traduzir `genre` por
    /// conta própria, e aí a lista de namespaces existiria em dois lugares.
    @GET("api/tag-namespaces")
    suspend fun espacosDeEtiqueta(): List<EspacoDeEtiqueta>

    // ----------------------------------------------------------------- fase 2

    /// A ficha da obra. Traz `files`, e é dela que sai o arquivo que vai tocar.
    @GET("api/works/{id}")
    suspend fun obra(@Path("id") id: String): ObraDetalhada

    /// O que este usuário pode assistir agora.
    ///
    /// ⚠️ Tem que ser perguntada **antes** de desenhar o play (§66), inclusive
    /// pro admin. Desenhar primeiro e descobrir depois é oferecer um 403.
    @GET("api/locadora/liberadas")
    suspend fun liberadas(): Liberadas

    /// Como este aparelho vai receber este arquivo.
    ///
    /// As capacidades vão na query, e são as **deste** aparelho — não uma lista
    /// fixa. A web pergunta ao `canPlayType` do navegador; aqui quem responde é
    /// o `MediaCodecList`, e responde melhor (§3 da espec). Os nomes dos
    /// parâmetros são os mesmos que a web manda, porque o servidor é o mesmo.
    @GET("api/playback/{arquivo}/plan")
    suspend fun plano(
        @Path("arquivo") arquivoId: String,
        @Query("containers") containers: String,
        @Query("video_codecs") codecsDeVideo: String,
        @Query("audio_codecs") codecsDeAudio: String,
        @Query("supports_hls") suportaHls: String = "true",
        /// Qual faixa de áudio o plano deve considerar. `null` não entra na query
        /// — Retrofit omite —, e aí o servidor usa a 0. Ver
        /// `PlanoDeReproducao.faixaDeAudio`.
        @Query("audio_track") faixaDeAudio: Int? = null,
    ): PlanoDeReproducao

    /// Abre a sessão de HLS. Só quando o plano **não** for `direct_play`.
    @POST("api/playback/{arquivo}/session")
    suspend fun abrirSessao(
        @Path("arquivo") arquivoId: String,
        @Query("containers") containers: String,
        @Query("video_codecs") codecsDeVideo: String,
        @Query("audio_codecs") codecsDeAudio: String,
        @Query("supports_hls") suportaHls: String = "true",
        @Query("start") comecandoEm: Int = 0,
        /// ⚠️ Trocar de faixa **exige sessão nova**, como o `start`: a playlist
        /// já foi escrita com a faixa anterior, e o `ffmpeg` daquela sessão não
        /// muda de ideia no meio.
        @Query("audio_track") faixaDeAudio: Int? = null,
    ): SessaoDeTranscodificacao

    // ----------------------------------------------------------------- fase 7

    /// "Para você" — recomendação com motivo.
    ///
    /// `minutes` filtra por tempo disponível ("tenho 90 minutos"), e é o
    /// parâmetro que faz esta tela ser de celular e não de catálogo.
    @GET("api/curation/for-you")
    suspend fun paraVoce(
        @Query("limit") limite: Int = 24,
        @Query("minutes") minutos: Int? = null,
    ): ParaVoce

    // ----------------------------------------------------------------- fase 5

    /// O que está fora da estante. **Só lê.**
    /// A loja: as estantes com as caixas expostas. Ver `Loja` — é a rota que
    /// faz a locadora ser uma loja e não uma lista de empréstimos.
    /// O mural — o que aconteceu na casa. `GET /api/feed`, e o nome da rota é
    /// `feed` mesmo: é a única do app cujo endereço não está em português, e é
    /// do servidor, não nosso.
    @GET("api/feed")
    suspend fun mural(@Query("limit") limite: Int = 40): Mural

    /// O guia — os eixos pelos quais o acervo pode ser olhado.
    @GET("api/guia")
    suspend fun guia(): GuiaDeEixos

    /// A revista da semana — a **capa** do guia.
    ///
    /// Rota separada de propósito no servidor, e por isso separada aqui: a
    /// revista vira toda segunda e o índice não. Ver `Revista`.
    @GET("api/guia/revista")
    suspend fun revista(): Revista

    @GET("api/locadora/estantes")
    suspend fun estantes(): Loja

    @GET("api/locadora/prateleira")
    suspend fun prateleira(): Prateleira

    /// Pegar a fita.
    ///
    /// ⚠️ **Escreve em produção**, no acervo compartilhado por três pessoas
    /// (§11). Um empréstimo criado por engano fica no perfil de alguém — e a
    /// limpeza é no `serious-server`, não daqui.
    @POST("api/locadora/alugar")
    suspend fun alugar(@Body alvo: AlvoDaCaixa): RespostaDoAluguel

    /// O menu do disco — capítulos, legendas, onde parou e o clima.
    @GET("api/works/{obra}/menu")
    suspend fun menuDoDisco(@Path("obra") obraId: String): MenuDoDisco

    /// As cenas: as miniaturas da grade de capítulos.
    ///
    /// Rota separada do menu de propósito, e a web faz igual: são doze imagens
    /// e o menu tem que abrir antes delas chegarem. Ver as molduras vazias em
    /// `MenuDeDVD`.
    @GET("api/works/{obra}/cenas")
    suspend fun cenasDoDisco(@Path("obra") obraId: String): List<Cena>

    /// Onde esta fita parou. **Só lê.**
    @GET("api/locadora/fita/{obra}")
    suspend fun fita(@Path("obra") obraId: String): Fita

    /// Rebobinar.
    ///
    /// ⚠️ Escreve, e mexe **na fita** — não no "continuar de onde parou" de
    /// ninguém. É por isso que a web não pede confirmação: o gesto é grande, o
    /// efeito é pequeno, e desfazê-lo é dar play de novo.
    @POST("api/locadora/rebobinar")
    suspend fun rebobinar(@Body alvo: AlvoDaCaixa): Map<String, kotlinx.serialization.json.JsonElement>

    /// Pedir de volta.
    ///
    /// ⚠️ **Não encurta o prazo de ninguém** (§6 da referência) — dar a um
    /// morador poder sobre o prazo do outro transformaria a locadora em disputa.
    /// O que ela faz é avisar: aparece um recado na caixa de quem está com ela.
    @POST("api/locadora/pedir/{id}")
    suspend fun pedirDeVolta(@Path("id") emprestimoId: Int): Map<String, kotlinx.serialization.json.JsonElement>

    /// Devolver. Escreve, pelo mesmo motivo — e **apagar o empréstimo errado é
    /// apagar o empréstimo de uma pessoa**.
    @POST("api/locadora/devolver/{id}")
    suspend fun devolver(@Path("id") emprestimoId: Int): Map<String, kotlinx.serialization.json.JsonElement>

    /// De onde continuar. O outro lado do `marcarProgresso`.
    @GET("api/continue")
    suspend fun paraContinuar(): List<ItemPraContinuar>

    /// Encerra a sessão de HLS.
    ///
    /// ⚠️ **Não é higiene, é CPU do servidor de casa.** O comentário da web é
    /// direto: «sem isto o ffmpeg fica vivo até o reaper passar». O `serious-server`
    /// atende três pessoas de verdade e ainda roda o Postgres e a identificação;
    /// um ffmpeg esquecido por sessão abandonada é o tipo de custo que ninguém
    /// vê até a casa toda ficar lenta.
    @DELETE("api/hls/{sessao}")
    suspend fun encerrarSessao(@Path("sessao") sessaoId: String): Unit

    /// Onde eu parei. É o que a fase 3 (continuar de onde parou) vai ler.
    @POST("api/works/{obra}/progress")
    suspend fun marcarProgresso(
        @Path("obra") obraId: String,
        @Body marca: MarcaDeProgresso,
    ): RespostaDeProgresso

    /// Quem você é dentro da casa — nível, XP, conquistas, vitrine, placar.
    ///
    /// ## Uma chamada, dois usos, e é de propósito
    ///
    /// A insígnia do canto (rosto + anel do nível) e a tela do perfil saem da
    /// **mesma** resposta. A web faz igual (`App.tsx:238`) e o comentário dela
    /// diz por quê: «uma requisição, na montagem: o número muda devagar e a
    /// barra não é lugar de ficar perguntando».
    ///
    /// Sem o parâmetro é o seu. Com ele seria o de outra pessoa — e não está
    /// declarado porque não há por onde chegar no perfil de alguém neste app: o
    /// mural não tem `gente`, e §53 vale pro contrato também.
    @GET("api/perfil")
    suspend fun perfil(): Perfil

    /// A folha de sprites do preview de seek.
    ///
    /// ⚠️ Devolve **404** quando o sprite ainda não foi gerado, e isso é normal:
    /// o servidor gera aos poucos. Quem chama trata 404 como "não há preview" e
    /// **só** o 404 — ver `FolhaDeSprites` pro defeito que mascarar o 401 causou
    /// na web.
    @GET("api/media/{arquivo}/scrub")
    suspend fun folhaDeSprites(@Path("arquivo") arquivoId: String): FolhaDeSprites

    // ── o ao vivo ────────────────────────────────────────────────────────────

    /// Os canais e o que está no ar em cada um.
    @GET("api/live/channels")
    suspend fun canaisAoVivo(): List<CanalNoAr>

    /// A grade dos canais de fonte externa.
    @GET("api/live/guide")
    suspend fun guiaAoVivo(@Query("hours") horas: Int = 4): Guia

    /// A grade que o **próprio Odeon** programa, do acervo da casa.
    @GET("api/live/odeon")
    suspend fun gradeDoOdeon(@Query("hours") horas: Int = 5): GradeDoOdeon

    /// Abre uma sessão num canal e devolve a playlist.
    /// Os lembretes marcados — `GET /api/live/reminders`.
    ///
    /// ⚠️ Eles existiam na web desde sempre e **nunca no app**. O
    /// `ProgramaDoGuia` já vinha com `lembrete: Boolean` dizendo se o programa
    /// está marcado; o app lia o campo e não tinha o que fazer com ele.
    @GET("api/live/reminders")
    suspend fun lembretes(): List<LembreteDoGuia>

    @POST("api/live/reminders/{programa}")
    suspend fun marcarLembrete(@Path("programa") programaId: Int): LembreteMarcado

    @DELETE("api/live/reminders/{programa}")
    suspend fun desmarcarLembrete(@Path("programa") programaId: Int)

    @POST("api/live/{id}/watch")
    suspend fun sintonizar(@Path("id") canalId: String): CanalAberto
}
