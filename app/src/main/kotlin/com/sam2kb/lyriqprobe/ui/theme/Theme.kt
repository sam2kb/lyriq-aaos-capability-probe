package com.sam2kb.lyriqprobe.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF080C10)
val InkRaised = Color(0xFF0E151B)
val Slate = Color(0xFF121C23)
val SlateHigh = Color(0xFF18262F)
val Line = Color(0xFF263842)
val LineBright = Color(0xFF36505C)
val Paper = Color(0xFFE7EFF3)
val Muted = Color(0xFF91A6B2)
val Faint = Color(0xFF5F7884)
val Amber = Color(0xFFE8A84C)
val AmberDim = Color(0xFF3A2B17)
val Teal = Color(0xFF7BD6C7)
val TealDim = Color(0xFF12302E)
val Green = Color(0xFF83C995)
val Red = Color(0xFFE17B83)
val RedDim = Color(0xFF341B1E)
val Violet = Color(0xFFB8A1E8)
val Blue = Color(0xFF87B8DE)

private val Scheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1206),
    primaryContainer = AmberDim,
    onPrimaryContainer = Amber,
    secondary = Teal,
    onSecondary = Color(0xFF06211E),
    secondaryContainer = TealDim,
    onSecondaryContainer = Teal,
    tertiary = Violet,
    background = Ink,
    onBackground = Paper,
    surface = Slate,
    onSurface = Paper,
    surfaceVariant = SlateHigh,
    onSurfaceVariant = Muted,
    outline = Line,
    outlineVariant = Color(0xFF1B2A33),
    error = Red,
    onError = Color(0xFF2A0D10),
    errorContainer = RedDim,
    onErrorContainer = Red,
    scrim = Color.Black,
)

private val ProbeShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(5.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

private val ProbeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 23.sp,
        lineHeight = 29.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.8.sp,
    ),
)

@Composable
fun LyriqProbeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = ProbeTypography,
        shapes = ProbeShapes,
        content = content,
    )
}
