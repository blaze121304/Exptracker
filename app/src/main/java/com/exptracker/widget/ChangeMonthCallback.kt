package com.exptracker.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class ChangeMonthCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val delta = parameters[DELTA_PARAM]?.toIntOrNull() ?: return
        val ymFmt   = DateTimeFormatter.ofPattern("yyyy-MM")
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val now   = LocalDate.now()
        val nowYm = YearMonth.of(now.year, now.month)

        updateAppWidgetState(context, glanceId) { prefs: MutablePreferences ->
            val currentStr = prefs[DISPLAYED_MONTH_KEY] ?: nowYm.format(ymFmt)
            val next = YearMonth.parse(currentStr).plusMonths(delta.toLong())
            if (next > nowYm) return@updateAppWidgetState   // 미래 이동 불가
            prefs[DISPLAYED_MONTH_KEY] = next.format(ymFmt)
            prefs[SelectDateCallback.SELECTED_DATE_KEY] =
                if (next == nowYm) now.format(dateFmt) else next.atEndOfMonth().format(dateFmt)
        }
        ExpenseWidget().update(context, glanceId)
    }

    companion object {
        val DELTA_PARAM         = ActionParameters.Key<String>("month_delta")
        val DISPLAYED_MONTH_KEY = stringPreferencesKey("displayed_month")
    }
}
