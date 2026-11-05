package com.moneytracker.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.moneytracker.app.security.SecurityManager
import com.moneytracker.core.DebtDirection
import com.moneytracker.core.Fmt
import com.moneytracker.core.ImportedRow
import com.moneytracker.core.Importer
import com.moneytracker.core.Schedule
import com.moneytracker.core.TxType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class Repository(
    val db: AppDatabase,
    private val context: Context,
    val security: SecurityManager,
    scope: CoroutineScope,
) {
    val dao: AppDao = db.dao()

    @Suppress("UNCHECKED_CAST")
    val snapshot: StateFlow<Snapshot?> = combine(
        listOf<Flow<Any?>>(
            dao.user(), dao.wallets(), dao.categories(), dao.transactions(), dao.vehicles(), dao.fuelLogs(),
            dao.odometerLogs(), dao.budgets(), dao.goals(), dao.debts(), dao.recurring(), dao.assets(), dao.attachments(),
        ),
    ) { a ->
        Snapshot(
            a[0] as UserEntity?, a[1] as List<WalletEntity>, a[2] as List<CategoryEntity>, a[3] as List<TransactionEntity>,
            a[4] as List<VehicleEntity>, a[5] as List<FuelLogEntity>, a[6] as List<OdometerLogEntity>, a[7] as List<BudgetEntity>,
            a[8] as List<SavingsGoalEntity>, a[9] as List<DebtEntity>, a[10] as List<RecurringEntity>, a[11] as List<AssetEntity>,
            a[12] as List<AttachmentEntity>,
        ).also { it.netWorth } // hitung awal di background
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, null)

    fun auditFor(entity: String, id: Long) = dao.auditFor(entity, id)
    fun auditLog() = dao.auditLog()

    suspend fun ensureSeeded() = Seed.seedCategories(dao)

    suspend fun setUserName(name: String) = dao.upsertUser(UserEntity(name = name.trim().ifEmpty { "Pengguna" }))

    private suspend fun audit(entity: String, id: Long, action: String, summary: String, before: Any?, after: Any?) {
        dao.insertAudit(AuditEntity(entity = entity, entityId = id, action = action, summary = summary, before = before?.toString(), after = after?.toString()))
    }

    private fun txSummary(t: TransactionEntity) = "${t.type.label} ${Fmt.rupiah(t.amount)}" + if (t.description.isNotBlank()) " — ${t.description}" else ""

    // =============== Transaksi ===============
    class ValidationException(message: String) : Exception(message)

    fun validate(t: TransactionEntity) {
        if (t.type != TxType.ADJUSTMENT && t.amount <= 0) throw ValidationException("Nominal harus lebih dari 0")
        if (t.fee < 0) throw ValidationException("Biaya tidak boleh negatif")
        if (t.type.needsTargetWallet && t.toWalletId == null) throw ValidationException("Pilih wallet tujuan")
        if (t.toWalletId != null && t.toWalletId == t.walletId) throw ValidationException("Wallet asal dan tujuan tidak boleh sama")
        if ((t.type == TxType.DEBT_PAYMENT || t.type == TxType.DEBT_COLLECTION) && t.debtId == null) throw ValidationException("Pilih utang/piutang")
    }

    suspend fun saveTransaction(t: TransactionEntity, newImages: List<Uri> = emptyList(), removed: List<AttachmentEntity> = emptyList()): Long {
        validate(t)
        val copied = withContext(Dispatchers.IO) { newImages.mapNotNull { copyImage(it) } }
        return db.withTransaction {
            val before = if (t.id != 0L) dao.transaction(t.id) else null
            val entity = t.copy(updatedAt = System.currentTimeMillis())
            val id = dao.upsertTransaction(entity).let { if (t.id != 0L) t.id else it }
            copied.forEach { dao.upsertAttachment(AttachmentEntity(transactionId = id, path = it)) }
            removed.forEach { dao.deleteAttachment(it); File(context.filesDir, it.path).delete() }
            audit("transaction", id, if (before == null) "CREATE" else "UPDATE", txSummary(entity), before, entity.copy(id = id))
            id
        }
    }

    suspend fun deleteTransaction(id: Long) {
        db.withTransaction {
            val t = dao.transaction(id) ?: return@withTransaction
            dao.fuelLogByTx(id)?.let { dao.deleteFuelLog(it.id) }
            dao.attachmentsOf(id).forEach { File(context.filesDir, it.path).delete() }
            dao.deleteAttachmentsOf(id)
            dao.deleteTransaction(id)
            audit("transaction", id, "DELETE", txSummary(t), t, null)
        }
    }

    suspend fun duplicateTransaction(id: Long): Long? {
        val t = dao.transaction(id) ?: return null
        if (dao.fuelLogByTx(id) != null) return null // duplikasi BBM lewat form BBM agar odometer benar
        return saveTransaction(t.copy(id = 0, dateTime = System.currentTimeMillis(), importKey = null, recurringId = null, createdAt = System.currentTimeMillis()))
    }

    private fun copyImage(uri: Uri): String? = runCatching {
        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
        val name = "receipts/${UUID.randomUUID()}.jpg"
        context.contentResolver.openInputStream(uri)?.use { input -> File(context.filesDir, name).outputStream().use { input.copyTo(it) } }
        if (dir.exists()) name else null
    }.getOrNull()

    fun attachmentFile(a: AttachmentEntity) = File(context.filesDir, a.path)

    // =============== Wallet ===============
    suspend fun saveWallet(w: WalletEntity): Long {
        val before = w.id.takeIf { it != 0L }?.let { id -> snapshot.value?.walletMap?.get(id) }
        val e = w.copy(
            updatedAt = System.currentTimeMillis(),
            sortOrder = if (w.id == 0L) dao.maxWalletOrder() + 1 else w.sortOrder,
        )
        val id = dao.upsertWallet(e).let { if (w.id != 0L) w.id else it }
        audit("wallet", id, if (before == null) "CREATE" else "UPDATE", "Wallet ${w.name}", before, e)
        return id
    }

    /** Simpan urutan hasil drag & drop. [ordered] = (walletId, bagian Utama?) dari atas ke bawah. */
    suspend fun reorderWallets(ordered: List<Pair<Long, Boolean>>) = db.withTransaction {
        ordered.forEachIndexed { i, (id, main) -> dao.setWalletOrder(id, i, main) }
    }

    /** Jadikan wallet utama/default: dipindah ke posisi teratas bagian Utama. */
    suspend fun setPrimaryWallet(walletId: Long) {
        val list = snapshot.value?.wallets?.filter { !it.archived } ?: return
        val target = list.firstOrNull { it.id == walletId } ?: return
        reorderWallets(listOf(target.id to true) + list.filter { it.id != walletId }.map { it.id to it.isMain })
    }

    /** Menghapus wallet beserta seluruh transaksinya (termasuk log BBM terkait). */
    suspend fun deleteWallet(w: WalletEntity) {
        db.withTransaction {
            val txs = dao.transactionsOfWallet(w.id)
            for (t in txs) {
                dao.fuelLogByTx(t.id)?.let { dao.deleteFuelLog(it.id) }
                dao.attachmentsOf(t.id).forEach { File(context.filesDir, it.path).delete() }
                dao.deleteAttachmentsOf(t.id)
            }
            dao.deleteTransactionsOfWallet(w.id)
            dao.deleteWallet(w)
            audit("wallet", w.id, "DELETE", "Wallet ${w.name} (+${txs.size} transaksi)", w, null)
        }
    }

    /**
     * Rekonsiliasi: pengguna memasukkan saldo aktual; selisih dicatat sebagai transaksi Penyesuaian
     * (bukan menimpa saldo), sehingga riwayat tetap bisa ditelusuri.
     */
    suspend fun reconcile(walletId: Long, actualBalance: Long, note: String): Long {
        val current = snapshot.value?.balance(walletId) ?: 0
        val diff = actualBalance - current
        if (diff == 0L) return 0
        return saveTransaction(
            TransactionEntity(
                type = TxType.ADJUSTMENT, amount = diff, walletId = walletId, dateTime = System.currentTimeMillis(),
                description = "Rekonsiliasi saldo", note = note.ifBlank { "Saldo tercatat ${Fmt.rupiah(current)} → aktual ${Fmt.rupiah(actualBalance)}" },
            ),
        )
    }

    // =============== Kategori ===============
    suspend fun saveCategory(c: CategoryEntity): Long = dao.upsertCategory(c).let { if (c.id != 0L) c.id else it }

    /** Hapus kategori + subkategori; transaksinya menjadi "Tanpa kategori" atau dipindah ke [replacementId]. */
    suspend fun deleteCategory(c: CategoryEntity, replacementId: Long?) {
        db.withTransaction {
            val all = snapshot.value?.categories ?: emptyList()
            val ids = com.moneytracker.core.Ledger.descendantsOf(c.id, all.map { com.moneytracker.core.CategoryInfo(it.id, it.name, it.parentId, it.kind) })
            for (id in ids) {
                dao.reassignCategory(id, replacementId)
                all.firstOrNull { it.id == id }?.let { dao.deleteCategory(it) }
            }
            audit("category", c.id, "DELETE", "Kategori ${c.name}", c, null)
        }
    }

    // =============== BBM & Kendaraan ===============
    suspend fun saveVehicle(v: VehicleEntity): Long = dao.upsertVehicle(v).let { if (v.id != 0L) v.id else it }

    /** Log BBM dihapus; transaksi pengeluarannya TETAP ada (uangnya memang keluar), hanya tidak terhubung lagi. */
    suspend fun deleteVehicle(v: VehicleEntity) = db.withTransaction {
        dao.deleteFuelLogsOf(v.id); dao.deleteOdometersOf(v.id); dao.unlinkVehicle(v.id); dao.deleteVehicle(v)
        audit("vehicle", v.id, "DELETE", "Kendaraan ${v.name}", v, null)
    }

    private fun fuelCategoryFor(fuelType: String): Long? {
        val s = snapshot.value ?: return null
        val fuelCats = s.categories.filter { it.systemKey == "fuel" && s.categoryMap[it.parentId]?.systemKey == "transport" }
        val lower = fuelType.lowercase()
        return (fuelCats.firstOrNull { lower.startsWith(it.name.lowercase()) }
            ?: fuelCats.firstOrNull { (lower.contains("solar") && it.name == "Solar") || (lower.contains("dex") && it.name == "Dex") }
            ?: fuelCats.firstOrNull { it.name == "Bensin" })?.id
    }

    /**
     * Menyimpan pengisian BBM sekaligus transaksi pengeluarannya (satu input, dua catatan yang selalu sinkron).
     */
    suspend fun saveFuelLog(log: FuelLogEntity): Long {
        val vehicle = snapshot.value?.vehicleMap?.get(log.vehicleId)
        return db.withTransaction {
            val existingTx = log.transactionId?.let { dao.transaction(it) }
            val tx = TransactionEntity(
                id = existingTx?.id ?: 0,
                type = TxType.EXPENSE,
                amount = log.totalCost,
                walletId = log.walletId,
                categoryId = existingTx?.categoryId?.takeIf { log.fuelType == dao.fuelLog(log.id)?.fuelType } ?: fuelCategoryFor(log.fuelType),
                vehicleId = log.vehicleId,
                dateTime = log.dateTime,
                description = "BBM ${log.fuelType}" + (vehicle?.let { " · ${it.name}" } ?: ""),
                note = log.note,
                location = log.station,
                tags = "bbm",
                createdAt = existingTx?.createdAt ?: System.currentTimeMillis(),
            )
            validate(tx)
            val txId = dao.upsertTransaction(tx).let { if (tx.id != 0L) tx.id else it }
            val id = dao.upsertFuelLog(log.copy(transactionId = txId)).let { if (log.id != 0L) log.id else it }
            audit("transaction", txId, if (existingTx == null) "CREATE" else "UPDATE", txSummary(tx), existingTx, tx.copy(id = txId))
            id
        }
    }

    suspend fun deleteFuelLog(id: Long) {
        val log = dao.fuelLog(id) ?: return
        db.withTransaction {
            dao.deleteFuelLog(id)
            log.transactionId?.let { txId ->
                dao.transaction(txId)?.let { audit("transaction", txId, "DELETE", txSummary(it), it, null) }
                dao.deleteTransaction(txId)
            }
        }
    }

    suspend fun addOdometer(vehicleId: Long, km: Long, date: Long) = dao.upsertOdometer(OdometerLogEntity(vehicleId = vehicleId, dateTime = date, odometerKm = km))
    suspend fun deleteOdometer(id: Long) = dao.deleteOdometer(id)

    // =============== Anggaran / Tabungan / Aset ===============
    suspend fun saveBudget(b: BudgetEntity) = dao.upsertBudget(b)
    suspend fun deleteBudget(b: BudgetEntity) = dao.deleteBudget(b)

    suspend fun saveGoal(g: SavingsGoalEntity) = dao.upsertGoal(g)
    suspend fun deleteGoal(g: SavingsGoalEntity) = db.withTransaction { dao.unlinkGoal(g.id); dao.deleteGoal(g) }

    /** Setor ke target: alokasi tabungan dari wallet sumber ke wallet target (jika ada). Bukan pengeluaran. */
    suspend fun contributeGoal(goal: SavingsGoalEntity, fromWalletId: Long, amount: Long, date: Long, note: String) = saveTransaction(
        TransactionEntity(
            type = TxType.SAVINGS_ALLOCATION, amount = amount, walletId = fromWalletId,
            toWalletId = goal.walletId?.takeIf { it != fromWalletId }, goalId = goal.id, dateTime = date,
            categoryId = snapshot.value?.categories?.firstOrNull { it.systemKey == "savings" }?.id,
            description = "Setor ke ${goal.name}", note = note,
        ),
    )

    /** Tarik dana target ke wallet lain (transfer, bukan pemasukan). Butuh target yang terhubung ke wallet. */
    suspend fun withdrawGoal(goal: SavingsGoalEntity, toWalletId: Long, amount: Long, date: Long, note: String): Long {
        val from = goal.walletId ?: throw ValidationException("Target ini tidak terhubung ke wallet. Hubungkan wallet tabungan untuk penarikan.")
        return saveTransaction(
            TransactionEntity(type = TxType.TRANSFER, amount = amount, walletId = from, toWalletId = toWalletId, goalId = goal.id, dateTime = date, description = "Tarik dari ${goal.name}", note = note),
        )
    }

    suspend fun saveAsset(a: AssetEntity) = dao.upsertAsset(a.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteAsset(a: AssetEntity) = dao.deleteAsset(a)

    // =============== Utang & Piutang ===============
    /**
     * @param disburseWalletId bila diisi saat membuat: utang → uang pinjaman masuk ke wallet (LOAN_RECEIVED);
     * piutang → uang dipinjamkan keluar dari wallet (LOAN_GIVEN). Keduanya bukan pemasukan/pengeluaran.
     */
    suspend fun saveDebt(d: DebtEntity, disburseWalletId: Long?): Long = db.withTransaction {
        val isNew = d.id == 0L
        val id = dao.upsertDebt(d).let { if (!isNew) d.id else it }
        if (isNew && disburseWalletId != null) {
            dao.upsertTransaction(
                TransactionEntity(
                    type = if (d.direction == DebtDirection.PAYABLE) TxType.LOAN_RECEIVED else TxType.LOAN_GIVEN,
                    amount = d.principal, walletId = disburseWalletId, debtId = id, dateTime = Fmt.toMillis(LocalDate.ofEpochDay(d.startDate).atTime(LocalTime.NOON)),
                    description = (if (d.direction == DebtDirection.PAYABLE) "Pinjaman dari " else "Pinjaman ke ") + d.counterparty.ifBlank { d.name },
                ),
            )
        }
        audit("debt", id, if (isNew) "CREATE" else "UPDATE", "${d.direction.label} ${d.name}", null, d)
        id
    }

    suspend fun deleteDebt(d: DebtEntity) = db.withTransaction { dao.unlinkDebt(d.id); dao.deleteDebt(d) }

    /** Pembayaran utang (pokok + bunga/denda) atau penerimaan piutang. Memajukan jatuh tempo cicilan. */
    suspend fun payDebt(d: DebtEntity, walletId: Long, principal: Long, interest: Long, date: Long, note: String): Long {
        val id = saveTransaction(
            TransactionEntity(
                type = if (d.direction == DebtDirection.PAYABLE) TxType.DEBT_PAYMENT else TxType.DEBT_COLLECTION,
                amount = principal, fee = if (d.direction == DebtDirection.PAYABLE) interest else 0, walletId = walletId, debtId = d.id, dateTime = date,
                categoryId = snapshot.value?.categories?.firstOrNull { it.systemKey == "debt" }?.id,
                description = (if (d.direction == DebtDirection.PAYABLE) "Bayar " else "Terima ") + d.name, note = note,
            ),
        )
        if (d.kind == com.moneytracker.core.DebtKind.INSTALLMENT && d.dueDate != null) {
            dao.upsertDebt(d.copy(dueDate = LocalDate.ofEpochDay(d.dueDate).plusMonths(1).toEpochDay()))
        }
        return id
    }

    // =============== Berulang & Langganan ===============
    suspend fun saveRecurring(r: RecurringEntity) = dao.upsertRecurring(r)
    suspend fun deleteRecurring(r: RecurringEntity) = dao.deleteRecurring(r)

    /** Mencatat satu kejadian transaksi berulang (tanggal = jatuh tempo) lalu memajukan jadwal. */
    suspend fun postRecurring(r: RecurringEntity, amountOverride: Long? = null): Long = db.withTransaction {
        val date = LocalDate.ofEpochDay(r.nextDate)
        val tx = TransactionEntity(
            type = if (r.kind.isIncome) TxType.INCOME else TxType.EXPENSE,
            amount = amountOverride ?: r.amount, walletId = r.walletId, categoryId = r.categoryId, recurringId = r.id,
            dateTime = Fmt.toMillis(date.atTime(LocalTime.of(8, 0))), description = r.name, note = r.kind.label, tags = "rutin",
        )
        val id = dao.upsertTransaction(tx)
        dao.upsertRecurring(r.copy(nextDate = Schedule.next(date, r.period, r.interval, r.anchorDay).toEpochDay()))
        audit("transaction", id, "CREATE", txSummary(tx) + " (otomatis)", null, tx.copy(id = id))
        id
    }

    suspend fun skipRecurring(r: RecurringEntity) =
        dao.upsertRecurring(r.copy(nextDate = Schedule.next(LocalDate.ofEpochDay(r.nextDate), r.period, r.interval, r.anchorDay).toEpochDay()))

    /** Dipanggil saat aplikasi dibuka & oleh worker harian. Mengembalikan jumlah transaksi yang dicatat. */
    suspend fun processDueRecurring(today: LocalDate = LocalDate.now(Fmt.zone)): Int {
        var count = 0
        for (r0 in dao.activeRecurring().filter { it.autoPost }) {
            var r = r0
            var guard = 0
            while (r.nextDate <= today.toEpochDay() && guard++ < 36) {
                postRecurring(r); count++
                r = r.copy(nextDate = Schedule.next(LocalDate.ofEpochDay(r.nextDate), r.period, r.interval, r.anchorDay).toEpochDay())
            }
        }
        return count
    }

    // =============== Impor ===============
    data class ImportCandidate(val row: ImportedRow, val exactDuplicate: Boolean, val possibleDuplicate: Boolean)

    suspend fun prepareImport(walletId: Long, rows: List<ImportedRow>): List<ImportCandidate> {
        val keys = dao.importKeys(walletId).toSet()
        val existing = snapshot.value?.txCore?.filter { it.walletId == walletId } ?: emptyList()
        val loose = existing.map { "${Fmt.toDate(it.dateTime)}|${kotlin.math.abs(com.moneytracker.core.Ledger.walletDelta(it, walletId))}" }.toSet()
        return rows.map { r ->
            val key = Importer.duplicateKey(r.dateTime.toLocalDate(), r.amount, r.description)
            ImportCandidate(r, key in keys, "${r.dateTime.toLocalDate()}|${kotlin.math.abs(r.amount)}" in loose)
        }
    }

    suspend fun importRows(walletId: Long, rows: List<ImportedRow>, incomeCategoryId: Long?, expenseCategoryId: Long?): Int {
        val entities = rows.map { r ->
            TransactionEntity(
                type = if (r.amount > 0) TxType.INCOME else TxType.EXPENSE,
                amount = kotlin.math.abs(r.amount), walletId = walletId,
                categoryId = if (r.amount > 0) incomeCategoryId else expenseCategoryId,
                dateTime = Fmt.toMillis(r.dateTime), description = r.description.take(200), tags = "impor",
                importKey = Importer.duplicateKey(r.dateTime.toLocalDate(), r.amount, r.description),
            )
        }
        db.withTransaction {
            dao.upsertTransactions(entities)
            audit("import", walletId, "CREATE", "Impor ${entities.size} transaksi", null, null)
        }
        return entities.size
    }

    // =============== Akun ===============
    /** Hapus akun: seluruh database, lampiran, preferensi & kunci enkripsi. */
    suspend fun wipeEverything() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        db.close()
        // Kunci enkripsi akan dihapus, jadi file database lama juga harus dihapus.
        context.deleteDatabase(AppDatabase.NAME)
        File(context.filesDir, "receipts").deleteRecursively()
        File(context.filesDir, "vehicles").deleteRecursively()
    }

    suspend fun saveVehiclePhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            File(context.filesDir, "vehicles").mkdirs()
            val name = "vehicles/${UUID.randomUUID()}.jpg"
            context.contentResolver.openInputStream(uri)?.use { i -> File(context.filesDir, name).outputStream().use { i.copyTo(it) } }
            name
        }.getOrNull()
    }

    fun file(path: String) = File(context.filesDir, path)
}
