package com.moneytracker.app.ui.more

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.moneytracker.app.data.DebtEntity
import com.moneytracker.app.data.RecurringEntity
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.ui.Routes
import com.moneytracker.app.ui.components.BarChart
import com.moneytracker.app.ui.components.BarSeries
import com.moneytracker.app.ui.components.BasisNote
import com.moneytracker.app.ui.components.ConfirmDialog
import com.moneytracker.app.ui.components.DateField
import com.moneytracker.app.ui.components.EmojiIcon
import com.moneytracker.app.ui.components.EmptyState
import com.moneytracker.app.ui.components.FinCard
import com.moneytracker.app.ui.components.FinProgress
import com.moneytracker.app.ui.components.FinTextField
import com.moneytracker.app.ui.components.FinTopBar
import com.moneytracker.app.ui.components.KeyValue
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.components.MoneyField
import com.moneytracker.app.ui.components.Option
import com.moneytracker.app.ui.components.Pill
import com.moneytracker.app.ui.components.SectionHeader
import com.moneytracker.app.ui.components.SegmentedTabs
import com.moneytracker.app.ui.components.SelectField
import com.moneytracker.app.ui.components.WalletBadge
import com.moneytracker.app.ui.components.money
import com.moneytracker.app.ui.components.toAmount
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.app.ui.thisMonth
import com.moneytracker.app.ui.today
import com.moneytracker.app.ui.transactions.TransactionRow
import com.moneytracker.app.ui.transactions.categoryOptions
import com.moneytracker.core.CategoryKind
import com.moneytracker.core.DebtDirection
import com.moneytracker.core.DebtKind
import com.moneytracker.core.Fmt
import com.moneytracker.core.Ledger
import com.moneytracker.core.Period
import com.moneytracker.core.RecurringKind
import com.moneytracker.core.Schedule
import com.moneytracker.core.TxType
import com.moneytracker.core.WalletType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ===================== Pemasukan =====================
@Composable
fun IncomeScreen(s: Snapshot, nav: NavHostController) {
    val data = remember(s) {
        val months = (11 downTo 0).map { thisMonth().minusMonths(it.toLong()) }
        val perMonth = months.map { it to Ledger.summarize(s.txCore, it).income }
        val incomeTx = s.transactions.filter { it.type == TxType.INCOME }
        val bySource = incomeTx.groupBy { it.categoryId }.map { (c, l) -> c to l.sumOf { it.amount } }.sortedByDescending { it.second }
        Triple(perMonth, bySource, incomeTx)
    }
    val (perMonth, bySource, incomeTx) = data
    val schedules = s.recurring.filter { it.kind.isIncome }
    Column(Modifier.fillMaxSize()) {
        FinTopBar("Pemasukan", onBack = { nav.popBackStack() }) { IconButton(onClick = { nav.navigate(Routes.txForm(type = "INCOME")) }) { Icon(Icons.Filled.Add, "Tambah pemasukan") } }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                FinCard {
                    val cur = perMonth.last().second; val prev = perMonth[perMonth.size - 2].second
                    Text("Pemasukan ${Fmt.month(thisMonth())}", style = MaterialTheme.typography.labelMedium, color = Fin.colors.muted)
                    Text(money(cur), style = MaterialTheme.typography.headlineMedium, color = Fin.colors.income)
                    if (prev > 0) Text("Bulan lalu ${money(prev)} (" + Fmt.percent((cur - prev).toDouble() / prev).let { if (cur >= prev) "+$it" else it } + ")", style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted)
                    Spacer(Modifier.height(12.dp))
                    BarChart(perMonth.map { Fmt.monthShort(it.first) }, listOf(BarSeries("Pemasukan", Fin.colors.income, perMonth.map { it.second })))
                    val active = perMonth.filter { it.second > 0 }
                    if (active.isNotEmpty()) KeyValue("Rata-rata per bulan (bulan berpemasukan)", money(active.sumOf { it.second } / active.size))
                }
            }
            item { SectionHeader("Jadwal gaji & pemasukan rutin", action = "Tambah", onAction = { nav.navigate(Routes.recurringForm(kind = "SALARY")) }) }
            if (schedules.isEmpty()) item { Text("Belum ada jadwal. Tambahkan gaji bulanan agar prediksi saldo akhir bulan lebih akurat.", color = Fin.colors.muted, style = MaterialTheme.typography.bodySmall) }
            items(schedules, key = { it.id }) { r -> RecurringCard(s, r, nav) }
            item { SectionHeader("Sumber pemasukan (sepanjang waktu)") }
            item {
                FinCard {
                    if (bySource.isEmpty()) Text("Belum ada pemasukan", color = Fin.colors.muted)
                    val total = bySource.sumOf { it.second }.coerceAtLeast(1)
                    bySource.forEach { (c, amt) ->
                        val cat = c?.let { s.categoryMap[it] }
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row { Text("${cat?.icon ?: "💰"} ${cat?.name ?: "Tanpa kategori"}", Modifier.weight(1f)); Text(money(amt), style = MaterialTheme.typography.labelLarge) }
                            Spacer(Modifier.height(4.dp))
                            FinProgress(amt.toFloat() / total, Fin.colors.income, height = 6.dp)
                        }
                    }
                }
            }
            item { SectionHeader("Riwayat pemasukan") }
            item {
                FinCard(padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    if (incomeTx.isEmpty()) Text("Belum ada", color = Fin.colors.muted, modifier = Modifier.padding(8.dp))
                    incomeTx.take(30).forEach { t -> TransactionRow(s, t, onClick = { nav.navigate(Routes.txDetail(t.id)) }, showDate = true) }
                }
            }
        }
    }
}

// ===================== Langganan & berulang =====================
@Composable
fun SubscriptionsScreen(s: Snapshot, nav: NavHostController) {
    var tab by remember { mutableIntStateOf(0) }
    val billKinds = setOf(RecurringKind.SUBSCRIPTION, RecurringKind.BILL)
    val list = s.recurring.filter { if (tab == 0) it.kind in billKinds else it.kind !in billKinds }
    val expenseActive = s.recurring.filter { it.active && !it.kind.isIncome }
    val monthly = expenseActive.sumOf { Schedule.monthlyCost(it.amount, it.period, it.interval) }.toLong()
    val subsMonthly = expenseActive.filter { it.kind == RecurringKind.SUBSCRIPTION }.sumOf { Schedule.monthlyCost(it.amount, it.period, it.interval) }.toLong()
    Column(Modifier.fillMaxSize()) {
        FinTopBar("Langganan & Rutin", onBack = { nav.popBackStack() }) {
            IconButton(onClick = { nav.navigate(Routes.recurringForm(kind = if (tab == 0) "SUBSCRIPTION" else "RECURRING_EXPENSE")) }) { Icon(Icons.Filled.Add, "Tambah") }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                FinCard {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Langganan / bulan", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                            Text(money(subsMonthly), style = MaterialTheme.typography.titleLarge)
                            Text("${money(subsMonthly * 12)} / tahun", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Semua biaya rutin / bulan", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                            Text(money(monthly), style = MaterialTheme.typography.titleLarge)
                            Text("${money(monthly * 12)} / tahun", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                        }
                    }
                    BasisNote("Dinormalisasi per bulan: mingguan × 52/12, 3 bulanan ÷ 3, tahunan ÷ 12.")
                }
            }
            item { SegmentedTabs(listOf("Langganan & tagihan", "Transaksi berulang"), tab, { tab = it }) }
            if (list.isEmpty()) item {
                EmptyState("📆", "Belum ada data", if (tab == 0) "Contoh: Netflix, Spotify, YouTube Premium, internet, hosting, VPS, domain, cicilan." else "Contoh: transfer bulanan ke orang tua, iuran, gaji.")
            }
            items(list, key = { it.id }) { r -> RecurringCard(s, r, nav) }
        }
    }
}

@Composable
fun RecurringCard(s: Snapshot, r: RecurringEntity, nav: NavHostController) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val due = LocalDate.ofEpochDay(r.nextDate)
    val days = ChronoUnit.DAYS.between(today(), due)
    FinCard(onClick = { nav.navigate(Routes.recurringForm(r.id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiIcon(r.categoryId?.let { s.categoryMap[it]?.icon } ?: if (r.kind.isIncome) "💰" else "📆", if (r.kind.isIncome) Fin.colors.income else MaterialTheme.colorScheme.primary, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleSmall)
                Text("${r.kind.label} · ${if (r.interval > 1) "tiap ${r.interval} " else ""}${r.period.label.lowercase()} · ${s.walletMap[r.walletId]?.name ?: "?"}", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(r.amount), style = MaterialTheme.typography.titleSmall, color = if (r.kind.isIncome) Fin.colors.income else MaterialTheme.colorScheme.onSurface)
                if (!r.active) Pill("Nonaktif", Fin.colors.muted)
                else Text(
                    when { days < 0 -> "Terlambat ${-days} hari"; days == 0L -> "Hari ini"; days == 1L -> "Besok"; else -> Fmt.dateShort(due) },
                    style = MaterialTheme.typography.labelSmall, color = if (days <= 1) Fin.colors.warning else Fin.colors.muted,
                )
            }
        }
        if (r.active && days <= r.reminderDays) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scope.launch { app.repository.postRecurring(r) } }, modifier = Modifier.weight(1f)) { Text(if (r.kind.isIncome) "Catat diterima" else "Catat dibayar") }
            OutlinedButton(onClick = { scope.launch { app.repository.skipRecurring(r) } }, modifier = Modifier.weight(1f)) { Text("Lewati") }
        }
        if (r.autoPost) BasisNote("Dicatat otomatis saat jatuh tempo")
    }
}

@Composable
fun RecurringFormScreen(s: Snapshot, nav: NavHostController, id: Long, kindArg: String) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val e = s.recurring.firstOrNull { it.id == id }
    var kind by rememberSaveable { mutableStateOf(e?.kind ?: runCatching { RecurringKind.valueOf(kindArg) }.getOrDefault(RecurringKind.SUBSCRIPTION)) }
    var name by rememberSaveable { mutableStateOf(e?.name ?: "") }
    var amount by rememberSaveable { mutableStateOf(e?.amount?.toString() ?: "") }
    var walletId by rememberSaveable { mutableStateOf(e?.walletId ?: s.activeWallets.firstOrNull()?.id) }
    var categoryId by rememberSaveable { mutableStateOf(e?.categoryId) }
    var period by rememberSaveable { mutableStateOf(e?.period ?: Period.MONTHLY) }
    var interval by rememberSaveable { mutableStateOf(e?.interval?.toString() ?: "1") }
    var next by rememberSaveable { mutableStateOf(e?.nextDate?.let(LocalDate::ofEpochDay) ?: today()) }
    var autoPost by rememberSaveable { mutableStateOf(e?.autoPost ?: false) }
    var reminder by rememberSaveable { mutableStateOf(e?.reminderDays?.toString() ?: "2") }
    var active by rememberSaveable { mutableStateOf(e?.active ?: true) }
    var note by rememberSaveable { mutableStateOf(e?.note ?: "") }
    var confirm by remember { mutableStateOf(false) }
    val presets = listOf("Netflix", "Spotify", "YouTube Premium", "Internet/WiFi", "Hosting", "VPS", "Domain", "Listrik", "BPJS", "Cicilan")

    Column(Modifier.fillMaxSize()) {
        FinTopBar(if (e == null) "Jadwal baru" else "Edit jadwal", onBack = { nav.popBackStack() }) {
            if (e != null) IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.Delete, "Hapus") }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectField("Jenis", RecurringKind.entries.map { Option(it, it.label) }, kind) { kind = it; categoryId = null }
            if (e == null && kind == RecurringKind.SUBSCRIPTION) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presets) { p -> androidx.compose.material3.AssistChip(onClick = { name = p }, label = { Text(p) }) }
                }
            }
            FinTextField(name, { name = it }, "Nama")
            MoneyField(amount, { amount = it }, "Nominal")
            SelectField(if (kind.isIncome) "Masuk ke wallet" else "Dibayar dari", s.activeWallets.map { w -> Option<Long?>(w.id, w.name, leading = { WalletBadge(w, 28.dp) }) }, walletId) { walletId = it }
            SelectField("Kategori", listOf(Option<Long?>(null, "Tanpa kategori")) + categoryOptions(s, if (kind.isIncome) CategoryKind.INCOME else CategoryKind.EXPENSE, hidePreferred = true), categoryId) { categoryId = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectField("Periode", Period.entries.map { Option(it, it.label) }, period, Modifier.weight(1.4f)) { period = it }
                FinTextField(interval, { interval = it.filter(Char::isDigit).take(2) }, "Setiap", Modifier.weight(1f), keyboardType = KeyboardType.Number)
            }
            DateField("Jatuh tempo berikutnya", next, { if (it != null) next = it })
            FinTextField(reminder, { reminder = it.filter(Char::isDigit).take(2) }, "Ingatkan H-", keyboardType = KeyboardType.Number, suffix = "hari")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Catat otomatis", style = MaterialTheme.typography.bodyLarge)
                    Text("Transaksi dibuat otomatis saat jatuh tempo. Matikan bila nominal sering berubah.", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                }
                Switch(autoPost, { autoPost = it })
            }
            if (e != null) Row(verticalAlignment = Alignment.CenterVertically) { Text("Aktif", Modifier.weight(1f)); Switch(active, { active = it }) }
            FinTextField(note, { note = it }, "Catatan")
            Button(onClick = {
                val w = walletId ?: return@Button
                scope.launch {
                    app.repository.saveRecurring(
                        RecurringEntity(
                            id = e?.id ?: 0, kind = kind, name = name.trim(), amount = amount.toAmount(), walletId = w, categoryId = categoryId,
                            period = period, interval = interval.toIntOrNull()?.coerceAtLeast(1) ?: 1, nextDate = next.toEpochDay(),
                            anchorDay = if (period == Period.MONTHLY || period == Period.QUARTERLY || period == Period.YEARLY) next.dayOfMonth else null,
                            autoPost = autoPost, reminderDays = reminder.toIntOrNull() ?: 2, active = active, note = note, createdAt = e?.createdAt ?: System.currentTimeMillis(),
                        ),
                    )
                    if (autoPost) app.repository.processDueRecurring()
                    nav.popBackStack()
                }
            }, enabled = name.isNotBlank() && amount.toAmount() > 0 && walletId != null, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Simpan") }
        }
    }
    if (confirm && e != null) ConfirmDialog("Hapus ${e.name}?", "Transaksi yang sudah tercatat tetap ada.", onDismiss = { confirm = false }) {
        scope.launch { app.repository.deleteRecurring(e); nav.popBackStack() }
    }
}

// ===================== Utang & piutang =====================
@Composable
fun DebtsScreen(s: Snapshot, nav: NavHostController) {
    var tab by remember { mutableIntStateOf(0) }
    val dir = if (tab == 0) DebtDirection.PAYABLE else DebtDirection.RECEIVABLE
    val list = s.debts.filter { it.direction == dir && !it.archived }
    val ccWallets = s.wallets.filter { it.type == WalletType.CREDIT_CARD }
    Column(Modifier.fillMaxSize()) {
        FinTopBar("Utang & Piutang", onBack = { nav.popBackStack() }) { IconButton(onClick = { nav.navigate(Routes.debtForm()) }) { Icon(Icons.Filled.Add, "Tambah") } }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                FinCard {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Total utang", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                            Text(money(s.payables), style = MaterialTheme.typography.titleLarge, color = Fin.colors.expense)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Total piutang", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                            Text(money(s.receivables), style = MaterialTheme.typography.titleLarge, color = Fin.colors.income)
                        }
                    }
                    val cc = ccWallets.sumOf { (-s.balance(it.id)).coerceAtLeast(0) }
                    if (ccWallets.isNotEmpty()) KeyValue("Tagihan kartu kredit", money(cc))
                    BasisNote("Kartu kredit dikelola sebagai wallet. Pelunasan dicatat sebagai \"Pelunasan Kartu Kredit\" sehingga tidak dihitung ulang sebagai pengeluaran.")
                }
            }
            item { SegmentedTabs(listOf("Utang saya", "Piutang"), tab, { tab = it }) }
            if (list.isEmpty()) item { EmptyState(if (tab == 0) "🤝" else "📥", "Tidak ada data", if (tab == 0) "Catat utang pribadi, cicilan, atau pinjaman." else "Catat uang yang Anda pinjamkan ke orang lain.") }
            items(list, key = { it.id }) { d ->
                val st = s.debtStatus(d)
                FinCard(onClick = { nav.navigate(Routes.debt(d.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.titleSmall)
                            Text("${d.kind.label}" + (d.counterparty.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                        }
                        if (st.settled) Pill("Lunas", Fin.colors.income) else Text(money(st.remaining), style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    FinProgress(st.ratio.toFloat(), if (d.direction == DebtDirection.PAYABLE) MaterialTheme.colorScheme.primary else Fin.colors.income)
                    Row(Modifier.padding(top = 6.dp)) {
                        Text("${Fmt.percent(st.ratio)} dari ${money(d.principal)}", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted, modifier = Modifier.weight(1f))
                        d.dueDate?.let { if (!st.settled) Text("Jatuh tempo ${Fmt.dateShort(LocalDate.ofEpochDay(it))}", style = MaterialTheme.typography.labelSmall, color = if (LocalDate.ofEpochDay(it) <= today().plusDays(3)) Fin.colors.warning else Fin.colors.muted) }
                    }
                }
            }
        }
    }
}

@Composable
fun DebtDetailScreen(s: Snapshot, nav: NavHostController, id: Long) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val d = s.debtMap[id]
    var pay by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        FinTopBar(d?.name ?: "Utang", onBack = { nav.popBackStack() }) {
            if (d != null) {
                IconButton(onClick = { nav.navigate(Routes.debtForm(d.id)) }) { Icon(Icons.Outlined.Edit, "Edit") }
                IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.Delete, "Hapus") }
            }
        }
        if (d == null) { EmptyState("🤝", "Tidak ditemukan", ""); return }
        val st = s.debtStatus(d)
        val txs = s.transactions.filter { it.debtId == d.id }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                FinCard {
                    Text(if (d.direction == DebtDirection.PAYABLE) "Sisa utang" else "Sisa piutang", style = MaterialTheme.typography.labelMedium, color = Fin.colors.muted)
                    Text(money(st.remaining), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(10.dp))
                    FinProgress(st.ratio.toFloat(), MaterialTheme.colorScheme.primary, height = 10.dp)
                    Spacer(Modifier.height(8.dp))
                    KeyValue("Pokok", money(d.principal))
                    KeyValue(if (d.direction == DebtDirection.PAYABLE) "Sudah dibayar" else "Sudah diterima", money(st.paid))
                    if (st.interestPaid > 0) KeyValue("Bunga/denda dibayar", money(st.interestPaid))
                    KeyValue("Progres pelunasan", Fmt.percent(st.ratio))
                    KeyValue("Mulai", Fmt.date(LocalDate.ofEpochDay(d.startDate)))
                    d.dueDate?.let { KeyValue("Jatuh tempo", Fmt.date(LocalDate.ofEpochDay(it))) }
                    d.installmentAmount?.let { KeyValue("Cicilan", money(it)) }
                    d.installmentCount?.let { total ->
                        val paidCount = d.installmentAmount?.takeIf { it > 0 }?.let { (st.paid / it).toInt() } ?: 0
                        KeyValue("Sisa cicilan", "${(total - paidCount).coerceAtLeast(0)} dari $total kali")
                    }
                    if (d.note.isNotBlank()) KeyValue("Catatan", d.note)
                }
            }
            if (!st.settled) item { Button(onClick = { pay = true }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(if (d.direction == DebtDirection.PAYABLE) "Catat pembayaran" else "Catat penerimaan") } }
            item { SectionHeader("Riwayat pembayaran") }
            item {
                FinCard(padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    if (txs.isEmpty()) Text("Belum ada", color = Fin.colors.muted, modifier = Modifier.padding(8.dp))
                    txs.forEach { t -> TransactionRow(s, t, onClick = { nav.navigate(Routes.txDetail(t.id)) }, showDate = true) }
                }
            }
        }
        if (pay) {
            var principal by remember { mutableStateOf((d.installmentAmount ?: st.remaining).coerceAtMost(st.remaining).toString()) }
            var interest by remember { mutableStateOf("") }
            var walletId by remember { mutableStateOf(s.activeWallets.firstOrNull { !it.type.isLiability }?.id) }
            var date by remember { mutableStateOf(today()) }
            AlertDialog(
                onDismissRequest = { pay = false },
                title = { Text(if (d.direction == DebtDirection.PAYABLE) "Bayar ${d.name}" else "Terima dari ${d.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MoneyField(principal, { principal = it }, "Pokok", supporting = "Sisa ${Fmt.rupiah(st.remaining)}")
                        if (d.direction == DebtDirection.PAYABLE) MoneyField(interest, { interest = it }, "Bunga / denda (opsional)", supporting = "Dihitung sebagai pengeluaran")
                        SelectField(if (d.direction == DebtDirection.PAYABLE) "Dari wallet" else "Ke wallet", s.activeWallets.map { w -> Option<Long?>(w.id, w.name, money(s.balance(w.id))) }, walletId) { walletId = it }
                        DateField("Tanggal", date, { if (it != null) date = it })
                    }
                },
                confirmButton = {
                    TextButton(enabled = principal.toAmount() > 0 && walletId != null, onClick = {
                        scope.launch { app.repository.payDebt(d, walletId!!, principal.toAmount(), interest.toAmount(), Fmt.toMillis(date.atTime(java.time.LocalTime.NOON)), "") }
                        pay = false
                    }) { Text("Simpan") }
                },
                dismissButton = { TextButton(onClick = { pay = false }) { Text("Batal") } },
            )
        }
    }
    if (confirm && d != null) ConfirmDialog("Hapus ${d.name}?", "Transaksi pembayaran tetap tersimpan, hanya tidak terhubung lagi.", onDismiss = { confirm = false }) {
        scope.launch { app.repository.deleteDebt(d); nav.popBackStack() }
    }
}

@Composable
fun DebtFormScreen(s: Snapshot, nav: NavHostController, id: Long) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val e = s.debtMap[id]
    var direction by rememberSaveable { mutableStateOf(e?.direction ?: DebtDirection.PAYABLE) }
    var kind by rememberSaveable { mutableStateOf(e?.kind ?: DebtKind.PERSONAL) }
    var name by rememberSaveable { mutableStateOf(e?.name ?: "") }
    var counterparty by rememberSaveable { mutableStateOf(e?.counterparty ?: "") }
    var principal by rememberSaveable { mutableStateOf(e?.principal?.toString() ?: "") }
    var start by rememberSaveable { mutableStateOf(e?.startDate?.let(LocalDate::ofEpochDay) ?: today()) }
    var due by rememberSaveable { mutableStateOf(e?.dueDate?.let(LocalDate::ofEpochDay)) }
    var installment by rememberSaveable { mutableStateOf(e?.installmentAmount?.toString() ?: "") }
    var count by rememberSaveable { mutableStateOf(e?.installmentCount?.toString() ?: "") }
    var note by rememberSaveable { mutableStateOf(e?.note ?: "") }
    var archived by rememberSaveable { mutableStateOf(e?.archived ?: false) }
    var disburse by rememberSaveable { mutableStateOf(false) }
    var walletId by rememberSaveable { mutableStateOf(s.activeWallets.firstOrNull { !it.type.isLiability }?.id) }

    Column(Modifier.fillMaxSize()) {
        FinTopBar(if (e == null) "Utang/piutang baru" else "Edit", onBack = { nav.popBackStack() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (e == null) SegmentedTabs(listOf("Utang saya", "Piutang (dipinjamkan)"), direction.ordinal, { direction = DebtDirection.entries[it] })
            SegmentedTabs(DebtKind.entries.map { it.label }, kind.ordinal, { kind = DebtKind.entries[it] })
            FinTextField(name, { name = it }, "Nama", supporting = "mis. Cicilan motor, Pinjaman Budi")
            FinTextField(counterparty, { counterparty = it }, if (direction == DebtDirection.PAYABLE) "Pemberi pinjaman" else "Peminjam")
            MoneyField(principal, { principal = it }, "Pokok / total", large = true)
            DateField("Tanggal mulai", start, { if (it != null) start = it })
            DateField(if (kind == DebtKind.INSTALLMENT) "Jatuh tempo cicilan berikutnya" else "Jatuh tempo", due, { due = it }, allowClear = true)
            if (kind == DebtKind.INSTALLMENT) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyField(installment, { installment = it }, "Cicilan / bulan", Modifier.weight(1.4f))
                FinTextField(count, { count = it.filter(Char::isDigit).take(3) }, "Tenor", Modifier.weight(1f), keyboardType = KeyboardType.Number, suffix = "x")
            }
            FinTextField(note, { note = it }, "Catatan / bunga", singleLine = false)
            if (e == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (direction == DebtDirection.PAYABLE) "Uang pinjaman masuk ke wallet" else "Uang dipinjamkan dari wallet", style = MaterialTheme.typography.bodyLarge)
                        Text("Dicatat sebagai mutasi pinjaman — bukan pemasukan/pengeluaran", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                    }
                    Switch(disburse, { disburse = it })
                }
                if (disburse) SelectField("Wallet", s.activeWallets.map { w -> Option<Long?>(w.id, w.name) }, walletId) { walletId = it }
            } else Row(verticalAlignment = Alignment.CenterVertically) { Text("Arsipkan", Modifier.weight(1f)); Switch(archived, { archived = it }) }
            Button(onClick = {
                scope.launch {
                    app.repository.saveDebt(
                        DebtEntity(
                            id = e?.id ?: 0, name = name.trim(), counterparty = counterparty.trim(), direction = direction, kind = kind, principal = principal.toAmount(),
                            startDate = start.toEpochDay(), dueDate = due?.toEpochDay(), installmentAmount = installment.toAmount().takeIf { it > 0 && kind == DebtKind.INSTALLMENT },
                            installmentCount = count.toIntOrNull()?.takeIf { kind == DebtKind.INSTALLMENT }, note = note, archived = archived, createdAt = e?.createdAt ?: System.currentTimeMillis(),
                        ),
                        if (disburse) walletId else null,
                    )
                    nav.popBackStack()
                }
            }, enabled = name.isNotBlank() && principal.toAmount() > 0, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Simpan") }
        }
    }
}
