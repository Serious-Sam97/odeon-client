package dev.odeon.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

private const val KEY_BASE_URL = "base_url"
private const val KEY_DEVICE_ID = "device_id"
private const val KEY_TOKEN = "auth_token"

/** Estado da biblioteca, compartilhado por celular, TV e iOS. */
data class LibraryState(
    val loading: Boolean = true,
    /// null enquanto não se sabe; a UI mostra login quando fica false.
    val authenticated: Boolean? = null,
    val user: AuthUser? = null,
    val needsSetup: Boolean = false,
    val works: List<WorkListItem> = emptyList(),
    val continueWatching: List<WorkListItem> = emptyList(),
    val tags: List<TagRow> = emptyList(),
    val filter: LibraryFilter = LibraryFilter(),
    val error: String? = null,
    val connected: Boolean = false,
)

/**
 * Toda a lógica não-visual dos três clientes vive aqui.
 *
 * Cada superfície (celular, TV, iOS) desenha à sua maneira e consome o mesmo
 * [state]. É o que faz "4 alvos, 2 codebases" ser sustentável: o que diverge é
 * navegação e player, não regra de negócio.
 */
class OdeonRepository(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    /** Estável entre execuções: é o que faz o sync do M3 ignorar o próprio eco. */
    val deviceId: String = Prefs.get(KEY_DEVICE_ID) ?: run {
        val generated = "$platformName-${Random.nextLong(1_000_000_000L, 9_999_999_999L)}"
        Prefs.put(KEY_DEVICE_ID, generated)
        generated
    }

    var baseUrl: String = Prefs.get(KEY_BASE_URL) ?: defaultBaseUrl()
        private set

    private var token: String? = Prefs.get(KEY_TOKEN)?.takeIf { it.isNotEmpty() }

    private var client = OdeonClient(baseUrl) { token }

    /**
     * Descobre o endereço a partir do que foi digitado.
     *
     * Tenta https antes de http (ver [ServerUrl.candidates]) e fica com o
     * primeiro que responder `/api/health`. Assim "qual esquema?" deixa de ser
     * pergunta pra quem só quer assistir.
     *
     * Devolve mensagem de erro, ou `null` se conectou.
     */
    suspend fun connect(input: String): String? {
        val options = ServerUrl.candidates(input)
        if (options.isEmpty()) return "endereço inválido"

        for (option in options) {
            val probe = OdeonClient(option) { token }
            val ok = runCatching { probe.health() }.isSuccess
            probe.close()
            if (ok) {
                applyBaseUrl(option)
                checkAuth()
                return null
            }
        }

        // Todos falharam: a mensagem cita o que foi tentado, senão vira
        // adivinhação de qual porta o servidor usa.
        return "não respondeu em " + options.joinToString(" nem ")
    }

    private fun applyBaseUrl(url: String) {
        if (url == baseUrl) return
        Prefs.put(KEY_BASE_URL, url)
        baseUrl = url
        client.close()
        client = OdeonClient(url) { token }
    }

    /** Define sem sondar. Use [connect] quando a entrada vier do usuário. */
    fun setBaseUrl(url: String) {
        val normalized = ServerUrl.normalize(url)?.let {
            if (ServerUrl.isSecure(it) || it.startsWith("http://")) it else "http://$it"
        } ?: defaultBaseUrl()
        applyBaseUrl(normalized)
        checkAuth()
    }

    val isSecure: Boolean get() = ServerUrl.isSecure(baseUrl)

    fun streamUrl(mediaFileId: String) = client.streamUrl(mediaFileId)
    fun artworkUrl(path: String) = client.artworkUrl(path)
    fun spriteUrl(path: String) = client.spriteUrl(path)

    fun setFilter(filter: LibraryFilter) {
        _state.value = _state.value.copy(filter = filter)
        refresh()
    }

    /// Decide entre login, setup e biblioteca. Chamado no boot e a cada troca
    /// de servidor — o token de um servidor não vale no outro.
    fun checkAuth() {
        scope.launch {
            val status = runCatching { client.authStatus() }.getOrNull()
            if (status == null) {
                _state.value = _state.value.copy(
                    loading = false,
                    connected = false,
                    authenticated = false,
                    error = "não consegui falar com $baseUrl",
                )
                return@launch
            }
            if (status.needsSetup) {
                _state.value = _state.value.copy(
                    loading = false, connected = true,
                    authenticated = false, needsSetup = true, error = null,
                )
                return@launch
            }
            val user = if (token != null) runCatching { client.me() }.getOrNull() else null
            if (user == null) {
                token = null
                _state.value = _state.value.copy(
                    loading = false, connected = true,
                    authenticated = false, needsSetup = false, user = null,
                )
            } else {
                _state.value = _state.value.copy(authenticated = true, user = user, error = null)
                refresh()
            }
        }
    }

    suspend fun signIn(username: String, password: String, isSetup: Boolean): String? {
        // Se ainda não conectou, não adianta mandar senha pro lugar errado.
        val result = runCatching {
            if (isSetup) client.setup(username, password, platformName)
            else client.login(username, password, platformName)
        }
        return result.fold(
            onSuccess = { response ->
                token = response.token
                Prefs.put(KEY_TOKEN, response.token)
                _state.value = _state.value.copy(
                    authenticated = true, user = response.user, needsSetup = false, error = null,
                )
                refresh()
                null
            },
            // Mensagem única: o servidor não distingue usuário errado de senha
            // errada, e o cliente não deve inventar essa distinção.
            onFailure = { if (isSetup) it.message ?: "falhou" else "usuário ou senha incorretos" },
        )
    }

    fun signOut() {
        token = null
        Prefs.put(KEY_TOKEN, "")
        _state.value = LibraryState(loading = false, authenticated = false, connected = true)
    }

    fun refresh() {
        scope.launch {
            _state.value = _state.value.copy(loading = true)
            try {
                val filter = _state.value.filter
                val works = withContext(Dispatchers.Default) { client.works(filter) }
                val resume = withContext(Dispatchers.Default) { client.continueWatching() }
                val tags = withContext(Dispatchers.Default) { client.tags() }
                // `copy`, não `LibraryState(...)`: construir um estado novo
                // zeraria `authenticated` de volta pra null e a UI voltaria
                // pra tela de "ainda não sei quem é" logo depois do login.
                _state.value = _state.value.copy(
                    loading = false,
                    works = works,
                    continueWatching = resume,
                    tags = tags.filter { it.workCount > 0 },
                    filter = filter,
                    error = null,
                    connected = true,
                )
            } catch (e: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    connected = false,
                    // A mensagem crua do Ktor é o que o usuário precisa pra
                    // saber se errou o IP ou se o servidor está fora.
                    error = e.message ?: "não consegui falar com $baseUrl",
                )
            }
        }
    }

    suspend fun spriteInfo(mediaFileId: String): SpriteInfo? =
        runCatching { client.spriteInfo(mediaFileId) }.getOrNull()

    suspend fun collectionTree(): List<CollectionNode> =
        runCatching { client.collectionTree() }.getOrElse { emptyList() }

    /** Dispara e esquece: falhar em reportar progresso não pode travar o player. */
    fun reportProgress(
        workId: String,
        positionSeconds: Double,
        durationSeconds: Double?,
        mediaFileId: String?,
        eventType: String,
    ) {
        scope.launch {
            runCatching {
                client.reportProgress(
                    workId,
                    ProgressReport(
                        positionSeconds = positionSeconds,
                        durationSeconds = durationSeconds,
                        mediaFileId = mediaFileId,
                        eventType = eventType,
                        client = platformName,
                        deviceId = deviceId,
                    ),
                )
            }
        }
    }
}
