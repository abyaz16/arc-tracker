package com.example.mytracker.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Nine styles, not fifteen. The hierarchy comes from an extreme scale jump —
 * a 44sp numeral sitting next to an 11sp label, with very little in between —
 * which is what makes a dense metrics screen readable at a glance.
 *
 * Every metric style uses tabular figures so numbers don't jitter as they animate.
 */
object CadenceType {
    private val f = FontFamily.Default

    /** The screen title. Bigger and heavier than any card heading, on purpose. */
    val pageTitle = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-1.0).sp)

    val metricXL = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 46.sp, lineHeight = 48.sp, letterSpacing = (-1.3).sp)
    val metricL = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.9).sp)
    val metricM = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.4).sp)
    val titleL = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp)
    val titleM = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp)
    val body = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodyStrong = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp)
    val label = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp)
    val caption = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp)
    /** A section heading: plain ink, not a tiny grey all-caps whisper. */
    val eyebrow = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp)
    val centered = TextStyle(textAlign = TextAlign.Center)
}
