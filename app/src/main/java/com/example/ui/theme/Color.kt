package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Splash Screen & Brand Colors
val SplashBgColor = Color(0xFF0A1A2A)      // Exact background sampled from the splash screen
val GoldenAccent = Color(0xFFEAB308)       // Exact gold from the splash screen
val GoldenTrack = Color(0xFFFACC15)
val TextOffWhite = Color(0xFFF1F5F9)

// Bioluminescent Accents
val SlumberGreen = Color(0xFF00E5A3)
val StirringAmber = Color(0xFFFFB300)
val StrikeMagenta = Color(0xFFFF2A6D)
val ElectricCyan = Color(0xFF00E5FF)
val FogMutedBlue = Color(0xFF5A6E85)

// Text & Accents (Default)
val TextHigh = Color(0xFFF1F5F9)
val TextMedium = Color(0xFF94A3B8)
val TextDim = Color(0xFF475569)

val PrimaryEmerald = Color(0xFF00E5A3)
val SecondaryCyan = Color(0xFF00D1FF)
val TertiaryViolet = Color(0xFFB388FF)

@Immutable
data class GoblinThemeColors(
    val isDark: Boolean,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val blob1: Color,
    val blob2: Color,
    val blob3: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val cardStroke: Color,
    val cardMuted: Color,
    val textTitle: Color,
    val textDesc: Color,
    val accentGold: Color,
    val accentGoldTrack: Color,
    val buttonDark: Color,
    val buttonText: Color,
    val navBg: Color,
    val navSelected: Color,
    val navUnselected: Color,
    val navIndicator: Color,
    val chipSelectedBg: Color,
    val chipSelectedText: Color,
    val chipSelectedBorder: Color,
    val chipUnselectedBg: Color,
    val chipUnselectedText: Color,
    val chipUnselectedBorder: Color,
    val badgeBg: Color,
    val badgeIconTint: Color
) {
    val screenGradient: Brush
        get() = Brush.verticalGradient(listOf(backgroundTop, backgroundBottom))
}

// Dark Mode Palette: Matches the Splash Screen (#0A1A2A background + Sacred Gold #EAB308)
val GoblinDarkThemeColors = GoblinThemeColors(
    isDark = true,
    backgroundTop = Color(0xFF0A1A2A),       // Exact splash background
    backgroundBottom = Color(0xFF060F1A),    // Deep midnight gradient
    blob1 = Color(0xFFEAB308).copy(alpha = 0.12f), // Golden celestial nebula
    blob2 = Color(0xFF0E3854).copy(alpha = 0.45f), // Deep cosmic navy blob
    blob3 = Color(0xFF133658).copy(alpha = 0.40f), // Celestial blue blob
    cardBg = Color(0xFF0D2035).copy(alpha = 0.72f), // Translucent frosted dark glass
    cardBorder = Color(0xFF1E3D5F).copy(alpha = 0.65f), // Crisp cosmic rim
    cardStroke = Color(0xFF1A334D).copy(alpha = 0.50f),
    cardMuted = Color(0xFF081524).copy(alpha = 0.75f),
    textTitle = Color(0xFFF1F5F9),           // High-contrast off-white
    textDesc = Color(0xFF94A3B8),            // Refined slate
    accentGold = Color(0xFFEAB308),          // Splash golden accent
    accentGoldTrack = Color(0xFFFACC15),     // Bright gold slider track
    buttonDark = Color(0xFF142B45),          // Deep cosmic navy button
    buttonText = Color(0xFFF1F5F9),          // Off-white button text
    navBg = Color(0xFF0A1A2A).copy(alpha = 0.90f), // Translucent navy nav
    navSelected = Color(0xFFEAB308),         // Gold selected
    navUnselected = Color(0xFF64748B),       // Muted slate unselected
    navIndicator = Color(0xFFEAB308).copy(alpha = 0.22f),
    chipSelectedBg = Color(0xFFEAB308).copy(alpha = 0.22f),
    chipSelectedText = Color(0xFFFDE047),
    chipSelectedBorder = Color(0xFFEAB308),
    chipUnselectedBg = Color(0xFF0D2035).copy(alpha = 0.65f),
    chipUnselectedText = Color(0xFF94A3B8),
    chipUnselectedBorder = Color(0xFF1E3D5F).copy(alpha = 0.55f),
    badgeBg = Color(0xFF132F4C),
    badgeIconTint = Color(0xFFEAB308)
)

// Light Mode Palette: The original soft lavender & sky frosted glass
val GoblinLightThemeColors = GoblinThemeColors(
    isDark = false,
    backgroundTop = Color(0xFFEFE9FF),
    backgroundBottom = Color(0xFFE3F3FF),
    blob1 = Color(0xFFD6C6FF).copy(alpha = 0.55f),
    blob2 = Color(0xFFBFE4FF).copy(alpha = 0.55f),
    blob3 = Color(0xFFFFE1C7).copy(alpha = 0.50f),
    cardBg = Color(0xFFFFFFFF).copy(alpha = 0.55f),
    cardBorder = Color(0xFFFFFFFF).copy(alpha = 0.65f),
    cardStroke = Color(0xFFFFFFFF).copy(alpha = 0.45f),
    cardMuted = Color(0xFFFFFFFF).copy(alpha = 0.40f),
    textTitle = Color(0xFF241B3D),
    textDesc = Color(0xFF6B6480),
    accentGold = Color(0xFFEAB308),
    accentGoldTrack = Color(0xFFFACC15),
    buttonDark = Color(0xFF1E1533),
    buttonText = Color.White,
    navBg = Color(0xFFFFFFFF).copy(alpha = 0.75f),
    navSelected = Color(0xFF241B3D),
    navUnselected = Color(0xFF8A86A0),
    navIndicator = Color(0xFFEAB308).copy(alpha = 0.25f),
    chipSelectedBg = Color(0xFFEAB308).copy(alpha = 0.25f),
    chipSelectedText = Color(0xFF8A6200),
    chipSelectedBorder = Color(0xFFEAB308),
    chipUnselectedBg = Color.White.copy(alpha = 0.60f),
    chipUnselectedText = Color(0xFF241B3D),
    chipUnselectedBorder = Color(0xFFFFFFFF).copy(alpha = 0.40f),
    badgeBg = Color(0xFFFEF3C7),
    badgeIconTint = Color(0xFFEAB308)
)

val LocalGoblinThemeColors = staticCompositionLocalOf { GoblinDarkThemeColors }

