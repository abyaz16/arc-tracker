package com.example.mytracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.mytracker.data.Priority
import com.example.mytracker.data.Task
import com.example.mytracker.data.TaskStatus
import com.example.mytracker.domain.D
import com.example.mytracker.ui.theme.C
import com.example.mytracker.ui.theme.CadenceType
import com.example.mytracker.ui.theme.Radius
import com.example.mytracker.ui.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Priority as a tint rather than a traffic light. The order is fixed and always
 * drawn in the same sequence, so the ranking survives for anyone who cannot
 * separate the hues.
 */
@Composable
fun priorityColor(p: Priority): Color = when (p) {
    Priority.CRITICAL -> C.coral.deep
    Priority.HIGH -> C.butter.deep
    Priority.MEDIUM -> C.sky.deep
    Priority.LOW -> C.sage.deep
}

/**
 * A task row with a real swipe gesture: right to complete, left to reveal actions.
 * Priority is a 3dp edge bar rather than a coloured background — status stays
 * legible without the list turning into a traffic light.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskRow(
    task: Task,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onPostpone: () -> Unit,
    onFail: () -> Unit,
    modifier: Modifier = Modifier,
    categoryName: String? = null,
    goalTitle: String? = null,
    showDate: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offset = remember { Animatable(0f) }
    var opened by remember { mutableStateOf(false) }

    val actionsWidth = with(density) { 132.dp.toPx() }
    val completeThreshold = with(density) { 72.dp.toPx() }
    val done = task.isDone
    val failed = task.status == TaskStatus.FAILED
    val overdue = !done && !failed && task.dueDate != null && task.dueDate < D.today()

    LaunchedEffect(task.id, done) {
        if (done) offset.snapTo(0f)
    }

    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md))) {
        // revealed layer
        Row(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(Radius.md))
                .background(C.surfaceAlt)
                .padding(horizontal = Space.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            if (offset.value > 20f) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    CIcon(Ic.CHECK, size = 18.dp, tint = C.success)
                    Spacer(Modifier.width(Space.s3))
                    T("Complete", CadenceType.label, C.success)
                }
            } else {
                CButton("Move", { scope.launch { offset.animateTo(0f); opened = false }; onPostpone() }, variant = "secondary", small = true)
                Spacer(Modifier.width(Space.s2))
                CButton("Why?", { scope.launch { offset.animateTo(0f); opened = false }; onFail() }, variant = "secondary", small = true)
            }
        }

        // foreground row
        Row(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .clip(RoundedCornerShape(Radius.lg))
                .background(C.surface)
                .pointerInput(task.id, done) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, delta ->
                            scope.launch { offset.snapTo((offset.value + delta).coerceIn(-160f, 140f)) }
                        },
                        onDragEnd = {
                            scope.launch {
                                when {
                                    offset.value > completeThreshold && !done -> {
                                        offset.animateTo(0f)
                                        opened = false
                                        onToggle()
                                    }

                                    offset.value < -60f -> {
                                        offset.animateTo(-actionsWidth)
                                        opened = true
                                    }

                                    else -> {
                                        offset.animateTo(0f)
                                        opened = false
                                    }
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(if (opened) -actionsWidth else 0f) } },
                    )
                }
                .padding(start = Space.s3, top = Space.s4, bottom = Space.s4, end = Space.s4),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(Radius.full))
                    .background(priorityColor(task.priority)),
            )
            Spacer(Modifier.width(Space.s4))
            CCheckbox(
                checked = done,
                onToggle = onToggle,
                label = if (done) "Mark ${task.title} not done" else "Complete ${task.title}",
            )
            Spacer(Modifier.width(Space.s4))

            Column(Modifier.weight(1f).clickable { onOpen() }) {
                T(
                    task.title,
                    CadenceType.bodyStrong.copy(
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                    ),
                    if (done) C.textMuted else C.textPrimary,
                )
                Spacer(Modifier.height(Space.s2))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    task.dueTime?.let {
                        T(D.formatTime(it), CadenceType.caption, if (overdue) C.overdue else C.textMuted)
                    }
                    if (showDate) task.dueDate?.let {
                        T(D.relativeDay(it), CadenceType.caption, if (overdue) C.overdue else C.textMuted)
                    }
                    task.estimateMin?.let { T(D.formatDuration(it), CadenceType.caption, C.textMuted) }
                    categoryName?.let { T(it, CadenceType.caption, C.textMuted) }
                    goalTitle?.let { T(it, CadenceType.caption, C.lilac.ink) }
                    if (task.subtasks.isNotEmpty()) {
                        T("${task.subtasks.count { s -> s.done }}/${task.subtasks.size} steps", CadenceType.caption, C.textMuted)
                    }
                    if (task.postponeCount >= 3) {
                        Badge("moved ${task.postponeCount}x", C.attentionSubtle, C.attention)
                    }
                    if (failed) Badge("not done", C.overdueSubtle, C.overdue)
                }
            }
        }
    }
}
