package com.moneytracker.app.ui.more

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.moneytracker.app.data.AssetEntity
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.ui.Routes
import com.moneytracker.app.ui.components.BasisNote
import com.moneytracker.app.ui.components.DonutChart
import com.moneytracker.app.ui.components.EmptyState
import com.moneytracker.app.ui.components.FinCard
import com.moneytracker.app.ui.components.FinTextField
import com.moneytracker.app.ui.components.FinTopBar
import com.moneytracker.app.ui.components.KeyValue
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.components.MoneyField
import com.moneytracker.app.ui.components.Slice
import com.moneytracker.app.ui.components.money
import com.moneytracker.app.ui.components.toAmount
import com.moneytracker.app.ui.insights
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.app.ui.thisMonth
import com.moneytracker.core.Fmt
import com.moneytracker.core.Insight
import com.moneytracker.core.Ledger
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

private data class MenuItem(val emoji: String, val title: String, val sub: String, val route: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreScreen(nav: NavHostController) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val sections = listOf(
        "Keuangan" to listOf(
            MenuItem("📊", "Analitik", "Grafik & perbandingan periode", Routes.ANALYTICS),
            MenuItem("🎯", "Anggaran", "Batas bulanan per kategori", Routes.BUDGET),
            MenuItem("🐷", "Tabungan", "Target & estimasi tercapai", Routes.SAVINGS),
            MenuItem("💰", "Pemasukan", "Gaji, jadwal & sumber", Routes.INCOME),
            MenuItem("🤝", "Utang & Piutang", "Cicilan, pinjaman, pelunasan", Routes.DEBTS),
            MenuItem("📆", "Langganan & Rutin", "Netflix, internet, hosting…", Routes.SUBSCRIPTIONS),
            MenuItem("🏠", "Aset", "Properti, kendaraan, emas…", Routes.ASSETS),
            MenuItem("✨", "Asisten Keuangan", "Insight dari data Anda", Routes.ASSISTANT),
        ),
        "Data" to listOf(
            MenuItem("📄", "Laporan", "PDF bulanan", Routes.REPORTS),
            MenuItem("📥", "Impor CSV/Excel", "Mutasi bank & e-wallet", Routes.IMPORT),
            MenuItem("🗂️", "Kategori", "Tambah, ubah, hapus", Routes.CATEGORIES),
            MenuItem("💾", "Backup & Restore", "File terenkripsi", Routes.BACKUP),
            MenuItem("🕘", "Riwayat perubahan", "Audit log transaksi", Routes.AUDIT),
            MenuItem("⚙️", "Pengaturan", "PIN, biometrik, tema, akun", Routes.SETTINGS),
        ),
    )
    Column(Modifier.fillMaxSize()) {
        FinTopBar("Lainnya")
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            sections.forEach { (title, items) ->
                item { Text(title, style = MaterialTheme.typography.titleSmall, color = Fin.colors.muted, modifier = Modifier.padding(top = 8.dp)) }
                item {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = if (wide) 3 else 2) {
                        items.forEach { m ->
                            FinCard(Modifier.weight(1f), onClick = { nav.navigate(m.route) }, padding = PaddingValues(14.dp)) {
                                Text(m.emoji, fontSize = 24.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(m.title, style = MaterialTheme.typography.titleSmall)
                                Text(m.sub, style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================== Asisten =====================
@Composable
fun InsightCard(i: Insight) {
    val color = when (i.severity) {
        Insight.Severity.ALERT -> Fin.colors.expense
        Insight.Severity.WARNING -> Fin.colors.warning
        Insight.Severity.POSITIVE -> Fin.colors.income
        Insight.Severity.INFO -> MaterialTheme.colorScheme.primary
    }
    FinCard(color = color.copy(alpha = 0.08f)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(when (i.severity) { Insight.Severity.ALERT -> "🚨"; Insight.Severity.WARNING -> "⚠️"; Insight.Severity.POSITIVE -> "✅"; Insight.Severity.INFO -> "💡" }, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(i.title, style = MaterialTheme.typography.titleSmall, color = color)
                Text(i.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                BasisNote("Dasar perhitungan: ${i.basis}")
            }
        }
    }
}

@Composable
fun AssistantScreen(s: Snapshot, onBack: () -> Unit) {
    val list = remember(s) { s.insights() }
    Column(Modifier.fillMaxSize()) {
        FinTopBar("Asisten Keuangan", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "Analisis dibuat dari transaksi Anda sendiri, dihitung di perangkat. Insight hanya muncul jika datanya cukup, dan selalu disertai dasar perhitungan.",
                    style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted,
                )
            }
            if (list.isEmpty()) item { EmptyState("✨", "Belum cukup data", "Catat transaksi minimal 1–2 minggu agar asisten dapat memberi analisis yang bermakna.") }
            items(list, key = { it.id }) { InsightCard(it) }
        }
    }
}

// ===================== Laporan =====================
@Composable
fun ReportsScreen(s: Snapshot, onBack: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(thisMonth()) }
    val sum = remember(s, month) { Ledger.summarize(s.txCore, month) }
    val byCat = remember(s, month) {
        val (f, t) = Ledger.monthRange(month)
        Ledger.expenseByRootCategory(s.txCore.filter { it.dateTime in f until t }, s.catInfo).entries.sortedByDescending { it.value }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch { app.exporter.exportMonthlyPdf(uri, s, month); Toast.makeText(ctx, "Laporan PDF tersimpan", Toast.LENGTH_SHORT).show() }
    }
    val chart = Fin.colors.chart
    Column(Modifier.fillMaxSize()) {
        FinTopBar("Laporan", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Sebelumnya") }
                    Text(Fmt.month(month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Berikutnya") }
                }
            }
            item {
                FinCard {
                    KeyValue("Pemasukan", money(sum.income), valueColor = Fin.colors.income)
                    KeyValue("Pengeluaran", money(sum.expense), valueColor = Fin.colors.expense)
                    KeyValue("Arus kas bersih", money(sum.net, true))
                    KeyValue("Dialokasikan ke tabungan", money(sum.savings))
                    KeyValue("Rasio tabungan", sum.savingsRate?.let { Fmt.percent(it) } ?: "-")
                }
            }
            item {
                FinCard {
                    Text("Pengeluaran per kategori", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    DonutChart(byCat.mapIndexed { i, (c, v) -> Slice(c?.let { s.categoryMap[it]?.name } ?: "Tanpa kategori", v, c?.let { s.categoryMap[it]?.color }?.let { Color(it) } ?: chart[i % chart.size]) })
                }
            }
            item { Button(onClick = { pdf.launch("laporan-moneytracker-$month.pdf") }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Ekspor PDF ${Fmt.month(month)}") } }
            item { BasisNote("Ekspor CSV & Excel tersedia di halaman Transaksi (ikon unduh) dan mengikuti filter yang aktif.") }
        }
    }
}

// ===================== Aset =====================
@Composable
fun AssetsScreen(s: Snapshot, onBack: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<AssetEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    val nw = s.netWorth
    Column(Modifier.fillMaxSize()) {
        FinTopBar("Aset & Net Worth", onBack = onBack) { IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "Tambah aset") } }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                FinCard {
                    Text("Net worth", style = MaterialTheme.typography.labelMedium, color = Fin.colors.muted)
                    Text(money(nw.netWorth), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    KeyValue("Saldo wallet (positif)", money(nw.liquidAssets))
                    KeyValue("Aset lain", money(s.assets.sumOf { it.value }))
                    if (s.offWalletSavings > 0) KeyValue("Tabungan di luar wallet", money(s.offWalletSavings))
                    KeyValue("Piutang", money(nw.receivables))
                    KeyValue("Total aset", money(nw.totalAssets), valueColor = Fin.colors.income)
                    KeyValue("Kartu kredit & saldo negatif", money(nw.walletLiabilities))
                    KeyValue("Sisa utang", money(nw.debts))
                    KeyValue("Total kewajiban", money(nw.totalLiabilities), valueColor = Fin.colors.expense)
                    BasisNote("Net worth = total aset − total kewajiban.")
                }
            }
            if (s.assets.isEmpty()) item { Text("Tambahkan aset non-tunai seperti rumah, kendaraan, emas, atau perangkat untuk melengkapi net worth.", style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted) }
            items(s.assets, key = { it.id }) { a ->
                FinCard(onClick = { editing = a }, padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.titleSmall)
                            Text("${a.category} · diperbarui ${Fmt.dateShort(a.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                        }
                        Text(money(a.value), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
    if (creating || editing != null) {
        val e = editing
        var name by remember { mutableStateOf(e?.name ?: "") }
        var cat by remember { mutableStateOf(e?.category ?: "Properti") }
        var value by remember { mutableStateOf(e?.value?.toString() ?: "") }
        val close = { creating = false; editing = null }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(if (e == null) "Aset baru" else "Edit aset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FinTextField(name, { name = it }, "Nama aset")
                    FinTextField(cat, { cat = it }, "Kategori", supporting = "Properti, Kendaraan, Emas, Elektronik, …")
                    MoneyField(value, { value = it }, "Nilai saat ini")
                    if (e != null) TextButton(onClick = { scope.launch { app.repository.deleteAsset(e) }; close() }) { Text("Hapus aset", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch { app.repository.saveAsset(AssetEntity(id = e?.id ?: 0, name = name.trim(), category = cat.trim(), value = value.toAmount())) }
                    close()
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = close) { Text("Batal") } },
        )
    }
}

// ===================== Audit =====================
@Composable
fun AuditScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val log by app.repository.auditLog().collectAsState(initial = emptyList())
    Column(Modifier.fillMaxSize()) {
        FinTopBar("Riwayat perubahan", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (log.isEmpty()) item { EmptyState("🕘", "Belum ada riwayat", "Setiap pembuatan, perubahan, dan penghapusan transaksi tercatat di sini.") }
            items(log, key = { it.id }) { h ->
                FinCard(padding = PaddingValues(12.dp)) {
                    Text(
                        "${when (h.action) { "CREATE" -> "➕ Dibuat"; "UPDATE" -> "✏️ Diubah"; else -> "🗑️ Dihapus" }} · ${h.entity} · ${Fmt.dateTime(h.timestamp)}",
                        style = MaterialTheme.typography.labelMedium, color = Fin.colors.muted,
                    )
                    Text(h.summary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
