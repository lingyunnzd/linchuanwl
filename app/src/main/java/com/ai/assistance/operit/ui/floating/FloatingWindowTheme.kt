package com.ai.assistance.operit.ui.floating

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ColorScheme

/**
 * 为悬浮窗提供的独立主题
 * 使用静态颜色，避免对Activity上下文的依赖
 */
@Composable
fun FloatingWindowTheme(
    colorScheme: ColorScheme? = null,
    typography: Typography? = null,
    content: @Composable () -> Unit
) {
    // 使用静态颜色，匹配动态主题的默认值
    val finalColorScheme = colorScheme ?: lightColorScheme(
        // 主要颜色
        primary = Color(0xFF1E4FA3),                // 霖川品牌蓝 - 与主应用默认主色匹配
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD6E3FB),       // 浅蓝容器
        onPrimaryContainer = Color(0xFF0A2E68),     // 深蓝文本

        // 次要颜色
        secondary = Color(0xFF45608C),              // 石墨蓝灰 - 次要色
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD9E2F7),     // 浅蓝灰容器
        onSecondaryContainer = Color(0xFF1D3355),   // 深蓝灰文本

        // 第三颜色
        tertiary = Color(0xFF2E6E63),               // 黛青 - 第三色
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFCCEAE2),      // 浅青容器
        onTertiaryContainer = Color(0xFF0F352E),    // 深青文本

        // 错误颜色
        error = Color(0xFFBA1A1A),                  // 标准Material错误色
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),         // 浅红色容器
        onErrorContainer = Color(0xFF410002),       // 深红色文本

        // 背景和表面
        background = Color(0xFFF6F7F9),             // 微冷调浅灰背景
        onBackground = Color(0xFF191B1F),           // 深色文本
        surface = Color(0xFFFDFDFE),                // 表面
        onSurface = Color(0xFF191B1F),              // 表面上的文本
        surfaceVariant = Color(0xFFE1E3E9),         // 浅灰色变体表面
        onSurfaceVariant = Color(0xFF44474E),       // 变体表面上的文本

        // 轮廓
        outline = Color(0xFF75787F)                 // 标准轮廓色
    )
    
    // 创建调整大小后的默认Typography，如果没有传入typography参数则使用此默认值
    val defaultSmallTypography = Typography(
        // 正文大字号
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.5.sp
        ),
        // 正文中字号 
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.25.sp
        ),
        // 正文小字号 
        bodySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.4.sp
        ),
        // 标签小字号
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.5.sp
        ),
        // 标题小字号
        titleSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.5.sp
        ),
        // 按钮文本样式
        labelMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        ),
        // 按钮大文本样式
        labelLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.5.sp
        )
    )

    // 优先使用传入的typography，如果没有则使用默认的小型typography
    val finalTypography = typography ?: defaultSmallTypography
    
    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = finalTypography,
        content = content
    )
} 