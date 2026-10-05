package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Emerald700,
    onPrimary = Color.White,
    primaryContainer = Emerald100,
    onPrimaryContainer = Emerald800,
    secondary = MintTeal,
    onSecondary = Color.White,
    secondaryContainer = MintTealContainer,
    onSecondaryContainer = Emerald800,
    tertiary = Slate700,
    onTertiary = Color.White,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,
    outline = Slate300,
    outlineVariant = Slate200,
    error = ExpenseRed,
    errorContainer = ExpenseRedContainer,
    onError = Color.White,
    onErrorContainer = ExpenseRed
)

private val DarkColorScheme = darkColorScheme(
    primary = Emerald500,
    onPrimary = Color.Black,
    primaryContainer = Emerald800,
    onPrimaryContainer = Emerald100,
    secondary = Emerald500,
    onSecondary = Color.Black,
    secondaryContainer = Emerald800,
    onSecondaryContainer = MintTealContainer,
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // The user explicitly requested: "Light theme", but we support both gracefully
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
