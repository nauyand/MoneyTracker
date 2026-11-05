package com.moneytracker.app.ui.wallets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.moneytracker.app.data.Providers
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.data.WalletEntity
import com.moneytracker.app.ui.Routes
import com.moneytracker.app.ui.components.BasisNote
import com.moneytracker.app.ui.components.ConfirmDialog
import com.moneytracker.app.ui.components.EmptyState
import com.moneytracker.app.ui.components.FinCard
import com.moneytracker.app.ui.components.FinProgress
import com.moneytracker.app.ui.components.FinTextField
import com.moneytracker.app.ui.components.FinTopBar
import com.moneytracker.app.ui.components.KeyValue
import com.moneytracker.app.ui.components.LineChart
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.components.MoneyField
import com.moneytracker.app.ui.components.Pill
import com.moneytracker.app.ui.components.Option
import com.moneytracker.app.ui.components.SectionHeader
import com.moneytracker.app.ui.components.SelectField
import com.moneytracker.app.ui.components.WalletBadge
import com.moneytracker.app.ui.components.money
import com.moneytracker.app.ui.components.toAmount
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.app.ui.today
import com.moneytracker.app.ui.transactions.TransactionRow
import com.moneytracker.core.Fmt
import com.moneytracker.core.Ledger
import com.moneytracker.core.TxType
import com.moneytracker.core.WalletType
import kotlinx.coroutines.launch

@Composable
fun WalletsScreen(s: Snapshot, nav: NavHostController) {
    var showArchived by remember { mutableStateOf(false) }
    val primaryId = s.primaryWallet?.id
    val groups = remember(s, showArchived) {
        val active = s.wallets.filter { !it.archived }
        listOf(
            "Utama" to active.filter { it.isMain },
            "Opsional" to active.filter { !it.isMain },
            "Diarsipkan" to if (showArchived) s.wallets.filter { it.archived } else emptyList(),
        ).filter { it.second.isNotEmpty() }
    }

    Column(Modifier.fillMaxSize()) {
        FinTopBar("Wallet & Rekening") {
            IconButton(onClick = { nav.navigate(Routes.WALLET_ORDER) }, enabled = s.activeWallets.size > 1) { Icon(Icons.Outlined.SwapVert, "Atur urutan & wallet utama") }
            IconButton(onClick = { nav.navigate(Routes.txForm(type = "TRANSFER")) }) { Text("⇄", style = MaterialTheme.typography.titleLarge) }
            IconButton(onClick = { nav.navigate(Routes.walletForm()) }) { Icon(Icons.Filled.Add, "Tambah wallet") }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                FinCard {
                    Text("Total saldo", style = MaterialTheme.typography.labelMedium, color = Fin.colors.muted)
                    Text(money(s.totalBalance), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    val cc = s.wallets.filter { it.type == WalletType.CREDIT_CARD }.sumOf { -s.balance(it.id) }
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Kartu kredit terpakai", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                            Text(money(cc), style = MaterialTheme.typography.titleSmall, color = if (cc > 0) Fin.colors.expense else MaterialTheme.colorScheme.onSurface)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Jumlah wallet aktif", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                            Text("${s.activeWallets.size}", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            if (s.wallets.isEmpty()) item {
                EmptyState("👛", "Belum ada wallet", "Tambahkan rekening bank, e-wallet, bank digital, uang tunai, atau kartu kredit.", action = "Tambah wallet", onAction = { nav.navigate(Routes.walletForm()) })
            }
            groups.forEachIndexed { gi, (title, list) ->
                item(key = "h$title") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(money(list.sumOf { s.balance(it.id) }), style = MaterialTheme.typography.labelLarge, color = Fin.colors.muted)
                        if (gi == 0 && s.activeWallets.size > 1) TextButton(onClick = { nav.navigate(Routes.WALLET_ORDER) }) { Text("Atur") }
                    }
                }
                items(list, key = { it.id }) { w -> WalletCard(s, w, isPrimary = w.id == primaryId) { nav.navigate(Routes.wallet(w.id)) } }
            }
            if (s.wallets.any { it.archived }) item {
                TextButton(onClick = { showArchived = !showArchived }) { Text(if (showArchived) "Sembunyikan arsip" else "Tampilkan wallet diarsipkan") }
            }
        }
    }
}

@Composable
private fun WalletCard(s: Snapshot, w: WalletEntity, isPrimary: Boolean = false, onClick: () -> Unit) {
    val bal = s.balance(w.id)
    FinCard(onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WalletBadge(w, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.name + if (w.archived) " (arsip)" else "", style = MaterialTheme.typography.titleSmall)
                    if (isPrimary) { Spacer(Modifier.width(6.dp)); Pill("⭐ Default", MaterialTheme.colorScheme.primary) }
                }
                Text(w.type.label + " · " + Providers.byCode(w.provider).name + (w.accountSuffix?.let { " ••$it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
            }
            Text(money(bal), style = MaterialTheme.typography.titleSmall, color = if (bal < 0) Fin.colors.expense else MaterialTheme.colorScheme.onSurface)
        }
        if (w.type == WalletType.CREDIT_CARD && (w.creditLimit ?: 0) > 0) {
            val used = (-bal).coerceAtLeast(0)
            Spacer(Modifier.height(10.dp))
            FinProgress(used.toFloat() / w.creditLimit!!, if (used > w.creditLimit * 0.8) Fin.colors.expense else MaterialTheme.colorScheme.primary)
            Text("Terpakai ${money(used)} dari limit ${money(w.creditLimit)}", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun WalletDetailScreen(s: Snapshot, nav: NavHostController, id: Long) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val w = s.walletMap[id]
    var confirmDelete by remember { mutableStateOf(false) }
    var reconcile by remember { mutableStateOf(false) }
    var onlyTransfers by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        FinTopBar(w?.name ?: "Wallet", onBack = { nav.popBackStack() }) {
            if (w != null) {
                IconButton(onClick = { nav.navigate(Routes.walletForm(w.id)) }) { Icon(Icons.Outlined.Edit, "Edit") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Hapus") }
            }
        }
        if (w == null) { EmptyState("👛", "Wallet tidak ditemukan", ""); return }
        val txs = remember(s, id) { s.transactions.filter { it.walletId == id || it.toWalletId == id } }
        val flow = remember(s, id) { Ledger.walletFlows(id, s.txCore) }
        val series = remember(s, id) {
            val info = s.walletInfos.filter { it.id == id }
            Ledger.dailyBalanceSeries(info, s.txCore, today().minusDays(59), today())
        }
        val shown = if (onlyTransfers) txs.filter { it.type == TxType.TRANSFER || it.type == TxType.CC_SETTLEMENT || it.type == TxType.SAVINGS_ALLOCATION } else txs

        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(w.color)).padding(20.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Providers.byCode(w.provider).name, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text(w.type.label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("Saldo saat ini", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                        Text(money(s.balance(id)), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        w.accountSuffix?.let { Text("•••• $it", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            item {
                FinCard {
                    KeyValue("Saldo awal", money(w.initialBalance))
                    KeyValue("Total uang masuk", money(flow.moneyIn), valueColor = Fin.colors.income)
                    KeyValue("Total uang keluar", money(flow.moneyOut), valueColor = Fin.colors.expense)
                    KeyValue("Terakhir diperbarui", Fmt.dateTime(maxOf(flow.lastActivity ?: 0L, w.updatedAt)))
                    BasisNote("Saldo = saldo awal + uang masuk − uang keluar (termasuk transfer). Saldo tidak pernah diedit langsung.")
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { nav.navigate(Routes.txForm(type = "TRANSFER", wallet = id)) }, modifier = Modifier.weight(1f)) { Text("Transfer") }
                    OutlinedButton(onClick = { reconcile = true }, modifier = Modifier.weight(1f)) { Text("Rekonsiliasi") }
                }
                if (!w.archived && s.primaryWallet?.id != w.id) TextButton(onClick = { scope.launch { app.repository.setPrimaryWallet(w.id) } }) { Text("⭐ Jadikan wallet utama (default)") }
            }
            item { FinCard { Text("Saldo 60 hari", style = MaterialTheme.typography.titleSmall); LineChart(series.map { Fmt.dayMonth(it.first) }, series.map { it.second.toDouble() }, Color(w.color).let { if (it.luminanceLow()) MaterialTheme.colorScheme.primary else it }, title = "Saldo") } }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(if (onlyTransfers) "Riwayat transfer" else "Riwayat transaksi", Modifier.weight(1f))
                    FilterChip(onlyTransfers, { onlyTransfers = !onlyTransfers }, { Text("Hanya transfer") })
                }
            }
            if (shown.isEmpty()) item { Text("Belum ada transaksi", color = Fin.colors.muted, modifier = Modifier.padding(8.dp)) }
            items(shown, key = { it.id }) { t -> TransactionRow(s, t, onClick = { nav.navigate(Routes.txDetail(t.id)) }, perspectiveWalletId = id, showDate = true) }
        }
    }

    if (confirmDelete && w != null) {
        val count = s.transactions.count { it.walletId == id || it.toWalletId == id }
        ConfirmDialog(
            "Hapus ${w.name}?",
            if (count == 0) "Wallet akan dihapus permanen." else "Wallet beserta $count transaksi terkait akan dihapus permanen. Untuk menyimpan riwayat, pilih Edit → Arsipkan.",
            onDismiss = { confirmDelete = false },
        ) { scope.launch { app.repository.deleteWallet(w); nav.popBackStack() } }
    }
    if (reconcile && w != null) {
        var actual by remember { mutableStateOf(s.balance(id).let { if (it < 0) "-${-it}" else "$it" }) }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reconcile = false },
            title = { Text("Rekonsiliasi saldo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Masukkan saldo aktual sesuai aplikasi bank/e-wallet. Selisihnya dicatat sebagai transaksi Penyesuaian.", style = MaterialTheme.typography.bodySmall)
                    MoneyField(actual, { actual = it }, "Saldo aktual", allowNegative = true)
                    val diff = actual.toAmount() - s.balance(id)
                    Text("Selisih: ${Fmt.rupiah(diff, withSign = true)}", style = MaterialTheme.typography.labelLarge, color = if (diff == 0L) Fin.colors.income else Fin.colors.warning)
                    FinTextField(note, { note = it }, "Catatan (opsional)")
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { app.repository.reconcile(id, actual.toAmount(), note) }; reconcile = false }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { reconcile = false }) { Text("Batal") } },
        )
    }
}

private fun Color.luminanceLow(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.25f

@Composable
fun WalletFormScreen(s: Snapshot, nav: NavHostController, id: Long, typeArg: String) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val existing = s.walletMap[id]
    var type by rememberSaveable { mutableStateOf(existing?.type ?: runCatching { WalletType.valueOf(typeArg) }.getOrDefault(WalletType.BANK)) }
    var provider by rememberSaveable { mutableStateOf(existing?.provider ?: Providers.all.first { it.type == type }.code) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var initial by rememberSaveable { mutableStateOf(existing?.initialBalance?.let { if (it < 0) "-${-it}" else "$it" } ?: "") }
    var limit by rememberSaveable { mutableStateOf(existing?.creditLimit?.toString() ?: "") }
    var suffix by rememberSaveable { mutableStateOf(existing?.accountSuffix ?: "") }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var include by rememberSaveable { mutableStateOf(existing?.includeInTotal ?: true) }
    var archived by rememberSaveable { mutableStateOf(existing?.archived ?: false) }
    var isMain by rememberSaveable { mutableStateOf(existing?.isMain ?: true) }
    var color by rememberSaveable { mutableStateOf(existing?.color ?: Providers.byCode(provider).color) }
    var error by remember { mutableStateOf<String?>(null) }

    val providerOptions = Providers.all.filter { it.type == type || (it.code == "CUSTOM") }.map { p ->
        Option(p.code, p.name, leading = { WalletBadge(WalletEntity(name = p.name, type = p.type, provider = p.code, color = p.color, initialBalance = 0), 32.dp) })
    }
    val palette = listOf(0xFF7C5CFF, 0xFF3D8BFF, 0xFF2DD4A4, 0xFFFFB547, 0xFFFF5C7A, 0xFF22C3E6, 0xFFE879F9, 0xFF0060AF, 0xFF2BB673, 0xFF5B6B8C)

    Column(Modifier.fillMaxSize()) {
        FinTopBar(if (existing == null) "Tambah wallet" else "Edit wallet", onBack = { nav.popBackStack() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectField("Jenis", WalletType.entries.map { Option(it, it.label) }, type) {
                type = it
                val p = Providers.all.first { pr -> pr.type == it }
                provider = p.code; color = p.color
            }
            SelectField("Bank / penyedia", providerOptions, provider) {
                provider = it
                val p = Providers.byCode(it)
                color = p.color
                if (name.isBlank() || Providers.all.any { pr -> pr.name == name }) name = if (it == "CUSTOM") "" else p.name
            }
            FinTextField(name, { name = it }, "Nama akun", supporting = "mis. BCA Gaji, GoPay Harian")
            MoneyField(
                initial, { initial = it }, if (type == WalletType.CREDIT_CARD) "Tagihan berjalan saat ini (isi negatif)" else "Saldo awal",
                allowNegative = true,
                supporting = if (existing != null) "Mengubah saldo awal akan menggeser saldo saat ini. Untuk mencocokkan saldo, gunakan Rekonsiliasi." else "Saldo saat wallet mulai dicatat",
            )
            if (type == WalletType.CREDIT_CARD) MoneyField(limit, { limit = it }, "Limit kartu kredit")
            FinTextField(suffix, { if (it.length <= 4 && it.all(Char::isDigit)) suffix = it }, "4 digit terakhir (opsional)", supporting = "Hanya sebagai penanda. Jangan pernah memasukkan PIN/password.")
            FinTextField(note, { note = it }, "Catatan", singleLine = false)
            Text("Warna", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                palette.forEach { c ->
                    Box(
                        Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(10.dp)).background(Color(c))
                            .then(if (c == color) Modifier.padding(3.dp).background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp)) else Modifier)
                            .clickable { color = c },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Hitung dalam total saldo", modifier = Modifier.weight(1f)); Switch(include, { include = it }) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Wallet utama")
                    Text("Tampil di bagian atas. Matikan untuk memindahkan ke bagian Opsional.", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                }
                Switch(isMain, { isMain = it })
            }
            if (existing != null) Row(verticalAlignment = Alignment.CenterVertically) { Text("Arsipkan (sembunyikan, riwayat tetap)", modifier = Modifier.weight(1f)); Switch(archived, { archived = it }) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                if (name.isBlank()) { error = "Nama wajib diisi"; return@Button }
                scope.launch {
                    app.repository.saveWallet(
                        (existing ?: WalletEntity(name = name, type = type, provider = provider, color = color, initialBalance = 0)).copy(
                            name = name.trim(), type = type, provider = provider, color = color, initialBalance = initial.toAmount(),
                            creditLimit = if (type == WalletType.CREDIT_CARD) limit.toAmount().takeIf { it > 0 } else null,
                            accountSuffix = suffix.ifBlank { null }, note = note, includeInTotal = include, archived = archived, isMain = isMain,
                        ),
                    )
                    nav.popBackStack()
                }
            }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Simpan") }
        }
    }
}
