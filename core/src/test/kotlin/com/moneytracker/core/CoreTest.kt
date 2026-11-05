package com.moneytracker.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerTest {
    @BeforeTest fun zone() { Fmt.zone = ZoneId.of("Asia/Jakarta") }

    private fun at(y: Int, m: Int, d: Int) = Fmt.toMillis(LocalDate.of(y, m, d).atTime(10, 0))

    private val bca = WalletInfo(1, "BCA", WalletType.BANK, 1_000_000)
    private val cash = WalletInfo(2, "Cash", WalletType.CASH, 100_000)
    private val cc = WalletInfo(3, "Kartu Kredit", WalletType.CREDIT_CARD, 0)

    @Test fun transferIsNotIncomeOrExpenseExceptFee() {
        val txs = listOf(
            Tx(1, TxType.INCOME, 5_000_000, walletId = 1, dateTime = at(2026, 11, 1)),
            Tx(2, TxType.TRANSFER, 200_000, fee = 2_500, walletId = 1, toWalletId = 2, dateTime = at(2026, 11, 2)),
            Tx(3, TxType.EXPENSE, 50_000, walletId = 2, dateTime = at(2026, 11, 3)),
        )
        val b = Ledger.balances(listOf(bca, cash), txs)
        assertEquals(1_000_000 + 5_000_000 - 202_500, b[1])
        assertEquals(100_000 + 200_000 - 50_000, b[2])
        val s = Ledger.summarize(txs, YearMonth.of(2026, 11))
        assertEquals(5_000_000, s.income)
        assertEquals(52_500, s.expense) // makan + biaya admin, BUKAN transfer
    }

    @Test fun creditCardNoDoubleCounting() {
        val txs = listOf(
            Tx(1, TxType.EXPENSE, 300_000, walletId = 3, dateTime = at(2026, 11, 1)),            // belanja pakai CC
            Tx(2, TxType.CC_SETTLEMENT, 300_000, walletId = 1, toWalletId = 3, dateTime = at(2026, 11, 20)), // pelunasan
        )
        val b = Ledger.balances(listOf(bca, cc), txs)
        assertEquals(0, b[3])
        assertEquals(700_000, b[1])
        assertEquals(300_000, Ledger.summarize(txs, YearMonth.of(2026, 11)).expense)
    }

    @Test fun debtPaymentOnlyInterestIsExpense() {
        val txs = listOf(
            Tx(1, TxType.LOAN_RECEIVED, 2_000_000, walletId = 1, debtId = 9, dateTime = at(2026, 10, 1)),
            Tx(2, TxType.DEBT_PAYMENT, 500_000, fee = 25_000, walletId = 1, debtId = 9, dateTime = at(2026, 11, 1)),
        )
        assertEquals(1_000_000 + 2_000_000 - 525_000, Ledger.balances(listOf(bca), txs)[1])
        assertEquals(0, Ledger.summarize(txs, YearMonth.of(2026, 10)).income)
        assertEquals(25_000, Ledger.summarize(txs, YearMonth.of(2026, 11)).expense)
        val st = DebtCalc.status(DebtDirection.PAYABLE, 2_000_000, txs.filter { it.debtId == 9L })
        assertEquals(1_500_000, st.remaining)
    }

    @Test fun netWorth() {
        val nw = Ledger.netWorth(listOf(1_000_000, -300_000, 50_000), otherAssets = 10_000_000, receivables = 200_000, payables = 1_500_000)
        assertEquals(11_250_000, nw.totalAssets)
        assertEquals(1_800_000, nw.totalLiabilities)
        assertEquals(9_450_000, nw.netWorth)
    }

    @Test fun dailySeries() {
        val txs = listOf(Tx(1, TxType.EXPENSE, 100_000, walletId = 1, dateTime = at(2026, 11, 2)))
        val s = Ledger.dailyBalanceSeries(listOf(bca), txs, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3))
        assertEquals(listOf(1_000_000L, 900_000L, 900_000L), s.map { it.second })
    }
}

class FormatTest {
    @Test fun rupiah() {
        assertEquals("Rp1.250.000", Fmt.rupiah(1_250_000))
        assertEquals("-Rp50.000", Fmt.rupiah(-50_000))
        assertEquals("Rp0", Fmt.rupiah(0))
        assertEquals("Rp1,3 jt", Fmt.rupiahCompact(1_250_000))
        assertEquals("12,5", Fmt.decimal(12.5))
        assertEquals("1.234,57", Fmt.decimal(1234.567, 2))
    }

    @Test fun parseAmount() {
        assertEquals(1_250_000, Fmt.parseAmount("1.250.000"))
        assertEquals(1_250_001, Fmt.parseAmount("1.250.000,50"))
        assertEquals(1_250_000, Fmt.parseAmount("1,250,000.00"))
        assertEquals(-50_000, Fmt.parseAmount("-Rp 50.000"))
        assertEquals(-50_000, Fmt.parseAmount("(50.000)"))
        assertEquals(12_000, Fmt.parseAmount("12.000"))
        assertEquals(75_000, Fmt.parseAmount("75000 CR"))
        assertEquals(-75_000, Fmt.parseAmount("75.000 DB"))
        assertNull(Fmt.parseAmount("abc"))
    }
}

class FuelTest {
    @BeforeTest fun zone() { Fmt.zone = ZoneId.of("Asia/Jakarta") }

    private fun fill(id: Long, day: LocalDate, odo: Long, liters: Double, price: Long = 12_000, full: Boolean = true) =
        FuelEntry(id, Fmt.toMillis(day.atTime(8, 0)), odo, (liters * 1000).toLong(), (liters * price).toLong(), price, "Pertamax", full)

    @Test fun fullToFullCycles() {
        val d0 = LocalDate.of(2026, 10, 1)
        val logs = listOf(
            fill(1, d0, 10_000, 8.0),
            fill(2, d0.plusDays(5), 10_200, 4.0, full = false),
            fill(3, d0.plusDays(10), 10_400, 4.0),   // 400 km / 8 L = 50 km/L
            fill(4, d0.plusDays(20), 10_800, 8.0),   // 400 km / 8 L = 50 km/L
            fill(5, d0.plusDays(30), 11_200, 8.0),   // 50 km/L
        )
        val r = FuelAnalytics.analyze(logs, VehicleSpec(8_500, null), today = d0.plusDays(32))
        assertEquals(3, r.cycles.size)
        assertEquals(50.0, r.kmPerLiter!!.value, 0.001)
        assertEquals(Confidence.HIGH, r.kmPerLiter!!.confidence)
        assertEquals(1200.0 / 30, r.kmPerDay!!.value, 0.001)    // 40 km/hari
        assertEquals(0.8, r.litersPerDay!!.value, 0.001)         // 40/50
        assertEquals(9_600.0, r.costPerDay!!.value, 1.0)         // 0.8 × 12.000
        assertEquals(240.0, r.costPerKm!!.value, 0.001)
        // 2 hari sejak isi penuh: 8,5 L − 80 km/50 = 6,9 L
        assertEquals(6.9, r.remainingLiters!!.value, 0.001)
        assertEquals(2L, r.daysSinceLastFill)
        val daysLeft = 6.9 / 0.8
        assertEquals(daysLeft, r.daysUntilEmpty!!.value, 0.001)
        assertEquals(d0.plusDays(32).plusDays(daysLeft.toLong()), r.emptyDate)
        assertTrue(r.suggestedRefillDate!!.isBefore(r.emptyDate!!))
    }

    @Test fun litersBoughtIsNotAssumedConsumed() {
        // Hanya satu pengisian parsial: tidak boleh mengklaim konsumsi pasti.
        val d0 = LocalDate.of(2026, 11, 1)
        val r = FuelAnalytics.analyze(listOf(fill(1, d0, 5_000, 10.0, full = false)), VehicleSpec(null, null), today = d0.plusDays(5))
        assertNull(r.kmPerLiter)
        assertNull(r.litersPerDay)
        assertEquals(Confidence.LOW, r.remainingLiters!!.confidence)
        assertTrue(r.warnings.isNotEmpty())
        assertEquals(5L, r.daysSinceLastFill)
    }

    @Test fun referenceConsumptionFallbackIsLowConfidence() {
        val d0 = LocalDate.of(2026, 11, 1)
        val logs = listOf(fill(1, d0, 5_000, 4.0), fill(2, d0.plusDays(4), 5_100, 2.0, full = false))
        val r = FuelAnalytics.analyze(logs, VehicleSpec(4_000, 45.0), today = d0.plusDays(5))
        assertNotNull(r.kmPerLiter)
        assertEquals(Confidence.LOW, r.kmPerLiter!!.confidence) // berbasis pembelian, bukan full-to-full
        assertEquals(Confidence.LOW, r.remainingLiters!!.confidence)
    }

    @Test fun litersFromPayment() {
        assertEquals(10_000, FuelAnalytics.litersFromPayment(120_000, 12_000))
        assertEquals(7_440, FuelAnalytics.litersFromPayment(75_000, 10_080))
    }
}

class PlanningTest {
    @Test fun budgetRollover() {
        val spec = BudgetSpec(1, 1_000_000, rollover = true, startMonth = YearMonth.of(2026, 9))
        val spent = mapOf(YearMonth.of(2026, 9) to 800_000L, YearMonth.of(2026, 10) to 1_100_000L, YearMonth.of(2026, 11) to 500_000L)
        val st = BudgetCalc.status(spec, YearMonth.of(2026, 11)) { spent[it] ?: 0 }
        // Sep sisa 200rb → Okt tersedia 1,2jt, terpakai 1,1jt → sisa 100rb → Nov 1,1jt
        assertEquals(100_000, st.carriedOver)
        assertEquals(600_000, st.remaining)
        val noRoll = BudgetCalc.status(spec.copy(rollover = false), YearMonth.of(2026, 10)) { spent[it] ?: 0 }
        assertEquals(BudgetStatus.Level.OVER, noRoll.level)
    }

    @Test fun scheduleMonthlyAnchor() {
        val d = LocalDate.of(2026, 1, 31)
        val n1 = Schedule.next(d, Period.MONTHLY, 1, anchorDay = 31)
        assertEquals(LocalDate.of(2026, 2, 28), n1)
        assertEquals(LocalDate.of(2026, 3, 31), Schedule.next(n1, Period.MONTHLY, 1, anchorDay = 31))
    }

    @Test fun goalEstimate() {
        val today = LocalDate.of(2026, 11, 5)
        val contrib = listOf(LocalDate.of(2026, 9, 5) to 1_000_000L, LocalDate.of(2026, 10, 5) to 1_000_000L)
        val g = GoalCalc.progress(10_000_000, 2_000_000, contrib, LocalDate.of(2027, 6, 1), null, today)
        assertEquals(4_000_000, g.collected)
        assertNotNull(g.estimatedCompletion)
        assertTrue(g.ratio in 0.39..0.41)
    }
}

class ImportTest {
    @Test fun csvAndMapping() {
        val text = "Tanggal;Keterangan;Debit;Kredit\n01/11/2026;\"Kopi; Kenangan\";25.000;\n02/11/2026;Gaji;;8.500.000\n"
        val table = Csv.parse(text)
        assertEquals(3, table.size)
        assertEquals("Kopi; Kenangan", table[1][1])
        val res = Importer.apply(table, ImportMapping(dateCol = 0, descriptionCol = 1, debitCol = 2, creditCol = 3))
        assertEquals(listOf(-25_000L, 8_500_000L), res.rows.map { it.amount })
        assertEquals(LocalDate.of(2026, 11, 1), res.rows[0].dateTime.toLocalDate())
    }

    @Test fun indonesianDates() {
        assertEquals(LocalDate.of(2026, 11, 1), Importer.parseDate("01 Nov 2026")!!.toLocalDate())
        assertEquals(LocalDate.of(2026, 8, 17), Importer.parseDate("17 Agustus 2026")!!.toLocalDate())
        assertEquals(LocalDateTime.of(2026, 11, 1, 14, 30), Importer.parseDate("2026-11-01 14:30"))
    }

    @Test fun xlsxRoundTrip() {
        val bos = java.io.ByteArrayOutputStream()
        Xlsx.write(listOf("Transaksi" to listOf(listOf(Xlsx.Text("Tanggal"), Xlsx.Text("Nominal")), listOf(Xlsx.Text("01/11/2026"), Xlsx.Num(-25000.0)))), bos)
        val rows = Xlsx.readFirstSheet(bos.toByteArray().inputStream())
        assertEquals(listOf("Tanggal", "Nominal"), rows[0])
        assertEquals("-25000", rows[1][1])
    }
}

class InsightTest {
    @BeforeTest fun zone() { Fmt.zone = ZoneId.of("Asia/Jakarta") }

    @Test fun increaseUsesComparablePeriod() {
        val cats = mapOf(1L to CategoryInfo(1, "Makan", null, CategoryKind.EXPENSE, Nature.NEED))
        fun at(d: LocalDate) = Fmt.toMillis(d.atTime(12, 0))
        val txs = listOf(
            Tx(1, TxType.EXPENSE, 100_000, walletId = 1, categoryId = 1, dateTime = at(LocalDate.of(2026, 10, 2))),
            Tx(2, TxType.EXPENSE, 900_000, walletId = 1, categoryId = 1, dateTime = at(LocalDate.of(2026, 10, 25))), // di luar periode setara
            Tx(3, TxType.EXPENSE, 200_000, walletId = 1, categoryId = 1, dateTime = at(LocalDate.of(2026, 11, 3))),
        )
        val ins = Insights.generate(txs, cats, 1_000_000, emptyList(), emptyList(), emptySet(), setOf(1L), LocalDate.of(2026, 11, 5))
        val inc = ins.first { it.id == "increase_1" }
        assertTrue(inc.message.contains("100%"), inc.message)
        assertTrue(ins.any { it.id == "projection" })
        assertTrue(ins.all { it.basis.isNotBlank() })
    }

    @Test fun noInsightsWithoutData() {
        assertTrue(Insights.generate(emptyList(), emptyMap(), 0, emptyList(), emptyList(), emptySet(), emptySet()).isEmpty())
    }
}

class ReminderTest {
    private val t = { h: Int, m: Int -> java.time.LocalTime.of(h, m) }

    @Test fun nextDailyToday() {
        val c = ReminderConfig(dailyEnabled = true, dailyTimes = listOf(t(12, 0), t(20, 0)))
        val n = ReminderSchedule.next(LocalDateTime.of(2026, 11, 5, 13, 10), c)!!
        assertEquals(LocalDateTime.of(2026, 11, 5, 20, 0), n.at)
        assertEquals(setOf(ReminderKind.DAILY), n.kinds)
    }

    @Test fun nextDailyRollsToTomorrow() {
        val c = ReminderConfig(dailyEnabled = true, dailyTimes = listOf(t(12, 0), t(20, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 6, 12, 0), ReminderSchedule.next(LocalDateTime.of(2026, 11, 5, 20, 0), c)!!.at)
    }

    @Test fun monthlyShortMonthUsesLastDay() {
        val c = ReminderConfig(monthlyEnabled = true, monthlyDays = setOf(31), monthlyTime = t(9, 0))
        assertEquals(LocalDateTime.of(2026, 11, 30, 9, 0), ReminderSchedule.next(LocalDateTime.of(2026, 11, 5, 10, 0), c)!!.at)
        assertEquals(LocalDateTime.of(2027, 2, 28, 9, 0), ReminderSchedule.next(LocalDateTime.of(2027, 2, 1, 10, 0), c)!!.at)
    }

    @Test fun combinedSameMinute() {
        val c = ReminderConfig(true, listOf(t(9, 0)), true, setOf(6), t(9, 0))
        val n = ReminderSchedule.next(LocalDateTime.of(2026, 11, 5, 21, 0), c)!!
        assertEquals(LocalDateTime.of(2026, 11, 6, 9, 0), n.at)
        assertEquals(setOf(ReminderKind.DAILY, ReminderKind.MONTHLY), n.kinds)
    }

    @Test fun disabledReturnsNull() {
        assertNull(ReminderSchedule.next(LocalDateTime.of(2026, 11, 5, 9, 0), ReminderConfig()))
    }

    @Test fun windowAndParsing() {
        val times = ReminderSchedule.parseTimes("20:00, 12:00,bad,12:00")
        assertEquals(listOf(t(12, 0), t(20, 0)), times)
        assertEquals("12:00,20:00", ReminderSchedule.formatTimes(times))
        assertEquals(LocalDateTime.of(2026, 11, 5, 12, 0), ReminderSchedule.windowStart(LocalDateTime.of(2026, 11, 5, 20, 0), times))
        assertEquals(LocalDateTime.of(2026, 11, 5, 0, 0), ReminderSchedule.windowStart(LocalDateTime.of(2026, 11, 5, 12, 0), times))
        assertEquals(setOf(1, 15, 31), ReminderSchedule.parseDays("15,1,31,40,x"))
    }
}
