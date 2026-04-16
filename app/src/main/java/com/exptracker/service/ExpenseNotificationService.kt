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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Step 1: NotificationListenerService skeleton
// Step 2: Saves parsed expense to Room DB
// Step 3: Triggers widget update after save
class ExpenseNotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    // Step 1: Keywords that identify a payment notification (hard-coded per PRD)
    private val PAYMENT_KEYWORDS = listOf("승인", "결제", "원")

    // Step 1: Regex — extracts amount like "15,000원" or "15000원"
    private val AMOUNT_REGEX = Regex("""(\d[\d,]+)원""")

    // Step 1: Regex — captures vendor text immediately before the amount
    private val VENDOR_REGEX = Regex("""([가-힣a-zA-Z0-9\s()]+?)\s+(\d[\d,]+)원""")

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE).orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        val fullText = "$title $text $bigText".trim()

        // Step 1: Logcat dump — verify raw notification text
        Log.d(TAG, "── Notification ─────────────────────────")
        Log.d(TAG, "pkg   : ${sbn.packageName}")
        Log.d(TAG, "title : $title")
        Log.d(TAG, "text  : $text")
        Log.d(TAG, "big   : $bigText")

        val isPayment = PAYMENT_KEYWORDS.any { fullText.contains(it) }
        if (!isPayment) return

        Log.d(TAG, ">>> Payment notification detected!")
        parseAndSave(fullText)
    }

    // Step 2: Parse -> Save to Room
    private fun parseAndSave(text: String) {
        val amountStr = AMOUNT_REGEX.find(text)
            ?.groupValues?.get(1)
            ?.replace(",", "")
            ?: run { Log.w(TAG, "Amount not found in: $text"); return }

        val amount = amountStr.toIntOrNull()
            ?: run { Log.w(TAG, "Amount parse failed: $amountStr"); return }

        val vendor = VENDOR_REGEX.find(text)
            ?.groupValues?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "알수없음"

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        Log.d(TAG, "Parsed → vendor=$vendor  amount=$amount  date=$today")

        scope.launch {
            val dao = ExpenseDatabase.getDatabase(applicationContext).expenseDao()
            dao.insert(SimpleExpense(amount = amount, vendor = vendor, date = today))
            Log.d(TAG, "Saved to DB ✓")

            // Step 3: Push widget update immediately after save
            ExpenseWidgetReceiver.updateWidget(applicationContext)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit

    companion object {
        private const val TAG = "ExpTracker"
    }
}
