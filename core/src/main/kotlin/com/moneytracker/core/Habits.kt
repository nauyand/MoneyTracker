package com.moneytracker.core

import java.time.LocalDate
import java.time.YearMonth

/** Streak = jumlah hari berturut-turut pengguna mencatat minimal satu transaksi. */
data class StreakInfo(
    val current: Int,
    val best: Int,
    val recordedToday: Boolean,
    /** 7 hari terakhir (lama → hari ini): apakah ada catatan. */
    val last7: List<Pair<LocalDate, Boolean>>,
) {
    /** Streak masih hidup tapi hari ini belum mencatat — akan putus tengah malam. */
    val atRisk: Boolean get() = current > 0 && !recordedToday
    val nextMilestone: Int? get() = Streaks.MILESTONES.firstOrNull { it > current }
}

object Streaks {
    val MILESTONES = listOf(3, 7, 14, 21, 30, 50, 75, 100, 150, 200, 365)

    fun isMilestone(n: Int) = n in MILESTONES

    /**
     * @param recordDays tanggal-tanggal saat pengguna MENCATAT (tanggal dibuatnya catatan, bukan tanggal transaksinya),
     * agar streak mengukur kebiasaan mencatat. Transaksi impor/otomatis sebaiknya tidak disertakan.
     */
    fun compute(recordDays: Set<LocalDate>, today: LocalDate): StreakInfo {
        val recordedToday = today in recordDays
        var d = if (recordedToday) today else today.minusDays(1)
        var current = 0
        while (d in recordDays) { current++; d = d.minusDays(1) }
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (day in recordDays.filter { !it.isAfter(today) }.sorted()) {
            run = if (prev != null && prev.plusDays(1) == day) run + 1 else 1
            best = maxOf(best, run); prev = day
        }
        val last7 = (6 downTo 0).map { today.minusDays(it.toLong()).let { x -> x to (x in recordDays) } }
        return StreakInfo(current, maxOf(best, current), recordedToday, last7)
    }
}

/** Rekap bulanan bergaya "Wrapped". Semua angka dari transaksi aktual bulan tersebut. */
data class MonthWrap(
    val month: YearMonth,
    val income: Long,
    val expense: Long,
    val savings: Long,
    val txCount: Int,
    val prevExpense: Long?,
    val topCategory: Pair<Long?, Long>?,
    val biggest: Tx?,
    val priciestDay: Pair<LocalDate, Long>?,
    /** Hari paling hemat yang tetap ada pengeluaran. */
    val thriftiestDay: Pair<LocalDate, Long>?,
    val noSpendDays: Int,
    val daysCounted: Int,
    val topMerchant: Triple<String, Int, Long>?,
    val topWalletId: Long?,
    val fuelCost: Long,
    val fuelLiters: Double,
    val fuelFills: Int,
    val prevFuelCost: Long?,
) {
    val savingsRate: Double? get() = if (income > 0) (income - expense).toDouble() / income else null
    val avgDaily: Long get() = if (daysCounted > 0) expense / daysCounted else 0
    val hasData: Boolean get() = txCount > 0
}

object Wrapped {
    /**
     * @param fuel pasangan (epochMillis, volumeMl, cost) pengisian BBM.
     * @param today hari ini; untuk bulan berjalan, hari dihitung hanya sampai hari ini.
     */
    fun build(
        month: YearMonth,
        txs: List<Tx>,
        categories: Map<Long, CategoryInfo>,
        fuel: List<Triple<Long, Long, Long>>,
        today: LocalDate,
    ): MonthWrap {
        val (from, to) = Ledger.monthRange(month)
        val inMonth = txs.filter { it.dateTime in from until to }
        val sum = Ledger.summarize(txs, month)
        val prevSum = Ledger.summarize(txs, month.minusMonths(1))
        val hasPrev = txs.any { val (pf, pt) = Ledger.monthRange(month.minusMonths(1)); it.dateTime in pf until pt }
        val expenses = inMonth.filter { it.type == TxType.EXPENSE }

        val lastDay = if (YearMonth.from(today) == month) today else month.atEndOfMonth()
        val days = generateSequence(month.atDay(1)) { it.plusDays(1) }.takeWhile { !it.isAfter(lastDay) }.toList()
        val perDay = days.associateWith { d -> inMonth.filter { Fmt.toDate(it.dateTime) == d }.sumOf { Ledger.expenseOf(it) } }
        val spendDays = perDay.filter { it.value > 0 }

        val merchant = expenses.filter { it.description.isNotBlank() }
            .groupBy { it.description.trim().lowercase() }
            .map { (_, l) -> Triple(l.first().description.trim(), l.size, l.sumOf { it.amount }) }
            .filter { it.second >= 2 }
            .maxWithOrNull(compareBy({ it.second }, { it.third }))

        fun fuelIn(m: YearMonth) = fuel.filter { YearMonth.from(Fmt.toDate(it.first)) == m }
        val f = fuelIn(month)
        val prevFuel = fuelIn(month.minusMonths(1))

        return MonthWrap(
            month = month,
            income = sum.income,
            expense = sum.expense,
            savings = sum.savings,
            txCount = inMonth.size,
            prevExpense = if (hasPrev) prevSum.expense else null,
            topCategory = Ledger.expenseByRootCategory(inMonth, categories).maxByOrNull { it.value }?.toPair(),
            biggest = expenses.maxByOrNull { it.amount },
            priciestDay = spendDays.maxByOrNull { it.value }?.toPair(),
            thriftiestDay = spendDays.minByOrNull { it.value }?.toPair(),
            noSpendDays = perDay.count { it.value == 0L },
            daysCounted = days.size,
            topMerchant = merchant,
            topWalletId = inMonth.groupBy { it.walletId }.maxByOrNull { it.value.size }?.key,
            fuelCost = f.sumOf { it.third },
            fuelLiters = f.sumOf { it.second } / 1000.0,
            fuelFills = f.size,
            prevFuelCost = if (prevFuel.isNotEmpty()) prevFuel.sumOf { it.third } else null,
        )
    }
}
