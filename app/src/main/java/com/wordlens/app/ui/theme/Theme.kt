package com.wordlens.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val Ink = Color(0xFF12221F)
private val Teal = Color(0xFF0F6B63)
private val TealLight = Color(0xFF7FD1C4)
private val Amber = Color(0xFFE8A33D)
private val Paper = Color(0xFFFBF8F2)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDE5),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF8A5A12),
    secondaryContainer = Color(0xFFFFE1B3),
    onSecondaryContainer = Color(0xFF2C1900),
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDE7DC),
    onSurfaceVariant = Color(0xFF4C463D),
    outline = Color(0xFF8C857A),
    tertiary = Amber
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF00352F),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFB8F2E8),
    secondary = Color(0xFFF2C078),
    secondaryContainer = Color(0xFF5B3F00),
    onSecondaryContainer = Color(0xFFFFE1B3),
    background = Color(0xFF101614),
    onBackground = Color(0xFFE4E7E4),
    surface = Color(0xFF171E1C),
    onSurface = Color(0xFFE4E7E4),
    surfaceVariant = Color(0xFF242C2A),
    onSurfaceVariant = Color(0xFFC5CBC7),
    outline = Color(0xFF8E9691),
    tertiary = Amber
)

@Composable
fun WordLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content
    )
}

/** 阅读正文用：略大的行高，长时间看不累 */
val ReaderLineHeight = 26.dp
