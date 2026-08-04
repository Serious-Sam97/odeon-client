package dev.odeon.android.dados

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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
}
