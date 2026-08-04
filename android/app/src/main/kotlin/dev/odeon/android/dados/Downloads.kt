package dev.odeon.android.dados

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.Executors

/// O que o app guarda sobre um arquivo baixado, além dos bytes.
///
/// Vai serializado dentro do `data` do próprio download do Media3 — ver
/// `Baixados` pro porquê de não haver uma segunda tabela.
@Serializable
data class FichaDoDownload(
    val obraId: String,
    val arquivoId: String,
    val titulo: String,
    val poster: String? = null,
    /// De onde veio, e é isto que decide se expira. Ver `OrigemDoDownload`.
    val origem: String = OrigemDoDownload.BIBLIOTECA.name,
    /// Epoch em ms. `null` quando não expira — todo download de biblioteca.
    @kotlinx.serialization.SerialName("vence_em") val venceEm: Long? = null,
    @kotlinx.serialization.SerialName("duracao_segundos") val duracaoEmSegundos: Double? = null,
) {
    val origemTipada: OrigemDoDownload
        get() = runCatching { OrigemDoDownload.valueOf(origem) }.getOrDefault(OrigemDoDownload.BIBLIOTECA)
}

/// Um download, do jeito que a tela precisa dele.
///
/// As constantes de estado do `Download` são `@UnstableApi`, e por isso a classe
/// carrega o opt-in. O que ela expõe pra fora — `pronto`, `baixando`, `falhou` —
/// **não é**: a tela lê três booleanos e não sabe que o Media3 existe.
@UnstableApi
data class Baixado(
    val id: String,
    val ficha: FichaDoDownload,
    val estado: Int,
    val porcentagem: Float,
    val bytes: Long,
) {
    val pronto: Boolean get() = estado == Download.STATE_COMPLETED
    val baixando: Boolean get() = estado == Download.STATE_DOWNLOADING || estado == Download.STATE_QUEUED
    val falhou: Boolean get() = estado == Download.STATE_FAILED
}

/// A fila de downloads.
///
/// ## Por que **não** tem Room, e isso é diferente do que a espec propôs
///
/// A §4 diz que offline «puxa banco local (Room)». O Media3 já traz um: o
/// `DownloadManager` persiste cada download num índice SQLite próprio, com
/// estado, progresso, retomada e um campo `data` livre por item.
///
/// Pôr Room ao lado disso criaria **duas fontes de verdade sobre os mesmos
/// arquivos** — e elas divergem no dia em que a política de espaço apagar um
/// download por dentro do Media3 sem avisar a tabela. Uma tela listando um filme
/// que não existe mais no disco é pior que não ter tela.
///
/// Então a ficha do app (de onde veio, quando vence, o título) mora **dentro** do
/// download, no `data`. Um lugar só, apagado junto com os bytes.
///
/// ⚠️ Room continua fazendo falta — mas pra **outra** coisa: navegar o acervo sem
/// rede, que é catálogo e não arquivo. Isso é trabalho separado e não bloqueia
/// baixar nem tocar offline.
@UnstableApi
class Baixados(contexto: Context, cliente: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /// O cache de download, separado do cache de reprodução.
    ///
    /// `NoOpCacheEvictor` porque **o Media3 não pode decidir apagar sozinho**: um
    /// filme baixado de propósito não é cache, é arquivo que a pessoa pediu. Quem
    /// apaga é ela, na tela de downloads — ou a política de espaço, quando
    /// houver, e aí é decisão explícita e não despejo silencioso.
    private val cache: SimpleCache by lazy {
        SimpleCache(
            File(contexto.getExternalFilesDir(null) ?: contexto.filesDir, "baixados"),
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(contexto),
        )
    }

    val gerente: DownloadManager by lazy {
        DownloadManager(
            contexto,
            StandaloneDatabaseProvider(contexto),
            cache,
            OkHttpDataSource.Factory(cliente),
            Executors.newFixedThreadPool(2),
        ).apply {
            /// Dois de cada vez. Num servidor de casa que também transcodifica,
            /// seis downloads paralelos competem com quem está assistindo agora.
            maxParallelDownloads = 2
        }
    }

    fun cacheDeDownload(): SimpleCache = cache

    /// Põe um arquivo na fila.
    ///
    /// A `url` já vem com o token de mídia. O `customCacheKey` é o id do arquivo
    /// — sem ele, o cache indexaria pela URL, e a URL **muda quando o token de
    /// mídia é renovado** (§43). O mesmo filme viraria dois downloads.
    fun baixar(url: String, ficha: FichaDoDownload) {
        val pedido = DownloadRequest.Builder(ficha.arquivoId, android.net.Uri.parse(url))
            .setCustomCacheKey(ficha.arquivoId)
            .setData(json.encodeToString(FichaDoDownload.serializer(), ficha).toByteArray())
            .build()
        gerente.addDownload(pedido)
    }

    fun apagar(arquivoId: String) = gerente.removeDownload(arquivoId)

    /// O que está no disco, com a ficha de volta.
    fun lista(): List<Baixado> = buildList {
        val cursor = gerente.downloadIndex.getDownloads()
        cursor.use {
            while (it.moveToNext()) {
                val d = it.download
                val ficha = runCatching {
                    json.decodeFromString(FichaDoDownload.serializer(), String(d.request.data))
                }.getOrNull() ?: continue
                add(
                    Baixado(
                        id = d.request.id,
                        ficha = ficha,
                        estado = d.state,
                        porcentagem = d.percentDownloaded.takeIf { p -> p >= 0f } ?: 0f,
                        bytes = d.bytesDownloaded,
                    ),
                )
            }
        }
    }
}
