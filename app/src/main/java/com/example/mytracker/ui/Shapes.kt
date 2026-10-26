package com.example.mytracker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mytracker.ui.theme.C
import com.example.mytracker.ui.theme.CadenceType
import com.example.mytracker.ui.theme.Radius
import com.example.mytracker.ui.theme.Space
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/* ------------------------------------------------------------------- dial */

/**
 * The thick ticked gauge: a ring of marks where the completed arc is drawn solid
 * and the rest stays faint, with a number in the middle. The ticks read as
 * progress on their own, so the dial does not depend on colour alone.
 */
@Composable
fun Dial(
    pct: Float,
    modifier: Modifier = Modifier,
    size: Dp = 146.dp,
    ticks: Int = 44,
    color: Color = Color.Unspecified,
    track: Color = Color.Unspecified,
    label: String? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val lit = if (color == Color.Unspecified) C.surface else color
    val dim = if (track == Color.Unspecified) C.textPrimary.copy(alpha = 0.14f) else track
    val animated by animateFloatAsState(pct.coerceIn(0f, 1f), spring(stiffness = 180f), label = "dial")
    Box(
        modifier
            .size(size)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.width / 2f
            val outer = r - 2.dp.toPx()
            val inner = r - 14.dp.toPx()
            val on = (ticks * animated).roundToInt()
            for (i in 0 until ticks) {
                // start at the top and sweep clockwise
                val a = (i.toFloat() / ticks) * 2f * PI.toFloat() - (PI.toFloat() / 2f)
                drawLine(
                    color = if (i < on) lit else dim,
                    start = Offset(r + cos(a) * inner, r + sin(a) * inner),
                    end = Offset(r + cos(a) * outer, r + sin(a) * outer),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        content?.invoke()
    }
}

/* -------------------------------------------------------------- metric row */

/** Four (or three) labelled numbers across a tinted card. */
@Composable
fun MetricRow(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
        items.forEach { (k, v) ->
            Column(Modifier.weight(1f)) {
                T(k, CadenceType.label, C.textPrimary.copy(alpha = 0.70f), maxLines = 1)
                Spacer(Modifier.height(Space.s1))
                T(v, CadenceType.metricM, C.textPrimary, maxLines = 1)
            }
        }
    }
}

/* -------------------------------------------------------------- week strip */

/** One day in the seven-day selector. */
data class StripDay(
    val date: String,
    val dow: String,
    val num: Int,
    val marked: Boolean,
)

/**
 * The week selector: a letter, a dot when the day has something recorded, and
 * the date in a pill that fills when selected.
 */
@Composable
fun WeekStrip(
    days: List<StripDay>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    today: String? = null,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        days.forEach { d ->
            val on = d.date == selected
            val numBg by animateColorAsState(
                if (on) C.sky.base else Color.Transparent, tween(180), label = "dayBg",
            )
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.full))
                    .background(C.surfaceAlt)
                    .clickable { onSelect(d.date) }
                    .semantics {
                        contentDescription =
                            "${d.dow} ${d.num}" + if (d.marked) ", has activity" else ""
                    }
                    .padding(vertical = Space.s3),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                T(d.dow, CadenceType.label, C.textPrimary, maxLines = 1)
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(RoundedCornerShape(Radius.full))
                        .background(if (d.marked) C.textPrimary else C.textMuted.copy(alpha = 0.4f)),
                )
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(Radius.full))
                        .background(numBg),
                    contentAlignment = Alignment.Center,
                ) {
                    T(
                        d.num.toString(),
                        CadenceType.label,
                        if (d.date == today) C.textPrimary else C.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/* --------------------------------------------------------------- minestat */

/** One row inside a [MiniStat]: a coloured tick beside a number and its unit. */
data class MiniRow(val n: String, val u: String, val color: Color)

/**
 * The compact stat card: a heading, an optional menu, and rows of a coloured
 * tick beside a number.
 */
@Composable
fun MiniStat(
    title: String,
    rows: List<MiniRow>,
    modifier: Modifier = Modifier,
    tinted: Boolean = false,
    onMenu: (() -> Unit)? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.xl))
            .background(if (tinted) C.sky.base else C.surfaceAlt)
            .padding(Space.s5),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(title, CadenceType.titleM, C.textPrimary, Modifier.weight(1f), maxLines = 1)
            if (onMenu != null) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(Radius.full))
                        .clickable { onMenu() },
                    contentAlignment = Alignment.Center,
                ) { CIcon(Ic.MORE, size = 18.dp, tint = C.textPrimary) }
            }
        }
        Spacer(Modifier.height(Space.s5))
        rows.forEachIndexed { i, r ->
            if (i > 0) Spacer(Modifier.height(Space.s4))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(5.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(Radius.full))
                        .background(r.color),
                )
                Spacer(Modifier.width(Space.s4))
                Column {
                    T(r.n, CadenceType.titleM, C.textPrimary, maxLines = 1)
                    T(r.u, CadenceType.label, C.textPrimary.copy(alpha = 0.62f), maxLines = 1)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ vbars */

/** The water-style chart: a thin stem per day with a dot on top. */
@Composable
fun VBars(
    data: List<Pair<String, Float>>,
    label: String,
    modifier: Modifier = Modifier,
    onTint: Boolean = false,
) {
    val max = (data.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(1f)
    val stem = if (onTint) Color.White.copy(alpha = 0.8f) else C.textPrimary.copy(alpha = 0.22f)
    val dot = if (onTint) Color.White else C.textPrimary.copy(alpha = 0.45f)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            data.forEach { (l, _) ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    T(l, CadenceType.caption, if (onTint) C.textPrimary.copy(alpha = 0.5f) else C.textMuted)
                }
            }
        }
        Spacer(Modifier.height(Space.s3))
        Row(
            Modifier.fillMaxWidth().height(108.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.s3),
            verticalAlignment = Alignment.Bottom,
        ) {
            data.forEach { (_, v) ->
                val h = (108f * (v / max)).coerceAtLeast(10f)
                Box(
                    Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = label },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .width(5.dp)
                            .height(h.dp)
                            .clip(RoundedCornerShape(Radius.full))
                            .background(stem),
                    )
                    Box(
                        Modifier
                            .padding(bottom = (h - 4).dp)
                            .size(9.dp)
                            .clip(RoundedCornerShape(Radius.full))
                            .background(dot),
                    )
                }
            }
        }
    }
}

/* ----------------------------------------------------------------- cta bar */

/** The wide butter bar with a round cap at each end. */
@Composable
fun CtaBar(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String = Ic.CHECK_SQUARE,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(Radius.full))
            .background(C.accent)
            .clickable { onClick() }
            .padding(Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cap(icon)
        Spacer(Modifier.weight(1f))
        T(text, CadenceType.titleM, C.onAccent, maxLines = 1)
        Spacer(Modifier.width(Space.s4))
        T(">>>", CadenceType.titleM, C.onAccent.copy(alpha = 0.55f), maxLines = 1)
        Spacer(Modifier.weight(1f))
        Cap(Ic.ARROW_RIGHT)
    }
}

@Composable
private fun Cap(icon: String) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(Radius.full))
            .background(Color.White.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) { CIcon(icon, size = 18.dp, tint = C.onAccent) }
}

/* ---------------------------------------------------------------- pill nav */

/** One destination in the floating bottom nav. */
data class NavItem(val id: String, val icon: String, val label: String)

/**
 * The floating white pill: one circle per destination, the active one filled in
 * butter. It replaces a conventional bottom bar, so it is placed over content
 * and the screen below it pads for its height.
 */
@Composable
fun PillNav(
    items: List<NavItem>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(Radius.full))
            .background(C.surface)
            .padding(Space.s3),
        horizontalArrangement = Arrangement.spacedBy(Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { it ->
            val on = it.id == selected
            val bg by animateColorAsState(if (on) C.accent else C.surface, tween(200), label = "navBg")
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(Radius.full))
                    .background(bg)
                    .clickable { onSelect(it.id) }
                    .semantics { contentDescription = it.label },
                contentAlignment = Alignment.Center,
            ) { CIcon(it.icon, size = 20.dp, tint = if (on) C.onAccent else C.textPrimary) }
        }
    }
}

/* ----------------------------------------------------------------- top bar */

/**
 * The persistent chrome: who you are on the left, utilities on the right. It is
 * the same row on every screen, so the page title lives in [PageHead].
 */
@Composable
fun TopBar(
    hi: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    /**
     * The user's name. The avatar shows its first letter, or a glyph when unset.
     * Deriving it from [hi] would show "G" for "Good evening".
     */
    initial: String? = null,
    onAvatar: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val letter = initial?.trim()?.take(1)?.uppercase().orEmpty()
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.s5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(Radius.full))
                .background(C.sky.soft)
                .then(if (onAvatar != null) Modifier.clickable { onAvatar() } else Modifier)
                .semantics {
                    contentDescription =
                        if (letter.isEmpty()) "Add your name in Settings" else "Settings"
                },
            contentAlignment = Alignment.Center,
        ) {
            if (letter.isEmpty()) CIcon(Ic.SETTINGS, size = 18.dp, tint = C.sky.ink)
            else T(letter, CadenceType.titleM, C.sky.ink)
        }
        Spacer(Modifier.width(Space.s4))
        Column(Modifier.weight(1f)) {
            T(hi, CadenceType.titleM, C.textPrimary, maxLines = 1)
            if (sub != null) T(sub, CadenceType.label, C.textMuted, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) { actions() }
    }
}

/* --------------------------------------------------------------- page head */

/** The big screen title under the top bar. */
@Composable
fun PageHead(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(bottom = Space.s6),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            T(title, CadenceType.pageTitle, C.textPrimary, maxLines = 1)
            if (subtitle != null) {
                Spacer(Modifier.height(Space.s1))
                T(subtitle, CadenceType.body, C.textMuted, maxLines = 2)
            }
        }
        action?.invoke()
    }
}

/* ------------------------------------------------------------------ people */

/** A row of faces, as on the shareable board in the reference. */
@Composable
fun People(names: List<String>, modifier: Modifier = Modifier, onAdd: (() -> Unit)? = null) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
        if (onAdd != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(Radius.full))
                        .background(C.surface)
                        .clickable { onAdd() }
                        .semantics { contentDescription = "Add a person" },
                    contentAlignment = Alignment.Center,
                ) { CIcon(Ic.PLUS, size = 18.dp, tint = C.textPrimary) }
            }
        }
        names.forEach { n ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(Radius.full))
                        .background(C.surfaceAlt),
                    contentAlignment = Alignment.Center,
                ) { T(n.take(1).uppercase(), CadenceType.titleM, C.textSecondary) }
                Spacer(Modifier.height(Space.s2))
                T(n, CadenceType.label, C.textPrimary, maxLines = 1)
            }
        }
    }
}

/* ----------------------------------------------------------------- toggle */

/** A switch: a drawn track and knob, with the whole row as the hit target. */
@Composable
fun CToggle(checked: Boolean, onChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier) {
    val track by animateColorAsState(if (checked) C.accent else C.surfaceSunken, tween(200), label = "swTrack")
    Box(
        modifier
            .width(50.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(Radius.full))
            .background(track)
            .clickable { onChange(!checked) }
            .semantics { contentDescription = label }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(Radius.full))
                .background(C.surface),
        )
    }
}

/* ------------------------------------------------------------ setting row */

/** A labelled row with a control on the right, separated by a hairline. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    onClick: (() -> Unit)? = null,
    control: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T(title, CadenceType.bodyStrong, C.textPrimary)
            if (hint != null) {
                Spacer(Modifier.height(2.dp))
                T(hint, CadenceType.caption, C.textMuted)
            }
        }
        if (control != null) {
            Spacer(Modifier.width(Space.s4))
            control()
        }
    }
}

/** The hairline between setting rows. */
@Composable
fun SettingDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
}
