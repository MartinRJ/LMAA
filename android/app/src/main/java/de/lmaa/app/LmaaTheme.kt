package de.lmaa.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LmaaLightColors = lightColorScheme(
    primary = Color(0xFF5E5879),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6E0FF),
    onPrimaryContainer = Color(0xFF1A1432),
    secondary = Color(0xFF5D6251),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E7D4),
    onSecondaryContainer = Color(0xFF1A1E13),
    tertiary = Color(0xFF4F665D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD2E9DF),
    onTertiaryContainer = Color(0xFF0B2019),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFAF5),
    onBackground = Color(0xFF1B1D18),
    surface = Color(0xFFFAFAF5),
    onSurface = Color(0xFF1B1D18),
    surfaceVariant = Color(0xFFE4E5DC),
    onSurfaceVariant = Color(0xFF46483F),
    outline = Color(0xFF777970),
    outlineVariant = Color(0xFFC7C9BF),
    inverseSurface = Color(0xFF30312C),
    inverseOnSurface = Color(0xFFF2F1EB),
    inversePrimary = Color(0xFFC8C0E8),
    surfaceTint = Color(0xFF5E5879),
)

private val LmaaDarkColors = darkColorScheme(
    primary = Color(0xFFC8C0E8),
    onPrimary = Color(0xFF302A4A),
    primaryContainer = Color(0xFF464060),
    onPrimaryContainer = Color(0xFFE6E0FF),
    secondary = Color(0xFFC5CBB7),
    onSecondary = Color(0xFF2F3426),
    secondaryContainer = Color(0xFF454B3B),
    onSecondaryContainer = Color(0xFFE1E7D4),
    tertiary = Color(0xFFB6CCC2),
    onTertiary = Color(0xFF21372F),
    tertiaryContainer = Color(0xFF374E46),
    onTertiaryContainer = Color(0xFFD2E9DF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF12140F),
    onBackground = Color(0xFFE4E4DD),
    surface = Color(0xFF12140F),
    onSurface = Color(0xFFE4E4DD),
    surfaceVariant = Color(0xFF46483F),
    onSurfaceVariant = Color(0xFFC7C9BF),
    outline = Color(0xFF91938A),
    outlineVariant = Color(0xFF46483F),
    inverseSurface = Color(0xFFE4E4DD),
    inverseOnSurface = Color(0xFF30312C),
    inversePrimary = Color(0xFF5E5879),
    surfaceTint = Color(0xFFC8C0E8),
)

@Composable
fun LmaaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) LmaaDarkColors else LmaaLightColors,
        content = content,
    )
}
