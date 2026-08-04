package dev.odeon.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.odeon.android.dados.Cofre
import dev.odeon.android.dados.RepositorioOdeon

/// O `Application`, e ele existe por um motivo só: **guardar o que dura o app
/// inteiro**.
///
/// O `Cofre` e o `RepositorioOdeon` não podem nascer de novo a cada tela — o
/// repositório carrega o OkHttp, e um OkHttp por tela é um pool de conexões por
/// tela.
///
/// Não há injeção de dependência aqui, e é escolha: Hilt/Koin resolvem grafo de
/// dependência, e o grafo deste app tem **dois nós**. Quando ele crescer a ponto
/// de doer, entra — e aí entra resolvendo dor medida, não prevista.
class OdeonApp : Application(), SingletonImageLoader.Factory {

    val cofre: Cofre by lazy { Cofre(this) }
    val odeon: RepositorioOdeon by lazy { RepositorioOdeon(cofre) }

    /// O Coil usa **o mesmo OkHttp** da API.
    ///
    /// Sem isto ele cria um cliente próprio, e aí o app tem dois pools de
    /// conexão pro mesmo servidor de casa — cada pôster reabrindo handshake que
    /// a chamada de API ao lado já tinha feito.
    ///
    /// É a segunda das três coisas que compartilham a instância; a terceira é o
    /// Media3, na fase 2. Ver `dados/Rede.kt`.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { odeon.clienteHttp() }))
            }
            .build()
}
