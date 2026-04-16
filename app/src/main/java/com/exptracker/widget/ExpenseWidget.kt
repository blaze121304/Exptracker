package com.exptracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.exptracker.MainActivity
import com.exptracker.data.ExpenseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Step 3: Glance widget — text-only, clicks open MainActivity
class ExpenseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = ExpenseDatabase.getDatabase(context).expenseDao()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val todayTotal = dao.getTodayTotal(today)
        val recentLines = dao.getRecentByDate(today)
            .map { "${it.vendor}: ${"%,d".format(it.amount)}원" }

        provideContent {
            WidgetContent(todayTotal = todayTotal, recentLines = recentLines)
        }
    }
}

@Composable
private fun WidgetContent(todayTotal: Int, recentLines: List<String>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color.White))
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        // Header — today's total
        Text(
            text = "오늘 총액: ${"%,d".format(todayTotal)}원",
            style = TextStyle(
                color = ColorProvider(Color(0xFF1A1A1A)),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )

        // Recent 3 entries
        if (recentLines.isEmpty()) {
            Text(
                text = "최근 내역 없음",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF888888)),
                    fontSize = 12.sp
                )
            )
        } else {
            recentLines.forEach { line ->
                Text(
                    text = line,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF444444)),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}
