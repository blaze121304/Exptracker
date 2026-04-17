package com.exptracker.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.exptracker.data.ExpenseDatabase
import com.exptracker.data.SimpleExpense
import com.exptracker.widget.ExpenseWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ExpenseNotificationService : NotificationListenerService() {

    // SupervisorJob: 개별 코루틴 실패가 다른 코루틴에 영향 안 줌
    // onListenerDisconnected에서 cancel → 서비스 종료 시 누수 없음
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    // 카드사 파서 목록 — 새 카드 추가 시 여기에만 추가
    private val parsers: List<CardParser> = listOf(
        LotteCardParser()
    )

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        job.cancel()  // 서비스 연결 해제 시 진행 중인 코루틴 모두 정리
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title   = extras.getString(Notification.EXTRA_TITLE).orEmpty()
        val text    = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        Log.d(TAG, "── Notification ──────────────────────")
        Log.d(TAG, "pkg   : ${sbn.packageName}")
        Log.d(TAG, "title : $title")
        Log.d(TAG, "text  : $text")
        Log.d(TAG, "big   : $bigText")

        val parser = parsers.firstOrNull { it.canParse(title, sbn.packageName) } ?: return

        val body = bigText.ifBlank { text }
        val parsed = parser.parse(title, body) ?: return

        Log.d(TAG, "Parsed → ${parsed.vendor}  ${parsed.amount}원  ${parsed.date} ${parsed.time}")

        scope.launch {
            val dao = ExpenseDatabase.getDatabase(applicationContext).expenseDao()
            dao.insert(
                SimpleExpense(
                    amount = parsed.amount,
                    vendor = parsed.vendor,
                    date   = parsed.date,
                    time   = parsed.time
                )
            )
            Log.d(TAG, "Saved to DB ✓")
            ExpenseWidgetReceiver.updateWidget(applicationContext)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit

    companion object {
        private const val TAG = "ExpTracker"
    }
}
