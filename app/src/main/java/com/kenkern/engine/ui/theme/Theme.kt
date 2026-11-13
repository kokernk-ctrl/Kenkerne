package com.kenkern.engine.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Design System da KENKERN
 * Identidade: escuro profundo de estúdio com âmbar/laranja como acento.
 * Nada de azul/índigo; superfícies em camadas (0.00 / 0.04 / 0.08 de elevação).
 */

// ---- Paleta base ----
val KenkernAmber = Color(0xFFFFA940)
val KenkernAmberBright = Color(0xFFFFC46B)
val KenkernAmberDim = Color(0xFFB26E1B)

// Camadas de fundo (do mais profundo ao mais elevado)
val KenkernBackground = Color(0xFF0D0D13)
val KenkernSurface = Color(0xFF14141C)
val KenkernSurfaceHigh = Color(0xFF1B1B26)
val KenkernSurfaceOverlay = Color(0xFF232330)
val KenkernOutline = Color(0xFF33334A)
val KenkernOutlineSoft = Color(0xFF262638)

// Texto
val KenkernTextHigh = Color(0xFFF2F2F7)
val KenkernTextMid = Color(0xFFA8A8BC)
val KenkernTextLow = Color(0xFF6E6E86)

// Estados
val KenkernSuccess = Color(0xFF6BD9A0)
val KenkernError = Color(0xFFFF6B5E)
val KenkernInfo = Color(0xFF63D8C6)

// Alcance global (usado nas telas fora do MaterialTheme)
val KenkernAccent = KenkernAmber

private val KenkernColors = darkColorScheme(
    primary = KenkernAmber,
    onPrimary = Color(0xFF241300),
    primaryContainer = KenkernAmberDim,
    onPrimaryContainer = Color(0xFFFFE8CC),
    inversePrimary = KenkernAmberBright,
    secondary = KenkernInfo,
    onSecondary = Color(0xFF00201B),
    secondaryContainer = Color(0xFF1E4A44),
    onSecondaryContainer = Color(0xFFC4F1E8),
    tertiary = KenkernSuccess,
    onTertiary = Color(0xFF002117),
    background = KenkernBackground,
    onBackground = KenkernTextHigh,
    surface = KenkernSurface,
    onSurface = KenkernTextHigh,
    surfaceVariant = KenkernSurfaceHigh,
    onSurfaceVariant = KenkernTextMid,
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFE4E2E9),
    inverseOnSurface = Color(0xFF1B1B1F),
    outline = KenkernOutline,
    outlineVariant = KenkernOutlineSoft,
    error = KenkernError,
    onError = Color(0xFF2B0703),
    errorContainer = Color(0xFF4A1410),
    onErrorContainer = Color(0xFFFFDAD4),
    scrim = Color(0xFF000000)
)

/**
 * Tipografia de ferramenta profissional: hierarquia clara, rótulos compactos
 * com tracking largo para seções (estilo IDE) e corpo confortável em mobile.
 */
private val KenkernTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 58.sp,
        letterSpacing = 4.sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = 2.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.6.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 9.5.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.1.sp
    )
)

/** Tema da KENKERN: sempre dark (engine mobile-first de estúdio). */
@Composable
fun KenkernTheme(content: @Composable () -> Unit) {
    isSystemInDarkTheme() // o tema é dark permanente; leitura só para transitória
    MaterialTheme(
        colorScheme = KenkernColors,
        typography = KenkernTypography,
        content = content
    )
}
