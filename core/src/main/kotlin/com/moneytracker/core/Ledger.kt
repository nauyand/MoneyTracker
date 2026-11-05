package com.moneytracker.core

import java.time.LocalDate
import java.time.YearMonth

/**
 * Ledger: satu-satunya tempat aturan efek saldo dan klasifikasi pemasukan/pengeluaran.
 * Saldo wallet TIDAK pernah disimpan; selalu dihitung = saldo awal + Σ mutasi.
 */
object Ledger {

    /** Perubahan saldo [walletId] akibat [tx]. */
    fun walletDelta(tx: Tx, walletId: Long): Long {
        var delta = 0L
        if (tx.walletId == walletId) {
            delta += when (tx.type) {
                TxType.INCOME, TxType.DEBT_COLLECTION, TxType.LOAN_RECEIVED -> tx.amount
                TxType.ADJUSTMENT -> tx.amount // bertanda
                TxType.EXPENSE, TxType.LOAN_GIVEN -> -tx.amount
                TxType.TRANSFER, TxType.SAVINGS_ALLOCATION, TxType.CC_SETTLEMENT, TxType.DEBT_PAYMENT -> -(tx.amount + tx.fee)
            }
        }
        if (tx.toWalletId == walletId) {
            delta += when (tx.type) {
                TxType.TRANSFER, TxType.SAVINGS_ALLOCATION, TxType.CC_SETTLEMENT -> tx.amount
                else -> 0
            }
        }
        return delta
    }

    /** Nominal yang dihitung sebagai pemasukan (hanya INCOME). */
    fun incomeOf(tx: Tx): Long = if (tx.type == TxType.INCOME) tx.amount else 0

    /**
     * Nominal yang dihitung sebagai pengeluaran konsumsi.
     * Transfer, tabungan, pelunasan kartu kredit, dan pokok utang TIDAK dihitung — hanya biayanya.
     */
    fun expenseOf(tx: Tx): Long = when (tx.type) {
        TxType.EXPENSE -> tx.amount
        TxType.TRANSFER, TxType.SAVINGS_ALLOCATION, TxType.CC_SETTLEMENT, TxType.DEBT_PAYMENT -> tx.fee
        else -> 0
    }

    fun savingsOf(tx: Tx): Long = if (tx.type == TxType.SAVINGS_ALLOCATION) tx.amount else 0

    fun balances(wallets: List<WalletInfo>, txs: List<Tx>): Map<Long, Long> {
        val result = wallets.associate { it.id to it.initialBalance }.toMutableMap()
        for (tx in txs) {
            if (result.containsKey(tx.walletId)) result[tx.walletId] = result.getValue(tx.walletId) + walletDelta(tx, tx.walletId)
            val to = tx.toWalletId
            if (to != null && to != tx.walletId && result.containsKey(to)) result[to] = result.getValue(to) + walletDelta(tx, to)
        }
        return result
    }

    data class WalletFlow(val moneyIn: Long, val moneyOut: Long, val lastActivity: Long?)

    /** Total uang masuk/keluar per wallet (semua jenis mutasi, termasuk transfer). */
    fun walletFlows(walletId: Long, txs: List<Tx>): WalletFlow {
        var inSum = 0L
        var outSum = 0L
        var last: Long? = null
        for (tx in txs) {
            if (tx.walletId != walletId && tx.toWalletId != walletId) continue
            val d = walletDelta(tx, walletId)
            if (d >= 0) inSum += d else outSum += -d
            if (last == null || tx.dateTime > last) last = tx.dateTime
        }
        return WalletFlow(inSum, outSum, last)
    }

    data class PeriodSummary(val income: Long, val expense: Long, val savings: Long) {
        val net: Long get() = income - expense
        /** Rasio tabungan = (pemasukan - pengeluaran) / pemasukan. */
        val savingsRate: Double? get() = if (income > 0) (income - expense).toDouble() / income else null
    }

    fun summarize(txs: List<Tx>, from: Long, toExclusive: Long): PeriodSummary {
        var inc = 0L; var exp = 0L; var sav = 0L
        for (tx in txs) {
            if (tx.dateTime < from || tx.dateTime >= toExclusive) continue
            inc += incomeOf(tx); exp += expenseOf(tx); sav += savingsOf(tx)
        }
        return PeriodSummary(inc, exp, sav)
    }

    fun summarize(txs: List<Tx>, month: YearMonth): PeriodSummary {
        val (from, to) = monthRange(month)
        return summarize(txs, from, to)
    }

    fun monthRange(month: YearMonth): Pair<Long, Long> =
        Fmt.toMillis(month.atDay(1)) to Fmt.toMillis(month.plusMonths(1).atDay(1))

    fun dayRange(day: LocalDate): Pair<Long, Long> = Fmt.toMillis(day) to Fmt.toMillis(day.plusDays(1))

    /**
     * Saldo total (wallet yang dipilih) pada akhir setiap hari dari [from] s.d. [to].
     * Dipakai untuk grafik saldo dari waktu ke waktu.
     */
    fun dailyBalanceSeries(
        wallets: List<WalletInfo>,
        txs: List<Tx>,
        from: LocalDate,
        to: LocalDate,
    ): List<Pair<LocalDate, Long>> {
        val ids = wallets.map { it.id }.toSet()
        val sorted = txs.sortedBy { it.dateTime }
        var running = wallets.sumOf { it.initialBalance }
        var idx = 0
        val out = ArrayList<Pair<LocalDate, Long>>()
        var day = from
        while (!day.isAfter(to)) {
            val end = Fmt.toMillis(day.plusDays(1))
            while (idx < sorted.size && sorted[idx].dateTime < end) {
                val tx = sorted[idx]
                if (tx.walletId in ids) running += walletDelta(tx, tx.walletId)
                val t = tx.toWalletId
                if (t != null && t != tx.walletId && t in ids) running += walletDelta(tx, t)
                idx++
            }
            out += day to running
            day = day.plusDays(1)
        }
        return out
    }

    /** Pengeluaran per kategori induk (subkategori digabung ke induknya). */
    fun expenseByRootCategory(txs: List<Tx>, categories: Map<Long, CategoryInfo>): Map<Long?, Long> {
        val out = HashMap<Long?, Long>()
        for (tx in txs) {
            if (tx.type != TxType.EXPENSE) continue
            val root = rootOf(tx.categoryId, categories)
            out[root] = (out[root] ?: 0) + tx.amount
        }
        return out
    }

    fun rootOf(categoryId: Long?, categories: Map<Long, CategoryInfo>): Long? {
        var id = categoryId ?: return null
        var guard = 0
        while (guard++ < 10) {
            val parent = categories[id]?.parentId ?: return id
            id = parent
        }
        return id
    }

    /** Semua id kategori + seluruh turunannya. */
    fun descendantsOf(categoryId: Long, categories: Collection<CategoryInfo>): Set<Long> {
        val result = mutableSetOf(categoryId)
        var changed = true
        while (changed) {
            changed = false
            for (c in categories) {
                val p = c.parentId ?: continue
                if (p in result && result.add(c.id)) changed = true
            }
        }
        return result
    }

    data class NetWorth(
        val liquidAssets: Long,      // saldo wallet positif
        val otherAssets: Long,       // aset lain (properti, kendaraan, dsb.)
        val receivables: Long,       // piutang
        val walletLiabilities: Long, // kartu kredit / wallet bersaldo negatif (nilai positif)
        val debts: Long,             // sisa utang
    ) {
        val totalAssets: Long get() = liquidAssets + otherAssets + receivables
        val totalLiabilities: Long get() = walletLiabilities + debts
        val netWorth: Long get() = totalAssets - totalLiabilities
    }

    fun netWorth(balances: Collection<Long>, otherAssets: Long, receivables: Long, payables: Long): NetWorth {
        val pos = balances.filter { it > 0 }.sum()
        val neg = -balances.filter { it < 0 }.sum()
        return NetWorth(pos, otherAssets, receivables, neg, payables)
    }
}
