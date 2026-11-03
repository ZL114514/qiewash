package com.zl.qiewash

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Ongoing "live" notification for the running machine plus an exact-alarm finish alert. */
object Notify {
    // A promoted (island) notification needs at least IMPORTANCE_DEFAULT, so keep a v2 id:
    // channel importance is frozen once created.
    private const val CH_LIVE = "wash_live_v2"
    private const val CH_DONE = "wash_done"
    private const val ID_LIVE = 1001
    private const val ID_DONE = 1002
    private const val REQ_LIVE = 11
    private const val REQ_DONE = 12

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_LIVE, "洗涤进行中", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "洗衣机倒计时常驻通知"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "洗涤完成", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun contentIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, REQ_LIVE, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Live countdown; asks the system to promote it into the status-bar island. */
    fun live(ctx: Context, title: String, endAt: Long) {
        ensureChannels(ctx)
        val b = NotificationCompat.Builder(ctx, CH_LIVE)
            .setSmallIcon(R.drawable.ic_notify_wash)
            .setContentTitle(title)
            .setContentText("结束时间 " + timeText(endAt))
            .setWhen(endAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(contentIntent(ctx))
        // Android 16+ live updates: progress style + promoted-ongoing request is what the
        // island actually renders. Without the style the system silently ignores the request.
        val style = NotificationCompat.ProgressStyle()
            .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(100)))
            .setStyledByProgress(false)
            .setProgress(0)
        b.setStyle(style)
        b.setRequestPromotedOngoing(true)
        if (Build.VERSION.SDK_INT >= 36) {
            runCatching { b.setShortCriticalText(shortText(endAt)) }
        }
        post(ctx, ID_LIVE, b.build())
    }

    private fun shortText(endAt: Long): String {
        val left = ((endAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
        return if (left > 0) "剩 $left 分" else "即将结束"
    }

    fun cancelLive(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_LIVE)
    }

    fun done(ctx: Context, text: String) {
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_notify_wash)
            .setContentTitle("洗完了")
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentIntent(ctx))
            .build()
        NotificationManagerCompat.from(ctx).cancel(ID_LIVE)
        try {
            NotificationManagerCompat.from(ctx).notify(ID_DONE, n)
        } catch (_: SecurityException) {
        }
    }

    private fun post(ctx: Context, id: Int, n: android.app.Notification) {
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
            // notifications revoked; the in-app countdown still works
        }
    }

    /** Whether the system currently allows promoting ongoing notifications. */
    fun canPromote(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return try {
            ctx.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
        } catch (_: Throwable) {
            false
        }
    }

    fun canNotify(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun canExactAlarm(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    /** Schedule the finish alert; falls back to an inexact alarm without the permission. */
    fun scheduleEnd(ctx: Context, endAt: Long, text: String) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            ctx, REQ_DONE,
            Intent(ctx, WashAlarmReceiver::class.java).putExtra("text", text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            if (canExactAlarm(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi)
            else am.set(AlarmManager.RTC_WAKEUP, endAt, pi)
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, endAt, pi)
        }
    }

    fun cancelEnd(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(
            PendingIntent.getBroadcast(
                ctx, REQ_DONE,
                Intent(ctx, WashAlarmReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
    }

    private fun timeText(at: Long): String {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = at }
        return "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
    }

    fun test(ctx: Context) {
        ensureChannels(ctx)
        live(ctx, "测试 · 洗衣机进行中", System.currentTimeMillis() + 10 * 60_000L)
    }
}

class WashAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: "洗衣已结束，记得取衣"
        Notify.done(context, text)
        if (!Notify.canNotify(context)) return
        val vib = ContextCompat.getSystemService(context, android.os.Vibrator::class.java)
        @Suppress("DEPRECATION")
        vib?.vibrate(400)
    }
}
