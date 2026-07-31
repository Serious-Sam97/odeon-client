package dev.odeon.shared

/**
 * Armazenamento de preferências. Só guarda a URL do servidor e o id do
 * aparelho — não vale a pena arrastar DataStore/multiplatform-settings pra isso.
 */
expect object Prefs {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/**
 * URL padrão do servidor por plataforma.
 *
 * No emulador Android, `localhost` é o próprio emulador; o host é 10.0.2.2.
 * Errar isso é a primeira coisa que faz o app "não conectar" sem explicação.
 */
expect fun defaultBaseUrl(): String

expect val platformName: String
