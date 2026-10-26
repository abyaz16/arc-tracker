package com.example.mytracker.domain

import com.example.mytracker.data.Db
import com.example.mytracker.data.GoalStatus
import com.example.mytracker.data.Insight
import com.example.mytracker.data.InsightKind
import com.example.mytracker.data.TaskStatus
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The insight engine.
 *
 * Hard rules, enforced in code rather than merely documented:
 *  - a PATTERN needs >= 14 days of data and >= 5 days in each group, else nothing is emitted
 *  - associations are stated as associations; no causal claim is ever made
 *  - every insight carries the evidence it was computed from, so the user can check it
 *  - if the data isn't there, the rule returns nothing. Nothing is invented.
 */
object Insights {

    private const val MIN_DAYS = 14
    private const val MIN_GROUP = 5

    fun generate(db: Db, base: String = D.today()): List<Insight> {
        val out = ArrayList<Insight>()
        val from30 = D.addDays(base, -29)
        val days30 = Stats.rollup(db, from30, base)

        // FACT — when in the day work actually gets finished
        run {
            val done = db.tasks.filter { it.isDone && it.completedAt != null && it.dueDate != null && it.dueDate >= from30 }
            if (done.size >= 20) {
                val early = done.count { D.hourOfEpoch(it.completedAt!!) < 18 }
                val share = (early.toDouble() / done.size * 100).roundToInt()
                out += Insight(
                    id = "time-of-day",
                    kind = InsightKind.FACT,
                    title = "$share% of what you finish, you finish before 6pm",
                    body = if (share >= 60)
                        "Your afternoons carry most of the load. Work scheduled late in the evening competes with a smaller share of your completions."
                    else
                        "A large part of your completions land after 6pm. Worth knowing when you plan your day.",
                    evidence = "${done.size} completed tasks in the last 30 days, bucketed by completion time",
                )
            }
        }

        // FACT — this week against last week
        run {
            val thisW = days30.takeLast(7)
            val lastW = days30.dropLast(7).takeLast(7)
            val a = thisW.sumOf { it.tasksDone }
            val b = lastW.sumOf { it.tasksDone }
            if (b >= MIN_GROUP) {
                val delta = a - b
                val pct = (delta.toDouble() / b * 100).roundToInt()
                if (abs(pct) >= 15) {
                    out += Insight(
                        id = "week-delta",
                        kind = InsightKind.FACT,
                        title = if (delta > 0) "You completed $delta more tasks than last week"
                        else "You completed ${-delta} fewer tasks than last week",
                        body = "$a this week against $b the week before — a change of ${if (pct > 0) "+" else ""}$pct%.",
                        evidence = "Completed task counts for the last two 7-day windows",
                    )
                }
            }
        }

        // PATTERN — focus and completion, stated strictly as an association
        run {
            val valid = days30.filter { it.tasksDue > 0 }
            if (valid.size >= MIN_DAYS) {
                val withFocus = valid.filter { it.focusMin >= 25 }
                val without = valid.filter { it.focusMin < 25 }
                if (withFocus.size >= MIN_GROUP && without.size >= MIN_GROUP) {
                    val rateA = Stats.pct(withFocus.sumOf { it.tasksDone }, withFocus.sumOf { it.tasksDue })
                    val rateB = Stats.pct(without.sumOf { it.tasksDone }, without.sumOf { it.tasksDue })
                    if (abs(rateA - rateB) >= 8) {
                        out += Insight(
                            id = "focus-completion",
                            kind = InsightKind.PATTERN,
                            title = "Days with a focus session and days without differ by ${abs(rateA - rateB)} points",
                            body = "On days you ran at least one focus session you completed $rateA% of due tasks; on days you didn't, $rateB%. " +
                                "These occur together in your data. Which way the influence runs is not established here.",
                            evidence = "${withFocus.size} days with focus vs ${without.size} without, over the last 30 days",
                        )
                    }
                }
            }
        }

        // YOU SAID — the user's own recorded reasons
        run {
            val breakdown = Stats.failureBreakdown(db, from30, base)
            val total = breakdown.sumOf { it.second }
            if (total >= 8 && breakdown.isNotEmpty()) {
                val (label, n) = breakdown.first()
                out += Insight(
                    id = "failure-top",
                    kind = InsightKind.SAID,
                    title = "You recorded “${label.lowercase()}” on $n of $total unfinished tasks",
                    body = "This is what you told the app, not something it inferred. It is the most frequent reason you gave this month.",
                    evidence = "$total recorded reasons in the last 30 days",
                )
            }
        }

        // FACT — a task that keeps moving
        run {
            val stuck = db.tasks
                .filter { it.postponeCount >= 3 && it.status != TaskStatus.COMPLETED && it.status != TaskStatus.CANCELLED }
                .maxByOrNull { it.postponeCount }
            if (stuck != null) {
                out += Insight(
                    id = "postponed-${stuck.id}",
                    kind = InsightKind.FACT,
                    title = "“${stuck.title}” has moved ${stuck.postponeCount} times",
                    body = "Tasks that move this often are usually too large or too vague to start. Breaking it into a first 10-minute step is one way out.",
                    evidence = "Postpone counter on the task itself",
                )
            }
        }

        // FACT — a streak worth protecting
        run {
            for (h in db.habits) {
                val s = Stats.currentStreak(db, h, base)
                val best = Stats.bestStreak(db, h, base)
                val weekly = Stats.streakUnit(h) == "w"
                val floor = if (weekly) 3 else 7
                if (s >= floor && s >= best) {
                    out += Insight(
                        id = "streak-${h.id}",
                        kind = InsightKind.FACT,
                        title = "$s ${if (weekly) "weeks" else "days"} of ${h.name} — your longest run",
                        body = "Nothing to do here. Recorded because it took work.",
                        evidence = "Derived from your habit logs",
                    )
                    break
                }
            }
        }

        // FACT — a habit slipping against its own baseline, not anyone else's
        run {
            for (h in db.habits) {
                val recent = Stats.completionRate(db, h, 14, base)
                val baseline = Stats.completionRate(db, h, 56, base)
                if (baseline >= 30 && baseline - recent >= 20) {
                    out += Insight(
                        id = "slipping-${h.id}",
                        kind = InsightKind.FACT,
                        title = "${h.name} is at $recent%, against $baseline% over eight weeks",
                        body = "A drop against your own baseline. Worth a look at whether the schedule still fits your week.",
                        evidence = "Completion rate over 14 days compared with 56 days",
                    )
                    break
                }
            }
        }

        // SUGGESTION — a goal behind an even pace
        run {
            for (g in db.goals.filter { it.status == GoalStatus.ACTIVE }) {
                val p = Stats.goalProgress(db, g)
                val need = Stats.requiredPace(g, base)
                val left = Stats.daysLeft(g, base)
                if (need > 0.25f && p.pct < need - 0.2f && left > 0) {
                    out += Insight(
                        id = "pace-${g.id}",
                        kind = InsightKind.SUGGESTION,
                        title = "“${g.title}” is behind an even pace",
                        body = "${(p.pct * 100).roundToInt()}% done with ${(need * 100).roundToInt()}% of the period elapsed, $left days left. " +
                            "You could reduce the target, extend the deadline, or schedule the next step now — all three are reasonable.",
                        evidence = "Progress ${p.label} measured against elapsed time in the goal period",
                    )
                    break
                }
            }
        }

        // FACT — focus time this week
        run {
            val week = days30.takeLast(7)
            val mins = week.sumOf { it.focusMin }
            if (mins > 0) {
                out += Insight(
                    id = "focus-week",
                    kind = InsightKind.FACT,
                    title = "${D.formatDuration(mins)} of focused time this week",
                    body = "Across ${week.count { it.focusMin > 0 }} days. Sessions you abandoned are counted at the time actually spent.",
                    evidence = "Sum of focus session durations in the last 7 days",
                )
            }
        }

        return out.filter { it.id !in db.dismissedInsights }
    }
}
