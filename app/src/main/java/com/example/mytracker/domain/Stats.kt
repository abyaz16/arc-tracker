package com.example.mytracker.domain

import com.example.mytracker.data.Db
import com.example.mytracker.data.Goal
import com.example.mytracker.data.Habit
import com.example.mytracker.data.HabitLog
import com.example.mytracker.data.ScheduleType
import com.example.mytracker.data.TargetType
import com.example.mytracker.data.Task
import com.example.mytracker.data.TaskStatus
import kotlin.math.min
import kotlin.math.roundToInt

/** Derived reads over the database. Pure functions — no state, trivially testable. */
object Stats {

    /* ------------------------------------------------------------------ tasks */

    fun tasksOn(db: Db, date: String) = db.tasks.filter { it.dueDate == date }

    fun overdue(db: Db, base: String = D.today()) =
        db.tasks.filter { it.isOpen && it.dueDate != null && it.dueDate < base }

    data class DayTasks(val done: Int, val total: Int, val list: List<Task>)

    fun todayProgress(db: Db, date: String = D.today()): DayTasks {
        val list = tasksOn(db, date).filter { it.status != TaskStatus.CANCELLED }
        return DayTasks(list.count { it.isDone }, list.size, list)
    }

    data class Score(val score: Int, val parts: List<Pair<String, Int>>)

    /**
     * Transparent priority score. Every term is explainable to the user —
     * there is no hidden model here, which is the point.
     */
    fun priorityScore(t: Task, base: String = D.today()): Score {
        val parts = ArrayList<Pair<String, Int>>()
        parts += "Your priority" to t.priority.weight

        var overdue = 0
        if (t.dueDate != null && t.dueDate < base) {
            val late = D.diffDays(base, t.dueDate)
            overdue = min(30, 12 + late * 4)
            parts += "Overdue by ${late}d" to overdue
        }

        var proximity = 0
        if (t.dueDate != null && t.dueDate >= base) {
            val d = D.diffDays(t.dueDate, base)
            proximity = when {
                d == 0 -> 20
                d == 1 -> 12
                d < 4 -> 6
                else -> 2
            }
            parts += (if (d == 0) "Due today" else "Due in ${d}d") to proximity
        }

        val goal = if (t.goalId != null) 10 else 0
        if (goal > 0) parts += "Linked to a goal" to goal

        val post = min(8, t.postponeCount * 3)
        if (post > 0) parts += "Postponed ${t.postponeCount}x" to post

        val short = if (t.estimateMin != null && t.estimateMin <= 30) 5 else 0
        if (short > 0) parts += "Short enough to start now" to short

        return Score(t.priority.weight + overdue + proximity + goal + post + short, parts)
    }

    fun topPriorities(db: Db, n: Int = 3, base: String = D.today()): List<Task> =
        db.tasks
            .filter { it.isOpen && it.dueDate != null && it.dueDate <= base }
            .sortedByDescending { priorityScore(it, base).score }
            .take(n)

    /* ----------------------------------------------------------------- habits */

    /** Structural schedule check: could this habit be logged on this date at all? */
    fun isHabitDue(h: Habit, date: String): Boolean {
        if (h.archived) return false
        if (h.startDate.isNotEmpty() && date < h.startDate) return false
        return when (h.schedule.type) {
            ScheduleType.DAILY -> true
            ScheduleType.WEEKDAYS -> D.dow(date) in h.schedule.days
            ScheduleType.TIMES_PER_WEEK -> true // loggable any day; the quota is weekly
        }
    }

    fun logFor(db: Db, habitId: String, date: String): HabitLog? =
        db.habitLogs.firstOrNull { it.habitId == habitId && it.date == date }

    /**
     * The user's chosen first day of the week. Every week boundary in this file
     * goes through here, so changing the setting cannot leave half the app on
     * Monday and half on Sunday.
     */
    fun weekStart(db: Db, date: String): String =
        D.startOfWeek(date, db.settings.weekStartsMonday)

    fun weekCompletions(db: Db, h: Habit, date: String): Int {
        val start = weekStart(db, date)
        val end = D.addDays(start, 6)
        return db.habitLogs.count { it.habitId == h.id && it.completed && it.date >= start && it.date <= end }
    }

    /**
     * Whether the habit still wants something from you today. An "X times a week"
     * habit stops asking once its quota is met, rather than reading as a miss on
     * every remaining day of the week.
     */
    fun isHabitOpen(db: Db, h: Habit, date: String): Boolean {
        if (!isHabitDue(h, date)) return false
        if (h.schedule.type != ScheduleType.TIMES_PER_WEEK) return true
        if (logFor(db, h.id, date)?.completed == true) return true
        return weekCompletions(db, h, date) < h.schedule.timesPerWeek
    }

    fun habitsDueOn(db: Db, date: String): List<Habit> =
        db.habits.filter { isHabitOpen(db, it, date) }.sortedBy { it.order }

    data class DayHabits(val done: Int, val total: Int, val due: List<Habit>)

    fun habitDayProgress(db: Db, date: String = D.today()): DayHabits {
        val due = habitsDueOn(db, date)
        return DayHabits(due.count { logFor(db, it.id, date)?.completed == true }, due.size, due)
    }

    fun streakUnit(h: Habit): String = if (h.schedule.type == ScheduleType.TIMES_PER_WEEK) "w" else "d"

    /** Always derived from the logs, never stored — so backfilling stays honest. */
    fun currentStreak(db: Db, h: Habit, base: String = D.today()): Int {
        if (h.schedule.type == ScheduleType.TIMES_PER_WEEK) {
            var weeks = 0
            var cursor = weekStart(db, base)
            if (weekCompletions(db, h, cursor) < h.schedule.timesPerWeek) cursor = D.addDays(cursor, -7)
            repeat(200) {
                if (D.addDays(cursor, 6) < h.startDate) return weeks
                if (weekCompletions(db, h, cursor) >= h.schedule.timesPerWeek) {
                    weeks++
                    cursor = D.addDays(cursor, -7)
                } else return weeks
            }
            return weeks
        }

        var streak = 0
        var cursor = base
        // today not being logged yet shouldn't break a live streak
        if (isHabitDue(h, cursor) && logFor(db, h.id, cursor)?.completed != true) cursor = D.addDays(cursor, -1)
        repeat(400) {
            if (cursor < h.startDate) return streak
            if (!isHabitDue(h, cursor)) {
                cursor = D.addDays(cursor, -1)
            } else if (logFor(db, h.id, cursor)?.completed == true) {
                streak++
                cursor = D.addDays(cursor, -1)
            } else return streak
        }
        return streak
    }

    fun bestStreak(db: Db, h: Habit, base: String = D.today()): Int {
        var best = 0
        var run = 0
        val start = if (h.startDate.isEmpty()) D.addDays(base, -120) else h.startDate

        if (h.schedule.type == ScheduleType.TIMES_PER_WEEK) {
            var cursor = weekStart(db, start)
            val end = weekStart(db, base)
            var guard = 0
            while (cursor <= end && guard++ < 400) {
                if (weekCompletions(db, h, cursor) >= h.schedule.timesPerWeek) {
                    run++
                    best = maxOf(best, run)
                } else run = 0
                cursor = D.addDays(cursor, 7)
            }
            return best
        }

        for (d in D.range(start, base)) {
            if (!isHabitDue(h, d)) continue
            if (logFor(db, h.id, d)?.completed == true) {
                run++
                best = maxOf(best, run)
            } else run = 0
        }
        return best
    }

    fun completionRate(db: Db, h: Habit, days: Int, base: String = D.today()): Int {
        val window = D.range(D.addDays(base, -(days - 1).toLong()), base)

        if (h.schedule.type == ScheduleType.TIMES_PER_WEEK) {
            val done = window.count { logFor(db, h.id, it)?.completed == true }
            val quota = days.toDouble() / 7.0 * h.schedule.timesPerWeek
            return if (quota <= 0) 0 else min(100, (done / quota * 100).roundToInt())
        }

        var due = 0
        var done = 0
        for (d in window) {
            if (!isHabitDue(h, d)) continue
            due++
            if (logFor(db, h.id, d)?.completed == true) done++
        }
        return if (due == 0) 0 else (done.toDouble() / due * 100).roundToInt()
    }

    /** A weekly-quota habit has no missed day — only an unmet week. */
    fun missedDays(db: Db, h: Habit, days: Int, base: String = D.today()): List<String> {
        if (h.schedule.type == ScheduleType.TIMES_PER_WEEK) return emptyList()
        return D.range(D.addDays(base, -(days - 1).toLong()), base)
            .filter { it != base && isHabitDue(h, it) && logFor(db, h.id, it)?.completed != true }
    }

    /* ------------------------------------------------------------------ goals */

    data class Progress(val value: Int, val target: Int, val pct: Float, val label: String)

    fun goalProgress(db: Db, g: Goal): Progress = when (g.targetType) {
        TargetType.MILESTONES -> {
            val target = g.milestones.size
            val value = g.milestones.count { it.done }
            Progress(value, target, if (target == 0) 0f else value.toFloat() / target, "$value / $target milestones")
        }

        TargetType.BINARY -> {
            val v = if (g.status == com.example.mytracker.data.GoalStatus.COMPLETED) 1 else 0
            Progress(v, 1, v.toFloat(), if (v == 1) "Done" else "Not yet")
        }

        TargetType.DERIVED -> {
            val value = when (g.derived?.kind) {
                "habit_days" -> db.habitLogs.count {
                    it.habitId == g.derived.habitId && it.completed &&
                        it.date >= g.periodStart && it.date <= g.periodEnd
                }

                else -> db.tasks.count {
                    it.isDone && it.dueDate != null &&
                        it.dueDate >= g.periodStart && it.dueDate <= g.periodEnd &&
                        (g.derived?.categoryId == null || it.categoryId == g.derived.categoryId)
                }
            }
            Progress(
                value, g.targetValue,
                if (g.targetValue == 0) 0f else min(1f, value.toFloat() / g.targetValue),
                "$value / ${g.targetValue}${if (g.unit.isEmpty()) "" else " ${g.unit}"}",
            )
        }

        TargetType.NUMERIC -> Progress(
            g.manualValue, g.targetValue,
            if (g.targetValue == 0) 0f else min(1f, g.manualValue.toFloat() / g.targetValue),
            "${g.manualValue} / ${g.targetValue}${if (g.unit.isEmpty()) "" else " ${g.unit}"}",
        )
    }

    /** Where the goal would be by now if progress were even across the period. */
    fun requiredPace(g: Goal, base: String = D.today()): Float {
        if (g.periodStart.isEmpty() || g.periodEnd.isEmpty()) return 0f
        val total = maxOf(1, D.diffDays(g.periodEnd, g.periodStart) + 1)
        val elapsed = (D.diffDays(base, g.periodStart) + 1).coerceIn(0, total)
        return elapsed.toFloat() / total
    }

    fun daysLeft(g: Goal, base: String = D.today()): Int =
        maxOf(0, D.diffDays(g.periodEnd, base))

    /* --------------------------------------------------------------- rollups */

    data class DayRollup(
        val date: String,
        var tasksDone: Int = 0,
        var tasksDue: Int = 0,
        var habitsDone: Int = 0,
        var habitsDue: Int = 0,
        var focusMin: Int = 0,
    )

    fun rollup(db: Db, from: String, to: String): List<DayRollup> {
        val byDate = LinkedHashMap<String, DayRollup>()
        for (d in D.range(from, to)) byDate[d] = DayRollup(d)

        for (t in db.tasks) {
            if (t.dueDate == null || t.status == TaskStatus.CANCELLED) continue
            val r = byDate[t.dueDate] ?: continue
            r.tasksDue++
            if (t.isDone) r.tasksDone++
        }
        for ((date, r) in byDate) r.habitsDue = habitsDueOn(db, date).size
        for (l in db.habitLogs) {
            if (!l.completed) continue
            byDate[l.date]?.let { it.habitsDone++ }
        }
        for (s in db.sessions) {
            byDate[D.isoOfEpoch(s.startedAt)]?.let { it.focusMin += s.actualMin }
        }
        // once a weekly quota is met the day has fewer "open" slots than completions
        for (r in byDate.values) r.habitsDue = maxOf(r.habitsDue, r.habitsDone)
        return byDate.values.toList()
    }

    fun focusMinutesOn(db: Db, date: String): Int = rollup(db, date, date).firstOrNull()?.focusMin ?: 0

    fun pct(a: Int, b: Int): Int = if (b == 0) 0 else (a.toDouble() / b * 100).roundToInt()

    fun completionByHour(db: Db, fromDate: String): IntArray {
        val buckets = IntArray(24)
        for (t in db.tasks) {
            val at = t.completedAt ?: continue
            if (!t.isDone || t.dueDate == null || t.dueDate < fromDate) continue
            buckets[D.hourOfEpoch(at)]++
        }
        return buckets
    }

    fun failureBreakdown(db: Db, from: String, to: String): List<Pair<String, Int>> =
        db.failures
            .filter { it.date in from..to }
            .groupingBy { it.code.label }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
}
