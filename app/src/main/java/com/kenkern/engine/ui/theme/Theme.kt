package com.kenkern.engine.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Identidade visual da KENKERN: escuro profundo com âmbar (sem azul/índigo).
val KenkernAmber = Color(0xFFFFA940)
val KenkernAmberDim = Color(0xFFB26E1B)
val KenkernBackground = Color(0xFF0B0B12)
val KenkernSurface = Color(0xFF15151F)
val KenkernSurfaceHigh = Color(0xFF1E1E2B)
val KenkernOutline = Color(0xFF3C3C4E)

private val KenkernColors = darkColorScheme(
    primary = KenkernAmber,
    onPrimary = Color(0xFF201200),
    primaryContainer = KenkernAmberDim,
    onPrimaryContainer = Color(0xFFFFE8CC),
    secondary = Color(0xFF63D8C6),
    onSecondary = Color(0xFF00201B),
    background = KenkernBackground,
    onBackground = Color(0xFFEDEDF2),
    surface = KenkernSurface,
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = KenkernSurfaceHigh,
    onSurfaceVariant = Color(0xFFB9B9C6),
    outline = KenkernOutline,
    error = Color(0xFFFF6B5E)
)

@Composable
fun KenkernTheme(content: @Composable () -> Unit) {
    isSystemInDarkTheme() // a engine é mobile-first dark; mantém esquema escuro sempre
    MaterialTheme(
        colorScheme = KenkernColors,
        content = content
    )
}
