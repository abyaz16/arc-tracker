package com.example.mytracker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mytracker.ui.theme.C
import com.example.mytracker.ui.theme.CadenceType
import com.example.mytracker.ui.theme.Space

/**
 * Charts, each paired in its host screen with the question it answers.
 * Every one carries a spoken description, because colour is never the only channel.
 */

@Composable
fun Sparkline(values: List<Int>, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    if (values.size < 2) {
        Spacer(modifier.height(32.dp))
        return
    }
    val stroke = if (color == Color.Unspecified) C.sky.deep else color
    val max = (values.max()).coerceAtLeast(1)
    val min = values.min().coerceAtMost(0)
    val span = (max - min).coerceAtLeast(1)
    val trend = if (values.last() >= values.first()) "rising" else "falling"

    Canvas(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .semantics { contentDescription = "Trend over ${values.size} days, $trend overall" },
    ) {
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i.toFloat() / (values.size - 1) * size.width
            val y = size.height - (v - min).toFloat() / span * (size.height - 4f) - 2f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, stroke, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val lastX = size.width
        val lastY = size.height - (values.last() - min).toFloat() / span * (size.height - 4f) - 2f
        drawCircle(stroke, radius = 3f, center = Offset(lastX - 2f, lastY))
    }
}

@Composable
fun BarChart(
    data: List<Triple<String, Int, Color?>>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    color: Color = Color.Unspecified,
    format: (Int) -> String = { it.toString() },
) {
    if (data.isEmpty()) return
    val fill = if (color == Color.Unspecified) C.sky.deep else color
    val max = (data.maxOf { it.second }).coerceAtLeast(1)
    val grid = C.line
    val animated by animateFloatAsState(1f, tween(500), label = "bars")
    val desc = data.joinToString(", ") { "${it.first}: ${format(it.second)}" }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = "Bar chart. $desc" },
        ) {
            listOf(0.5f, 1f).forEach { g ->
                val y = size.height - g * (size.height - 8f)
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            val slot = size.width / data.size
            data.forEachIndexed { i, (_, v, c) ->
                val bh = v.toFloat() / max * (size.height - 8f) * animated
                drawRoundRect(
                    color = c ?: fill,
                    topLeft = Offset(i * slot + slot * 0.18f, size.height - bh),
                    size = Size(slot * 0.64f, bh.coerceAtLeast(if (v > 0) 2f else 0f)),
                    cornerRadius = CornerRadius(3f),
                )
            }
        }
        Spacer(Modifier.height(Space.s2))
        Row(Modifier.fillMaxWidth()) {
            data.forEach { (label, _, _) ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    T(label, CadenceType.caption, C.textMuted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    color: Color = Color.Unspecified,
    firstLabel: String = "",
    lastLabel: String = "",
    format: (Float) -> String = { it.toInt().toString() },
) {
    if (values.size < 2) {
        EmptyState("Not enough recorded yet to draw a trend.", Ic.INFO)
        return
    }
    val stroke = if (color == Color.Unspecified) C.sky.deep else color
    val max = (values.max()).coerceAtLeast(1f)
    val grid = C.line
    val animated by animateFloatAsState(1f, tween(600), label = "line")

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics {
                    contentDescription =
                        "Line chart over ${values.size} points. Latest ${format(values.last())}, highest ${format(max)}."
                },
        ) {
            val top = 10f
            val usable = size.height - top - 10f
            listOf(0f, 0.5f, 1f).forEach { g ->
                val y = top + usable * g
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            fun pointAt(i: Int): Offset {
                val x = i.toFloat() / (values.size - 1) * size.width
                val y = top + usable - (values[i] / max) * usable * animated
                return Offset(x, y)
            }

            val line = Path()
            val area = Path()
            values.indices.forEach { i ->
                val p = pointAt(i)
                if (i == 0) {
                    line.moveTo(p.x, p.y)
                    area.moveTo(p.x, p.y)
                } else {
                    line.lineTo(p.x, p.y)
                    area.lineTo(p.x, p.y)
                }
            }
            area.lineTo(size.width, top + usable)
            area.lineTo(0f, top + usable)
            area.close()

            drawPath(
                area,
                Brush.verticalGradient(
                    listOf(stroke.copy(alpha = 0.22f), stroke.copy(alpha = 0f)),
                    startY = top,
                    endY = top + usable,
                ),
            )
            drawPath(line, stroke, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Spacer(Modifier.height(Space.s2))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            T(firstLabel, CadenceType.caption, C.textMuted)
            T(lastLabel, CadenceType.caption, C.textMuted)
        }
    }
}

@Composable
fun RankedBars(
    data: List<Triple<String, Int, Color>>,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() },
) {
    val max = (data.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        data.forEach { (label, value, color) ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    T(label, CadenceType.label)
                    T(format(value), CadenceType.label, C.textMuted)
                }
                Spacer(Modifier.height(Space.s1))
                CBar(value.toFloat() / max, color = color)
            }
        }
    }
}

/**
 * 18-week heatmap, one column per week. Darker means closer to target;
 * an empty day is the sunken surface so the grid still reads as a grid.
 */
@Composable
fun Heatmap(
    days: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    /** Which day sits in the top row — must match the rest of the app's week. */
    mondayFirst: Boolean = true,
    onPick: ((String) -> Unit)? = null,
) {
    if (days.isEmpty()) return
    val seq = C.seq
    val empty = C.surfaceSunken
    val scroll = rememberScrollState()

    // pad so each column starts on the week's first day
    val w0 = com.example.mytracker.domain.D.dow(days.first().first)
    val lead = if (mondayFirst) w0 - 1 else w0 % 7
    val padded: List<Pair<String, Int>?> = List(lead) { null } + days
    val columns = padded.chunked(7)

    Row(
        modifier.horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        columns.forEach { col ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) { r ->
                    val cell = col.getOrNull(r)
                    if (cell == null) {
                        Spacer(Modifier.size(12.dp))
                    } else {
                        val (date, level) = cell
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (level == 0) empty else seq[(level - 1).coerceIn(0, 4)])
                                .then(if (onPick != null) Modifier.clickable { onPick(date) } else Modifier)
                                .semantics {
                                    contentDescription = "${com.example.mytracker.domain.D.formatDay(date)}, level $level of 5"
                                },
                        )
                    }
                }
            }
        }
    }
}
