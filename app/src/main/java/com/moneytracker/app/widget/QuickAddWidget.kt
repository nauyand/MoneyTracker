package com.moneytracker.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.moneytracker.app.MainActivity
import com.moneytracker.app.R
import com.moneytracker.core.TxType

/**
 * Widget layar utama "Catat cepat": tombol Pengeluaran & Pemasukan membuka [QuickAddActivity]
 * langsung di atas wallpaper. Widget tidak menampilkan saldo agar aman dilihat orang lain.
 */
class QuickAddWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = views(context)
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    private fun views(context: Context) = RemoteViews(context.packageName, R.layout.widget_quick_add).apply {
        setOnClickPendingIntent(R.id.widget_expense, QuickAddActivity.pendingIntent(context, TxType.EXPENSE))
        setOnClickPendingIntent(R.id.widget_income, QuickAddActivity.pendingIntent(context, TxType.INCOME))
        setOnClickPendingIntent(
            R.id.widget_open,
            PendingIntent.getActivity(
                context, 99,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }
}
