package dev.odeon.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Os mesmos tokens do cliente web, pra as duas superfícies parecerem o mesmo produto. */
object OdeonColors {
    val bg = Color(0xFF0A0A0C)
    val raised = Color(0xFF131318)
    val line = Color(0xFF23232C)
    val fg = Color(0xFFECEEF4)
    val muted = Color(0xFF8B8D9A)
    val accent = Color(0xFFE0B062)
    val danger = Color(0xFFFF6B6B)
}

/** "#e0b062" vindo do backend → Color. Cor inválida não derruba a tela. */
fun parseHexColor(hex: String?): Color? {
    val cleaned = hex?.trim()?.removePrefix("#") ?: return null
    if (cleaned.length != 6) return null
    return runCatching { Color(cleaned.toLong(16) or 0xFF000000) }.getOrNull()
}

@Composable
fun OdeonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = OdeonColors.accent,
            onPrimary = Color(0xFF17130A),
            background = OdeonColors.bg,
            onBackground = OdeonColors.fg,
            surface = OdeonColors.raised,
            onSurface = OdeonColors.fg,
            surfaceVariant = OdeonColors.line,
            onSurfaceVariant = OdeonColors.muted,
            error = OdeonColors.danger,
        ),
        content = content,
    )
}
