package com.moneytracker.core

/**
 * Jenis transaksi. Setiap jenis punya efek saldo yang TETAP (lihat [Ledger.walletDelta])
 * dan aturan TETAP apakah dihitung sebagai pemasukan/pengeluaran konsumsi.
 *
 * | Jenis              | walletId      | toWalletId     | Pemasukan | Pengeluaran        |
 * |--------------------|---------------|----------------|-----------|--------------------|
 * | INCOME             | + amount      | -              | amount    | -                  |
 * | EXPENSE            | - amount      | -              | -         | amount             |
 * | TRANSFER           | - (amt + fee) | + amount       | -         | fee (biaya admin)  |
 * | SAVINGS_ALLOCATION | - (amt + fee) | + amount (ops) | -         | fee                |
 * | CC_SETTLEMENT      | - (amt + fee) | + amount (CC)  | -         | fee                |
 * | DEBT_PAYMENT       | - (amt + fee) | -              | -         | fee (bunga/denda)  |
 * | DEBT_COLLECTION    | + amount      | -              | -         | -                  |
 * | LOAN_RECEIVED      | + amount      | -              | -         | -                  |
 * | LOAN_GIVEN         | - amount      | -              | -         | -                  |
 * | ADJUSTMENT         | + amount (±)  | -              | -         | -                  |
 */
enum class TxType {
    INCOME, EXPENSE, TRANSFER, SAVINGS_ALLOCATION, CC_SETTLEMENT,
    DEBT_PAYMENT, DEBT_COLLECTION, LOAN_RECEIVED, LOAN_GIVEN, ADJUSTMENT;

    val label: String
        get() = when (this) {
            INCOME -> "Pemasukan"
            EXPENSE -> "Pengeluaran"
            TRANSFER -> "Transfer"
            SAVINGS_ALLOCATION -> "Alokasi Tabungan"
            CC_SETTLEMENT -> "Pelunasan Kartu Kredit"
            DEBT_PAYMENT -> "Bayar Utang"
            DEBT_COLLECTION -> "Terima Piutang"
            LOAN_RECEIVED -> "Terima Pinjaman"
            LOAN_GIVEN -> "Beri Pinjaman"
            ADJUSTMENT -> "Penyesuaian Saldo"
        }

    val needsTargetWallet: Boolean get() = this == TRANSFER || this == CC_SETTLEMENT
}

enum class WalletType(val label: String, val isLiability: Boolean = false) {
    BANK("Bank"),
    EWALLET("E-Wallet"),
    DIGITAL_BANK("Bank Digital"),
    CASH("Uang Tunai"),
    BUSINESS("Rekening Bisnis"),
    SAVINGS("Rekening Tabungan"),
    INVESTMENT("Rekening Investasi"),
    CREDIT_CARD("Kartu Kredit", isLiability = true),
}

enum class CategoryKind { EXPENSE, INCOME }

/** Kebutuhan vs keinginan, untuk rasio needs/wants. */
enum class Nature(val label: String) { NEED("Kebutuhan"), WANT("Keinginan"), NONE("Lainnya") }

enum class Period(val label: String) {
    DAILY("Harian"), WEEKLY("Mingguan"), MONTHLY("Bulanan"), QUARTERLY("3 Bulanan"), YEARLY("Tahunan");

    /** Faktor konversi ke biaya per bulan. */
    val perMonthFactor: Double
        get() = when (this) {
            DAILY -> 365.0 / 12.0
            WEEKLY -> 52.0 / 12.0
            MONTHLY -> 1.0
            QUARTERLY -> 1.0 / 3.0
            YEARLY -> 1.0 / 12.0
        }
}

enum class RecurringKind(val label: String) {
    SUBSCRIPTION("Langganan"), BILL("Tagihan Rutin"), SALARY("Gaji"), RECURRING_INCOME("Pemasukan Rutin"), RECURRING_EXPENSE("Pengeluaran Rutin");

    val isIncome: Boolean get() = this == SALARY || this == RECURRING_INCOME
}

enum class DebtDirection(val label: String) { PAYABLE("Utang"), RECEIVABLE("Piutang") }

enum class DebtKind(val label: String) { PERSONAL("Pribadi"), INSTALLMENT("Cicilan"), LOAN("Pinjaman") }

enum class VehicleType(val label: String) { MOTOR("Motor"), MOBIL("Mobil"), LAINNYA("Lainnya") }

enum class Confidence(val label: String) { HIGH("Tinggi"), MEDIUM("Sedang"), LOW("Rendah") }

/** Representasi ringan transaksi untuk perhitungan (dipetakan dari entitas Room). */
data class Tx(
    val id: Long,
    val type: TxType,
    val amount: Long,
    val fee: Long = 0,
    val walletId: Long,
    val toWalletId: Long? = null,
    val categoryId: Long? = null,
    val goalId: Long? = null,
    val debtId: Long? = null,
    val vehicleId: Long? = null,
    val recurringId: Long? = null,
    val dateTime: Long,
    val description: String = "",
)

data class WalletInfo(
    val id: Long,
    val name: String,
    val type: WalletType,
    val initialBalance: Long,
    val createdAt: Long = 0,
)

data class CategoryInfo(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val kind: CategoryKind,
    val nature: Nature = Nature.NONE,
)
