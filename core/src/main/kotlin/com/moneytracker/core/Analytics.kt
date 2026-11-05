package com.moneytracker.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class Granularity(val label: String) { DAY("Harian"), WEEK("Mingguan"), MONTH("Bulanan"), YEAR("Tahunan") }

data class Bucket(val start: LocalDate, val label: String, val income: Long, val expense: Long, val savings: Long) {
    val net: Long get() = income - expense
}

object Analytics {
    fun bucketStart(d: LocalDate, g: Granularity): LocalDate = when (g) {
        Granularity.DAY -> d
        Granularity.WEEK -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Granularity.MONTH -> d.withDayOfMonth(1)
        Granularity.YEAR -> d.withDayOfYear(1)
    }

    fun nextBucket(d: LocalDate, g: Granularity): LocalDate = when (g) {
        Granularity.DAY -> d.plusDays(1)
        Granularity.WEEK -> d.plusWeeks(1)
        Granularity.MONTH -> d.plusMonths(1)
        Granularity.YEAR -> d.plusYears(1)
    }

    fun label(d: LocalDate, g: Granularity): String = when (g) {
        Granularity.DAY -> Fmt.dayMonth(d)
        Granularity.WEEK -> Fmt.dayMonth(d)
        Granularity.MONTH -> Fmt.monthShortYear(YearMonth.from(d))
        Granularity.YEAR -> d.year.toString()
    }

    /** Default jumlah bucket yang ditampilkan per granularitas. */
    fun defaultCount(g: Granularity) = when (g) { Granularity.DAY -> 14; Granularity.WEEK -> 12; Granularity.MONTH -> 12; Granularity.YEAR -> 5 }

    fun buckets(txs: List<Tx>, g: Granularity, endDate: LocalDate, count: Int = defaultCount(g)): List<Bucket> {
        var start = bucketStart(endDate, g)
        repeat(count - 1) { start = when (g) {
            Granularity.DAY -> start.minusDays(1); Granularity.WEEK -> start.minusWeeks(1)
            Granularity.MONTH -> start.minusMonths(1); Granularity.YEAR -> start.minusYears(1)
        } }
        val starts = generateSequence(start) { nextBucket(it, g) }.take(count).toList()
        val inc = LongArray(count); val exp = LongArray(count); val sav = LongArray(count)
        val firstMillis = Fmt.toMillis(starts.first())
        val endMillis = Fmt.toMillis(nextBucket(starts.last(), g))
        val index = starts.withIndex().associate { it.value to it.index }
        for (tx in txs) {
            if (tx.dateTime < firstMillis || tx.dateTime >= endMillis) continue
            val i = index[bucketStart(Fmt.toDate(tx.dateTime), g)] ?: continue
            inc[i] += Ledger.incomeOf(tx); exp[i] += Ledger.expenseOf(tx); sav[i] += Ledger.savingsOf(tx)
        }
        return starts.mapIndexed { i, s -> Bucket(s, label(s, g), inc[i], exp[i], sav[i]) }
    }

    data class NeedWant(val need: Long, val want: Long, val other: Long) {
        val total get() = need + want + other
    }

    fun needWant(txs: List<Tx>, categories: Map<Long, CategoryInfo>): NeedWant {
        var n = 0L; var w = 0L; var o = 0L
        for (tx in txs) {
            if (tx.type != TxType.EXPENSE) continue
            // Nature subkategori; jika NONE gunakan nature induk
            var nature = tx.categoryId?.let { categories[it]?.nature } ?: Nature.NONE
            if (nature == Nature.NONE) nature = Ledger.rootOf(tx.categoryId, categories)?.let { categories[it]?.nature } ?: Nature.NONE
            when (nature) { Nature.NEED -> n += tx.amount; Nature.WANT -> w += tx.amount; Nature.NONE -> o += tx.amount }
        }
        return NeedWant(n, w, o)
    }

    /** Wallet paling sering dipakai: jumlah transaksi & total keluar. */
    fun walletUsage(txs: List<Tx>): List<Triple<Long, Int, Long>> =
        txs.groupBy { it.walletId }.map { (w, l) -> Triple(w, l.size, l.sumOf { Ledger.expenseOf(it) }) }
            .sortedByDescending { it.second }
}
