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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.exptracker.MainActivity
import com.exptracker.data.ExpenseDatabase
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class ExpenseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = ExpenseDatabase.getDatabase(context).expenseDao()
        val now = LocalDate.now()
        val yearMonthStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM"))

        val expenses = dao.getByMonth(yearMonthStr)
        // date → 일별 합계
        val dailyTotals = expenses.groupBy { it.date }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val monthTotal = expenses.sumOf { it.amount }

        provideContent {
            WidgetContent(
                today = now,
                dailyTotals = dailyTotals,
                monthTotal = monthTotal
            )
        }
    }
}

// ─── 위젯 루트 ────────────────────────────────────────────────────────────────

@Composable
private fun WidgetContent(
    today: LocalDate,
    dailyTotals: Map<String, Int>,
    monthTotal: Int
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color.White))
            .padding(8.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        // 헤더: 월 + 총액
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)
        ) {
            Text(
                text = today.format(DateTimeFormatter.ofPattern("yyyy년 M월")),
                style = TextStyle(
                    color = ColorProvider(Color(0xFF1A1A1A)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "%,d원".format(monthTotal),
                style = TextStyle(
                    color = ColorProvider(Color(0xFF1976D2)),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        // 요일 헤더
        DayOfWeekRow()

        // 날짜 그리드
        CalendarGrid(today = today, dailyTotals = dailyTotals)
    }
}

// ─── 요일 헤더 ────────────────────────────────────────────────────────────────

@Composable
private fun DayOfWeekRow() {
    val days = listOf("일" to Color(0xFFE53935), "월" to Color(0xFF555555),
        "화" to Color(0xFF555555), "수" to Color(0xFF555555),
        "목" to Color(0xFF555555), "금" to Color(0xFF555555),
        "토" to Color(0xFF1E88E5))

    Row(modifier = GlanceModifier.fillMaxWidth()) {
        days.forEach { (label, color) ->
            Text(
                text = label,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = ColorProvider(color),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

// ─── 날짜 그리드 ──────────────────────────────────────────────────────────────

@Composable
private fun CalendarGrid(today: LocalDate, dailyTotals: Map<String, Int>) {
    val yearMonth = YearMonth.of(today.year, today.month)
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value % 7  // 일=0
    val daysInMonth = yearMonth.lengthOfMonth()
    val totalCells = firstDayOfWeek + daysInMonth
    val rows = (totalCells + 6) / 7

    val fmt = DateTimeFormatter.ofPattern("yyyy-MM")
    val todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        repeat(rows) { row ->
            Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    val day = cellIndex - firstDayOfWeek + 1

                    if (day < 1 || day > daysInMonth) {
                        Box(modifier = GlanceModifier.defaultWeight()) { }
                    } else {
                        val dateStr = "%s-%02d".format(yearMonth.format(fmt), day)
                        val dayTotal = dailyTotals[dateStr] ?: 0
                        val isToday = dateStr == todayStr
                        val textColor = when {
                            isToday -> Color(0xFF1976D2)
                            col == 0 -> Color(0xFFE53935)
                            col == 6 -> Color(0xFF1E88E5)
                            else -> Color(0xFF333333)
                        }

                        Column(
                            modifier = GlanceModifier.defaultWeight(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = day.toString(),
                                style = TextStyle(
                                    color = ColorProvider(textColor),
                                    fontSize = if (isToday) 11.sp else 10.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )
                            )
                            if (dayTotal > 0) {
                                Text(
                                    text = amountShort(dayTotal),
                                    style = TextStyle(
                                        color = ColorProvider(Color(0xFF1976D2)),
                                        fontSize = 7.sp,
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 위젯 셀이 좁으므로 금액을 축약 표시: 12,500 → 1.2만 / 150,000 → 15만 */
private fun amountShort(amount: Int): String = when {
    amount >= 100_000 -> "%d만".format(amount / 10_000)
    amount >= 10_000  -> "%.1f만".format(amount / 10_000.0)
    else              -> "%,d".format(amount)
}
