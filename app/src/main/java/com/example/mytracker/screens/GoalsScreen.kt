package com.example.mytracker.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.mytracker.data.Db
import com.example.mytracker.data.Goal
import com.example.mytracker.data.GoalStatus
import com.example.mytracker.data.Horizon
import com.example.mytracker.domain.D
import com.example.mytracker.domain.Stats
import com.example.mytracker.ui.Badge
import com.example.mytracker.ui.CBar
import com.example.mytracker.ui.CButton
import com.example.mytracker.ui.CCard
import com.example.mytracker.ui.PageHead
import com.example.mytracker.ui.EmptyState
import com.example.mytracker.ui.Ic
import com.example.mytracker.ui.IconBtn
import com.example.mytracker.ui.Segmented
import com.example.mytracker.ui.T
import com.example.mytracker.ui.theme.C
import com.example.mytracker.ui.theme.CadenceType
import com.example.mytracker.ui.theme.Radius
import com.example.mytracker.ui.theme.Space
import com.example.mytracker.ui.theme.byKey
import kotlin.math.roundToInt

@Composable
fun GoalsScreen(
    db: Db,
    onNew: (Horizon) -> Unit,
    onOpen: (Goal) -> Unit,
) {
    var horizon by remember { mutableStateOf(Horizon.MONTH) }
    val t = D.today()
    val goals = db.goals.filter { it.horizon == horizon }.sortedBy { it.order }
    val completed = goals.count { it.status == GoalStatus.COMPLETED }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.s7, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        item {
            PageHead(
                title = "Goals",
                subtitle = if (horizon == Horizon.MONTH)
                    "${D.monthName(D.parse(t).monthValue)} · $completed/${goals.size} complete"
                else "${D.parse(t).year} · $completed/${goals.size} complete",
                action = { IconBtn(Ic.PLUS, "New goal", { onNew(horizon) }) },
            )
            Segmented(
                options = listOf("month" to "This month", "year" to "This year"),
                value = if (horizon == Horizon.MONTH) "month" else "year",
                onChange = { horizon = if (it == "month") Horizon.MONTH else Horizon.YEAR },
            )
            Spacer(Modifier.height(Space.s2))
        }

        if (goals.isEmpty()) {
            item {
                CCard {
                    EmptyState(
                        if (horizon == Horizon.MONTH)
                            "Nothing claimed for this month. Three goals is usually more honest than ten."
                        else "No goals for the year yet. These are the ones worth writing a reason for.",
                        Ic.TARGET,
                    ) { CButton("Add a goal", { onNew(horizon) }, icon = Ic.PLUS, small = true) }
                }
            }
        } else {
            items(goals, key = { it.id }) { g ->
                val p = Stats.goalProgress(db, g)
                val need = Stats.requiredPace(g, t)
                val left = Stats.daysLeft(g, t)
                val behind = p.pct < need - 0.12f && g.status == GoalStatus.ACTIVE
                val cat = db.categories.firstOrNull { it.id == g.categoryId }

                CCard(onClick = { onOpen(g) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (cat != null) {
                            Box(
                                Modifier.size(8.dp).clip(RoundedCornerShape(Radius.full))
                                    .background(C.byKey(cat.colorKey)),
                            )
                            Spacer(Modifier.width(Space.s3))
                        }
                        T(g.title, CadenceType.titleM, modifier = Modifier.weight(1f), maxLines = 1)
                        when {
                            g.status == GoalStatus.COMPLETED -> Badge("Done", C.successSubtle, C.success)
                            behind -> Badge("behind pace", C.attentionSubtle, C.attention)
                        }
                    }
                    Spacer(Modifier.height(Space.s4))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        T("${(p.pct * 100).roundToInt()}", CadenceType.metricL, modifier = Modifier.weight(1f))
                        T(p.label, CadenceType.caption, C.textMuted)
                    }
                    Spacer(Modifier.height(Space.s4))
                    CBar(
                        pct = p.pct,
                        color = when {
                            g.status == GoalStatus.COMPLETED -> C.sage.deep
                            behind -> C.butter.deep
                            else -> C.sky.deep
                        },
                        pace = if (g.status == GoalStatus.ACTIVE) need else null,
                    )
                    Spacer(Modifier.height(Space.s3))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        T("$left days left", CadenceType.caption, C.textMuted)
                        T("even pace: ${(need * 100).roundToInt()}%", CadenceType.caption, C.textMuted)
                    }
                }
            }
        }
    }
}
