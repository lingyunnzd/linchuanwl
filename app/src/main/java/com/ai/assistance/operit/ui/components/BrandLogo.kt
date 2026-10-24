package com.ai.assistance.operit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * 霖川 AI 品牌标识：深海军蓝到亮蓝的对角渐变底，外加三道倾斜的行星环与白轨道、三颗星点。
 * 与启动器图标（ic_launcher_simple_foreground）保持同一套几何。
 */
@Composable
fun LinchuanLogo(
    size: Int,
    modifier: Modifier = Modifier,
    withTile: Boolean = true,
) {
    val tileModifier = if (withTile) {
        modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.23f).dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF050D19),
                        Color(0xFF0B2A5C),
                        Color(0xFF1E6BFF),
                    )
                )
            )
    } else {
        modifier.size(size.dp)
    }

    Box(modifier = tileModifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size((size * 0.82f).dp)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val u = this.size.width / 100f

            // 三道行星环（倾斜 -16°）
            rotate(degrees = -16f, pivot = Offset(cx, cy)) {
                drawOval(
                    color = Color(0xFFB9F1FF),
                    topLeft = Offset(cx - 43f * u, cy - 27f * u),
                    size = Size(86f * u, 54f * u),
                    style = Stroke(width = 3.0f * u)
                )
                drawOval(
                    color = Color(0xFF1E5FE0),
                    topLeft = Offset(cx - 32f * u, cy - 20f * u),
                    size = Size(64f * u, 40f * u),
                    style = Stroke(width = 8.0f * u)
                )
                drawOval(
                    color = Color(0xFF7FE3FF),
                    topLeft = Offset(cx - 21f * u, cy - 13f * u),
                    size = Size(42f * u, 26f * u),
                    style = Stroke(width = 3.6f * u)
                )
            }

            // 白色轨道线
            rotate(degrees = -8f, pivot = Offset(cx, cy)) {
                drawOval(
                    color = Color.White.copy(alpha = 0.95f),
                    topLeft = Offset(cx - 31f * u, cy - 17f * u),
                    size = Size(62f * u, 34f * u),
                    style = Stroke(width = 1.4f * u)
                )
            }

            // 三颗星点
            drawCircle(Color.White, radius = 2.2f * u, center = Offset(cx - 13f * u, cy + 4f * u))
            drawCircle(Color.White, radius = 3.4f * u, center = Offset(cx, cy - 2f * u))
            drawCircle(Color.White, radius = 2.0f * u, center = Offset(cx + 13f * u, cy - 5f * u))
        }
    }
}
