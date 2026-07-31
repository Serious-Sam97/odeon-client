package dev.odeon.shared

import android.content.Context
import android.content.SharedPreferences

actual object Prefs {
    /** Preenchido no `onCreate` da Application. */
    lateinit var appContext: Context

    private val store: SharedPreferences by lazy {
        appContext.getSharedPreferences("odeon", Context.MODE_PRIVATE)
    }

    actual fun get(key: String): String? = store.getString(key, null)

    actual fun put(key: String, value: String) {
        store.edit().putString(key, value).apply()
    }
}

// Só o CHUTE INICIAL do emulador — 10.0.2.2 é o host visto de dentro dele.
// Em aparelho de verdade o usuário digita o nome da tailnet e o app sonda
// https antes de http (ver ServerUrl.candidates).
actual fun defaultBaseUrl(): String = "http://10.0.2.2:8080"

actual val platformName: String = "android"
