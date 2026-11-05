package com.moneytracker.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class ReminderKind { DAILY, MONTHLY, STREAK }

/**
 * Pengaturan pengingat pencatatan.
 * - Harian: pada jam-jam tertentu setiap hari (mis. 12:00 dan 20:00).
 * - Bulanan: pada tanggal-tanggal tertentu (mis. 1, 15, 25) pada [monthlyTime].
 *   Tanggal yang tidak ada di bulan pendek (29–31) dipindah ke hari terakhir bulan itu.
 */
data class ReminderConfig(
    val dailyEnabled: Boolean = false,
    val dailyTimes: List<LocalTime> = emptyList(),
    val monthlyEnabled: Boolean = false,
    val monthlyDays: Set<Int> = emptySet(),
    val monthlyTime: LocalTime = LocalTime.of(9, 0),
    val skipIfRecorded: Boolean = true,
    /** Peringatan malam bila streak akan putus (hari ini belum mencatat). */
    val streakEnabled: Boolean = false,
    val streakTime: LocalTime = LocalTime.of(20, 30),
)

data class NextReminder(val at: LocalDateTime, val kinds: Set<ReminderKind>)

object ReminderSchedule {
    /** Apakah [date] adalah tanggal pengingat bulanan. */
    fun isMonthlyDay(date: LocalDate, days: Set<Int>): Boolean {
        val dom = date.dayOfMonth
        if (dom in days) return true
        val last = date.lengthOfMonth()
        return dom == last && days.any { it > last }
    }

    /** Jadwal pengingat berikutnya yang lebih besar dari [now] (presisi menit). */
    fun next(now: LocalDateTime, c: ReminderConfig): NextReminder? {
        val nowMin = now.withSecond(0).withNano(0)
        val candidates = mutableListOf<Pair<LocalDateTime, ReminderKind>>()
        if (c.dailyEnabled && c.dailyTimes.isNotEmpty()) {
            for (offset in 0L..1L) {
                val day = nowMin.toLocalDate().plusDays(offset)
                for (t in c.dailyTimes) {
                    val dt = day.atTime(t.withSecond(0).withNano(0))
                    if (dt.isAfter(nowMin)) candidates += dt to ReminderKind.DAILY
                }
            }
        }
        if (c.monthlyEnabled && c.monthlyDays.isNotEmpty()) {
            var day = nowMin.toLocalDate()
            repeat(63) {
                if (isMonthlyDay(day, c.monthlyDays)) {
                    val dt = day.atTime(c.monthlyTime.withSecond(0).withNano(0))
                    if (dt.isAfter(nowMin)) { candidates += dt to ReminderKind.MONTHLY; return@repeat }
                }
                day = day.plusDays(1)
            }
        }
        if (c.streakEnabled) {
            for (offset in 0L..1L) {
                val dt = nowMin.toLocalDate().plusDays(offset).atTime(c.streakTime.withSecond(0).withNano(0))
                if (dt.isAfter(nowMin)) { candidates += dt to ReminderKind.STREAK; break }
            }
        }
        val first = candidates.minOfOrNull { it.first } ?: return null
        return NextReminder(first, candidates.filter { it.first == first }.map { it.second }.toSet())
    }

    /**
     * Awal jendela "sudah mencatat?" untuk pengingat harian di [at]:
     * jam pengingat harian sebelumnya pada hari yang sama, atau awal hari bila ini yang pertama.
     */
    fun windowStart(at: LocalDateTime, dailyTimes: List<LocalTime>): LocalDateTime {
        val prev = dailyTimes.map { at.toLocalDate().atTime(it) }.filter { it.isBefore(at) }.maxOrNull()
        return prev ?: at.toLocalDate().atStartOfDay()
    }

    fun parseTimes(s: String): List<LocalTime> =
        s.split(',').mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }.distinct().sorted()

    fun formatTimes(times: List<LocalTime>): String = times.distinct().sorted().joinToString(",") { "%02d:%02d".format(it.hour, it.minute) }

    fun parseDays(s: String): Set<Int> = s.split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { d -> d in 1..31 } }.toSortedSet()
}
