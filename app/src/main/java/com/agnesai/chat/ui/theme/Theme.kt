package com.agnesai.chat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F6EF7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF596084),
    secondaryContainer = Color(0xFFDDE4FF),
    surface = Color(0xFFFAF9FF),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    background = Color(0xFFFAF9FF),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C4FF),
    onPrimary = Color(0xFF002087),
    primaryContainer = Color(0xFF3548CE),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC0C8F4),
    secondaryContainer = Color(0xFF414963),
    surface = Color(0xFF121318),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC6C5D0),
    background = Color(0xFF121318),
    error = Color(0xFFFFB4AB)
)

@Composable
fun AgnesChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
