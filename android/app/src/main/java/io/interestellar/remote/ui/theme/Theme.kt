package io.interestellar.remote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.interestellar.remote.R

private val DarkColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF03131B),
    primaryContainer = Color(0xFF163142),
    onPrimaryContainer = Color(0xFFE8FCFF),
    secondary = BrandPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF222E57),
    onSecondaryContainer = Color(0xFFE8EBFF),
    tertiary = BrandGold,
    onTertiary = Color(0xFF281500),
    tertiaryContainer = Color(0xFF453014),
    onTertiaryContainer = Color(0xFFFFECD0),
    background = DeepSpace,
    onBackground = SpaceHighlight,
    surface = SpaceSurface,
    onSurface = SpaceHighlight,
    surfaceVariant = SpaceElevated,
    onSurfaceVariant = Color(0xFFB4C2DA),
    outline = Color(0xFF43516C),
    outlineVariant = Color(0xFF24324A),
    error = StatusError,
    onError = Color(0xFF350008),
    errorContainer = Color(0xFF4C1620),
    onErrorContainer = Color(0xFFFFDCE1),
)

private val DisplayFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold),
)

private val BodyFamily = FontFamily(
    Font(R.font.manrope, FontWeight.Normal),
    Font(R.font.manrope, FontWeight.Medium),
    Font(R.font.manrope, FontWeight.SemiBold),
    Font(R.font.manrope, FontWeight.Bold),
)

private val MonoFamily = FontFamily.Monospace

private val FuturisticTypography = Typography(
    headlineLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.45).sp),
    headlineSmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.08).sp),
    titleSmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.08.sp),
    labelLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.35.sp),
    labelMedium = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.95.sp),
    labelSmall = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.75.sp),
)

private val FuturisticShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
)

val AppBackground = Brush.linearGradient(
    colors = listOf(
        Color(0xFF08111E),
        Color(0xFF07111B),
        DeepSpace,
        Color(0xFF040812),
    ),
)

@Composable
fun AntigravityRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = FuturisticTypography,
        shapes = FuturisticShapes,
        content = content,
    )
}

