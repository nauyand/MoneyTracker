package com.moneytracker.app.ui.savings

import com.moneytracker.app.ui.fx.streakAfterNewRecord
import com.moneytracker.app.ui.fx.CelebKind
import com.moneytracker.app.ui.fx.Celebration
import com.moneytracker.app.LocalUi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.moneytracker.app.data.Repository
import com.moneytracker.app.data.SavingsGoalEntity
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.ui.Routes
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
import com.moneytracker.app.ui.components.SegmentedTabs
import com.moneytracker.app.ui.components.SelectField
import com.moneytracker.app.ui.components.WalletBadge
import com.moneytracker.app.ui.components.money
import com.moneytracker.app.ui.components.toAmount
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.core.Fmt
import kotlinx.coroutines.launch
import java.time.LocalDate

private val goalPresets = listOf(
    "🆘" to "Dana darurat", "💻" to "Beli laptop", "🏍️" to "Beli kendaraan", "🎓" to "Biaya pendidikan",
    "💍" to "Dana menikah", "🏠" to "DP rumah", "🐷" to "Tabungan pribadi", "✈️" to "Liburan",
)

@Composable
fun SavingsScreen(s: Snapshot, nav: NavHostController) {
    val goals = s.goals.filter { !it.archived }
    var contribute by remember { mutableStateOf<SavingsGoalEntity?>(null) }
    val progress = remember(s) { goals.associate { it.id to s.goalProgress(it) } }

    Column(Modifier.fillMaxSize()) {
        FinTopBar("Target Tabungan") { IconButton(onClick = { nav.navigate(Routes.goalForm()) }) { Icon(Icons.Filled.Add, "Tambah target") } }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                FinCard {
                    Text("Total tabungan", style = MaterialTheme.typography.labelMedium, color = Fin.colors.muted)
                    Text(money(s.totalSavings), style = MaterialTheme.typography.headlineMedium)
                    val collected = progress.values.sumOf { it.collected }
                    val target = progress.values.sumOf { it.target }
                    if (target > 0) {
                        Spacer(Modifier.height(8.dp))
                        FinProgress(collected.toFloat() / target, Fin.colors.savings)
                        Text("Terkumpul di target: ${money(collected)} dari ${money(target)}", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted, modifier = Modifier.padding(top = 4.dp))
                    }
                    BasisNote("Total tabungan = saldo rekening bertipe Tabungan + dana yang dialokasikan ke target tanpa wallet tujuan.")
                }
            }
            if (goals.isEmpty()) item {
                EmptyState("🎯", "Belum ada target", "Buat target seperti dana darurat, laptop baru, atau DP rumah — lengkap dengan estimasi tanggal tercapai.",
                    action = "Buat target", onAction = { nav.navigate(Routes.goalForm()) })
            }
            items(goals, key = { it.id }) { g ->
                val p = progress.getValue(g.id)
                FinCard(onClick = { nav.navigate(Routes.goalForm(g.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiIcon(g.icon, Color(g.color), 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.titleMedium)
                            Text("${money(p.collected)} / ${money(g.target)}", style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted)
                        }
                        Text(Fmt.percent(p.ratio), style = MaterialTheme.typography.titleLarge, color = Color(g.color))
                    }
                    Spacer(Modifier.height(12.dp))
                    FinProgress(p.ratio.toFloat(), Color(g.color), height = 10.dp)
                    Spacer(Modifier.height(10.dp))
                    g.deadline?.let { KeyValue("Deadline", Fmt.date(LocalDate.ofEpochDay(it))) }
                    p.requiredPerMonth?.let { if (p.remaining > 0) KeyValue("Perlu ditabung", "${money(it.toLong())}/bulan") }
                    g.monthlyTarget?.let { KeyValue("Rencana menabung", "${money(it)}/bulan") }
                    p.monthlyPace?.let { KeyValue("Laju aktual", "${money(it.toLong())}/bulan") }
                    KeyValue("Estimasi tercapai", p.estimatedCompletion?.let { if (p.remaining == 0L) "Sudah tercapai 🎉" else Fmt.date(it) } ?: "Belum bisa diestimasi")
                    p.onTrack?.let { Row(Modifier.padding(top = 4.dp)) { Pill(if (it) "Sesuai jadwal" else "Terlambat dari deadline", if (it) Fin.colors.income else Fin.colors.expense) } }
                    BasisNote(p.basis)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { contribute = g }, modifier = Modifier.fillMaxWidth()) { Text("Setor / tarik dana") }
                }
            }
        }
    }
    contribute?.let { ContributeDialog(s, it) { contribute = null } }
}

@Composable
private fun ContributeDialog(s: Snapshot, g: SavingsGoalEntity, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(0) }
    var amount by remember { mutableStateOf("") }
    var walletId by remember { mutableStateOf(s.activeWallets.firstOrNull { it.id != g.walletId && !it.type.isLiability }?.id) }
    var date by remember { mutableStateOf(LocalDate.now(Fmt.zone)) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(g.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SegmentedTabs(listOf("Setor", "Tarik"), mode, { mode = it })
                MoneyField(amount, { amount = it }, "Nominal")
                SelectField(if (mode == 0) "Dari wallet" else "Ke wallet", s.activeWallets.filter { it.id != g.walletId }.map { w -> Option<Long?>(w.id, w.name, money(s.balance(w.id)), leading = { WalletBadge(w, 28.dp) }) }, walletId) { walletId = it }
                DateField("Tanggal", date, { if (it != null) date = it })
                Text(
                    if (mode == 0) "Dicatat sebagai alokasi tabungan" + (g.walletId?.let { " ke ${s.walletMap[it]?.name}" } ?: "") + " — bukan pengeluaran."
                    else "Dicatat sebagai transfer dari wallet target — bukan pemasukan.",
                    style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = amount.toAmount() > 0 && walletId != null, onClick = {
                scope.launch {
                    try {
                        val millis = Fmt.toMillis(date.atTime(java.time.LocalTime.now()))
                        val amt = amount.toAmount()
                        if (mode == 0) {
                            val p = s.goalProgress(g)
                            app.repository.contributeGoal(g, walletId!!, amt, millis, "")
                            ui.celebrate(Celebration(
                                CelebKind.SAVINGS, amt, "Tabungan bertambah", from = s.walletMap[walletId!!],
                                subtitle = "Disetor ke ${g.name}", goalBefore = p.ratio.toFloat(), streak = s.streakAfterNewRecord(),
                                goalAfter = if (g.target > 0) ((p.collected + amt).toDouble() / g.target).toFloat().coerceAtMost(1f) else null, goalName = g.name,
                            ))
                        } else {
                            app.repository.withdrawGoal(g, walletId!!, amt, millis, "")
                            ui.celebrate(Celebration(CelebKind.TRANSFER, amt, "Dana ditarik", s.walletMap[g.walletId ?: 0], s.walletMap[walletId!!], "Dari target ${g.name}"))
                        }
                        onDismiss()
                    } catch (e: Repository.ValidationException) { error = e.message }
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun GoalFormScreen(s: Snapshot, nav: NavHostController, id: Long) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val e = s.goalMap[id]
    var name by rememberSaveable { mutableStateOf(e?.name ?: "") }
    var icon by rememberSaveable { mutableStateOf(e?.icon ?: "🐷") }
    var target by rememberSaveable { mutableStateOf(e?.target?.toString() ?: "") }
    var initial by rememberSaveable { mutableStateOf(e?.initialAmount?.takeIf { it > 0 }?.toString() ?: "") }
    var monthly by rememberSaveable { mutableStateOf(e?.monthlyTarget?.toString() ?: "") }
    var deadline by rememberSaveable { mutableStateOf(e?.deadline?.let(LocalDate::ofEpochDay)) }
    var walletId by rememberSaveable { mutableStateOf(e?.walletId) }
    var archived by rememberSaveable { mutableStateOf(e?.archived ?: false) }
    var confirm by remember { mutableStateOf(false) }
    val colors = listOf(0xFFB57CFF, 0xFF7C5CFF, 0xFF3D8BFF, 0xFF2DD4A4, 0xFFFFB547, 0xFFFF5C7A)
    var color by rememberSaveable { mutableStateOf(e?.color ?: colors.first()) }

    Column(Modifier.fillMaxSize()) {
        FinTopBar(if (e == null) "Target baru" else "Edit target", onBack = { nav.popBackStack() }) {
            if (e != null) TextButton(onClick = { confirm = true }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (e == null) {
                Text("Pilih cepat", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    goalPresets.take(8).forEach { (em, n) ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (icon == em) MaterialTheme.colorScheme.primaryContainer else Fin.colors.cardElevated)
                                .clickable { icon = em; if (name.isBlank() || goalPresets.any { it.second == name }) name = n }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(em, fontSize = 20.sp) }
                    }
                }
            }
            FinTextField(name, { name = it }, "Nama target")
            MoneyField(target, { target = it }, "Nominal target", large = true)
            MoneyField(initial, { initial = it }, "Sudah terkumpul sebelumnya (opsional)", supporting = "Hanya untuk progres; tidak mengubah saldo wallet")
            MoneyField(monthly, { monthly = it }, "Target menabung per bulan (opsional)")
            DateField("Deadline", deadline, { deadline = it }, allowClear = true)
            SelectField(
                "Wallet penyimpanan (opsional)",
                listOf(Option<Long?>(null, "Tidak ada (mis. celengan)")) + s.activeWallets.filter { !it.type.isLiability }.map { w -> Option<Long?>(w.id, w.name, w.type.label, leading = { WalletBadge(w, 28.dp) }) },
                walletId,
            ) { walletId = it }
            Text("Warna", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                colors.forEach { c ->
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(c)).clickable { color = c }, contentAlignment = Alignment.Center) {
                        if (c == color) Text("✓", color = Color.White)
                    }
                }
            }
            if (e != null) Row(verticalAlignment = Alignment.CenterVertically) { Text("Arsipkan target", Modifier.weight(1f)); Switch(archived, { archived = it }) }
            Button(onClick = {
                scope.launch {
                    app.repository.saveGoal(
                        SavingsGoalEntity(
                            id = e?.id ?: 0, name = name.trim(), icon = icon, color = color, target = target.toAmount(), initialAmount = initial.toAmount(),
                            deadline = deadline?.toEpochDay(), monthlyTarget = monthly.toAmount().takeIf { it > 0 }, walletId = walletId, archived = archived,
                            createdAt = e?.createdAt ?: System.currentTimeMillis(),
                        ),
                    )
                    nav.popBackStack()
                }
            }, enabled = name.isNotBlank() && target.toAmount() > 0, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Simpan") }
            if (e != null) OutlinedButton(onClick = { nav.navigate(Routes.TRANSACTIONS) }, modifier = Modifier.fillMaxWidth()) { Text("Lihat transaksi") }
        }
    }
    if (confirm && e != null) ConfirmDialog("Hapus target ${e.name}?", "Transaksi setoran tetap tersimpan, hanya tidak terhubung lagi ke target ini.", onDismiss = { confirm = false }) {
        scope.launch { app.repository.deleteGoal(e); nav.popBackStack() }
    }
}
