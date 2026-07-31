package dev.odeon.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Cliente HTTP do Odeon.
 *
 * Um objeto só por servidor; a URL base é imutável. Trocar de servidor cria um
 * cliente novo — evita o estado meio-trocado que aparece quando a base muda por
 * baixo de requisições em voo.
 */
class OdeonClient(val baseUrl: String, private val token: () -> String?) {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        // O Rust serializa em snake_case; isto evita `@SerialName` em cada campo.
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    private val http = HttpClient {
        install(ContentNegotiation) { json(json) }
        // Toda chamada de API leva o Bearer. As URLs de mídia não passam por
        // aqui — o ExoPlayer/AVPlayer busca sozinho, e por isso levam ?token=.
        install(DefaultRequest) {
            token()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
    }

    fun close() = http.close()

    // --- descoberta -------------------------------------------------------

    suspend fun health(): Health = http.get(url("/api/health")).body()

    suspend fun authStatus(): AuthStatus = http.get(url("/api/auth/status")).body()

    suspend fun login(username: String, password: String, device: String): LoginResponse =
        http.post(url("/api/auth/login")) {
            contentType(ContentType.Application.Json)
            setBody(Credentials(username, password, device))
        }.body()

    suspend fun setup(username: String, password: String, device: String): LoginResponse =
        http.post(url("/api/auth/setup")) {
            contentType(ContentType.Application.Json)
            setBody(Credentials(username, password, device))
        }.body()

    suspend fun me(): AuthUser = http.get(url("/api/auth/me")).body()

    // --- biblioteca -------------------------------------------------------

    suspend fun works(filter: LibraryFilter = LibraryFilter(), limit: Int = 300): List<WorkListItem> =
        http.get(url("/api/works")) {
            parameter("limit", limit)
            parameter("sort", filter.sort)
            filter.query?.takeIf { it.isNotBlank() }?.let { parameter("q", it) }
            if (filter.tags.isNotEmpty()) {
                parameter("tags", filter.tags.joinToString(","))
                parameter("tag_mode", filter.tagMode)
            }
            filter.minMinutes?.let { parameter("min_minutes", it) }
            filter.maxMinutes?.let { parameter("max_minutes", it) }
            filter.collection?.let { parameter("collection", it) }
            filter.state?.let { parameter("state", it) }
        }.body()

    suspend fun continueWatching(): List<WorkListItem> =
        http.get(url("/api/continue")).body()

    suspend fun tags(): List<TagRow> = http.get(url("/api/tags")).body()

    suspend fun collectionTree(): List<CollectionNode> =
        http.get(url("/api/collections/tree")).body()

    suspend fun collection(id: String): CollectionDetail =
        http.get(url("/api/collections/$id")).body()

    // --- reprodução -------------------------------------------------------

    fun streamUrl(mediaFileId: String): String = url("/api/stream/$mediaFileId")

    fun artworkUrl(path: String): String = url("/artwork/$path")

    fun spriteUrl(path: String): String = url("/scrub/$path")

    /** `null` quando a folha ainda não foi gerada — o player só perde o preview. */
    suspend fun spriteInfo(mediaFileId: String): SpriteInfo? {
        val response: HttpResponse = http.get(url("/api/media/$mediaFileId/scrub"))
        return if (response.status == HttpStatusCode.OK) response.body() else null
    }

    suspend fun reportProgress(workId: String, report: ProgressReport) {
        http.post(url("/api/works/$workId/progress")) {
            contentType(ContentType.Application.Json)
            setBody(report)
        }
    }

    fun eventsUrl(): String = url("/api/events")

    private fun url(path: String): String = baseUrl.trimEnd('/') + path
}
