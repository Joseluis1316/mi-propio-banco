package com.mipropiobanco.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mipropiobanco.data.prefs.ModoTema

private val EsquemaClaro = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F0D4),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4C6358),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9DA),
    onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF2C1800),
    background = Color(0xFFF6FBF7),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF6FBF7),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973)
)

private val EsquemaOscuro = darkColorScheme(
    primary = Color(0xFF7FD9B0),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF005239),
    onPrimaryContainer = Color(0xFFB7F0D4),
    secondary = Color(0xFFB3CCBE),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFFF2BE6B),
    onTertiary = Color(0xFF482A00),
    tertiaryContainer = Color(0xFF684000),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4DF),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4DF),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C1),
    outline = Color(0xFF89938C)
)

@Composable
fun esModoOscuro(modo: ModoTema): Boolean = when (modo) {
    ModoTema.SISTEMA -> isSystemInDarkTheme()
    ModoTema.CLARO -> false
    ModoTema.OSCURO -> true
}

@Composable
fun MiPropioBancoTheme(oscuro: Boolean, contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (oscuro) EsquemaOscuro else EsquemaClaro,
        typography = Typography(),
        content = contenido
    )
}
