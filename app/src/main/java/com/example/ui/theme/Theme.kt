package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.utils.HighlightTheme

@Composable
fun MyApplicationTheme(
    theme: HighlightTheme = HighlightTheme.SOFT_DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = theme.accent,
        onPrimary = Color.White,
        primaryContainer = theme.surface,
        onPrimaryContainer = theme.textPrimary,
        secondary = theme.accent,
        onSecondary = Color.White,
        background = theme.background,
        surface = theme.surface,
        onBackground = theme.textPrimary,
        onSurface = theme.textPrimary,
        surfaceVariant = theme.surface,
        onSurfaceVariant = theme.textPrimary.copy(alpha = 0.8f),
        outline = theme.comment.copy(alpha = 0.3f)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
