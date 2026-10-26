package com.example.mytracker.data

/**
 * Domain model. Mirrors the web build and docs/03-data-model.md, trimmed to the core loop.
 *
 * Convention: anything a human would call "a day" is a local `yyyy-MM-dd` String.
 * Instants are epoch millis. Mixing those two is the classic bug in trackers.
 */

enum class TaskStatus { NOT_STARTED, IN_PROGRESS, COMPLETED, POSTPONED, CANCELLED, FAILED }

enum class Priority(val label: String, val weight: Int) {
    CRITICAL("Critical", 25),
    HIGH("High", 18),
    MEDIUM("Medium", 10),
    LOW("Low", 4),
}

enum class RepeatKind { NONE, DAILY, WEEKDAYS, MONTHLY }

data class Subtask(val id: String, val title: String, val done: Boolean = false)

data class Task(
    val id: String,
    val title: String,
    val notes: String = "",
    val status: TaskStatus = TaskStatus.NOT_STARTED,
    val priority: Priority = Priority.MEDIUM,
    val categoryId: String? = null,
    val goalId: String? = null,
    val dueDate: String? = null,
    val dueTime: String? = null,
    val reminderEnabled: Boolean = false,
    val reminderStyle: ReminderStyle? = null,
    val estimateMin: Int? = null,
    val actualMin: Int? = null,
    val subtasks: List<Subtask> = emptyList(),
    val repeat: RepeatKind = RepeatKind.NONE,
    val order: Long = 0,
    val postponeCount: Int = 0,
    val createdAt: Long = 0,
    val completedAt: Long? = null,
) {
    val isOpen: Boolean
        get() = status == TaskStatus.NOT_STARTED || status == TaskStatus.IN_PROGRESS || status == TaskStatus.POSTPONED
    val isDone: Boolean get() = status == TaskStatus.COMPLETED
}

enum class ScheduleType { DAILY, WEEKDAYS, TIMES_PER_WEEK }

enum class TrackingType { BINARY, COUNT, DURATION }

data class HabitSchedule(
    val type: ScheduleType = ScheduleType.DAILY,
    /** 1=Mon .. 7=Sun, matching java.time.DayOfWeek */
    val days: List<Int> = emptyList(),
    val timesPerWeek: Int = 4,
)

data class Habit(
    val id: String,
    val name: String,
    val icon: String = "target",
    val colorKey: String = "c1",
    val schedule: HabitSchedule = HabitSchedule(),
    val tracking: TrackingType = TrackingType.BINARY,
    val target: Int = 1,
    val unit: String = "",
    val goalId: String? = null,
    /** "HH:mm", or null for no reminder on this habit. */
    val reminderEnabled: Boolean = false,
    val reminderTime: String? = null,
    val reminderStyle: ReminderStyle? = null,
    val startDate: String = "",
    val archived: Boolean = false,
    val order: Long = 0,
    val createdAt: Long = 0,
)

data class HabitLog(
    val id: String,
    val habitId: String,
    val date: String,
    val value: Int,
    val completed: Boolean,
    val loggedAt: Long,
)

enum class Horizon { MONTH, YEAR }

enum class GoalStatus { ACTIVE, COMPLETED, ABANDONED, PAUSED }

enum class TargetType { BINARY, NUMERIC, MILESTONES, DERIVED }

data class Milestone(val id: String, val title: String, val dueDate: String? = null, val done: Boolean = false)

/** How a derived goal computes its own progress. The user never updates these by hand. */
data class DerivedSource(val kind: String, val habitId: String? = null, val categoryId: String? = null)

data class Goal(
    val id: String,
    val title: String,
    val why: String = "",
    val horizon: Horizon = Horizon.MONTH,
    val categoryId: String? = null,
    val targetType: TargetType = TargetType.MILESTONES,
    val targetValue: Int = 0,
    val manualValue: Int = 0,
    val unit: String = "",
    val derived: DerivedSource? = null,
    val periodStart: String = "",
    val periodEnd: String = "",
    val status: GoalStatus = GoalStatus.ACTIVE,
    val milestones: List<Milestone> = emptyList(),
    val order: Long = 0,
    val createdAt: Long = 0,
    val completedAt: Long? = null,
)

data class FocusSession(
    val id: String,
    val taskId: String? = null,
    val plannedMin: Int,
    val actualMin: Int,
    val startedAt: Long,
    val endedAt: Long? = null,
    val completed: Boolean = false,
    val interruptions: Int = 0,
    val rating: Int? = null,
)

enum class FailureCode(val label: String) {
    FORGOT("Forgot"),
    PROCRASTINATED("Procrastinated"),
    TOO_TIRED("Too tired"),
    TOO_DIFFICULT("Too difficult"),
    NO_TIME("Not enough time"),
    DISTRACTED("Distracted"),
    PHONE("Phone"),
    UNEXPECTED_WORK("Unexpected work"),
    LOST_MOTIVATION("Lost motivation"),
    POOR_PLANNING("Poor planning"),
    OTHER("Something else"),
}

data class FailureEntry(
    val id: String,
    val taskId: String,
    val code: FailureCode,
    val note: String = "",
    val date: String,
    val createdAt: Long,
)

data class Category(val id: String, val name: String, val colorKey: String)

/**
 * How a reminder arrives.
 *
 * [NOTIFICATION] is an ordinary quiet notification on an inexact alarm, which
 * needs no extra permission and may land a few minutes late.
 *
 * [ALARM] rings with the device's alarm sound, vibrates, and is scheduled
 * exactly to the minute — which on Android 12 and later needs the "alarms and
 * reminders" permission, granted in system settings.
 */
enum class ReminderStyle(val key: String, val label: String) {
    NOTIFICATION("notification", "Notification"),
    ALARM("alarm", "Alarm");

    companion object {
        fun from(key: String?): ReminderStyle = entries.firstOrNull { it.key == key } ?: NOTIFICATION
    }
}

/** Which palette to paint in. "system" follows the device. */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun from(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

data class Settings(
    /** Empty until the user gives one. It is not filled with a stand-in word. */
    val name: String = "",
    val wakeTime: String = "07:00",
    val sleepTime: String = "23:00",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val weekStartsMonday: Boolean = true,
    /**
     * The master switch. Off by default: an app that starts notifying you
     * before you have asked it to is an app you turn off entirely.
     */
    val remindersEnabled: Boolean = false,
    /** Remind about tasks that carry a time of day. */
    val remindTasks: Boolean = true,
    /** Remind about habits that have a reminder time set. */
    val remindHabits: Boolean = true,
    val reminderStyle: ReminderStyle = ReminderStyle.NOTIFICATION,
)

data class Db(
    val version: Int = 1,
    val tasks: List<Task> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val habitLogs: List<HabitLog> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val sessions: List<FocusSession> = emptyList(),
    val failures: List<FailureEntry> = emptyList(),
    val categories: List<Category> = emptyList(),
    val settings: Settings = Settings(),
    val dismissedInsights: List<String> = emptyList(),
)

/** An observation from the insight engine. The kind is shown to the user as a label. */
enum class InsightKind(val label: String) {
    FACT("FACT"),
    PATTERN("PATTERN"),
    SAID("YOU SAID"),
    SUGGESTION("SUGGESTION"),
}

data class Insight(
    val id: String,
    val kind: InsightKind,
    val title: String,
    val body: String,
    val evidence: String,
)
