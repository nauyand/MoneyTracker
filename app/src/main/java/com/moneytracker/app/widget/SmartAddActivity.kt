package com.moneytracker.app.widget

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneytracker.app.MainActivity
import com.moneytracker.app.MoneyTrackerApp
import com.moneytracker.app.data.Repository
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.data.TransactionEntity
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.components.LocalHideBalance
import com.moneytracker.app.ui.components.MoneyField
import com.moneytracker.app.ui.components.Option
import com.moneytracker.app.ui.components.OptionSheet
import com.moneytracker.app.ui.components.WalletBadge
import com.moneytracker.app.ui.components.toAmount
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.app.ui.theme.MoneyTrackerTheme
import com.moneytracker.app.ui.transactions.categoryOptions
import com.moneytracker.app.work.Reminders
import com.moneytracker.core.CategoryKind
import com.moneytracker.core.Fmt
import com.moneytracker.core.QuickParser
import com.moneytracker.core.TxType
import kotlinx.coroutines.launch

/**
 * Lembar "Ketik cepat" dari widget: pengguna mengetik/mengucapkan kalimat bebas
 * ("jajan 10rb", "gaji 5jt bca", banyak baris sekaligus), [QuickParser] menebak jenis, nominal,
 * kategori & wallet, lalu hasilnya ditampilkan untuk dikoreksi sebelum disimpan.
 *
 * Seperti "Catat cepat", lembar ini terbuka tanpa PIN sehingga tidak menampilkan saldo atau riwayat.
 */
class SmartAddActivity : ComponentActivity() {
    private val app get() = application as MoneyTrackerApp

    /** true selama layar input suara Google terbuka, agar lembar ini tidak ikut ditutup di onStop. */
    var awaitingVoice = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (app.security.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val startWithVoice = savedInstanceState == null && intent.getBooleanExtra(EXTRA_VOICE, false)

        setContent {
            CompositionLocalProvider(LocalApp provides app, LocalHideBalance provides true) {
                MoneyTrackerTheme(app.security.theme) {
                    WidgetSheet("Ketik cepat", ::finish) {
                        WidgetGate(::openFullApp) { s ->
                            SmartAddForm(
                                s = s,
                                startWithVoice = startWithVoice,
                                activity = this@SmartAddActivity,
                                onSaved = { msg ->
                                    Toast.makeText(this@SmartAddActivity, msg, Toast.LENGTH_SHORT).show()
                                    finish()
                                },
                                onOpenApp = ::openFullApp,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Lembar sementara: tutup bila pengguna pergi (tombol Home), kecuali sedang input suara.
        if (!isChangingConfigurations && !awaitingVoice) finish()
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
        private const val EXTRA_VOICE = "smart_add_voice"

        fun pendingIntent(context: Context, voice: Boolean): PendingIntent {
            val intent = Intent(context, SmartAddActivity::class.java)
                .putExtra(EXTRA_VOICE, voice)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                context, if (voice) 111 else 110, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

/** Data pengguna untuk [QuickParser]: wallet (urutan tampil), kategori, riwayat & tagihan rutin. */
private fun Snapshot.quickContext(): QuickParser.Context = QuickParser.Context(
    wallets = activeWallets.map { QuickParser.WalletRef(it.id, it.name, it.provider) },
    categories = categories.filter { !it.archived && it.preferredType == null }
        .map { QuickParser.CategoryRef(it.id, it.name, it.kind, it.parentId) },
    history = transactions.asSequence()
        .filter { (it.type == TxType.INCOME || it.type == TxType.EXPENSE) && it.description.isNotBlank() }
        .sortedByDescending { it.dateTime }.take(500)
        .map { QuickParser.HistoryItem(it.description, it.type == TxType.INCOME, it.categoryId) }.toList(),
    bills = recurring.filter { it.active }
        .map { QuickParser.KnownBill(it.name, it.amount, it.walletId, it.categoryId, it.kind.isIncome) },
    defaultWalletId = primaryWallet?.id,
)

/** Koreksi pengguna atas satu draf. */
private data class Edit(
    val income: Boolean? = null,
    val amount: Long? = null,
    val categorySet: Boolean = false,
    val categoryId: Long? = null,
    val walletId: Long? = null,
)

private class Final(val draft: QuickParser.Draft, val income: Boolean, val amount: Long?, val categoryId: Long?, val walletId: Long?)

private fun QuickParser.Draft.withEdit(e: Edit?): Final {
    val inc = e?.income ?: income
    val cat = when {
        e?.categorySet == true -> e.categoryId
        inc != income -> null // jenis diganti → kategori lama tidak cocok lagi
        else -> categoryId
    }
    return Final(this, inc, e?.amount ?: amount, cat, e?.walletId ?: walletId)
}

@Composable
private fun SmartAddForm(
    s: Snapshot,
    startWithVoice: Boolean,
    activity: SmartAddActivity,
    onSaved: (String) -> Unit,
    onOpenApp: () -> Unit,
) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val ctx = remember(s) { s.quickContext() }
    val drafts = remember(text, ctx) { QuickParser.parse(text, ctx) }
    // Koreksi direset setiap teks berubah (barisnya bisa bergeser).
    val edits = remember(text) { mutableStateMapOf<Int, Edit>() }
    val finals = drafts.mapIndexed { i, d -> d.withEdit(edits[i]) }

    val focus = remember { FocusRequester() }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        activity.awaitingVoice = false
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) text = if (text.isBlank()) heard else text.trimEnd() + "\n" + heard
    }
    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Contoh: jajan sepuluh ribu")
        try {
            activity.awaitingVoice = true
            voice.launch(intent)
        } catch (e: ActivityNotFoundException) {
            activity.awaitingVoice = false
            error = "Input suara tidak tersedia di HP ini. Silakan ketik."
        }
    }
    LaunchedEffect(Unit) { if (startWithVoice) listen() else runCatching { focus.requestFocus() } }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it; error = null },
        placeholder = { Text("jajan 10rb\ngaji 5jt bca\nbensin 50rb gopay", color = Fin.colors.muted) },
        trailingIcon = { IconButton(onClick = ::listen) { Icon(Icons.Filled.Mic, "Input suara", tint = MaterialTheme.colorScheme.primary) } },
        minLines = 2,
        maxLines = 8,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )

    if (drafts.isEmpty()) {
        Text(
            "Ketik seperti chat, satu transaksi per baris. Wallet bisa disebut (\"gopay\", \"rek 2\") " +
                "atau untuk semua: \"semua ke bca\".",
            style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted,
        )
    }

    var sheet by remember { mutableStateOf<Pair<Int, String>?>(null) }
    finals.forEachIndexed { i, f ->
        DraftCard(
            s, f,
            onToggleType = { edits[i] = (edits[i] ?: Edit()).copy(income = !f.income, categorySet = false, categoryId = null) },
            onAmount = { edits[i] = (edits[i] ?: Edit()).copy(amount = it) },
            onPickCategory = { sheet = i to "category" },
            onPickWallet = { sheet = i to "wallet" },
        )
    }

    sheet?.let { (i, what) ->
        val f = finals.getOrNull(i)
        if (f == null) { sheet = null; return@let }
        if (what == "category") {
            OptionSheet(
                "Kategori",
                listOf(Option<Long?>(null, "Tanpa kategori")) + categoryOptions(s, if (f.income) CategoryKind.INCOME else CategoryKind.EXPENSE, hidePreferred = true),
                f.categoryId, { sheet = null },
            ) { edits[i] = (edits[i] ?: Edit()).copy(categorySet = true, categoryId = it) }
        } else {
            // Saldo sengaja tidak ditampilkan (lembar ini bisa dibuka tanpa PIN).
            OptionSheet(
                "Wallet",
                s.activeWallets.mapIndexed { n, w -> Option<Long?>(w.id, w.name, "rek ${n + 1} · ${w.type.label}", leading = { WalletBadge(w, 32.dp) }) },
                f.walletId, { sheet = null },
            ) { id -> if (id != null) edits[i] = (edits[i] ?: Edit()).copy(walletId = id) }
        }
    }

    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

    val ready = finals.isNotEmpty() && finals.all { (it.amount ?: 0L) > 0L && it.walletId != null }
    Button(
        onClick = {
            saving = true
            scope.launch {
                try {
                    val entities = finals.map { f ->
                        TransactionEntity(
                            type = if (f.income) TxType.INCOME else TxType.EXPENSE,
                            amount = f.amount ?: 0L,
                            walletId = f.walletId!!,
                            categoryId = f.categoryId,
                            dateTime = System.currentTimeMillis(),
                            description = f.draft.description,
                        )
                    }
                    entities.forEach { app.repository.validate(it) }
                    entities.forEach { app.repository.saveTransaction(it) }
                    onSaved(
                        if (entities.size == 1) "${entities[0].type.label} ${Fmt.rupiah(entities[0].amount)} tersimpan"
                        else "${entities.size} transaksi tersimpan",
                    )
                } catch (e: Repository.ValidationException) {
                    error = e.message
                    saving = false
                }
            }
        },
        enabled = ready && !saving,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(
            when {
                finals.isEmpty() -> "Simpan"
                !ready -> "Lengkapi nominal dulu"
                finals.size == 1 -> "Simpan"
                else -> "Simpan semua (${finals.size})"
            },
        )
    }

    TextButton(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) { Text("Isi lengkap di aplikasi") }
}

@Composable
private fun DraftCard(
    s: Snapshot,
    f: Final,
    onToggleType: () -> Unit,
    onAmount: (Long?) -> Unit,
    onPickCategory: () -> Unit,
    onPickWallet: () -> Unit,
) {
    val accent = if (f.income) Fin.colors.income else Fin.colors.expense
    Column(
        Modifier.fillMaxWidth().background(Fin.colors.cardElevated, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Ketuk untuk menukar jenis.
            Text(
                if (f.income) "Pemasukan ⇄" else "Pengeluaran ⇄",
                color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp)).clickable(onClick = onToggleType)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text(
                f.draft.description.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (f.amount != null && QuickParser.Note.NO_AMOUNT !in f.draft.notes) {
                Text(
                    (if (f.income) "+" else "−") + Fmt.rupiah(f.amount),
                    color = accent, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                )
            }
        }

        if (QuickParser.Note.NO_AMOUNT in f.draft.notes) {
            MoneyField(f.amount?.toString() ?: "", { onAmount(it.toAmount().takeIf { v -> v > 0 }) }, "Nominal belum ada — isi di sini")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val cat = f.categoryId?.let { s.categoryMap[it] }
            AssistChip(onClick = onPickCategory, label = { Text(cat?.let { "${it.icon} ${it.name}" } ?: "Pilih kategori", maxLines = 1) })
            val w = f.walletId?.let { s.walletMap[it] }
            AssistChip(onClick = onPickWallet, label = { Text(w?.name ?: "Pilih wallet", maxLines = 1) })
        }

        val notes = buildList {
            if (QuickParser.Note.AMOUNT_ASSUMED_THOUSANDS in f.draft.notes) add("Angka kecil dibaca sebagai ribuan")
            if (QuickParser.Note.AMOUNT_FROM_BILL in f.draft.notes) add("Nominal diambil dari tagihan rutin")
            if (QuickParser.Note.TYPE_GUESSED in f.draft.notes) add("Jenis ditebak, ketuk label untuk mengganti")
        }
        if (notes.isNotEmpty()) {
            Text(notes.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Fin.colors.warning)
        }
    }
}
