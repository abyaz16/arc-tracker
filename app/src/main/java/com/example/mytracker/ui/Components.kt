package com.example.mytracker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mytracker.ui.theme.C
import com.example.mytracker.ui.theme.CadenceType
import com.example.mytracker.ui.theme.Radius
import com.example.mytracker.ui.theme.Space

/* ------------------------------------------------------------------- text */

@Composable
fun T(
    text: String,
    style: TextStyle = CadenceType.body,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) = Text(
    text = text,
    style = style,
    color = if (color == Color.Unspecified) C.textPrimary else color,
    modifier = modifier,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
)

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) =
    T(text, CadenceType.eyebrow, C.textPrimary, modifier)

/* ------------------------------------------------------------------- card */

/** The card tints, named by the meaning each one carries. */
object CardTone {
    const val DEFAULT = "default"
    const val QUIET = "quiet"
    const val SKY = "sky"
    const val SAGE = "sage"
    const val BUTTER = "butter"
    const val CORAL = "coral"
    const val LILAC = "lilac"
    const val ACCENT = "accent"
}

private val TINTED = listOf(
    CardTone.SKY, CardTone.SAGE, CardTone.BUTTER,
    CardTone.CORAL, CardTone.LILAC, CardTone.ACCENT,
)

@Composable
private fun toneBackground(variant: String): Color = when (variant) {
    CardTone.SKY -> C.sky.base
    CardTone.SAGE -> C.sage.base
    CardTone.BUTTER -> C.butter.base
    CardTone.CORAL -> C.coral.base
    CardTone.LILAC -> C.lilac.base
    CardTone.ACCENT -> C.accent
    CardTone.QUIET -> C.surfaceAlt
    else -> C.surface
}

/** The stacked-paper pull tab that sits on a card's edge. */
@Composable
private fun Notch() {
    Box(
        Modifier
            .width(52.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(Radius.full))
            .background(C.textPrimary.copy(alpha = 0.12f)),
    )
}

/**
 * Card anatomy, formalised: icon chip + title + action on one row, then the
 * content. A card is a soft slab — generous radius, no border, and either white
 * or one of the tints. Text stays near-black on every tint, so colour never
 * costs legibility.
 */
@Composable
fun CCard(
    modifier: Modifier = Modifier,
    variant: String = CardTone.DEFAULT,
    icon: String? = null,
    title: String? = null,
    actionIcon: String? = null,
    onAction: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false,
    /** "top", "bottom" or null */
    notch: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val bg = toneBackground(variant)
    val fg = C.textPrimary
    // on a tint the inner chips are white; on white they are the next step down
    val chipBg = if (variant in TINTED) C.surface else C.surfaceAlt
    val pad = if (compact) Space.s5 else Space.s6

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(
                start = pad,
                end = pad,
                top = if (notch == "top") Space.s7 else pad,
                bottom = if (notch == "bottom") Space.s7 else pad,
            ),
    ) {
        if (notch == "top") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Notch() }
            Spacer(Modifier.height(Space.s4))
        }
        if (icon != null || title != null || actionIcon != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(Radius.full))
                            .background(chipBg),
                        contentAlignment = Alignment.Center,
                    ) { CIcon(icon, size = 18.dp, tint = fg) }
                    Spacer(Modifier.width(Space.s4))
                }
                if (title != null) {
                    T(title, CadenceType.titleM, fg, Modifier.weight(1f), maxLines = 1)
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (actionIcon != null) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(Radius.full))
                            .background(chipBg)
                            .clickable { onAction?.invoke() },
                        contentAlignment = Alignment.Center,
                    ) { CIcon(actionIcon, size = 18.dp, tint = fg) }
                }
            }
            Spacer(Modifier.height(Space.s5))
        }
        content()
        if (notch == "bottom") {
            Spacer(Modifier.height(Space.s4))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Notch() }
        }
    }
}

/* --------------------------------------------------------------- stat tile */

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: String? = null,
    onClick: (() -> Unit)? = null,
    foot: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.xl))
            .background(C.surface)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(Space.s5),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            T(label, CadenceType.body, C.textPrimary, Modifier.weight(1f), maxLines = 2)
            if (icon != null) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(Radius.full))
                        .background(C.surfaceAlt),
                    contentAlignment = Alignment.Center,
                ) { CIcon(icon, size = 16.dp, tint = C.textPrimary) }
            }
        }
        Spacer(Modifier.height(Space.s3))
        Row(verticalAlignment = Alignment.Bottom) {
            T(value, CadenceType.metricL)
            if (unit != null) {
                Spacer(Modifier.width(Space.s2))
                T(unit, CadenceType.titleM, C.textMuted, Modifier.padding(bottom = 3.dp))
            }
        }
        if (foot != null) {
            Spacer(Modifier.height(Space.s3))
            foot()
        }
    }
}

/* ----------------------------------------------------------------- buttons */

@Composable
fun CButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: String = "primary",
    icon: String? = null,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    val bg = when (variant) {
        "primary" -> C.accent
        "secondary" -> C.surfaceAlt
        "danger" -> C.rose.soft
        // "onAccent" sits on top of a tinted card, so it inverts to plain white
        "onAccent" -> C.surface
        else -> Color.Transparent
    }
    val fg = when (variant) {
        "danger" -> C.overdue
        "ghost" -> C.textSecondary
        else -> C.textPrimary
    }
    Row(
        modifier
            .height(if (small) 42.dp else 54.dp)
            .clip(RoundedCornerShape(Radius.full))
            .background(if (enabled) bg else bg.copy(alpha = 0.45f))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = if (small) Space.s5 else Space.s6),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            CIcon(icon, size = if (small) 16.dp else 18.dp, tint = if (enabled) fg else fg.copy(alpha = 0.5f))
            Spacer(Modifier.width(Space.s3))
        }
        T(text, if (small) CadenceType.label else CadenceType.titleM, if (enabled) fg else fg.copy(alpha = 0.5f), maxLines = 1)
    }
}

@Composable
fun IconBtn(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Boolean = false,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(Radius.full))
            .background(C.surface)
            .clickable { onClick() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CIcon(icon, size = 18.dp, tint = C.textPrimary)
        if (dot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 8.dp)
                    .size(9.dp)
                    .clip(RoundedCornerShape(Radius.full))
                    .background(C.notify),
            )
        }
    }
}

@Composable
fun CChip(
    text: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
) {
    val bg by animateColorAsState(if (selected) C.accent else C.surfaceAlt, tween(140), label = "chipBg")
    val fg by animateColorAsState(C.textPrimary, tween(140), label = "chipFg")
    Row(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(Radius.full))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(Radius.full)).background(dotColor))
            Spacer(Modifier.width(Space.s2))
        }
        T(text, CadenceType.label, fg, maxLines = 1)
    }
}

/* --------------------------------------------------------------- segmented */

@Composable
fun Segmented(
    options: List<Pair<String, String>>,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.full))
            .background(C.surface)
            .padding(4.dp),
    ) {
        options.forEach { (key, label) ->
            val on = key == value
            val bg by animateColorAsState(if (on) C.accent else Color.Transparent, tween(200), label = "segBg")
            val fg by animateColorAsState(if (on) C.onAccent else C.textMuted, tween(200), label = "segFg")
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(Radius.full))
                    .background(bg)
                    .clickable { onChange(key) },
                contentAlignment = Alignment.Center,
            ) { T(label, CadenceType.label, fg, maxLines = 1) }
        }
    }
}

/* ---------------------------------------------------------------- progress */

@Composable
fun CBar(pct: Float, modifier: Modifier = Modifier, color: Color = Color.Unspecified, pace: Float? = null) {
    val fill = if (color == Color.Unspecified) C.sky.deep else color
    val animated by animateFloatAsState(pct.coerceIn(0f, 1f), spring(stiffness = 220f), label = "bar")
    val track = C.surfaceSunken
    val marker = C.textPrimary
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val r = size.height / 2
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        if (animated > 0f) {
            drawRoundRect(
                fill,
                size = androidx.compose.ui.geometry.Size(size.width * animated, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
            )
        }
        // where an even pace would put you by now
        if (pace != null && pace > 0f && pace < 1f) {
            drawRoundRect(
                marker.copy(alpha = 0.7f),
                topLeft = androidx.compose.ui.geometry.Offset(size.width * pace - 1.dp.toPx(), -3.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height + 6.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
            )
        }
    }
}

/** Vertical capsule with an inline value pill — the reference's strongest idea. */
@Composable
fun Capsule(pct: Float, label: String, caption: String, color: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(pct.coerceIn(0f, 1f), spring(stiffness = 200f), label = "capsule")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(56.dp)
                .height(124.dp)
                .clip(RoundedCornerShape(Radius.full))
                .background(C.surfaceAlt),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((124 * animated).dp)
                    .clip(RoundedCornerShape(Radius.full))
                    .background(color),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = ((124 * animated) - 30).coerceIn(6f, 86f).dp)
                    .clip(RoundedCornerShape(Radius.full))
                    .background(C.surface)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) { T(label, CadenceType.label) }
        }
        Spacer(Modifier.height(Space.s4))
        T(caption, CadenceType.body, C.textPrimary, maxLines = 1)
    }
}

@Composable
fun Ring(
    pct: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    stroke: Dp = 10.dp,
    color: Color = Color.Unspecified,
    label: String? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val fill = if (color == Color.Unspecified) C.coral.deep else color
    val animated by animateFloatAsState(pct.coerceIn(0f, 1f), spring(stiffness = 180f), label = "ring")
    val track = C.surfaceAlt
    Box(
        modifier
            .size(size)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw),
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                drawArc(
                    color = fill,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw),
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
            }
        }
        content?.invoke()
    }
}

/* --------------------------------------------------------------- checkbox */

@Composable
fun CCheckbox(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, label: String = "") {
    val bg by animateColorAsState(if (checked) C.sage.deep else Color.Transparent, tween(140), label = "cbBg")
    val border by animateColorAsState(if (checked) C.sage.deep else C.line, tween(140), label = "cbBorder")
    Box(
        modifier
            .size(26.dp)
            .clip(RoundedCornerShape(Radius.full))
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(Radius.full))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onToggle() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) CIcon(Ic.CHECK, size = 13.dp, tint = Color.White, strokeWidth = 2.6f)
    }
}

/* ------------------------------------------------------------ empty state */

@Composable
fun EmptyState(text: String, icon: String = Ic.INBOX, action: @Composable (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = Space.s9, horizontal = Space.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        CIcon(icon, size = 28.dp, tint = C.textMuted)
        T(text, CadenceType.body.merge(CadenceType.centered), C.textMuted)
        action?.invoke()
    }
}

/* ----------------------------------------------------------------- badge */

@Composable
fun Badge(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(Radius.full))
            .background(bg)
            .padding(horizontal = Space.s3, vertical = 2.dp),
    ) { T(text, CadenceType.caption, fg) }
}
