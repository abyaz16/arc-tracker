package com.example.mytracker.domain

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.Calendar

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val foregroundMillis: Long,
)

data class DailyPhoneUsage(
    val hasPermission: Boolean,
    val totalScreenTimeMillis: Long,
    val apps: List<AppUsageInfo>,
)

object UsageTracker {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun getTodayUsage(context: Context): DailyPhoneUsage {
        if (!hasUsageAccess(context)) {
            return DailyPhoneUsage(hasPermission = false, totalScreenTimeMillis = 0L, apps = emptyList())
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return DailyPhoneUsage(hasPermission = true, totalScreenTimeMillis = 0L, apps = emptyList())

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        if (stats.isNullOrEmpty()) {
            return DailyPhoneUsage(hasPermission = true, totalScreenTimeMillis = 0L, apps = emptyList())
        }

        val pm = context.packageManager
        val aggregated = mutableMapOf<String, Long>()

        for (u in stats) {
            if (u.totalTimeInForeground > 0) {
                aggregated[u.packageName] = (aggregated[u.packageName] ?: 0L) + u.totalTimeInForeground
            }
        }

        val appItems = aggregated.mapNotNull { (pkg, millis) ->
            if (millis < 15_000L) return@mapNotNull null
            val appName = try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg.substringAfterLast('.')
            }
            AppUsageInfo(
                packageName = pkg,
                appName = appName,
                foregroundMillis = millis,
            )
        }
        .sortedByDescending { it.foregroundMillis }

        val totalMillis = appItems.sumOf { it.foregroundMillis }

        return DailyPhoneUsage(
            hasPermission = true,
            totalScreenTimeMillis = totalMillis,
            apps = appItems,
        )
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            totalMinutes == 0L -> "< 1m"
            hours == 0L -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }
}
