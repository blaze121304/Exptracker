package com.exptracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.exptracker.data.ExpenseDatabase
import com.exptracker.data.SimpleExpense
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class ExpenseWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = ExpenseDatabase.getDatabase(context).expenseDao()
        val now = LocalDate.now()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val todayStr = now.format(fmt)

        // ── 결제일 기준 청구 기간 계산 ──────────────────────────────────────
        // 오늘 >= 10일 → 이번달 10일 ~ 다음달 9일
        // 오늘 < 10일  → 지난달 10일 ~ 이번달 9일
        val billingDay = 10
        val cycleStart = if (now.dayOfMonth >= billingDay)
            now.withDayOfMonth(billingDay)
        else
            now.minusMonths(1).withDayOfMonth(billingDay)
        val cycleEnd = cycleStart.plusMonths(1).minusDays(1)

        val cycleStartStr = cycleStart.format(fmt)
        val cycleEndStr   = cycleEnd.format(fmt)
        val cycleTotal = dao.getTotalInRange(cycleStartStr, cycleEndStr)

        // ── 캘린더 표시용 이번달 데이터 ─────────────────────────────────────
        val yearMonthStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val monthExpenses = dao.getByMonth(yearMonthStr)
        val dailyExpenses = monthExpenses.groupBy { it.date }
        val dailyTotals   = dailyExpenses.mapValues { e -> e.value.sumOf { it.amount } }

        provideContent {
            val prefs = currentState<Preferences>()
            val selectedDate = prefs[SelectDateCallback.SELECTED_DATE_KEY] ?: todayStr
            val selectedExpenses = dailyExpenses[selectedDate] ?: emptyList()
            val selectedTotal = selectedExpenses.sumOf { it.amount }

            WidgetContent(
                today = now,
                todayStr = todayStr,
                selectedDate = selectedDate,
                dailyTotals = dailyTotals,
                cycleTotal = cycleTotal,
                selectedExpenses = selectedExpenses,
                selectedTotal = selectedTotal
            )
        }
    }
}

// ─── 루트 레이아웃 ── 상단 50% 캘린더 / 하단 50% 내역 ──────────────────────────

@Composable
private fun WidgetContent(
    today: LocalDate,
    todayStr: String,
    selectedDate: String,
    dailyTotals: Map<String, Int>,
    cycleTotal: Int,
    selectedExpenses: List<SimpleExpense>,
    selectedTotal: Int
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFFFAFAFA)))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // ── 월 헤더 (고정) ────────────────────────────────────────────────────
        Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(
                text = today.format(DateTimeFormatter.ofPattern("yyyy년 M월")) + "(결제일 : 10일)",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = ColorProvider(Color(0xFF111111)),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "총 %,d원 결제".format(cycleTotal),
                style = TextStyle(
                    color = ColorProvider(Color(0xFF1565C0)),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        // ── 캘린더 영역 (상단 절반) ───────────────────────────────────────────
        Column(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight()
        ) {
            DayOfWeekRow()
            CalendarGrid(
                today = today,
                todayStr = todayStr,
                selectedDate = selectedDate,
                dailyTotals = dailyTotals,
                modifier = GlanceModifier.fillMaxSize()
            )
        }

        // ── 지출 내역 영역 (하단 절반) ────────────────────────────────────────
        Column(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight()
        ) {
            // 선택된 날짜 헤더 바
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(Color(0xFF1565C0)))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = selectedDate.substring(5).replace("-", "/"),
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "%,d원".format(selectedTotal),
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // 스크롤 가능한 지출 목록
            if (selectedExpenses.isEmpty()) {
                Text(
                    text = "지출 내역 없음",
                    modifier = GlanceModifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF999999)),
                        fontSize = 13.sp
                    )
                )
            } else {
                LazyColumn(
                    modifier = GlanceModifier.fillMaxSize()
                ) {
                    items(selectedExpenses) { expense ->
                        ExpenseRow(expense)
                    }
                }
            }
        }
    }
}

// ─── 요일 헤더 ────────────────────────────────────────────────────────────────

@Composable
private fun DayOfWeekRow() {
    val days = listOf(
        "일" to Color(0xFFD32F2F), "월" to Color(0xFF444444),
        "화" to Color(0xFF444444), "수" to Color(0xFF444444),
        "목" to Color(0xFF444444), "금" to Color(0xFF444444),
        "토" to Color(0xFF1565C0)
    )
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
        days.forEach { (label, color) ->
            Text(
                text = label,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = ColorProvider(color),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

// ─── 달력 그리드 ──────────────────────────────────────────────────────────────

@Composable
private fun CalendarGrid(
    today: LocalDate,
    todayStr: String,
    selectedDate: String,
    dailyTotals: Map<String, Int>,
    modifier: GlanceModifier = GlanceModifier
) {
    val yearMonth = YearMonth.of(today.year, today.month)
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value % 7
    val daysInMonth = yearMonth.lengthOfMonth()
    val rows = (firstDayOfWeek + daysInMonth + 6) / 7
    val ymFmt = DateTimeFormatter.ofPattern("yyyy-MM")

    // fillMaxSize + 각 Row가 defaultWeight → 남은 높이를 주(週) 수로 균등 분배
    Column(modifier = modifier) {
        repeat(rows) { row ->
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(7) { col ->
                    val day = row * 7 + col - firstDayOfWeek + 1
                    if (day < 1 || day > daysInMonth) {
                        Column(modifier = GlanceModifier.defaultWeight()) {}
                        return@repeat
                    }

                    val dateStr = "%s-%02d".format(yearMonth.format(ymFmt), day)
                    val dayTotal = dailyTotals[dateStr] ?: 0
                    val isToday = dateStr == todayStr
                    val isSelected = dateStr == selectedDate
                    val isPastOrToday = dateStr <= todayStr

                    val textColor = when (col) {
                        0 -> Color(0xFFD32F2F)
                        6 -> Color(0xFF1565C0)
                        else -> Color(0xFF333333)
                    }

                    // 연한 배경(bg) / 진한 배경(selectedBg) / 텍스트(fg) 쌍
                    data class DayColors(val bg: Color, val selectedBg: Color, val fg: Color)
                    val dayColors: DayColors? = if (isPastOrToday) when {
                        dayTotal == 0        -> DayColors(Color(0xFFD7F5DC), Color(0xFF2E7D32), Color(0xFF2E7D32))
                        dayTotal <= 10_000   -> DayColors(Color(0xFFD0E8FF), Color(0xFF1565C0), Color(0xFF1565C0))
                        dayTotal <= 30_000   -> DayColors(Color(0xFFFFF8D6), Color(0xFFF57F17), Color(0xFFF57F17))
                        dayTotal <= 50_000   -> DayColors(Color(0xFFFFE0B2), Color(0xFFE65100), Color(0xFFE65100))
                        dayTotal <= 100_000  -> DayColors(Color(0xFFFFE0E0), Color(0xFFC62828), Color(0xFFC62828))
                        else                 -> DayColors(Color(0xFFEF9A9A), Color(0xFF7F0000), Color(0xFF7F0000))
                    } else null

                    // 선택 시 → 진한 배경 / 미선택 → 연한 배경 / 데이터 없음 → 배경 없음
                    val activeBg = when {
                        isSelected && dayColors != null -> dayColors.selectedBg
                        dayColors != null               -> dayColors.bg
                        else                            -> null
                    }

                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .then(
                                if (activeBg != null) GlanceModifier.background(ColorProvider(activeBg))
                                else GlanceModifier
                            )
                            .padding(vertical = 2.dp)
                            .clickable(
                                actionRunCallback<SelectDateCallback>(
                                    actionParametersOf(SelectDateCallback.DATE_PARAM to dateStr)
                                )
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = day.toString(),
                            style = TextStyle(
                                color = ColorProvider(
                                    when {
                                        isSelected && dayColors != null -> Color(0xFFFFFFFF) // 진한 배경 위 흰 글씨
                                        dayColors != null -> dayColors.fg
                                        else -> textColor
                                    }
                                ),
                                fontSize = 18.sp,
                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        )
                        // 과거/오늘 날짜는 항상 두 번째 줄 렌더링 → 셀 높이 통일
                        if (dayColors != null) {
                            val zeroMsg = zeroDayMessage(dateStr)
                            Text(
                                text = if (dayTotal > 0) amountShort(dayTotal) else zeroMsg,
                                style = TextStyle(
                                    color = ColorProvider(
                                        when {
                                            isSelected -> Color(0xFFEEEEEE)
                                            dayTotal == 0 -> Color(0xFFE53935) // 빨간색
                                            else -> dayColors.fg
                                        }
                                    ),
                                    fontSize = 10.sp,
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

// ─── 지출 행 ─────────────────────────────────────────────────────────────────

@Composable
private fun ExpenseRow(expense: SimpleExpense) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = expense.time,
            style = TextStyle(color = ColorProvider(Color(0xFF888888)), fontSize = 11.sp)
        )
        androidx.glance.layout.Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = expense.vendor,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = ColorProvider(Color(0xFF222222)), fontSize = 12.sp)
        )
        Text(
            text = "%,d원".format(expense.amount),
            style = TextStyle(
                color = ColorProvider(Color(0xFF111111)),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

// ─── 금액 축약 ────────────────────────────────────────────────────────────────

private fun amountShort(amount: Int): String = when {
    amount >= 100_000 -> "%d만".format(amount / 10_000)
    amount >= 10_000  -> "%.1f만".format(amount / 10_000.0)
    else              -> "%,d".format(amount)
}

private fun zeroDayMessage(dateStr: String): String {
    val messages = listOf("Good!", "짠돌이!", "부자되겠어!", "빌딩사자!", "절약왕!", "통장부자!", "참을인!", "대단해!")
    val seed = dateStr.replace("-", "").toLongOrNull() ?: 0L
    return messages[(seed % messages.size).toInt()]
}
