package org.wayfarer.atlas.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val Forest = Color(0xFF23594B)
internal val Cream = Color(0xFFF5F2E9)
private val Light = lightColorScheme(
    primary = Forest, onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E9DF), onPrimaryContainer = Color(0xFF163E34),
    secondary = Color(0xFF657044), secondaryContainer = Color(0xFFE6E9D6),
    background = Cream, onBackground = Color(0xFF21342D),
    surface = Color(0xFFFFFDF7), onSurface = Color(0xFF21342D),
    surfaceVariant = Color(0xFFE9ECE2), onSurfaceVariant = Color(0xFF526158),
    outline = Color(0xFF76877C), error = Color(0xFF9D382D)
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA4D4BA), onPrimary = Color(0xFF103B2F),
    primaryContainer = Color(0xFF285344), onPrimaryContainer = Color(0xFFD7F0DF),
    secondary = Color(0xFFC1CEA1), secondaryContainer = Color(0xFF3F492C),
    background = Color(0xFF15241E), onBackground = Color(0xFFE1EADF),
    surface = Color(0xFF1D2C25), onSurface = Color(0xFFE1EADF),
    surfaceVariant = Color(0xFF34453A), onSurfaceVariant = Color(0xFFB7C8BB),
    outline = Color(0xFF82998A), error = Color(0xFFFFB4A8)
)
private val AtlasTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
)

@Composable
fun AtlasTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = AtlasTypography, content = content)
}

internal fun text(language: String, en: String, ru: String) = if (language == "ru") ru else en
