package com.moneytracker.app.work

import com.moneytracker.core.Streaks
import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moneytracker.app.MainActivity
import com.moneytracker.app.MoneyTrackerApp
import com.moneytracker.app.R
import com.moneytracker.app.data.toCore
import com.moneytracker.core.Fmt
import com.moneytracker.core.Ledger
import com.moneytracker.core.ReminderConfig
import com.moneytracker.core.ReminderKind
import com.moneytracker.core.ReminderSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Pengingat pencatatan (harian pada jam tertentu & bulanan pada tanggal tertentu).
 * Memakai AlarmManager (inexact, tetap jalan saat Doze) — tidak butuh izin exact alarm.
 * Hanya satu alarm aktif: alarm untuk pengingat terdekat; setelah berbunyi, alarm berikutnya dijadwalkan.
 */
object Reminders {
    const val EXTRA_OPEN = "open"
    const val OPEN_NEW_TX = "tx_new"
    const val OPEN_WALLETS = "wallets"
    const val OPEN_WRAPPED = "wrapped"
    private const val EXTRA_KINDS = "kinds"
    private const val EXTRA_AT = "at"
    private const val PREFS = "mt_reminders"

    fun load(ctx: Context): ReminderConfig {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ReminderConfig(
            dailyEnabled = p.getBoolean("daily_enabled", false),
            dailyTimes = ReminderSchedule.parseTimes(p.getString("daily_times", "12:00,20:00") ?: ""),
            monthlyEnabled = p.getBoolean("monthly_enabled", false),
            monthlyDays = ReminderSchedule.parseDays(p.getString("monthly_days", "1,25") ?: ""),
            monthlyTime = runCatching { LocalTime.parse(p.getString("monthly_time", "09:00")) }.getOrDefault(LocalTime.of(9, 0)),
            skipIfRecorded = p.getBoolean("skip_if_recorded", true),
            streakEnabled = p.getBoolean("streak_enabled", true),
            streakTime = runCatching { LocalTime.parse(p.getString("streak_time", "20:30")) }.getOrDefault(LocalTime.of(20, 30)),
        )
    }

    fun save(ctx: Context, c: ReminderConfig) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("daily_enabled", c.dailyEnabled)
            .putString("daily_times", ReminderSchedule.formatTimes(c.dailyTimes))
            .putBoolean("monthly_enabled", c.monthlyEnabled)
            .putString("monthly_days", c.monthlyDays.sorted().joinToString(","))
            .putString("monthly_time", "%02d:%02d".format(c.monthlyTime.hour, c.monthlyTime.minute))
            .putBoolean("skip_if_recorded", c.skipIfRecorded)
            .putBoolean("streak_enabled", c.streakEnabled)
            .putString("streak_time", "%02d:%02d".format(c.streakTime.hour, c.streakTime.minute))
            .apply()
        schedule(ctx, c)
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        alarmManager(ctx).cancel(pendingIntent(ctx, null))
    }

    private fun alarmManager(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(ctx: Context, extras: Intent?): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).apply { extras?.let { putExtras(it) } }
        return PendingIntent.getBroadcast(ctx, 7001, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Menjadwalkan (ulang) alarm untuk pengingat terdekat. */
    fun schedule(ctx: Context, c: ReminderConfig = load(ctx)) {
        val am = alarmManager(ctx)
        val next = ReminderSchedule.next(LocalDateTime.now(Fmt.zone), c)
        if (next == null) { am.cancel(pendingIntent(ctx, null)); return }
        val extras = Intent()
            .putExtra(EXTRA_KINDS, next.kinds.joinToString(",") { it.name })
            .putExtra(EXTRA_AT, Fmt.toMillis(next.at))
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, Fmt.toMillis(next.at), pendingIntent(ctx, extras))
    }

    fun next(ctx: Context) = ReminderSchedule.next(LocalDateTime.now(Fmt.zone), load(ctx))

    /** Isi & tampilkan notifikasi. [kinds] menentukan pesannya. */
    suspend fun fire(ctx: Context, kinds: Set<ReminderKind>, at: LocalDateTime, test: Boolean = false) {
        val app = ctx.applicationContext as MoneyTrackerApp
        val c = load(ctx)
        val dao = app.repository.dao
        if (ReminderKind.MONTHLY in kinds) {
            notify(
                ctx, 9101, "📅 Tanggal ${at.dayOfMonth}: waktunya cek keuangan",
                "Catat transaksi yang terlewat dan cocokkan saldo wallet dengan aplikasi bank/e-wallet (Rekonsiliasi).",
                OPEN_WALLETS, "Buka wallet",
            )
        }
        if (ReminderKind.STREAK in kinds) {
            val days = dao.recordTimestampsSince(Fmt.toMillis(at.toLocalDate().minusDays(400))).map { Fmt.toDate(it) }.toSet()
            val st = Streaks.compute(days, at.toLocalDate())
            if (test || st.atRisk) notify(
                ctx, 9102, "🔥 Streak ${st.current} hari hampir putus!",
                "Belum ada catatan hari ini. Catat satu transaksi sebelum tengah malam agar streak-mu terus menyala.",
                OPEN_NEW_TX, "Catat sekarang",
            )
        }
        if (ReminderKind.DAILY in kinds) {
            val since = Fmt.toMillis(ReminderSchedule.windowStart(at, c.dailyTimes))
            val recorded = dao.countCreatedSince(since)
            if (test || !c.skipIfRecorded || recorded == 0) {
                val (todayFrom, todayTo) = Ledger.dayRange(at.toLocalDate())
                val today = dao.transactionsDatedBetween(todayFrom, todayTo)
                val spent = today.sumOf { Ledger.expenseOf(it.toCore()) }
                val body = if (today.isEmpty()) "Belum ada transaksi hari ini. Catat pengeluaranmu selagi masih ingat ✍️"
                else "Hari ini tercatat ${today.size} transaksi (keluar ${Fmt.rupiah(spent)}). Ada yang belum dicatat?"
                notify(ctx, 9100, "💸 Sudah catat pengeluaran?", body, OPEN_NEW_TX, "Catat sekarang")
            }
        }
    }

    fun notify(ctx: Context, id: Int, title: String, text: String, open: String, actionLabel: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, open)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, MoneyTrackerApp.CHANNEL_RECORD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .addAction(0, actionLabel, pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    internal fun kindsFrom(i: Intent): Set<ReminderKind> =
        i.getStringExtra(EXTRA_KINDS)?.split(',')?.mapNotNull { runCatching { ReminderKind.valueOf(it) }.getOrNull() }?.toSet() ?: emptySet()

    internal fun atFrom(i: Intent): LocalDateTime =
        i.getLongExtra(EXTRA_AT, 0L).takeIf { it > 0 }?.let { Fmt.toDateTime(it) } ?: LocalDateTime.now(Fmt.zone)
}

/** Menerima alarm pengingat, menampilkan notifikasi, lalu menjadwalkan alarm berikutnya. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Reminders.fire(context, Reminders.kindsFrom(intent), Reminders.atFrom(intent))
            } finally {
                Reminders.schedule(context)
                pending.finish()
            }
        }
    }
}

/** Menjadwalkan ulang pengingat setelah HP restart, jam/zona waktu diubah, atau aplikasi diperbarui. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Reminders.schedule(context)
}
