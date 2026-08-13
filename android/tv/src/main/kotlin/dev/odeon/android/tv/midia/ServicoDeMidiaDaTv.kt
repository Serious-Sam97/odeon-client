package dev.odeon.android.tv.midia

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.odeon.android.tv.OdeonTv

/// A sessão de mídia da sala — e o único lugar deste módulo onde um `ExoPlayer`
/// existe.
///
/// ## Por que serviço, e não um player dentro da tela
///
/// No celular a resposta era tela de bloqueio e fone de ouvido. Aqui é outra, e
/// é mais dura de contornar: **o controle remoto**.
///
/// Os botões de play, pause e avanço de um controle de Google TV não chegam à
/// Activity. Eles viram `MediaButton`, que o sistema entrega à sessão de mídia
/// ativa — e se não houver sessão, não são entregues a ninguém. Um player que
/// vive dentro de um composable teria o botão ▶ do controle da TCL sem efeito,
/// que num aparelho onde o controle **é** a única entrada é o defeito inteiro.
///
/// É também o que põe o Odeon no "o que está tocando" da home da TV.
///
/// ## Ela é a do `:app` menos o cache, e isso é uma linha de diferença
///
/// O `ServicoDeMidia` do celular monta um `CacheDataSource` por cima do OkHttp,
/// pra ler do disco o que o download escreveu. Aqui não há download — ver
/// `OdeonTv` pro porquê — então a fonte é o OkHttp direto.
///
/// Não é perda: o cache de lá **só lia**, e o que ele lia era o que o download
/// tinha escrito. Sem download, ele leria um disco sempre vazio.
///
/// ## O OkHttp é o mesmo de sempre
///
/// `OkHttpDataSource.Factory` recebendo `app.odeon.clienteHttp()`. Um pool, um
/// lugar onde o cabeçalho é posto — e uma contabilidade só do token de mídia,
/// que é onde o §43 morde.
///
/// ## O `DefaultMediaSourceFactory` cobre HLS e arquivo direto
///
/// Ele escolhe a fonte pelo tipo do conteúdo, e com o `media3-exoplayer-hls` no
/// classpath (herdado do `:core`) a playlist vira `HlsMediaSource` sozinha. A
/// URL do Odeon carrega `?token=` e nem sempre termina em `.m3u8`, então quem
/// manda o tipo é a tela, no `MediaItem`.
@UnstableApi
class ServicoDeMidiaDaTv : MediaSessionService() {

    private var sessao: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as OdeonTv

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(OkHttpDataSource.Factory(app.odeon.clienteHttp())),
            )
            /// Numa TV "áudio ficando ruidoso" é o cabo do fone sendo tirado de
            /// uma soundbar, ou o HDMI trocando de saída. Continua valendo: sem
            /// isto, o filme segue tocando por cima do que veio depois.
            .setHandleAudioBecomingNoisy(true)
            .build()

        sessao = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = sessao

    /// Quando a tela solta o controle e nada está tocando, o serviço vai embora
    /// junto.
    ///
    /// Sem isto ele fica de pé segurando decodificador de hardware depois de o
    /// filme acabar — e numa TV isso é pior que num celular: o decodificador de
    /// 4K de uma TCL é **um**, e quem o segura impede o próximo filme de abrir.
    /// O sintoma aparece no filme seguinte, que é o que torna esse vazamento
    /// caro de achar.
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
