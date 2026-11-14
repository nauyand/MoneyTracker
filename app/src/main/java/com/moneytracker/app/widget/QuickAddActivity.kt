package com.moneytracker.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moneytracker.app.MainActivity
import com.moneytracker.app.MoneyTrackerApp
import com.moneytracker.app.data.CategoryEntity
import com.moneytracker.app.data.Repository
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.data.TransactionEntity
import com.moneytracker.app.ui.components.FinTextField
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.components.LocalHideBalance
import com.moneytracker.app.ui.components.MoneyField
import com.moneytracker.app.ui.components.Option
import com.moneytracker.app.ui.components.OptionSheet
import com.moneytracker.app.ui.components.SegmentedTabs
import com.moneytracker.app.ui.components.SelectField
import com.moneytracker.app.ui.components.WalletBadge
import com.moneytracker.app.ui.components.toAmount
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.app.ui.theme.MoneyTrackerTheme
import com.moneytracker.app.ui.transactions.categoryOptions
import com.moneytracker.app.work.Reminders
import com.moneytracker.core.CategoryKind
import com.moneytracker.core.Fmt
import com.moneytracker.core.TxType
import kotlinx.coroutines.launch

/**
 * Lembar "Catat cepat" yang dibuka dari widget layar utama. Tampil di atas wallpaper (jendela transparan),
 * tanpa membuka aplikasi penuh dan tanpa layar PIN.
 *
 * Karena bisa dibuka tanpa PIN, lembar ini sengaja TIDAK menampilkan saldo atau riwayat apa pun —
 * hanya bisa menambah transaksi pemasukan/pengeluaran baru.
 */
class QuickAddActivity : ComponentActivity() {
    private val app get() = application as MoneyTrackerApp
    private val type = mutableStateOf(TxType.EXPENSE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (app.security.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (savedInstanceState == null) type.value = typeFrom(intent)

        setContent {
            CompositionLocalProvider(LocalApp provides app, LocalHideBalance provides true) {
                MoneyTrackerTheme(app.security.theme) {
                    QuickAddSheet(
                        type = type.value,
                        onTypeChange = { type.value = it },
                        onClose = ::finish,
                        onSaved = { msg ->
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                            finish()
                        },
                        onOpenApp = ::openFullApp,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        type.value = typeFrom(intent)
    }

    private fun openFullApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(Reminders.EXTRA_OPEN, if (app.security.hasPin) Reminders.OPEN_NEW_TX else null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    companion object {
        private const val EXTRA_TYPE = "quick_add_type"

        private fun typeFrom(intent: Intent?): TxType =
            if (intent?.getStringExtra(EXTRA_TYPE) == TxType.INCOME.name) TxType.INCOME else TxType.EXPENSE

        fun pendingIntent(context: Context, type: TxType): PendingIntent {
            val intent = Intent(context, QuickAddActivity::class.java)
                .putExtra(EXTRA_TYPE, type.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // requestCode berbeda per jenis agar extra-nya tidak saling menimpa.
            return PendingIntent.getActivity(
                context, 100 + type.ordinal, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

@Composable
private fun QuickAddSheet(
    type: TxType,
    onTypeChange: (TxType) -> Unit,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    onOpenApp: () -> Unit,
) {
    WidgetSheet("Catat cepat", onClose) {
        WidgetGate(onOpenApp) { s -> QuickAddForm(s, type, onTypeChange, onSaved, onOpenApp) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAddForm(
    s: Snapshot,
    type: TxType,
    onTypeChange: (TxType) -> Unit,
    onSaved: (String) -> Unit,
    onOpenApp: () -> Unit,
) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val isIncome = type == TxType.INCOME
    val kind = if (isIncome) CategoryKind.INCOME else CategoryKind.EXPENSE
    val accent = if (isIncome) Fin.colors.income else Fin.colors.expense

    var amount by rememberSaveable { mutableStateOf("") }
    var walletId by rememberSaveable { mutableStateOf(s.primaryWallet?.id) }
    var categoryId by rememberSaveable(type) { mutableStateOf<Long?>(null) }
    var description by rememberSaveable { mutableStateOf("") }
    var pickCategory by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    SegmentedTabs(listOf("Pengeluaran", "Pemasukan"), if (isIncome) 1 else 0, { onTypeChange(if (it == 1) TxType.INCOME else TxType.EXPENSE) })

    MoneyField(amount, { amount = it; error = null }, "Nominal", Modifier.focusRequester(focus), large = true)

    // ---------- Kategori: yang paling sering dipakai + pilih lainnya ----------
    val quick = remember(s, type) { frequentCategories(s, type, kind) }
    val other = categoryId?.takeIf { id -> quick.none { it.id == id } }?.let { s.categoryMap[it] }
    Text("Kategori", style = MaterialTheme.typography.labelLarge, color = Fin.colors.muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        quick.forEach { c ->
            FilterChip(categoryId == c.id, { categoryId = if (categoryId == c.id) null else c.id }, { Text("${c.icon} ${c.name}") })
        }
        FilterChip(other != null, { pickCategory = true }, { Text(other?.let { "${it.icon} ${it.name}" } ?: "Lainnya…") })
    }
    if (pickCategory) {
        OptionSheet("Kategori", categoryOptions(s, kind, hidePreferred = true), categoryId, { pickCategory = false }) { categoryId = it }
    }

    // Saldo sengaja tidak ditampilkan (lembar ini bisa dibuka tanpa PIN).
    val walletOptions = s.activeWallets.map { w -> Option<Long?>(w.id, w.name, w.type.label, leading = { WalletBadge(w, 32.dp) }) }
    SelectField(if (isIncome) "Masuk ke wallet" else "Dari wallet", walletOptions, walletId) { walletId = it }

    FinTextField(description, { description = it }, "Deskripsi (opsional)")

    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

    Button(
        onClick = {
            val w = walletId
            if (w == null) { error = "Pilih wallet"; return@Button }
            val value = amount.toAmount()
            saving = true
            scope.launch {
                try {
                    app.repository.saveTransaction(
                        TransactionEntity(
                            type = type,
                            amount = value,
                            walletId = w,
                            categoryId = categoryId,
                            dateTime = System.currentTimeMillis(),
                            description = description.trim(),
                        ),
                    )
                    onSaved("${if (isIncome) "Pemasukan" else "Pengeluaran"} ${Fmt.rupiah(value)} tersimpan")
                } catch (e: Repository.ValidationException) {
                    error = e.message
                    saving = false
                }
            }
        },
        enabled = !saving,
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(if (isIncome) "Simpan pemasukan" else "Simpan pengeluaran") }

    TextButton(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) { Text("Isi lengkap di aplikasi") }
}

/**
 * Kategori yang paling sering dipakai 90 hari terakhir untuk jenis ini, dilengkapi kategori induk
 * agar selalu ada pilihan meski belum ada riwayat. Maksimal 8.
 */
private fun frequentCategories(s: Snapshot, type: TxType, kind: CategoryKind): List<CategoryEntity> {
    val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
    val used = s.transactions.asSequence()
        .filter { it.type == type && it.categoryId != null && it.dateTime >= cutoff }
        .groupingBy { it.categoryId!! }.eachCount()
        .entries.sortedByDescending { it.value }
        .mapNotNull { s.categoryMap[it.key] }
        .filter { !it.archived && it.kind == kind && it.preferredType == null }
    val roots = s.roots(kind).filter { it.preferredType == null }
    return (used + roots).distinctBy { it.id }.take(8)
}
