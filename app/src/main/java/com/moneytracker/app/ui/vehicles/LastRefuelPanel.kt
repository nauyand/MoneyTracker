package com.moneytracker.app.ui.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.data.VehicleEntity
import com.moneytracker.app.ui.components.BasisNote
import com.moneytracker.app.ui.components.ConfidenceChip
import com.moneytracker.app.ui.components.EmojiIcon
import com.moneytracker.app.ui.components.FinCard
import com.moneytracker.app.ui.components.FuelGauge
import com.moneytracker.app.ui.components.money
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.app.ui.today
import com.moneytracker.core.Confidence
import com.moneytracker.core.Estimate
import com.moneytracker.core.Fmt
import com.moneytracker.core.FuelReport
import com.moneytracker.core.VehicleType
import java.time.temporal.ChronoUnit

fun vehicleEmoji(v: VehicleEntity) = when (v.type) { VehicleType.MOTOR -> "🏍️"; VehicleType.MOBIL -> "🚗"; VehicleType.LAINNYA -> "🚐" }

/**
 * Panel "Last Refuel Analysis": menjawab kapan terakhir isi, berapa liter & biaya, sudah berapa hari,
 * pemakaian per hari, km tempuh, estimasi sisa, dan kapan perlu isi lagi — lengkap dengan tingkat akurasi.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LastRefuelPanel(s: Snapshot, v: VehicleEntity, compact: Boolean = false, onAddFuel: () -> Unit, report: FuelReport? = null) {
    val r = report ?: remember(s, v) { s.fuelReport(v) }
    val last = r.lastFill
    FinCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiIcon(vehicleEmoji(v), MaterialTheme.colorScheme.primary, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(v.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(v.type.label, v.fuelType, v.tankCapacityMl?.let { "Tangki ${Fmt.decimal(it / 1000.0, 1)} L" }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted,
                )
            }
        }
        if (last == null) {
            Spacer(Modifier.height(12.dp))
            Text("Belum ada data pengisian BBM.", style = MaterialTheme.typography.bodyMedium, color = Fin.colors.muted)
            Button(onClick = onAddFuel, modifier = Modifier.padding(top = 8.dp)) { Text("Catat pengisian pertama") }
            return@FinCard
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val cap = v.tankCapacityMl?.let { it / 1000.0 }
            val ratio = r.remainingLiters?.let { rem -> cap?.let { (rem.value / it).toFloat() } }
            FuelGauge(ratio, MaterialTheme.colorScheme.primary, size = if (compact) 112.dp else 140.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sisa ±", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                    Text(r.remainingLiters?.let { Fmt.decimal(it.value, 1) + " L" } ?: "?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (cap != null && r.remainingLiters != null) Text("dari ${Fmt.decimal(cap, 1)} L", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Pengisian terakhir", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                Text(Fmt.date(last.dateTime), style = MaterialTheme.typography.titleSmall)
                Text("${last.fuelType} · ${money(last.pricePerLiter)}/L", style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted)
                Text("${Fmt.liters(last.volumeMl)} · ${money(last.totalCost)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (last.isFullTank) Text("Full tank", style = MaterialTheme.typography.labelSmall, color = Fin.colors.income)
                else Text("Isi sebagian", style = MaterialTheme.typography.labelSmall, color = Fin.colors.warning)
            }
        }
        Spacer(Modifier.height(14.dp))
        r.suggestedRefillDate?.let { d ->
            val days = ChronoUnit.DAYS.between(today(), d)
            FinCard(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                Text("Estimasi isi ulang: ${Fmt.date(d)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    (if (days <= 0) "Disarankan isi sekarang" else "±$days hari lagi") +
                        (r.emptyDate?.let { " · perkiraan habis ${Fmt.dayMonth(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
                r.daysUntilEmpty?.let { Row(Modifier.padding(top = 6.dp)) { ConfidenceChip(it.confidence) } }
            }
            Spacer(Modifier.height(10.dp))
        }

        val metrics = buildList {
            add(Metric("Sudah digunakan", "${r.daysSinceLastFill ?: 0} hari", null))
            r.kmSinceLastFill?.let { add(Metric("Km sejak isi", "±${Fmt.decimal(it.value, 0)} km", it)) }
            r.litersPerDay?.let { add(Metric("Rata-rata pemakaian", "${Fmt.decimal(it.value, 2)} L/hari", it)) }
            r.costPerDay?.let { add(Metric("Biaya BBM", "${money(it.value.toLong())}/hari", it)) }
            r.kmPerLiter?.let { add(Metric("Konsumsi", "${Fmt.decimal(it.value, 1)} km/L", it)) }
            r.kmPerDay?.let { add(Metric("Jarak harian", "${Fmt.decimal(it.value, 1)} km/hari", it)) }
            if (!compact) r.costPerKm?.let { add(Metric("Biaya per km", money(it.value.toLong()), it)) }
            if (!compact) r.daysUntilEmpty?.let { add(Metric("Daya tahan BBM", "±${Fmt.decimal(it.value, 1)} hari", it)) }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
            metrics.forEach { m -> MetricTile(m, Modifier.weight(1f)) }
        }
        if (!compact) {
            Spacer(Modifier.height(6.dp))
            listOfNotNull(r.kmPerLiter, r.litersPerDay, r.remainingLiters, r.kmSinceLastFill).forEach { BasisNote(it.basis) }
        }
        r.warnings.take(if (compact) 1 else 3).forEach { BasisNote("⚠️ $it") }
        if (r.cycles.isEmpty()) BasisNote("Angka bertanda akurasi rendah masih berupa estimasi. Untuk hasil akurat, isi penuh (full tank) minimal dua kali dan catat odometer.")
    }
}

private data class Metric(val label: String, val value: String, val estimate: Estimate<*>?)

@Composable
private fun MetricTile(m: Metric, modifier: Modifier) {
    Column(modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.label, style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted, modifier = Modifier.weight(1f, fill = false))
            m.estimate?.let { e ->
                val c = when (e.confidence) { Confidence.HIGH -> Fin.colors.income; Confidence.MEDIUM -> Fin.colors.warning; Confidence.LOW -> Fin.colors.expense }
                Text(" ●", style = MaterialTheme.typography.labelSmall, color = c)
            }
        }
        Text(m.value, style = MaterialTheme.typography.titleSmall)
    }
}
