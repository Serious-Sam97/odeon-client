package dev.odeon.tv

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.odeon.shared.OdeonRepository
import dev.odeon.shared.Prefs

class OdeonTvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.appContext = this
    }
}

/**
 * A superfície de TV.
 *
 * Ela **não** reaproveita as telas do celular de propósito: 10-foot UI é outro
 * paradigma — navegação por D-pad, foco explícito, tipografia maior, margens de
 * overscan. O que é compartilhado é tudo abaixo da UI (`:shared`): modelos,
 * cliente HTTP, repositório, reporte de progresso.
 */
class TvActivity : ComponentActivity() {
    private val repository by lazy { OdeonRepository() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TvApp(repository) }
    }
}
