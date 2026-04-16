package com.exptracker.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExpenseWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = ExpenseWidget()

    companion object {
        // Called from ExpenseNotificationService after each DB insert
        fun updateWidget(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                val manager = GlanceAppWidgetManager(context)
                val ids = manager.getGlanceIds(ExpenseWidget::class.java)
                ids.forEach { id -> ExpenseWidget().update(context, id) }
            }
        }
    }
}
