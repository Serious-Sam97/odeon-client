package dev.odeon.app

import androidx.compose.ui.window.ComposeUIViewController
import dev.odeon.shared.OdeonRepository

/**
 * Ponto de entrada chamado pelo Swift. O `iosApp` é uma casca: cria este
 * controller e o resto é o mesmo Compose que roda no celular Android.
 */
private val repository by lazy { OdeonRepository() }

fun MainViewController() = ComposeUIViewController { App(repository) }
