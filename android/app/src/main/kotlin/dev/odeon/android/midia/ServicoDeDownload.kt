package dev.odeon.android.midia

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import dev.odeon.android.OdeonApp

/// O serviço que baixa.
///
/// ## Por que serviço, e por que este e não um `GET` grande
///
/// A §4b da espec separa "download de verdade" de "baixar um arquivo": fila com
/// **retomada, pausa e limite de rede**, e não uma requisição gigante que morre
/// junto com a tela. O `DownloadService` do Media3 é isso pronto — e o
/// `/api/stream` do servidor já fala `Range`, que é o que a retomada precisa.
///
/// Rodar em primeiro plano é o que mantém o download vivo com o app fechado. Sem
/// isso, um filme de 4 GB só desce enquanto a pessoa olha a tela — que é
/// exatamente o oposto do que "baixar pra ver depois" quer dizer.
@UnstableApi
class ServicoDeDownload : DownloadService(
    NOTIFICACAO_EM_ANDAMENTO,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CANAL,
    dev.odeon.android.R.string.canal_de_downloads,
    0,
) {

    override fun getDownloadManager(): DownloadManager =
        (application as OdeonApp).baixados.gerente

    /// Sem agendador.
    ///
    /// O `WorkManagerScheduler` retomaria a fila sozinho quando a rede voltasse,
    /// e ele entra quando houver política de rede ("só no Wi-Fi") pra ele
    /// respeitar. Hoje ele só adiantaria trabalho sem ninguém ter pedido — e num
    /// servidor de casa, baixar 4 GB por decisão do aparelho é o tipo de coisa
    /// que aparece na conta de outra pessoa.
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        naoPermitidos: Int,
    ): Notification =
        androidx.media3.exoplayer.offline.DownloadNotificationHelper(this, CANAL)
            .buildProgressNotification(
                this,
                android.R.drawable.stat_sys_download,
                null,
                null,
                downloads,
                naoPermitidos,
            )

    private companion object {
        const val NOTIFICACAO_EM_ANDAMENTO = 2001
        const val CANAL = "downloads"
    }
}
