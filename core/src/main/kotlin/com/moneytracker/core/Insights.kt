package com.moneytracker.core

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

data class Insight(
    val id: String,
    val title: String,
    val message: String,
    /** Penjelasan dasar perhitungan — selalu ditampilkan agar transparan. */
    val basis: String,
    val severity: Severity = Severity.INFO,
) {
    enum class Severity { INFO, POSITIVE, WARNING, ALERT }
}

data class RecurringSpec(
    val id: Long,
    val name: String,
    val amount: Long,
    val isIncome: Boolean,
    val isSubscription: Boolean,
    val period: Period,
    val interval: Int,
    val nextDate: LocalDate,
    val anchorDay: Int?,
    val active: Boolean,
)

data class BudgetAlert(val name: String, val status: BudgetStatus)

/**
 * Asisten keuangan berbasis aturan. Setiap insight hanya muncul bila datanya cukup,
 * dan selalu menyertakan dasar perhitungannya.
 */
object Insights {
    private const val MIN_CHANGE_RATIO = 0.15
    private const val MIN_CHANGE_AMOUNT = 50_000L

    fun generate(
        txs: List<Tx>,
        categories: Map<Long, CategoryInfo>,
        liquidBalance: Long,
        recurring: List<RecurringSpec>,
        budgetAlerts: List<BudgetAlert>,
        fuelCategoryIds: Set<Long>,
        foodCategoryIds: Set<Long>,
        today: LocalDate = LocalDate.now(Fmt.zone),
    ): List<Insight> {
        val out = mutableListOf<Insight>()
        if (txs.isEmpty()) return out
        val thisMonth = YearMonth.from(today)
        val prevMonth = thisMonth.minusMonths(1)
        val dayOfMonth = today.dayOfMonth
        val (mFrom, _) = Ledger.monthRange(thisMonth)
        val todayEnd = Fmt.toMillis(today.plusDays(1))
        val (pFrom, _) = Ledger.monthRange(prevMonth)
        // Periode pembanding setara: tgl 1..hari ini bulan lalu
        val pSameEnd = Fmt.toMillis(prevMonth.atDay(minOf(dayOfMonth, prevMonth.lengthOfMonth())).plusDays(1))
        val earliest = txs.minOf { it.dateTime }
        val hasPrevMonthData = earliest < Fmt.toMillis(prevMonth.atDay(1).plusDays(7))

        val thisTx = txs.filter { it.dateTime in mFrom until todayEnd }
        val prevSameTx = txs.filter { it.dateTime in pFrom until pSameEnd }

        // 1. Budget alerts
        for (a in budgetAlerts) {
            when (a.status.level) {
                BudgetStatus.Level.OVER -> out += Insight(
                    "budget_over_${a.status.budgetId}", "Anggaran ${a.name} terlampaui",
                    "Terpakai ${Fmt.rupiah(a.status.spent)} dari ${Fmt.rupiah(a.status.available)} (lebih ${Fmt.rupiah(-a.status.remaining)}).",
                    "Realisasi pengeluaran kategori ${a.name} bulan ${Fmt.month(thisMonth)} dibanding anggaran" +
                        (if (a.status.carriedOver > 0) " + rollover ${Fmt.rupiah(a.status.carriedOver)}" else ""),
                    Insight.Severity.ALERT,
                )
                BudgetStatus.Level.WARNING -> out += Insight(
                    "budget_warn_${a.status.budgetId}", "Anggaran ${a.name} hampir habis",
                    "Sudah ${Fmt.percent(a.status.usage)} terpakai, sisa ${Fmt.rupiah(a.status.remaining)}.",
                    "Peringatan muncul saat penggunaan ≥ ${Fmt.percent(BudgetStatus.WARN_THRESHOLD)}",
                    Insight.Severity.WARNING,
                )
                else -> {}
            }
        }

        // 2. Kategori terbesar bulan ini
        val byCat = Ledger.expenseByRootCategory(thisTx, categories)
        val totalExp = byCat.values.sum()
        byCat.maxByOrNull { it.value }?.let { (catId, amt) ->
            if (amt > 0 && totalExp > 0) {
                val name = catId?.let { categories[it]?.name } ?: "Tanpa kategori"
                out += Insight(
                    "top_category", "Pengeluaran terbesar: $name",
                    "$name mencapai ${Fmt.rupiah(amt)} atau ${Fmt.percent(amt.toDouble() / totalExp)} dari total pengeluaran bulan ini.",
                    "Total pengeluaran ${Fmt.rupiah(totalExp)} periode 1–$dayOfMonth ${Fmt.month(thisMonth)}, dikelompokkan per kategori induk",
                )
            }
        }

        // 3. Kategori yang meningkat (periode setara)
        if (hasPrevMonthData) {
            val prevByCat = Ledger.expenseByRootCategory(prevSameTx, categories)
            val increases = byCat.mapNotNull { (cat, now) ->
                val before = prevByCat[cat] ?: 0
                if (before <= 0) return@mapNotNull null
                val ratio = (now - before).toDouble() / before
                if (ratio >= MIN_CHANGE_RATIO && now - before >= MIN_CHANGE_AMOUNT) Triple(cat, now, before) else null
            }.sortedByDescending { it.second - it.third }.take(2)
            for ((cat, now, before) in increases) {
                val name = cat?.let { categories[it]?.name } ?: "Tanpa kategori"
                val ratio = (now - before).toDouble() / before
                out += Insight(
                    "increase_$cat", "$name naik ${Fmt.percent(ratio)}",
                    "Bulan ini pengeluaran $name meningkat ${Fmt.percent(ratio)} dibandingkan bulan sebelumnya (${Fmt.rupiah(before)} → ${Fmt.rupiah(now)}).",
                    "Dibandingkan pada periode setara: tgl 1–$dayOfMonth ${Fmt.month(thisMonth)} vs tgl 1–$dayOfMonth ${Fmt.month(prevMonth)}",
                    Insight.Severity.WARNING,
                )
                // Rekomendasi penghematan hanya untuk kategori "keinginan"
                if (cat != null && categories[cat]?.nature == Nature.WANT) {
                    val save = (now * 0.2).toLong()
                    out += Insight(
                        "saving_tip_$cat", "Peluang hemat di $name",
                        "Mengurangi $name 20% bulan ini menghemat sekitar ${Fmt.rupiah(save)}.",
                        "20% × pengeluaran $name bulan berjalan (${Fmt.rupiah(now)}); kategori ditandai sebagai Keinginan",
                        Insight.Severity.INFO,
                    )
                }
            }

            // Total pengeluaran vs bulan lalu (periode setara)
            val now = thisTx.sumOf { Ledger.expenseOf(it) }
            val before = prevSameTx.sumOf { Ledger.expenseOf(it) }
            if (before > 0) {
                val r = (now - before).toDouble() / before
                if (abs(r) >= 0.05) out += Insight(
                    "total_change", if (r > 0) "Pengeluaran naik ${Fmt.percent(r)}" else "Pengeluaran turun ${Fmt.percent(-r)}",
                    "Total pengeluaran ${Fmt.rupiah(now)} vs ${Fmt.rupiah(before)} pada periode yang sama bulan lalu.",
                    "Tgl 1–$dayOfMonth, transfer & tabungan tidak dihitung",
                    if (r > 0) Insight.Severity.WARNING else Insight.Severity.POSITIVE,
                )
            }
        }

        // 4. Rata-rata makan per hari
        if (foodCategoryIds.isNotEmpty()) {
            val food = thisTx.filter { it.type == TxType.EXPENSE && it.categoryId in foodCategoryIds }.sumOf { it.amount }
            if (food > 0 && dayOfMonth >= 3) out += Insight(
                "food_daily", "Makan ±${Fmt.rupiah(food / dayOfMonth)}/hari",
                "Anda mengeluarkan rata-rata ${Fmt.rupiah(food / dayOfMonth)} per hari untuk makan & minum bulan ini.",
                "${Fmt.rupiah(food)} ÷ $dayOfMonth hari (kategori Kebutuhan Sehari-hari › makan/minum)",
            )
        }

        // 5. Rata-rata BBM per bulan (bulan lengkap saja)
        if (fuelCategoryIds.isNotEmpty()) {
            val months = (1..3).map { thisMonth.minusMonths(it.toLong()) }
            val perMonth = months.map { m ->
                val (f, t) = Ledger.monthRange(m)
                m to txs.filter { it.type == TxType.EXPENSE && it.categoryId in fuelCategoryIds && it.dateTime in f until t }.sumOf { it.amount }
            }.filter { it.second > 0 && Fmt.toMillis(it.first.atDay(1)) >= Fmt.toMillis(Fmt.toDate(earliest).withDayOfMonth(1)) }
            if (perMonth.isNotEmpty()) {
                val avg = perMonth.sumOf { it.second } / perMonth.size
                out += Insight(
                    "fuel_avg", "BBM ±${Fmt.rupiah(avg)}/bulan",
                    "Pengeluaran bensin rata-rata mencapai ${Fmt.rupiah(avg)} per bulan.",
                    "Rata-rata ${perMonth.size} bulan penuh terakhir: " + perMonth.joinToString { "${Fmt.monthShort(it.first)} ${Fmt.rupiahCompact(it.second)}" },
                )
            }
        }

        // 6. Prediksi saldo akhir bulan
        val last30From = Fmt.toMillis(today.minusDays(29))
        val variable = txs.filter { it.dateTime in last30From until todayEnd && it.recurringId == null }
        val spanDays = minOf(30L, java.time.temporal.ChronoUnit.DAYS.between(Fmt.toDate(earliest), today) + 1)
        if (spanDays >= 7) {
            val avgDaily = variable.sumOf { Ledger.expenseOf(it) }.toDouble() / spanDays
            val remainingDays = thisMonth.lengthOfMonth() - dayOfMonth
            val monthEnd = thisMonth.atEndOfMonth()
            var scheduledIn = 0L; var scheduledOut = 0L
            for (r in recurring.filter { it.active }) {
                val occ = Schedule.occurrences(r.nextDate, r.period, r.interval, monthEnd, r.anchorDay)
                    .filter { it.isAfter(today) || it == today }
                if (r.isIncome) scheduledIn += occ.size * r.amount else scheduledOut += occ.size * r.amount
            }
            val projected = liquidBalance - (avgDaily * remainingDays).toLong() - scheduledOut + scheduledIn
            out += Insight(
                "projection", "Estimasi saldo akhir bulan ${Fmt.rupiah(projected)}",
                "Jika pola pengeluaran saat ini berlanjut, estimasi saldo akhir bulan adalah ${Fmt.rupiah(projected)}.",
                "Saldo saat ini ${Fmt.rupiah(liquidBalance)} − rata-rata pengeluaran tidak rutin ${Fmt.rupiah(avgDaily.toLong())}/hari × $remainingDays hari" +
                    " − tagihan terjadwal ${Fmt.rupiah(scheduledOut)} + pemasukan terjadwal ${Fmt.rupiah(scheduledIn)}" +
                    " (rata-rata dari $spanDays hari terakhir)",
                if (projected < 0) Insight.Severity.ALERT else Insight.Severity.INFO,
            )
        }

        // 7. Biaya langganan tahunan
        val subs = recurring.filter { it.active && it.isSubscription && !it.isIncome }
        if (subs.isNotEmpty()) {
            val yearly = subs.sumOf { Schedule.monthlyCost(it.amount, it.period, it.interval) * 12 }.toLong()
            out += Insight(
                "subs_yearly", "Langganan ${Fmt.rupiah(yearly)}/tahun",
                "Pengeluaran langganan tahunan Anda mencapai ${Fmt.rupiah(yearly)} (${subs.size} layanan aktif).",
                "Σ nominal × frekuensi per tahun: " + subs.take(5).joinToString { it.name } + if (subs.size > 5) ", …" else "",
            )
        }

        // 8. Rasio tabungan bulan lalu (bulan lengkap)
        if (hasPrevMonthData) {
            val s = Ledger.summarize(txs, prevMonth)
            s.savingsRate?.let { rate ->
                out += Insight(
                    "savings_rate", "Rasio tabungan ${Fmt.month(prevMonth)}: ${Fmt.percent(rate)}",
                    if (rate >= 0.2) "Bagus! Anda menyisihkan ${Fmt.percent(rate)} dari pemasukan." else if (rate >= 0) "Anda menyisihkan ${Fmt.percent(rate)} dari pemasukan. Target umum adalah 20%." else "Pengeluaran melebihi pemasukan sebesar ${Fmt.rupiah(-s.net)}.",
                    "(Pemasukan ${Fmt.rupiah(s.income)} − pengeluaran ${Fmt.rupiah(s.expense)}) ÷ pemasukan",
                    when { rate >= 0.2 -> Insight.Severity.POSITIVE; rate >= 0 -> Insight.Severity.INFO; else -> Insight.Severity.ALERT },
                )
            }
        }
        return out
    }
}
