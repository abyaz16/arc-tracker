package com.example.mytracker

import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.mytracker.R
import com.example.mytracker.ui.SplashScreen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.example.mytracker.data.FocusSession
import com.example.mytracker.data.Goal
import com.example.mytracker.data.Habit
import com.example.mytracker.data.Horizon
import com.example.mytracker.data.Store
import com.example.mytracker.data.Task
import com.example.mytracker.data.ThemeMode
import com.example.mytracker.screens.FailureSheet
import com.example.mytracker.screens.FocusScreen
import com.example.mytracker.screens.FocusSetupSheet
import com.example.mytracker.screens.GoalDetailSheet
import com.example.mytracker.screens.GoalSheet
import com.example.mytracker.screens.GoalsScreen
import com.example.mytracker.screens.HabitDetailSheet
import com.example.mytracker.screens.HabitSheet
import com.example.mytracker.screens.HabitsScreen
import com.example.mytracker.screens.HomeScreen
import com.example.mytracker.screens.InsightsScreen
import com.example.mytracker.screens.SettingsScreen
import com.example.mytracker.screens.PostponeSheet
import com.example.mytracker.screens.TaskSheet
import com.example.mytracker.screens.TasksScreen
import com.example.mytracker.domain.D
import com.example.mytracker.ui.CIcon
import com.example.mytracker.ui.Ic
import com.example.mytracker.ui.IconBtn
import com.example.mytracker.ui.NavItem
import com.example.mytracker.ui.PillNav
import com.example.mytracker.ui.T
import com.example.mytracker.ui.TopBar
import com.example.mytracker.ui.theme.C
import com.example.mytracker.ui.theme.CadenceType
import com.example.mytracker.ui.theme.Radius
import com.example.mytracker.ui.theme.Space

private val TABS = listOf(
    NavItem("home", Ic.HOME, "Home"),
    NavItem("tasks", Ic.CHECK_SQUARE, "Tasks"),
    NavItem("habits", Ic.REPEAT, "Habits"),
    NavItem("goals", Ic.TARGET, "Goals"),
    NavItem("insights", Ic.SPARK, "Insights"),
)

/** Which sheet, if any, is currently open. One at a time, by construction. */
private sealed interface SheetState {
    data class TaskEdit(val task: Task?) : SheetState
    data class Failure(val task: Task) : SheetState
    data class Postpone(val task: Task) : SheetState
    data class HabitEdit(val habit: Habit?) : SheetState
    data class HabitDetail(val habit: Habit) : SheetState
    data class GoalEdit(val goal: Goal?, val horizon: Horizon) : SheetState
    data class GoalDetail(val goal: Goal) : SheetState
    data class FocusSetup(val taskId: String?) : SheetState
}

@Composable
fun CadenceApp(store: Store) {
    val db = store.db
    var tab by remember { mutableStateOf("home") }
    var settingsOpen by remember { mutableStateOf(false) }
    var showSplash by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf<SheetState?>(null) }
    var running by remember { mutableStateOf<Pair<Int, String?>?>(null) }
    val overdue = db.tasks.count { it.isOpen && it.dueDate != null && it.dueDate < D.today() }

    // A running focus session takes over the whole screen — no tab bar, no chrome.
    val session = running
    if (session != null) {
        FocusScreen(
            minutes = session.first,
            taskId = session.second,
            db = db,
            onCancel = { running = null; store.showToast("Session discarded") },
            onDone = { r ->
                store.saveSession(
                    FocusSession(
                        id = "",
                        taskId = session.second,
                        plannedMin = session.first,
                        actualMin = r.actualMin,
                        startedAt = System.currentTimeMillis() - r.actualMin * 60_000L,
                        endedAt = System.currentTimeMillis(),
                        completed = r.completed,
                        interruptions = r.interruptions,
                        rating = r.rating,
                    ),
                )
                session.second?.let { id ->
                    store.updateTask(id) { it.copy(actualMin = (it.actualMin ?: 0) + r.actualMin) }
                }
                running = null
                store.showToast("${r.actualMin} minutes recorded")
            },
        )
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(C.canvas)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(Modifier.fillMaxSize()) {
            // the same chrome on every screen, so each screen only owns its title
            TopBar(
                hi = if (settingsOpen) "Settings"
                else D.greetingLine(db.settings.name, db.settings.wakeTime, db.settings.sleepTime),
                sub = if (settingsOpen) "Your name, theme and data"
                else if (db.settings.name.isBlank()) "Tap your avatar to add your name"
                else D.formatLongDay(D.today()),
                initial = db.settings.name,
                onAvatar = { settingsOpen = true },
                actions = {
                    IconBtn(
                        icon = when (db.settings.theme) {
                            ThemeMode.LIGHT -> Ic.SUN
                            ThemeMode.DARK -> Ic.MOON
                            ThemeMode.SYSTEM -> Ic.CONTRAST
                        },
                        label = "Theme: ${db.settings.theme.label}. Tap to change.",
                        onClick = { store.showToast("Theme: ${store.cycleTheme().label}") },
                    )
                    IconBtn(Ic.SEARCH, "Search", { tab = "tasks" })
                    IconBtn(
                        Ic.BELL,
                        if (overdue > 0) "$overdue overdue" else "Nothing overdue",
                        { tab = "tasks" },
                        dot = overdue > 0,
                    )
                },
            )
            if (settingsOpen) {
                SettingsScreen(store = store, db = db, onBack = { settingsOpen = false })
            } else {
            AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 24 })
                    .togetherWith(fadeOut(tween(140)))
            },
            label = "tab",
        ) { current ->
            when (current) {
                "home" -> HomeScreen(
                    store = store,
                    db = db,
                    onOpenTask = { sheet = SheetState.TaskEdit(it) },
                    onStartFocus = { sheet = SheetState.FocusSetup(it) },
                    onGoto = { tab = it },
                    onQuickAdd = { sheet = SheetState.TaskEdit(null) },
                    onFail = { sheet = SheetState.Failure(it) },
                    onPostpone = { sheet = SheetState.Postpone(it) },
                )

                "tasks" -> TasksScreen(
                    store = store,
                    db = db,
                    onOpenTask = { sheet = SheetState.TaskEdit(it) },
                    onNew = { sheet = SheetState.TaskEdit(null) },
                    onFail = { sheet = SheetState.Failure(it) },
                    onPostpone = { sheet = SheetState.Postpone(it) },
                )

                "habits" -> HabitsScreen(
                    store = store,
                    db = db,
                    onNew = { sheet = SheetState.HabitEdit(null) },
                    onOpen = { sheet = SheetState.HabitDetail(it) },
                )

                "goals" -> GoalsScreen(
                    db = db,
                    onNew = { sheet = SheetState.GoalEdit(null, it) },
                    onOpen = { sheet = SheetState.GoalDetail(it) },
                )

                else -> InsightsScreen(store = store, db = db)
            }
            }
            }
        }

        // focus is launched from a decision, never from a tab of its own
        if (tab != "insights" && !settingsOpen) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(end = Space.s6, bottom = 104.dp)
                    .size(56.dp)
                    .shadow(8.dp, RoundedCornerShape(Radius.full))
                    .clip(RoundedCornerShape(Radius.full))
                    .background(C.accent)
                    .clickable { sheet = SheetState.FocusSetup(null) },
                contentAlignment = Alignment.Center,
            ) { CIcon(Ic.PLAY, size = 22.dp, tint = C.onAccent, filled = true) }
        }

        // the floating pill: one circle per destination, active filled in butter
        PillNav(
            items = TABS,
            selected = tab,
            onSelect = { tab = it; settingsOpen = false },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = Space.s5)
                .shadow(6.dp, RoundedCornerShape(Radius.full)),
        )

        // toast
        store.toast?.let { t ->
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Space.s5)
                    .padding(bottom = 104.dp)
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(Radius.full))
                    .clip(RoundedCornerShape(Radius.full))
                    .background(C.textPrimary)
                    .padding(horizontal = Space.s6, vertical = Space.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T(t.message, CadenceType.label, C.canvas, modifier = Modifier.weight(1f))
                if (t.undo != null) {
                    Spacer(Modifier.width(Space.s4))
                    T(
                        "Undo", CadenceType.label, C.accentOnInverse,
                        Modifier.clickable { t.undo.invoke(); store.clearToast() },
                    )
                }
            }
        }
    }

    // ---- sheets ----
    when (val s = sheet) {
        null -> Unit
        is SheetState.TaskEdit -> TaskSheet(store, db, s.task) { sheet = null }
        is SheetState.Failure -> FailureSheet(store, s.task) { sheet = null }
        is SheetState.Postpone -> PostponeSheet(
            store, s.task,
            onClose = { sheet = null },
            onRecordWhy = { sheet = SheetState.Failure(s.task) },
        )

        is SheetState.HabitEdit -> HabitSheet(store, s.habit) { sheet = null }
        is SheetState.HabitDetail -> HabitDetailSheet(
            store, db, s.habit,
            onClose = { sheet = null },
            onEdit = { sheet = SheetState.HabitEdit(s.habit) },
        )

        is SheetState.GoalEdit -> GoalSheet(store, db, s.goal, s.horizon) { sheet = null }
        is SheetState.GoalDetail -> GoalDetailSheet(
            store, db, s.goal,
            onClose = { sheet = null },
            onEdit = { sheet = SheetState.GoalEdit(s.goal, s.goal.horizon) },
        )

                is SheetState.FocusSetup -> FocusSetupSheet(
            db = db,
            presetTaskId = s.taskId,
            onClose = { sheet = null },
            onStart = { minutes, taskId -> sheet = null; running = minutes to taskId },
        )
    }

    if (showSplash) {
        SplashScreen(onComplete = { showSplash = false })
    }
}
