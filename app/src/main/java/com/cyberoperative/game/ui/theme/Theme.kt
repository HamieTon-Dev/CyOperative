package com.cyberoperative.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Monospace terminal type, as in CyOps TD (platform font: no APK cost). */
val TerminalFont: FontFamily = FontFamily.Monospace

private val OperativeTypography = Typography(
    displayLarge = TextStyle(fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = 5.sp),
    displayMedium = TextStyle(fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 4.sp),
    headlineMedium = TextStyle(fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 2.sp),
    titleLarge = TextStyle(fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 1.5.sp),
    titleMedium = TextStyle(fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp),
    titleSmall = TextStyle(fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp),
    bodyLarge = TextStyle(fontFamily = TerminalFont, fontSize = 15.sp, letterSpacing = 0.4.sp),
    bodyMedium = TextStyle(fontFamily = TerminalFont, fontSize = 13.sp, letterSpacing = 0.4.sp),
    bodySmall = TextStyle(fontFamily = TerminalFont, fontSize = 11.5.sp, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.2.sp),
    labelMedium = TextStyle(fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp),
    labelSmall = TextStyle(fontFamily = TerminalFont, fontSize = 10.5.sp, letterSpacing = 0.8.sp)
)

private val OperativeColors = darkColorScheme(
    primary = Palette.Cyan,
    onPrimary = Palette.Background,
    secondary = Palette.Green,
    onSecondary = Palette.Background,
    tertiary = Palette.Purple,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceRaised,
    onSurfaceVariant = Palette.TextSecondary,
    error = Palette.Red,
    onError = Palette.TextPrimary,
    outline = Palette.Divider
)

/** Always dark: a terminal in "light mode" would be a different game. */
@Composable
fun CyberOperativeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OperativeColors, typography = OperativeTypography, content = content)
}
