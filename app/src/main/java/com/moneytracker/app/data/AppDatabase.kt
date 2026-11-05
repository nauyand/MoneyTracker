package com.moneytracker.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Dao
interface AppDao {
    // ---------- Flows ----------
    @Query("SELECT * FROM users LIMIT 1") fun user(): Flow<UserEntity?>
    @Query("SELECT * FROM wallets ORDER BY archived, isMain DESC, sortOrder, id") fun wallets(): Flow<List<WalletEntity>>
    @Query("SELECT * FROM categories ORDER BY sortOrder, id") fun categories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM transactions ORDER BY dateTime DESC, id DESC") fun transactions(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM vehicles ORDER BY archived, id") fun vehicles(): Flow<List<VehicleEntity>>
    @Query("SELECT * FROM fuel_logs ORDER BY dateTime, odometerKm") fun fuelLogs(): Flow<List<FuelLogEntity>>
    @Query("SELECT * FROM odometer_logs ORDER BY dateTime") fun odometerLogs(): Flow<List<OdometerLogEntity>>
    @Query("SELECT * FROM budgets ORDER BY id") fun budgets(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM savings_goals ORDER BY archived, id") fun goals(): Flow<List<SavingsGoalEntity>>
    @Query("SELECT * FROM debts ORDER BY archived, dueDate IS NULL, dueDate, id") fun debts(): Flow<List<DebtEntity>>
    @Query("SELECT * FROM recurring ORDER BY active DESC, nextDate") fun recurring(): Flow<List<RecurringEntity>>
    @Query("SELECT * FROM assets ORDER BY value DESC") fun assets(): Flow<List<AssetEntity>>
    @Query("SELECT * FROM attachments") fun attachments(): Flow<List<AttachmentEntity>>
    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT 500") fun auditLog(): Flow<List<AuditEntity>>
    @Query("SELECT * FROM audit_log WHERE entity = :entity AND entityId = :id ORDER BY timestamp DESC") fun auditFor(entity: String, id: Long): Flow<List<AuditEntity>>

    // ---------- One-shot ----------
    @Query("SELECT COUNT(*) FROM categories") suspend fun categoryCount(): Int
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun transaction(id: Long): TransactionEntity?
    @Query("SELECT * FROM transactions WHERE walletId = :walletId OR toWalletId = :walletId") suspend fun transactionsOfWallet(walletId: Long): List<TransactionEntity>
    @Query("SELECT importKey FROM transactions WHERE walletId = :walletId AND importKey IS NOT NULL") suspend fun importKeys(walletId: Long): List<String>
    @Query("SELECT * FROM transactions WHERE walletId = :walletId AND dateTime BETWEEN :from AND :to") suspend fun transactionsBetween(walletId: Long, from: Long, to: Long): List<TransactionEntity>
    @Query("SELECT * FROM fuel_logs WHERE id = :id") suspend fun fuelLog(id: Long): FuelLogEntity?
    @Query("SELECT * FROM fuel_logs WHERE transactionId = :txId") suspend fun fuelLogByTx(txId: Long): FuelLogEntity?
    @Query("SELECT * FROM recurring WHERE active = 1") suspend fun activeRecurring(): List<RecurringEntity>
    @Query("SELECT * FROM debts WHERE archived = 0") suspend fun activeDebts(): List<DebtEntity>
    @Query("SELECT * FROM attachments WHERE transactionId = :txId") suspend fun attachmentsOf(txId: Long): List<AttachmentEntity>
    @Query("SELECT COUNT(*) FROM transactions WHERE createdAt >= :since") suspend fun countCreatedSince(since: Long): Int
    @Query("SELECT createdAt FROM transactions WHERE createdAt >= :since AND importKey IS NULL AND recurringId IS NULL") suspend fun recordTimestampsSince(since: Long): List<Long>
    @Query("SELECT * FROM transactions WHERE dateTime >= :from AND dateTime < :to") suspend fun transactionsDatedBetween(from: Long, to: Long): List<TransactionEntity>
    @Query("UPDATE wallets SET sortOrder = :order, isMain = :main WHERE id = :id") suspend fun setWalletOrder(id: Long, order: Int, main: Boolean)
    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM wallets") suspend fun maxWalletOrder(): Int

    // ---------- Upserts ----------
    @Upsert suspend fun upsertUser(e: UserEntity)
    @Upsert suspend fun upsertWallet(e: WalletEntity): Long
    @Upsert suspend fun upsertCategory(e: CategoryEntity): Long
    @Upsert suspend fun upsertCategories(e: List<CategoryEntity>): List<Long>
    @Upsert suspend fun upsertTransaction(e: TransactionEntity): Long
    @Upsert suspend fun upsertTransactions(e: List<TransactionEntity>): List<Long>
    @Upsert suspend fun upsertAttachment(e: AttachmentEntity): Long
    @Upsert suspend fun upsertVehicle(e: VehicleEntity): Long
    @Upsert suspend fun upsertFuelLog(e: FuelLogEntity): Long
    @Upsert suspend fun upsertOdometer(e: OdometerLogEntity): Long
    @Upsert suspend fun upsertBudget(e: BudgetEntity): Long
    @Upsert suspend fun upsertGoal(e: SavingsGoalEntity): Long
    @Upsert suspend fun upsertDebt(e: DebtEntity): Long
    @Upsert suspend fun upsertRecurring(e: RecurringEntity): Long
    @Upsert suspend fun upsertAsset(e: AssetEntity): Long
    @Upsert suspend fun insertAudit(e: AuditEntity): Long

    // ---------- Deletes ----------
    @Delete suspend fun deleteWallet(e: WalletEntity)
    @Delete suspend fun deleteCategory(e: CategoryEntity)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteTransaction(id: Long)
    @Query("DELETE FROM transactions WHERE walletId = :walletId OR toWalletId = :walletId") suspend fun deleteTransactionsOfWallet(walletId: Long)
    @Query("DELETE FROM attachments WHERE transactionId = :txId") suspend fun deleteAttachmentsOf(txId: Long)
    @Delete suspend fun deleteAttachment(e: AttachmentEntity)
    @Delete suspend fun deleteVehicle(e: VehicleEntity)
    @Query("DELETE FROM fuel_logs WHERE id = :id") suspend fun deleteFuelLog(id: Long)
    @Query("DELETE FROM fuel_logs WHERE vehicleId = :vehicleId") suspend fun deleteFuelLogsOf(vehicleId: Long)
    @Query("DELETE FROM odometer_logs WHERE id = :id") suspend fun deleteOdometer(id: Long)
    @Query("DELETE FROM odometer_logs WHERE vehicleId = :vehicleId") suspend fun deleteOdometersOf(vehicleId: Long)
    @Delete suspend fun deleteBudget(e: BudgetEntity)
    @Delete suspend fun deleteGoal(e: SavingsGoalEntity)
    @Delete suspend fun deleteDebt(e: DebtEntity)
    @Delete suspend fun deleteRecurring(e: RecurringEntity)
    @Delete suspend fun deleteAsset(e: AssetEntity)
    @Query("UPDATE transactions SET categoryId = :newId WHERE categoryId = :oldId") suspend fun reassignCategory(oldId: Long, newId: Long?)
    @Query("UPDATE transactions SET goalId = NULL WHERE goalId = :goalId") suspend fun unlinkGoal(goalId: Long)
    @Query("UPDATE transactions SET debtId = NULL WHERE debtId = :debtId") suspend fun unlinkDebt(debtId: Long)
    @Query("UPDATE transactions SET vehicleId = NULL WHERE vehicleId = :vehicleId") suspend fun unlinkVehicle(vehicleId: Long)
    @Query("UPDATE categories SET parentId = :newParent WHERE parentId = :oldParent") suspend fun reparentCategories(oldParent: Long, newParent: Long?)
}

@Database(
    entities = [
        UserEntity::class, WalletEntity::class, CategoryEntity::class, TransactionEntity::class, AttachmentEntity::class,
        VehicleEntity::class, FuelLogEntity::class, OdometerLogEntity::class, BudgetEntity::class, SavingsGoalEntity::class,
        DebtEntity::class, RecurringEntity::class, AssetEntity::class, AuditEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        const val NAME = "moneytracker.db"

        /** Nama tabel untuk backup/restore generik (urutan penting saat restore). */
        val TABLES = listOf(
            "users", "wallets", "categories", "transactions", "attachments", "vehicles", "fuel_logs", "odometer_logs",
            "budgets", "savings_goals", "debts", "recurring", "assets", "audit_log",
        )

        /** Database dienkripsi penuh dengan SQLCipher; kunci acak disimpan terenkripsi oleh Android Keystore. */
        /** v2: wallet bisa ditandai Utama/Opsional (urutan tetap memakai sortOrder). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `wallets` ADD COLUMN `isMain` INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun create(context: Context, passphrase: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
