package com.mipropiobanco.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mipropiobanco.data.prefs.ModoTema

private val EsquemaClaro = lightColorScheme(
    primary = Color(0xFF00875A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC6F6DD),
    onPrimaryContainer = Color(0xFF002113),
    secondary = Color(0xFF3D5AFE),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E5FF),
    onSecondaryContainer = Color(0xFF00105C),
    tertiary = Color(0xFFB26A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE3BF),
    onTertiaryContainer = Color(0xFF2C1800),
    error = Color(0xFFD32F2F),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF151A22),
    surface = Color(0xFFF4F6FA),
    onSurface = Color(0xFF151A22),
    surfaceVariant = Color(0xFFE3E8F0),
    onSurfaceVariant = Color(0xFF4A5363),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F3F8),
    surfaceContainerHighest = Color(0xFFE8ECF3),
    outline = Color(0xFF7A8496),
    outlineVariant = Color(0xFFD5DBE5)
)

private val EsquemaOscuro = darkColorScheme(
    primary = Color(0xFF4ADE9B),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF0E4D33),
    onPrimaryContainer = Color(0xFFC6F6DD),
    secondary = Color(0xFF9AA8FF),
    onSecondary = Color(0xFF0B1A6E),
    secondaryContainer = Color(0xFF26337A),
    onSecondaryContainer = Color(0xFFE0E5FF),
    tertiary = Color(0xFFFFB95C),
    onTertiary = Color(0xFF462A00),
    tertiaryContainer = Color(0xFF5E3B00),
    onTertiaryContainer = Color(0xFFFFE3BF),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF7A1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B0F17),
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF0B0F17),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF1C2330),
    onSurfaceVariant = Color(0xFFA9B3C4),
    surfaceContainerLowest = Color(0xFF0E131C),
    surfaceContainerLow = Color(0xFF131A25),
    surfaceContainer = Color(0xFF161E2A),
    surfaceContainerHigh = Color(0xFF1C2533),
    surfaceContainerHighest = Color(0xFF232D3D),
    outline = Color(0xFF6B768A),
    outlineVariant = Color(0xFF2A3445)
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val base = Typography()
private val Tipografia = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
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
        typography = Tipografia,
        shapes = Formas,
        content = contenido
    )
}
