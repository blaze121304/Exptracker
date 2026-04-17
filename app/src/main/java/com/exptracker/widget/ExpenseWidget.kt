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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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

// ── 색상 팔레트 (Figma: Clean Calendar) ────────────────────────────────────
private val Purple       = Color(0xFF3F15EA)
private val PurpleLight  = Color(0xFFEDE9FC)
private val BgCalendar   = Color(0xFFF4F4F4)
private val BgWhite      = Color(0xFFFFFFFF)
private val TextPrimary  = Color(0xFF000000)
private val TextDim      = Color(0xFF999999)   // 요일 헤더: rgba(0,0,0,0.4) ≈ #666
private val TextGhost    = Color(0xFFBBBBBB)   // 이전/다음달 날짜

// ── 지출 금액 티어 (라이트 테마) ────────────────────────────────────────────
private data class Tier(val bg: Color, val selectedBg: Color, val fg: Color)
private fun tier(amount: Int): Tier? = when {
    amount == 0       -> Tier(Color(0xFFE8F5E9), Color(0xFF2E7D32), Color(0xFF2E7D32))
    amount <= 10_000  -> Tier(Color(0xFFE3F2FD), Color(0xFF1565C0), Color(0xFF1565C0))
    amount <= 30_000  -> Tier(Color(0xFFFFF8E1), Color(0xFFF9A825), Color(0xFFF57F17))
    amount <= 50_000  -> Tier(Color(0xFFFFF3E0), Color(0xFFE65100), Color(0xFFE65100))
    amount <= 100_000 -> Tier(Color(0xFFFFEBEE), Color(0xFFC62828), Color(0xFFC62828))
    else              -> Tier(Color(0xFFFCE4EC), Color(0xFF880E4F), Color(0xFF880E4F))
}

// ── 달력 그리드 메타 ───────────────────────────────────────────────────────
private data class GridMeta(
    val ym: YearMonth,
    val firstDayOffset: Int, // 일요일 시작: 일=0…토=6
    val daysInMonth: Int,
    val totalRows: Int,
    val ymFmtStr: String    // "yyyy-MM" formatted
)
private fun gridMeta(today: LocalDate): GridMeta {
    val ym = YearMonth.of(today.year, today.month)
    val offset = ym.atDay(1).dayOfWeek.value % 7
    val days = ym.lengthOfMonth()
    val rows = (offset + days + 6) / 7
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM")
    return GridMeta(ym, offset, days, rows, ym.format(fmt))
}

class ExpenseWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = ExpenseDatabase.getDatabase(context).expenseDao()
        val now = LocalDate.now()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val todayStr = now.format(fmt)

        val billingDay = context
            .getSharedPreferences("exptracker_prefs", Context.MODE_PRIVATE)
            .getInt("billing_day", 10)
        val cycleStart = if (now.dayOfMonth >= billingDay)
            now.withDayOfMonth(billingDay)
        else
            now.minusMonths(1).withDayOfMonth(billingDay)
        val cycleEnd = cycleStart.plusMonths(1).minusDays(1)
        val cycleTotal = dao.getTotalInRange(cycleStart.format(fmt), cycleEnd.format(fmt))

        val yearMonthStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val monthExpenses = dao.getByMonth(yearMonthStr)
        val dailyMap = monthExpenses.groupBy { it.date }
        val dailyTotals = dailyMap.mapValues { e -> e.value.sumOf { it.amount } }

        provideContent {
            val prefs = currentState<Preferences>()
            val selectedDate = prefs[SelectDateCallback.SELECTED_DATE_KEY] ?: todayStr
            val selectedExpenses = dailyMap[selectedDate] ?: emptyList()
            val selectedTotal = selectedExpenses.sumOf { it.amount }

            WidgetContent(
                today          = now,
                todayStr       = todayStr,
                selectedDate   = selectedDate,
                dailyTotals    = dailyTotals,
                cycleTotal     = cycleTotal,
                selectedExpenses = selectedExpenses,
                selectedTotal  = selectedTotal,
                billingDay     = billingDay
            )
        }
    }
}

// ─── 루트 레이아웃 ─────────────────────────────────────────────────────────────
// 비율: 캘린더 상단(1) + 캘린더 하단(1) + 결제내역(1) = 2:1

@Composable
private fun WidgetContent(
    today: LocalDate,
    todayStr: String,
    selectedDate: String,
    dailyTotals: Map<String, Int>,
    cycleTotal: Int,
    selectedExpenses: List<SimpleExpense>,
    selectedTotal: Int,
    billingDay: Int
) {
    val meta = gridMeta(today)
    val splitRow = 3 // 캘린더 상단: 0~2행, 하단: 3~(totalRows-1)행

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(BgCalendar))
    ) {
        // ── 캘린더 상단: 월 헤더 + 요일 + 0~2행 ─────────────────────────────
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .background(ColorProvider(BgCalendar))
                .padding(horizontal = 8.dp)
        ) {
            // 월/년 헤더 (Figma 7:106 스타일: 큰 Bold 텍스트)
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = today.format(DateTimeFormatter.ofPattern("yyyy년 M월")),
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = ColorProvider(TextPrimary),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "결제일 ${billingDay}일  %,d원".format(cycleTotal),
                    style = TextStyle(
                        color = ColorProvider(Purple),
                        fontSize = 11.sp
                    )
                )
            }

            // 요일 헤더 (Figma: rgba(0,0,0,0.4) 회색)
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEach { label ->
                    Text(
                        text = label,
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(
                            color = ColorProvider(TextDim),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }

            // 달력 0~2행
            Column(modifier = GlanceModifier.fillMaxSize()) {
                val endRow = minOf(splitRow - 1, meta.totalRows - 1)
                repeat(endRow + 1) { row ->
                    CalendarRow(
                        row          = row,
                        meta         = meta,
                        todayStr     = todayStr,
                        selectedDate = selectedDate,
                        dailyTotals  = dailyTotals,
                        modifier     = GlanceModifier.fillMaxWidth().defaultWeight()
                    )
                }
            }
        }

        // ── 캘린더 하단: 3행 이후 ──────────────────────────────────────────
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .background(ColorProvider(BgCalendar))
                .padding(horizontal = 8.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                if (meta.totalRows > splitRow) {
                    repeat(meta.totalRows - splitRow) { i ->
                        CalendarRow(
                            row          = splitRow + i,
                            meta         = meta,
                            todayStr     = todayStr,
                            selectedDate = selectedDate,
                            dailyTotals  = dailyTotals,
                            modifier     = GlanceModifier.fillMaxWidth().defaultWeight()
                        )
                    }
                }
            }
        }

        // ── 결제내역 ───────────────────────────────────────────────────────
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .background(ColorProvider(BgWhite))
        ) {
            // 날짜 헤더
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedDate.substring(5).replace("-", "월 ") + "일",
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = ColorProvider(TextPrimary),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "%,d원".format(selectedTotal),
                    style = TextStyle(
                        color = ColorProvider(Purple),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            if (selectedExpenses.isEmpty()) {
                Text(
                    text = "지출 내역 없음",
                    modifier = GlanceModifier.padding(horizontal = 12.dp),
                    style = TextStyle(color = ColorProvider(TextDim), fontSize = 12.sp)
                )
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(selectedExpenses) { expense -> ExpenseRow(expense) }
                }
            }
        }
    }
}

// ─── 달력 한 행 ───────────────────────────────────────────────────────────────

@Composable
private fun CalendarRow(
    row: Int,
    meta: GridMeta,
    todayStr: String,
    selectedDate: String,
    dailyTotals: Map<String, Int>,
    modifier: GlanceModifier = GlanceModifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(7) { col ->
            val day = row * 7 + col - meta.firstDayOffset + 1
            if (day < 1 || day > meta.daysInMonth) {
                Column(modifier = GlanceModifier.defaultWeight()) {}
                return@repeat
            }

            val dateStr = "%s-%02d".format(meta.ymFmtStr, day)
            val dayTotal = dailyTotals[dateStr] ?: 0
            val isToday    = dateStr == todayStr
            val isSelected = dateStr == selectedDate
            val isPastOrToday = dateStr <= todayStr
            val t = if (isPastOrToday) tier(dayTotal) else null

            val cellBg: Color = when {
                isSelected   -> Purple
                isToday      -> PurpleLight
                t != null    -> t.bg
                else         -> BgCalendar
            }
            val textColor: Color = when {
                isSelected   -> Color.White
                isToday      -> Purple
                t != null    -> t.fg
                else         -> TextPrimary
            }

            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .background(ColorProvider(cellBg))
                    .cornerRadius(10.dp)
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
                        color = ColorProvider(textColor),
                        fontSize = 14.sp,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                )
                // 지출이 있는 날: 금액 축약 표시 (작은 글씨)
                if (t != null && dayTotal > 0) {
                    Text(
                        text = amountShort(dayTotal),
                        style = TextStyle(
                            color = ColorProvider(if (isSelected) Color.White else t.fg),
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                }
                // 지출 0원 날: 절약 메시지 (작은 글씨)
                if (t != null && dayTotal == 0) {
                    Text(
                        text = zeroDayMessage(dateStr),
                        style = TextStyle(
                            color = ColorProvider(if (isSelected) Color.White else t.fg),
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center
                        )
                    )
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
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = expense.time,
            style = TextStyle(color = ColorProvider(TextDim), fontSize = 10.sp)
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = if (expense.cardName.isNotEmpty()) "[${expense.cardName}] ${expense.vendor}" else expense.vendor,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 11.sp)
        )
        Text(
            text = "%,d원".format(expense.amount),
            style = TextStyle(
                color = ColorProvider(TextPrimary),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

// ─── 유틸 ─────────────────────────────────────────────────────────────────────

private fun amountShort(amount: Int): String = when {
    amount >= 100_000 -> "%d만".format(amount / 10_000)
    amount >= 10_000  -> "%.1f만".format(amount / 10_000.0)
    else              -> "%,d".format(amount)
}

private fun zeroDayMessage(dateStr: String): String {
    val msgs = listOf("Good!", "짠돌이!", "부자!", "빌딩!", "절약왕!", "통장+!", "인내!", "대박!")
    val seed = dateStr.replace("-", "").toLongOrNull() ?: 0L
    return msgs[(seed % msgs.size).toInt()]
}
