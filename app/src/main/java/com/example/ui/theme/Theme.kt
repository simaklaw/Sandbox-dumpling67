package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GameColorScheme = lightColorScheme(
    primary = PelmenYellowPrimary,
    onPrimary = Color(0xFF3E2723),
    primaryContainer = PelmenYellowLight,
    onPrimaryContainer = Color(0xFF3E2723),
    secondary = PelmenOrangeSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFCCBC),
    onSecondaryContainer = Color(0xFF3E2723),
    tertiary = PelmenSkyBlue,
    onTertiary = Color.White,
    background = PelmenWarmBg,
    onBackground = PelmenDarkBrown,
    surface = PelmenCardBg,
    onSurface = PelmenDarkBrown
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GameColorScheme,
        typography = Typography,
        content = content
    )
}
