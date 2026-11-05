package com.moneytracker.app.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.ui.components.BarChart
import com.moneytracker.app.ui.components.BarSeries
import com.moneytracker.app.ui.components.BasisNote
import com.moneytracker.app.ui.components.DonutChart
import com.moneytracker.app.ui.components.FinCard
import com.moneytracker.app.ui.components.FinProgress
import com.moneytracker.app.ui.components.FinTopBar
import com.moneytracker.app.ui.components.KeyValue
import com.moneytracker.app.ui.components.LineChart
import com.moneytracker.app.ui.components.Option
import com.moneytracker.app.ui.components.OptionSheet
import com.moneytracker.app.ui.components.SegmentedTabs
import com.moneytracker.app.ui.components.Slice
import com.moneytracker.app.ui.components.WalletBadge
import com.moneytracker.app.ui.components.money
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.app.ui.today
import com.moneytracker.app.ui.transactions.TypeFilter
import com.moneytracker.app.ui.transactions.categoryOptions
import com.moneytracker.core.Analytics
import com.moneytracker.core.Fmt
import com.moneytracker.core.Granularity
import com.moneytracker.core.Ledger
import com.moneytracker.core.TxType

@Composable
fun AnalyticsScreen(s: Snapshot, @Suppress("UNUSED_PARAMETER") nav: NavHostController) {
    var gIndex by rememberSaveable { mutableStateOf(2) }
    val g = Granularity.entries[gIndex]
    var walletFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var vehicleFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var typeFilter by rememberSaveable { mutableStateOf(TypeFilter.ALL) }
    var sheet by remember { mutableStateOf("") }
    val chart = Fin.colors.chart

    val data = remember(s, g, walletFilter, categoryFilter, vehicleFilter, typeFilter) {
        val catIds = categoryFilter?.let { Ledger.descendantsOf(it, s.catInfo.values) }
        val txs = s.txCore.filter { t ->
            (walletFilter == null || t.walletId == walletFilter || t.toWalletId == walletFilter) &&
                (catIds == null || t.categoryId in catIds) &&
                (vehicleFilter == null || t.vehicleId == vehicleFilter) &&
                when (typeFilter) {
                    TypeFilter.ALL -> true
                    TypeFilter.EXPENSE -> t.type == TxType.EXPENSE
                    TypeFilter.INCOME -> t.type == TxType.INCOME
                    TypeFilter.MOVEMENT -> t.type in setOf(TxType.TRANSFER, TxType.SAVINGS_ALLOCATION, TxType.CC_SETTLEMENT)
                    TypeFilter.DEBT -> t.type in setOf(TxType.DEBT_PAYMENT, TxType.DEBT_COLLECTION, TxType.LOAN_GIVEN, TxType.LOAN_RECEIVED)
                }
        }
        val buckets = Analytics.buckets(txs, g, today())
        val cur = buckets.last()
        val prev = buckets.getOrNull(buckets.size - 2)
        val curFrom = Fmt.toMillis(cur.start)
        val curTo = Fmt.toMillis(Analytics.nextBucket(cur.start, g))
        val periodTxs = txs.filter { it.dateTime in curFrom until curTo }
        val prevTxs = prev?.let { p -> txs.filter { it.dateTime in Fmt.toMillis(p.start) until curFrom } } ?: emptyList()
        val byCat = Ledger.expenseByRootCategory(periodTxs, s.catInfo).entries.sortedByDescending { it.value }
        val prevByCat = Ledger.expenseByRootCategory(prevTxs, s.catInfo)
        val allRange = buckets.first().start
        val rangeTxs = txs.filter { it.dateTime >= Fmt.toMillis(allRange) }
        val needWant = Analytics.needWant(rangeTxs, s.catInfo)
        val walletUsage = Analytics.walletUsage(rangeTxs)
        // Net worth wallet (saldo bersih semua wallet termasuk kartu kredit) di akhir tiap bulan, 12 bulan
        val nwSeries = Ledger.dailyBalanceSeries(s.walletInfos.filter { w -> s.walletMap[w.id]?.includeInTotal == true || w.type.isLiability }, s.txCore, today().minusMonths(12).withDayOfMonth(1), today())
            .filter { (d, _) -> d == d.withDayOfMonth(d.lengthOfMonth()) || d == today() }
        AnalyticsData(buckets, cur, prev, byCat, prevByCat, needWant, walletUsage, nwSeries)
    }

    Column(Modifier.fillMaxSize()) {
        FinTopBar("Analitik Keuangan")
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SegmentedTabs(Granularity.entries.map { it.label }, gIndex, { gIndex = it }) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(walletFilter != null, { sheet = "wallet" }, { Text(walletFilter?.let { s.walletMap[it]?.name } ?: "Semua wallet") }) }
                    item { FilterChip(categoryFilter != null, { sheet = "category" }, { Text(categoryFilter?.let { s.categoryMap[it]?.name } ?: "Semua kategori") }) }
                    item { FilterChip(typeFilter != TypeFilter.ALL, { sheet = "type" }, { Text(typeFilter.label) }) }
                    if (s.vehicles.isNotEmpty()) item { FilterChip(vehicleFilter != null, { sheet = "vehicle" }, { Text(vehicleFilter?.let { s.vehicleMap[it]?.name } ?: "Semua kendaraan") }) }
                }
            }
            // Periode berjalan vs sebelumnya
            item {
                FinCard {
                    Text("Periode ini: ${periodLabel(data.current.start, g)}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Metric("Pemasukan", money(data.current.income), Fin.colors.income, Modifier.weight(1f))
                        Metric("Pengeluaran", money(data.current.expense), Fin.colors.expense, Modifier.weight(1f))
                        Metric("Arus kas bersih", money(data.current.net, true), if (data.current.net >= 0) Fin.colors.income else Fin.colors.expense, Modifier.weight(1f))
                    }
                    data.previous?.let { p ->
                        Spacer(Modifier.height(10.dp))
                        KeyValue("Pengeluaran periode sebelumnya", money(p.expense))
                        if (p.expense > 0) {
                            val r = (data.current.expense - p.expense).toDouble() / p.expense
                            KeyValue("Perubahan", (if (r >= 0) "+" else "") + Fmt.percent(r), valueColor = if (r > 0) Fin.colors.expense else Fin.colors.income)
                        }
                    }
                }
            }
            item {
                FinCard {
                    Text("Pemasukan vs pengeluaran (${g.label.lowercase()})", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    BarChart(data.buckets.map { it.label }, listOf(
                        BarSeries("Masuk", Fin.colors.income, data.buckets.map { it.income }),
                        BarSeries("Keluar", Fin.colors.expense, data.buckets.map { it.expense }),
                    ))
                }
            }
            item {
                FinCard {
                    Text("Net cash flow", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LineChart(data.buckets.map { it.label }, data.buckets.map { it.net.toDouble() }, MaterialTheme.colorScheme.primary, title = "Bersih")
                }
            }
            item {
                FinCard {
                    Text("Kategori paling boros (periode ini)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    DonutChart(data.byCategory.mapIndexed { i, (cat, amt) ->
                        Slice(cat?.let { s.categoryMap[it]?.name } ?: "Tanpa kategori", amt, cat?.let { s.categoryMap[it]?.color?.let { c -> Color(c) } } ?: chart[i % chart.size])
                    })
                    Spacer(Modifier.height(12.dp))
                    data.byCategory.take(6).forEach { (cat, amt) ->
                        val before = data.prevByCategory[cat] ?: 0
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(cat?.let { s.categoryMap[it]?.let { c -> "${c.icon} ${c.name}" } } ?: "Tanpa kategori", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money(amt), style = MaterialTheme.typography.labelLarge)
                                if (before > 0) {
                                    val r = (amt - before).toDouble() / before
                                    Text((if (r >= 0) "▲ " else "▼ ") + Fmt.percent(kotlin.math.abs(r)), style = MaterialTheme.typography.labelSmall, color = if (r > 0) Fin.colors.expense else Fin.colors.income)
                                }
                            }
                        }
                    }
                }
            }
            item {
                FinCard {
                    Text("Rasio tabungan & kebutuhan", style = MaterialTheme.typography.titleMedium)
                    val inc = data.buckets.sumOf { it.income }
                    val exp = data.buckets.sumOf { it.expense }
                    val rate = if (inc > 0) (inc - exp).toDouble() / inc else null
                    Spacer(Modifier.height(8.dp))
                    KeyValue("Rasio tabungan (seluruh rentang)", rate?.let { Fmt.percent(it) } ?: "-")
                    rate?.let { FinProgress(it.toFloat().coerceIn(0f, 1f), Fin.colors.savings) }
                    BasisNote("(Pemasukan − pengeluaran) ÷ pemasukan pada ${data.buckets.size} periode yang ditampilkan.")
                    Spacer(Modifier.height(12.dp))
                    val nw = data.needWant
                    if (nw.total > 0) {
                        Text("Kebutuhan vs keinginan", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth()) {
                            listOf(Triple("Kebutuhan", nw.need, Fin.colors.transfer), Triple("Keinginan", nw.want, Fin.colors.warning), Triple("Lainnya", nw.other, Fin.colors.muted))
                                .filter { it.second > 0 }.forEach { (l, v, c) ->
                                    Column(Modifier.weight(v.toFloat().coerceAtLeast(nw.total * 0.12f))) {
                                        FinProgress(1f, c)
                                        Text("$l ${Fmt.percent(v.toDouble() / nw.total)}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp, end = 4.dp))
                                    }
                                }
                        }
                        BasisNote("Berdasarkan penanda Kebutuhan/Keinginan di setiap kategori (bisa diubah di Kategori).")
                    }
                }
            }
            item {
                FinCard {
                    Text("Wallet paling sering digunakan", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    if (data.walletUsage.isEmpty()) Text("Belum ada data", color = Fin.colors.muted)
                    data.walletUsage.take(5).forEach { (wid, count, spent) ->
                        val w = s.walletMap[wid]
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            WalletBadge(w, 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(w?.name ?: "?", modifier = Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text("$count transaksi", style = MaterialTheme.typography.labelLarge)
                                Text("keluar ${money(spent)}", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                            }
                        }
                    }
                }
            }
            item {
                FinCard {
                    Text("Tren saldo bersih wallet (12 bulan)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LineChart(data.netWorthSeries.map { Fmt.monthShortYear(java.time.YearMonth.from(it.first)) }, data.netWorthSeries.map { it.second.toDouble() }, Fin.colors.savings, title = "Saldo bersih")
                    KeyValue("Net worth saat ini", money(s.netWorth.netWorth))
                    BasisNote("Grafik = saldo akhir bulan semua wallet dikurangi kartu kredit. Net worth saat ini juga memperhitungkan aset lain, piutang, dan utang.")
                }
            }
        }
    }

    when (sheet) {
        "wallet" -> OptionSheet("Wallet", listOf(Option<Long?>(null, "Semua wallet")) + s.wallets.map { Option<Long?>(it.id, it.name) }, walletFilter, { sheet = "" }) { walletFilter = it }
        "category" -> OptionSheet("Kategori", listOf(Option<Long?>(null, "Semua kategori")) + categoryOptions(s, null), categoryFilter, { sheet = "" }) { categoryFilter = it }
        "vehicle" -> OptionSheet("Kendaraan", listOf(Option<Long?>(null, "Semua kendaraan")) + s.vehicles.map { Option<Long?>(it.id, it.name) }, vehicleFilter, { sheet = "" }) { vehicleFilter = it }
        "type" -> OptionSheet("Jenis", TypeFilter.entries.map { Option(it, it.label) }, typeFilter, { sheet = "" }) { typeFilter = it }
    }
}

private data class AnalyticsData(
    val buckets: List<com.moneytracker.core.Bucket>,
    val current: com.moneytracker.core.Bucket,
    val previous: com.moneytracker.core.Bucket?,
    val byCategory: List<Map.Entry<Long?, Long>>,
    val prevByCategory: Map<Long?, Long>,
    val needWant: Analytics.NeedWant,
    val walletUsage: List<Triple<Long, Int, Long>>,
    val netWorthSeries: List<Pair<java.time.LocalDate, Long>>,
)

private fun periodLabel(start: java.time.LocalDate, g: Granularity) = when (g) {
    Granularity.DAY -> Fmt.dateWithDay(start)
    Granularity.WEEK -> "minggu ${Fmt.dayMonth(start)}"
    Granularity.MONTH -> Fmt.month(java.time.YearMonth.from(start))
    Granularity.YEAR -> start.year.toString()
}

@Composable
private fun Metric(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
        Text(value, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1)
    }
}
