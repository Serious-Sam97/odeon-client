package dev.odeon.android.dados

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/// A rede do app: **um** OkHttp, e é o ponto.
///
/// ## Por que uma instância só
///
/// Ela é compartilhada por três coisas, e vai ser por uma quarta:
///
///   1. o Retrofit, que fala a API
///   2. o Coil, que carrega os pôsteres de `/artwork/`
///   3. (fase 2) o Media3, via `media3-datasource-okhttp`
///
/// Um pool de conexões, um cache, um lugar onde o `Authorization` é posto. Dois
/// clientes fariam o player abrir conexão própria — e aí o token de mídia
/// passaria a ter duas contabilidades, que é exatamente onde o §43 morde.
///
/// Isso não é otimização prematura: é a única decisão da fase 1 que fica **cara
/// de desfazer** na fase 2.
object Rede {

    private val json = Json {
        /// O servidor manda campos que este app ainda não lê — `width`,
        /// `video_codec`, `match_confidence`. Sem isto, cada campo novo que o
        /// servidor ganhar quebra o app inteiro em tempo de execução.
        ignoreUnknownKeys = true

        /// Campo ausente cai no valor padrão do modelo em vez de estourar. É o
        /// que faz uma rota que ainda não devolve `total` não derrubar a tela.
        explicitNulls = false
    }

    /// O cliente. Criado uma vez, vive o app inteiro.
    ///
    /// Os tempos são de servidor de casa, não de internet: uma LAN que não
    /// respondeu em 10s não vai responder. O de leitura é maior porque a
    /// primeira listagem do acervo passa por 17.930 obras no Postgres.
    fun cliente(cofre: Cofre, interceptores: List<Interceptor> = emptyList()): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(InterceptorDeSessao(cofre))
            .apply { interceptores.forEach { addInterceptor(it) } }
            .build()

    /// Monta o contrato contra um endereço.
    ///
    /// ## Por que isto é uma função e não um singleton
    ///
    /// O `baseUrl` do Retrofit é fixo na construção, e o endereço do servidor
    /// **muda em tempo de execução** — a pessoa digita na tela de login, e a
    /// tela deixa trocar de servidor depois. Cada troca constrói um contrato
    /// novo; o OkHttp debaixo continua o mesmo, com o pool intacto.
    fun api(base: String, cliente: OkHttpClient): OdeonApi =
        Retrofit.Builder()
            /// A barra no fim não é estética: sem ela o Retrofit descarta o
            /// último segmento do caminho ao juntar com a rota relativa.
            .baseUrl(if (base.endsWith("/")) base else "$base/")
            .client(cliente)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OdeonApi::class.java)
}

/// Põe o `Authorization: Bearer` em toda requisição que tiver token.
///
/// ## Ele lê da memória, não do disco
///
/// Interceptor do OkHttp é síncrono e roda na thread de rede; ele não pode
/// suspender pra consultar o DataStore. Ler de uma cópia em memória mantida
/// pelo `Cofre` é o que evita um `runBlocking` por requisição.
///
/// ## E ele não renova nada
///
/// Um interceptor que reagisse a 401 pedindo token novo seria o caminho curto
/// pro defeito do §43: emitir aposenta o anterior, e o anterior pode estar
/// dentro de um player tocando. Quem decide renovar é a tela, que sabe se tem
/// filme no ar. Aqui, 401 sobe como 401.
private class InterceptorDeSessao(private val cofre: Cofre) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = cofre.sessaoEmMemoria
            ?: return chain.proceed(chain.request())

        val comToken = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(comToken)
    }
}
