package dev.odeon.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.odeon.shared.OdeonRepository
import dev.odeon.shared.Prefs

class OdeonApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // O Prefs multiplataforma precisa de Context no Android; é o único
        // lugar onde a plataforma vaza pra dentro do shared.
        Prefs.appContext = this
    }
}

class MainActivity : ComponentActivity() {
    private val repository by lazy { OdeonRepository() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App(repository) }
    }
}
