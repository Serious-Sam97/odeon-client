package dev.odeon.shared

import platform.Foundation.NSUserDefaults

actual object Prefs {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun get(key: String): String? = defaults.stringForKey(key)

    actual fun put(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }
}

// O simulador do iOS compartilha a rede do Mac, então localhost já resolve.
// É só o chute inicial; a tela de login sonda https antes de http.
actual fun defaultBaseUrl(): String = "http://localhost:8080"

actual val platformName: String = "ios"
