package com.kenkern.engine.ui.theme

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
 * Design System da KENKERN (paleta NEUTRA de ferramenta).
 *  - Fundo #1E1E1E, painéis #252526, barras #2D2D30, separadores #3C3C3C.
 *  - Texto principal #CCCCCC, secundário #9D9D9D.
 *  - Âmbar #FFA940 APENAS em seleção, foco e ação principal.
 *  - Ícones de ação em cinza (#CCCCCC), sem âmbar.
 *  - Cores de eixo reservadas: X #E5534B, Y #57AB5A, Z #539BF5.
 *  - Tipografia: mínimo 12sp, letterSpacing ZERO em todos os estilos.
 */

// ---- Acento (só seleção/foco/ação principal) ----
val KenkernAmber = Color(0xFFFFA940)
val KenkernAmberBright = Color(0xFFFFC46B)
val KenkernAmberDim = Color(0xFF8A5A17)

// Camadas neutras (do mais profundo ao mais elevado)
val KenkernBackground = Color(0xFF1E1E1E)
val KenkernSurface = Color(0xFF252526)
val KenkernSurfaceHigh = Color(0xFF2D2D30)
val KenkernSurfaceOverlay = Color(0xFF333333)
val KenkernSelection = Color(0xFF37373D)
val KenkernOutline = Color(0xFF3C3C3C)
val KenkernOutlineSoft = Color(0xFF333333)

// Texto
val KenkernTextHigh = Color(0xFFCCCCCC)
val KenkernTextMid = Color(0xFF9D9D9D)
val KenkernTextLow = Color(0xFF6E6E6E)

// Eixos (rótulos do Inspetor e gizmos)
val KenkernAxisX = Color(0xFFE5534B)
val KenkernAxisY = Color(0xFF57AB5A)
val KenkernAxisZ = Color(0xFF539BF5)

// Estados (vermelho só em confirmação/erro real)
val KenkernSuccess = Color(0xFF57AB5A)
val KenkernError = Color(0xFFE5534B)
val KenkernInfo = Color(0xFF9D9D9D)

// Alcance global (usado nas telas fora do MaterialTheme)
val KenkernAccent = KenkernAmber

private val KenkernColors = darkColorScheme(
    primary = KenkernAmber,
    onPrimary = Color(0xFF1E1E1E),
    primaryContainer = KenkernAmberDim,
    onPrimaryContainer = Color(0xFFFFE8CC),
    inversePrimary = KenkernAmberBright,
    secondary = KenkernTextMid,
    onSecondary = KenkernBackground,
    secondaryContainer = KenkernSurfaceHigh,
    onSecondaryContainer = KenkernTextHigh,
    tertiary = KenkernSuccess,
    onTertiary = Color(0xFF1E1E1E),
    background = KenkernBackground,
    onBackground = KenkernTextHigh,
    surface = KenkernSurface,
    onSurface = KenkernTextHigh,
    surfaceVariant = KenkernSurfaceHigh,
    onSurfaceVariant = KenkernTextMid,
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFCCCCCC),
    inverseOnSurface = Color(0xFF1E1E1E),
    outline = KenkernOutline,
    outlineVariant = KenkernOutlineSoft,
    error = KenkernError,
    onError = Color(0xFF1E1E1E),
    errorContainer = Color(0xFF3C2624),
    onErrorContainer = Color(0xFFFFDAD4),
    scrim = Color(0xFF000000)
)

/**
 * Tipografia de ferramenta: hierarquia por peso/tamanho, SEM tracking
 * (letterSpacing zero), mínimo 12sp em todos os estilos.
 */
private val KenkernTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 56.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
)

/** Tema da KENKERN: dark neutro permanente (ferramenta de estúdio). */
@Composable
fun KenkernTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KenkernColors,
        typography = KenkernTypography,
        content = content
    )
}
