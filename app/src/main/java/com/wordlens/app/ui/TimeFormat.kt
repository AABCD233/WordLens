package com.wordlens.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** 「3 分钟前」这种相对时间，列表里够用 */
fun formatTime(millis: Long): String {
    if (millis <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - millis
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 24 * 3_600_000 -> "${diff / 3_600_000} 小时前"
        abs(diff) < 7 * 24 * 3_600_000 -> "${diff / (24 * 3_600_000)} 天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
    }
}

fun formatDateTime(millis: Long): String =
    if (millis <= 0) "—" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

/** 下次复习时间 */
fun formatDue(dueAt: Long): String = when {
    dueAt <= System.currentTimeMillis() -> "待复习"
    dueAt - System.currentTimeMillis() < 3_600_000 -> "${(dueAt - System.currentTimeMillis()) / 60_000} 分钟后"
    dueAt - System.currentTimeMillis() < 24 * 3_600_000 -> "${(dueAt - System.currentTimeMillis()) / 3_600_000} 小时后"
    else -> "${(dueAt - System.currentTimeMillis()) / (24 * 3_600_000)} 天后"
}
