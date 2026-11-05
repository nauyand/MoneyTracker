package com.moneytracker.app.ui.fx

import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.data.TransactionEntity
import com.moneytracker.app.data.toCore
import com.moneytracker.core.BudgetStatus
import com.moneytracker.core.Fmt
import com.moneytracker.core.Ledger
import com.moneytracker.core.TxType
import java.time.YearMonth

/**
 * Membuat data animasi untuk transaksi yang BARU SAJA disimpan.
 * [s] adalah snapshot SEBELUM penyimpanan, sehingga saldo/progres "sesudah" dihitung = sebelum + efek transaksi.
 */
fun Snapshot.celebrationFor(tx: TransactionEntity, isNew: Boolean): Celebration {
    val core = tx.toCore()
    val from = walletMap[tx.walletId]
    val to = tx.toWalletId?.let { walletMap[it] }
    val kind = when (tx.type) {
        TxType.INCOME, TxType.DEBT_COLLECTION, TxType.LOAN_RECEIVED -> CelebKind.INCOME
        TxType.EXPENSE -> if (tx.vehicleId != null && tx.tags.contains("bbm")) CelebKind.FUEL else CelebKind.EXPENSE
        TxType.TRANSFER, TxType.CC_SETTLEMENT -> CelebKind.TRANSFER
        TxType.SAVINGS_ALLOCATION -> CelebKind.SAVINGS
        TxType.DEBT_PAYMENT, TxType.LOAN_GIVEN -> CelebKind.DEBT
        TxType.ADJUSTMENT -> CelebKind.OTHER
    }
    val title = when (tx.type) {
        TxType.INCOME -> "Pemasukan tercatat"
        TxType.EXPENSE -> if (kind == CelebKind.FUEL) "Pengisian BBM tercatat" else "Pengeluaran tercatat"
        TxType.TRANSFER -> "Transfer berhasil dicatat"
        TxType.CC_SETTLEMENT -> "Tagihan kartu kredit dilunasi"
        TxType.SAVINGS_ALLOCATION -> "Tabungan bertambah"
        TxType.DEBT_PAYMENT -> "Pembayaran utang tercatat"
        TxType.DEBT_COLLECTION -> "Piutang diterima"
        TxType.LOAN_RECEIVED -> "Pinjaman diterima"
        TxType.LOAN_GIVEN -> "Pinjaman diberikan"
        TxType.ADJUSTMENT -> "Saldo disesuaikan"
    }.let { if (isNew) it else "Perubahan disimpan" }

    // Saldo setelah transaksi (hanya akurat untuk transaksi baru)
    val subtitle = when {
        !isNew -> tx.description.ifBlank { null }
        tx.type == TxType.TRANSFER || tx.type == TxType.CC_SETTLEMENT ->
            "${from?.name} → ${to?.name}" + (to?.let { " · saldo ${it.name} ${Fmt.rupiah(balance(it.id) + Ledger.walletDelta(core, it.id))}" } ?: "")
        tx.type == TxType.DEBT_PAYMENT || tx.type == TxType.DEBT_COLLECTION -> tx.debtId?.let { debtMap[it] }?.let { d ->
            val left = (debtStatus(d).remaining - tx.amount).coerceAtLeast(0)
            if (left == 0L) "🎉 ${d.name} LUNAS!" else "Sisa ${d.name}: ${Fmt.rupiah(left)}"
        }
        from != null -> "Saldo ${from.name} sekarang ${Fmt.rupiah(balance(from.id) + Ledger.walletDelta(core, from.id))}"
        else -> null
    }

    // Progres target tabungan
    var goalBefore: Float? = null; var goalAfter: Float? = null; var goalName: String? = null
    if (isNew && tx.type == TxType.SAVINGS_ALLOCATION) tx.goalId?.let { goalMap[it] }?.let { g ->
        val p = goalProgress(g)
        if (g.target > 0) {
            goalBefore = p.ratio.toFloat()
            goalAfter = ((p.collected + tx.amount).toDouble() / g.target).toFloat().coerceAtMost(1f)
            goalName = g.name
        }
    }

    // Peringatan anggaran yang terpengaruh pengeluaran ini
    var warning: String? = null
    if (isNew && tx.type == TxType.EXPENSE && tx.categoryId != null) {
        val month = YearMonth.from(Fmt.toDate(tx.dateTime))
        budgets.filter { !it.isSavings && it.categoryId != null && tx.categoryId in Ledger.descendantsOf(it.categoryId, catInfo.values) }
            .firstOrNull()?.let { b ->
                val st = budgetStatus(b, month)
                val after = st.spent + tx.amount
                warning = when {
                    after > st.available -> "Anggaran ${budgetName(b)} terlampaui ${Fmt.rupiah(after - st.available)}"
                    after >= st.available * BudgetStatus.WARN_THRESHOLD && st.spent < st.available * BudgetStatus.WARN_THRESHOLD ->
                        "Anggaran ${budgetName(b)} tinggal ${Fmt.rupiah(st.available - after)}"
                    else -> null
                }
            }
    }

    return Celebration(kind, kotlin.math.abs(tx.amount), title, from, to, subtitle, goalBefore, goalAfter, goalName, warning, streak = if (isNew) streakAfterNewRecord() else null)
}

/** Streak setelah mencatat sekarang, bila ini catatan pertama hari ini dan streak ≥ 2. */
fun Snapshot.streakAfterNewRecord(): Int? {
    val st = streak()
    return if (st.recordedToday) null else (st.current + 1).takeIf { it >= 2 }
}
