package com.exptracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exptracker.data.ExpenseDatabase
import com.exptracker.data.SimpleExpense
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var isPermissionGranted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isPermissionGranted = isNotificationListenerEnabled(context)
    }

    if (!isPermissionGranted) {
        PermissionScreen(context)
    } else {
        CalendarScreen(context)
    }
}

// ─── 권한 미허용 화면 ───────────────────────────────────────────────────────────

@Composable
private fun PermissionScreen(context: Context) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("지출 추적기", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "알림 접근 권한: 미허용",
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) {
            Text("알림 접근 권한 설정하기")
        }
    }
}

// ─── 캘린더 메인 화면 ──────────────────────────────────────────────────────────

@Composable
private fun CalendarScreen(context: Context) {
    val dao = remember { ExpenseDatabase.getDatabase(context).expenseDao() }
    val scope = rememberCoroutineScope()

    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var expenseMap by remember { mutableStateOf<Map<String, List<SimpleExpense>>>(emptyMap()) }
    var selectedDate by remember { mutableStateOf<String?>(null) }

    // 월이 바뀔 때마다 DB 조회
    LaunchedEffect(currentMonth) {
        val yearMonth = currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val list = dao.getByMonth(yearMonth)
        expenseMap = list.groupBy { it.date }
        selectedDate = null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 월 헤더
        MonthHeader(
            month = currentMonth,
            onPrev = { currentMonth = currentMonth.minusMonths(1) },
            onNext = { currentMonth = currentMonth.plusMonths(1) }
        )

        // 월 총합
        val monthTotal = expenseMap.values.flatten().sumOf { it.amount }
        Text(
            text = "이번 달 총액: %,d원".format(monthTotal),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        // 요일 헤더
        DayOfWeekHeader()

        // 날짜 그리드
        CalendarGrid(
            yearMonth = currentMonth,
            expenseMap = expenseMap,
            selectedDate = selectedDate,
            onDateClick = { date -> selectedDate = if (selectedDate == date) null else date }
        )

        // 선택된 날 지출 목록
        selectedDate?.let { date ->
            Divider(modifier = Modifier.padding(top = 8.dp))
            DayExpenseList(date = date, expenses = expenseMap[date] ?: emptyList())
        }
    }
}

// ─── 월 헤더 ──────────────────────────────────────────────────────────────────

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "이전 달")
        }
        Text(
            text = month.format(DateTimeFormatter.ofPattern("yyyy년 M월")),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = "다음 달")
        }
    }
}

// ─── 요일 헤더 ────────────────────────────────────────────────────────────────

@Composable
private fun DayOfWeekHeader() {
    val days = listOf("일", "월", "화", "수", "목", "금", "토")
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        days.forEachIndexed { i, d ->
            Text(
                text = d,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = when (i) {
                    0 -> Color(0xFFE53935)
                    6 -> Color(0xFF1E88E5)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

// ─── 날짜 그리드 ──────────────────────────────────────────────────────────────

@Composable
private fun CalendarGrid(
    yearMonth: YearMonth,
    expenseMap: Map<String, List<SimpleExpense>>,
    selectedDate: String?,
    onDateClick: (String) -> Unit
) {
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value % 7  // 일=0, 월=1...
    val daysInMonth = yearMonth.lengthOfMonth()
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    val cells = firstDayOfWeek + daysInMonth
    val rows = (cells + 6) / 7

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    val day = cellIndex - firstDayOfWeek + 1

                    if (day < 1 || day > daysInMonth) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val dateStr = "%s-%02d".format(
                            yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")), day
                        )
                        val dayExpenses = expenseMap[dateStr]
                        val dayTotal = dayExpenses?.sumOf { it.amount } ?: 0
                        val isSelected = selectedDate == dateStr
                        val isToday = dateStr == today

                        DayCell(
                            day = day,
                            dayTotal = dayTotal,
                            isToday = isToday,
                            isSelected = isSelected,
                            dayOfWeek = col,
                            onClick = { onDateClick(dateStr) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    dayTotal: Int,
    isToday: Boolean,
    isSelected: Boolean,
    dayOfWeek: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = when {
        dayOfWeek == 0 -> Color(0xFFE53935)
        dayOfWeek == 6 -> Color(0xFF1E88E5)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                else if (isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                fontSize = 13.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            if (dayTotal > 0) {
                Text(
                    text = "%,d".format(dayTotal),
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    lineHeight = 9.sp
                )
            }
        }
    }
}

// ─── 선택된 날 지출 목록 ──────────────────────────────────────────────────────

@Composable
private fun DayExpenseList(date: String, expenses: List<SimpleExpense>) {
    val dayTotal = expenses.sumOf { it.amount }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "합계: %,d원".format(dayTotal),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))

        if (expenses.isEmpty()) {
            Text("내역 없음", color = Color.Gray, fontSize = 13.sp)
        } else {
            LazyColumn {
                items(expenses) { expense ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${expense.time}  ${expense.vendor}",
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "%,d원".format(expense.amount),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Divider(color = Color.LightGray, thickness = 0.5.dp)
                }
            }
        }
    }
}

// ─── 권한 체크 ────────────────────────────────────────────────────────────────

fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return enabled?.contains(context.packageName) == true
}
