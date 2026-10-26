package com.example.mytracker.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.mytracker.domain.D
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Single source of truth. Local-first: everything lives in one JSON file in the
 * app's private storage.
 *
 * Deliberately dependency-free — Compose state for reads, a Handler debounce plus a
 * single background thread for writes. No ViewModel, no coroutine artifacts, nothing
 * that could drag the build into version conflicts.
 *
 * Held as a process singleton so it survives configuration changes without a ViewModel.
 */
class Store private constructor(private val appContext: Context) {

    companion object {
        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context.applicationContext).also { instance = it }
            }
    }

    private val file: File get() = File(appContext.filesDir, "arc.json")
    private val legacyFile: File get() = File(appContext.filesDir, "cadence.json")
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val saveToken = Any()

    /** Reading this from a composable subscribes it to every change. */
    var db by mutableStateOf(load())
        private set

    var toast by mutableStateOf<Toast?>(null)
        private set

    data class Toast(val id: String, val message: String, val undo: (() -> Unit)? = null)

    private fun isSampleTask(t: Task): Boolean {
        val title = t.title.lowercase()
        return t.id == "task-hero" ||
            t.id.startsWith("task-done-") ||
            t.id.startsWith("task-seed-") ||
            title.contains("shape arc launch") ||
            title.contains("tax return") ||
            title.contains("ship the release") ||
            title.contains("weekly review") ||
            title.contains("draft launch") ||
            title.contains("finalize tokens")
    }

    private fun isSampleHabit(h: Habit): Boolean {
        val name = h.name.lowercase()
        return h.id == "hab-deepwork" ||
            h.id == "hab-hydrate" ||
            h.id.startsWith("hab-seed-") ||
            name.contains("hydration") ||
            name.contains("deep work") ||
            name.contains("read 20 pages")
    }

    private fun isSampleGoal(g: Goal): Boolean {
        val title = g.title.lowercase()
        return g.id == "goal-arc-launch" ||
            g.id == "goal-run" ||
            g.id == "goal-deepwork" ||
            g.id.startsWith("goal-seed-") ||
            title.contains("arc launch") ||
            title.contains("half marathon")
    }

    private fun isSampleLog(l: HabitLog): Boolean {
        return l.id.startsWith("log-dw-") ||
            l.id.startsWith("log-hy-") ||
            l.id.startsWith("log-rd-") ||
            l.id.startsWith("log-seed-")
    }

    /**
     * Production-ready: A new install starts clean and empty. Any legacy or sample data
     * is permanently purged on load.
     */
    private fun load(): Db = runCatching {
        // Purge legacy cadence.json file if present
        if (legacyFile.exists()) {
            runCatching { legacyFile.delete() }
        }

        if (!file.exists()) {
            val fresh = Seed.empty()
            persist(fresh)
            fresh
        } else {
            val parsed = dbFromJson(file.readText()) ?: Seed.empty()
            val hasSample =
                parsed.tasks.any { isSampleTask(it) } ||
                parsed.habits.any { isSampleHabit(it) } ||
                parsed.goals.any { isSampleGoal(it) } ||
                parsed.habitLogs.any { isSampleLog(it) } ||
                parsed.settings.name == "Alex" ||
                parsed.settings.name == "there"

            if (hasSample) {
                val clean = parsed.copy(
                    tasks = parsed.tasks.filterNot { isSampleTask(it) },
                    habits = parsed.habits.filterNot { isSampleHabit(it) },
                    goals = parsed.goals.filterNot { isSampleGoal(it) },
                    habitLogs = parsed.habitLogs.filterNot { isSampleLog(it) },
                    settings = parsed.settings.copy(
                        name = if (parsed.settings.name == "Alex" || parsed.settings.name == "there") "" else parsed.settings.name
                    ),
                    categories = if (parsed.categories.isEmpty()) Seed.categories else parsed.categories
                )
                persist(clean)
                clean
            } else {
                parsed
            }
        }
    }.getOrElse { 
        val fresh = Seed.empty()
        persist(fresh)
        fresh
    }

    private fun update(block: (Db) -> Db) {
        db = block(db)
        onDataChanged?.invoke()
        scheduleSave()
    }

    private fun scheduleSave() {
        main.removeCallbacksAndMessages(saveToken)
        main.postAtTime({ persist(db) }, saveToken, android.os.SystemClock.uptimeMillis() + 200)
    }

    private fun persist(snapshot: Db) {
        io.execute {
            runCatching {
                // write-then-rename, so a crash mid-write can't leave a truncated file
                val tmp = File(appContext.filesDir, "arc.json.tmp")
                tmp.writeText(snapshot.toJson())
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            }
        }
    }

    fun showToast(message: String, undo: (() -> Unit)? = null) {
        val t = Toast(UUID.randomUUID().toString(), message, undo)
        toast = t
        main.postDelayed({ if (toast?.id == t.id) toast = null }, 4000)
    }

    fun clearToast() {
        toast = null
    }

    private fun uid(p: String) = p + UUID.randomUUID().toString().take(12)

    /* ------------------------------------------------------------------ tasks */

    fun addTask(t: Task): Task {
        val task = t.copy(
            id = t.id.ifEmpty { uid("t-") },
            order = -System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
        )
        update { it.copy(tasks = listOf(task) + it.tasks) }
        return task
    }

    fun updateTask(id: String, block: (Task) -> Task) =
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) block(it) else it }) }

    fun toggleTask(id: String) = update { d ->
        val t = d.tasks.firstOrNull { it.id == id } ?: return@update d
        if (t.isDone) {
            d.copy(tasks = d.tasks.map {
                if (it.id == id) it.copy(status = TaskStatus.NOT_STARTED, completedAt = null) else it
            })
        } else {
            val completed = t.copy(status = TaskStatus.COMPLETED, completedAt = System.currentTimeMillis())
            val list = d.tasks.map { if (it.id == id) completed else it }
            // completing a repeating task schedules the next instance
            val next = nextOccurrence(t)
            d.copy(tasks = if (next != null) listOf(next) + list else list)
        }
    }

    private fun nextOccurrence(t: Task): Task? {
        val due = t.dueDate ?: return null
        val nextDate = when (t.repeat) {
            RepeatKind.NONE -> return null
            RepeatKind.DAILY -> D.addDays(due, 1)
            RepeatKind.MONTHLY -> D.parse(due).plusMonths(1).toString()
            RepeatKind.WEEKDAYS -> {
                var c = D.addDays(due, 1)
                var guard = 0
                while (D.dow(c) > 5 && guard++ < 7) c = D.addDays(c, 1)
                c
            }
        }
        return t.copy(
            id = uid("t-"),
            status = TaskStatus.NOT_STARTED,
            completedAt = null,
            actualMin = null,
            dueDate = nextDate,
            postponeCount = 0,
            createdAt = System.currentTimeMillis(),
            subtasks = t.subtasks.map { it.copy(done = false) },
        )
    }

    fun deleteTask(id: String) = update { d ->
        d.copy(
            tasks = d.tasks.filterNot { it.id == id },
            failures = d.failures.filterNot { it.taskId == id },
        )
    }

    fun restoreTask(t: Task) = update { d -> d.copy(tasks = listOf(t) + d.tasks) }

    /** Moving keeps history — the postpone counter is what makes stuck work visible. */
    fun postpone(id: String, to: String) = updateTask(id) {
        it.copy(dueDate = to, postponeCount = it.postponeCount + 1, status = TaskStatus.POSTPONED)
    }

    fun postponeAll(ids: List<String>, to: String) = update { d ->
        d.copy(tasks = d.tasks.map {
            if (it.id in ids) it.copy(dueDate = to, postponeCount = it.postponeCount + 1, status = TaskStatus.POSTPONED)
            else it
        })
    }

    fun recordFailure(taskId: String, code: FailureCode, note: String) = update { d ->
        val t = d.tasks.firstOrNull { it.id == taskId } ?: return@update d
        d.copy(
            tasks = d.tasks.map { if (it.id == taskId) it.copy(status = TaskStatus.FAILED) else it },
            failures = d.failures + FailureEntry(
                uid("f-"), taskId, code, note, t.dueDate ?: D.today(), System.currentTimeMillis(),
            ),
        )
    }

    fun toggleSubtask(taskId: String, subId: String) = updateTask(taskId) { t ->
        t.copy(subtasks = t.subtasks.map { if (it.id == subId) it.copy(done = !it.done) else it })
    }

    fun reorderTasks(ids: List<String>) = update { d ->
        val index = ids.withIndex().associate { (i, id) -> id to i.toLong() }
        d.copy(tasks = d.tasks.map { t -> index[t.id]?.let { t.copy(order = it) } ?: t })
    }

    /* ----------------------------------------------------------------- habits */

    fun addHabit(h: Habit): Habit {
        val habit = h.copy(
            id = h.id.ifEmpty { uid("h-") },
            startDate = h.startDate.ifEmpty { D.today() },
            order = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
        )
        update { it.copy(habits = it.habits + habit) }
        return habit
    }

    fun updateHabit(id: String, block: (Habit) -> Habit) =
        update { d -> d.copy(habits = d.habits.map { if (it.id == id) block(it) else it }) }

    fun deleteHabit(id: String) = update { d ->
        d.copy(
            habits = d.habits.filterNot { it.id == id },
            habitLogs = d.habitLogs.filterNot { it.habitId == id },
        )
    }

    /** Logs are keyed on (habitId, date) — exactly one row per habit per day. */
    fun logHabit(habitId: String, date: String, value: Int? = null) = update { d ->
        val h = d.habits.firstOrNull { it.id == habitId } ?: return@update d
        val existing = d.habitLogs.firstOrNull { it.habitId == habitId && it.date == date }

        if (h.tracking == TrackingType.BINARY) {
            return@update if (existing != null) {
                d.copy(habitLogs = d.habitLogs.filterNot { it.id == existing.id })
            } else {
                d.copy(habitLogs = d.habitLogs + HabitLog(uid("hl-"), habitId, date, 1, true, System.currentTimeMillis()))
            }
        }

        val v = value ?: ((existing?.value ?: 0) + 1)
        when {
            existing != null && v <= 0 -> d.copy(habitLogs = d.habitLogs.filterNot { it.id == existing.id })
            existing != null -> d.copy(
                habitLogs = d.habitLogs.map {
                    if (it.id == existing.id) it.copy(value = v, completed = v >= h.target, loggedAt = System.currentTimeMillis())
                    else it
                },
            )

            v > 0 -> d.copy(habitLogs = d.habitLogs + HabitLog(uid("hl-"), habitId, date, v, v >= h.target, System.currentTimeMillis()))
            else -> d
        }
    }

    /* ------------------------------------------------------------------ goals */

    fun addGoal(g: Goal): Goal {
        val goal = g.copy(
            id = g.id.ifEmpty { uid("g-") },
            order = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
        )
        update { it.copy(goals = it.goals + goal) }
        return goal
    }

    fun updateGoal(id: String, block: (Goal) -> Goal) =
        update { d -> d.copy(goals = d.goals.map { if (it.id == id) block(it) else it }) }

    fun deleteGoal(id: String) = update { d ->
        d.copy(
            goals = d.goals.filterNot { it.id == id },
            tasks = d.tasks.map { if (it.goalId == id) it.copy(goalId = null) else it },
        )
    }

    fun toggleMilestone(goalId: String, msId: String) = updateGoal(goalId) { g ->
        val milestones = g.milestones.map { if (it.id == msId) it.copy(done = !it.done) else it }
        val complete = g.targetType == TargetType.MILESTONES &&
            milestones.isNotEmpty() && milestones.all { it.done }
        g.copy(
            milestones = milestones,
            status = if (complete) GoalStatus.COMPLETED else GoalStatus.ACTIVE,
            completedAt = if (complete) System.currentTimeMillis() else null,
        )
    }

    /* ------------------------------------------------------------------ focus */

    fun saveSession(s: FocusSession) =
        update { it.copy(sessions = it.sessions + s.copy(id = uid("fs-"))) }

    /* ------------------------------------------------------------------- misc */

    fun updateSettings(block: (Settings) -> Settings) =
        update { d -> d.copy(settings = block(d.settings)) }

    /**
     * Called after any change that could move a reminder. The Store cannot
     * depend on the notify layer directly (it would drag Android types into the
     * data layer), so the app hands it a callback at startup.
     */
    var onDataChanged: (() -> Unit)? = null

    /** Steps System -> Light -> Dark -> System, which is the whole control. */
    fun cycleTheme(): ThemeMode {
        val next = when (db.settings.theme) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        updateSettings { it.copy(theme = next) }
        return next
    }

    fun dismissInsight(id: String) = update {
        if (id in it.dismissedInsights) it else it.copy(dismissedInsights = it.dismissedInsights + id)
    }

    fun reset(demo: Boolean = false) {
        db = Seed.empty()
        persist(db)
        onDataChanged?.invoke()
    }
}
