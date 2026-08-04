package dev.odeon.android.midia

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.odeon.android.OdeonApp

/// A sessão de mídia — e o único lugar do app onde um `ExoPlayer` existe.
///
/// ## Por que serviço, e não um player dentro da tela
///
/// A espec (§5) põe PiP e sessão de mídia **dentro** da fase 2, e escreve o
/// motivo: «um player de Android que não faz os dois é um `<video>` com mais
/// passos». Sessão de mídia é o que faz o filme aparecer nos controles do
/// sistema, na tela de bloqueio e no fone de ouvido — e nada disso funciona com
/// um player que vive e morre com uma tela do Compose.
///
/// ## E ela deixou a decisão da espec **mais** verdadeira, não menos
///
/// A regra é escrever a UI contra `Player`, nunca contra `ExoPlayer`. Com o
/// serviço, a tela nem chega perto de um `ExoPlayer`: ela conecta um
/// `MediaController`, que **implementa `Player`**. Ou seja, a UI passou a falar
/// com um objeto que nem sequer é o player — é um controle remoto pra ele — e
/// continuou funcionando sem mudar uma linha da timeline.
///
/// É exatamente a prova que a fase 4 vai cobrar: se trocar o `ExoPlayer` por um
/// controle remoto não quebrou nada, trocar por um `CastPlayer` também não vai.
///
/// ## O OkHttp é o mesmo de sempre
///
/// `OkHttpDataSource.Factory` recebendo `app.odeon.clienteHttp()`. Um pool, um
/// cache, um lugar onde o cabeçalho é posto — e uma contabilidade só do token de
/// mídia, que é onde o §43 morde.
///
/// ## O `DefaultMediaSourceFactory` cobre HLS e arquivo direto
///
/// Ele escolhe a fonte pelo tipo do conteúdo, e com o `media3-exoplayer-hls` no
/// classpath a playlist vira `HlsMediaSource` sozinha. A URL do Odeon carrega
/// `?token=` e nem sempre termina em `.m3u8`, então quem manda o tipo é a tela,
/// no `MediaItem` — ver `TelaDoPlayer`.
@UnstableApi
class ServicoDeMidia : MediaSessionService() {

    private var sessao: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as OdeonApp

        /// A fonte lê **do disco primeiro**, e só cai na rede pelo que falta.
        ///
        /// É o que faz a fase 6 valer: um filme baixado toca sem rede porque o
        /// `CacheDataSource` acha os bytes no mesmo `SimpleCache` em que o
        /// download os escreveu. Sem esta camada, baixar seria ocupar espaço e
        /// continuar streamando.
        ///
        /// E ela não é só pra offline: um filme baixado pela metade continua de
        /// onde parou em vez de rebaixar, e o que já está no disco não volta a
        /// pesar no servidor de casa.
        val fonteDeRede = OkHttpDataSource.Factory(app.odeon.clienteHttp())
        val fonteComCache = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(app.baixados.cacheDeDownload())
            .setUpstreamDataSourceFactory(fonteDeRede)
            /// Só lê. **Não** escreve: cache de reprodução encheria o disco com
            /// o que a pessoa só passou por cima, e o disco aqui é o dela.
            /// Quem escreve é o download, que foi pedido.
            .setCacheWriteDataSinkFactory(null)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(fonteComCache))
            /// Trata o áudio como música/vídeo de verdade: pausa quando chega
            /// ligação, abaixa quando o GPS fala. Sem isto o filme continua
            /// tocando por cima de tudo, que é o comportamento que faz alguém
            /// desinstalar um app de vídeo.
            .setHandleAudioBecomingNoisy(true)
            .build()

        sessao = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = sessao

    /// Quando a última tela solta o controle e nada está tocando, o serviço vai
    /// embora junto.
    ///
    /// Sem isto ele fica de pé segurando decodificador de hardware depois de o
    /// filme acabar — e o sintoma aparece no **próximo** filme, sem
    /// decodificador disponível, que é o que torna esse vazamento caro de achar.
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = sessao?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        sessao?.run {
            player.release()
            release()
        }
        sessao = null
        super.onDestroy()
    }
}
