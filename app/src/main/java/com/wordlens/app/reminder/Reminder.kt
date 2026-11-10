package com.wordlens.app.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.wordlens.app.MainActivity
import com.wordlens.app.WordLensApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 每日复习提醒。
 *
 * 用 AlarmManager 的「不精确重复闹钟」：系统会攒批唤醒，几乎不耗电，
 * 也不需要精确闹钟权限。整个功能只有 BroadcastReceiver，不占常驻内存。
 */
object ReminderScheduler {

    private const val REQ = 2201

    fun schedule(context: Context, hour: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context, 0)
        am.cancel(pi)
        if (!enabled(context)) return
        val next = nextTrigger(hour)
        runCatching {
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                next,
                AlarmManager.INTERVAL_DAY,
                pending(context, PendingIntent.FLAG_UPDATE_CURRENT)
            )
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, 0))
    }

    private fun pending(context: Context, flags: Int): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java)
        var f = PendingIntent.FLAG_IMMUTABLE
        if (flags != 0) f = f or flags
        return PendingIntent.getBroadcast(context, REQ, i, f)
    }

    private fun nextTrigger(hour: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun enabled(context: Context): Boolean =
        (context.applicationContext as? WordLensApp)?.container?.settings?.state?.value?.dailyReminder == true

    const val CHANNEL = "wordlens_daily"
}

/** 到点后：查一下今天有几个词要复习，发一条通知 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as? WordLensApp ?: return
        val settings = app.container.settings.state.value
        if (!settings.dailyReminder) return

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val due = runCatching { app.container.database.dueWords(System.currentTimeMillis(), 1).size }
                .getOrDefault(0)
            post(context, due)
            result.finish()
        }
        // 重复闹钟一般会自己继续，这里再兜一次，防止某些 ROM 清掉
        ReminderScheduler.schedule(context, settings.reminderHour)
    }

    private fun post(context: Context, due: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    ReminderScheduler.CHANNEL, "每日复习提醒", NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "到点提醒你复习生词" }
            )
        }
        val pi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                putExtra("open_review", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (due > 0) "今天有生词到期，花两分钟过一遍吧" else "今天还没背词，拍一页英文试试"
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            android.app.Notification.Builder(context, ReminderScheduler.CHANNEL)
        else @Suppress("DEPRECATION") android.app.Notification.Builder(context)
        val n = b.setContentTitle("词镜 · 该复习了")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(3001, n) }
    }
}

/** 开机后把闹钟补回来（闹钟不会跨重启保留） */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? WordLensApp ?: return
        val s = app.container.settings.state.value
        if (s.dailyReminder) ReminderScheduler.schedule(context, s.reminderHour)
    }
}
