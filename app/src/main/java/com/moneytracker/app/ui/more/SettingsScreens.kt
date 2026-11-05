package com.moneytracker.app.ui.more

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.moneytracker.app.LocalActivity
import com.moneytracker.app.LocalUi
import com.moneytracker.app.data.CategoryEntity
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.ui.Routes
import com.moneytracker.app.ui.components.BasisNote
import com.moneytracker.app.ui.components.ConfirmDialog
import com.moneytracker.app.ui.components.EmojiIcon
import com.moneytracker.app.ui.components.FinCard
import com.moneytracker.app.ui.components.FinTextField
import com.moneytracker.app.ui.components.FinTopBar
import com.moneytracker.app.ui.components.ListDivider
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.components.Option
import com.moneytracker.app.ui.components.SegmentedTabs
import com.moneytracker.app.ui.components.SelectField
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.core.CategoryKind
import com.moneytracker.core.Nature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ===================== Pengaturan =====================
@Composable
fun SettingsScreen(s: Snapshot, nav: NavHostController) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val activity = LocalActivity.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(s.user?.name ?: "") }
    var bio by remember { mutableStateOf(app.security.biometricEnabled) }
    var secure by remember { mutableStateOf(app.security.secureScreen) }
    var timeout by remember { mutableIntStateOf(app.security.lockTimeoutSec) }
    var changePin by remember { mutableStateOf(false) }
    var deleteAccount by remember { mutableStateOf(0) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(ctx, if (granted) "Notifikasi diaktifkan" else "Izin notifikasi ditolak", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize()) {
        FinTopBar("Pengaturan", onBack = { nav.popBackStack() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FinCard {
                Text("Profil", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FinTextField(name, { name = it }, "Nama")
                TextButton(onClick = { scope.launch { app.repository.setUserName(name) } }) { Text("Simpan nama") }
            }
            FinCard {
                Text("Tampilan", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val themes = listOf("dark", "light", "system")
                SegmentedTabs(listOf("Gelap", "Terang", "Sistem"), themes.indexOf(ui.theme).coerceAtLeast(0), { ui.theme = themes[it]; app.security.theme = themes[it] })
                var hz by remember { mutableStateOf(app.security.highRefreshRate) }
                SwitchRow("Refresh rate maksimal (${activity.maxRefreshRate()} Hz)", "Animasi & scroll lebih mulus; sedikit lebih boros baterai", hz) {
                    hz = it; app.security.highRefreshRate = it; activity.applyRefreshRate()
                }
                var intro by remember { mutableStateOf(app.security.introEnabled) }
                SwitchRow("Animasi pembuka", "Logo & nama pembuat saat aplikasi dibuka", intro) { intro = it; app.security.introEnabled = it }
                SwitchRow("Animasi transaksi", "Efek saat menyimpan transaksi & saldo berjalan", ui.motion) { ui.motion = it; app.security.motionEnabled = it }
                SwitchRow("Sembunyikan nominal saldo", "Tampilkan Rp••••• di layar", ui.hideBalances) { ui.hideBalances = it; app.security.hideBalances = it }
            }
            FinCard {
                Text("Keamanan", style = MaterialTheme.typography.titleMedium)
                SwitchRow("Kunci biometrik", if (activity.canUseBiometric()) "Sidik jari / wajah" else "Tidak tersedia di perangkat ini", bio, enabled = activity.canUseBiometric()) {
                    bio = it; app.security.biometricEnabled = it
                }
                SwitchRow("Lindungi layar", "Blokir screenshot & sembunyikan di Recent apps", secure) {
                    secure = it; app.security.secureScreen = it; activity.applySecureFlag()
                }
                Spacer(Modifier.height(6.dp))
                SelectField("Kunci otomatis setelah di background", listOf(0 to "Segera", 30 to "30 detik", 60 to "1 menit", 300 to "5 menit").map { Option(it.first, it.second) }, timeout) {
                    timeout = it; app.security.lockTimeoutSec = it
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { changePin = true }, modifier = Modifier.fillMaxWidth()) { Text("Ubah PIN") }
                OutlinedButton(onClick = { ui.locked = true }, modifier = Modifier.fillMaxWidth()) { Text("Kunci sekarang") }
                BasisNote("PIN disimpan sebagai hash PBKDF2 + salt. Database dienkripsi (SQLCipher) dengan kunci yang dilindungi Android Keystore. MoneyTracker tidak pernah menyimpan PIN/password internet banking atau kredensial bank.")
            }
            ReminderSettingsCard()
            FinCard {
                Text("Notifikasi lain", style = MaterialTheme.typography.titleMedium)
                Text("Pengingat jatuh tempo tagihan, langganan, cicilan, dan peringatan anggaran berjalan otomatis.", style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted)
                if (Build.VERSION.SDK_INT >= 33) TextButton(onClick = { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Izinkan notifikasi") }
            }
            FinCard {
                Text("Data", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { nav.navigate(Routes.CATEGORIES) }) { Text("Kelola kategori") }
                TextButton(onClick = { nav.navigate(Routes.BACKUP) }) { Text("Backup, restore & ekspor seluruh data") }
                TextButton(onClick = { nav.navigate(Routes.AUDIT) }) { Text("Riwayat perubahan") }
            }
            FinCard(color = Fin.colors.expense.copy(alpha = 0.08f)) {
                Text("Hapus akun & semua data", style = MaterialTheme.typography.titleMedium, color = Fin.colors.expense)
                Text("Menghapus seluruh transaksi, wallet, lampiran, PIN, dan kunci enkripsi dari perangkat ini. Tidak dapat dibatalkan — buat backup terlebih dahulu.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { deleteAccount = 1 }) { Text("Hapus akun", color = Fin.colors.expense) }
            }
            Text("MoneyTracker 1.0 · data tersimpan lokal di perangkat", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted, modifier = Modifier.fillMaxWidth().padding(8.dp))
        }
    }

    if (changePin) {
        var old by remember { mutableStateOf("") }
        var new1 by remember { mutableStateOf("") }
        var new2 by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { changePin = false },
            title = { Text("Ubah PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PinInput(old, { old = it }, "PIN lama")
                    PinInput(new1, { new1 = it }, "PIN baru (6 digit)")
                    PinInput(new2, { new2 = it }, "Ulangi PIN baru")
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) { app.security.verifyPin(old) }
                        err = when {
                            !ok -> "PIN lama salah"
                            new1.length != 6 -> "PIN baru harus 6 digit"
                            new1 != new2 -> "PIN baru tidak sama"
                            else -> null
                        }
                        if (err == null) {
                            withContext(Dispatchers.Default) { app.security.setPin(new1) }
                            Toast.makeText(ctx, "PIN diperbarui", Toast.LENGTH_SHORT).show()
                            changePin = false
                        }
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { changePin = false }) { Text("Batal") } },
        )
    }
    if (deleteAccount == 1) ConfirmDialog(
        "Hapus akun?", "Semua data MoneyTracker di perangkat ini akan dihapus permanen.", confirm = "Lanjutkan", onDismiss = { if (deleteAccount == 1) deleteAccount = 0 },
    ) { deleteAccount = 2 }
    if (deleteAccount == 2) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { deleteAccount = 0 },
            title = { Text("Konfirmasi terakhir") },
            text = { Column { Text("Ketik HAPUS untuk menghapus semua data."); Spacer(Modifier.height(8.dp)); FinTextField(typed, { typed = it }, "Konfirmasi") } },
            confirmButton = {
                TextButton(enabled = typed.trim().uppercase() == "HAPUS", onClick = {
                    scope.launch {
                        app.repository.wipeEverything()
                        com.moneytracker.app.work.Reminders.clear(ctx)
                        app.security.wipe()
                        activity.restartApp()
                    }
                }) { Text("Hapus semua", color = Fin.colors.expense) }
            },
            dismissButton = { TextButton(onClick = { deleteAccount = 0 }) { Text("Batal") } },
        )
    }
}

@Composable
private fun PinInput(value: String, onChange: (String) -> Unit, label: String) {
    androidx.compose.material3.OutlinedTextField(
        value, { v -> onChange(v.filter(Char::isDigit).take(6)) }, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
        }
        Switch(checked, onChange, enabled = enabled)
    }
}

// ===================== Backup =====================
@Composable
fun BackupScreen(s: Snapshot, onBack: () -> Unit) {
    val app = LocalApp.current
    val activity = LocalActivity.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = runCatching { app.backup.writeBackup(uri, password); "Backup terenkripsi tersimpan." }.getOrElse { "Gagal: ${it.message}" }
            busy = false
        }
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = runCatching { app.backup.writeBackup(uri, null); "Seluruh data diekspor (JSON, TIDAK terenkripsi)." }.getOrElse { "Gagal: ${it.message}" }
            busy = false
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }

    Column(Modifier.fillMaxSize()) {
        FinTopBar("Backup & Restore", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FinCard {
                Text("Backup terenkripsi", style = MaterialTheme.typography.titleMedium)
                Text("${s.transactions.size} transaksi · ${s.wallets.size} wallet · ${s.fuelLogs.size} log BBM · ${s.attachments.size} lampiran (foto tidak ikut backup)", style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted)
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    password, { password = it }, label = { Text("Password backup (min. 8 karakter)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                BasisNote("Simpan password ini baik-baik. Tanpa password, file backup tidak dapat dibuka oleh siapa pun — termasuk kami. Anda bisa menyimpan file ke Google Drive atau penyimpanan lain.")
                Button(onClick = { backupLauncher.launch("moneytracker-backup-${java.time.LocalDate.now()}.mtbak") }, enabled = password.length >= 8 && !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Buat backup") }
            }
            FinCard {
                Text("Restore", style = MaterialTheme.typography.titleMedium)
                Text("Mengganti SELURUH data saat ini dengan isi file backup.", style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted)
                OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Pilih file backup") }
            }
            FinCard {
                Text("Ekspor seluruh data", style = MaterialTheme.typography.titleMedium)
                Text("File JSON terbaca (tidak terenkripsi) berisi semua tabel. Simpan di tempat aman.", style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted)
                OutlinedButton(onClick = { exportJson.launch("moneytracker-data-${java.time.LocalDate.now()}.json") }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Ekspor JSON") }
            }
            message?.let { FinCard(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) { Text(it) } }
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Data saat ini akan diganti seluruhnya. Masukkan password bila file terenkripsi.")
                    androidx.compose.material3.OutlinedTextField(restorePassword, { restorePassword = it }, label = { Text("Password backup") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        busy = true
                        try {
                            val n = app.backup.restore(uri, restorePassword)
                            Toast.makeText(ctx, "Restore berhasil ($n baris). Aplikasi dimuat ulang.", Toast.LENGTH_LONG).show()
                            activity.restartApp()
                        } catch (e: Exception) {
                            message = "Restore gagal: ${e.message}"
                        }
                        busy = false
                    }
                }) { Text("Restore", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Batal") } },
        )
    }
}

// ===================== Kategori =====================
@Composable
fun CategoriesScreen(s: Snapshot, onBack: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var creatingParent by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }
    val kind = if (tab == 0) CategoryKind.EXPENSE else CategoryKind.INCOME

    Column(Modifier.fillMaxSize()) {
        FinTopBar("Kategori", onBack = onBack) { IconButton(onClick = { creatingParent = null; creating = true }) { Icon(Icons.Filled.Add, "Tambah kategori") } }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SegmentedTabs(listOf("Pengeluaran", "Pemasukan"), tab, { tab = it }) }
            items(s.roots(kind), key = { it.id }) { root ->
                FinCard(padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    CategoryRow(root, s.transactions.count { it.categoryId == root.id }, onClick = { editing = root })
                    s.children(root.id).forEach { c ->
                        ListDivider()
                        Row(Modifier.padding(start = 24.dp)) { CategoryRow(c, s.transactions.count { it.categoryId == c.id }, onClick = { editing = c }) }
                    }
                    TextButton(onClick = { creatingParent = root.id; creating = true }) { Text("+ Subkategori") }
                }
            }
        }
    }

    if (creating || editing != null) {
        val e = editing
        var name by remember { mutableStateOf(e?.name ?: "") }
        var icon by remember { mutableStateOf(e?.icon ?: "📁") }
        var nature by remember { mutableStateOf(e?.nature ?: Nature.NONE) }
        val parentId = e?.parentId ?: creatingParent
        val close = { creating = false; editing = null }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(if (e == null) (if (parentId != null) "Subkategori baru" else "Kategori baru") else "Edit kategori") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    parentId?.let { Text("Induk: ${s.categoryMap[it]?.name}", style = MaterialTheme.typography.labelMedium, color = Fin.colors.muted) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FinTextField(icon, { icon = it.take(2) }, "Ikon", Modifier.width(80.dp))
                        FinTextField(name, { name = it }, "Nama", Modifier.weight(1f))
                    }
                    if (kind == CategoryKind.EXPENSE) SegmentedTabs(Nature.entries.map { it.label }, nature.ordinal, { nature = Nature.entries[it] })
                    if (e != null) TextButton(onClick = { deleting = e; close() }) { Text("Hapus kategori", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch {
                        val parent = parentId?.let { s.categoryMap[it] }
                        app.repository.saveCategory(
                            (e ?: CategoryEntity(name = "", kind = kind, icon = icon, color = parent?.color ?: 0xFF7C5CFF, parentId = parentId, sortOrder = (s.categories.maxOfOrNull { it.sortOrder } ?: 0) + 1))
                                .copy(name = name.trim(), icon = icon.ifBlank { "📁" }, nature = nature),
                        )
                    }
                    close()
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = close) { Text("Batal") } },
        )
    }
    deleting?.let { c ->
        val subCount = s.children(c.id).size
        val txCount = s.transactions.count { t -> t.categoryId == c.id || s.children(c.id).any { it.id == t.categoryId } }
        ConfirmDialog(
            "Hapus ${c.name}?",
            (if (subCount > 0) "$subCount subkategori ikut terhapus. " else "") +
                if (txCount > 0) "$txCount transaksi akan dipindah ke ${c.parentId?.let { s.categoryMap[it]?.name } ?: "Tanpa kategori"}." else "",
            onDismiss = { deleting = null },
        ) { scope.launch { app.repository.deleteCategory(c, c.parentId) } }
    }
}

@Composable
private fun CategoryRow(c: CategoryEntity, txCount: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        EmojiIcon(c.icon, Color(c.color), 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = if (c.parentId == null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(c.nature.takeIf { it != Nature.NONE }?.label, c.preferredType?.let { "dicatat sbg ${it.label}" }, "$txCount transaksi").joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted,
            )
        }
    }
}
