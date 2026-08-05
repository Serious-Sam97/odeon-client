package dev.odeon.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.odeon.android.dados.Cofre
import dev.odeon.android.dados.RepositorioOdeon
import kotlinx.coroutines.launch

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
/// O `@OptIn` cobre a classe porque ela **segura** as peças do Media3 que ficam
/// abaixo da fronteira estável — o `DownloadManager` e o cache. É o mesmo lugar
/// onde já moram o OkHttp e o repositório: o que dura o app inteiro.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class OdeonApp : Application(), SingletonImageLoader.Factory {

    val cofre: Cofre by lazy { Cofre(this) }
    val odeon: RepositorioOdeon by lazy { RepositorioOdeon(cofre) }

    /// O barramento — **uma conexão pro app inteiro** (§62).
    ///
    /// Aqui pelo mesmo motivo do OkHttp e do cofre: uma conexão por tela seriam
    /// cinco SSE abertos contra o servidor de casa, e cinco reconexões a cada
    /// piscada de wifi. Ver `Barramento`.
    val barramento: dev.odeon.android.dados.Barramento by lazy {
        dev.odeon.android.dados.Barramento(cofre, odeon.clienteHttp())
    }

    /// A fila de downloads — fase 6.
    ///
    /// Aqui pelo mesmo motivo dos outros dois: ela abre um cache em disco e um
    /// índice SQLite, e um por tela seria um banco por tela. E recebe **o mesmo
    /// OkHttp**, que é a quarta coisa a usá-lo — depois do Retrofit, do Coil e
    /// do Media3.
    val baixados: dev.odeon.android.dados.Baixados by lazy {
        dev.odeon.android.dados.Baixados(this, odeon.clienteHttp())
    }

    /// Põe um arquivo na fila de download.
    ///
    /// ## Ele pergunta o plano antes, e só baixa o que é direto
    ///
    /// Um `transcode` não tem arquivo pra baixar: ele é uma sessão de HLS que o
    /// servidor gera enquanto se assiste, e guardar isso no disco seria guardar
    /// uma sessão, não um filme. Então o download só existe pro `direct_play` —
    /// o que, pelo perfil deste app (com `mkv`), é a maior parte do acervo.
    ///
    /// A alternativa seria baixar os segmentos de HLS e remontar, que é
    /// reimplementar o contêiner do lado errado da rede.
    ///
    /// ## A origem entra aqui, e é ela que decide o prazo
    ///
    /// Baixado pela ficha é `BIBLIOTECA` — **não expira**, porque a biblioteca é
    /// modo livre desde o §71. Quando a locadora ganhar o botão de baixar, ela
    /// passa `LOCADORA` e o `vence_em` do empréstimo junto. Ver
    /// `OrigemDoDownload`.
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun baixarArquivo(
        arquivoId: String,
        obraId: String,
        origem: dev.odeon.android.dados.OrigemDoDownload =
            dev.odeon.android.dados.OrigemDoDownload.BIBLIOTECA,
        venceEm: Long? = null,
    ) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()).launch {
            runCatching {
                val obra = odeon.obra(obraId)
                val plano = odeon.plano(arquivoId)
                if (!plano.eDireto) {
                    android.util.Log.w("Odeon", "não dá pra baixar $arquivoId: o plano é ${plano.mode}")
                    return@runCatching
                }
                val url = odeon.urlDeMidia(plano.urlDireta) ?: return@runCatching
                baixados.baixar(
                    url = url,
                    ficha = dev.odeon.android.dados.FichaDoDownload(
                        obraId = obraId,
                        arquivoId = arquivoId,
                        titulo = obra.title,
                        poster = obra.artwork["poster"],
                        origem = origem.name,
                        venceEm = venceEm,
                        duracaoEmSegundos = obra.duracaoEmSegundos,
                    ),
                )
                androidx.media3.exoplayer.offline.DownloadService.sendResumeDownloads(
                    this@OdeonApp,
                    dev.odeon.android.midia.ServicoDeDownload::class.java,
                    false,
                )
            }.onFailure { android.util.Log.w("Odeon", "download não entrou na fila: $it") }
        }
    }

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
