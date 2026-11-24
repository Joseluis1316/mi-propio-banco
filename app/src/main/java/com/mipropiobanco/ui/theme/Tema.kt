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

/*
 * Paleta "Grafito y Lima": neutros sobrios (como las apps de banca premium) y un solo color de acento.
 * Verde = entra / bien · Coral = sale / alerta · Ámbar = cuidado.
 */
private val EsquemaClaro = lightColorScheme(
    primary = Color(0xFF3F6212),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4F5C8),
    onPrimaryContainer = Color(0xFF17260A),
    secondary = Color(0xFF3B4A6B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6EAF2),
    onSecondaryContainer = Color(0xFF1A2233),
    tertiary = Color(0xFF9A5B00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCEBCF),
    onTertiaryContainer = Color(0xFF2E1B00),
    error = Color(0xFFC8373D),
    errorContainer = Color(0xFFFCE4E4),
    onErrorContainer = Color(0xFF410006),
    background = Color(0xFFF5F5F3),
    onBackground = Color(0xFF141517),
    surface = Color(0xFFF5F5F3),
    onSurface = Color(0xFF141517),
    surfaceVariant = Color(0xFFE7E7E3),
    onSurfaceVariant = Color(0xFF5C5F66),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFEFEC),
    surfaceContainerHighest = Color(0xFFE8E8E4),
    outline = Color(0xFF8A8D93),
    outlineVariant = Color(0xFFDDDDD8)
)

private val EsquemaOscuro = darkColorScheme(
    primary = Color(0xFFB5E86A),
    onPrimary = Color(0xFF1B2A06),
    primaryContainer = Color(0xFF26331A),
    onPrimaryContainer = Color(0xFFDDF5B8),
    secondary = Color(0xFFAEB9D0),
    onSecondary = Color(0xFF1B2232),
    secondaryContainer = Color(0xFF242A35),
    onSecondaryContainer = Color(0xFFDCE2EE),
    tertiary = Color(0xFFF2B84B),
    onTertiary = Color(0xFF3A2500),
    tertiaryContainer = Color(0xFF3A2E17),
    onTertiaryContainer = Color(0xFFFBE3B5),
    error = Color(0xFFFF7A7A),
    errorContainer = Color(0xFF3D1C1E),
    onErrorContainer = Color(0xFFFFD9D9),
    background = Color(0xFF0E0F11),
    onBackground = Color(0xFFF1F2F4),
    surface = Color(0xFF0E0F11),
    onSurface = Color(0xFFF1F2F4),
    surfaceVariant = Color(0xFF23262B),
    onSurfaceVariant = Color(0xFF9EA3AB),
    surfaceContainerLowest = Color(0xFF0B0C0E),
    surfaceContainerLow = Color(0xFF141518),
    surfaceContainer = Color(0xFF17191C),
    surfaceContainerHigh = Color(0xFF1E2024),
    surfaceContainerHighest = Color(0xFF262930),
    outline = Color(0xFF6E737B),
    outlineVariant = Color(0xFF2A2D33)
)

/** Fondo de las tarjetas grandes (antes eran degradados de colores): grafito sobrio. */
val FondoHero = androidx.compose.ui.graphics.Brush.linearGradient(
    listOf(Color(0xFF22252B), Color(0xFF17191D))
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
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
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
