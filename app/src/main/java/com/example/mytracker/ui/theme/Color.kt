package com.example.mytracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Cadence palette. Identical values to the web build's design tokens, so the two
 * clients are recognisably the same product.
 *
 * The system is soft-pastel: a warm off-white canvas, white cards, and four tinted
 * card families that carry meaning rather than decoration. Text on every tint is the
 * same near-black, so tinting a card never costs legibility.
 */

/**
 * One colour family. [base] fills a card, [soft] fills a track or an inactive state,
 * [deep] draws a line or an arc on top of one, and [ink] is text on [soft] where the
 * usual near-black would read as flat.
 */
data class Tint(
    val base: Color,
    val soft: Color,
    val deep: Color,
    val ink: Color,
)

data class CadenceColors(
    val canvas: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceSunken: Color,
    val line: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    /** The thing you are looking at now. */
    val sky: Tint,
    /** Settled, kept, done. */
    val sage: Tint,
    /** The one action. Butter is also the accent. */
    val butter: Tint,
    /** Attention, without alarm. */
    val coral: Tint,
    val lilac: Tint,
    val rose: Tint,
    val accent: Color,
    val accentPressed: Color,
    val accentSubtle: Color,
    val onAccent: Color,
    /**
     * A toast paints itself in [textPrimary], so its accent has to read against
     * the INVERTED surface — which flips between the themes, unlike [accent].
     */
    val accentOnInverse: Color,
    /** Outlines need contrast the pastels cannot give, so focus is ink. */
    val focus: Color,
    val notify: Color,
    val success: Color,
    val successSubtle: Color,
    val attention: Color,
    val attentionSubtle: Color,
    val overdue: Color,
    val overdueSubtle: Color,
    val info: Color,
    val infoSubtle: Color,
    /** Categorical ramp. Index is stable across the whole app: cat[0] always means the same thing. */
    val cat: List<Color>,
    /** Sequential ramp for heatmaps, light to dark. */
    val seq: List<Color>,
    val isDark: Boolean,
)

val LightColors = CadenceColors(
    canvas = Color(0xFFF1F0EE),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF7F6F4),
    surfaceSunken = Color(0xFFECEBE8),
    line = Color(0xFFE9E7E3),
    textPrimary = Color(0xFF17171A),
    textSecondary = Color(0xFF5C5C63),
    // dark enough to clear 4.5:1 on the canvas — caption text is normal-size
    // text, so it does not get the large-text exemption
    textMuted = Color(0xFF6A6A74),
    sky = Tint(Color(0xFFBFE4EE), Color(0xFFE6F4F8), Color(0xFF6FC1D6), Color(0xFF1C6578)),
    sage = Tint(Color(0xFFD5E9C4), Color(0xFFECF5E3), Color(0xFF9BCC7C), Color(0xFF3D6A2A)),
    butter = Tint(Color(0xFFFBE27C), Color(0xFFFEF6D7), Color(0xFFF0C73C), Color(0xFF806008)),
    coral = Tint(Color(0xFFF4A97E), Color(0xFFFDECE1), Color(0xFFEA8B57), Color(0xFF9C4E24)),
    lilac = Tint(Color(0xFFCBB6E8), Color(0xFFF0E8F9), Color(0xFFA98BD1), Color(0xFF5F3F87)),
    rose = Tint(Color(0xFFF3918E), Color(0xFFFDE7E6), Color(0xFFE9706C), Color(0xFFA23C39)),
    accent = Color(0xFFFBE27C),
    accentPressed = Color(0xFFF5D65A),
    accentSubtle = Color(0xFFFEF6D7),
    onAccent = Color(0xFF17171A),
    accentOnInverse = Color(0xFFF0C73C),
    focus = Color(0xFF17171A),
    notify = Color(0xFFF2726F),
    success = Color(0xFF3D6A2A),
    successSubtle = Color(0xFFECF5E3),
    attention = Color(0xFF806008),
    attentionSubtle = Color(0xFFFEF6D7),
    overdue = Color(0xFFA23C39),
    overdueSubtle = Color(0xFFFDE7E6),
    info = Color(0xFF1C6578),
    infoSubtle = Color(0xFFE6F4F8),
    cat = listOf(
        Color(0xFF7FC9DC), Color(0xFFA9D389), Color(0xFFF3CF5C),
        Color(0xFFF2A177), Color(0xFFBB9EDE), Color(0xFFF0908D),
    ),
    seq = listOf(
        Color(0xFFEEF6EA), Color(0xFFD9ECC9), Color(0xFFBCDDA5),
        Color(0xFF9BCC7C), Color(0xFF74AE57),
    ),
    isDark = false,
)

val DarkColors = CadenceColors(
    canvas = Color(0xFF121214),
    surface = Color(0xFF1C1C1F),
    surfaceAlt = Color(0xFF232327),
    surfaceSunken = Color(0xFF141416),
    line = Color(0xFF2E2E33),
    textPrimary = Color(0xFFF4F3F1),
    textSecondary = Color(0xFFA8A8B0),
    textMuted = Color(0xFF8E8E97),
    // the tints keep their hue but drop to a depth near-white text can sit on
    sky = Tint(Color(0xFF1F4D59), Color(0xFF15313A), Color(0xFF67B8CD), Color(0xFFA8DCED)),
    sage = Tint(Color(0xFF2F4A25), Color(0xFF1E2F18), Color(0xFF8FC272), Color(0xFFC6E3B2)),
    butter = Tint(Color(0xFF5A4A12), Color(0xFF3A300C), Color(0xFFE8C349), Color(0xFFF6E2A0)),
    coral = Tint(Color(0xFF5E3521), Color(0xFF3D2216), Color(0xFFE08A58), Color(0xFFF6C6A8)),
    lilac = Tint(Color(0xFF40305A), Color(0xFF2A1F3C), Color(0xFFA98BD1), Color(0xFFD8C6EF)),
    rose = Tint(Color(0xFF5C2B2A), Color(0xFF3B1C1B), Color(0xFFE3736F), Color(0xFFF5BAB8)),
    accent = Color(0xFFE8C349),
    accentPressed = Color(0xFFD4B03A),
    accentSubtle = Color(0xFF3A300C),
    onAccent = Color(0xFF17171A),
    // in dark the inverted surface is near-white, so this goes dark
    accentOnInverse = Color(0xFF806008),
    focus = Color(0xFFF4F3F1),
    notify = Color(0xFFF2726F),
    success = Color(0xFFC6E3B2),
    successSubtle = Color(0xFF1E2F18),
    attention = Color(0xFFF6E2A0),
    attentionSubtle = Color(0xFF3A300C),
    overdue = Color(0xFFF5BAB8),
    overdueSubtle = Color(0xFF3B1C1B),
    info = Color(0xFFA8DCED),
    infoSubtle = Color(0xFF15313A),
    cat = listOf(
        Color(0xFF67B8CD), Color(0xFF8FC272), Color(0xFFE8C349),
        Color(0xFFE08A58), Color(0xFFA98BD1), Color(0xFFE3736F),
    ),
    seq = listOf(
        Color(0xFF1C2B17), Color(0xFF2C4522), Color(0xFF41632F),
        Color(0xFF5D8742), Color(0xFF8FC272),
    ),
    isDark = true,
)

/** Resolve a stored colour key ("c1".."c6") to the current theme's ramp. */
fun CadenceColors.byKey(key: String): Color {
    val i = key.removePrefix("c").toIntOrNull()?.minus(1) ?: 0
    return cat[i.coerceIn(0, cat.lastIndex)]
}
