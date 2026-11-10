package com.wordlens.app.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.wordlens.app.MainActivity
import com.wordlens.app.WordLensApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 每日复习提醒：用一个很轻的前台服务守着，到点发通知。
 *
 * 比闹钟好在不受 Doze/厂商省电策略影响；代价是常驻一条低优先级通知。
 * 服务本身不持有界面、不查库（只在到点那一刻查一次），占用很低。
 */
class ReminderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(FOREGROUND_ID, buildOngoing())
        scope.launch {
            while (true) {
                val hour = (application as? WordLensApp)?.container?.settings
                    ?.state?.value?.reminderHour ?: 20
                val wait = millisUntil(hour)
                delay(wait)
                runCatching { fire() }
            }
        }
    }

    private fun millisUntil(hour: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        var target = cal.timeInMillis
        // 不在未来就顺延到明天；再留 1 秒余量，避免同一秒重复触发
        while (target <= System.currentTimeMillis() + 1000) target += 24L * 3600 * 1000
        return target - System.currentTimeMillis()
    }

    private suspend fun fire() {
        val app = applicationContext as? WordLensApp ?: return
        val due = runCatching { app.container.database.dueWords(System.currentTimeMillis(), 1).size }
            .getOrDefault(0)
        val text = if (due > 0) "今天有生词到期，花两分钟过一遍吧" else "今天还没背词，拍一页英文试试"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = builder(DAILY_CHANNEL)
            .setContentTitle("词镜 · 该复习了")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(DAILY_ID, n) }
    }

    private fun builder(channel: String): Notification.Builder {
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, channel)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        return b
    }

    private fun buildOngoing(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(ONGOING_CHANNEL, "复习提醒守护", NotificationManager.IMPORTANCE_MIN)
                    .apply { setShowBadge(false); description = "保持每日提醒可用" }
            )
            nm.createNotificationChannel(
                NotificationChannel(DAILY_CHANNEL, "每日复习提醒", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "到点提醒你复习生词" }
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return builder(ONGOING_CHANNEL)
            .setContentTitle("词镜 · 每日提醒已开启")
            .setContentText("到点会提醒你复习")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val FOREGROUND_ID = 4001
        private const val DAILY_ID = 4002
        private const val ONGOING_CHANNEL = "wordlens_reminder_guard"
        private const val DAILY_CHANNEL = "wordlens_daily"

        fun start(context: Context) {
            val i = Intent(context, ReminderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ReminderService::class.java))
        }
    }
}

/** 开机后把提醒服务拉起来 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? WordLensApp ?: return
        if (app.container.settings.state.value.dailyReminder) {
            ReminderService.start(context)
        }
    }
}
