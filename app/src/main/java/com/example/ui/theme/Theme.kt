package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GoblinDarkColorScheme = darkColorScheme(
    primary = PrimaryEmerald,
    onPrimary = MidnightBg,
    primaryContainer = MidnightCard,
    onPrimaryContainer = PrimaryEmerald,
    secondary = SecondaryCyan,
    onSecondary = MidnightBg,
    secondaryContainer = MidnightCard,
    onSecondaryContainer = SecondaryCyan,
    tertiary = StrikeMagenta,
    onTertiary = TextHigh,
    background = MidnightBg,
    onBackground = TextHigh,
    surface = MidnightSurface,
    onSurface = TextHigh,
    surfaceVariant = MidnightCard,
    onSurfaceVariant = TextMedium,
    outline = MidnightCardBorder,
    outlineVariant = TextDim
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GoblinDarkColorScheme,
        typography = Typography,
        content = content
    )
}
