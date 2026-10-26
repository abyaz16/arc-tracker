package com.example.mytracker.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.mytracker.data.Db
import com.example.mytracker.data.Habit
import com.example.mytracker.data.ScheduleType
import com.example.mytracker.data.Store
import com.example.mytracker.data.TrackingType
import com.example.mytracker.domain.D
import com.example.mytracker.domain.Stats
import com.example.mytracker.ui.CButton
import com.example.mytracker.ui.CCard
import com.example.mytracker.ui.CardTone
import com.example.mytracker.ui.PageHead
import com.example.mytracker.ui.CIcon
import com.example.mytracker.ui.Capsule
import com.example.mytracker.ui.EmptyState
import com.example.mytracker.ui.Eyebrow
import com.example.mytracker.ui.Ic
import com.example.mytracker.ui.IconBtn
import com.example.mytracker.ui.Ring
import com.example.mytracker.ui.T
import com.example.mytracker.ui.theme.C
import com.example.mytracker.ui.theme.CadenceType
import com.example.mytracker.ui.theme.Radius
import com.example.mytracker.ui.theme.Space
import com.example.mytracker.ui.theme.byKey

@Composable
fun HabitsScreen(
    store: Store,
    db: Db,
    onNew: () -> Unit,
    onOpen: (Habit) -> Unit,
) {
    val t = D.today()
    val progress = Stats.habitDayProgress(db, t)

    val drifting = db.habits.filter {
        val recent = Stats.completionRate(db, it, 14, t)
        val base = Stats.completionRate(db, it, 56, t)
        base >= 30 && base - recent >= 20
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.s7, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        item {
            PageHead(
                title = "Habits",
                subtitle = "${progress.done} of ${progress.total} done today",
                action = { IconBtn(Ic.PLUS, "New habit", onNew) },
            )
        }

        if (db.habits.isEmpty()) {
            item {
                CCard {
                    EmptyState(
                        "Habits are the things you want to be true of you by default. Start with one — two at most.",
                        Ic.REPEAT,
                    ) { CButton("Create a habit", onNew, icon = Ic.PLUS, small = true) }
                }
            }
            return@LazyColumn
        }

        // today's rings
        item {
            CCard(variant = CardTone.SAGE, icon = Ic.SUN, title = "Today", notch = "bottom") {
                if (progress.due.isEmpty()) {
                    T("Nothing scheduled today.", CadenceType.body, C.textMuted)
                } else {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Space.s4),
                    ) {
                        progress.due.forEach { h ->
                            val log = Stats.logFor(db, h.id, t)
                            val value = log?.value ?: 0
                            val complete = log?.completed == true
                            Column(
                                Modifier.width(76.dp).clickable { store.logHabit(h.id, t) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Ring(
                                    pct = if (h.tracking == TrackingType.BINARY) (if (complete) 1f else 0f)
                                    else (value.toFloat() / h.target).coerceAtMost(1f),
                                    size = 62.dp,
                                    stroke = 5.dp,
                                    color = C.byKey(h.colorKey),
                                    label = "${h.name}: ${if (complete) "done" else "$value of ${h.target} ${h.unit}"}. Tap to log.",
                                ) {
                                    if (h.tracking == TrackingType.BINARY) {
                                        CIcon(Ic.byKey(h.icon), size = 20.dp, tint = if (complete) C.byKey(h.colorKey) else C.textMuted)
                                    } else {
                                        T("$value", CadenceType.label, if (complete) C.byKey(h.colorKey) else C.textSecondary)
                                    }
                                }
                                Spacer(Modifier.height(Space.s3))
                                T(h.name, CadenceType.caption, if (complete) C.textPrimary else C.textSecondary, maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Space.s3))
                T(
                    "Tap to log. Counters step up one at a time; tap past the target to reset.",
                    CadenceType.caption, C.textMuted,
                )
            }
        }

        // consistency capsules
        item {
            Spacer(Modifier.height(Space.s7))
            Eyebrow("Consistency · 30 days")
            Spacer(Modifier.height(Space.s4))
            CCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    db.habits.take(6).forEach { h ->
                        val rate = Stats.completionRate(db, h, 30, t)
                        Capsule(
                            pct = rate / 100f,
                            label = "$rate%",
                            caption = if (h.name.length > 7) h.name.take(7) + "…" else h.name,
                            color = C.byKey(h.colorKey),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (drifting.isNotEmpty()) {
            item {
                Spacer(Modifier.height(Space.s7))
                Eyebrow("Drifting against your own baseline")
                Spacer(Modifier.height(Space.s4))
            }
            items(drifting, key = { "drift-${it.id}" }) { h ->
                CCard(compact = true, onClick = { onOpen(h) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(Radius.full)).background(C.surfaceAlt),
                            contentAlignment = Alignment.Center,
                        ) { CIcon(Ic.byKey(h.icon), size = 16.dp, tint = C.byKey(h.colorKey)) }
                        Spacer(Modifier.width(Space.s4))
                        Column(Modifier.weight(1f)) {
                            T(h.name, CadenceType.bodyStrong)
                            T(
                                "${Stats.completionRate(db, h, 14, t)}% recently vs ${Stats.completionRate(db, h, 56, t)}% over 8 weeks",
                                CadenceType.caption, C.textMuted,
                            )
                        }
                        CIcon(Ic.CHEVRON_RIGHT, size = 16.dp, tint = C.textMuted)
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(Space.s7))
            Eyebrow("All habits")
            Spacer(Modifier.height(Space.s4))
        }

        items(db.habits, key = { it.id }) { h ->
            val streak = Stats.currentStreak(db, h, t)
            val unit = Stats.streakUnit(h)
            val rate = Stats.completionRate(db, h, 30, t)
            CCard(compact = true, onClick = { onOpen(h) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(Radius.full)).background(C.surfaceAlt),
                        contentAlignment = Alignment.Center,
                    ) { CIcon(Ic.byKey(h.icon), size = 16.dp, tint = C.byKey(h.colorKey)) }
                    Spacer(Modifier.width(Space.s4))
                    Column(Modifier.weight(1f)) {
                        T(h.name, CadenceType.bodyStrong, maxLines = 1)
                        T(scheduleLabel(h), CadenceType.caption, C.textMuted)
                    }
                    if (streak > 0) {
                        CIcon(Ic.FLAME, size = 14.dp, tint = C.attention)
                        Spacer(Modifier.width(Space.s1))
                        T("$streak$unit", CadenceType.label, C.attention)
                        Spacer(Modifier.width(Space.s5))
                    }
                    T("$rate%", CadenceType.label, C.textMuted)
                }
            }
        }
    }
}

fun scheduleLabel(h: Habit): String {
    val base = when (h.schedule.type) {
        ScheduleType.DAILY -> "Every day"
        ScheduleType.WEEKDAYS -> "${h.schedule.days.size} days a week"
        ScheduleType.TIMES_PER_WEEK -> "${h.schedule.timesPerWeek}x a week"
    }
    return if (h.tracking == TrackingType.BINARY) base
    else "$base · ${h.target}${if (h.unit.isEmpty()) "" else " ${h.unit}"}"
}
