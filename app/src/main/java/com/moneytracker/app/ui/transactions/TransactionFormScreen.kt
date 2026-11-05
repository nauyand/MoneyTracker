package com.moneytracker.app.ui.transactions

import com.moneytracker.app.ui.fx.celebrationFor
import com.moneytracker.app.LocalUi
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.moneytracker.app.data.AttachmentEntity
import com.moneytracker.app.data.Repository
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.data.TransactionEntity
import com.moneytracker.app.ui.Routes
import com.moneytracker.app.ui.components.FinCard
import com.moneytracker.app.ui.components.FinTextField
import com.moneytracker.app.ui.components.FinTopBar
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.components.MoneyField
import com.moneytracker.app.ui.components.Option
import com.moneytracker.app.ui.components.DateField
import com.moneytracker.app.ui.components.SegmentedTabs
import com.moneytracker.app.ui.components.SelectField
import com.moneytracker.app.ui.components.Thumb
import com.moneytracker.app.ui.components.TimeField
import com.moneytracker.app.ui.components.WalletBadge
import com.moneytracker.app.ui.components.money
import com.moneytracker.app.ui.components.toAmount
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.core.CategoryKind
import com.moneytracker.core.DebtDirection
import com.moneytracker.core.Fmt
import com.moneytracker.core.TxType
import com.moneytracker.core.WalletType
import kotlinx.coroutines.launch
import java.time.LocalDateTime

private val mainTypes = listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)
private val otherTypes = listOf(TxType.SAVINGS_ALLOCATION, TxType.CC_SETTLEMENT, TxType.DEBT_PAYMENT, TxType.DEBT_COLLECTION, TxType.LOAN_RECEIVED, TxType.LOAN_GIVEN, TxType.ADJUSTMENT)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionFormScreen(s: Snapshot, nav: NavHostController, id: Long, typeArg: String, walletArg: Long, duplicate: Boolean) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val existing = remember(id) { if (id != 0L) s.transactions.firstOrNull { it.id == id } else null }
    val isFuel = existing != null && existing.id in s.fuelTxIds && !duplicate
    val defaultWallet = walletArg.takeIf { it != 0L } ?: s.primaryWallet?.id

    var type by rememberSaveable { mutableStateOf(existing?.type ?: runCatching { TxType.valueOf(typeArg) }.getOrDefault(TxType.EXPENSE)) }
    var amount by rememberSaveable { mutableStateOf(existing?.amount?.toString() ?: "") }
    var fee by rememberSaveable { mutableStateOf(existing?.fee?.takeIf { it > 0 }?.toString() ?: "") }
    var walletId by rememberSaveable { mutableStateOf(existing?.walletId ?: defaultWallet) }
    var toWalletId by rememberSaveable { mutableStateOf(existing?.toWalletId) }
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId) }
    var goalId by rememberSaveable { mutableStateOf(existing?.goalId) }
    var debtId by rememberSaveable { mutableStateOf(existing?.debtId) }
    val initialDt = if (existing != null && !duplicate) Fmt.toDateTime(existing.dateTime) else LocalDateTime.now(Fmt.zone)
    var date by rememberSaveable { mutableStateOf(initialDt.toLocalDate()) }
    var time by rememberSaveable { mutableStateOf(initialDt.toLocalTime().withSecond(0).withNano(0)) }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var tags by rememberSaveable { mutableStateOf(existing?.tags ?: "") }
    var location by rememberSaveable { mutableStateOf(existing?.location ?: "") }
    var showMore by rememberSaveable { mutableStateOf(existing != null && (existing.note.isNotBlank() || existing.tags.isNotBlank() || existing.location.isNotBlank())) }
    val newImages = remember { mutableStateListOf<Uri>() }
    val removed = remember { mutableStateListOf<AttachmentEntity>() }
    val existingAttachments = if (existing != null && !duplicate) s.attachmentsByTx[existing.id].orEmpty().filter { it !in removed } else emptyList()
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) newImages += uri }

    val category = categoryId?.let { s.categoryMap[it] }
    val walletOptions = s.wallets.filter { !it.archived || it.id == walletId || it.id == toWalletId }.map { w ->
        Option<Long?>(w.id, w.name, "${w.type.label} · saldo ${money(s.balance(w.id))}", leading = { WalletBadge(w, 32.dp) })
    }

    Column(Modifier.fillMaxSize()) {
        FinTopBar(if (existing == null || duplicate) "Transaksi baru" else "Edit transaksi", onBack = { nav.popBackStack() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isFuel) {
                FinCard(color = Fin.colors.warning.copy(alpha = 0.12f)) {
                    Text("Transaksi dari pengisian BBM", style = MaterialTheme.typography.titleSmall)
                    Text("Ubah lewat form BBM agar odometer, liter, dan analisis konsumsi tetap sinkron.", style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted)
                    TextButton(onClick = {
                        val log = s.fuelLogs.first { it.transactionId == existing!!.id }
                        nav.popBackStack()
                        nav.navigate(Routes.fuelForm(log.vehicleId, log.id))
                    }) { Text("Buka form BBM") }
                }
            }

            // ---------- Jenis ----------
            SegmentedTabs(
                listOf("Pengeluaran", "Pemasukan", "Transfer"), mainTypes.indexOf(type).let { if (it < 0) -1 else it },
                { type = mainTypes[it]; categoryId = null },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                otherTypes.forEach { t -> FilterChip(type == t, { type = t; categoryId = null }, { Text(t.label) }) }
            }

            MoneyField(
                amount, { amount = it }, if (type == TxType.ADJUSTMENT) "Selisih (boleh negatif)" else "Nominal",
                allowNegative = type == TxType.ADJUSTMENT, large = true,
            )

            // ---------- Wallet ----------
            val fromLabel = when (type) {
                TxType.INCOME, TxType.DEBT_COLLECTION, TxType.LOAN_RECEIVED -> "Masuk ke wallet"
                TxType.ADJUSTMENT -> "Wallet"
                else -> "Dari wallet"
            }
            SelectField(fromLabel, walletOptions, walletId) { walletId = it }

            if (type == TxType.TRANSFER || type == TxType.CC_SETTLEMENT || type == TxType.SAVINGS_ALLOCATION) {
                val targets = walletOptions.filter { o ->
                    val w = s.walletMap[o.value] ?: return@filter false
                    o.value != walletId && (type != TxType.CC_SETTLEMENT || w.type == WalletType.CREDIT_CARD)
                }
                val opts = if (type == TxType.SAVINGS_ALLOCATION) listOf(Option<Long?>(null, "Tidak ke wallet (mis. celengan)")) + targets else targets
                SelectField(if (type == TxType.CC_SETTLEMENT) "Kartu kredit yang dilunasi" else "Ke wallet", opts, toWalletId,
                    placeholder = if (type == TxType.CC_SETTLEMENT && targets.isEmpty()) "Belum ada wallet kartu kredit" else "Pilih") { toWalletId = it }
            }

            if (type == TxType.SAVINGS_ALLOCATION) {
                SelectField("Target tabungan (opsional)", listOf(Option<Long?>(null, "Tanpa target")) + s.goals.filter { !it.archived }.map { Option<Long?>(it.id, "${it.icon} ${it.name}") }, goalId) {
                    goalId = it
                    it?.let { g -> s.goalMap[g]?.walletId?.let { w -> if (w != walletId) toWalletId = w } }
                }
            }

            if (type in setOf(TxType.DEBT_PAYMENT, TxType.DEBT_COLLECTION, TxType.LOAN_GIVEN, TxType.LOAN_RECEIVED)) {
                val dir = if (type == TxType.DEBT_PAYMENT || type == TxType.LOAN_RECEIVED) DebtDirection.PAYABLE else DebtDirection.RECEIVABLE
                val debts = s.debts.filter { it.direction == dir && !it.archived }
                SelectField(
                    if (dir == DebtDirection.PAYABLE) "Utang" else "Piutang",
                    (if (type == TxType.LOAN_GIVEN || type == TxType.LOAN_RECEIVED) listOf(Option<Long?>(null, "Tidak dihubungkan")) else emptyList()) +
                        debts.map { Option<Long?>(it.id, it.name, "sisa ${money(s.debtStatus(it).remaining)}") },
                    debtId, placeholder = if (debts.isEmpty()) "Belum ada data — tambah di menu Utang" else "Pilih",
                ) { debtId = it }
            }

            if (type in setOf(TxType.TRANSFER, TxType.SAVINGS_ALLOCATION, TxType.CC_SETTLEMENT, TxType.DEBT_PAYMENT)) {
                MoneyField(fee, { fee = it }, if (type == TxType.DEBT_PAYMENT) "Bunga / denda (dihitung pengeluaran)" else "Biaya admin (opsional)")
            }

            // ---------- Kategori ----------
            if (type == TxType.EXPENSE || type == TxType.INCOME) {
                val kind = if (type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                SelectField("Kategori", categoryOptions(s, kind, hidePreferred = existing == null), categoryId, placeholder = "Pilih kategori") { categoryId = it }
                val preferred = category?.preferredType
                if (type == TxType.EXPENSE && preferred != null) {
                    FinCard(color = Fin.colors.warning.copy(alpha = 0.12f)) {
                        Text("Hindari penghitungan ganda", style = MaterialTheme.typography.titleSmall)
                        Text("“${category.name}” sebaiknya dicatat sebagai ${preferred.label} agar tidak dihitung sebagai pengeluaran konsumsi.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { type = preferred }) { Text("Ganti ke ${preferred.label}") }
                    }
                }
            }

            FinTextField(description, { description = it }, "Deskripsi")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField("Tanggal", date, { if (it != null) date = it }, Modifier.weight(1.4f))
                TimeField("Jam", time, { time = it }, Modifier.weight(1f))
            }

            if (!showMore) TextButton(onClick = { showMore = true }) { Text("+ Catatan, tag, lokasi, foto struk") }
            if (showMore) {
                FinTextField(note, { note = it }, "Catatan", singleLine = false)
                FinTextField(tags, { tags = it }, "Tag (pisahkan dengan koma)", supporting = "contoh: kantor, proyek-a")
                FinTextField(location, { location = it }, "Lokasi (opsional)")
                Text("Foto struk", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    existingAttachments.forEach { a ->
                        RemovableThumb(onRemove = { removed += a }) { Thumb(file = app.repository.attachmentFile(a)) }
                    }
                    newImages.forEach { u -> RemovableThumb(onRemove = { newImages.remove(u) }) { Thumb(uri = u) } }
                    Box(
                        Modifier.size(84.dp).background(Fin.colors.cardElevated, RoundedCornerShape(14.dp))
                            .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.AddAPhoto, "Tambah foto", tint = Fin.colors.muted) }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            Button(
                onClick = {
                    val w = walletId
                    if (w == null) { error = "Tambahkan/pilih wallet terlebih dahulu"; return@Button }
                    saving = true
                    scope.launch {
                        try {
                            val entity = TransactionEntity(
                                id = if (duplicate) 0 else existing?.id ?: 0,
                                type = type,
                                amount = amount.toAmount(),
                                fee = if (type in setOf(TxType.TRANSFER, TxType.SAVINGS_ALLOCATION, TxType.CC_SETTLEMENT, TxType.DEBT_PAYMENT)) fee.toAmount() else 0,
                                walletId = w,
                                toWalletId = if (type == TxType.TRANSFER || type == TxType.CC_SETTLEMENT || type == TxType.SAVINGS_ALLOCATION) toWalletId else null,
                                categoryId = when (type) {
                                    TxType.EXPENSE, TxType.INCOME -> categoryId
                                    TxType.SAVINGS_ALLOCATION -> s.categories.firstOrNull { it.systemKey == "savings" }?.id
                                    TxType.DEBT_PAYMENT -> s.categories.firstOrNull { it.systemKey == "debt" }?.id
                                    TxType.CC_SETTLEMENT -> s.categories.firstOrNull { it.systemKey == "cc" }?.id
                                    else -> null
                                },
                                goalId = if (type == TxType.SAVINGS_ALLOCATION || type == TxType.TRANSFER) goalId else null,
                                debtId = if (type in setOf(TxType.DEBT_PAYMENT, TxType.DEBT_COLLECTION, TxType.LOAN_GIVEN, TxType.LOAN_RECEIVED)) debtId else null,
                                vehicleId = existing?.vehicleId,
                                recurringId = if (duplicate) null else existing?.recurringId,
                                dateTime = Fmt.toMillis(date.atTime(time)),
                                description = description.trim(),
                                note = note.trim(),
                                tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(","),
                                location = location.trim(),
                                createdAt = if (duplicate) System.currentTimeMillis() else existing?.createdAt ?: System.currentTimeMillis(),
                            )
                            app.repository.saveTransaction(entity, newImages.toList(), removed.toList())
                            ui.celebrate(s.celebrationFor(entity, isNew = entity.id == 0L))
                            nav.popBackStack()
                        } catch (e: Repository.ValidationException) {
                            error = e.message; saving = false
                        }
                    }
                },
                enabled = !saving && !isFuel,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Simpan") }

            if (s.activeWallets.isEmpty()) OutlinedButton(onClick = { nav.navigate(Routes.walletForm()) }, modifier = Modifier.fillMaxWidth()) { Text("Tambah wallet dulu") }
        }
    }
}

@Composable
private fun RemovableThumb(onRemove: () -> Unit, content: @Composable () -> Unit) {
    Box {
        content()
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, "Hapus", tint = Color.White, modifier = Modifier.size(14.dp)) }
    }
}
