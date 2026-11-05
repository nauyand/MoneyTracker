package com.moneytracker.app.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.moneytracker.core.Csv
import com.moneytracker.core.Fmt
import com.moneytracker.core.Ledger
import com.moneytracker.core.TxType
import com.moneytracker.core.Xlsx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth

class ExportManager(private val context: Context) {

    private val header = listOf("Tanggal", "Jam", "Jenis", "Kategori", "Subkategori", "Wallet", "Wallet Tujuan", "Nominal", "Biaya", "Efek Bersih", "Deskripsi", "Catatan", "Tag", "Lokasi")

    private fun rows(s: Snapshot, txs: List<TransactionEntity>): List<List<Any>> = txs.map { t ->
        val cat = t.categoryId?.let { s.categoryMap[it] }
        val parent = cat?.parentId?.let { s.categoryMap[it] }
        val net = Ledger.incomeOf(s.core(t)) - Ledger.expenseOf(s.core(t))
        listOf(
            Fmt.toDate(t.dateTime).toString(), Fmt.time(t.dateTime), t.type.label,
            parent?.name ?: cat?.name.orEmpty(), if (parent != null) cat?.name.orEmpty() else "",
            s.walletMap[t.walletId]?.name.orEmpty(), t.toWalletId?.let { s.walletMap[it]?.name }.orEmpty(),
            t.amount, t.fee, net, t.description, t.note, t.tags, t.location,
        )
    }

    suspend fun exportCsv(uri: Uri, s: Snapshot, txs: List<TransactionEntity>) = withContext(Dispatchers.IO) {
        val text = Csv.write(listOf(header) + rows(s, txs).map { r -> r.map { it.toString() } })
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun exportXlsx(uri: Uri, s: Snapshot, txs: List<TransactionEntity>) = withContext(Dispatchers.IO) {
        fun cell(v: Any): Xlsx.Cell = when (v) { is Long -> Xlsx.Num(v.toDouble()); is Int -> Xlsx.Num(v.toDouble()); else -> Xlsx.Text(v.toString()) }
        val txSheet = listOf(header.map { Xlsx.Text(it) }) + rows(s, txs).map { r -> r.map(::cell) }
        val walletSheet = listOf(listOf("Wallet", "Jenis", "Saldo Awal", "Saldo Saat Ini").map { Xlsx.Text(it) }) +
            s.wallets.map { w -> listOf(Xlsx.Text(w.name), Xlsx.Text(w.type.label), Xlsx.Num(w.initialBalance.toDouble()), Xlsx.Num(s.balance(w.id).toDouble())) }
        context.contentResolver.openOutputStream(uri, "wt")?.use { Xlsx.write(listOf("Transaksi" to txSheet, "Wallet" to walletSheet), it) }
    }

    /** Laporan bulanan PDF: ringkasan, pengeluaran per kategori, daftar transaksi. */
    suspend fun exportMonthlyPdf(uri: Uri, s: Snapshot, month: YearMonth) = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val W = 595; val H = 842; val M = 40f
        val title = Paint().apply { color = Color.rgb(20, 24, 40); textSize = 20f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val h2 = Paint().apply { color = Color.rgb(90, 70, 220); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val body = Paint().apply { color = Color.rgb(40, 44, 60); textSize = 9.5f; isAntiAlias = true }
        val muted = Paint().apply { color = Color.rgb(120, 125, 140); textSize = 8.5f; isAntiAlias = true }
        val bar = Paint().apply { color = Color.rgb(124, 92, 255) }
        val right = Paint(body).apply { textAlign = Paint.Align.RIGHT }

        var pageNo = 0
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create())
        var c = page.canvas
        var y = M + 10

        fun newPageIfNeeded(space: Float) {
            if (y + space > H - M) {
                doc.finishPage(page)
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create())
                c = page.canvas; y = M
            }
        }

        val (from, to) = Ledger.monthRange(month)
        val txs = s.transactions.filter { it.dateTime in from until to }.sortedBy { it.dateTime }
        val sum = Ledger.summarize(s.txCore, month)

        c.drawText("Laporan Keuangan — ${Fmt.month(month)}", M, y, title); y += 18
        c.drawText("${s.user?.name ?: "MoneyTracker"} · dibuat ${Fmt.dateTime(System.currentTimeMillis())}", M, y, muted); y += 26

        c.drawText("Ringkasan", M, y, h2); y += 18
        val lines = listOf(
            "Pemasukan" to Fmt.rupiah(sum.income), "Pengeluaran" to Fmt.rupiah(sum.expense),
            "Arus kas bersih" to Fmt.rupiah(sum.net), "Dialokasikan ke tabungan" to Fmt.rupiah(sum.savings),
            "Rasio tabungan" to (sum.savingsRate?.let { Fmt.percent(it) } ?: "-"),
            "Total saldo saat ini" to Fmt.rupiah(s.totalBalance), "Net worth saat ini" to Fmt.rupiah(s.netWorth.netWorth),
        )
        for ((k, v) in lines) { c.drawText(k, M, y, body); c.drawText(v, W - M, y, right); y += 14 }
        y += 12

        c.drawText("Pengeluaran per kategori", M, y, h2); y += 16
        val byCat = Ledger.expenseByRootCategory(s.txCore.filter { it.dateTime in from until to }, s.catInfo).entries.sortedByDescending { it.value }
        val max = byCat.maxOfOrNull { it.value } ?: 1
        for ((cat, amt) in byCat) {
            newPageIfNeeded(16f)
            val name = cat?.let { s.categoryMap[it]?.name } ?: "Tanpa kategori"
            c.drawText(name, M, y, body)
            c.drawRect(M + 150, y - 8, M + 150 + 230f * amt / max, y, bar)
            c.drawText(Fmt.rupiah(amt), W - M, y, right)
            y += 15
        }
        y += 14

        newPageIfNeeded(40f)
        c.drawText("Transaksi (${txs.size})", M, y, h2); y += 16
        for (t in txs) {
            newPageIfNeeded(14f)
            c.drawText(Fmt.dateShort(t.dateTime), M, y, muted)
            val label = (t.description.ifBlank { s.categoryLabel(t.categoryId) }).take(42)
            c.drawText(label, M + 70, y, body)
            c.drawText(s.walletMap[t.walletId]?.name.orEmpty().take(16), M + 300, y, muted)
            val sign = when (t.type) { TxType.INCOME, TxType.DEBT_COLLECTION, TxType.LOAN_RECEIVED -> "+"; TxType.EXPENSE -> "-"; else -> "" }
            c.drawText(sign + Fmt.rupiah(kotlin.math.abs(t.amount)), W - M, y, right)
            y += 13
        }
        doc.finishPage(page)
        context.contentResolver.openOutputStream(uri, "wt")?.use { doc.writeTo(it) }
        doc.close()
    }
}
