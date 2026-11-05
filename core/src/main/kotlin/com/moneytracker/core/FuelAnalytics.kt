package com.moneytracker.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/** Satu pengisian BBM. Volume dalam mililiter, harga dalam Rupiah. */
data class FuelEntry(
    val id: Long,
    val dateTime: Long,
    val odometerKm: Long,
    val volumeMl: Long,
    val totalCost: Long,
    val pricePerLiter: Long,
    val fuelType: String,
    val isFullTank: Boolean,
)

/** Pembacaan odometer tambahan (tanpa pengisian) untuk mempertajam estimasi. */
data class OdometerEntry(val dateTime: Long, val odometerKm: Long)

data class VehicleSpec(
    val tankCapacityMl: Long?,           // null = tidak diketahui
    val referenceKmPerLiter: Double?,    // konsumsi rata-rata dari pengguna/pabrikan
)

/** Satu siklus full-to-full: dari tangki penuh ke tangki penuh berikutnya. */
data class FuelCycle(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val distanceKm: Long,
    val litersUsed: Double,
    val cost: Long,
) {
    val days: Long get() = max(1, ChronoUnit.DAYS.between(startDate, endDate))
    val kmPerLiter: Double get() = distanceKm / litersUsed
    val litersPerDay: Double get() = litersUsed / days
    val costPerKm: Double get() = cost.toDouble() / distanceKm
    val costPerDay: Double get() = cost.toDouble() / days
    val kmPerDay: Double get() = distanceKm.toDouble() / days
}

data class Estimate<T>(val value: T, val confidence: Confidence, val basis: String)

data class FuelReport(
    val lastFill: FuelEntry?,
    val daysSinceLastFill: Long?,
    val cycles: List<FuelCycle>,
    val kmPerLiter: Estimate<Double>?,
    val kmPerDay: Estimate<Double>?,
    val litersPerDay: Estimate<Double>?,
    val costPerDay: Estimate<Double>?,
    val costPerKm: Estimate<Double>?,
    /** Km sejak pengisian terakhir: pasti (dari odometer) + estimasi sisa hari. */
    val kmSinceLastFill: Estimate<Double>?,
    val remainingLiters: Estimate<Double>?,
    val daysUntilEmpty: Estimate<Double>?,
    /** Tanggal BBM diperkirakan habis. */
    val emptyDate: LocalDate?,
    /** Tanggal disarankan isi ulang (saat tersisa cadangan [RESERVE_RATIO] tangki). */
    val suggestedRefillDate: LocalDate?,
    val monthlyCost: List<Pair<YearMonth, Long>>,
    val monthlyLiters: List<Pair<YearMonth, Double>>,
    val yearlyCost: List<Pair<Int, Long>>,
    val totalCost: Long,
    val totalLiters: Double,
    val totalDistanceKm: Long,
    val warnings: List<String>,
)

/**
 * Analisis BBM.
 *
 * Prinsip: liter yang DIBELI tidak sama dengan liter yang DIPAKAI.
 * - Efisiensi utama dihitung dengan metode full-to-full: jarak antara dua pengisian tangki penuh
 *   dibagi total liter yang diisikan setelah pengisian penuh pertama sampai (dan termasuk) pengisian penuh berikutnya.
 * - Sisa BBM disimulasikan: mulai dari pengisian penuh terakhir (= kapasitas tangki), dikurangi pemakaian
 *   (jarak ÷ km/L), ditambah setiap pengisian sesudahnya, dibatasi kapasitas tangki.
 * - Semua angka turunan diberi tingkat kepercayaan dan dasar perhitungan.
 */
object FuelAnalytics {
    const val RESERVE_RATIO = 0.15
    private const val MAX_CYCLES_FOR_AVERAGE = 6
    private const val KM_PER_DAY_WINDOW_DAYS = 60L

    fun analyze(
        entriesInput: List<FuelEntry>,
        spec: VehicleSpec,
        odometerLogs: List<OdometerEntry> = emptyList(),
        today: LocalDate = LocalDate.now(Fmt.zone),
    ): FuelReport {
        val entries = entriesInput.sortedWith(compareBy({ it.dateTime }, { it.odometerKm }))
        val warnings = mutableListOf<String>()
        val last = entries.lastOrNull()
        val capacityL = spec.tankCapacityMl?.takeIf { it > 0 }?.let { it / 1000.0 }

        // Validasi odometer menurun
        for (i in 1 until entries.size) {
            if (entries[i].odometerKm < entries[i - 1].odometerKm) {
                warnings += "Odometer pada ${Fmt.dateShort(entries[i].dateTime)} lebih kecil dari pengisian sebelumnya. Periksa data."
                break
            }
        }

        // 1. Siklus full-to-full
        val cycles = mutableListOf<FuelCycle>()
        var startIdx = -1
        var litersAcc = 0.0
        var costAcc = 0L
        for ((i, e) in entries.withIndex()) {
            if (startIdx >= 0) {
                litersAcc += e.volumeMl / 1000.0
                costAcc += e.totalCost
            }
            if (e.isFullTank) {
                if (startIdx >= 0) {
                    val s = entries[startIdx]
                    val dist = e.odometerKm - s.odometerKm
                    if (dist > 0 && litersAcc > 0) {
                        cycles += FuelCycle(Fmt.toDate(s.dateTime), Fmt.toDate(e.dateTime), dist, litersAcc, costAcc)
                    }
                }
                startIdx = i
                litersAcc = 0.0
                costAcc = 0L
            }
        }
        val recentCycles = cycles.takeLast(MAX_CYCLES_FOR_AVERAGE)

        // 2. Efisiensi km/L
        val kmPerLiter: Estimate<Double>? = when {
            recentCycles.isNotEmpty() -> {
                val dist = recentCycles.sumOf { it.distanceKm }
                val lit = recentCycles.sumOf { it.litersUsed }
                Estimate(
                    dist / lit,
                    if (recentCycles.size >= 3) Confidence.HIGH else Confidence.MEDIUM,
                    "Full-to-full dari ${recentCycles.size} siklus: ${Fmt.groupDigits(dist)} km ÷ ${Fmt.decimal(lit, 2)} L",
                )
            }
            entries.size >= 2 && entries.last().odometerKm > entries.first().odometerKm -> {
                // Pendekatan pembelian: liter yang dibeli sebelum pengisian terakhir ≈ dipakai untuk menempuh jarak tsb.
                val dist = entries.last().odometerKm - entries.first().odometerKm
                val lit = entries.dropLast(1).sumOf { it.volumeMl } / 1000.0
                if (lit > 0) Estimate(
                    dist / lit, Confidence.LOW,
                    "Perkiraan dari pembelian (belum ada 2 pengisian penuh): ${Fmt.groupDigits(dist)} km ÷ ${Fmt.decimal(lit, 2)} L dibeli",
                ) else null
            }
            spec.referenceKmPerLiter != null && spec.referenceKmPerLiter > 0 ->
                Estimate(spec.referenceKmPerLiter, Confidence.LOW, "Memakai konsumsi rata-rata yang Anda isi di data kendaraan")
            else -> null
        }

        // 3. Jarak harian dari odometer (pengisian + pembacaan odometer)
        val readings = (entries.map { OdometerEntry(it.dateTime, it.odometerKm) } + odometerLogs)
            .sortedBy { it.dateTime }
        val lastReading = readings.lastOrNull()
        val kmPerDay: Estimate<Double>? = run {
            if (readings.size < 2 || lastReading == null) return@run null
            val lastDate = Fmt.toDate(lastReading.dateTime)
            val windowStart = lastDate.minusDays(KM_PER_DAY_WINDOW_DAYS)
            val window = readings.filter { !Fmt.toDate(it.dateTime).isBefore(windowStart) }
            val base = if (window.size >= 2) window else readings
            val first = base.first()
            val days = ChronoUnit.DAYS.between(Fmt.toDate(first.dateTime), lastDate)
            val dist = lastReading.odometerKm - first.odometerKm
            if (days <= 0 || dist <= 0) return@run null
            val conf = when {
                days >= 14 && base.size >= 3 -> Confidence.HIGH
                days >= 5 -> Confidence.MEDIUM
                else -> Confidence.LOW
            }
            Estimate(dist.toDouble() / days, conf, "Odometer ${Fmt.groupDigits(dist)} km dalam $days hari")
        }

        // 4. Liter per hari
        val litersPerDay: Estimate<Double>? = when {
            kmPerDay != null && kmPerLiter != null -> Estimate(
                kmPerDay.value / kmPerLiter.value,
                minConf(kmPerDay.confidence, kmPerLiter.confidence),
                "${Fmt.decimal(kmPerDay.value, 1)} km/hari ÷ ${Fmt.decimal(kmPerLiter.value, 1)} km/L",
            )
            entries.size >= 2 -> {
                val days = ChronoUnit.DAYS.between(Fmt.toDate(entries.first().dateTime), Fmt.toDate(entries.last().dateTime))
                val lit = entries.dropLast(1).sumOf { it.volumeMl } / 1000.0
                if (days > 0 && lit > 0) Estimate(lit / days, Confidence.LOW, "Total liter dibeli (kecuali pengisian terakhir) ÷ $days hari") else null
            }
            else -> null
        }

        // 5. Biaya per km & per hari
        val avgPrice: Double? = entries.takeLast(5).filter { it.volumeMl > 0 }.let { l ->
            if (l.isEmpty()) null else l.sumOf { it.totalCost }.toDouble() / (l.sumOf { it.volumeMl } / 1000.0)
        }
        val costPerKm: Estimate<Double>? = when {
            recentCycles.isNotEmpty() -> {
                val c = recentCycles.sumOf { it.cost }; val d = recentCycles.sumOf { it.distanceKm }
                Estimate(c.toDouble() / d, kmPerLiter!!.confidence, "Biaya ${Fmt.rupiah(c)} ÷ ${Fmt.groupDigits(d)} km (full-to-full)")
            }
            kmPerLiter != null && avgPrice != null ->
                Estimate(avgPrice / kmPerLiter.value, Confidence.LOW, "Harga rata-rata ${Fmt.rupiah(avgPrice.toLong())}/L ÷ ${Fmt.decimal(kmPerLiter.value, 1)} km/L")
            else -> null
        }
        val costPerDay: Estimate<Double>? = when {
            litersPerDay != null && avgPrice != null -> Estimate(
                litersPerDay.value * avgPrice, litersPerDay.confidence,
                "${Fmt.decimal(litersPerDay.value, 2)} L/hari × ${Fmt.rupiah(avgPrice.toLong())}/L",
            )
            else -> null
        }

        // 6. Simulasi stok BBM sampai pengisian terakhir
        var stockAtLast: Estimate<Double>? = null
        if (last != null) {
            val lastFullIdx = entries.indexOfLast { it.isFullTank }
            if (capacityL != null && lastFullIdx >= 0 && kmPerLiter != null) {
                var stock: Double = capacityL
                for (i in lastFullIdx + 1 until entries.size) {
                    val used = (entries[i].odometerKm - entries[i - 1].odometerKm).coerceAtLeast(0) / kmPerLiter.value
                    stock = (stock - used).coerceAtLeast(0.0)
                    stock = min(capacityL, stock + entries[i].volumeMl / 1000.0)
                }
                val conf = if (lastFullIdx == entries.lastIndex) kmPerLiter.confidence.coerceAtMost(Confidence.HIGH)
                else minConf(kmPerLiter.confidence, Confidence.MEDIUM)
                stockAtLast = Estimate(
                    stock, conf,
                    if (lastFullIdx == entries.lastIndex) "Tangki penuh saat pengisian terakhir (${Fmt.decimal(capacityL, 1)} L)"
                    else "Simulasi dari tangki penuh terakhir + ${entries.lastIndex - lastFullIdx} pengisian sebagian",
                )
            } else if (capacityL != null && last.isFullTank) {
                stockAtLast = Estimate(capacityL, Confidence.MEDIUM, "Tangki penuh saat pengisian terakhir")
            } else {
                // Tidak tahu stok awal: minimal sebesar pembelian terakhir.
                stockAtLast = Estimate(
                    last.volumeMl / 1000.0, Confidence.LOW,
                    "Asumsi minimum: hanya liter yang dibeli terakhir (stok sebelumnya tidak diketahui" +
                        (if (capacityL == null) ", kapasitas tangki belum diisi" else "") + ")",
                )
            }
        }

        // 7. Kondisi saat ini
        val daysSince = last?.let { ChronoUnit.DAYS.between(Fmt.toDate(it.dateTime), today).coerceAtLeast(0) }
        var kmSince: Estimate<Double>? = null
        if (last != null) {
            val knownKm = ((lastReading?.odometerKm ?: last.odometerKm) - last.odometerKm).coerceAtLeast(0).toDouble()
            val readingDate = lastReading?.let { Fmt.toDate(it.dateTime) } ?: Fmt.toDate(last.dateTime)
            val daysAfterReading = ChronoUnit.DAYS.between(readingDate, today).coerceAtLeast(0)
            kmSince = when {
                daysAfterReading == 0L -> Estimate(knownKm, Confidence.HIGH, "Dari odometer tercatat")
                kmPerDay != null -> Estimate(
                    knownKm + kmPerDay.value * daysAfterReading, minConf(kmPerDay.confidence, Confidence.MEDIUM),
                    (if (knownKm > 0) "${Fmt.groupDigits(knownKm.toLong())} km tercatat + " else "") +
                        "estimasi ${Fmt.decimal(kmPerDay.value, 1)} km/hari × $daysAfterReading hari",
                )
                knownKm > 0 -> Estimate(knownKm, Confidence.LOW, "Hanya dari odometer tercatat")
                else -> null
            }
        }

        var remaining: Estimate<Double>? = null
        if (stockAtLast != null) {
            val usedSince: Double? = when {
                kmSince != null && kmPerLiter != null -> kmSince.value / kmPerLiter.value
                litersPerDay != null && daysSince != null -> litersPerDay.value * daysSince
                else -> null
            }
            if (usedSince != null) {
                val conf = listOfNotNull(stockAtLast.confidence, kmPerLiter?.confidence ?: litersPerDay?.confidence, kmSince?.confidence)
                    .reduce(::minConf)
                remaining = Estimate(
                    (stockAtLast.value - usedSince).coerceAtLeast(0.0), conf,
                    "${stockAtLast.basis}; terpakai ±${Fmt.decimal(usedSince, 2)} L sejak pengisian",
                )
            } else {
                remaining = Estimate(stockAtLast.value, Confidence.LOW, stockAtLast.basis + "; pemakaian harian belum diketahui")
            }
        }

        var daysUntilEmpty: Estimate<Double>? = null
        var emptyDate: LocalDate? = null
        var refillDate: LocalDate? = null
        if (remaining != null && litersPerDay != null && litersPerDay.value > 0) {
            val d = remaining.value / litersPerDay.value
            val conf = minConf(remaining.confidence, litersPerDay.confidence)
            daysUntilEmpty = Estimate(d, conf, "Sisa ${Fmt.decimal(remaining.value, 2)} L ÷ ${Fmt.decimal(litersPerDay.value, 2)} L/hari")
            emptyDate = today.plusDays(d.toLong())
            val reserve = (capacityL ?: 0.0) * RESERVE_RATIO
            val dRefill = ((remaining.value - reserve) / litersPerDay.value).coerceAtLeast(0.0)
            refillDate = today.plusDays(dRefill.toLong())
        }

        if (entries.isNotEmpty() && entries.none { it.isFullTank }) warnings += "Belum ada pengisian tangki penuh. Isi penuh dan tandai \"Full tank\" agar konsumsi dapat diukur akurat."
        if (capacityL == null) warnings += "Kapasitas tangki belum diisi, estimasi sisa BBM kurang akurat."

        // 8. Agregasi bulanan/tahunan (berdasarkan uang yang dibelanjakan)
        val monthlyCost = entries.groupBy { YearMonth.from(Fmt.toDate(it.dateTime)) }
            .map { (ym, l) -> ym to l.sumOf { it.totalCost } }.sortedBy { it.first }
        val monthlyLiters = entries.groupBy { YearMonth.from(Fmt.toDate(it.dateTime)) }
            .map { (ym, l) -> ym to l.sumOf { it.volumeMl } / 1000.0 }.sortedBy { it.first }
        val yearlyCost = entries.groupBy { Fmt.toDate(it.dateTime).year }
            .map { (y, l) -> y to l.sumOf { it.totalCost } }.sortedBy { it.first }

        return FuelReport(
            lastFill = last,
            daysSinceLastFill = daysSince,
            cycles = cycles,
            kmPerLiter = kmPerLiter,
            kmPerDay = kmPerDay,
            litersPerDay = litersPerDay,
            costPerDay = costPerDay,
            costPerKm = costPerKm,
            kmSinceLastFill = kmSince,
            remainingLiters = remaining,
            daysUntilEmpty = daysUntilEmpty,
            emptyDate = emptyDate,
            suggestedRefillDate = refillDate,
            monthlyCost = monthlyCost,
            monthlyLiters = monthlyLiters,
            yearlyCost = yearlyCost,
            totalCost = entries.sumOf { it.totalCost },
            totalLiters = entries.sumOf { it.volumeMl } / 1000.0,
            totalDistanceKm = if (entries.size >= 2) entries.last().odometerKm - entries.first().odometerKm else 0,
            warnings = warnings,
        )
    }

    /** Liter = total bayar ÷ harga per liter (dalam mililiter, dibulatkan). */
    fun litersFromPayment(totalCost: Long, pricePerLiter: Long): Long =
        if (pricePerLiter <= 0) 0 else Math.round(totalCost * 1000.0 / pricePerLiter)

    fun costFromLiters(volumeMl: Long, pricePerLiter: Long): Long = Math.round(volumeMl * pricePerLiter / 1000.0)

    fun minConf(a: Confidence, b: Confidence): Confidence = if (a.ordinal >= b.ordinal) a else b

    private fun Confidence.coerceAtMost(other: Confidence): Confidence = minConf(this, other)
}
