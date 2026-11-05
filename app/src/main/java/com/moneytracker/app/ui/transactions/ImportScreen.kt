package com.moneytracker.app.ui.transactions

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneytracker.app.data.Repository
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.ui.components.BasisNote
import com.moneytracker.app.ui.components.FinCard
import com.moneytracker.app.ui.components.FinTopBar
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.components.Option
import com.moneytracker.app.ui.components.SegmentedTabs
import com.moneytracker.app.ui.components.SelectField
import com.moneytracker.app.ui.components.WalletBadge
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.core.CategoryKind
import com.moneytracker.core.Csv
import com.moneytracker.core.Fmt
import com.moneytracker.core.ImportMapping
import com.moneytracker.core.Importer
import com.moneytracker.core.Xlsx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Impor mutasi dari file CSV/Excel hasil unduhan internet banking / e-wallet.
 * Tidak ada koneksi langsung ke bank — pengguna mengunduh sendiri file mutasinya.
 */
@Composable
fun ImportScreen(s: Snapshot, onBack: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var table by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var fileError by remember { mutableStateOf<String?>(null) }
    var hasHeader by remember { mutableStateOf(true) }
    var dateCol by remember { mutableStateOf<Int?>(null) }
    var descCol by remember { mutableStateOf<Int?>(null) }
    var mode by remember { mutableStateOf(0) } // 0 = satu kolom bertanda, 1 = debit/kredit
    var amountCol by remember { mutableStateOf<Int?>(null) }
    var debitCol by remember { mutableStateOf<Int?>(null) }
    var creditCol by remember { mutableStateOf<Int?>(null) }
    var positiveIsExpense by remember { mutableStateOf(false) }
    var walletId by remember { mutableStateOf(s.primaryWallet?.id) }
    var incomeCat by remember { mutableStateOf<Long?>(null) }
    var expenseCat by remember { mutableStateOf<Long?>(null) }
    var candidates by remember { mutableStateOf<List<Repository.ImportCandidate>>(emptyList()) }
    val selected = remember { mutableStateListOf<Int>() }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            fileError = null
            table = withContext(Dispatchers.IO) {
                runCatching {
                    val mime = ctx.contentResolver.getType(uri).orEmpty()
                    val name = uri.lastPathSegment.orEmpty().lowercase()
                    ctx.contentResolver.openInputStream(uri)!!.use { input ->
                        if (mime.contains("spreadsheetml") || name.endsWith(".xlsx")) Xlsx.readFirstSheet(input)
                        else Csv.parse(input.readBytes().toString(Charsets.UTF_8))
                    }
                }.getOrElse { fileError = "Gagal membaca file: ${it.message}"; emptyList() }
            }
            // Tebak kolom dari header
            val header = table.firstOrNull().orEmpty().map { it.lowercase() }
            dateCol = header.indexOfFirst { it.contains("tanggal") || it.contains("date") || it.contains("tgl") }.takeIf { it >= 0 } ?: 0
            descCol = header.indexOfFirst { it.contains("keterangan") || it.contains("deskripsi") || it.contains("description") || it.contains("uraian") || it.contains("remark") }.takeIf { it >= 0 }
            debitCol = header.indexOfFirst { it.contains("debit") || it.contains("keluar") || it == "db" }.takeIf { it >= 0 }
            creditCol = header.indexOfFirst { it.contains("kredit") || it.contains("credit") || it.contains("masuk") || it == "cr" }.takeIf { it >= 0 }
            amountCol = header.indexOfFirst { it.contains("nominal") || it.contains("jumlah") || it.contains("amount") || it.contains("mutasi") }.takeIf { it >= 0 }
            mode = if (debitCol != null && creditCol != null) 1 else 0
            candidates = emptyList()
        }
    }

    val columnOptions = remember(table) {
        val header = table.firstOrNull().orEmpty()
        header.indices.map { i -> Option<Int?>(i, "Kolom ${Xlsx.colName(i)}" + (if (hasHeader) " · ${header[i]}" else ""), table.getOrNull(1)?.getOrNull(i)?.take(30)) }
    }
    val optional = listOf(Option<Int?>(null, "Tidak ada")) + columnOptions

    LaunchedEffect(walletId) { candidates = emptyList() }

    Column(Modifier.fillMaxSize()) {
        FinTopBar("Impor CSV / Excel", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                FinCard {
                    Text("1. Pilih file mutasi", style = MaterialTheme.typography.titleMedium)
                    BasisNote("Unduh mutasi rekening dari internet/mobile banking atau e-wallet (CSV atau .xlsx). MoneyTracker tidak pernah meminta atau menyimpan user ID/password bank.")
                    OutlinedButton(onClick = {
                        picker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream"))
                    }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(if (table.isEmpty()) "Pilih file" else "Ganti file (${table.size} baris)") }
                    fileError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            if (table.isNotEmpty()) {
                item {
                    FinCard {
                        Text("2. Petakan kolom", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                            Text("Baris pertama adalah judul kolom", modifier = Modifier.weight(1f)); Switch(hasHeader, { hasHeader = it })
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SelectField("Tanggal", columnOptions, dateCol) { dateCol = it }
                            SelectField("Deskripsi", optional, descCol) { descCol = it }
                            SegmentedTabs(listOf("Satu kolom nominal", "Debit & Kredit"), mode, { mode = it })
                            if (mode == 0) {
                                SelectField("Nominal (negatif = keluar)", columnOptions, amountCol) { amountCol = it }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Nominal positif = pengeluaran", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Switch(positiveIsExpense, { positiveIsExpense = it })
                                }
                            } else {
                                SelectField("Debit (uang keluar)", optional, debitCol) { debitCol = it }
                                SelectField("Kredit (uang masuk)", optional, creditCol) { creditCol = it }
                            }
                            SelectField("Masukkan ke wallet", s.activeWallets.map { w -> Option<Long?>(w.id, w.name, leading = { WalletBadge(w, 30.dp) }) }, walletId) { walletId = it }
                            SelectField("Kategori default pemasukan", listOf(Option<Long?>(null, "Tanpa kategori")) + categoryOptions(s, CategoryKind.INCOME), incomeCat) { incomeCat = it }
                            SelectField("Kategori default pengeluaran", listOf(Option<Long?>(null, "Tanpa kategori")) + categoryOptions(s, CategoryKind.EXPENSE, hidePreferred = true), expenseCat) { expenseCat = it }
                        }
                        Button(
                            onClick = {
                                val w = walletId ?: return@Button
                                val d = dateCol ?: return@Button
                                val res = Importer.apply(table, ImportMapping(d, descCol, if (mode == 0) amountCol else null, if (mode == 1) debitCol else null, if (mode == 1) creditCol else null, hasHeader, positiveIsExpense))
                                errors = res.errors
                                scope.launch {
                                    candidates = app.repository.prepareImport(w, res.rows)
                                    selected.clear()
                                    candidates.forEachIndexed { i, c -> if (!c.exactDuplicate && !c.possibleDuplicate) selected += i }
                                }
                            },
                            enabled = walletId != null && dateCol != null && (if (mode == 0) amountCol != null else debitCol != null || creditCol != null),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) { Text("Pratinjau") }
                    }
                }
            }
            if (candidates.isNotEmpty() || errors.isNotEmpty()) {
                item {
                    FinCard {
                        Text("3. Pratinjau (${candidates.size} baris valid)", style = MaterialTheme.typography.titleMedium)
                        val dup = candidates.count { it.exactDuplicate || it.possibleDuplicate }
                        if (dup > 0) BasisNote("$dup baris terdeteksi mungkin duplikat (tanggal & nominal sama dengan transaksi yang sudah ada) dan tidak dicentang.")
                        if (errors.isNotEmpty()) BasisNote("${errors.size} baris dilewati: " + errors.take(3).joinToString("; ") + if (errors.size > 3) "…" else "")
                        Button(
                            onClick = {
                                val w = walletId ?: return@Button
                                scope.launch {
                                    val n = app.repository.importRows(w, selected.sorted().map { candidates[it].row }, incomeCat, expenseCat)
                                    Toast.makeText(ctx, "$n transaksi diimpor", Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                            },
                            enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 8.dp),
                        ) { Text("Impor ${selected.size} transaksi") }
                    }
                }
                itemsIndexed(candidates) { i, c ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(i in selected, { if (it) selected += i else selected -= i })
                        Column(Modifier.weight(1f)) {
                            Text(c.row.description.ifBlank { "(tanpa deskripsi)" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                Fmt.dateShort(c.row.dateTime.toLocalDate()) + when { c.exactDuplicate -> " · sudah pernah diimpor"; c.possibleDuplicate -> " · mungkin duplikat"; else -> "" },
                                style = MaterialTheme.typography.labelSmall, color = if (c.exactDuplicate || c.possibleDuplicate) Fin.colors.warning else Fin.colors.muted,
                            )
                        }
                        Text(Fmt.rupiah(c.row.amount, withSign = true), color = if (c.row.amount > 0) Fin.colors.income else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
