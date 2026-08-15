package dev.odeon.android.tv

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.odeon.android.dados.Barramento
import dev.odeon.android.dados.Cofre
import dev.odeon.android.dados.RepositorioOdeon

/// O `Application` da sala, e ele existe pelo mesmo motivo do `OdeonApp` do
/// celular: **guardar o que dura o app inteiro**.
///
/// O `Cofre` e o `RepositorioOdeon` não podem nascer de novo a cada tela — o
/// repositório carrega o OkHttp, e um OkHttp por tela é um pool de conexões por
/// tela.
///
/// ## Ele é mais curto que o do celular, e o que falta é o que a TV não faz
///
/// | | celular | sala |
/// |---|---|---|
/// | cofre, repositório, barramento | ✅ | ✅ |
/// | `Baixados` — cache em disco e índice SQLite | ✅ | **não** |
/// | `baixarArquivo` | ✅ | **não** |
///
/// A TV não baixa filme, e não é limitação: baixar existe pra quando a rede
/// falta, e uma TV que perdeu a rede perdeu o servidor junto — o Odeon mora na
/// mesma casa. Guardar 4 GB numa TCL pra assistir offline seria guardar pro caso
/// de o cabo entre dois cômodos cair.
///
/// O que **não** se perde por isso é o cache de reprodução, e ele nem estava no
/// download: quem lê do disco é o `CacheDataSource`, e a TV simplesmente não
/// monta um. Ver `midia/ServicoDeMidiaDaTv.kt`.
///
/// Não há injeção de dependência aqui, e é a mesma escolha do `:app`: o grafo
/// tem três nós.
class OdeonTv : Application(), SingletonImageLoader.Factory {

    val cofre: Cofre by lazy { Cofre(this) }
    val odeon: RepositorioOdeon by lazy { RepositorioOdeon(cofre) }

    /// O barramento — **uma conexão pro app inteiro** (§62).
    ///
    /// Na TV ele ganha um trabalho que no celular era só simpatia: é por ele que
    /// a locadora da sala sabe que alguém pegou a última cópia de um filme
    /// enquanto a tela estava aberta. Numa TV a tela fica aberta muito mais
    /// tempo do que num celular — ela não vai pro bolso.
    val barramento: Barramento by lazy { Barramento(cofre, odeon.clienteHttp()) }

    /// O Coil usa **o mesmo OkHttp** da API.
    ///
    /// Sem isto ele cria um cliente próprio, e aí o app tem dois pools de
    /// conexão pro mesmo servidor de casa — cada pôster reabrindo handshake que
    /// a chamada de API ao lado já tinha feito.
    ///
    /// Numa TV isso pesa mais do que num celular: uma fileira de cartazes em
    /// 1080p carrega imagem maior, e a home abre seis fileiras de uma vez.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { odeon.clienteHttp() }))
            }
            .build()
}
