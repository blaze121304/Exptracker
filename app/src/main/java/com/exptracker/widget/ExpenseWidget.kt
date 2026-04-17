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
private val TextPrimary  = Color(0xFF000000)
private val TextDim      = Color(0xFF999999)   // 요일 헤더: rgba(0,0,0,0.4) ≈ #666

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

        val prefsName = if (com.exptracker.BuildConfig.DEBUG) "exptracker_prefs_test" else "exptracker_prefs"
        val billingDay = context
            .getSharedPreferences(prefsName, Context.MODE_PRIVATE)
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
// 6등분(defaultWeight×6): 헤더1 + 달력3(요일+행0~1 / 행2~3 / 행4~5) + 결제내역2(날짜헤더 / 목록)

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

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(BgCalendar))
    ) {

        // ── 1/6: 헤더 (Figma "September, 2022" 구역 — 년월 + 결제정보) ──────
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = today.format(DateTimeFormatter.ofPattern("yyyy년 M월")),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = ColorProvider(TextPrimary),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "결제일 ${billingDay}일",
                    style = TextStyle(color = ColorProvider(Purple), fontSize = 10.sp)
                )
                Text(
                    text = "%,d원".format(cycleTotal),
                    style = TextStyle(
                        color = ColorProvider(Purple),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // ── 2/6: 달력 — 요일헤더 + 행 0~1 ───────────────────────────────────
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .padding(horizontal = 8.dp)
        ) {
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
            CalendarRow(0, meta, todayStr, selectedDate, dailyTotals, GlanceModifier.fillMaxWidth().defaultWeight())
            CalendarRow(1, meta, todayStr, selectedDate, dailyTotals, GlanceModifier.fillMaxWidth().defaultWeight())
        }

        // ── 3/6: 달력 — 행 2~3 ───────────────────────────────────────────────
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .padding(horizontal = 8.dp)
        ) {
            CalendarRow(2, meta, todayStr, selectedDate, dailyTotals, GlanceModifier.fillMaxWidth().defaultWeight())
            CalendarRow(3, meta, todayStr, selectedDate, dailyTotals, GlanceModifier.fillMaxWidth().defaultWeight())
        }

        // ── 4/6: 달력 — 행 4~5 ───────────────────────────────────────────────
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .padding(horizontal = 8.dp)
        ) {
            CalendarRow(4, meta, todayStr, selectedDate, dailyTotals, GlanceModifier.fillMaxWidth().defaultWeight())
            CalendarRow(5, meta, todayStr, selectedDate, dailyTotals, GlanceModifier.fillMaxWidth().defaultWeight())
        }

        // ── 5/6: 결제내역 — 날짜 헤더 (Figma "20 September 2022" 스타일) ──────
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .background(ColorProvider(BgCalendar))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = run {
                    val parts = selectedDate.split("-")
                    "${parts[1].trimStart('0')}월 ${parts[2].trimStart('0')}일"
                },
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = ColorProvider(TextPrimary),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal
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

        // ── 6/6: 결제내역 — 목록 (Figma 이벤트 카드 스타일) ─────────────────
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .background(ColorProvider(BgCalendar))
        ) {
            if (selectedExpenses.isEmpty()) {
                Text(
                    text = "지출 내역 없음",
                    modifier = GlanceModifier.padding(horizontal = 16.dp),
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
                    .cornerRadius(14.dp)
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

// ─── 지출 카드 (Figma 이벤트 카드: 20dp 라운드, 금액 티어별 색상) ───────────

@Composable
private fun ExpenseRow(expense: SimpleExpense) {
    // 피그마 팔레트: blue(≤1만) / yellow(≤3만) / red(≤5만) / rose(≤10만) / pink(초과)
    val cardBg: Color
    val cardFg: Color
    when {
        expense.amount <= 10_000  -> { cardBg = Color(0xFFBDD0FC); cardFg = Purple }
        expense.amount <= 30_000  -> { cardBg = Color(0xFFFFF0BD); cardFg = Color(0xFFB07800) }
        expense.amount <= 50_000  -> { cardBg = Color(0xFFFCC8BD); cardFg = Color(0xFFEA1515) }
        expense.amount <= 100_000 -> { cardBg = Color(0xFFFFB8D0); cardFg = Color(0xFFCF1669) }
        else                      -> { cardBg = Color(0xFFFCBDF2); cardFg = Color(0xFFEA15BB) }
    }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .background(ColorProvider(cardBg))
            .cornerRadius(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = expense.time,
            modifier = GlanceModifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
            style = TextStyle(color = ColorProvider(cardFg), fontSize = 10.sp)
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = expense.vendor,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = ColorProvider(cardFg), fontSize = 12.sp)
        )
        Text(
            text = "%,d원".format(expense.amount),
            modifier = GlanceModifier.padding(end = 14.dp),
            style = TextStyle(
                color = ColorProvider(cardFg),
                fontSize = 12.sp,
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
