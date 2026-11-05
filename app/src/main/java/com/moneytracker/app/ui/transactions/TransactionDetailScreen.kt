package com.moneytracker.app.ui.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.ui.Routes
import com.moneytracker.app.ui.components.ConfirmDialog
import com.moneytracker.app.ui.components.EmojiIcon
import com.moneytracker.app.ui.components.EmptyState
import com.moneytracker.app.ui.components.FinCard
import com.moneytracker.app.ui.components.FinTopBar
import com.moneytracker.app.ui.components.KeyValue
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.components.Pill
import com.moneytracker.app.ui.components.SectionHeader
import com.moneytracker.app.ui.components.Thumb
import com.moneytracker.app.ui.components.money
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.core.Fmt
import com.moneytracker.core.Ledger
import kotlinx.coroutines.launch

@Composable
fun TransactionDetailScreen(s: Snapshot, nav: NavHostController, id: Long) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val t = s.transactions.firstOrNull { it.id == id }
    var confirm by remember { mutableStateOf(false) }
    val history by app.repository.auditFor("transaction", id).collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        FinTopBar("Detail transaksi", onBack = { nav.popBackStack() }) {
            if (t != null) {
                IconButton(onClick = { nav.navigate(Routes.txForm(t.id)) }) { Icon(Icons.Outlined.Edit, "Edit") }
                if (t.id !in s.fuelTxIds) IconButton(onClick = { nav.navigate(Routes.txForm(t.id, duplicate = true)) }) { Icon(Icons.Outlined.ContentCopy, "Duplikat") }
                IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.Delete, "Hapus") }
            }
        }
        if (t == null) { EmptyState("🗑️", "Transaksi tidak ditemukan", "Mungkin sudah dihapus."); return }
        val v = txVisual(s, t)
        val core = s.core(t)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FinCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiIcon(v.emoji, v.color, 52.dp)
                    Spacer(Modifier.padding(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(v.title, style = MaterialTheme.typography.titleMedium)
                        Pill(t.type.label, v.color)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(v.amountText, style = MaterialTheme.typography.headlineMedium, color = v.amountColor)
                Text(Fmt.dateWithDay(Fmt.toDate(t.dateTime)) + " · " + Fmt.time(t.dateTime), style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted)
            }
            FinCard {
                KeyValue("Wallet", s.walletMap[t.walletId]?.name ?: "-")
                t.toWalletId?.let { KeyValue("Wallet tujuan", s.walletMap[it]?.name ?: "-") }
                if (t.categoryId != null) KeyValue("Kategori", s.categoryLabel(t.categoryId))
                KeyValue("Nominal", money(t.amount))
                if (t.fee > 0) KeyValue("Biaya/bunga", money(t.fee))
                KeyValue("Dihitung pemasukan", money(Ledger.incomeOf(core)))
                KeyValue("Dihitung pengeluaran", money(Ledger.expenseOf(core)))
                t.goalId?.let { g -> KeyValue("Target tabungan", s.goalMap[g]?.name ?: "-") }
                t.debtId?.let { d -> KeyValue("Utang/piutang", s.debtMap[d]?.name ?: "-") }
                t.vehicleId?.let { vId -> KeyValue("Kendaraan", s.vehicleMap[vId]?.name ?: "-") }
                if (t.recurringId != null) KeyValue("Sumber", "Transaksi berulang")
                if (t.importKey != null) KeyValue("Sumber", "Impor file")
            }
            if (t.note.isNotBlank() || t.tags.isNotBlank() || t.location.isNotBlank()) FinCard {
                if (t.note.isNotBlank()) KeyValue("Catatan", t.note)
                if (t.location.isNotBlank()) KeyValue("Lokasi", t.location)
                if (t.tags.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    t.tags.split(',').forEach { Pill("#$it", MaterialTheme.colorScheme.primary) }
                }
            }
            val atts = s.attachmentsByTx[t.id].orEmpty()
            if (atts.isNotEmpty()) {
                SectionHeader("Foto struk")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    atts.forEach { Thumb(file = app.repository.attachmentFile(it), size = 160.dp, maxPx = 800) }
                }
            }
            if (history.isNotEmpty()) {
                SectionHeader("Riwayat perubahan")
                FinCard {
                    history.forEach { h ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text("${when (h.action) { "CREATE" -> "Dibuat"; "UPDATE" -> "Diubah"; else -> "Dihapus" }} · ${Fmt.dateTime(h.timestamp)}", style = MaterialTheme.typography.labelLarge)
                            Text(h.summary, style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted)
                        }
                    }
                }
            }
        }
    }
    if (confirm && t != null) ConfirmDialog(
        "Hapus transaksi?",
        "Saldo wallet akan dihitung ulang." + if (t.id in s.fuelTxIds) " Data pengisian BBM terkait juga dihapus." else "",
        onDismiss = { confirm = false },
    ) { scope.launch { app.repository.deleteTransaction(t.id); nav.popBackStack() } }
}
