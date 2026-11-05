package com.moneytracker.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** Penjadwalan transaksi berulang. */
object Schedule {
    fun next(date: LocalDate, period: Period, interval: Int = 1, anchorDay: Int? = null): LocalDate {
        val n = interval.coerceAtLeast(1).toLong()
        return when (period) {
            Period.DAILY -> date.plusDays(n)
            Period.WEEKLY -> date.plusWeeks(n)
            Period.MONTHLY -> withAnchor(date.plusMonths(n), anchorDay)
            Period.QUARTERLY -> withAnchor(date.plusMonths(3 * n), anchorDay)
            Period.YEARLY -> withAnchor(date.plusYears(n), anchorDay)
        }
    }

    /** Menjaga tanggal jatuh tempo (mis. tgl 31) tetap konsisten di bulan pendek. */
    private fun withAnchor(d: LocalDate, anchorDay: Int?): LocalDate {
        if (anchorDay == null) return d
        val ym = YearMonth.from(d)
        return ym.atDay(anchorDay.coerceIn(1, ym.lengthOfMonth()))
    }

    /** Semua tanggal jatuh tempo dari [start] (inklusif) hingga [until] (inklusif). */
    fun occurrences(start: LocalDate, period: Period, interval: Int, until: LocalDate, anchorDay: Int? = null, limit: Int = 400): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = start
        while (!d.isAfter(until) && out.size < limit) {
            out += d
            d = next(d, period, interval, anchorDay)
        }
        return out
    }

    fun monthlyCost(amount: Long, period: Period, interval: Int = 1): Double = amount * period.perMonthFactor / interval.coerceAtLeast(1)
}

/** Anggaran bulanan dengan rollover opsional. */
data class BudgetSpec(
    val id: Long,
    val amount: Long,
    val rollover: Boolean,
    val startMonth: YearMonth,
)

data class BudgetStatus(
    val budgetId: Long,
    val base: Long,
    val carriedOver: Long,
    val spent: Long,
) {
    val available: Long get() = base + carriedOver
    val remaining: Long get() = available - spent
    val usage: Double get() = if (available <= 0) (if (spent > 0) 1.0 else 0.0) else spent.toDouble() / available
    val level: Level get() = when {
        usage >= 1.0 && spent > 0 -> Level.OVER
        usage >= WARN_THRESHOLD -> Level.WARNING
        else -> Level.OK
    }

    enum class Level { OK, WARNING, OVER }

    companion object { const val WARN_THRESHOLD = 0.8 }
}

object BudgetCalc {
    /**
     * Status anggaran untuk [month]. [spentByMonth] = realisasi tiap bulan.
     * Rollover: sisa (positif) bulan sebelumnya ditambahkan ke bulan berikutnya, berantai sejak [BudgetSpec.startMonth].
     * Overspend tidak mengurangi bulan berikutnya (carry minimal 0).
     */
    fun status(spec: BudgetSpec, month: YearMonth, spentByMonth: (YearMonth) -> Long): BudgetStatus {
        var carry = 0L
        if (spec.rollover && spec.startMonth.isBefore(month)) {
            var m = spec.startMonth
            var guard = 0
            while (m.isBefore(month) && guard++ < 240) {
                val left = spec.amount + carry - spentByMonth(m)
                carry = left.coerceAtLeast(0)
                m = m.plusMonths(1)
            }
        }
        return BudgetStatus(spec.id, spec.amount, carry, spentByMonth(month))
    }
}

data class GoalProgress(
    val target: Long,
    val collected: Long,
    val monthlyPace: Double?,           // rata-rata setoran per bulan (aktual)
    val requiredPerMonth: Double?,      // yang dibutuhkan agar tercapai sebelum deadline
    val estimatedCompletion: LocalDate?,
    val onTrack: Boolean?,
    val basis: String,
) {
    val ratio: Double get() = if (target <= 0) 0.0 else (collected.toDouble() / target).coerceIn(0.0, 1.0)
    val remaining: Long get() = (target - collected).coerceAtLeast(0)
}

object GoalCalc {
    /**
     * @param contributions pasangan (tanggal, nominal) setoran; dipakai untuk menghitung laju 3 bulan terakhir.
     * @param plannedMonthly target menabung per bulan yang diisi pengguna (dipakai bila belum ada riwayat).
     */
    fun progress(
        target: Long,
        initial: Long,
        contributions: List<Pair<LocalDate, Long>>,
        deadline: LocalDate?,
        plannedMonthly: Long?,
        today: LocalDate = LocalDate.now(Fmt.zone),
    ): GoalProgress {
        val collected = initial + contributions.sumOf { it.second }
        val remaining = (target - collected).coerceAtLeast(0)
        val windowStart = today.minusMonths(3)
        val recent = contributions.filter { !it.first.isBefore(windowStart) }
        val firstDate = contributions.minOfOrNull { it.first }
        val months = if (firstDate == null) 0.0 else
            (ChronoUnit.DAYS.between(maxOf(firstDate, windowStart), today).coerceAtLeast(1) / 30.4375).coerceAtLeast(1.0)
        val pace: Double? = if (recent.isNotEmpty()) recent.sumOf { it.second } / months else null

        val required = deadline?.let {
            val m = ChronoUnit.DAYS.between(today, it) / 30.4375
            if (m <= 0) remaining.toDouble() else remaining / m
        }

        val (rate, basis) = when {
            pace != null && pace > 0 -> pace to "Laju setoran aktual ±${Fmt.rupiah(pace.toLong())}/bulan (3 bulan terakhir)"
            plannedMonthly != null && plannedMonthly > 0 -> plannedMonthly.toDouble() to "Berdasarkan rencana ${Fmt.rupiah(plannedMonthly)}/bulan (belum ada riwayat setoran)"
            else -> null to "Belum ada setoran atau rencana per bulan"
        }
        val eta = when {
            remaining == 0L -> today
            rate != null && rate > 0 -> today.plusDays(ceil(remaining / rate * 30.4375).toLong())
            else -> null
        }
        val onTrack = if (deadline != null && eta != null) !eta.isAfter(deadline) else null
        return GoalProgress(target, collected, pace, required, eta, onTrack, basis)
    }
}

data class DebtStatus(val principal: Long, val paid: Long, val interestPaid: Long) {
    val remaining: Long get() = (principal - paid).coerceAtLeast(0)
    val ratio: Double get() = if (principal <= 0) 1.0 else (paid.toDouble() / principal).coerceIn(0.0, 1.0)
    val settled: Boolean get() = remaining == 0L
}

object DebtCalc {
    /**
     * Sisa utang/piutang. Pembayaran = DEBT_PAYMENT (utang) atau DEBT_COLLECTION (piutang) yang terhubung ke debtId.
     * Untuk utang: pokok = amount, bunga/denda = fee (dicatat sebagai pengeluaran oleh Ledger).
     */
    fun status(direction: DebtDirection, principal: Long, txs: List<Tx>): DebtStatus {
        val payType = if (direction == DebtDirection.PAYABLE) TxType.DEBT_PAYMENT else TxType.DEBT_COLLECTION
        val pays = txs.filter { it.type == payType }
        return DebtStatus(principal, pays.sumOf { it.amount }, pays.sumOf { it.fee })
    }
}
