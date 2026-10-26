package com.example.mytracker.domain

import com.example.mytracker.data.Db
import com.example.mytracker.data.Habit
import com.example.mytracker.data.Task
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** One notification the app intends to post, at a wall-clock instant. */
data class PlannedReminder(
    /** Stable across re-planning, so re-scheduling replaces rather than duplicates. */
    val key: String,
    val atMillis: Long,
    val title: String,
    val body: String,
    /** Which screen to open when tapped. */
    val tab: String,
    val style: com.example.mytracker.data.ReminderStyle = com.example.mytracker.data.ReminderStyle.NOTIFICATION,
)

/**
 * Decides what to remind about and when. Deliberately free of Android types so
 * the decision — the part where the bugs live — can be tested on the JVM.
 *
 * Two rules keep it from becoming noise:
 *  - nothing is scheduled in the past, and nothing for work already finished;
 *  - a habit whose weekly quota is already met is not nagged about again.
 */
object Reminders {

    /** How far ahead to schedule. Re-planned whenever the data changes. */
    const val HORIZON_HOURS = 48

    fun parseTime(hhmm: String?): LocalTime? = runCatching {
        hhmm?.takeIf { it.isNotBlank() }?.let { LocalTime.parse(it.trim()) }
    }.getOrNull()

    private fun millisOf(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()

    /**
     * Every reminder that should fire between [nowMillis] and the horizon.
     * Returns them in time order.
     */
    fun plan(
        db: Db,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        horizonHours: Int = HORIZON_HOURS,
    ): List<PlannedReminder> {
        val s = db.settings
        if (!s.remindersEnabled) return emptyList()

        val until = nowMillis + horizonHours * 3_600_000L
        val out = mutableListOf<PlannedReminder>()

        if (s.remindTasks) out += taskReminders(db, nowMillis, until, zone)
        if (s.remindHabits) out += habitReminders(db, nowMillis, until, zone)

        return out.sortedBy { it.atMillis }
    }

    /** A task reminds at its own due time, and only if it carries one. */
    private fun taskReminders(db: Db, now: Long, until: Long, zone: ZoneId): List<PlannedReminder> =
        db.tasks.mapNotNull { t ->
            if (!t.isOpen) return@mapNotNull null
            val date = t.dueDate ?: return@mapNotNull null
            if (!t.reminderEnabled && t.dueTime.isNullOrBlank()) return@mapNotNull null
            val time = parseTime(t.dueTime) ?: return@mapNotNull null
            val at = millisOf(LocalDate.parse(date), time, zone)
            if (at <= now || at > until) return@mapNotNull null
            val style = t.reminderStyle ?: db.settings.reminderStyle
            PlannedReminder(
                key = "task:${t.id}:$date",
                atMillis = at,
                title = t.title,
                body = bodyFor(t),
                tab = "tasks",
                style = style,
            )
        }

    private fun bodyFor(t: Task): String {
        val bits = buildList {
            add("${t.priority.label.lowercase()} priority")
            t.estimateMin?.let { add("about ${D.formatDuration(it)}") }
            if (t.postponeCount > 0) add("moved ${t.postponeCount}x already")
        }
        return bits.joinToString(" · ")
    }

    /**
     * A habit reminds at its own time, on each day it is actually due, and only
     * while it still wants something from you that day.
     */
    private fun habitReminders(db: Db, now: Long, until: Long, zone: ZoneId): List<PlannedReminder> {
        val out = mutableListOf<PlannedReminder>()
        // derived from `now`, not from the clock: this function must be a pure
        // function of its arguments, or it cannot be reasoned about or tested
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = (0..(HORIZON_HOURS / 24 + 1)).map { today.plusDays(it.toLong()) }

        db.habits.forEach { h ->
            if (h.archived) return@forEach
            val time = parseTime(h.reminderTime) ?: return@forEach
            days.forEach { d ->
                val iso = d.toString()
                if (!Stats.isHabitOpen(db, h, iso)) return@forEach
                if (Stats.logFor(db, h.id, iso)?.completed == true) return@forEach
                val at = millisOf(d, time, zone)
                if (at <= now || at > until) return@forEach
                val style = h.reminderStyle ?: db.settings.reminderStyle
                out += PlannedReminder(
                    key = "habit:${h.id}:$iso",
                    atMillis = at,
                    title = h.name,
                    body = bodyFor(h),
                    tab = "habits",
                    style = style,
                )
            }
        }
        return out
    }

    private fun bodyFor(h: Habit): String = when {
        h.target > 1 && h.unit.isNotBlank() -> "Today's target: ${h.target} ${h.unit}"
        h.target > 1 -> "Today's target: ${h.target}"
        else -> "Still open today"
    }
}
