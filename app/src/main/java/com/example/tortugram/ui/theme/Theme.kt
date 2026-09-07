package com.example.tortugram.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Tortugram siempre usa el esquema oscuro neutro de la marca,
// con un acento azul eléctrico fresco, sin importar el modo claro/oscuro
// del sistema.
private val TortugramColorScheme = darkColorScheme(
    primary = TortugramPrimary,
    onPrimary = TortugramOnPrimary,
    secondary = TortugramPrimaryLight,
    onSecondary = TortugramBackground,

    background = TortugramBackground,
    onBackground = TortugramOnBackground,

    surface = TortugramSurface,
    onSurface = TortugramOnBackground,

    surfaceVariant = TortugramSurfaceVariant,
    onSurfaceVariant = TortugramOnSurfaceVariant,
)

@Composable
fun TortugramTheme(

    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TortugramColorScheme,
        typography = Typography,
        content = content
    )
}
