package com.wordlens.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 底部导航与拍照按钮用的线性图标。
 *
 * 不用 material-icons-extended：那套图标让 APK 多出好几 MB，
 * 而我们只需要 4 个形状，Canvas 画一下就行，风格也能统一（2dp 圆头描边）。
 */
enum class NavIconKind { Camera, Search, Book, Settings }

@Composable
fun NavIcon(kind: NavIconKind, tint: Color, modifier: Modifier = Modifier, sizeDp: Int = 22, stroke: Float = 2f) {
    Canvas(modifier.size(sizeDp.dp)) {
        val w = size.width
        val h = size.height
        val s = Stroke(width = stroke.dp.toPx(), cap = StrokeCap.Round)
        when (kind) {
            NavIconKind.Camera -> {
                // 机身
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.06f, h * 0.26f),
                    size = Size(w * 0.88f, h * 0.56f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f),
                    style = s
                )
                // 取景器凸起
                drawLine(tint, Offset(w * 0.34f, h * 0.26f), Offset(w * 0.42f, h * 0.13f), stroke.dp.toPx(), StrokeCap.Round)
                drawLine(tint, Offset(w * 0.42f, h * 0.13f), Offset(w * 0.60f, h * 0.13f), stroke.dp.toPx(), StrokeCap.Round)
                drawLine(tint, Offset(w * 0.60f, h * 0.13f), Offset(w * 0.68f, h * 0.26f), stroke.dp.toPx(), StrokeCap.Round)
                // 镜头
                drawCircle(tint, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.55f), style = s)
            }

            NavIconKind.Search -> {
                drawCircle(tint, radius = w * 0.30f, center = Offset(w * 0.44f, h * 0.42f), style = s)
                drawLine(tint, Offset(w * 0.66f, h * 0.64f), Offset(w * 0.90f, h * 0.88f), stroke.dp.toPx(), StrokeCap.Round)
            }

            NavIconKind.Book -> {
                val path = Path().apply {
                    moveTo(w * 0.12f, h * 0.20f)
                    lineTo(w * 0.46f, h * 0.20f)
                    lineTo(w * 0.46f, h * 0.84f)
                    lineTo(w * 0.12f, h * 0.80f)
                    close()
                    moveTo(w * 0.54f, h * 0.20f)
                    lineTo(w * 0.88f, h * 0.20f)
                    lineTo(w * 0.88f, h * 0.80f)
                    lineTo(w * 0.54f, h * 0.84f)
                    close()
                }
                drawPath(path, tint, style = s)
                drawLine(tint, Offset(w * 0.5f, h * 0.22f), Offset(w * 0.5f, h * 0.84f), stroke.dp.toPx(), StrokeCap.Round)
            }

            NavIconKind.Settings -> {
                val cx = w * 0.5f
                val cy = h * 0.5f
                drawCircle(tint, radius = w * 0.26f, center = Offset(cx, cy), style = s)
                drawCircle(tint, radius = w * 0.10f, center = Offset(cx, cy), style = s)
                // 6 根齿
                repeat(6) { i ->
                    val a = Math.toRadians(60.0 * i)
                    val r1 = w * 0.30f
                    val r2 = w * 0.44f
                    drawLine(
                        tint,
                        Offset(cx + (r1 * Math.cos(a)).toFloat(), cy + (r1 * Math.sin(a)).toFloat()),
                        Offset(cx + (r2 * Math.cos(a)).toFloat(), cy + (r2 * Math.sin(a)).toFloat()),
                        stroke.dp.toPx(),
                        StrokeCap.Round
                    )
                }
            }
        }
    }
}
