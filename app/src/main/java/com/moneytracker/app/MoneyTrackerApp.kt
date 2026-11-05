package com.moneytracker.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.moneytracker.app.data.AppDatabase
import com.moneytracker.app.data.BackupManager
import com.moneytracker.app.data.ExportManager
import com.moneytracker.app.data.Repository
import com.moneytracker.app.security.SecurityManager
import com.moneytracker.app.work.DailyWorker
import com.moneytracker.app.work.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Kontainer dependensi sederhana (tanpa framework DI). */
class MoneyTrackerApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var security: SecurityManager; private set
    lateinit var database: AppDatabase; private set
    lateinit var repository: Repository; private set
    lateinit var backup: BackupManager; private set
    lateinit var exporter: ExportManager; private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        security = SecurityManager(this)
        database = AppDatabase.create(this, security.databasePassphrase())
        repository = Repository(database, this, security, appScope)
        backup = BackupManager(this, database)
        exporter = ExportManager(this)

        appScope.launch {
            repository.ensureSeeded()
            repository.processDueRecurring()
        }
        createChannels()
        Reminders.schedule(this)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyWorker>(12, TimeUnit.HOURS).build(),
        )
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REMINDERS, "Pengingat jatuh tempo", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_BUDGET, "Peringatan anggaran", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_RECORD, "Pengingat pencatatan", NotificationManager.IMPORTANCE_HIGH))
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_BUDGET = "budget"
        const val CHANNEL_RECORD = "record_reminder"
        lateinit var instance: MoneyTrackerApp; private set
    }
}
