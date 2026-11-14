package com.moneytracker.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.moneytracker.app.MainActivity
import com.moneytracker.app.R

/**
 * Widget layar utama "Ketik cepat": kotak ketik palsu + tombol mikrofon. Keduanya membuka
 * [SmartAddActivity] di atas wallpaper (keyboard langsung muncul, atau langsung input suara).
 */
class SmartAddWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_smart_add).apply {
            setOnClickPendingIntent(R.id.widget_type, SmartAddActivity.pendingIntent(context, voice = false))
            setOnClickPendingIntent(R.id.widget_mic, SmartAddActivity.pendingIntent(context, voice = true))
            setOnClickPendingIntent(
                R.id.widget_open,
                PendingIntent.getActivity(
                    context, 98,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }
}
