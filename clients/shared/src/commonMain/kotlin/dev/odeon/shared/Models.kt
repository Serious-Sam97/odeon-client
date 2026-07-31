package dev.odeon.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Espelho do que a API do Odeon devolve. Os nomes vêm em snake_case do Rust; o
 * `JsonNamingStrategy.SnakeCase` no [OdeonClient] faz a ponte, então aqui os
 * campos ficam idiomáticos em Kotlin sem 60 anotações `@SerialName`.
 *
 * Só os campos que os apps realmente usam. A API devolve mais — ignorar o resto
 * é de propósito: assim o backend pode crescer sem quebrar o cliente.
 */
@Serializable
data class WorkListItem(
    val id: String,
    val kind: String,
    val title: String,
    val year: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val matchState: String = "unmatched",
    val matchConfidence: Float? = null,
    val dominantColor: String? = null,
    val poster: String? = null,
    val seriesTitle: String? = null,
    val mediaFileId: String? = null,
    val durationSeconds: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val container: String? = null,
    val sizeBytes: Long? = null,
    val positionSeconds: Double? = null,
    val finished: Boolean? = null,
    val tags: List<String>? = null,
) {
    /** Quanto já foi assistido, de 0 a 1. */
    val progress: Float
        get() {
            val duration = durationSeconds ?: return 0f
            val position = positionSeconds ?: return 0f
            if (duration <= 0.0) return 0f
            return (position / duration).coerceIn(0.0, 1.0).toFloat()
        }

    val episodeLabel: String?
        get() {
            val season = seasonNumber ?: return null
            val episode = episodeNumber ?: return null
            return "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
        }
}

@Serializable
data class SpriteInfo(
    val mediaFileId: String,
    val path: String,
    val intervalSeconds: Float,
    val columns: Int,
    val rows: Int,
    val thumbWidth: Int,
    val thumbHeight: Int,
    val frameCount: Int,
) {
    /** Índice da célula que corresponde a um instante do vídeo. */
    fun frameAt(seconds: Double): Int =
        ((seconds / intervalSeconds).toInt()).coerceIn(0, (frameCount - 1).coerceAtLeast(0))
}

@Serializable
data class Collection(
    val id: String,
    val kind: String,
    val parentId: String? = null,
    val title: String,
    val year: Int? = null,
    val description: String? = null,
    val origin: String = "manual",
    val itemCount: Long = 0,
)

@Serializable
data class CollectionNode(
    val id: String,
    val kind: String,
    val parentId: String? = null,
    val title: String,
    val itemCount: Long = 0,
    val children: List<CollectionNode> = emptyList(),
)

@Serializable
data class CollectionDetail(
    val collection: Collection,
    val children: List<Collection> = emptyList(),
    val items: List<WorkListItem> = emptyList(),
)

@Serializable
data class ProgressReport(
    val positionSeconds: Double,
    val durationSeconds: Double? = null,
    val mediaFileId: String? = null,
    val eventType: String = "progress",
    val client: String,
    val deviceId: String,
)

@Serializable
data class Health(
    val status: String,
    val db: Boolean = false,
    val version: String = "",
)

/** Filtro da biblioteca. Espelha os parâmetros de `GET /api/works` do M2. */
data class LibraryFilter(
    val query: String? = null,
    val tags: List<String> = emptyList(),
    val tagMode: String = "all",
    val minMinutes: Int? = null,
    val maxMinutes: Int? = null,
    val collection: String? = null,
    val state: String? = null,
    val sort: String = "title",
)

@Serializable
data class TagRow(
    val id: String,
    val namespace: String,
    val value: String,
    val color: String? = null,
    val workCount: Long = 0,
) {
    /** A chave que o filtro do backend espera: `namespace:valor`. */
    val key: String get() = "$namespace:$value"
}

// ------------------------------------------------------------ autenticação

@Serializable
data class AuthStatus(val needsSetup: Boolean = false)

@Serializable
data class AuthUser(
    val id: String,
    val username: String,
    val displayName: String,
    val role: String = "user",
    val isActive: Boolean = true,
) {
    val isAdmin: Boolean get() = role == "admin"
}

@Serializable
data class Credentials(
    val username: String,
    val password: String,
    val deviceLabel: String? = null,
)

@Serializable
data class LoginResponse(val token: String, val user: AuthUser)
