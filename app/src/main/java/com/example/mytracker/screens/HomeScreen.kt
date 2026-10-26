package com.example.mytracker.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.mytracker.data.Db
import com.example.mytracker.data.Store
import com.example.mytracker.data.Task
import com.example.mytracker.data.TrackingType
import com.example.mytracker.domain.D
import com.example.mytracker.domain.Insights
import com.example.mytracker.domain.Stats
import com.example.mytracker.ui.Badge
import com.example.mytracker.ui.CButton
import com.example.mytracker.ui.CCard
import com.example.mytracker.ui.CChip
import com.example.mytracker.ui.CIcon
import com.example.mytracker.ui.CardTone
import com.example.mytracker.ui.Dial
import com.example.mytracker.ui.MetricRow
import com.example.mytracker.ui.EmptyState
import com.example.mytracker.ui.Eyebrow
import com.example.mytracker.ui.Ic
import com.example.mytracker.ui.IconBtn
import com.example.mytracker.ui.Ring
import com.example.mytracker.ui.Sparkline
import com.example.mytracker.ui.StatTile
import com.example.mytracker.ui.T
import com.example.mytracker.ui.TaskRow
import com.example.mytracker.ui.theme.C
import com.example.mytracker.ui.theme.byKey
import com.example.mytracker.ui.theme.CadenceType
import com.example.mytracker.ui.theme.Radius
import com.example.mytracker.ui.theme.Space

/**
 * Home answers one question: what should I do right now?
 *
 * Its hero slot holds a decision, not a statistic. Everything else on the screen
 * is capped — adding a block here means removing one, which is what stops a
 * personal dashboard becoming the clutter it was meant to fix.
 */
@Composable
fun HomeScreen(
    store: Store,
    db: Db,
    onOpenTask: (Task) -> Unit,
    onStartFocus: (String?) -> Unit,
    onGoto: (String) -> Unit,
    onQuickAdd: () -> Unit,
    onFail: (Task) -> Unit,
    onPostpone: (Task) -> Unit,
) {
    val t = D.today()
    val tasks = Stats.todayProgress(db, t)
    val habits = Stats.habitDayProgress(db, t)
    val focusMin = Stats.focusMinutesOn(db, t)
    val overdue = Stats.overdue(db, t)
    val hero = remember(db) { Stats.topPriorities(db, 3, t).firstOrNull() }
    val insight = remember(db) { Insights.generate(db, t).firstOrNull() }

    val week = remember(db) { Stats.rollup(db, D.addDays(t, -6), t) }
    val lastWeek = remember(db) { Stats.rollup(db, D.addDays(t, -13), D.addDays(t, -7)) }
    val doneThis = week.sumOf { it.tasksDone }
    val doneLast = lastWeek.sumOf { it.tasksDone }

    // everything the day actually asked of you, in one number
    val dueToday = tasks.total + habits.total
    val doneToday = tasks.done + habits.done
    val rate = if (dueToday == 0) 0f else doneToday.toFloat() / dueToday

    val upcoming = db.tasks
        .filter { it.isOpen && it.dueDate == t && it.id != hero?.id }
        .sortedBy { it.dueTime ?: "99:99" }
        .take(3)

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Space.gutter, end = Space.gutter, top = Space.s7, bottom = 120.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        // ---- the day in one card ----
        item {
            CCard(variant = CardTone.SKY, notch = "top") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(Radius.full)).background(C.surface),
                        contentAlignment = Alignment.Center,
                    ) { CIcon(Ic.TREND_UP, size = 18.dp, tint = C.textPrimary) }
                    Spacer(Modifier.width(Space.s4))
                    T("Your progress", CadenceType.titleM, C.textPrimary, Modifier.weight(1f))
                }
                Spacer(Modifier.height(Space.s5))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        // a percentage of nothing is not a reading, so it is not shown as one
                        T(
                            if (dueToday == 0) "—" else "${Stats.pct(doneToday, dueToday)}%",
                            CadenceType.metricXL, C.textPrimary,
                        )
                        Spacer(Modifier.height(Space.s4))
                        CChip(D.formatLongDay(t))
                    }
                    Dial(
                        pct = rate,
                        size = 140.dp,
                        label = if (dueToday == 0) "Nothing is due today"
                        else "$doneToday of $dueToday things due today are done",
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (dueToday == 0) {
                                T("Nothing", CadenceType.label, C.textPrimary)
                                T("due today", CadenceType.label, C.textPrimary)
                            } else {
                                T("$doneToday", CadenceType.metricM, C.textPrimary)
                                T("of $dueToday due", CadenceType.caption, C.textPrimary.copy(alpha = 0.55f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.s4))
        }

        // ---- hero: the next decision ----
        item {
            if (hero != null) {
                CCard(variant = CardTone.BUTTER, notch = "top") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T("Focus now", CadenceType.eyebrow, C.textPrimary, Modifier.weight(1f))
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(Radius.full))
                                .background(C.surface)
                                .clickable { onOpenTask(hero) },
                            contentAlignment = Alignment.Center,
                        ) { CIcon(Ic.CHEVRON_RIGHT, size = 18.dp, tint = C.textPrimary) }
                    }
                    Spacer(Modifier.height(Space.s4))
                    T(hero.title, CadenceType.titleL, C.textPrimary)
                    Spacer(Modifier.height(Space.s3))
                    val bits = buildList {
                        add("${hero.priority.label.lowercase()} priority")
                        hero.estimateMin?.let { add("about ${D.formatDuration(it)}") }
                        if (hero.postponeCount > 0) add("moved ${hero.postponeCount}x")
                        add("score ${Stats.priorityScore(hero, t).score}")
                    }
                    T(bits.joinToString(" · "), CadenceType.label, C.textPrimary.copy(alpha = 0.75f))
                    Spacer(Modifier.height(Space.s5))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        CButton("Start focus", { onStartFocus(hero.id) }, variant = "onAccent", icon = Ic.PLAY)
                        CButton(
                            "Mark done",
                            { store.toggleTask(hero.id); store.showToast("Nice — one down") },
                            variant = "ghost",
                        )
                    }
                }
            } else {
                CCard {
                    EmptyState(
                        "Nothing is claimed for right now. That's either freedom or drift — want to decide which?",
                        Ic.SPARK,
                    ) { CButton("Plan something", onQuickAdd, icon = Ic.PLUS, small = true) }
                }
            }
        }

        // ---- two tiles ----
        item {
            Spacer(Modifier.height(Space.s4))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                StatTile(
                    "Focus today", D.formatDuration(focusMin), Modifier.weight(1f),
                    icon = Ic.CLOCK,
                    onClick = { onGoto("insights") },
                ) { Sparkline(week.map { it.focusMin }, color = C.sky.deep) }
                StatTile(
                    "Done this week", "$doneThis", Modifier.weight(1f), unit = "tasks",
                    icon = Ic.CHECK,
                    onClick = { onGoto("insights") },
                ) {
                    T(
                        if (doneLast == 0) "no prior week"
                        else "${if (doneThis - doneLast >= 0) "+" else ""}${doneThis - doneLast} vs last week",
                        CadenceType.label,
                        if (doneThis >= doneLast) C.success else C.textMuted,
                    )
                }
            }
        }

        // ---- today at a glance ----
        item {
            Spacer(Modifier.height(Space.s4))
            CCard(variant = CardTone.SAGE, notch = "bottom") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(Radius.full)).background(C.surface),
                        contentAlignment = Alignment.Center,
                    ) { CIcon(Ic.SUN, size = 18.dp, tint = C.textPrimary) }
                    Spacer(Modifier.width(Space.s4))
                    Column(Modifier.weight(1f)) {
                        T("Today", CadenceType.titleM, C.textPrimary)
                        T(
                            if (dueToday == 0) "nothing scheduled" else "$doneToday of $dueToday done",
                            CadenceType.label, C.textPrimary.copy(alpha = 0.72f),
                        )
                    }
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(Radius.full))
                            .background(C.surface)
                            .clickable { onQuickAdd() },
                        contentAlignment = Alignment.Center,
                    ) { CIcon(Ic.PLUS, size = 18.dp, tint = C.textPrimary) }
                }
                Spacer(Modifier.height(Space.s5))
                MetricRow(
                    listOf(
                        "Tasks" to "${tasks.done}/${tasks.total}",
                        "Habits" to "${habits.done}/${habits.total}",
                        "Focus" to D.formatDuration(focusMin),
                        "Rate" to "${Stats.pct(doneToday, dueToday)}%",
                    ),
                )
                Spacer(Modifier.height(Space.s6))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (overdue.isNotEmpty()) {
                        CChip("${overdue.size} overdue", onClick = { onGoto("tasks") })
                    } else {
                        CChip("Nothing overdue")
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(Radius.full))
                            .background(C.surface)
                            .clickable { onGoto("tasks") },
                        contentAlignment = Alignment.Center,
                    ) { CIcon(Ic.EDIT, size = 16.dp, tint = C.textPrimary) }
                }
            }
        }

        // ---- habit rings, loggable without leaving home ----
        if (habits.due.isNotEmpty()) {
            item {
                Spacer(Modifier.height(Space.s7))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Habits today", Modifier.weight(1f))
                    T("All →", CadenceType.caption, C.textMuted, Modifier.clickable { onGoto("habits") })
                }
                Spacer(Modifier.height(Space.s4))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.s4),
                ) {
                    habits.due.forEach { h ->
                        val log = Stats.logFor(db, h.id, t)
                        val value = log?.value ?: 0
                        val complete = log?.completed == true
                        Column(
                            Modifier
                                .width(76.dp)
                                .clickable { store.logHabit(h.id, t) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Ring(
                                pct = if (h.tracking == TrackingType.BINARY) (if (complete) 1f else 0f)
                                else (value.toFloat() / h.target).coerceAtMost(1f),
                                size = 62.dp,
                                stroke = 6.dp,
                                color = C.byKey(h.colorKey),
                                label = "${h.name}, ${if (complete) "done" else "$value of ${h.target}"}. Tap to log.",
                            ) {
                                if (h.tracking == TrackingType.BINARY) {
                                    CIcon(
                                        Ic.byKey(h.icon), size = 20.dp,
                                        tint = if (complete) C.textPrimary else C.textMuted,
                                    )
                                } else {
                                    T(
                                        "$value", CadenceType.label,
                                        if (complete) C.textPrimary else C.textSecondary,
                                    )
                                }
                            }
                            Spacer(Modifier.height(Space.s3))
                            T(h.name, CadenceType.label, if (complete) C.textPrimary else C.textSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }

        // ---- next up ----
        if (upcoming.isNotEmpty()) {
            item {
                Spacer(Modifier.height(Space.s7))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Next up", Modifier.weight(1f))
                    T("All tasks →", CadenceType.caption, C.textMuted, Modifier.clickable { onGoto("tasks") })
                }
                Spacer(Modifier.height(Space.s4))
            }
            items(upcoming, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    onToggle = { store.toggleTask(task.id) },
                    onOpen = { onOpenTask(task) },
                    onPostpone = { onPostpone(task) },
                    onFail = { onFail(task) },
                    categoryName = db.categories.firstOrNull { it.id == task.categoryId }?.name,
                )
            }
        }

        // ---- one observation, tagged ----
        if (insight != null) {
            item {
                Spacer(Modifier.height(Space.s7))
                Eyebrow("One thing to know")
                Spacer(Modifier.height(Space.s4))
                CCard {
                    val (bg, fg) = insightColors(insight.kind.name)
                    Badge(insight.kind.label, bg, fg)
                    Spacer(Modifier.height(Space.s4))
                    T(insight.title, CadenceType.titleM)
                    Spacer(Modifier.height(Space.s2))
                    T(insight.body, CadenceType.body, C.textSecondary)
                    Spacer(Modifier.height(Space.s5))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T(insight.evidence, CadenceType.caption, C.textMuted, Modifier.weight(1f))
                        T(
                            "Dismiss", CadenceType.caption, C.textMuted,
                            Modifier.clickable { store.dismissInsight(insight.id) },
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(Space.s7))
            T(
                "Swipe a task right to complete it, left for more.",
                CadenceType.caption.merge(CadenceType.centered),
                C.textMuted,
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun insightColors(kind: String): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> =
    when (kind) {
        "FACT" -> C.sky.soft to C.sky.ink
        "PATTERN" -> C.lilac.soft to C.lilac.ink
        "SAID" -> C.butter.soft to C.butter.ink
        else -> C.sage.soft to C.sage.ink
    }
