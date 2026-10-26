package com.example.mytracker.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import android.view.ViewTreeObserver
import com.example.mytracker.data.Db
import com.example.mytracker.data.Task
import com.example.mytracker.domain.D
import com.example.mytracker.ui.CButton
import com.example.mytracker.ui.CIcon
import com.example.mytracker.ui.Ic
import com.example.mytracker.ui.IconBtn
import com.example.mytracker.ui.Ring
import com.example.mytracker.ui.T
import com.example.mytracker.ui.theme.C
import com.example.mytracker.ui.theme.CadenceType
import com.example.mytracker.ui.theme.Radius
import com.example.mytracker.ui.theme.Space
import kotlinx.coroutines.delay

data class FocusResult(val actualMin: Int, val completed: Boolean, val interruptions: Int, val rating: Int?)

/**
 * Immersive focus session.
 *
 * Elapsed time is recomputed from wall-clock on every tick rather than accumulated,
 * so backgrounding the app, dozing, or a slow frame cannot make the timer drift.
 * Leaving the app is recorded as an interruption — the honest signal available
 * without the special permissions a real app-blocker would need.
 */
@Composable
fun FocusScreen(
    minutes: Int,
    taskId: String?,
    db: Db,
    onDone: (FocusResult) -> Unit,
    onCancel: () -> Unit,
) {
    val totalSec = minutes * 60L
    val startedAt = remember { System.currentTimeMillis() }
    var elapsed by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var pausedFor by remember { mutableLongStateOf(0L) }
    var pausedAt by remember { mutableLongStateOf(0L) }
    var interruptions by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf<Int?>(null) }

    val task = taskId?.let { id -> db.tasks.firstOrNull { it.id == id } }

    LaunchedEffect(paused, finished) {
        while (!finished) {
            if (!paused) {
                elapsed = (System.currentTimeMillis() - startedAt - pausedFor) / 1000
                if (elapsed >= totalSec) {
                    finished = true
                    break
                }
            }
            delay(250)
        }
    }

    // Losing window focus means the user left for something else. It is the only
    // honest interruption signal available without the special permissions a real
    // app-blocker would require — so that is exactly what it is labelled as.
    val view = LocalView.current
    DisposableEffect(view, finished) {
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (!hasFocus && !finished) interruptions++
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.canvas)
            .padding(horizontal = Space.gutter, vertical = Space.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (finished) {
            Spacer(Modifier.weight(1f))
            Ring(pct = 1f, size = 180.dp, stroke = 10.dp, color = C.success) {
                CIcon(Ic.CHECK, size = 40.dp, tint = C.success)
            }
            Spacer(Modifier.height(Space.s7))
            T("${D.formatDuration(minutes)} done", CadenceType.titleL)
            Spacer(Modifier.height(Space.s3))
            T(
                buildString {
                    append(task?.title ?: "Unbound session")
                    if (interruptions > 0) append(" · $interruptions interruption${if (interruptions == 1) "" else "s"}")
                },
                CadenceType.body.merge(CadenceType.centered),
                C.textSecondary,
            )

            Spacer(Modifier.height(Space.s8))
            T("How productive was that?", CadenceType.label, C.textMuted)
            Spacer(Modifier.height(Space.s4))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                (1..5).forEach { n ->
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(Radius.full))
                            .background(if (rating == n) C.accent else C.surface)
                            .clickable { rating = n },
                        contentAlignment = Alignment.Center,
                    ) { T("$n", CadenceType.titleM, if (rating == n) C.onAccent else C.textSecondary) }
                }
            }

            Spacer(Modifier.weight(1f))
            CButton(
                "Save session",
                { onDone(FocusResult(minutes, true, interruptions, rating)) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Ic.X, "End session early", onCancel)
                Spacer(Modifier.weight(1f))
                T("FOCUS", CadenceType.eyebrow, C.textMuted)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(40.dp))
            }

            Spacer(Modifier.weight(1f))

            val remaining = (totalSec - elapsed).coerceAtLeast(0)
            Ring(
                pct = (elapsed.toFloat() / totalSec).coerceIn(0f, 1f),
                size = 236.dp,
                stroke = 10.dp,
                label = "${D.clock(remaining)} remaining",
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T(D.clock(remaining), CadenceType.metricXL)
                    T(if (paused) "Paused" else "remaining", CadenceType.caption, C.textMuted)
                }
            }

            Spacer(Modifier.height(Space.s8))
            if (task != null) {
                T("WORKING ON", CadenceType.eyebrow, C.textMuted)
                Spacer(Modifier.height(Space.s2))
                T(task.title, CadenceType.titleM.merge(CadenceType.centered))
            } else {
                T("Unbound session", CadenceType.body, C.textMuted)
            }
            if (interruptions > 0) {
                Spacer(Modifier.height(Space.s4))
                T(
                    "$interruptions time${if (interruptions == 1) "" else "s"} away from the app",
                    CadenceType.caption, C.textMuted,
                )
            }

            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                CButton(
                    if (paused) "Resume" else "Pause",
                    {
                        if (paused) {
                            pausedFor += System.currentTimeMillis() - pausedAt
                            paused = false
                        } else {
                            pausedAt = System.currentTimeMillis()
                            paused = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    variant = "secondary",
                    icon = if (paused) Ic.PLAY else Ic.PAUSE,
                )
                CButton(
                    "Finish early",
                    {
                        onDone(
                            FocusResult(
                                actualMin = ((elapsed / 60).toInt()).coerceAtLeast(1),
                                completed = false,
                                interruptions = interruptions,
                                rating = null,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    variant = "secondary",
                )
            }
            Spacer(Modifier.height(Space.s4))
            T(
                "Time you've actually spent is recorded either way.",
                CadenceType.caption.merge(CadenceType.centered),
                C.textMuted,
                Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Tasks worth offering as a focus target — open, and due by today. */
fun focusableTasks(db: Db): List<Task> =
    db.tasks.filter { it.isOpen && it.dueDate != null && it.dueDate <= D.today() }
