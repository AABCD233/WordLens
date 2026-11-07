package com.wordlens.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ---------------- 品牌色（青绿 + 暖琥珀） ----------------
private val Teal = Color(0xFF0F6B63)
private val TealLight = Color(0xFF7FD1C4)
private val Amber = Color(0xFFB37A16)
private val AmberLight = Color(0xFFF2C078)

/**
 * 浅色：纸感背景 + 一层层抬高的 surfaceContainer。
 * MD3 的层高靠 surfaceContainer* 这套色角色表达，而不是给 Card 叠半透明色。
 */
private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDE5),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4E635E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E7E1),
    onSecondaryContainer = Color(0xFF0A1F1B),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDAA),
    onTertiaryContainer = Color(0xFF2A1800),
    background = Color(0xFFFBF8F2),
    onBackground = Color(0xFF12221F),
    surface = Color(0xFFFBF8F2),
    onSurface = Color(0xFF12221F),
    surfaceVariant = Color(0xFFDCE5E1),
    onSurfaceVariant = Color(0xFF404944),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F1E9),
    surfaceContainer = Color(0xFFEFEBE2),
    surfaceContainerHigh = Color(0xFFE9E4DA),
    surfaceContainerHighest = Color(0xFFE2DDD2),
    outline = Color(0xFF707974),
    outlineVariant = Color(0xFFC0C9C4),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF00352F),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFB8F2E8),
    secondary = Color(0xFFB5CCC6),
    onSecondary = Color(0xFF20332F),
    secondaryContainer = Color(0xFF364A45),
    onSecondaryContainer = Color(0xFFD1E7E1),
    tertiary = AmberLight,
    onTertiary = Color(0xFF432C00),
    tertiaryContainer = Color(0xFF5F4100),
    onTertiaryContainer = Color(0xFFFFDDAA),
    background = Color(0xFF0E1413),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0E1413),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFBFC9C4),
    surfaceContainerLowest = Color(0xFF090F0E),
    surfaceContainerLow = Color(0xFF161D1B),
    surfaceContainer = Color(0xFF1A211F),
    surfaceContainerHigh = Color(0xFF242C2A),
    surfaceContainerHighest = Color(0xFF2F3735),
    outline = Color(0xFF89938E),
    outlineVariant = Color(0xFF404944),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/** 比默认更圆：卡片 20dp、胶囊 999dp，接近 PiliPlus / Material You 的观感 */
private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Medium),
        bodyLarge = t.bodyLarge.copy(lineHeight = 24.sp),
        bodyMedium = t.bodyMedium.copy(lineHeight = 21.sp)
    )
}

@Composable
fun WordLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Android 12+ 跟随系统壁纸取色（Material You）；关掉就用品牌青绿 */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 不强制给系统栏加半透明"刮痕"底色，否则底部会出现一条和页面不同色的带
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

/** 阅读正文用：略大的行高，长时间看不累 */
val ReaderLineHeight = 26.dp

/** 统一的「大标题 + 副标题」样式，各页头部复用 */
val PageTitleStyle: TextStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
