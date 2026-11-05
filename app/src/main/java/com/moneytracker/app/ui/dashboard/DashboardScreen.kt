package com.moneytracker.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.moneytracker.app.LocalUi
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.ui.Routes
import com.moneytracker.app.ui.components.BarChart
import com.moneytracker.app.ui.components.BarSeries
import com.moneytracker.app.ui.components.BasisNote
import com.moneytracker.app.ui.components.EmptyState
import com.moneytracker.app.ui.components.FinCard
import com.moneytracker.app.ui.components.LineChart
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.components.SectionHeader
import com.moneytracker.app.ui.components.StatTile
import com.moneytracker.app.ui.components.money
import com.moneytracker.app.ui.insights
import com.moneytracker.app.ui.more.InsightCard
import com.moneytracker.app.ui.thisMonth
import com.moneytracker.app.ui.today
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.app.ui.transactions.TransactionRow
import com.moneytracker.app.ui.vehicles.LastRefuelPanel
import com.moneytracker.core.Analytics
import com.moneytracker.core.Fmt
import com.moneytracker.core.Granularity
import com.moneytracker.core.Ledger
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(s: Snapshot, nav: NavHostController) {
    val ui = LocalUi.current
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    val today = today()
    val month = thisMonth()

    val data = remember(s) {
        val sum = Ledger.summarize(s.txCore, month)
        val prevMonth = month.minusMonths(1)
        val (pFrom, _) = Ledger.monthRange(prevMonth)
        val pSameEnd = Fmt.toMillis(prevMonth.atDay(minOf(today.dayOfMonth, prevMonth.lengthOfMonth())).plusDays(1))
        val prevSame = Ledger.summarize(s.txCore, pFrom, pSameEnd)
        val buckets = Analytics.buckets(s.txCore, Granularity.MONTH, today, 6)
        val series = Ledger.dailyBalanceSeries(s.walletInfos.filter { w -> s.countedWallets.any { it.id == w.id } }, s.txCore, today.minusDays(29), today)
        val upcoming = s.recurring.filter { it.active && LocalDate.ofEpochDay(it.nextDate) <= today.plusDays(7) }.sortedBy { it.nextDate }
        DashData(sum, prevSame, buckets, series, s.insights(), upcoming)
    }

    LazyColumn(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Halo, ${s.user?.name ?: "Pengguna"} 👋", style = MaterialTheme.typography.titleLarge)
                    Text(Fmt.dateWithDay(today), style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted)
                }
                IconButton(onClick = { ui.hideBalances = !ui.hideBalances; app.security.hideBalances = ui.hideBalances }) {
                    Icon(if (ui.hideBalances) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Sembunyikan saldo")
                }
                IconButton(onClick = { nav.navigate(Routes.ASSISTANT) }) { Icon(Icons.Outlined.AutoAwesome, "Asisten", tint = MaterialTheme.colorScheme.primary) }
            }
        }

        if (s.wallets.isEmpty()) {
            item {
                FinCard {
                    EmptyState("🏦", "Tambahkan rekening pertama", "Mulai dengan menambahkan rekening bank, e-wallet, atau uang tunai beserta saldo saat ini.",
                        action = "Tambah wallet", onAction = { nav.navigate(Routes.walletForm()) })
                }
            }
        }

        // ---------- Hero: Net worth ----------
        item {
            val nw = s.netWorth
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Fin.colors.heroGradient).clickable { nav.navigate(Routes.WALLETS) }.padding(20.dp)) {
                Column {
                    Text("Total saldo", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                    Text(money(s.totalBalance), style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("${s.countedWallets.size} wallet · net worth ${money(nw.netWorth)}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeroMini("Aset", money(nw.totalAssets), Modifier.weight(1f))
                        HeroMini("Kewajiban", money(nw.totalLiabilities), Modifier.weight(1f))
                        HeroMini("Net worth", money(nw.netWorth), Modifier.weight(1f))
                    }
                }
            }
        }

        // ---------- Statistik ----------
        item {
            val sum = data.summary
            val cols = if (wide) 3 else 2
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = cols) {
                val m = Modifier.weight(1f)
                StatTile("Pemasukan ${Fmt.monthShort(month)}", money(sum.income), Icons.AutoMirrored.Outlined.TrendingUp, Fin.colors.income, m) { nav.navigate(Routes.INCOME) }
                StatTile("Pengeluaran ${Fmt.monthShort(month)}", money(sum.expense), Icons.AutoMirrored.Outlined.TrendingDown, Fin.colors.expense, m,
                    sub = changeText(sum.expense, data.prevSame.expense)) { nav.navigate(Routes.ANALYTICS) }
                StatTile("Total tabungan", money(s.totalSavings), Icons.Outlined.Savings, Fin.colors.savings, m) { nav.navigate(Routes.SAVINGS) }
                StatTile("Total aset", money(s.netWorth.totalAssets), Icons.Outlined.AccountBalance, Fin.colors.transfer, m) { nav.navigate(Routes.ASSETS) }
                StatTile("Total utang", money(s.netWorth.totalLiabilities), Icons.Outlined.CreditCard, Fin.colors.warning, m, sub = "termasuk kartu kredit") { nav.navigate(Routes.DEBTS) }
                StatTile("Berhasil ditabung", sum.savingsRate?.let { Fmt.percent(it) } ?: "–", Icons.Outlined.Percent, MaterialTheme.colorScheme.primary, m,
                    sub = "dari pemasukan bulan ini")
            }
        }

        // ---------- Quick actions ----------
        item {
            val actions = listOf(
                Triple("Pemasukan", "💰") { nav.navigate(Routes.txForm(type = "INCOME")) },
                Triple("Pengeluaran", "💸") { nav.navigate(Routes.txForm(type = "EXPENSE")) },
                Triple("Transfer", "🔁") { nav.navigate(Routes.txForm(type = "TRANSFER")) },
                Triple("Isi BBM", "⛽") { nav.navigate(s.vehicles.firstOrNull { !it.archived }?.let { Routes.fuelForm(it.id) } ?: Routes.vehicleForm()) },
                Triple("Transaksi rutin", "📆") { nav.navigate(Routes.recurringForm(kind = "RECURRING_EXPENSE")) },
                Triple("Target tabungan", "🎯") { nav.navigate(Routes.goalForm()) },
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(actions) { (label, emoji, action) ->
                    Column(
                        Modifier.width(84.dp).clip(RoundedCornerShape(18.dp)).background(Fin.colors.card).clickable(onClick = action).padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 20.sp) }
                        Spacer(Modifier.height(6.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }

        // ---------- Insight ----------
        if (data.insights.isNotEmpty()) {
            item { SectionHeader("Smart insights", action = "Lihat semua", onAction = { nav.navigate(Routes.ASSISTANT) }) }
            items(data.insights.take(3), key = { it.id }) { InsightCard(it) }
        }

        // ---------- Cash flow ----------
        item {
            FinCard {
                Text("Arus kas 6 bulan", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                BarChart(
                    labels = data.buckets.map { it.label },
                    series = listOf(
                        BarSeries("Masuk", Fin.colors.income, data.buckets.map { it.income }),
                        BarSeries("Keluar", Fin.colors.expense, data.buckets.map { it.expense }),
                    ),
                )
                BasisNote("Transfer antar-wallet, alokasi tabungan, dan pelunasan kartu kredit tidak dihitung.")
            }
        }
        item {
            FinCard {
                Text("Saldo 30 hari terakhir", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LineChart(data.balanceSeries.map { Fmt.dayMonth(it.first) }, data.balanceSeries.map { it.second.toDouble() }, MaterialTheme.colorScheme.primary, title = "Saldo")
            }
        }
        item {
            FinCard {
                Text("Bulan ini vs bulan lalu", style = MaterialTheme.typography.titleMedium)
                Text("Periode setara: tgl 1–${today.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                Spacer(Modifier.height(10.dp))
                CompareRow("Pengeluaran", data.summary.expense, data.prevSame.expense, higherIsBad = true)
                CompareRow("Pemasukan", data.summary.income, data.prevSame.income, higherIsBad = false)
                CompareRow("Ditabung", data.summary.savings, data.prevSame.savings, higherIsBad = false)
            }
        }

        // ---------- Jatuh tempo ----------
        if (data.upcoming.isNotEmpty()) {
            item { SectionHeader("Jatuh tempo 7 hari", action = "Semua", onAction = { nav.navigate(Routes.SUBSCRIPTIONS) }) }
            item {
                FinCard {
                    data.upcoming.take(5).forEach { r ->
                        val due = LocalDate.ofEpochDay(r.nextDate)
                        val days = ChronoUnit.DAYS.between(today, due)
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${money(r.amount)} · " + when { days < 0 -> "terlambat ${-days} hari"; days == 0L -> "hari ini"; else -> Fmt.dateShort(due) },
                                    style = MaterialTheme.typography.bodySmall, color = if (days < 0) Fin.colors.expense else Fin.colors.muted,
                                )
                            }
                            TextButton(onClick = { scope.launch { app.repository.postRecurring(r) } }) { Text("Catat") }
                        }
                    }
                }
            }
        }

        // ---------- BBM ----------
        s.vehicles.firstOrNull { !it.archived }?.let { v ->
            item { SectionHeader("Analisis BBM terakhir", action = "Detail", onAction = { nav.navigate(Routes.vehicle(v.id)) }) }
            item { LastRefuelPanel(s, v, compact = true, onAddFuel = { nav.navigate(Routes.fuelForm(v.id)) }) }
        }

        // ---------- Transaksi terbaru ----------
        if (s.transactions.isNotEmpty()) {
            item { SectionHeader("Transaksi terbaru", action = "Semua", onAction = { nav.navigate(Routes.TRANSACTIONS) }) }
            item {
                FinCard(padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    s.transactions.take(6).forEach { t -> TransactionRow(s, t, onClick = { nav.navigate(Routes.txDetail(t.id)) }, showDate = true) }
                }
            }
        }
    }
}

private data class DashData(
    val summary: Ledger.PeriodSummary,
    val prevSame: Ledger.PeriodSummary,
    val buckets: List<com.moneytracker.core.Bucket>,
    val balanceSeries: List<Pair<LocalDate, Long>>,
    val insights: List<com.moneytracker.core.Insight>,
    val upcoming: List<com.moneytracker.app.data.RecurringEntity>,
)

private fun changeText(now: Long, before: Long): String? {
    if (before <= 0) return null
    val r = (now - before).toDouble() / before
    return (if (r >= 0) "▲ " else "▼ ") + Fmt.percent(kotlin.math.abs(r)) + " vs bln lalu"
}

@Composable
private fun HeroMini(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.14f)).padding(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun CompareRow(label: String, now: Long, before: Long, higherIsBad: Boolean) {
    val diff = now - before
    val ratio = if (before > 0) diff.toDouble() / before else null
    val bad = if (higherIsBad) diff > 0 else diff < 0
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 120.dp)) {
            Text(money(now), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "vs ${money(before)}" + (ratio?.let { " (" + (if (it >= 0) "+" else "") + Fmt.percent(it) + ")" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = if (diff == 0L) Fin.colors.muted else if (bad) Fin.colors.expense else Fin.colors.income,
            )
        }
    }
}
