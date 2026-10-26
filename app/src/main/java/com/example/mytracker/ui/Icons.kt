package com.example.mytracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One monoline icon set, drawn from the same 24x24 path data as the web build so
 * both clients are visually identical. Rendered through Compose's PathParser, which
 * avoids pulling in the material-icons-extended artifact.
 */
object Ic {
    const val HOME = "M3 10.5 12 3l9 7.5M5.5 9.5V20h13V9.5"
    const val CHECK_SQUARE = "M4 6.5A2.5 2.5 0 0 1 6.5 4h11A2.5 2.5 0 0 1 20 6.5v11a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 17.5zM8.5 12l2.5 2.5L16 9.5"
    const val REPEAT = "M17 2.5 20.5 6 17 9.5M20.5 6H7.5A3.5 3.5 0 0 0 4 9.5v1M7 21.5 3.5 18 7 14.5M3.5 18h13a3.5 3.5 0 0 0 3.5-3.5v-1"
    const val TARGET = "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM12 16.5a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9ZM12 13.5a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Z"
    const val SPARK = "M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M18 6l-2.5 2.5M8.5 15.5 6 18"
    const val PLUS = "M12 5v14M5 12h14"
    const val MINUS = "M5 12h14"
    const val SEARCH = "M11 18.5a7.5 7.5 0 1 0 0-15 7.5 7.5 0 0 0 0 15ZM16.5 16.5 21 21"
    const val BELL = "M18 9a6 6 0 1 0-12 0c0 5-2 6.5-2 6.5h16S18 14 18 9ZM10.5 19a1.8 1.8 0 0 0 3 0"
    const val CHEVRON_RIGHT = "m9 5 7 7-7 7"
    const val CHEVRON_DOWN = "m5 9 7 7 7-7"
    const val PLAY = "M7 4.5v15l13-7.5z"
    const val PAUSE = "M8.5 4.5v15M15.5 4.5v15"
    const val X = "M6 6l12 12M18 6 6 18"
    const val CHECK = "m4.5 12.5 5 5 10-11"
    const val CLOCK = "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM12 7v5.5l3.5 2"
    const val CALENDAR = "M4 8.5A2.5 2.5 0 0 1 6.5 6h11A2.5 2.5 0 0 1 20 8.5v9a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 17.5zM4 10.5h16M8.5 3.5V7M15.5 3.5V7"
    const val TRASH = "M4.5 6.5h15M9.5 6.5V4h5v2.5M6.5 6.5 7.5 20h9l1-13.5M10 10.5v6M14 10.5v6"
    const val EDIT = "M4 20h4L19 9a2.5 2.5 0 0 0-3.5-3.5L4.5 16.5zM14.5 6.5 17.5 9.5"
    const val TREND_UP = "M3 17.5 9.5 11l4 4L21 7.5M21 7.5h-5M21 7.5v5"
    const val TREND_DOWN = "M3 7.5 9.5 14l4-4L21 16.5M21 16.5h-5M21 16.5v-5"
    const val FLAME = "M12 21c3.6 0 6-2.4 6-5.6 0-4.4-4.5-5.4-3.6-11.4-3 1.4-5.4 4.6-5.4 7.6 0 1.3-.9 2-1.8 1.2-.6-.5-.8-1.4-.8-2.2C5.1 12 6 13.6 6 15.4 6 18.6 8.4 21 12 21Z"
    const val BRAIN = "M9.5 4.5A3 3 0 0 0 6.5 7.5a3 3 0 0 0-1.5 5.3A3 3 0 0 0 7 18a3 3 0 0 0 5 1.2V4.9a3 3 0 0 0-2.5-.4ZM14.5 4.5A3 3 0 0 1 17.5 7.5a3 3 0 0 1 1.5 5.3A3 3 0 0 1 17 18a3 3 0 0 1-5 1.2"
    const val BOOK = "M5 4.5h8a4 4 0 0 1 4 4V20a3 3 0 0 0-3-3H5zM19 6.5V20"
    const val ACTIVITY = "M3 12.5h4l3-7 4 14 3-7h4"
    const val DROPLET = "M12 3.5c3.5 4 6 6.7 6 10a6 6 0 1 1-12 0c0-3.3 2.5-6 6-10Z"
    const val MOON = "M20 14.5A8.5 8.5 0 0 1 9.5 4 8.5 8.5 0 1 0 20 14.5Z"
    const val SUN = "M12 16.5a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9ZM12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5 5l1.5 1.5M17.5 17.5 19 19M19 5l-1.5 1.5M6.5 17.5 5 19"
    const val INBOX = "M4 13.5h4l1.5 3h5l1.5-3h4M4 13.5 6.5 5h11L20 13.5V18a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18z"
    const val ARROW_RIGHT = "M4 12h15M13.5 6.5 20 12l-6.5 5.5"
    const val INFO = "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM12 11v5.5M12 7.5h.01"
    const val FILTER = "M4 6.5h16M7 12h10M10 17.5h4"
    const val MORE = "M12 6.5h.01M12 12h.01M12 17.5h.01"
    /** A circle split down the middle: the "follow the system" theme state.
     *  Drawn as outline only, because CIcon strokes rather than fills. */
    const val CONTRAST = "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM12 3v18"
    const val SETTINGS = "M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM12 2.5l1.3 2.6 2.9-.4 .4 2.9 2.6 1.3-1.4 2.6 1.4 2.6-2.6 1.3-.4 2.9-2.9-.4L12 21.5l-1.3-2.6-2.9.4-.4-2.9-2.6-1.3L6.2 12 4.8 9.4l2.6-1.3.4-2.9 2.9.4z"
    const val CHEVRON_LEFT = "m15 5-7 7 7 7"
    const val FLAG = "M5.5 21V3.5h12l-2.5 4 2.5 4h-12"
    const val UNDO = "M4 9.5h10a5 5 0 0 1 0 10h-6M4 9.5 8 5.5M4 9.5l4 4"

    /** Habit icon keys are stored as strings, so they survive schema changes. */
    fun byKey(key: String): String = when (key) {
        "brain" -> BRAIN
        "book" -> BOOK
        "activity" -> ACTIVITY
        "droplet" -> DROPLET
        "moon" -> MOON
        "sun" -> SUN
        "flame" -> FLAME
        "check-square" -> CHECK_SQUARE
        else -> TARGET
    }

    val habitKeys = listOf("brain", "book", "activity", "droplet", "moon", "sun", "target", "flame", "check-square")
}

@Composable
fun CIcon(
    path: String,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = Color.Unspecified,
    strokeWidth: Float = 1.7f,
    filled: Boolean = false,
    contentDescription: String? = null,
) {
    val parsed = remember(path) { PathParser().parsePathString(path).toPath() }
    val color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint
    val desc = contentDescription
    Canvas(
        modifier = modifier
            .size(size)
            .then(if (desc != null) Modifier.semantics { this.contentDescription = desc } else Modifier)
    ) {
        // the path is authored in a 24x24 space; scaling here also scales the
        // stroke, which keeps the visual weight constant at every icon size
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            if (filled) {
                drawPath(parsed, color)
            } else {
                drawPath(
                    parsed,
                    color,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}
