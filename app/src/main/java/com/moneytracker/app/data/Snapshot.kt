package com.moneytracker.app.data

import com.moneytracker.core.BudgetCalc
import com.moneytracker.core.BudgetSpec
import com.moneytracker.core.BudgetStatus
import com.moneytracker.core.CategoryInfo
import com.moneytracker.core.CategoryKind
import com.moneytracker.core.DebtCalc
import com.moneytracker.core.DebtDirection
import com.moneytracker.core.DebtStatus
import com.moneytracker.core.Fmt
import com.moneytracker.core.FuelAnalytics
import com.moneytracker.core.FuelEntry
import com.moneytracker.core.FuelReport
import com.moneytracker.core.GoalCalc
import com.moneytracker.core.GoalProgress
import com.moneytracker.core.Ledger
import com.moneytracker.core.OdometerEntry
import com.moneytracker.core.RecurringKind
import com.moneytracker.core.RecurringSpec
import com.moneytracker.core.Tx
import com.moneytracker.core.TxType
import com.moneytracker.core.VehicleSpec
import com.moneytracker.core.WalletInfo
import com.moneytracker.core.WalletType
import java.time.LocalDate
import java.time.YearMonth

/**
 * Potret seluruh data (immutable). Semua layar membaca dari sini sehingga
 * setiap modul memakai angka yang sama persis (satu sumber kebenaran).
 */
class Snapshot(
    val user: UserEntity?,
    val wallets: List<WalletEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val vehicles: List<VehicleEntity>,
    val fuelLogs: List<FuelLogEntity>,
    val odometers: List<OdometerLogEntity>,
    val budgets: List<BudgetEntity>,
    val goals: List<SavingsGoalEntity>,
    val debts: List<DebtEntity>,
    val recurring: List<RecurringEntity>,
    val assets: List<AssetEntity>,
    val attachments: List<AttachmentEntity>,
) {
    val walletMap: Map<Long, WalletEntity> = wallets.associateBy { it.id }
    val categoryMap: Map<Long, CategoryEntity> = categories.associateBy { it.id }
    val vehicleMap: Map<Long, VehicleEntity> = vehicles.associateBy { it.id }
    val goalMap: Map<Long, SavingsGoalEntity> = goals.associateBy { it.id }
    val debtMap: Map<Long, DebtEntity> = debts.associateBy { it.id }

    val catInfo: Map<Long, CategoryInfo> = categories.associate { it.id to CategoryInfo(it.id, it.name, it.parentId, it.kind, it.nature) }
    val txCore: List<Tx> = transactions.map { it.toCore() }
    private val txById: Map<Long, Tx> = txCore.associateBy { it.id }
    val walletInfos: List<WalletInfo> = wallets.map { WalletInfo(it.id, it.name, it.type, it.initialBalance, it.createdAt) }
    val balances: Map<Long, Long> = Ledger.balances(walletInfos, txCore)
    val fuelTxIds: Set<Long> = fuelLogs.mapNotNull { it.transactionId }.toSet()
    val attachmentsByTx: Map<Long, List<AttachmentEntity>> = attachments.groupBy { it.transactionId }

    fun balance(walletId: Long): Long = balances[walletId] ?: 0
    fun core(tx: TransactionEntity): Tx = txById[tx.id] ?: tx.toCore()

    val activeWallets: List<WalletEntity> get() = wallets.filter { !it.archived }

    /** Wallet yang dihitung dalam "Total saldo" (bukan kartu kredit). */
    val countedWallets: List<WalletEntity> by lazy { wallets.filter { it.includeInTotal && !it.type.isLiability } }

    /** Total seluruh saldo (aset likuid): wallet non-kartu-kredit. */
    val totalBalance: Long by lazy { countedWallets.sumOf { balance(it.id) } }

    /** Dana yang dialokasikan ke target tabungan tanpa wallet tujuan (mis. celengan). */
    val offWalletSavings: Long by lazy {
        transactions.sumOf { t ->
            when {
                t.type == TxType.SAVINGS_ALLOCATION && t.toWalletId == null -> t.amount
                else -> 0L
            }
        }
    }

    /** Total tabungan = saldo rekening bertipe Tabungan + dana tabungan di luar wallet. */
    val totalSavings: Long by lazy {
        wallets.filter { it.type == WalletType.SAVINGS }.sumOf { balance(it.id) } + offWalletSavings
    }

    fun debtStatus(d: DebtEntity): DebtStatus = DebtCalc.status(d.direction, d.principal, txCore.filter { it.debtId == d.id })

    val payables: Long by lazy { debts.filter { it.direction == DebtDirection.PAYABLE }.sumOf { debtStatus(it).remaining } }
    val receivables: Long by lazy { debts.filter { it.direction == DebtDirection.RECEIVABLE }.sumOf { debtStatus(it).remaining } }

    val netWorth: Ledger.NetWorth by lazy {
        Ledger.netWorth(
            balances = wallets.filter { it.includeInTotal || it.type.isLiability }.map { balance(it.id) },
            otherAssets = assets.sumOf { it.value } + offWalletSavings,
            receivables = receivables,
            payables = payables,
        )
    }

    // ---------- Kategori ----------
    fun categoryIdsByKey(key: String): Set<Long> {
        val roots = categories.filter { it.systemKey == key }.map { it.id }
        return roots.flatMap { Ledger.descendantsOf(it, catInfo.values) }.toSet()
    }

    val fuelCategoryIds: Set<Long> by lazy { categoryIdsByKey("fuel") }
    val foodCategoryIds: Set<Long> by lazy { categoryIdsByKey("food") }

    fun rootCategory(id: Long?): CategoryEntity? = Ledger.rootOf(id, catInfo)?.let { categoryMap[it] }

    fun categoryLabel(id: Long?): String {
        val c = id?.let { categoryMap[it] } ?: return "Tanpa kategori"
        val p = c.parentId?.let { categoryMap[it] }
        return if (p != null) "${p.name} › ${c.name}" else c.name
    }

    fun roots(kind: CategoryKind) = categories.filter { it.kind == kind && it.parentId == null && !it.archived }
    fun children(parentId: Long) = categories.filter { it.parentId == parentId && !it.archived }

    // ---------- Anggaran ----------
    fun budgetSpent(b: BudgetEntity, month: YearMonth): Long {
        val (from, to) = Ledger.monthRange(month)
        return if (b.isSavings) {
            txCore.filter { it.dateTime in from until to }.sumOf { Ledger.savingsOf(it) }
        } else {
            val ids = b.categoryId?.let { Ledger.descendantsOf(it, catInfo.values) } ?: emptySet()
            txCore.filter { it.type == TxType.EXPENSE && it.dateTime in from until to && it.categoryId in ids }.sumOf { it.amount }
        }
    }

    fun budgetStatus(b: BudgetEntity, month: YearMonth): BudgetStatus {
        val start = runCatching { YearMonth.parse(b.startMonth) }.getOrDefault(month)
        return BudgetCalc.status(BudgetSpec(b.id, b.amount, b.rollover, start), month) { budgetSpent(b, it) }
    }

    fun budgetName(b: BudgetEntity): String = if (b.isSavings) "Tabungan" else categoryMap[b.categoryId]?.name ?: "Kategori dihapus"

    // ---------- Tabungan ----------
    fun goalContributions(goalId: Long): List<Pair<LocalDate, Long>> = transactions.filter { it.goalId == goalId }.mapNotNull {
        when (it.type) {
            TxType.SAVINGS_ALLOCATION -> Fmt.toDate(it.dateTime) to it.amount
            TxType.TRANSFER -> Fmt.toDate(it.dateTime) to -it.amount // penarikan dana target
            else -> null
        }
    }

    fun goalProgress(g: SavingsGoalEntity, today: LocalDate = LocalDate.now(Fmt.zone)): GoalProgress =
        GoalCalc.progress(g.target, g.initialAmount, goalContributions(g.id), g.deadline?.let(LocalDate::ofEpochDay), g.monthlyTarget, today)

    // ---------- Kendaraan ----------
    fun fuelLogsOf(vehicleId: Long) = fuelLogs.filter { it.vehicleId == vehicleId }

    fun fuelReport(v: VehicleEntity, today: LocalDate = LocalDate.now(Fmt.zone)): FuelReport = FuelAnalytics.analyze(
        fuelLogsOf(v.id).map { FuelEntry(it.id, it.dateTime, it.odometerKm, it.volumeMl, it.totalCost, it.pricePerLiter, it.fuelType, it.fullTank) },
        VehicleSpec(v.tankCapacityMl, v.refKmPerLiter),
        odometers.filter { it.vehicleId == v.id }.map { OdometerEntry(it.dateTime, it.odometerKm) },
        today,
    )

    fun lastOdometer(vehicleId: Long): Long? {
        val a = fuelLogsOf(vehicleId).maxOfOrNull { it.odometerKm }
        val b = odometers.filter { it.vehicleId == vehicleId }.maxOfOrNull { it.odometerKm }
        return listOfNotNull(a, b, vehicleMap[vehicleId]?.initialOdometer).maxOrNull()
    }

    // ---------- Berulang ----------
    val recurringSpecs: List<RecurringSpec> by lazy {
        recurring.map {
            RecurringSpec(
                it.id, it.name, it.amount, it.kind.isIncome,
                it.kind == RecurringKind.SUBSCRIPTION, it.period, it.interval, LocalDate.ofEpochDay(it.nextDate), it.anchorDay, it.active,
            )
        }
    }

    val isEmpty: Boolean get() = wallets.isEmpty() && transactions.isEmpty()
}

fun TransactionEntity.toCore() = Tx(
    id = id, type = type, amount = amount, fee = fee, walletId = walletId, toWalletId = toWalletId, categoryId = categoryId,
    goalId = goalId, debtId = debtId, vehicleId = vehicleId, recurringId = recurringId, dateTime = dateTime, description = description,
)
