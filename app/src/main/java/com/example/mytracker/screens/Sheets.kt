package com.example.mytracker.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.mytracker.data.Db
import com.example.mytracker.data.DerivedSource
import com.example.mytracker.data.FailureCode
import com.example.mytracker.data.Goal
import com.example.mytracker.data.Habit
import com.example.mytracker.data.HabitSchedule
import com.example.mytracker.data.Horizon
import com.example.mytracker.data.Milestone
import com.example.mytracker.data.Priority
import com.example.mytracker.data.RepeatKind
import com.example.mytracker.data.ReminderStyle
import com.example.mytracker.ui.CToggle
import com.example.mytracker.data.ScheduleType
import com.example.mytracker.data.Store
import com.example.mytracker.data.Subtask
import com.example.mytracker.data.TargetType
import com.example.mytracker.data.Task
import com.example.mytracker.data.TrackingType
import com.example.mytracker.domain.D
import com.example.mytracker.domain.Reminders
import com.example.mytracker.domain.Stats
import com.example.mytracker.ui.CButton
import com.example.mytracker.ui.CCheckbox
import com.example.mytracker.ui.CChip
import com.example.mytracker.ui.CIcon
import com.example.mytracker.ui.Heatmap
import com.example.mytracker.ui.Ic
import com.example.mytracker.ui.T
import com.example.mytracker.ui.theme.C
import com.example.mytracker.ui.theme.CadenceType
import com.example.mytracker.ui.theme.Radius
import com.example.mytracker.ui.theme.Space
import com.example.mytracker.ui.theme.byKey
import java.util.UUID
import kotlin.math.roundToInt

private fun uid(p: String) = p + UUID.randomUUID().toString().take(12)

/* -------------------------------------------------------------- primitives */

@Composable
fun Field(label: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        T(label, CadenceType.label, C.textMuted)
        Spacer(Modifier.height(Space.s3))
        content()
        if (hint != null) {
            Spacer(Modifier.height(Space.s2))
            T(hint, CadenceType.caption, C.textMuted)
        }
    }
}

@Composable
fun CInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    multiline: Boolean = false,
    numeric: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = (if (big) CadenceType.titleM else CadenceType.body).copy(color = C.textPrimary),
        cursorBrush = SolidColor(C.textPrimary),
        singleLine = !multiline,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        ),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(C.surfaceAlt)
                    .then(if (multiline) Modifier.heightIn(min = 84.dp) else Modifier)
                    .padding(horizontal = Space.s5, vertical = if (big) Space.s5 else Space.s4),
            ) {
                if (value.isEmpty()) {
                    T(placeholder, if (big) CadenceType.titleM else CadenceType.body, C.textMuted)
                }
                inner()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CSheet(title: String, onClose: () -> Unit, footer: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = state,
        containerColor = C.surface,
        contentColor = C.textPrimary,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(vertical = Space.s4), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(Radius.full)).background(C.line))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.s6).padding(bottom = Space.s8)) {
            T(title, CadenceType.titleL)
            Spacer(Modifier.height(Space.s5))
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) { content() }
            if (footer != null) {
                Spacer(Modifier.height(Space.s6))
                footer()
            }
        }
    }
}

/* -------------------------------------------------------------- task sheet */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskSheet(store: Store, db: Db, task: Task?, onClose: () -> Unit) {
    var title by remember { mutableStateOf(task?.title ?: "") }
    var notes by remember { mutableStateOf(task?.notes ?: "") }
    var priority by remember { mutableStateOf(task?.priority ?: Priority.MEDIUM) }
    var dueDate by remember { mutableStateOf(task?.dueDate ?: D.today()) }
    var estimate by remember { mutableStateOf(task?.estimateMin?.toString() ?: "") }
    var categoryId by remember { mutableStateOf(task?.categoryId) }
    var goalId by remember { mutableStateOf(task?.goalId) }
    var repeat by remember { mutableStateOf(task?.repeat ?: RepeatKind.NONE) }
    var subtasks by remember { mutableStateOf(task?.subtasks ?: emptyList()) }
    var newSub by remember { mutableStateOf("") }
    var reminderEnabled by remember { mutableStateOf(task?.reminderEnabled ?: (task?.dueTime != null)) }
    var dueTime by remember { mutableStateOf(task?.dueTime ?: "09:00") }
    var reminderStyle by remember { mutableStateOf(task?.reminderStyle ?: db.settings.reminderStyle) }

    CSheet(
        title = if (task != null) "Edit task" else "New task",
        onClose = onClose,
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                if (task != null) {
                    CButton("Delete", {
                        val snapshot = task
                        store.deleteTask(task.id)
                        onClose()
                        store.showToast("Task deleted") { store.restoreTask(snapshot) }
                    }, variant = "danger", icon = Ic.TRASH)
                }
                CButton(
                    if (task != null) "Save changes" else "Add task",
                    {
                        if (title.isBlank()) return@CButton
                        if (task != null) {
                            store.updateTask(task.id) {
                                it.copy(
                                    title = title.trim(), notes = notes, priority = priority,
                                    dueDate = dueDate,
                                    dueTime = if (reminderEnabled) Reminders.parseTime(dueTime)?.toString() else null,
                                    reminderEnabled = reminderEnabled,
                                    reminderStyle = if (reminderEnabled) reminderStyle else null,
                                    estimateMin = estimate.toIntOrNull(),
                                    categoryId = categoryId, goalId = goalId, repeat = repeat,
                                    subtasks = subtasks,
                                )
                            }
                            store.showToast("Task updated")
                        } else {
                            store.addTask(
                                Task(
                                    id = "", title = title.trim(), notes = notes, priority = priority,
                                    dueDate = dueDate,
                                    dueTime = if (reminderEnabled) Reminders.parseTime(dueTime)?.toString() else null,
                                    reminderEnabled = reminderEnabled,
                                    reminderStyle = if (reminderEnabled) reminderStyle else null,
                                    estimateMin = estimate.toIntOrNull(),
                                    categoryId = categoryId, goalId = goalId, repeat = repeat,
                                    subtasks = subtasks,
                                ),
                            )
                            store.showToast("Task added")
                        }
                        onClose()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = title.isNotBlank(),
                )
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s6)) {
            CInput(title, { title = it }, "What needs doing?", big = true)

            Field("Priority") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    Priority.entries.forEach { p ->
                        CChip(p.label, priority == p, { priority = p })
                    }
                }
            }

            Field("When") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    listOf(
                        "Today" to D.today(),
                        "Tomorrow" to D.addDays(D.today(), 1),
                        "In 3 days" to D.addDays(D.today(), 3),
                        "Next week" to D.addDays(D.today(), 7),
                    ).forEach { (label, value) ->
                        CChip(label, dueDate == value, { dueDate = value })
                    }
                }
            }

            Field("Reminder") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .background(C.surfaceAlt)
                        .padding(horizontal = Space.s4, vertical = Space.s3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        T(
                            if (reminderEnabled) "Reminder ON" else "Reminder OFF",
                            CadenceType.titleM,
                            if (reminderEnabled) C.accent else C.textMuted,
                        )
                        T(
                            if (reminderEnabled) "Alert at specified time" else "No alert for this task",
                            CadenceType.caption,
                            C.textSecondary,
                        )
                    }
                    CToggle(
                        checked = reminderEnabled,
                        onChange = { reminderEnabled = it },
                        label = "Task reminder",
                    )
                }
            }

            if (reminderEnabled) {
                Field(
                    "Reminder Time & Method",
                    hint = if (reminderStyle == ReminderStyle.ALARM) "Rings with device alarm sound & vibration" else "Quiet notification in Android tray",
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.s3),
                    ) {
                        Box(Modifier.weight(1f)) {
                            CInput(dueTime, { dueTime = it.take(5) }, "09:00")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                            CChip(
                                "Notification",
                                reminderStyle == ReminderStyle.NOTIFICATION,
                                { reminderStyle = ReminderStyle.NOTIFICATION },
                            )
                            CChip(
                                "Alarm",
                                reminderStyle == ReminderStyle.ALARM,
                                { reminderStyle = ReminderStyle.ALARM },
                            )
                        }
                    }
                }
            }

            Field("Estimate", "Used to fit the task into the time you actually have.") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    listOf(10, 25, 45, 60, 90).forEach { m ->
                        CChip("${m}m", estimate == m.toString(), { estimate = m.toString() })
                    }
                    if (estimate.isNotEmpty()) CChip("Clear", false, { estimate = "" })
                }
            }

            Field("Repeats") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    listOf(
                        RepeatKind.NONE to "Once",
                        RepeatKind.DAILY to "Daily",
                        RepeatKind.WEEKDAYS to "Weekdays",
                        RepeatKind.MONTHLY to "Monthly",
                    ).forEach { (kind, label) -> CChip(label, repeat == kind, { repeat = kind }) }
                }
            }

            if (db.categories.isNotEmpty()) {
                Field("Category") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        db.categories.forEach { c ->
                            CChip(
                                c.name, categoryId == c.id,
                                { categoryId = if (categoryId == c.id) null else c.id },
                                dotColor = C.byKey(c.colorKey),
                            )
                        }
                    }
                }
            }

            val activeGoals = db.goals.filter { it.status == com.example.mytracker.data.GoalStatus.ACTIVE }
            if (activeGoals.isNotEmpty()) {
                Field("Goal", "Linking a task to a goal raises its priority score.") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        activeGoals.forEach { g ->
                            CChip(g.title, goalId == g.id, { goalId = if (goalId == g.id) null else g.id })
                        }
                    }
                }
            }

            Field("Steps") {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    subtasks.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CCheckbox(s.done, {
                                subtasks = subtasks.map { if (it.id == s.id) it.copy(done = !it.done) else it }
                            }, label = "Toggle ${s.title}")
                            Spacer(Modifier.width(Space.s4))
                            T(s.title, CadenceType.body, if (s.done) C.textMuted else C.textPrimary, Modifier.weight(1f))
                            Box(
                                Modifier.size(28.dp).clickable { subtasks = subtasks.filterNot { it.id == s.id } },
                                contentAlignment = Alignment.Center,
                            ) { CIcon(Ic.X, size = 14.dp, tint = C.textMuted) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CInput(newSub, { newSub = it }, "Break it into a first step…", Modifier.weight(1f))
                        Spacer(Modifier.width(Space.s3))
                        CButton("Add", {
                            if (newSub.isNotBlank()) {
                                subtasks = subtasks + Subtask(uid("s-"), newSub.trim(), false)
                                newSub = ""
                            }
                        }, variant = "secondary", small = true)
                    }
                }
            }

            Field("Notes") {
                CInput(notes, { notes = it }, "Anything worth remembering", multiline = true)
            }
        }
    }
}

/* ----------------------------------------------------------- failure sheet */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FailureSheet(store: Store, task: Task, onClose: () -> Unit) {
    var code by remember { mutableStateOf<FailureCode?>(null) }
    var note by remember { mutableStateOf("") }

    CSheet("What got in the way?", onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s5)) {
            T(
                "“${task.title}” didn't get done. Noting why builds a record you can actually learn from later — " +
                    "but it's optional, and nothing here counts against you.",
                CadenceType.body, C.textSecondary,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                FailureCode.entries.forEach { c ->
                    CChip(c.label, code == c, { code = c })
                    Spacer(Modifier.height(Space.s3))
                }
            }

            CInput(note, { note = it }, "Anything else worth remembering (optional)", multiline = true)

            CButton(
                "Record it",
                { code?.let { store.recordFailure(task.id, it, note); onClose(); store.showToast("Recorded") } },
                modifier = Modifier.fillMaxWidth(),
                enabled = code != null,
            )
            CButton(
                if (code != null) "Move to tomorrow and record" else "Move to tomorrow",
                {
                    code?.let { store.recordFailure(task.id, it, note) }
                    store.postpone(task.id, D.addDays(D.today(), 1))
                    onClose()
                    store.showToast("Moved to tomorrow")
                },
                modifier = Modifier.fillMaxWidth(),
                variant = "secondary",
            )
            CButton("Skip", onClose, modifier = Modifier.fillMaxWidth(), variant = "ghost")
        }
    }
}

/* ---------------------------------------------------------- postpone sheet */

@Composable
fun PostponeSheet(store: Store, task: Task, onClose: () -> Unit, onRecordWhy: () -> Unit) {
    CSheet("Move it to", onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
            T(
                "Moving keeps the task's history — it will remember how many times it has shifted.",
                CadenceType.body, C.textSecondary,
            )
            Spacer(Modifier.height(Space.s3))
            listOf("Tomorrow" to 1L, "In two days" to 2L, "Next week" to 7L).forEach { (label, days) ->
                CButton(label, {
                    store.postpone(task.id, D.addDays(D.today(), days))
                    store.showToast("Moved to ${label.lowercase()}")
                    onClose()
                }, modifier = Modifier.fillMaxWidth(), variant = "secondary")
            }
            CButton("Record why instead", { onClose(); onRecordWhy() }, modifier = Modifier.fillMaxWidth(), variant = "ghost")
        }
    }
}

/* ------------------------------------------------------------- habit sheet */

@Composable
fun HabitSheet(store: Store, habit: Habit?, onClose: () -> Unit) {
    var name by remember { mutableStateOf(habit?.name ?: "") }
    var icon by remember { mutableStateOf(habit?.icon ?: "target") }
    var colorKey by remember { mutableStateOf(habit?.colorKey ?: "c1") }
    var scheduleType by remember { mutableStateOf(habit?.schedule?.type ?: ScheduleType.DAILY) }
    var days by remember { mutableStateOf(habit?.schedule?.days?.ifEmpty { listOf(1, 2, 3, 4, 5) } ?: listOf(1, 2, 3, 4, 5)) }
    var timesPerWeek by remember { mutableStateOf(habit?.schedule?.timesPerWeek ?: 4) }
    var tracking by remember { mutableStateOf(habit?.tracking ?: TrackingType.BINARY) }
    var target by remember { mutableStateOf(habit?.target?.toString() ?: "1") }
    var unit by remember { mutableStateOf(habit?.unit ?: "") }
    var reminderEnabled by remember { mutableStateOf(habit?.reminderEnabled ?: (habit?.reminderTime != null)) }
    var remindAt by remember { mutableStateOf(habit?.reminderTime ?: "08:00") }
    var reminderStyle by remember { mutableStateOf(habit?.reminderStyle ?: store.db.settings.reminderStyle) }

    CSheet(
        title = if (habit != null) "Edit habit" else "New habit",
        onClose = onClose,
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                if (habit != null) {
                    CButton("Delete", {
                        store.deleteHabit(habit.id); onClose(); store.showToast("Habit and its logs deleted")
                    }, variant = "danger", icon = Ic.TRASH)
                }
                CButton(
                    if (habit != null) "Save changes" else "Create habit",
                    {
                        if (name.isBlank()) return@CButton
                        val schedule = HabitSchedule(scheduleType, days, timesPerWeek)
                        val tgt = if (tracking == TrackingType.BINARY) 1 else (target.toIntOrNull() ?: 1).coerceAtLeast(1)
                        if (habit != null) {
                            store.updateHabit(habit.id) {
                                it.copy(
                                    name = name.trim(), icon = icon, colorKey = colorKey, schedule = schedule,
                                    tracking = tracking, target = tgt,
                                    unit = if (tracking == TrackingType.BINARY) "" else unit,
                                    reminderEnabled = reminderEnabled,
                                    reminderTime = if (reminderEnabled) Reminders.parseTime(remindAt)?.toString() else null,
                                    reminderStyle = if (reminderEnabled) reminderStyle else null,
                                )
                            }
                            store.showToast("Habit updated")
                        } else {
                            store.addHabit(
                                Habit(
                                    id = "", name = name.trim(), icon = icon, colorKey = colorKey,
                                    schedule = schedule, tracking = tracking, target = tgt,
                                    unit = if (tracking == TrackingType.BINARY) "" else unit,
                                    reminderEnabled = reminderEnabled,
                                    reminderTime = if (reminderEnabled) Reminders.parseTime(remindAt)?.toString() else null,
                                    reminderStyle = if (reminderEnabled) reminderStyle else null,
                                ),
                            )
                            store.showToast("Habit created")
                        }
                        onClose()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank(),
                )
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s6)) {
            CInput(name, { name = it }, "What are you building?", big = true)

            Field("Reminder") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .background(C.surfaceAlt)
                        .padding(horizontal = Space.s4, vertical = Space.s3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        T(
                            if (reminderEnabled) "Reminder ON" else "Reminder OFF",
                            CadenceType.titleM,
                            if (reminderEnabled) C.accent else C.textMuted,
                        )
                        T(
                            if (reminderEnabled) "Alert on scheduled days" else "No reminder for this habit",
                            CadenceType.caption,
                            C.textSecondary,
                        )
                    }
                    CToggle(
                        checked = reminderEnabled,
                        onChange = { reminderEnabled = it },
                        label = "Habit reminder",
                    )
                }
            }

            if (reminderEnabled) {
                Field(
                    "Reminder Time & Method",
                    hint = if (reminderStyle == ReminderStyle.ALARM) "Rings with device alarm sound & vibration" else "Quiet notification in Android tray",
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.s3),
                    ) {
                        Box(Modifier.weight(1f)) {
                            CInput(remindAt, { remindAt = it.take(5) }, "08:00")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                            CChip(
                                "Notification",
                                reminderStyle == ReminderStyle.NOTIFICATION,
                                { reminderStyle = ReminderStyle.NOTIFICATION },
                            )
                            CChip(
                                "Alarm",
                                reminderStyle == ReminderStyle.ALARM,
                                { reminderStyle = ReminderStyle.ALARM },
                            )
                        }
                    }
                }
            }

            Field("Look") {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        Ic.habitKeys.forEach { key ->
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(Radius.full))
                                    .background(if (icon == key) C.byKey(colorKey) else C.surfaceAlt)
                                    .clickable { icon = key },
                                contentAlignment = Alignment.Center,
                            ) {
                                CIcon(Ic.byKey(key), size = 16.dp, tint = if (icon == key) C.surface else C.textSecondary)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        listOf("c1", "c2", "c3", "c4", "c5", "c6").forEach { k ->
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(Radius.full))
                                    .background(C.byKey(k))
                                    .then(
                                        if (colorKey == k) Modifier.border(2.dp, C.textPrimary, RoundedCornerShape(Radius.full))
                                        else Modifier,
                                    )
                                    .clickable { colorKey = k },
                            )
                        }
                    }
                }
            }

            Field("How often") {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        listOf(
                            ScheduleType.DAILY to "Every day",
                            ScheduleType.WEEKDAYS to "Certain days",
                            ScheduleType.TIMES_PER_WEEK to "X times a week",
                        ).forEach { (type, label) -> CChip(label, scheduleType == type, { scheduleType = type }) }
                    }
                    if (scheduleType == ScheduleType.WEEKDAYS) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                            (1..7).forEach { d ->
                                val on = d in days
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(Radius.sm))
                                        .background(if (on) C.accentSubtle else C.surfaceAlt)
                                        .clickable { days = if (on) days - d else days + d },
                                    contentAlignment = Alignment.Center,
                                ) { T(D.dowLetter(d), CadenceType.label, if (on) C.accent else C.textMuted) }
                            }
                        }
                    }
                    if (scheduleType == ScheduleType.TIMES_PER_WEEK) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                            (1..7).forEach { n -> CChip("${n}x", timesPerWeek == n, { timesPerWeek = n }) }
                        }
                    }
                }
            }

            Field(
                "How you'll track it",
                when (tracking) {
                    TrackingType.BINARY -> "A simple yes or no."
                    TrackingType.COUNT -> "A number you reach, like 8 glasses."
                    TrackingType.DURATION -> "Time spent, like 20 minutes of reading."
                },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        listOf(
                            TrackingType.BINARY to "Did it",
                            TrackingType.COUNT to "Count",
                            TrackingType.DURATION to "Minutes",
                        ).forEach { (type, label) -> CChip(label, tracking == type, { tracking = type }) }
                    }
                    if (tracking != TrackingType.BINARY) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                            CInput(target, { target = it.filter { ch -> ch.isDigit() } }, "Target", Modifier.weight(1f), numeric = true)
                            CInput(unit, { unit = it }, if (tracking == TrackingType.DURATION) "min" else "glasses", Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------ habit detail sheet */

@Composable
fun HabitDetailSheet(store: Store, db: Db, habit: Habit, onClose: () -> Unit, onEdit: () -> Unit) {
    val t = D.today()
    val streak = Stats.currentStreak(db, habit, t)
    val best = Stats.bestStreak(db, habit, t)
    val unit = Stats.streakUnit(habit)
    val r30 = Stats.completionRate(db, habit, 30, t)
    val r7 = Stats.completionRate(db, habit, 7, t)
    val missed = Stats.missedDays(db, habit, 30, t)

    val cells = remember(db, habit.id) {
        D.range(D.addDays(t, -125), t).map { date ->
            val log = Stats.logFor(db, habit.id, date)
            val level = if (log == null) 0 else {
                val ratio = if (habit.tracking == TrackingType.BINARY) 1f
                else (log.value.toFloat() / habit.target).coerceAtMost(1f)
                when {
                    ratio >= 1f -> 5
                    ratio >= 0.75f -> 4
                    ratio >= 0.5f -> 3
                    ratio >= 0.25f -> 2
                    else -> 1
                }
            }
            date to level
        }
    }

    CSheet(
        title = habit.name,
        onClose = onClose,
        footer = { CButton("Edit habit", onEdit, modifier = Modifier.fillMaxWidth(), variant = "secondary", icon = Ic.EDIT) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s7)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                Metric(if (unit == "w") "WEEKS" else "STREAK", "$streak", Modifier.weight(1f))
                Metric("BEST", "$best", Modifier.weight(1f))
                Metric("30 DAYS", "$r30%", Modifier.weight(1f))
                Metric("7 DAYS", "$r7%", Modifier.weight(1f))
            }

            if (unit == "w") {
                T(
                    "Counted in weeks that met the ${habit.schedule.timesPerWeek}x quota — a weekly habit has no missed day, only an unmet week.",
                    CadenceType.caption, C.textMuted,
                )
            }

            Column {
                T("LAST 18 WEEKS", CadenceType.eyebrow, C.textMuted)
                Spacer(Modifier.height(Space.s4))
                Heatmap(cells.takeLast(126), mondayFirst = db.settings.weekStartsMonday, onPick = { date ->
                    if (date <= t) {
                        store.logHabit(habit.id, date)
                        store.showToast("Logged ${D.formatDay(date)}")
                    }
                })
                Spacer(Modifier.height(Space.s3))
                T(
                    "Darker means closer to target. Tap any past day to correct the record.",
                    CadenceType.caption, C.textMuted,
                )
            }

            if (missed.isNotEmpty()) {
                Column {
                    T("MISSED IN THE LAST 30 DAYS · ${missed.size}", CadenceType.eyebrow, C.textMuted)
                    Spacer(Modifier.height(Space.s4))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        missed.takeLast(10).forEach { d ->
                            CChip(D.formatDay(d), false, {
                                store.logHabit(habit.id, d)
                                store.showToast("Logged ${D.formatDay(d)}")
                            })
                        }
                    }
                    Spacer(Modifier.height(Space.s3))
                    T(
                        "Backfilling is allowed — streaks are recomputed from the logs, so the record stays honest either way.",
                        CadenceType.caption, C.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        T(label, CadenceType.caption, C.textMuted)
        T(value, CadenceType.metricM)
    }
}

/* -------------------------------------------------------------- goal sheet */

@Composable
fun GoalSheet(store: Store, db: Db, goal: Goal?, defaultHorizon: Horizon, onClose: () -> Unit) {
    var title by remember { mutableStateOf(goal?.title ?: "") }
    var why by remember { mutableStateOf(goal?.why ?: "") }
    var horizon by remember { mutableStateOf(goal?.horizon ?: defaultHorizon) }
    var targetType by remember { mutableStateOf(goal?.targetType ?: TargetType.MILESTONES) }
    var targetValue by remember { mutableStateOf(goal?.targetValue?.takeIf { it > 0 }?.toString() ?: "10") }
    var unit by remember { mutableStateOf(goal?.unit ?: "") }
    var habitId by remember { mutableStateOf(goal?.derived?.habitId) }
    var categoryId by remember { mutableStateOf(goal?.categoryId) }
    var milestones by remember { mutableStateOf(goal?.milestones ?: emptyList()) }
    var newMs by remember { mutableStateOf("") }

    CSheet(
        title = if (goal != null) "Edit goal" else "New goal",
        onClose = onClose,
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                if (goal != null) {
                    CButton("Delete", { store.deleteGoal(goal.id); onClose(); store.showToast("Goal deleted") },
                        variant = "danger", icon = Ic.TRASH)
                }
                CButton(
                    if (goal != null) "Save changes" else "Create goal",
                    {
                        if (title.isBlank() || why.isBlank()) return@CButton
                        val t = D.today()
                        val payload = { g: Goal ->
                            g.copy(
                                title = title.trim(), why = why.trim(), horizon = horizon,
                                categoryId = categoryId, targetType = targetType,
                                targetValue = if (targetType == TargetType.NUMERIC || targetType == TargetType.DERIVED)
                                    (targetValue.toIntOrNull() ?: 1) else 0,
                                unit = unit,
                                derived = if (targetType == TargetType.DERIVED) {
                                    if (habitId != null) DerivedSource("habit_days", habitId = habitId)
                                    else DerivedSource("task_count", categoryId = categoryId)
                                } else null,
                                periodStart = if (horizon == Horizon.MONTH) D.startOfMonth(t) else D.startOfYear(t),
                                periodEnd = if (horizon == Horizon.MONTH) D.endOfMonth(t) else D.endOfYear(t),
                                milestones = milestones,
                            )
                        }
                        if (goal != null) {
                            store.updateGoal(goal.id, payload)
                            store.showToast("Goal updated")
                        } else {
                            store.addGoal(payload(Goal(id = "", title = "")))
                            store.showToast("Goal created")
                        }
                        onClose()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = title.isNotBlank() && why.isNotBlank(),
                )
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s6)) {
            CInput(title, { title = it }, "What are you trying to achieve?", big = true)

            Field(
                "Why this matters",
                "Required on purpose. A goal without a stated reason is the one you quietly drop.",
            ) {
                CInput(why, { why = it }, "Be honest — this is the line you'll reread in three weeks.", multiline = true)
            }

            Field("Horizon") {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    CChip("This month", horizon == Horizon.MONTH, { horizon = Horizon.MONTH })
                    CChip("This year", horizon == Horizon.YEAR, { horizon = Horizon.YEAR })
                }
            }

            Field(
                "How progress is measured",
                when (targetType) {
                    TargetType.MILESTONES -> "Progress comes from the steps you tick off."
                    TargetType.NUMERIC -> "You update the count yourself."
                    TargetType.DERIVED -> "Counted from your habit logs or completed tasks — you never update it by hand."
                    TargetType.BINARY -> "A single thing that either happens or does not."
                },
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    listOf(
                        TargetType.MILESTONES to "Milestones",
                        TargetType.NUMERIC to "A number",
                        TargetType.DERIVED to "From a habit",
                        TargetType.BINARY to "Done or not",
                    ).forEach { (type, label) -> CChip(label, targetType == type, { targetType = type }) }
                }
            }

            if (targetType == TargetType.NUMERIC || targetType == TargetType.DERIVED) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                    CInput(targetValue, { targetValue = it.filter { ch -> ch.isDigit() } }, "Target", Modifier.weight(1f), numeric = true)
                    CInput(unit, { unit = it }, "books / days / tasks", Modifier.weight(1f))
                }
            }

            if (targetType == TargetType.DERIVED && db.habits.isNotEmpty()) {
                Field("Counted from", "Leave the habit unset to count completed tasks instead.") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        db.habits.forEach { h ->
                            CChip(h.name, habitId == h.id, { habitId = if (habitId == h.id) null else h.id })
                        }
                    }
                }
            }

            if (targetType == TargetType.MILESTONES) {
                Field("Milestones") {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                        milestones.forEach { m ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CCheckbox(m.done, {
                                    milestones = milestones.map { if (it.id == m.id) it.copy(done = !it.done) else it }
                                }, label = "Toggle ${m.title}")
                                Spacer(Modifier.width(Space.s4))
                                T(m.title, CadenceType.body, if (m.done) C.textMuted else C.textPrimary, Modifier.weight(1f))
                                Box(
                                    Modifier.size(28.dp).clickable { milestones = milestones.filterNot { it.id == m.id } },
                                    contentAlignment = Alignment.Center,
                                ) { CIcon(Ic.X, size = 14.dp, tint = C.textMuted) }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CInput(newMs, { newMs = it }, "Add a step…", Modifier.weight(1f))
                            Spacer(Modifier.width(Space.s3))
                            CButton("Add", {
                                if (newMs.isNotBlank()) {
                                    milestones = milestones + Milestone(uid("m-"), newMs.trim(), null, false)
                                    newMs = ""
                                }
                            }, variant = "secondary", small = true)
                        }
                    }
                }
            }

            Field("Category") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    db.categories.forEach { c ->
                        CChip(
                            c.name, categoryId == c.id,
                            { categoryId = if (categoryId == c.id) null else c.id },
                            dotColor = C.byKey(c.colorKey),
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------- goal detail sheet */

@Composable
fun GoalDetailSheet(store: Store, db: Db, goal: Goal, onClose: () -> Unit, onEdit: () -> Unit) {
    val p = Stats.goalProgress(db, goal)
    val linked = db.tasks.filter { it.goalId == goal.id }
    val openLinked = linked.count { it.isOpen }

    CSheet(
        title = goal.title,
        onClose = onClose,
        footer = { CButton("Edit goal", onEdit, modifier = Modifier.fillMaxWidth(), variant = "secondary", icon = Ic.EDIT) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s7)) {
            if (goal.why.isNotBlank()) {
                Column {
                    T("WHY THIS MATTERS", CadenceType.eyebrow, C.textMuted)
                    Spacer(Modifier.height(Space.s2))
                    T(goal.why, CadenceType.body, C.textSecondary)
                }
            }

            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    T("${(p.pct * 100).roundToInt()}%", CadenceType.metricL, modifier = Modifier.weight(1f))
                    T(p.label, CadenceType.caption, C.textMuted)
                }
                Spacer(Modifier.height(Space.s3))
                com.example.mytracker.ui.CBar(p.pct)
                Spacer(Modifier.height(Space.s3))
                T("${D.formatDay(goal.periodStart)} — ${D.formatDay(goal.periodEnd)}", CadenceType.caption, C.textMuted)
            }

            if (goal.targetType == TargetType.NUMERIC) {
                Column {
                    T("UPDATE PROGRESS", CadenceType.eyebrow, C.textMuted)
                    Spacer(Modifier.height(Space.s4))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CButton("−", {
                            store.updateGoal(goal.id) { it.copy(manualValue = (it.manualValue - 1).coerceAtLeast(0)) }
                        }, variant = "secondary")
                        T(
                            "${goal.manualValue} / ${goal.targetValue} ${goal.unit}",
                            CadenceType.metricM.merge(CadenceType.centered),
                            modifier = Modifier.weight(1f),
                        )
                        CButton("+", {
                            store.updateGoal(goal.id) { it.copy(manualValue = it.manualValue + 1) }
                        }, variant = "secondary")
                    }
                }
            }

            if (goal.targetType == TargetType.DERIVED) {
                T(
                    "This one counts itself from your " +
                        (if (goal.derived?.kind == "habit_days") "habit logs" else "completed tasks") +
                        " — there is nothing to update by hand.",
                    CadenceType.caption, C.textMuted,
                )
            }

            if (goal.milestones.isNotEmpty()) {
                Column {
                    T("MILESTONES", CadenceType.eyebrow, C.textMuted)
                    Spacer(Modifier.height(Space.s4))
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                        goal.milestones.forEach { m ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CCheckbox(m.done, {
                                    store.toggleMilestone(goal.id, m.id)
                                    if (!m.done) store.showToast("Milestone done")
                                }, label = "Toggle ${m.title}")
                                Spacer(Modifier.width(Space.s4))
                                Column(Modifier.weight(1f)) {
                                    T(m.title, CadenceType.body, if (m.done) C.textMuted else C.textPrimary)
                                    m.dueDate?.let { T(D.formatDay(it), CadenceType.caption, C.textMuted) }
                                }
                            }
                        }
                    }
                }
            }

            Column {
                T("LINKED TASKS · $openLinked OPEN OF ${linked.size}", CadenceType.eyebrow, C.textMuted)
                Spacer(Modifier.height(Space.s4))
                if (linked.isEmpty()) {
                    T(
                        "No tasks point at this goal yet. Linking them raises their priority score and makes the goal move on its own.",
                        CadenceType.caption, C.textMuted,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                        linked.take(6).forEach { task ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CIcon(
                                    if (task.isDone) Ic.CHECK else Ic.CHEVRON_RIGHT,
                                    size = 14.dp,
                                    tint = if (task.isDone) C.success else C.textMuted,
                                )
                                Spacer(Modifier.width(Space.s3))
                                T(task.title, CadenceType.body, if (task.isDone) C.textMuted else C.textPrimary)
                            }
                        }
                    }
                }
            }

            CButton(
                if (goal.status == com.example.mytracker.data.GoalStatus.COMPLETED) "Reopen" else "Mark complete",
                {
                    val completed = goal.status == com.example.mytracker.data.GoalStatus.COMPLETED
                    store.updateGoal(goal.id) {
                        it.copy(
                            status = if (completed) com.example.mytracker.data.GoalStatus.ACTIVE
                            else com.example.mytracker.data.GoalStatus.COMPLETED,
                            completedAt = if (completed) null else System.currentTimeMillis(),
                        )
                    }
                    store.showToast(if (completed) "Reopened" else "Goal marked complete")
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(),
                variant = "secondary",
            )
        }
    }
}

/* ------------------------------------------------------------ focus setup */

@Composable
fun FocusSetupSheet(db: Db, presetTaskId: String?, onClose: () -> Unit, onStart: (Int, String?) -> Unit) {
    var minutes by remember { mutableStateOf(45) }
    var taskId by remember { mutableStateOf(presetTaskId) }
    val tasks = remember(db) { focusableTasks(db) }

    CSheet(
        title = "Focus session",
        onClose = onClose,
        footer = {
            CButton("Start $minutes minutes", { onStart(minutes, taskId) }, modifier = Modifier.fillMaxWidth(), icon = Ic.PLAY)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s6)) {
            Field("How long") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    listOf(5, 25, 45, 60, 90).forEach { m ->
                        CChip(if (m == 5) "Just 5m" else "${m}m", minutes == m, { minutes = m })
                    }
                }
            }
            if (minutes == 5) {
                T(
                    "Five minutes is a legitimate session. Starting is the part that's hard.",
                    CadenceType.caption, C.textMuted,
                )
            }

            Field("On what") {
                if (tasks.isEmpty()) {
                    T("No open tasks — you can run an unbound session.", CadenceType.caption, C.textMuted)
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        tasks.take(8).forEach { t ->
                            CChip(
                                if (t.title.length > 26) t.title.take(26) + "…" else t.title,
                                taskId == t.id,
                                { taskId = if (taskId == t.id) null else t.id },
                            )
                        }
                    }
                }
            }

            T(
                "This build does not block other apps — Android has no API that genuinely can. " +
                    "Leaving the app is recorded as an interruption instead, which is the honest version of the same idea.",
                CadenceType.caption, C.textMuted,
            )
        }
    }
}
