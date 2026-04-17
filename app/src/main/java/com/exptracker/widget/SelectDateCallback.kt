package com.exptracker.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

class SelectDateCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val date = parameters[DATE_PARAM] ?: return
        // MutablePreferences 직접 수정하는 심플 오버로드 사용
        updateAppWidgetState(context, glanceId) { prefs: MutablePreferences ->
            prefs[SELECTED_DATE_KEY] = date
        }
        ExpenseWidget().update(context, glanceId)
    }

    companion object {
        val DATE_PARAM = ActionParameters.Key<String>("date")
        val SELECTED_DATE_KEY = stringPreferencesKey("selected_date")
    }
}
