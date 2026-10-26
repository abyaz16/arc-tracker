package com.example.mytracker.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mytracker.data.Db
import com.example.mytracker.data.Store
import com.example.mytracker.domain.D
import com.example.mytracker.domain.Insights
import com.example.mytracker.domain.Stats
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.mytracker.domain.UsageTracker
import com.example.mytracker.ui.CIcon
import com.example.mytracker.ui.theme.Radius

import com.example.mytracker.ui.Badge
import com.example.mytracker.ui.BarChart
import com.example.mytracker.ui.CButton
import com.example.mytracker.ui.CButton
import com.example.mytracker.ui.CCard
import com.example.mytracker.ui.Capsule
import com.example.mytracker.ui.CardTone
import com.example.mytracker.ui.Dial
import com.example.mytracker.ui.PageHead
import com.example.mytracker.ui.VBars
import com.example.mytracker.ui.EmptyState
import com.example.mytracker.ui.Eyebrow
import com.example.mytracker.ui.Ic
import com.example.mytracker.ui.LineChart
import com.example.mytracker.ui.RankedBars
import com.example.mytracker.ui.Segmented
import com.example.mytracker.ui.T
import com.example.mytracker.ui.theme.C
import com.example.mytracker.ui.theme.CadenceType
import com.example.mytracker.ui.theme.Space

@Composable
fun InsightsScreen(store: Store, db: Db) {
    val context = LocalContext.current
    var phoneUsage by remember { mutableStateOf(UsageTracker.getTodayUsage(context)) }

    DisposableEffect(context) {
        val lifecycleOwner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                phoneUsage = UsageTracker.getTodayUsage(context)
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    var period by remember { mutableStateOf("30") }
    val t = D.today()
    val days = period.toInt()

    val data = remember(db, period) { Stats.rollup(db, D.addDays(t, -(days - 1).toLong()), t) }
    val prev = remember(db, period) {
        Stats.rollup(db, D.addDays(t, -(days * 2 - 1).toLong()), D.addDays(t, -days.toLong()))
    }
    val insights = remember(db) { Insights.generate(db, t) }

    val tasksDone = data.sumOf { it.tasksDone }
    val tasksDue = data.sumOf { it.tasksDue }
    val prevDone = prev.sumOf { it.tasksDone }
    val focusMin = data.sumOf { it.focusMin }
    val prevFocus = prev.sumOf { it.focusMin }
    val habitsDone = data.sumOf { it.habitsDone }
    val habitsDue = data.sumOf { it.habitsDue }

    val byWeekday = remember(data) {
        val done = IntArray(8)
        val due = IntArray(8)
        data.forEach {
            val w = D.dow(it.date)
            done[w] += it.tasksDone
            due[w] += it.tasksDue
        }
        D.weekOrder(db.settings.weekStartsMonday)
            .map { w -> Triple(D.dowLetter(w), Stats.pct(done[w], due[w]), null) }
    }

    val hourly = remember(db, period) {
        val raw = Stats.completionByHour(db, D.addDays(t, -(days - 1).toLong()))
        listOf(
            Triple("6–10", (6..9).sumOf { raw[it] }, null),
            Triple("10–14", (10..13).sumOf { raw[it] }, null),
            Triple("14–18", (14..17).sumOf { raw[it] }, null),
            Triple("18–22", (18..21).sumOf { raw[it] }, null),
            Triple("22–6", (22..23).sumOf { raw[it] } + (0..5).sumOf { raw[it] }, null),
        )
    }

    // days in the window on which anything at all was recorded
    val activeDays = data.count { it.tasksDone > 0 || it.habitsDone > 0 || it.focusMin > 0 }

    val ramp = C.cat
    val failures = remember(db, period, ramp) {
        Stats.failureBreakdown(db, D.addDays(t, -(days - 1).toLong()), t)
            .mapIndexed { i, (label, n) -> Triple(label, n, ramp[i % ramp.size]) }
    }

    val failureCount = failures.sumOf { it.second }

    // Has anything at all ever been recorded? A dashboard of zeros looks like a
    // reading when it is really the absence of one, so nothing is drawn until
    // there is something to draw it from.
    val hasAnyRecord = db.tasks.isNotEmpty() || db.habits.isNotEmpty() || db.goals.isNotEmpty() ||
        db.sessions.isNotEmpty() || db.failures.isNotEmpty() || db.habitLogs.isNotEmpty()

    // ...and is there anything inside the window currently selected?
    val hasWindowRecord =
        tasksDue > 0 || tasksDone > 0 || focusMin > 0 || habitsDue > 0 || failures.isNotEmpty()

    if (!hasAnyRecord) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter)
                .padding(top = Space.s7),
        ) {
            PageHead(title = "Insights", subtitle = "What your own record says")
            CCard {
                EmptyState(
                    "Nothing recorded yet, so there is nothing true to say. This screen fills itself " +
                        "in from what you actually do — finish a task, log a habit, run a focus " +
                        "session, and it starts reporting.",
                    Ic.SPARK,
                )
            }
            Spacer(Modifier.height(Space.s5))
            T(
                "Nothing here is ever simulated. Every number comes from your own record.",
                CadenceType.caption.merge(CadenceType.centered),
                C.textMuted,
                Modifier.fillMaxWidth(),
            )
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.s7),
    ) {
        item {
            PageHead(title = "Insights", subtitle = "What your own record says")
            Spacer(Modifier.height(Space.s4))
            Segmented(
                options = listOf("7" to "7 days", "30" to "30 days", "90" to "90 days"),
                value = period,
                onChange = { period = it },
            )
        }

        // ---- the shape of the period, at a glance ----
        if (hasWindowRecord) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                CCard(Modifier.weight(1f)) {
                    T("Completion", CadenceType.titleM)
                    Spacer(Modifier.height(Space.s5))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Dial(
                            pct = if (tasksDue == 0) 0f else tasksDone.toFloat() / tasksDue,
                            size = 150.dp,
                            color = C.coral.deep,
                            track = C.surfaceAlt,
                            label = "${Stats.pct(tasksDone, tasksDue)} percent of due tasks completed",
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                T("${Stats.pct(tasksDone, tasksDue)}%", CadenceType.metricL)
                                T("of $tasksDue due", CadenceType.caption, C.textMuted)
                            }
                        }
                    }
                }
                CCard(Modifier.weight(1f), variant = CardTone.SKY) {
                    T("Focus", CadenceType.titleM)
                    Spacer(Modifier.height(Space.s5))
                    VBars(
                        data = data.takeLast(7).map { D.dowLetter(D.dow(it.date)) to it.focusMin.toFloat() },
                        label = "Focus minutes over the last seven days",
                        onTint = true,
                    )
                    Spacer(Modifier.height(Space.s4))
                    T(
                        "${D.formatDuration(data.takeLast(7).sumOf { it.focusMin })} in the last 7 days",
                        CadenceType.label, C.textPrimary.copy(alpha = 0.75f),
                    )
                }
            }
        }

        // ---- the five numbers as columns ----
        item {
            CCard {
                T("Last $days days", CadenceType.titleM)
                Spacer(Modifier.height(Space.s6))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    Capsule(
                        if (tasksDue == 0) 0f else tasksDone.toFloat() / tasksDue,
                        "$tasksDone", "Done", C.sage.base, Modifier.weight(1f),
                    )
                    Capsule(
                        (focusMin.toFloat() / (days * 60).coerceAtLeast(1)).coerceAtMost(1f),
                        D.formatDuration(focusMin), "Focus", C.sky.base, Modifier.weight(1f),
                    )
                    Capsule(
                        if (habitsDue == 0) 0f else habitsDone.toFloat() / habitsDue,
                        "${Stats.pct(habitsDone, habitsDue)}%", "Habits", C.butter.base, Modifier.weight(1f),
                    )
                    Capsule(
                        (activeDays.toFloat() / 30f).coerceAtMost(1f),
                        "$activeDays", "Active", C.lilac.base, Modifier.weight(1f),
                    )
                    Capsule(
                        if (tasksDue == 0) 0f else (failureCount.toFloat() / tasksDue).coerceAtMost(1f),
                        "$failureCount", "Reasons", C.coral.base, Modifier.weight(1f),
                    )
                }
            }
        }
        }

        item {
            Spacer(Modifier.height(Space.s5))
            Eyebrow("Observations")
        }

        if (insights.isEmpty()) {
            item {
                CCard {
                    EmptyState(
                        "Not enough recorded yet to say anything that would actually be true. Keep using it for a week or two.",
                        Ic.SPARK,
                    )
                }
            }
        } else {
            items(insights.take(5), key = { it.id }) { i ->
                CCard {
                    val (bg, fg) = insightColors(i.kind.name)
                    Badge(i.kind.label, bg, fg)
                    Spacer(Modifier.height(Space.s4))
                    T(i.title, CadenceType.titleM)
                    Spacer(Modifier.height(Space.s2))
                    T(i.body, CadenceType.body, C.textSecondary)
                    Spacer(Modifier.height(Space.s5))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T(i.evidence, CadenceType.caption, C.textMuted, Modifier.weight(1f))
                        Spacer(Modifier.width(Space.s4))
                        T(
                            "Dismiss", CadenceType.caption, C.textMuted,
                            Modifier.clickable { store.dismissInsight(i.id) },
                        )
                    }
                }
            }
        }

        item {
            T(
                "FACT is computed directly. PATTERN is an association, not a cause. " +
                    "YOU SAID is your own recorded reason. SUGGESTION is optional.",
                CadenceType.caption, C.textMuted,
            )
        }

        if (hasWindowRecord) {
        // headline numbers
        item {
            Spacer(Modifier.height(Space.s5))
            Eyebrow("Last $days days")
            Spacer(Modifier.height(Space.s4))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                CCard(compact = true, modifier = Modifier.weight(1f)) {
                    T("TASKS COMPLETED", CadenceType.caption, C.textMuted)
                    T("$tasksDone", CadenceType.metricL)
                    T(
                        if (prevDone == 0) "no prior period"
                        else "${if (tasksDone - prevDone >= 0) "+" else ""}${tasksDone - prevDone} vs previous",
                        CadenceType.caption, if (tasksDone >= prevDone) C.success else C.textMuted,
                    )
                }
                CCard(compact = true, modifier = Modifier.weight(1f)) {
                    T("COMPLETION RATE", CadenceType.caption, C.textMuted)
                    T("${Stats.pct(tasksDone, tasksDue)}%", CadenceType.metricL)
                    T("$tasksDone of $tasksDue due", CadenceType.caption, C.textMuted)
                }
            }
            Spacer(Modifier.height(Space.s4))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                CCard(compact = true, modifier = Modifier.weight(1f)) {
                    T("FOCUS TIME", CadenceType.caption, C.textMuted)
                    T(D.formatDuration(focusMin), CadenceType.metricM)
                    T(
                        if (prevFocus == 0) "no prior period"
                        else "${if (focusMin - prevFocus >= 0) "+" else "-"}${D.formatDuration(kotlin.math.abs(focusMin - prevFocus))} vs previous",
                        CadenceType.caption, if (focusMin >= prevFocus) C.success else C.textMuted,
                    )
                }
                CCard(compact = true, modifier = Modifier.weight(1f)) {
                    T("HABITS KEPT", CadenceType.caption, C.textMuted)
                    T("${Stats.pct(habitsDone, habitsDue)}%", CadenceType.metricL)
                    T("$habitsDone of $habitsDue due", CadenceType.caption, C.textMuted)
                }
            }
        }

        item {
            ChartSection("Am I more reliable than I was?") {
                LineChart(
                    values = data.map { if (it.tasksDue == 0) 0f else it.tasksDone.toFloat() / it.tasksDue * 100f },
                    firstLabel = D.formatDay(data.first().date),
                    lastLabel = D.formatDay(data.last().date),
                    format = { "${it.toInt()}%" },
                )
                Spacer(Modifier.height(Space.s4))
                T(
                    "Share of each day's due tasks that you completed. Days with nothing due read as zero.",
                    CadenceType.caption, C.textMuted,
                )
            }
        }

        item {
            ChartSection("When in the day do I finish things?") {
                if (hourly.sumOf { it.second } == 0) {
                    T("Nothing completed in this window yet.", CadenceType.body, C.textMuted)
                } else {
                    BarChart(hourly, format = { "$it tasks" })
                }
                Spacer(Modifier.height(Space.s4))
                T(
                    "Bucketed by the time you actually ticked them off, not when they were due.",
                    CadenceType.caption, C.textMuted,
                )
            }
        }

        item {
            ChartSection("Which days carry the load?") {
                BarChart(byWeekday, color = C.cat[3], format = { "$it%" })
                Spacer(Modifier.height(Space.s4))
                T("Completion rate by weekday, Monday first.", CadenceType.caption, C.textMuted)
            }
        }

        item {
            ChartSection("Am I protecting real time for real work?") {
                val last14 = data.takeLast(14)
                BarChart(
                    last14.map { Triple(D.dowLetter(D.dow(it.date)), it.focusMin, null) },
                    color = C.cat[1],
                    format = { D.formatDuration(it) },
                )
                Spacer(Modifier.height(Space.s4))
                T(
                    "Focus minutes over the last 14 days. Abandoned sessions count at the time actually spent.",
                    CadenceType.caption, C.textMuted,
                )
            }
        }

        item {
            ChartSection("What actually stops me?") {
                if (failures.isEmpty()) {
                    EmptyState(
                        "No reasons recorded in this window. They appear here when you note why something didn't get done.",
                        Ic.INFO,
                    )
                } else {
                    RankedBars(failures, format = { "${it}x" })
                    Spacer(Modifier.height(Space.s4))
                    T(
                        "These are your own words, not the app's inference. Nothing here is a diagnosis.",
                        CadenceType.caption, C.textMuted,
                    )
                }
            }
        }

        }

        item {
            Spacer(Modifier.height(Space.s5))
            Eyebrow("Phone usage & screen time")
            Spacer(Modifier.height(Space.s4))

            if (!phoneUsage.hasPermission) {
                CCard(variant = CardTone.BUTTER, notch = "top") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T("Phone Usage Tracking", CadenceType.titleM, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(Space.s2))
                    T(
                        "Track your actual daily screen time and see which apps consume your day. Uses Android's native UsageStatsManager with zero network calls.",
                        CadenceType.body,
                        C.textSecondary,
                    )
                    Spacer(Modifier.height(Space.s4))
                    CButton(
                        text = "Grant Usage Access",
                        onClick = { UsageTracker.openUsageSettings(context) },
                        icon = Ic.SPARK,
                        small = true,
                    )
                }
            } else {
                CCard(variant = CardTone.SKY, notch = "top") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T("TODAY'S SCREEN TIME", CadenceType.caption, C.textMuted)
                            Spacer(Modifier.height(Space.s1))
                            T(UsageTracker.formatDuration(phoneUsage.totalScreenTimeMillis), CadenceType.metricL)
                        }
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(Radius.full))
                                .background(C.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            CIcon(Ic.CLOCK, size = 22.dp, tint = C.sky.deep)
                        }
                    }
                    Spacer(Modifier.height(Space.s3))
                    T(
                        if (phoneUsage.apps.isEmpty()) "No significant phone usage recorded today."
                        else "Top apps by active foreground time today:",
                        CadenceType.caption,
                        C.textMuted,
                    )
                    if (phoneUsage.apps.isNotEmpty()) {
                        Spacer(Modifier.height(Space.s4))
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                            val maxMillis = phoneUsage.apps.firstOrNull()?.foregroundMillis?.coerceAtLeast(1L) ?: 1L
                            phoneUsage.apps.take(6).forEachIndexed { index, app ->
                                val fraction = (app.foregroundMillis.toFloat() / maxMillis).coerceIn(0.06f, 1f)
                                val color = C.cat[index % C.cat.size]
                                Column(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        T(app.appName, CadenceType.label, C.textPrimary, modifier = Modifier.weight(1f, fill = false))
                                        Spacer(Modifier.width(Space.s2))
                                        T(UsageTracker.formatDuration(app.foregroundMillis), CadenceType.label, C.textSecondary)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(Radius.full))
                                            .background(C.surfaceAlt),
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(fraction)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(Radius.full))
                                                .background(color),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(Space.s5))
            CCard(variant = CardTone.QUIET, compact = true, icon = Ic.INFO, title = "Platform capabilities") {
                T(
                    "Phone usage and screen time are read directly from Android's UsageStatsManager when permission is granted. " +
                        "Data stays 100% on your device and is never sent to any server. " +
                        "Android has no API that genuinely blocks another app — the strongest approach interrupts you rather than preventing launch.",
                    CadenceType.caption, C.textMuted,
                )
            }
        }
    }
}

@Composable
private fun ChartSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(Space.s5))
        Eyebrow(title)
        Spacer(Modifier.height(Space.s4))
        CCard(content = content)
    }
}

