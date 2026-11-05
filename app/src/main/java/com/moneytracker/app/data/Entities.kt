package com.moneytracker.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneytracker.core.CategoryKind
import com.moneytracker.core.DebtDirection
import com.moneytracker.core.DebtKind
import com.moneytracker.core.Nature
import com.moneytracker.core.Period
import com.moneytracker.core.RecurringKind
import com.moneytracker.core.TxType
import com.moneytracker.core.VehicleType
import com.moneytracker.core.WalletType

/*
 * Konvensi:
 *  - Uang: Long Rupiah. Volume BBM: Long mililiter. Tanggal-jam: epoch millis. Tanggal saja: epochDay.
 *  - Saldo wallet TIDAK disimpan — dihitung dari initialBalance + transaksi (lihat core.Ledger).
 *  - Tidak pernah ada kolom untuk PIN/password/kredensial bank.
 */

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Long = 1,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: WalletType,
    /** Kode penyedia, mis. "BCA", "GOPAY", "CUSTOM". */
    val provider: String,
    val color: Long,
    val initialBalance: Long,
    val creditLimit: Long? = null,
    /** Hanya 4 digit terakhir (opsional) sebagai penanda — bukan kredensial. */
    val accountSuffix: String? = null,
    val note: String = "",
    val archived: Boolean = false,
    val includeInTotal: Boolean = true,
    /** Urutan tampil (hasil drag & drop). Wallet Utama pertama = wallet default. */
    val sortOrder: Int = 0,
    /** true = bagian "Utama" (di atas), false = bagian "Opsional". */
    @ColumnInfo(defaultValue = "1") val isMain: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "categories", indices = [Index("parentId")])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val kind: CategoryKind,
    val icon: String,
    val color: Long,
    val nature: Nature = Nature.NONE,
    /** Kunci sistem untuk kategori khusus (mis. "fuel", "food", "fee", "savings"). */
    val systemKey: String? = null,
    /** Kategori yang seharusnya dicatat dengan jenis transaksi lain agar tidak terhitung ganda. */
    val preferredType: TxType? = null,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

@Entity(
    tableName = "transactions",
    indices = [Index("dateTime"), Index("walletId"), Index("toWalletId"), Index("categoryId"), Index("vehicleId"), Index("goalId"), Index("debtId")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    /** Selalu positif, kecuali ADJUSTMENT (bertanda). */
    val amount: Long,
    /** Biaya admin / bunga — dihitung sebagai pengeluaran. */
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
    val note: String = "",
    /** Dipisah koma. */
    val tags: String = "",
    val location: String = "",
    /** Kunci deteksi duplikat untuk impor. */
    val importKey: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "attachments", indices = [Index("transactionId")])
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    /** Path relatif di filesDir. */
    val path: String,
    val mime: String = "image/jpeg",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: VehicleType,
    val brand: String = "",
    val model: String = "",
    val year: Int? = null,
    val fuelType: String,
    val tankCapacityMl: Long? = null,
    /** Konsumsi rata-rata (km/L) dari pengguna/pabrikan — fallback bila riwayat belum cukup. */
    val refKmPerLiter: Double? = null,
    val initialOdometer: Long? = null,
    val photoPath: String? = null,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "fuel_logs", indices = [Index("vehicleId"), Index("transactionId")])
data class FuelLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    /** Transaksi pengeluaran yang dibuat otomatis (mengurangi wallet). */
    val transactionId: Long? = null,
    val dateTime: Long,
    val odometerKm: Long,
    val fuelType: String,
    val pricePerLiter: Long,
    val totalCost: Long,
    val volumeMl: Long,
    val station: String = "",
    val walletId: Long,
    val fullTank: Boolean,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "odometer_logs", indices = [Index("vehicleId")])
data class OdometerLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val dateTime: Long,
    val odometerKm: Long,
    val note: String = "",
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Kategori induk; null bila [isSavings]. */
    val categoryId: Long? = null,
    val isSavings: Boolean = false,
    val amount: Long,
    val rollover: Boolean = false,
    /** "yyyy-MM" */
    val startMonth: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Long,
    val target: Long,
    /** Dana yang sudah terkumpul sebelum memakai aplikasi. */
    val initialAmount: Long = 0,
    /** epochDay */
    val deadline: Long? = null,
    val monthlyTarget: Long? = null,
    /** Wallet tujuan penyimpanan dana (opsional, mis. rekening tabungan). */
    val walletId: Long? = null,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val counterparty: String = "",
    val direction: DebtDirection,
    val kind: DebtKind,
    val principal: Long,
    /** epochDay */
    val startDate: Long,
    /** epochDay, jatuh tempo pelunasan / cicilan berikutnya. */
    val dueDate: Long? = null,
    val installmentAmount: Long? = null,
    val installmentCount: Int? = null,
    val note: String = "",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Satu tabel untuk Subscriptions & RecurringTransactions; dibedakan oleh [kind]. */
@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: RecurringKind,
    val name: String,
    val amount: Long,
    val walletId: Long,
    val categoryId: Long? = null,
    val period: Period,
    val interval: Int = 1,
    /** epochDay jatuh tempo berikutnya. */
    val nextDate: Long,
    /** Tanggal patokan untuk periode bulanan/tahunan (1–31). */
    val anchorDay: Int? = null,
    /** true = otomatis dicatat saat jatuh tempo; false = hanya diingatkan. */
    val autoPost: Boolean = false,
    val reminderDays: Int = 2,
    val active: Boolean = true,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val value: Long,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "audit_log", indices = [Index("entity", "entityId")])
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entity: String,
    val entityId: Long,
    /** CREATE / UPDATE / DELETE */
    val action: String,
    val summary: String,
    val before: String? = null,
    val after: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
