package com.phonGuard.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 安全主题色
private val SecurityGreen = Color(0xFF2E7D32)
private val SecurityGreenLight = Color(0xFF4CAF50)
private val SecurityRed = Color(0xFFC62828)
private val SecurityRedLight = Color(0xFFE53935)
private val SecurityOrange = Color(0xFFEF6C00)
private val DarkSurface = Color(0xFF1A1A2E)
private val DarkBackground = Color(0xFF0F0F1A)

private val LightColors = lightColorScheme(
    primary = SecurityGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFF1565C0),
    tertiary = SecurityOrange,
    error = SecurityRed,
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFE8F5E9),
)

private val DarkColors = darkColorScheme(
    primary = SecurityGreenLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1B5E20),
    secondary = Color(0xFF42A5F5),
    tertiary = SecurityOrange,
    error = SecurityRedLight,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF1B3B1B),
)

@Composable
fun PhoneGuardTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}