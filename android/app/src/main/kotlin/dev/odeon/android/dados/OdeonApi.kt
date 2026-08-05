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
    @GET("api/library")
    suspend fun biblioteca(
        @Query("limit") limite: Int = 60,
        @Query("offset") pulando: Int = 0,
        @Query("q") busca: String? = null,
    ): List<ItemDaBiblioteca>

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

    /// A folha de sprites do preview de seek.
    ///
    /// ⚠️ Devolve **404** quando o sprite ainda não foi gerado, e isso é normal:
    /// o servidor gera aos poucos. Quem chama trata 404 como "não há preview" e
    /// **só** o 404 — ver `FolhaDeSprites` pro defeito que mascarar o 401 causou
    /// na web.
    @GET("api/media/{arquivo}/scrub")
    suspend fun folhaDeSprites(@Path("arquivo") arquivoId: String): FolhaDeSprites
}
