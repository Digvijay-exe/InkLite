package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SophisticatedDarkColorScheme = darkColorScheme(
    primary = AccentLavender,
    onPrimary = OnAccentLavender,
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = AccentIceBlue,
    onSecondary = Color(0xFF003355),
    secondaryContainer = Color(0xFF004B75),
    onSecondaryContainer = Color(0xFFC2E7FF),
    tertiary = AccentCoral,
    onTertiary = Color(0xFF680008),
    tertiaryContainer = Color(0xFF8C0012),
    onTertiaryContainer = Color(0xFFFFDAD6),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfacePaperLine,
    surfaceContainerHighest = DarkSurfaceElevated,
    outline = DarkBorder,
    outlineVariant = Color(0xFF49454F).copy(alpha = 0.5f),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = SophisticatedDarkColorScheme,
        typography = Typography,
        content = content
    )
}
