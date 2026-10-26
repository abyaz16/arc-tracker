package com.example.mytracker.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The only place in the app that does calendar arithmetic.
 *
 * Rule: a "day" is a local yyyy-MM-dd String. An instant is epoch millis.
 * java.time handles DST correctly, which hand-rolled millisecond maths does not.
 */
object D {

    fun today(): String = LocalDate.now().toString()

    fun parse(iso: String): LocalDate = runCatching { LocalDate.parse(iso) }.getOrDefault(LocalDate.now())

    fun addDays(iso: String, n: Long): String = parse(iso).plusDays(n).toString()

    fun diffDays(a: String, b: String): Int = ChronoUnit.DAYS.between(parse(b), parse(a)).toInt()

    /** 1 = Monday .. 7 = Sunday */
    fun dow(iso: String): Int = parse(iso).dayOfWeek.value

    fun startOfWeek(iso: String, mondayFirst: Boolean = true): String {
        val back = if (mondayFirst) dow(iso) - 1 else dow(iso) % 7
        return parse(iso).minusDays(back.toLong()).toString()
    }

    /** Weekday numbers in the user's display order, e.g. [1..7] for Monday-first. */
    fun weekOrder(mondayFirst: Boolean): List<Int> =
        (0..6).map { if (mondayFirst) it + 1 else (it + 6) % 7 + 1 }

    fun startOfMonth(iso: String): String = parse(iso).withDayOfMonth(1).toString()

    fun endOfMonth(iso: String): String = parse(iso).withDayOfMonth(parse(iso).lengthOfMonth()).toString()

    fun startOfYear(iso: String): String = parse(iso).withDayOfYear(1).toString()

    fun endOfYear(iso: String): String = parse(iso).withDayOfYear(parse(iso).lengthOfYear()).toString()

    fun range(from: String, to: String): List<String> {
        val out = ArrayList<String>()
        var cur = parse(from)
        val end = parse(to)
        var guard = 0
        while (!cur.isAfter(end) && guard++ < 1200) {
            out.add(cur.toString())
            cur = cur.plusDays(1)
        }
        return out
    }

    fun epochOf(iso: String): Long =
        parse(iso).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun isoOfEpoch(ms: Long): String =
        java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    fun hourOfEpoch(ms: Long): Int =
        java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).hour

    private val DOW_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val DOW_LETTER = listOf("M", "T", "W", "T", "F", "S", "S")
    private val MONTHS = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    fun dowLetter(d: Int): String = DOW_LETTER[(d - 1).coerceIn(0, 6)]

    fun monthName(m: Int): String = MONTHS[(m - 1).coerceIn(0, 11)]

    fun formatDay(iso: String): String {
        val d = parse(iso)
        return "${DOW_SHORT[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${MONTHS[d.monthValue - 1].take(3)}"
    }

    fun formatLongDay(iso: String): String {
        val d = parse(iso)
        return "${DOW_SHORT[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
    }

    fun relativeDay(iso: String, base: String = today()): String = when (val n = diffDays(iso, base)) {
        0 -> "Today"
        1 -> "Tomorrow"
        -1 -> "Yesterday"
        else -> if (n < 0) "${-n} days overdue" else if (n < 7) DOW_SHORT[dow(iso) - 1] else formatDay(iso)
    }

    fun formatTime(hhmm: String): String = runCatching {
        val t = LocalTime.parse(hhmm)
        val ampm = if (t.hour >= 12) "pm" else "am"
        val h12 = if (t.hour % 12 == 0) 12 else t.hour % 12
        if (t.minute == 0) "$h12$ampm" else "$h12:${t.minute.toString().padStart(2, '0')}$ampm"
    }.getOrDefault(hhmm)

    fun formatDuration(mins: Int): String {
        if (mins <= 0) return "0m"
        val h = mins / 60
        val m = mins % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    fun clock(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** "HH:mm" for right now, so time-of-day comparisons stay plain string compares. */
    fun nowTime(now: LocalDateTime = LocalDateTime.now()): String =
        "%02d:%02d".format(now.hour, now.minute)

    /**
     * Greets against the user's own day rather than a clock baked into the code:
     * before they are normally up it reads as still up, after wind-down as
     * winding down.
     */
    /**
     * The whole greeting line. With a name it is "Good evening, Sam"; without
     * one it is just "Good evening" — never "Good evening, there", which reads
     * as the app not knowing who you are and saying so awkwardly.
     */
    fun greetingLine(name: String, wake: String, sleep: String, now: String = nowTime()): String {
        val g = greeting(wake, sleep, now)
        val n = name.trim()
        return if (n.isEmpty()) g else "$g, $n"
    }

    fun greeting(wake: String = "05:00", sleep: String = "22:00", now: String = nowTime()): String = when {
        now < wake -> "Still up"
        now >= sleep -> "Winding down"
        now.substring(0, 2).toInt() < 12 -> "Good morning"
        now.substring(0, 2).toInt() < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}
