package com.moneytracker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Warna semantik tambahan di luar Material ColorScheme. */
@Immutable
data class FinColors(
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val warning: Color,
    val savings: Color,
    val card: Color,
    val cardElevated: Color,
    val stroke: Color,
    val muted: Color,
    val heroGradient: Brush,
    val chart: List<Color>,
    val isDark: Boolean,
)

private val Violet = Color(0xFF7C5CFF)
private val ElectricBlue = Color(0xFF3D8BFF)
private val Mint = Color(0xFF2DD4A4)
private val Coral = Color(0xFFFF5C7A)
private val Amber = Color(0xFFFFB547)

private val chartPalette = listOf(
    Violet, ElectricBlue, Mint, Amber, Coral, Color(0xFF22C3E6), Color(0xFFE879F9), Color(0xFFFF8A4C), Color(0xFF8FA3BF), Color(0xFFA3E635),
)

private val DarkFin = FinColors(
    income = Mint, expense = Coral, transfer = ElectricBlue, warning = Amber, savings = Color(0xFFB57CFF),
    card = Color(0xFF131A2B), cardElevated = Color(0xFF1A2338), stroke = Color(0xFF243049), muted = Color(0xFF8A96B0),
    heroGradient = Brush.linearGradient(listOf(Color(0xFF5B3DF5), Color(0xFF3D6BFF), Color(0xFF1E9BD7))),
    chart = chartPalette, isDark = true,
)

private val LightFin = FinColors(
    income = Color(0xFF0E9F77), expense = Color(0xFFE23B5E), transfer = Color(0xFF2468E0), warning = Color(0xFFD48806), savings = Color(0xFF8B4FE0),
    card = Color(0xFFFFFFFF), cardElevated = Color(0xFFF3F4FA), stroke = Color(0xFFE3E6F0), muted = Color(0xFF6B7489),
    heroGradient = Brush.linearGradient(listOf(Color(0xFF6A4DF7), Color(0xFF3D7BFF), Color(0xFF26A9E0))),
    chart = chartPalette, isDark = false,
)

val LocalFin = staticCompositionLocalOf { DarkFin }

private val DarkScheme = darkColorScheme(
    primary = Violet, onPrimary = Color.White, primaryContainer = Color(0xFF2B2160), onPrimaryContainer = Color(0xFFE2DBFF),
    secondary = ElectricBlue, onSecondary = Color.White, secondaryContainer = Color(0xFF16305C), onSecondaryContainer = Color(0xFFD6E4FF),
    tertiary = Mint, onTertiary = Color(0xFF00382A),
    background = Color(0xFF0B0F1A), onBackground = Color(0xFFE8ECF5),
    surface = Color(0xFF0E1322), onSurface = Color(0xFFE8ECF5),
    surfaceVariant = Color(0xFF1A2338), onSurfaceVariant = Color(0xFF9AA6BF),
    surfaceContainer = Color(0xFF131A2B), surfaceContainerHigh = Color(0xFF1A2338), surfaceContainerHighest = Color(0xFF212B43),
    surfaceContainerLow = Color(0xFF0F1524), surfaceContainerLowest = Color(0xFF0A0E18),
    outline = Color(0xFF34405C), outlineVariant = Color(0xFF243049),
    error = Coral, onError = Color.White,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5B3DF5), onPrimary = Color.White, primaryContainer = Color(0xFFE6E0FF), onPrimaryContainer = Color(0xFF1E0F6B),
    secondary = Color(0xFF2468E0), tertiary = Color(0xFF0E9F77),
    background = Color(0xFFF5F6FB), onBackground = Color(0xFF12162A),
    surface = Color(0xFFF5F6FB), onSurface = Color(0xFF12162A),
    surfaceVariant = Color(0xFFE9EBF4), onSurfaceVariant = Color(0xFF5A6378),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFF0F1F8), surfaceContainerHighest = Color(0xFFE7E9F3),
    outline = Color(0xFFC5CAD8), outlineVariant = Color(0xFFE3E6F0),
    error = Color(0xFFE23B5E),
)

private val Sans = FontFamily.SansSerif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.3.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MoneyTrackerTheme(themePref: String = "dark", content: @Composable () -> Unit) {
    val dark = when (themePref) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    CompositionLocalProvider(LocalFin provides if (dark) DarkFin else LightFin) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object Fin {
    val colors: FinColors @Composable get() = LocalFin.current
}
