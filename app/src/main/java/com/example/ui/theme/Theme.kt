package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PremiumDarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = OnPrimaryDark,
    secondary = GoldSecondary,
    tertiary = GoldTertiary,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceDark,
    outline = BorderDark,
    error = RedError
)

private val PremiumLightColorScheme = lightColorScheme(
    primary = GoldPrimary,
    onPrimary = Color.Black,
    secondary = GoldSecondary,
    background = Color(0xFFF9F9FB),
    onBackground = Color(0xFF1E2127),
    surface = Color.White,
    onSurface = Color(0xFF1E2127),
    surfaceVariant = Color(0xFFF0F1F5),
    onSurfaceVariant = Color(0xFF555B68),
    outline = Color(0xFFDCDFE6),
    error = RedError
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // We default to dark theme for a premium music player/downloader immersion!
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PremiumDarkColorScheme else PremiumLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
