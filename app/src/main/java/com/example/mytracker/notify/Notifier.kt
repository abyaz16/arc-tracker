package com.example.mytracker.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.mytracker.MainActivity
import com.example.mytracker.R
import com.example.mytracker.data.ReminderStyle
import com.example.mytracker.data.Store
import com.example.mytracker.domain.PlannedReminder
import com.example.mytracker.domain.Reminders
import kotlin.math.absoluteValue

/**
 * Local reminders, built on the platform's own AlarmManager and
 * NotificationManager — no WorkManager, no dependency added.
 *
 * The scheduling decision lives in [Reminders] so it can be tested; this file
 * only carries it out.
 *
 * Two styles, because they are genuinely different things:
 *
 *  - [ReminderStyle.NOTIFICATION] posts quietly on an inexact alarm. Costs no
 *    extra permission, and may arrive a few minutes late.
 *  - [ReminderStyle.ALARM] rings with the device's alarm sound, vibrates, and
 *    fires exactly on the minute. On Android 12+ that needs the user to grant
 *    "alarms and reminders" in system settings.
 *
 * A channel's importance and sound are fixed once it is created, so each style
 * gets its own channel rather than one being mutated.
 */
object Notifier {

    private const val CHANNEL_QUIET = "cadence.reminders"
    private const val CHANNEL_ALARM = "cadence.alarms"
    const val EXTRA_TAB = "tab"

    /** Everything this object has scheduled, so it can cancel before re-planning. */
    private var scheduled: List<PlannedReminder> = emptyList()

    private fun channelFor(style: ReminderStyle) =
        if (style == ReminderStyle.ALARM) CHANNEL_ALARM else CHANNEL_QUIET

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_QUIET,
                "Reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Tasks with a time, and habits you asked to be reminded about."
            },
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARM,
                "Alarm reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "The same reminders, but they ring like an alarm."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build(),
                )
            },
        )
    }

    /** Whether the OS will actually let us post at all. */
    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    /** Whether alarm style can actually fire on the minute. */
    fun canScheduleExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    /** Opens the system screen where "alarms and reminders" is granted. */
    fun exactAlarmSettingsIntent(ctx: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = android.net.Uri.parse("package:${ctx.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Cancels what was scheduled and schedules what the current data asks for.
     * Safe to call often — it is the only way alarms are ever set.
     */
    fun reschedule(ctx: Context, store: Store) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        scheduled.forEach { am.cancel(pendingFor(ctx, it)) }
        scheduled = emptyList()

        val settings = store.db.settings
        if (!settings.remindersEnabled || !canPost(ctx)) return

        ensureChannels(ctx)
        // alarm style silently degrades to inexact rather than not firing at all
        // exact alarm check done per reminder
        val canExact = canScheduleExact(ctx)
        val plan = Reminders.plan(store.db, System.currentTimeMillis())

        plan.forEach { r ->
            val pi = pendingFor(ctx, r)
            val exact = r.style == ReminderStyle.ALARM && canExact
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.atMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.atMillis, pi)
            }
        }
        scheduled = plan
    }

    /** Cancels everything, for when the user turns reminders off. */
    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        scheduled.forEach { am.cancel(pendingFor(ctx, it)) }
        scheduled = emptyList()
    }

    private fun requestCode(key: String): Int = key.hashCode().absoluteValue

    private fun pendingFor(ctx: Context, r: PlannedReminder): PendingIntent {
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            action = "com.example.mytracker.REMIND"
            // the key is in the data URI so two reminders never collide
            data = android.net.Uri.parse("cadence://reminder/${r.key}")
            putExtra("title", r.title)
            putExtra("body", r.body)
            putExtra(EXTRA_TAB, r.tab)
            putExtra("key", r.key)
            putExtra("style", r.style.key)
        }
        return PendingIntent.getBroadcast(
            ctx,
            requestCode(r.key),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun post(ctx: Context, key: String, title: String, body: String, tab: String, style: ReminderStyle) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TAB, tab)
        }
        val tap = PendingIntent.getActivity(
            ctx,
            requestCode(key),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarm = style == ReminderStyle.ALARM
        val n = NotificationCompat.Builder(ctx, channelFor(style))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (alarm) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(requestCode(key), n) }
    }
}

/** Fires at the scheduled instant and posts the notification. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val store = Store.get(ctx)
        Notifier.post(
            ctx,
            key = intent.getStringExtra("key") ?: return,
            title = intent.getStringExtra("title") ?: return,
            body = intent.getStringExtra("body").orEmpty(),
            tab = intent.getStringExtra(Notifier.EXTRA_TAB) ?: "home",
            style = intent.getStringExtra("style")?.let { ReminderStyle.from(it) } ?: store.db.settings.reminderStyle,
        )
        // one alarm has now been consumed; re-plan so the next days get set
        Notifier.reschedule(ctx, store)
    }
}

/** Alarms do not survive a reboot, so they are rebuilt when the device comes up. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Notifier.reschedule(ctx, Store.get(ctx))
    }
}
