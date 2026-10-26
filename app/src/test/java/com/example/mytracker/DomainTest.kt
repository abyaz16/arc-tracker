package com.example.mytracker

import com.example.mytracker.data.Db
import com.example.mytracker.data.FailureCode
import com.example.mytracker.data.FailureEntry
import com.example.mytracker.data.Habit
import com.example.mytracker.data.HabitLog
import com.example.mytracker.data.HabitSchedule
import com.example.mytracker.data.ScheduleType
import com.example.mytracker.data.Seed
import com.example.mytracker.data.Priority
import com.example.mytracker.data.ReminderStyle
import com.example.mytracker.data.Task
import com.example.mytracker.data.TaskStatus
import com.example.mytracker.data.ThemeMode
import com.example.mytracker.data.TrackingType
import com.example.mytracker.domain.D
import com.example.mytracker.domain.Insights
import com.example.mytracker.domain.Reminders
import com.example.mytracker.domain.Stats
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import com.example.mytracker.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the parts where bugs actually live: date arithmetic across DST,
 * streak derivation, the weekly-quota habit model, and the insight engine's guards.
 */
class DomainTest {

    private val t = D.today()

    /* ------------------------------------------------------------------ dates */

    @Test
    fun `addDays round trips`() {
        assertEquals(t, D.addDays(D.addDays(t, 40), -40))
    }

    @Test
    fun `diffDays is symmetric`() {
        assertEquals(17, D.diffDays(D.addDays(t, 17), t))
        assertEquals(-17, D.diffDays(t, D.addDays(t, 17)))
    }

    @Test
    fun `range is inclusive of both ends`() {
        assertEquals(10, D.range(D.addDays(t, -9), t).size)
        assertEquals(1, D.range(t, t).size)
    }

    @Test
    fun `week starts on monday`() {
        assertEquals(1, D.dow(D.startOfWeek(t)))
        assertEquals(1, D.dow(D.startOfWeek("2026-03-29")))
    }

    /** The classic tracker bug: millisecond maths silently loses or gains a day here. */
    @Test
    fun `date maths survives a DST boundary`() {
        assertEquals(2, D.diffDays("2026-03-30", "2026-03-28"))
        assertEquals(5, D.range("2026-03-27", "2026-03-31").size)
        assertEquals(2, D.diffDays("2026-11-02", "2026-10-31"))
        assertEquals("2026-03-30", D.addDays("2026-03-28", 2))
    }

    @Test
    fun `month and year boundaries`() {
        assertEquals("2026-02-28", D.endOfMonth("2026-02-10"))
        assertEquals("2024-02-29", D.endOfMonth("2024-02-10")) // leap year
        assertEquals("2026-01-01", D.startOfYear("2026-07-04"))
        assertEquals("2026-12-31", D.endOfYear("2026-07-04"))
    }

    @Test
    fun `duration formatting`() {
        assertEquals("0m", D.formatDuration(0))
        assertEquals("45m", D.formatDuration(45))
        assertEquals("2h", D.formatDuration(120))
        assertEquals("2h 10m", D.formatDuration(130))
    }

    /* ----------------------------------------------------------------- habits */

    private fun dailyHabit(start: String) = Habit(
        id = "h1", name = "Read", schedule = HabitSchedule(ScheduleType.DAILY),
        tracking = TrackingType.BINARY, target = 1, startDate = start,
    )

    private fun logsFor(habitId: String, dates: List<String>) = dates.mapIndexed { i, d ->
        HabitLog("l$i", habitId, d, 1, true, 0)
    }

    @Test
    fun `streak counts back from today and an unlogged today does not break it`() {
        val h = dailyHabit(D.addDays(t, -30))
        // logged yesterday and the two days before, nothing today yet
        val db = Db(habits = listOf(h), habitLogs = logsFor(h.id, listOf(-1, -2, -3).map { D.addDays(t, it.toLong()) }))
        assertEquals(3, Stats.currentStreak(db, h, t))
    }

    @Test
    fun `a gap breaks the streak but not the best streak`() {
        val h = dailyHabit(D.addDays(t, -30))
        val dates = listOf(-1L, -3L, -4L, -5L, -6L).map { D.addDays(t, it) }
        val db = Db(habits = listOf(h), habitLogs = logsFor(h.id, dates))
        assertEquals(1, Stats.currentStreak(db, h, t))
        assertEquals(4, Stats.bestStreak(db, h, t))
    }

    @Test
    fun `completion rate is a percentage of due days`() {
        val h = dailyHabit(D.addDays(t, -30))
        val dates = (1L..5L).map { D.addDays(t, -it) }
        val db = Db(habits = listOf(h), habitLogs = logsFor(h.id, dates))
        assertEquals(50, Stats.completionRate(db, h, 10, t)) // 5 of the last 10 days
    }

    /**
     * A "4x a week" habit must not read as missed on the other three days —
     * that was understating completion and inflating the daily habit count.
     */
    @Test
    fun `weekly quota habits are measured against the quota not every day`() {
        val h = Habit(
            id = "hw", name = "Exercise",
            schedule = HabitSchedule(ScheduleType.TIMES_PER_WEEK, timesPerWeek = 4),
            tracking = TrackingType.BINARY, target = 1, startDate = D.addDays(t, -60),
        )
        val weekStart = D.startOfWeek(t)
        val db = Db(habits = listOf(h), habitLogs = logsFor(h.id, (0L..3L).map { D.addDays(weekStart, it) }))

        assertEquals(4, Stats.weekCompletions(db, h, t))
        // quota met, so it stops asking
        assertFalse(Stats.isHabitOpen(db, h, D.addDays(weekStart, 6)))
        // and a weekly habit has no concept of a missed day
        assertTrue(Stats.missedDays(db, h, 30, t).isEmpty())
        assertEquals("w", Stats.streakUnit(h))
    }

    @Test
    fun `weekly quota habit still asks while the quota is unmet`() {
        val h = Habit(
            id = "hw", name = "Exercise",
            schedule = HabitSchedule(ScheduleType.TIMES_PER_WEEK, timesPerWeek = 4),
            tracking = TrackingType.BINARY, target = 1, startDate = D.addDays(t, -60),
        )
        val weekStart = D.startOfWeek(t)
        val db = Db(habits = listOf(h), habitLogs = logsFor(h.id, listOf(D.addDays(weekStart, 0))))
        assertTrue(Stats.isHabitOpen(db, h, D.addDays(weekStart, 6)))
    }

    /* ------------------------------------------------------------------ seed */

    @Test
    fun `seed produces a coherent database`() {
        val db = Seed.demo()
        assertTrue("tasks", db.tasks.size > 50)
        assertEquals(5, db.habits.size)
        assertTrue("logs", db.habitLogs.size > 100)
        assertEquals(5, db.goals.size)
        assertTrue("failures", db.failures.isNotEmpty())
        assertTrue("sessions", db.sessions.isNotEmpty())

        // one log per habit per day, always
        val keys = db.habitLogs.map { it.habitId to it.date }
        assertEquals(keys.size, keys.toSet().size)

        // every failure points at a real task
        val ids = db.tasks.map { it.id }.toSet()
        assertTrue(db.failures.all { it.taskId in ids })
    }

    @Test
    fun `derived goals compute their own progress`() {
        val db = Seed.demo()
        db.goals.forEach { g ->
            val p = Stats.goalProgress(db, g)
            assertTrue("pct in range for ${g.title}: ${p.pct}", p.pct in 0f..1f)
        }
    }

    @Test
    fun `rollup never reports more habits done than due`() {
        val db = Seed.demo()
        Stats.rollup(db, D.addDays(t, -29), t).forEach {
            assertTrue("${it.date}: ${it.habitsDone}/${it.habitsDue}", it.habitsDone <= it.habitsDue)
        }
    }

    @Test
    fun `priority score is explainable and ordered`() {
        val db = Seed.demo()
        val top = Stats.topPriorities(db, 3, t)
        assertEquals(3, top.size)
        val scores = top.map { Stats.priorityScore(it, t).score }
        assertEquals(scores.sortedDescending(), scores)
        assertTrue(Stats.priorityScore(top[0], t).parts.isNotEmpty())
    }

    /* --------------------------------------------------------------- insights */

    @Test
    fun `insights never use causal language`() {
        val db = Seed.demo()
        val causal = Regex("""\b(because|caused|due to|makes you|leads to|results in)\b""", RegexOption.IGNORE_CASE)
        Insights.generate(db, t).forEach {
            assertFalse("causal wording in: ${it.title}", causal.containsMatchIn(it.title + " " + it.body))
            assertTrue("missing evidence: ${it.title}", it.evidence.length > 5)
        }
    }

    /** With nothing recorded, the engine must say nothing rather than invent something. */
    @Test
    fun `an empty database yields no insights`() {
        assertTrue(Insights.generate(Seed.empty(), t).isEmpty())
        assertEquals(0, Stats.todayProgress(Seed.empty(), t).total)
        assertEquals(7, Stats.rollup(Seed.empty(), D.addDays(t, -6), t).size)
    }

    @Test
    fun `a pattern is not emitted below the minimum sample`() {
        // ten days of data is under the 14-day floor, so no PATTERN may appear
        val db = Db(
            tasks = (1..10).map {
                com.example.mytracker.data.Task(
                    id = "t$it", title = "x", dueDate = D.addDays(t, -it.toLong()),
                    status = com.example.mytracker.data.TaskStatus.COMPLETED, completedAt = System.currentTimeMillis(),
                )
            },
        )
        assertTrue(Insights.generate(db, t).none { it.kind == com.example.mytracker.data.InsightKind.PATTERN })
    }

    @Test
    fun `failure breakdown is ranked`() {
        val db = Db(
            failures = listOf(
                FailureEntry("1", "t", FailureCode.DISTRACTED, "", t, 0),
                FailureEntry("2", "t", FailureCode.DISTRACTED, "", t, 0),
                FailureEntry("3", "t", FailureCode.FORGOT, "", t, 0),
            ),
        )
        val ranked = Stats.failureBreakdown(db, D.addDays(t, -7), t)
        assertEquals("Distracted", ranked.first().first)
        assertEquals(2, ranked.first().second)
    }

    /* ------------------------------------------------------------- theme */

    /* ------------------------------------------------------------- theme */

    /**
     * These exercise [ThemeMode.from], which is the whole of the decoder's
     * behaviour for this field — Json.kt does nothing but hand it the value
     * `optString("theme", "system")` returned. The codec itself is not covered
     * here because `org.json` is an unimplemented stub under JVM unit tests,
     * and adding a real one would mean adding a dependency this project
     * deliberately does without.
     */
    @Test
    fun `every stored theme key round-trips to its mode`() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.from(it.key)) }
    }

    @Test
    fun `a file written before themes existed reads as system`() {
        // a pre-existing install has no theme key at all, so the decoder's
        // default reaches from() as either the default string or null
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from("system"))
    }

    @Test
    fun `an unrecognised theme falls back rather than crashing`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from("solarized"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from(""))
    }

    @Test
    fun `the theme cycle is the same three states as the web build`() {
        // System -> Light -> Dark -> System, the order the top-bar button steps
        assertEquals(listOf("system", "light", "dark"), ThemeMode.entries.map { it.key })
    }

    /* ------------------------------------------------- first run is empty */

    /**
     * A new install must start with nothing recorded. Seeding demo data on first
     * run makes every number on every screen a fiction until the user notices,
     * and the insight engine would then report on invented tasks.
     */
    @Test
    fun `a new install has nothing recorded`() {
        val fresh = Seed.empty()
        assertTrue(fresh.tasks.isEmpty())
        assertTrue(fresh.habits.isEmpty())
        assertTrue(fresh.habitLogs.isEmpty())
        assertTrue(fresh.goals.isEmpty())
        assertTrue(fresh.sessions.isEmpty())
        assertTrue(fresh.failures.isEmpty())
        // categories are a fixed vocabulary, not a record of anything the user did
        assertTrue(fresh.categories.isNotEmpty())
    }

    @Test
    fun `an empty database produces no insights at all`() {
        assertTrue(Insights.generate(Seed.empty(), D.today()).isEmpty())
    }

    @Test
    fun `the demo set is still available on request`() {
        assertTrue(Seed.demo().tasks.isNotEmpty())
    }

    /* --------------------------------------------------- greeting and name */

    @Test
    fun `the greeting uses the name when there is one`() {
        assertEquals("Good morning, Musthafa", D.greetingLine("Musthafa", "07:00", "23:00", "09:00"))
        assertEquals("Good evening, Musthafa", D.greetingLine("Musthafa", "07:00", "23:00", "19:00"))
        assertEquals("Winding down, Sam", D.greetingLine("Sam", "07:00", "23:00", "23:30"))
        assertEquals("Good morning, Sam", D.greetingLine("  Sam  ", "07:00", "23:00", "09:00"))
    }

    /** It must never read "Good evening, there" — the old placeholder name. */
    @Test
    fun `with no name the greeting stands alone`() {
        assertEquals("Good evening", D.greetingLine("", "07:00", "23:00", "19:00"))
        assertEquals("Good morning", D.greetingLine("   ", "07:00", "23:00", "09:00"))
    }

    @Test
    fun `a new install has no name rather than a stand-in word`() {
        assertEquals("", Seed.empty().settings.name)
    }

    /* ----------------------------------------------------- first day of week */

    @Test
    fun `the week starts on the day the user chose`() {
        // 2026-10-01 is a Thursday
        assertEquals("2026-09-28", D.startOfWeek("2026-10-01", mondayFirst = true))
        assertEquals("2026-09-27", D.startOfWeek("2026-10-01", mondayFirst = false))
        // a Sunday belongs to the week that just ended when weeks start on Monday
        assertEquals("2026-09-28", D.startOfWeek("2026-10-04", mondayFirst = true))
        assertEquals("2026-10-04", D.startOfWeek("2026-10-04", mondayFirst = false))
    }

    @Test
    fun `weekday display order follows the same setting`() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), D.weekOrder(mondayFirst = true))
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), D.weekOrder(mondayFirst = false))
    }

    /* ----------------------------------------------------------- reminders */

    private val zone: ZoneId = ZoneId.of("UTC")

    /** Midnight UTC on the given day, as epoch millis. */
    private fun at(date: String, time: String): Long =
        LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
            .atZone(zone).toInstant().toEpochMilli()

    private fun remindersOn(
        tasks: List<Task> = emptyList(),
        habits: List<Habit> = emptyList(),
        logs: List<HabitLog> = emptyList(),
        enabled: Boolean = true,
        tasksOn: Boolean = true,
        habitsOn: Boolean = true,
    ) = Db(
        tasks = tasks,
        habits = habits,
        habitLogs = logs,
        settings = Settings(
            remindersEnabled = enabled,
            remindTasks = tasksOn,
            remindHabits = habitsOn,
        ),
    )

    private fun task(
        id: String = "t1",
        due: String? = "2026-10-02",
        time: String? = "09:00",
        status: TaskStatus = TaskStatus.NOT_STARTED,
    ) = Task(
        id = id, title = "Write the thing", dueDate = due, dueTime = time,
        status = status, priority = Priority.HIGH,
    )

    private fun dailyHabit(id: String = "h1", remind: String? = "08:00") = Habit(
        id = id, name = "Read", startDate = "2026-01-01", reminderTime = remind,
        schedule = HabitSchedule(ScheduleType.DAILY),
    )

    @Test
    fun `nothing is scheduled while reminders are switched off`() {
        val db = remindersOn(tasks = listOf(task()), habits = listOf(dailyHabit()), enabled = false)
        assertTrue(Reminders.plan(db, at("2026-10-01", "07:00"), zone).isEmpty())
    }

    @Test
    fun `a task with a time reminds at that time`() {
        val db = remindersOn(tasks = listOf(task()))
        val plan = Reminders.plan(db, at("2026-10-01", "07:00"), zone)
        assertEquals(1, plan.size)
        assertEquals(at("2026-10-02", "09:00"), plan[0].atMillis)
        assertEquals("Write the thing", plan[0].title)
    }

    @Test
    fun `a task with no time is never used`() {
        val db = remindersOn(tasks = listOf(task(time = null)))
        assertTrue(Reminders.plan(db, at("2026-10-01", "07:00"), zone).isEmpty())
    }

    @Test
    fun `a finished task is not reminded about`() {
        val db = remindersOn(tasks = listOf(task(status = TaskStatus.COMPLETED)))
        assertTrue(Reminders.plan(db, at("2026-10-01", "07:00"), zone).isEmpty())
    }

    @Test
    fun `nothing is scheduled in the past`() {
        val db = remindersOn(tasks = listOf(task(due = "2026-10-02", time = "09:00")))
        // now is after the due instant
        assertTrue(Reminders.plan(db, at("2026-10-02", "10:00"), zone).isEmpty())
    }

    @Test
    fun `nothing is scheduled beyond the horizon`() {
        val db = remindersOn(tasks = listOf(task(due = "2026-10-20")))
        assertTrue(Reminders.plan(db, at("2026-10-01", "07:00"), zone).isEmpty())
    }

    @Test
    fun `each switch silences only its own kind`() {
        val both = remindersOn(tasks = listOf(task()), habits = listOf(dailyHabit()))
        val plan = Reminders.plan(both, at("2026-10-01", "07:00"), zone)
        assertTrue(plan.any { it.tab == "tasks" })
        assertTrue(plan.any { it.tab == "habits" })

        val noTasks = both.copy(settings = both.settings.copy(remindTasks = false))
        assertTrue(Reminders.plan(noTasks, at("2026-10-01", "07:00"), zone).isNotEmpty())
        assertTrue(Reminders.plan(noTasks, at("2026-10-01", "07:00"), zone).all { it.tab == "habits" })

        val noHabits = both.copy(settings = both.settings.copy(remindHabits = false))
        assertTrue(Reminders.plan(noHabits, at("2026-10-01", "07:00"), zone).all { it.tab == "tasks" })
    }

    @Test
    fun `a habit with no reminder time stays quiet`() {
        val db = remindersOn(habits = listOf(dailyHabit(remind = null)))
        assertTrue(Reminders.plan(db, at("2026-10-01", "07:00"), zone).isEmpty())
    }

    @Test
    fun `a habit already logged today is not nagged again`() {
        val h = dailyHabit()
        val today = LocalDate.now(zone).toString()
        val db = remindersOn(
            habits = listOf(h),
            logs = listOf(HabitLog("l1", h.id, today, 1, true, 0L)),
        )
        val plan = Reminders.plan(db, at(today, "00:30"), zone)
        assertTrue(plan.none { it.key.endsWith(today) })
    }

    @Test
    fun `an archived habit is never reminded about`() {
        val db = remindersOn(habits = listOf(dailyHabit().copy(archived = true)))
        assertTrue(Reminders.plan(db, at("2026-10-01", "07:00"), zone).isEmpty())
    }

    @Test
    fun `reminder keys are stable and unique so rescheduling replaces`() {
        val db = remindersOn(tasks = listOf(task("a"), task("b")), habits = listOf(dailyHabit()))
        val now = at("2026-10-01", "07:00")
        val first = Reminders.plan(db, now, zone)
        val second = Reminders.plan(db, now, zone)
        assertEquals(first.map { it.key }, second.map { it.key })
        assertEquals(first.size, first.map { it.key }.toSet().size)
    }

    @Test
    fun `the plan comes back in time order`() {
        val db = remindersOn(
            tasks = listOf(task("late", time = "18:00"), task("early", time = "06:00")),
        )
        val plan = Reminders.plan(db, at("2026-10-01", "01:00"), zone)
        assertEquals(plan.sortedBy { it.atMillis }, plan)
    }

    @Test
    fun `a malformed time is ignored rather than crashing`() {
        assertEquals(null, Reminders.parseTime("25:99"))
        assertEquals(null, Reminders.parseTime(""))
        assertEquals(null, Reminders.parseTime(null))
        assertEquals(LocalTime.of(7, 30), Reminders.parseTime("07:30"))
    }

    /**
     * The horizon exists so reminders still arrive on a day the app is never
     * opened. A daily habit must therefore be queued for tomorrow as well.
     */
    @Test
    fun `a daily habit is queued beyond today`() {
        val db = remindersOn(habits = listOf(dailyHabit()))
        val plan = Reminders.plan(db, at("2026-10-01", "07:00"), zone)
        assertEquals(listOf(at("2026-10-01", "08:00"), at("2026-10-02", "08:00")), plan.map { it.atMillis })
    }

    /* ------------------------------------------------------ reminder style */

    @Test
    fun `reminder style defaults to the quiet one`() {
        // an app that starts ringing like an alarm before being asked to is an
        // app you uninstall, so the loud option is opt-in
        assertEquals(ReminderStyle.NOTIFICATION, Settings().reminderStyle)
        assertEquals(ReminderStyle.NOTIFICATION, ReminderStyle.from(null))
    }

    @Test
    fun `every reminder style key round-trips`() {
        ReminderStyle.entries.forEach { assertEquals(it, ReminderStyle.from(it.key)) }
    }

    @Test
    fun `an unrecognised reminder style falls back rather than crashing`() {
        assertEquals(ReminderStyle.NOTIFICATION, ReminderStyle.from("klaxon"))
        assertEquals(ReminderStyle.NOTIFICATION, ReminderStyle.from(""))
    }

    /** The style changes how a reminder arrives, never whether it is planned. */
    @Test
    fun `style does not change what gets scheduled`() {
        val quiet = remindersOn(tasks = listOf(task()), habits = listOf(dailyHabit()))
        val loud = quiet.copy(settings = quiet.settings.copy(reminderStyle = ReminderStyle.ALARM))
        val now = at("2026-10-01", "07:00")
        assertEquals(
            Reminders.plan(quiet, now, zone).map { it.key },
            Reminders.plan(loud, now, zone).map { it.key },
        )
    }
}
