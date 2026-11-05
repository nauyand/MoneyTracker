package com.moneytracker.app.ui.budget

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.moneytracker.app.data.BudgetEntity
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.ui.components.BasisNote
import com.moneytracker.app.ui.components.EmojiIcon
import com.moneytracker.app.ui.components.EmptyState
import com.moneytracker.app.ui.components.FinCard
import com.moneytracker.app.ui.components.FinProgress
import com.moneytracker.app.ui.components.FinTopBar
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.components.MoneyField
import com.moneytracker.app.ui.components.Option
import com.moneytracker.app.ui.components.Pill
import com.moneytracker.app.ui.components.SectionHeader
import com.moneytracker.app.ui.components.SelectField
import com.moneytracker.app.ui.components.money
import com.moneytracker.app.ui.components.toAmount
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.app.ui.thisMonth
import com.moneytracker.core.BudgetStatus
import com.moneytracker.core.CategoryKind
import com.moneytracker.core.Fmt
import com.moneytracker.core.Ledger
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

@Composable
fun BudgetScreen(s: Snapshot, @Suppress("UNUSED_PARAMETER") nav: NavHostController) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(thisMonth()) }
    var editing by remember { mutableStateOf<BudgetEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    val statuses = remember(s, month) { s.budgets.map { it to s.budgetStatus(it, month) } }
    val totalAvail = statuses.filter { !it.first.isSavings }.sumOf { it.second.available }
    val totalSpent = statuses.filter { !it.first.isSavings }.sumOf { it.second.spent }
    val unbudgeted = remember(s, month) {
        val (f, t) = Ledger.monthRange(month)
        val budgeted = s.budgets.mapNotNull { it.categoryId }.toSet()
        Ledger.expenseByRootCategory(s.txCore.filter { it.dateTime in f until t }, s.catInfo).filter { it.key !in budgeted && it.value > 0 }
            .entries.sortedByDescending { it.value }
    }

    Column(Modifier.fillMaxSize()) {
        FinTopBar("Anggaran") { IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "Tambah anggaran") } }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Bulan sebelumnya") }
                    Text(Fmt.month(month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Bulan berikutnya") }
                }
            }
            item {
                FinCard {
                    Text("Total anggaran pengeluaran", style = MaterialTheme.typography.labelMedium, color = Fin.colors.muted)
                    Text("${money(totalSpent)} / ${money(totalAvail)}", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    val ratio = if (totalAvail > 0) totalSpent.toFloat() / totalAvail else 0f
                    FinProgress(ratio, levelColor(if (ratio >= 1f) BudgetStatus.Level.OVER else if (ratio >= 0.8f) BudgetStatus.Level.WARNING else BudgetStatus.Level.OK), height = 10.dp)
                    Text("Sisa ${money(totalAvail - totalSpent)}", style = MaterialTheme.typography.labelMedium, color = Fin.colors.muted, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (s.budgets.isEmpty()) item {
                EmptyState("🎯", "Belum ada anggaran", "Tetapkan batas bulanan untuk makan, transportasi, bensin, belanja, hiburan, tagihan, dan tabungan.",
                    action = "Buat anggaran", onAction = { creating = true })
            }
            items(statuses, key = { it.first.id }) { (b, st) ->
                val cat = b.categoryId?.let { s.categoryMap[it] }
                FinCard(onClick = { editing = b }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiIcon(if (b.isSavings) "🐷" else cat?.icon ?: "📁", Color(cat?.color ?: 0xFFB57CFF), 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.budgetName(b), style = MaterialTheme.typography.titleSmall)
                            Text(
                                (if (b.isSavings) "Target ditabung " else "Anggaran ") + money(st.base) + if (st.carriedOver > 0) " + rollover ${money(st.carriedOver)}" else "",
                                style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted,
                            )
                        }
                        when {
                            b.isSavings -> Pill(if (st.spent >= st.available) "Tercapai" else Fmt.percent(st.usage), if (st.spent >= st.available) Fin.colors.income else Fin.colors.savings)
                            st.level == BudgetStatus.Level.OVER -> Pill("Lewat batas", Fin.colors.expense)
                            st.level == BudgetStatus.Level.WARNING -> Pill("Hampir habis", Fin.colors.warning)
                            else -> Pill(Fmt.percent(st.usage), Fin.colors.income)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    FinProgress(st.usage.toFloat(), if (b.isSavings) Fin.colors.savings else levelColor(st.level))
                    Row(Modifier.padding(top = 6.dp)) {
                        Text((if (b.isSavings) "Ditabung " else "Terpakai ") + money(st.spent), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Text(
                            if (st.remaining >= 0) (if (b.isSavings) "Kurang " else "Sisa ") + money(st.remaining) else (if (b.isSavings) "Lebih " else "Lebih ") + money(-st.remaining),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                            color = if (b.isSavings) Fin.colors.muted else if (st.remaining < 0) Fin.colors.expense else Fin.colors.muted,
                        )
                    }
                }
            }
            if (unbudgeted.isNotEmpty()) {
                item { SectionHeader("Belum dianggarkan") }
                item {
                    FinCard {
                        unbudgeted.forEach { (cat, amt) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(cat?.let { s.categoryMap[it]?.let { c -> "${c.icon} ${c.name}" } } ?: "Tanpa kategori", modifier = Modifier.weight(1f))
                                Text(money(amt), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            item { BasisNote("Realisasi dihitung dari pengeluaran kategori induk beserta seluruh subkategorinya. Peringatan muncul pada 80% dan saat melewati 100%. Rollover menambahkan sisa anggaran bulan lalu (bila positif).") }
        }
    }

    if (creating || editing != null) BudgetDialog(s, editing, onDismiss = { creating = false; editing = null },
        onDelete = { b -> scope.launch { app.repository.deleteBudget(b) } }) { b -> scope.launch { app.repository.saveBudget(b) } }
}

@Composable
fun levelColor(level: BudgetStatus.Level) = when (level) {
    BudgetStatus.Level.OK -> Fin.colors.income
    BudgetStatus.Level.WARNING -> Fin.colors.warning
    BudgetStatus.Level.OVER -> Fin.colors.expense
}

@Composable
private fun BudgetDialog(s: Snapshot, existing: BudgetEntity?, onDismiss: () -> Unit, onDelete: (BudgetEntity) -> Unit, onSave: (BudgetEntity) -> Unit) {
    val SAVINGS = -1L
    var target by remember { mutableStateOf<Long?>(if (existing?.isSavings == true) SAVINGS else existing?.categoryId) }
    var amount by remember { mutableStateOf(existing?.amount?.toString() ?: "") }
    var rollover by remember { mutableStateOf(existing?.rollover ?: false) }
    val used = s.budgets.filter { it.id != existing?.id }.map { if (it.isSavings) SAVINGS else it.categoryId }.toSet()
    val options = listOf(Option<Long?>(SAVINGS, "🐷  Tabungan", "realisasi = alokasi tabungan")) +
        s.roots(CategoryKind.EXPENSE).filter { it.id !in used }.map { Option<Long?>(it.id, "${it.icon}  ${it.name}") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Anggaran baru" else "Edit anggaran") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectField("Kategori", options.filter { it.value !in used || it.value == target }, target) { target = it }
                MoneyField(amount, { amount = it }, "Batas per bulan")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Rollover", style = MaterialTheme.typography.bodyLarge)
                        Text("Sisa anggaran dibawa ke bulan berikutnya", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                    }
                    Switch(rollover, { rollover = it })
                }
                if (existing != null) TextButton(onClick = { onDelete(existing); onDismiss() }) { Text("Hapus anggaran", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = target != null && amount.toAmount() > 0, onClick = {
                val t = target!!
                onSave(
                    (existing ?: BudgetEntity(amount = 0, startMonth = thisMonth().toString())).copy(
                        categoryId = if (t == SAVINGS) null else t, isSavings = t == SAVINGS, amount = amount.toAmount(), rollover = rollover,
                    ),
                )
                onDismiss()
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
