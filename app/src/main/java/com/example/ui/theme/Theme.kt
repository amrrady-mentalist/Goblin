package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val GoblinDarkColorScheme = darkColorScheme(
    primary = GoldenAccent,
    onPrimary = SplashBgColor,
    primaryContainer = Color(0xFF0D2035),
    onPrimaryContainer = GoldenAccent,
    secondary = GoldenTrack,
    onSecondary = SplashBgColor,
    secondaryContainer = Color(0xFF0D2035),
    onSecondaryContainer = GoldenTrack,
    tertiary = StrikeMagenta,
    onTertiary = TextOffWhite,
    background = SplashBgColor,
    onBackground = TextOffWhite,
    surface = Color(0xFF0D2035),
    onSurface = TextOffWhite,
    surfaceVariant = Color(0xFF102842),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF1E3D5F),
    outlineVariant = Color(0xFF64748B)
)

private val GoblinLightColorScheme = lightColorScheme(
    primary = GoldenAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFF241B3D),
    secondary = Color(0xFF241B3D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFFFFF),
    onSecondaryContainer = Color(0xFF241B3D),
    tertiary = StrikeMagenta,
    onTertiary = Color.White,
    background = Color(0xFFEFE9FF),
    onBackground = Color(0xFF241B3D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF241B3D),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF6B6480),
    outline = Color(0xFFD6C6FF),
    outlineVariant = Color(0xFFBFE4FF)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val themeColors = if (darkTheme) GoblinDarkThemeColors else GoblinLightThemeColors
    val colorScheme = if (darkTheme) GoblinDarkColorScheme else GoblinLightColorScheme

    CompositionLocalProvider(LocalGoblinThemeColors provides themeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

