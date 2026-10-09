package com.example.barpos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryOrange,
    secondary = RadiantAmber,
    tertiary = SuccessGreen,
    background = BackgroundDark,
    surface = SurfaceDark,
    error = WarningRed,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onError = TextPrimary,
    surfaceContainerHigh = SurfaceContainerHigh
)

@Composable
fun BarPOSTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
