package com.moneytracker.app.ui.transactions

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.data.TransactionEntity
import com.moneytracker.app.ui.Routes
import com.moneytracker.app.ui.components.EmptyState
import com.moneytracker.app.ui.components.FinCard
import com.moneytracker.app.ui.components.FinTopBar
import com.moneytracker.app.ui.components.KeyValue
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.components.Option
import com.moneytracker.app.ui.components.OptionSheet
import com.moneytracker.app.ui.components.WalletBadge
import com.moneytracker.app.ui.components.money
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.app.ui.today
import com.moneytracker.core.CategoryKind
import com.moneytracker.core.Fmt
import com.moneytracker.core.Ledger
import com.moneytracker.core.TxType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class PeriodFilter(val label: String) { THIS_MONTH("Bulan ini"), LAST_MONTH("Bulan lalu"), LAST_30("30 hari"), THIS_YEAR("Tahun ini"), ALL("Semua") }
enum class TypeFilter(val label: String) { ALL("Semua jenis"), EXPENSE("Pengeluaran"), INCOME("Pemasukan"), MOVEMENT("Transfer & tabungan"), DEBT("Utang/piutang") }
enum class SortMode(val label: String) { NEWEST("Terbaru"), OLDEST("Terlama"), LARGEST("Nominal terbesar"), SMALLEST("Nominal terkecil") }

fun periodRange(p: PeriodFilter, today: LocalDate = today()): Pair<Long, Long>? = when (p) {
    PeriodFilter.THIS_MONTH -> Ledger.monthRange(YearMonth.from(today))
    PeriodFilter.LAST_MONTH -> Ledger.monthRange(YearMonth.from(today).minusMonths(1))
    PeriodFilter.LAST_30 -> Fmt.toMillis(today.minusDays(29)) to Fmt.toMillis(today.plusDays(1))
    PeriodFilter.THIS_YEAR -> Fmt.toMillis(today.withDayOfYear(1)) to Fmt.toMillis(today.withDayOfYear(1).plusYears(1))
    PeriodFilter.ALL -> null
}

/** Pencarian: deskripsi, catatan, tag, kategori, wallet, nominal, tanggal. */
fun matchesQuery(s: Snapshot, t: TransactionEntity, q: String): Boolean {
    if (q.isBlank()) return true
    val query = q.trim().lowercase()
    val digits = query.filter { it.isDigit() }
    val isAmountQuery = digits.isNotEmpty() && query.all { it.isDigit() || it == '.' || it == ',' || it == ' ' || it == 'r' || it == 'p' }
    if (isAmountQuery && (t.amount.toString().contains(digits) || t.fee.toString() == digits)) return true
    val hay = buildString {
        append(t.description.lowercase()); append(' '); append(t.note.lowercase()); append(' '); append(t.tags.lowercase()); append(' ')
        append(t.location.lowercase()); append(' '); append(s.categoryLabel(t.categoryId).lowercase()); append(' ')
        append(s.walletMap[t.walletId]?.name?.lowercase().orEmpty()); append(' ')
        append(t.toWalletId?.let { s.walletMap[it]?.name?.lowercase() }.orEmpty()); append(' ')
        append(t.type.label.lowercase()); append(' ')
        append(Fmt.date(t.dateTime).lowercase()); append(' '); append(Fmt.toDate(t.dateTime).toString())
        val d = Fmt.toDate(t.dateTime); append(' '); append("%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year))
    }
    return query.split(' ').filter { it.isNotBlank() }.all { hay.contains(it) }
}

@Composable
fun TransactionsScreen(s: Snapshot, nav: NavHostController) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf(PeriodFilter.THIS_MONTH) }
    var typeFilter by rememberSaveable { mutableStateOf(TypeFilter.ALL) }
    var walletFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var vehicleFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var sort by rememberSaveable { mutableStateOf(SortMode.NEWEST) }
    var sheet by remember { mutableStateOf("") }
    var exportMenu by remember { mutableStateOf(false) }

    val filtered = remember(s, query, period, typeFilter, walletFilter, categoryFilter, vehicleFilter, sort) {
        val range = periodRange(period)
        val catIds = categoryFilter?.let { Ledger.descendantsOf(it, s.catInfo.values) }
        s.transactions.asSequence()
            .filter { range == null || it.dateTime in range.first until range.second }
            .filter {
                when (typeFilter) {
                    TypeFilter.ALL -> true
                    TypeFilter.EXPENSE -> it.type == TxType.EXPENSE
                    TypeFilter.INCOME -> it.type == TxType.INCOME
                    TypeFilter.MOVEMENT -> it.type in setOf(TxType.TRANSFER, TxType.SAVINGS_ALLOCATION, TxType.CC_SETTLEMENT)
                    TypeFilter.DEBT -> it.type in setOf(TxType.DEBT_PAYMENT, TxType.DEBT_COLLECTION, TxType.LOAN_GIVEN, TxType.LOAN_RECEIVED)
                }
            }
            .filter { walletFilter == null || it.walletId == walletFilter || it.toWalletId == walletFilter }
            .filter { catIds == null || it.categoryId in catIds }
            .filter { vehicleFilter == null || it.vehicleId == vehicleFilter }
            .filter { matchesQuery(s, it, query) }
            .toList()
            .let { l ->
                when (sort) {
                    SortMode.NEWEST -> l
                    SortMode.OLDEST -> l.sortedBy { it.dateTime }
                    SortMode.LARGEST -> l.sortedByDescending { it.amount }
                    SortMode.SMALLEST -> l.sortedBy { it.amount }
                }
            }
    }
    val totals = remember(filtered) {
        val core = filtered.map { s.core(it) }
        core.sumOf { Ledger.incomeOf(it) } to core.sumOf { Ledger.expenseOf(it) }
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { app.exporter.exportCsv(uri, s, filtered); Toast.makeText(ctx, "CSV tersimpan (${filtered.size} transaksi)", Toast.LENGTH_SHORT).show() }
    }
    val xlsxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) scope.launch { app.exporter.exportXlsx(uri, s, filtered); Toast.makeText(ctx, "Excel tersimpan (${filtered.size} transaksi)", Toast.LENGTH_SHORT).show() }
    }

    Column(Modifier.fillMaxSize()) {
        FinTopBar("Transaksi") {
            IconButton(onClick = { nav.navigate(Routes.IMPORT) }) { Icon(Icons.Outlined.FileUpload, "Impor") }
            IconButton(onClick = { exportMenu = true }) { Icon(Icons.Outlined.FileDownload, "Ekspor") }
            DropdownMenu(exportMenu, { exportMenu = false }) {
                val stamp = Fmt.toDate(System.currentTimeMillis()).toString()
                DropdownMenuItem(text = { Text("Ekspor CSV") }, onClick = { exportMenu = false; csvLauncher.launch("moneytracker-transaksi-$stamp.csv") })
                DropdownMenuItem(text = { Text("Ekspor Excel (.xlsx)") }, onClick = { exportMenu = false; xlsxLauncher.launch("moneytracker-transaksi-$stamp.xlsx") })
                DropdownMenuItem(text = { Text("Laporan PDF…") }, onClick = { exportMenu = false; nav.navigate(Routes.REPORTS) })
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Cari nominal, deskripsi, tanggal, kategori, wallet…") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Hapus") } },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(true, { sheet = "period" }, { Text(period.label) }) }
            item { FilterChip(typeFilter != TypeFilter.ALL, { sheet = "type" }, { Text(typeFilter.label) }) }
            item { FilterChip(walletFilter != null, { sheet = "wallet" }, { Text(walletFilter?.let { s.walletMap[it]?.name } ?: "Semua wallet") }) }
            item { FilterChip(categoryFilter != null, { sheet = "category" }, { Text(categoryFilter?.let { s.categoryMap[it]?.name } ?: "Semua kategori") }) }
            if (s.vehicles.isNotEmpty()) item { FilterChip(vehicleFilter != null, { sheet = "vehicle" }, { Text(vehicleFilter?.let { s.vehicleMap[it]?.name } ?: "Semua kendaraan") }) }
            item { FilterChip(sort != SortMode.NEWEST, { sheet = "sort" }, { Text(sort.label) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Sort, null) }) }
        }
        FinCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${filtered.size} transaksi", style = MaterialTheme.typography.labelMedium, color = Fin.colors.muted, modifier = Modifier.weight(1f))
                Text("+" + money(totals.first), style = MaterialTheme.typography.labelLarge, color = Fin.colors.income)
                Spacer(Modifier.width(12.dp))
                Text("-" + money(totals.second), style = MaterialTheme.typography.labelLarge, color = Fin.colors.expense)
            }
        }

        if (filtered.isEmpty()) {
            EmptyState("🧾", "Tidak ada transaksi", if (s.transactions.isEmpty()) "Catat transaksi pertama Anda dengan tombol +" else "Coba ubah filter atau kata kunci.")
        } else {
            val grouped = sort == SortMode.NEWEST || sort == SortMode.OLDEST
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
                if (grouped) {
                    filtered.groupBy { Fmt.toDate(it.dateTime) }.forEach { (date, list) ->
                        item(key = "h$date") {
                            val net = list.sumOf { Ledger.incomeOf(s.core(it)) - Ledger.expenseOf(s.core(it)) }
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp)) {
                                Text(Fmt.dateWithDay(date), style = MaterialTheme.typography.labelLarge, color = Fin.colors.muted, modifier = Modifier.weight(1f))
                                Text(money(net, withSign = true), style = MaterialTheme.typography.labelMedium, color = Fin.colors.muted)
                            }
                        }
                        items(list, key = { it.id }) { t -> TransactionRow(s, t, onClick = { nav.navigate(Routes.txDetail(t.id)) }) }
                    }
                } else {
                    items(filtered, key = { it.id }) { t -> TransactionRow(s, t, onClick = { nav.navigate(Routes.txDetail(t.id)) }, showDate = true) }
                }
            }
        }
    }

    when (sheet) {
        "period" -> OptionSheet("Periode", PeriodFilter.entries.map { Option(it, it.label) }, period, { sheet = "" }) { period = it }
        "type" -> OptionSheet("Jenis transaksi", TypeFilter.entries.map { Option(it, it.label) }, typeFilter, { sheet = "" }) { typeFilter = it }
        "sort" -> OptionSheet("Urutkan", SortMode.entries.map { Option(it, it.label) }, sort, { sheet = "" }) { sort = it }
        "wallet" -> OptionSheet(
            "Wallet",
            listOf(Option<Long?>(null, "Semua wallet")) + s.wallets.map { w -> Option<Long?>(w.id, w.name, money(s.balance(w.id)), leading = { WalletBadge(w, 32.dp) }) },
            walletFilter, { sheet = "" },
        ) { walletFilter = it }
        "vehicle" -> OptionSheet("Kendaraan", listOf(Option<Long?>(null, "Semua kendaraan")) + s.vehicles.map { Option<Long?>(it.id, it.name) }, vehicleFilter, { sheet = "" }) { vehicleFilter = it }
        "category" -> OptionSheet("Kategori", listOf(Option<Long?>(null, "Semua kategori")) + categoryOptions(s, null), categoryFilter, { sheet = "" }) { categoryFilter = it }
    }
}

/** Opsi kategori bertingkat (induk + anak menjorok). [kind] null = semua. */
fun categoryOptions(s: Snapshot, kind: CategoryKind?, hidePreferred: Boolean = false): List<Option<Long?>> {
    val kinds = kind?.let { listOf(it) } ?: listOf(CategoryKind.EXPENSE, CategoryKind.INCOME)
    return kinds.flatMap { k ->
        s.roots(k).flatMap { root ->
            listOf(Option<Long?>(root.id, "${root.icon}  ${root.name}", if (kind == null) (if (k == CategoryKind.INCOME) "Pemasukan" else "Pengeluaran") else null)) +
                s.children(root.id).filter { !hidePreferred || it.preferredType == null }.map { Option<Long?>(it.id, "${it.icon}  ${it.name}", indent = true) }
        }
    }
}
