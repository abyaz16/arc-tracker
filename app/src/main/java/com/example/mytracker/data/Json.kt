package com.example.mytracker.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Hand-written JSON codec over org.json (part of the Android platform).
 *
 * Chosen over kotlinx.serialization purely to keep the dependency surface at zero —
 * the schema is small and stable, and every read is defensive so a partially written
 * or older file degrades to defaults rather than crashing on launch.
 */

private fun JSONObject.strOrNull(key: String): String? =
    if (isNull(key)) null else optString(key, "").ifEmpty { null }

private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)

private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)

private fun JSONArray.strings(): List<String> = (0 until length()).map { optString(it) }

private fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

private inline fun <reified T : Enum<T>> JSONObject.enum(key: String, fallback: T): T =
    runCatching { enumValueOf<T>(optString(key, fallback.name)) }.getOrDefault(fallback)

/* ------------------------------------------------------------------ encode */

fun Db.toJson(): String = JSONObject().apply {
    put("version", version)
    put("tasks", JSONArray(tasks.map { it.toJson() }))
    put("habits", JSONArray(habits.map { it.toJson() }))
    put("habitLogs", JSONArray(habitLogs.map { it.toJson() }))
    put("goals", JSONArray(goals.map { it.toJson() }))
    put("sessions", JSONArray(sessions.map { it.toJson() }))
    put("failures", JSONArray(failures.map { it.toJson() }))
    put("categories", JSONArray(categories.map { it.toJson() }))
    put("settings", settings.toJson())
    put("dismissedInsights", JSONArray(dismissedInsights))
}.toString()

private fun Task.toJson() = JSONObject().apply {
    put("id", id); put("title", title); put("notes", notes)
    put("status", status.name); put("priority", priority.name)
    put("categoryId", categoryId); put("goalId", goalId)
    put("dueDate", dueDate); put("dueTime", dueTime); put("reminderEnabled", reminderEnabled); put("reminderStyle", reminderStyle?.name)
    put("estimateMin", estimateMin); put("actualMin", actualMin)
    put("subtasks", JSONArray(subtasks.map {
        JSONObject().apply { put("id", it.id); put("title", it.title); put("done", it.done) }
    }))
    put("repeat", repeat.name); put("order", order)
    put("postponeCount", postponeCount); put("createdAt", createdAt); put("completedAt", completedAt)
}

private fun Habit.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("icon", icon); put("colorKey", colorKey)
    put("schedule", JSONObject().apply {
        put("type", schedule.type.name)
        put("days", JSONArray(schedule.days))
        put("timesPerWeek", schedule.timesPerWeek)
    })
    put("tracking", tracking.name); put("target", target); put("unit", unit)
    put("goalId", goalId); put("startDate", startDate); put("archived", archived)
    put("reminderTime", reminderTime); put("reminderEnabled", reminderEnabled); put("reminderStyle", reminderStyle?.name)
    put("order", order); put("createdAt", createdAt)
}

private fun HabitLog.toJson() = JSONObject().apply {
    put("id", id); put("habitId", habitId); put("date", date)
    put("value", value); put("completed", completed); put("loggedAt", loggedAt)
}

private fun Goal.toJson() = JSONObject().apply {
    put("id", id); put("title", title); put("why", why); put("horizon", horizon.name)
    put("categoryId", categoryId); put("targetType", targetType.name)
    put("targetValue", targetValue); put("manualValue", manualValue); put("unit", unit)
    put("derived", derived?.let {
        JSONObject().apply { put("kind", it.kind); put("habitId", it.habitId); put("categoryId", it.categoryId) }
    })
    put("periodStart", periodStart); put("periodEnd", periodEnd); put("status", status.name)
    put("milestones", JSONArray(milestones.map {
        JSONObject().apply { put("id", it.id); put("title", it.title); put("dueDate", it.dueDate); put("done", it.done) }
    }))
    put("order", order); put("createdAt", createdAt); put("completedAt", completedAt)
}

private fun FocusSession.toJson() = JSONObject().apply {
    put("id", id); put("taskId", taskId); put("plannedMin", plannedMin); put("actualMin", actualMin)
    put("startedAt", startedAt); put("endedAt", endedAt); put("completed", completed)
    put("interruptions", interruptions); put("rating", rating)
}

private fun FailureEntry.toJson() = JSONObject().apply {
    put("id", id); put("taskId", taskId); put("code", code.name)
    put("note", note); put("date", date); put("createdAt", createdAt)
}

private fun Category.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("colorKey", colorKey)
}

private fun Settings.toJson() = JSONObject().apply {
    put("name", name); put("wakeTime", wakeTime); put("sleepTime", sleepTime)
    put("theme", theme.key); put("weekStartsMonday", weekStartsMonday)
    put("remindersEnabled", remindersEnabled)
    put("remindTasks", remindTasks); put("remindHabits", remindHabits)
    put("reminderStyle", reminderStyle.key)
}

/* ------------------------------------------------------------------ decode */

fun dbFromJson(raw: String): Db? = runCatching {
    val o = JSONObject(raw)
    if (o.optInt("version", 0) != 1) return null
    Db(
        version = 1,
        tasks = o.optJSONArray("tasks")?.objects()?.map { it.toTask() } ?: emptyList(),
        habits = o.optJSONArray("habits")?.objects()?.map { it.toHabit() } ?: emptyList(),
        habitLogs = o.optJSONArray("habitLogs")?.objects()?.map { it.toHabitLog() } ?: emptyList(),
        goals = o.optJSONArray("goals")?.objects()?.map { it.toGoal() } ?: emptyList(),
        sessions = o.optJSONArray("sessions")?.objects()?.map { it.toSession() } ?: emptyList(),
        failures = o.optJSONArray("failures")?.objects()?.map { it.toFailure() } ?: emptyList(),
        categories = o.optJSONArray("categories")?.objects()?.map { it.toCategory() } ?: emptyList(),
        settings = o.optJSONObject("settings")?.toSettings() ?: Settings(),
        dismissedInsights = o.optJSONArray("dismissedInsights")?.strings() ?: emptyList(),
    )
}.getOrNull()

private fun JSONObject.toTask() = Task(
    id = optString("id"),
    title = optString("title"),
    notes = optString("notes", ""),
    status = enum("status", TaskStatus.NOT_STARTED),
    priority = enum("priority", Priority.MEDIUM),
    categoryId = strOrNull("categoryId"),
    goalId = strOrNull("goalId"),
    dueDate = strOrNull("dueDate"),
    dueTime = strOrNull("dueTime"),
    reminderEnabled = optBoolean("reminderEnabled", optString("dueTime").isNotEmpty()),
    reminderStyle = strOrNull("reminderStyle")?.let { runCatching { ReminderStyle.valueOf(it) }.getOrNull() },
    estimateMin = intOrNull("estimateMin"),
    actualMin = intOrNull("actualMin"),
    subtasks = optJSONArray("subtasks")?.objects()?.map {
        Subtask(it.optString("id"), it.optString("title"), it.optBoolean("done"))
    } ?: emptyList(),
    repeat = enum("repeat", RepeatKind.NONE),
    order = optLong("order"),
    postponeCount = optInt("postponeCount"),
    createdAt = optLong("createdAt"),
    completedAt = longOrNull("completedAt"),
)

private fun JSONObject.toHabit(): Habit {
    val s = optJSONObject("schedule")
    return Habit(
        id = optString("id"),
        name = optString("name"),
        icon = optString("icon", "target"),
        colorKey = optString("colorKey", "c1"),
        schedule = HabitSchedule(
            type = s?.enum("type", ScheduleType.DAILY) ?: ScheduleType.DAILY,
            days = s?.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList(),
            timesPerWeek = s?.optInt("timesPerWeek", 4) ?: 4,
        ),
        tracking = enum("tracking", TrackingType.BINARY),
        target = optInt("target", 1),
        unit = optString("unit", ""),
        goalId = strOrNull("goalId"),
        reminderEnabled = optBoolean("reminderEnabled", optString("reminderTime").isNotEmpty()),
    reminderTime = strOrNull("reminderTime"),
    reminderStyle = strOrNull("reminderStyle")?.let { runCatching { ReminderStyle.valueOf(it) }.getOrNull() },
        startDate = optString("startDate"),
        archived = optBoolean("archived"),
        order = optLong("order"),
        createdAt = optLong("createdAt"),
    )
}

private fun JSONObject.toHabitLog() = HabitLog(
    id = optString("id"),
    habitId = optString("habitId"),
    date = optString("date"),
    value = optInt("value"),
    completed = optBoolean("completed"),
    loggedAt = optLong("loggedAt"),
)

private fun JSONObject.toGoal() = Goal(
    id = optString("id"),
    title = optString("title"),
    why = optString("why", ""),
    horizon = enum("horizon", Horizon.MONTH),
    categoryId = strOrNull("categoryId"),
    targetType = enum("targetType", TargetType.MILESTONES),
    targetValue = optInt("targetValue"),
    manualValue = optInt("manualValue"),
    unit = optString("unit", ""),
    derived = optJSONObject("derived")?.let {
        DerivedSource(it.optString("kind"), it.strOrNull("habitId"), it.strOrNull("categoryId"))
    },
    periodStart = optString("periodStart"),
    periodEnd = optString("periodEnd"),
    status = enum("status", GoalStatus.ACTIVE),
    milestones = optJSONArray("milestones")?.objects()?.map {
        Milestone(it.optString("id"), it.optString("title"), it.strOrNull("dueDate"), it.optBoolean("done"))
    } ?: emptyList(),
    order = optLong("order"),
    createdAt = optLong("createdAt"),
    completedAt = longOrNull("completedAt"),
)

private fun JSONObject.toSession() = FocusSession(
    id = optString("id"),
    taskId = strOrNull("taskId"),
    plannedMin = optInt("plannedMin"),
    actualMin = optInt("actualMin"),
    startedAt = optLong("startedAt"),
    endedAt = longOrNull("endedAt"),
    completed = optBoolean("completed"),
    interruptions = optInt("interruptions"),
    rating = intOrNull("rating"),
)

private fun JSONObject.toFailure() = FailureEntry(
    id = optString("id"),
    taskId = optString("taskId"),
    code = enum("code", FailureCode.OTHER),
    note = optString("note", ""),
    date = optString("date"),
    createdAt = optLong("createdAt"),
)

private fun JSONObject.toCategory() = Category(optString("id"), optString("name"), optString("colorKey", "c1"))

private fun JSONObject.toSettings() = Settings(
    // installs made before the name was asked for carry the old placeholder
    name = optString("name", "").let { if (it == "there") "" else it },
    wakeTime = optString("wakeTime", "07:00"),
    sleepTime = optString("sleepTime", "23:00"),
    // an older file has no theme key, and "system" is the right thing to assume
    theme = ThemeMode.from(optString("theme", ThemeMode.SYSTEM.key)),
    weekStartsMonday = optBoolean("weekStartsMonday", true),
    remindersEnabled = optBoolean("remindersEnabled", false),
    remindTasks = optBoolean("remindTasks", true),
    remindHabits = optBoolean("remindHabits", true),
    reminderStyle = ReminderStyle.from(optString("reminderStyle", ReminderStyle.NOTIFICATION.key)),
)
