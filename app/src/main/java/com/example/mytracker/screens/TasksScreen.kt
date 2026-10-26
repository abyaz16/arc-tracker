package com.example.mytracker.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.example.mytracker.data.Db
import com.example.mytracker.data.Priority
import com.example.mytracker.data.Store
import com.example.mytracker.data.Task
import com.example.mytracker.data.TaskStatus
import com.example.mytracker.domain.D
import com.example.mytracker.domain.Stats
import com.example.mytracker.ui.CButton
import com.example.mytracker.ui.CCard
import com.example.mytracker.ui.CardTone
import com.example.mytracker.ui.CtaBar
import com.example.mytracker.ui.Eyebrow
import com.example.mytracker.ui.MiniRow
import com.example.mytracker.ui.MiniStat
import com.example.mytracker.ui.PageHead
import com.example.mytracker.ui.StripDay
import com.example.mytracker.ui.WeekStrip
import com.example.mytracker.ui.EmptyState
import com.example.mytracker.ui.Ic
import com.example.mytracker.ui.IconBtn
import com.example.mytracker.ui.Segmented
import com.example.mytracker.ui.T
import com.example.mytracker.ui.TaskRow
import com.example.mytracker.ui.theme.C
import com.example.mytracker.ui.theme.CadenceType
import com.example.mytracker.ui.theme.Radius
import com.example.mytracker.ui.theme.Space

@Composable
fun TasksScreen(
    store: Store,
    db: Db,
    onOpenTask: (Task) -> Unit,
    onNew: () -> Unit,
    onFail: (Task) -> Unit,
    onPostpone: (Task) -> Unit,
) {
    var view by remember { mutableStateOf("day") }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val t = D.today()
    var day by remember { mutableStateOf(t) }

    val open = db.tasks.filter { it.isOpen }
    val today = open.filter { it.dueDate == day }

    // the seven days of the week containing the selected day
    val strip = remember(db, day) {
        val start = Stats.weekStart(db, day)
        (0..6).map { i ->
            val date = D.addDays(start, i.toLong())
            StripDay(
                date = date,
                dow = D.dowLetter(D.dow(date)),
                num = date.substring(8).toInt(),
                marked = db.tasks.any { it.dueDate == date },
            )
        }
    }

    // the day's remaining load, split by priority — the reference's calorie bar
    val estimate = today.sumOf { it.estimateMin ?: 0 }
    val topTwo = today.sortedByDescending { Stats.priorityScore(it, t).score }.take(2)
    val upcoming = open.filter { it.dueDate != null && it.dueDate > t }
    val overdue = open.filter { it.dueDate != null && it.dueDate < t }
    val finished = db.tasks
        .filter { it.isDone || it.status == TaskStatus.FAILED }
        .sortedByDescending { it.completedAt ?: 0L }
        .take(60)

    val visible = when {
        query.isNotBlank() -> db.tasks.filter {
            it.title.contains(query, true) || it.notes.contains(query, true)
        }

        view == "day" -> today.sortedByDescending { Stats.priorityScore(it, t).score }
        view == "upcoming" -> upcoming.sortedBy { it.dueDate }
        view == "overdue" -> overdue.sortedBy { it.dueDate }
        else -> finished
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.s7, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        item {
            PageHead(
                title = "Tasks",
                subtitle = "${today.size} on ${D.relativeDay(day, t).lowercase()} · ${overdue.size} overdue",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        IconBtn(Ic.SEARCH, "Search tasks", { searching = !searching; if (!searching) query = "" })
                        IconBtn(Ic.CALENDAR, "Jump to today", { day = t; view = "day" })
                        IconBtn(Ic.PLUS, "New task", onNew)
                    }
                },
            )

            // ---- the week, and what is left on the chosen day ----
            CCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Week days", Modifier.weight(1f))
                    T(D.relativeDay(day, t), CadenceType.label, C.textMuted)
                }
                Spacer(Modifier.height(Space.s5))
                WeekStrip(
                    days = strip,
                    selected = day,
                    today = t,
                    onSelect = { day = it; view = "day"; query = "" },
                )
                Spacer(Modifier.height(Space.s8))
                T("Tasks left", CadenceType.body, C.textSecondary)
                Spacer(Modifier.height(Space.s2))
                Row(verticalAlignment = Alignment.Bottom) {
                    T("${today.size}", CadenceType.metricXL)
                    Spacer(Modifier.width(Space.s3))
                    T(
                        if (estimate > 0) "~ ${D.formatDuration(estimate)}" else "open",
                        CadenceType.titleM, C.textMuted,
                        Modifier.padding(bottom = 6.dp),
                    )
                }
                Spacer(Modifier.height(Space.s5))
                PriorityBar(today)
                Spacer(Modifier.height(Space.s3))
                T(
                    if (today.isEmpty()) "Nothing open on this day."
                    else "Critical, high, medium then low — the widest band is where the day actually sits.",
                    CadenceType.caption, C.textMuted,
                )
            }
            Spacer(Modifier.height(Space.s4))

            // ---- the two that matter most ----
            if (topTwo.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                    topTwo.forEachIndexed { i, task ->
                        MiniStat(
                            title = task.title,
                            tinted = i == 0,
                            modifier = Modifier.weight(1f),
                            onMenu = { onOpenTask(task) },
                            rows = listOf(
                                MiniRow(
                                    "${Stats.priorityScore(task, t).score}", "Score",
                                    priorityTint(task.priority),
                                ),
                                MiniRow(
                                    task.estimateMin?.let { D.formatDuration(it) } ?: "—",
                                    "Estimate", C.lilac.deep,
                                ),
                            ),
                        )
                    }
                    if (topTwo.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(Space.s5))
                CtaBar("Open the top one", { onOpenTask(topTwo[0]) })
                Spacer(Modifier.height(Space.s7))
            }

            if (searching) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = CadenceType.body.copy(color = C.textPrimary),
                    cursorBrush = SolidColor(C.textPrimary),
                    decorationBox = { inner ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.md))
                                .background(C.surfaceAlt)
                                .padding(horizontal = Space.s5, vertical = Space.s4),
                        ) {
                            if (query.isEmpty()) T("Search every task…", CadenceType.body, C.textMuted)
                            inner()
                        }
                    },
                )
                Spacer(Modifier.height(Space.s4))
            }

            Segmented(
                options = listOf(
                    "day" to "Day${if (today.isNotEmpty()) " · ${today.size}" else ""}",
                    "upcoming" to "Upcoming${if (upcoming.isNotEmpty()) " · ${upcoming.size}" else ""}",
                    "overdue" to "Overdue${if (overdue.isNotEmpty()) " · ${overdue.size}" else ""}",
                    "done" to "Done",
                ),
                value = view,
                onChange = { view = it; query = "" },
            )
            Spacer(Modifier.height(Space.s4))
        }

        if (view == "overdue" && overdue.isNotEmpty() && query.isBlank()) {
            item {
                CCard(variant = CardTone.QUIET, compact = true) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T("${overdue.size} rolled over", CadenceType.bodyStrong)
                            T("Move them all to today, or handle each one.", CadenceType.caption, C.textMuted)
                        }
                        CButton("Move all", {
                            store.postponeAll(overdue.map { it.id }, t)
                            store.showToast("${overdue.size} moved to today")
                        }, variant = "secondary", small = true)
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                CCard {
                    EmptyState(
                        text = when {
                            query.isNotBlank() -> "Nothing matches that."
                            view == "day" ->
                                "Nothing due ${D.relativeDay(day, t).lowercase()}. " +
                                    "Add something, or leave the day open on purpose."
                            view == "overdue" -> "Nothing overdue. That is worth noticing."
                            view == "upcoming" -> "Nothing scheduled ahead yet."
                            else -> "Completed tasks will collect here."
                        },
                        icon = if (view == "done") Ic.CHECK_SQUARE else Ic.INBOX,
                    ) {
                        if (view != "done") CButton("New task", onNew, icon = Ic.PLUS, small = true)
                    }
                }
            }
        } else {
            items(visible, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    onToggle = {
                        val wasDone = task.isDone
                        store.toggleTask(task.id)
                        if (!wasDone) store.showToast("Done") { store.toggleTask(task.id) }
                    },
                    onOpen = { onOpenTask(task) },
                    onPostpone = { onPostpone(task) },
                    onFail = { onFail(task) },
                    categoryName = db.categories.firstOrNull { it.id == task.categoryId }?.name,
                    goalTitle = db.goals.firstOrNull { it.id == task.goalId }?.title,
                    showDate = view != "day",
                )
            }
        }
    }
}

/** The day's open tasks as one bar, split by priority in a fixed order. */
@Composable
private fun PriorityBar(tasks: List<Task>) {
    val order = listOf(Priority.CRITICAL, Priority.HIGH, Priority.MEDIUM, Priority.LOW)
    val counts = order.map { p -> p to tasks.count { it.priority == p } }.filter { it.second > 0 }
    val total = counts.sumOf { it.second }.coerceAtLeast(1)
    Row(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(Radius.full))
            .background(C.surfaceSunken),
    ) {
        if (counts.isEmpty()) {
            Box(Modifier.fillMaxWidth().fillMaxHeight().background(C.sage.base))
        } else {
            counts.forEach { (p, n) ->
                Box(
                    Modifier
                        .weight(n.toFloat() / total)
                        .fillMaxHeight()
                        .background(priorityTint(p)),
                )
            }
        }
    }
}

/**
 * Priority as a tint rather than a traffic light. The order is fixed and always
 * drawn in the same sequence, so the ranking survives for anyone who cannot
 * separate the hues.
 */
@Composable
fun priorityTint(p: Priority): Color = when (p) {
    Priority.CRITICAL -> C.coral.deep
    Priority.HIGH -> C.butter.deep
    Priority.MEDIUM -> C.sky.deep
    Priority.LOW -> C.sage.deep
}
