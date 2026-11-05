package com.moneytracker.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Format Rupiah dan tanggal Indonesia. Semua uang disimpan sebagai Long (Rupiah bulat). */
object Fmt {
    val ID: Locale = Locale.forLanguageTag("id-ID")
    var zone: ZoneId = ZoneId.systemDefault()

    private val MONTHS = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember",
    )
    private val MONTHS_SHORT = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
    private val DAYS = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")

    fun groupDigits(value: Long): String {
        val s = abs(value).toString()
        val sb = StringBuilder()
        s.forEachIndexed { i, c ->
            if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return sb.toString()
    }

    /** Rp1.250.000 / -Rp50.000 */
    fun rupiah(value: Long, withSign: Boolean = false): String {
        val sign = when {
            value < 0 -> "-"
            withSign && value > 0 -> "+"
            else -> ""
        }
        return "${sign}Rp${groupDigits(value)}"
    }

    /** Rp1,2 jt / Rp850 rb — untuk label grafik. */
    fun rupiahCompact(value: Long): String {
        val a = abs(value).toDouble()
        val sign = if (value < 0) "-" else ""
        return when {
            a >= 1_000_000_000 -> sign + "Rp" + decimal(a / 1_000_000_000, 1) + " M"
            a >= 1_000_000 -> sign + "Rp" + decimal(a / 1_000_000, 1) + " jt"
            a >= 1_000 -> sign + "Rp" + decimal(a / 1_000, 0) + " rb"
            else -> sign + "Rp" + a.toLong()
        }
    }

    /** 12,5 (koma desimal ala Indonesia), tanpa nol berlebih. */
    fun decimal(value: Double, digits: Int = 1): String {
        if (value.isNaN() || value.isInfinite()) return "-"
        val factor = Math.pow(10.0, digits.toDouble())
        val rounded = (value * factor).roundToLong() / factor
        val str = if (digits == 0) rounded.roundToLong().toString() else String.format(Locale.US, "%.${digits}f", rounded)
        val trimmed = if (str.contains('.')) str.trimEnd('0').trimEnd('.') else str
        val parts = trimmed.split('.')
        val intPart = parts[0].toLongOrNull()?.let { (if (it < 0 || trimmed.startsWith("-")) "-" else "") + groupDigits(it) } ?: parts[0]
        return if (parts.size > 1) "$intPart,${parts[1]}" else intPart
    }

    fun percent(value: Double, digits: Int = 0): String = decimal(value * 100, digits) + "%"

    fun liters(ml: Long): String = decimal(ml / 1000.0, 2) + " L"

    fun toDate(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    fun toDateTime(epochMillis: Long): LocalDateTime = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()
    fun toMillis(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
    fun toMillis(dt: LocalDateTime): Long = dt.atZone(zone).toInstant().toEpochMilli()

    /** 1 November 2026 */
    fun date(d: LocalDate): String = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}"
    fun date(epochMillis: Long): String = date(toDate(epochMillis))

    /** 1 Nov 2026 */
    fun dateShort(d: LocalDate): String = "${d.dayOfMonth} ${MONTHS_SHORT[d.monthValue - 1]} ${d.year}"
    fun dateShort(epochMillis: Long): String = dateShort(toDate(epochMillis))

    /** 1 Nov */
    fun dayMonth(d: LocalDate): String = "${d.dayOfMonth} ${MONTHS_SHORT[d.monthValue - 1]}"

    /** Senin, 1 November 2026 */
    fun dateWithDay(d: LocalDate): String = "${DAYS[d.dayOfWeek.value - 1]}, ${date(d)}"

    fun time(epochMillis: Long): String = toDateTime(epochMillis).format(DateTimeFormatter.ofPattern("HH:mm"))

    fun dateTime(epochMillis: Long): String = dateShort(epochMillis) + ", " + time(epochMillis)

    /** November 2026 */
    fun month(ym: YearMonth): String = "${MONTHS[ym.monthValue - 1]} ${ym.year}"
    fun monthShort(ym: YearMonth): String = MONTHS_SHORT[ym.monthValue - 1]
    fun monthShortYear(ym: YearMonth): String = "${MONTHS_SHORT[ym.monthValue - 1]} ${ym.year % 100}"

    /**
     * Parsing nominal yang diketik pengguna atau berasal dari CSV bank.
     * Mendukung: "1.250.000", "1.250.000,50", "1,250,000.50", "Rp 50.000", "-50.000", "(50.000)", "50000 CR/DB".
     * Hasil dibulatkan ke Rupiah.
     */
    fun parseAmount(raw: String): Long? {
        var s = raw.trim().uppercase(Locale.ROOT)
        if (s.isEmpty()) return null
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length - 1) }
        if (s.endsWith("DB") || s.endsWith(" D")) negative = true
        s = s.replace("RP", "").replace("IDR", "").replace("CR", "").replace("DB", "").replace(" ", "").replace(" ", "")
        if (s.endsWith("D")) { negative = true; s = s.dropLast(1) }
        if (s.startsWith("-")) { negative = true; s = s.substring(1) }
        if (s.startsWith("+")) s = s.substring(1)
        if (s.isEmpty() || s.any { !(it.isDigit() || it == '.' || it == ',') }) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val normalized = when {
            lastDot >= 0 && lastComma >= 0 ->
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            lastComma >= 0 -> {
                val decimals = s.length - lastComma - 1
                if (s.count { it == ',' } == 1 && decimals in 1..2) s.replace(',', '.') else s.replace(",", "")
            }
            lastDot >= 0 -> {
                val decimals = s.length - lastDot - 1
                // "12.000" atau "1.250.000" = pemisah ribuan; "12.5" = desimal
                if (s.count { it == '.' } == 1 && decimals in 1..2) s else s.replace(".", "")
            }
            else -> s
        }
        val value = normalized.toBigDecimalOrNull() ?: return null
        val rounded = value.setScale(0, java.math.RoundingMode.HALF_UP).toLong()
        return if (negative) -rounded else rounded
    }

    /** Parsing angka desimal (liter, km/L) dengan koma atau titik. */
    fun parseDecimal(raw: String): Double? {
        val s = raw.trim().replace(" ", "")
        if (s.isEmpty()) return null
        return if (s.contains(',')) s.replace(".", "").replace(',', '.').toDoubleOrNull() else s.toDoubleOrNull()
    }
}
