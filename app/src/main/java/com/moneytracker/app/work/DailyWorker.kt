package com.moneytracker.app.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moneytracker.app.MainActivity
import com.moneytracker.app.MoneyTrackerApp
import com.moneytracker.app.R
import com.moneytracker.core.BudgetStatus
import com.moneytracker.core.Fmt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

/**
 * Berjalan ±2x sehari:
 *  1. Mencatat transaksi berulang yang diset otomatis.
 *  2. Mengingatkan tagihan/langganan/cicilan yang mendekati jatuh tempo.
 *  3. Memberi peringatan anggaran (sekali per kondisi per bulan).
 */
class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MoneyTrackerApp
        val repo = app.repository
        val posted = repo.processDueRecurring()
        val s = repo.snapshot.filterNotNull().first()
        val today = LocalDate.now(Fmt.zone)
        val notified = app.security.notifiedKeys.toMutableSet()
        var n = 100

        if (posted > 0) notify(app, MoneyTrackerApp.CHANNEL_REMINDERS, n++, "Transaksi rutin dicatat", "$posted transaksi berulang otomatis dicatat.")

        for (r in s.recurring.filter { it.active }) {
            val due = LocalDate.ofEpochDay(r.nextDate)
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
            val key = "rec_${r.id}_${r.nextDate}"
            if (days in 0..r.reminderDays.toLong() && key !in notified && !r.autoPost) {
                notify(app, MoneyTrackerApp.CHANNEL_REMINDERS, n++, "${r.name} jatuh tempo ${if (days == 0L) "hari ini" else "dalam $days hari"}",
                    "${Fmt.rupiah(r.amount)} · ${Fmt.date(due)}")
                notified += key
            }
        }
        for (d in s.debts.filter { !it.archived && it.dueDate != null }) {
            val st = s.debtStatus(d)
            if (st.settled) continue
            val due = LocalDate.ofEpochDay(d.dueDate!!)
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
            val key = "debt_${d.id}_${d.dueDate}"
            if (days in -1..3 && key !in notified) {
                notify(app, MoneyTrackerApp.CHANNEL_REMINDERS, n++, "${d.direction.label} ${d.name} jatuh tempo ${Fmt.dateShort(due)}",
                    "Sisa ${Fmt.rupiah(st.remaining)}" + (d.installmentAmount?.let { " · cicilan ${Fmt.rupiah(it)}" } ?: ""))
                notified += key
            }
        }
        val month = YearMonth.from(today)
        for (b in s.budgets) {
            val st = s.budgetStatus(b, month)
            if (st.level == BudgetStatus.Level.OK) continue
            val key = "budget_${b.id}_${month}_${st.level}"
            if (key in notified) continue
            val name = s.budgetName(b)
            if (st.level == BudgetStatus.Level.OVER) notify(app, MoneyTrackerApp.CHANNEL_BUDGET, n++, "Anggaran $name terlampaui", "Terpakai ${Fmt.rupiah(st.spent)} dari ${Fmt.rupiah(st.available)}")
            else notify(app, MoneyTrackerApp.CHANNEL_BUDGET, n++, "Anggaran $name hampir habis", "${Fmt.percent(st.usage)} terpakai, sisa ${Fmt.rupiah(st.remaining)}")
            notified += key
        }
        // Rekap bulan lalu siap (tanggal 1–3)
        val prev = month.minusMonths(1)
        val wrapKey = "wrap_$prev"
        if (today.dayOfMonth <= 3 && wrapKey !in notified && s.transactions.any { YearMonth.from(Fmt.toDate(it.dateTime)) == prev }) {
            val w = s.wrap(prev, today)
            Reminders.notify(
                app, 9103, "✨ Rekap ${Fmt.month(prev)} sudah siap!",
                "Total pengeluaran ${Fmt.rupiah(w.expense)}" + (w.fuelFills.takeIf { it > 0 }?.let { " · BBM ${Fmt.rupiah(w.fuelCost)}" } ?: "") + ". Lihat hari paling hemat & pengeluaran terbesarmu.",
                Reminders.OPEN_WRAPPED, "Lihat rekap",
            )
            notified += wrapKey
        }
        app.security.notifiedKeys = notified
        return Result.success()
    }

    private fun notify(ctx: Context, channel: String, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, notif)
    }
}
