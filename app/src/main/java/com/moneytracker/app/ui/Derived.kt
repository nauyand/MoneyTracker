package com.moneytracker.app.ui

import com.moneytracker.app.data.Snapshot
import com.moneytracker.core.BudgetAlert
import com.moneytracker.core.Fmt
import com.moneytracker.core.Insight
import com.moneytracker.core.Insights
import java.time.LocalDate
import java.time.YearMonth

fun today(): LocalDate = LocalDate.now(Fmt.zone)
fun thisMonth(): YearMonth = YearMonth.from(today())

fun Snapshot.budgetAlerts(month: YearMonth = thisMonth()): List<BudgetAlert> =
    budgets.map { BudgetAlert(budgetName(it), budgetStatus(it, month)) }

/** Insight asisten keuangan — dihitung dari data aktual snapshot. */
fun Snapshot.insights(): List<Insight> = Insights.generate(
    txs = txCore,
    categories = catInfo,
    liquidBalance = totalBalance,
    recurring = recurringSpecs,
    budgetAlerts = budgetAlerts(),
    fuelCategoryIds = fuelCategoryIds,
    foodCategoryIds = foodCategoryIds,
    today = today(),
).sortedBy { it.severity.ordinal * -1 }
