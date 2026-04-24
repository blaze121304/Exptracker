package com.exptracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.exptracker.data.ExpenseDatabase
import com.exptracker.data.SimpleExpense
import com.exptracker.data.VendorTotal
import com.exptracker.widget.ExpenseWidget
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    val scope = rememberCoroutineScope()
    val fmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    var isRunning by remember { mutableStateOf(false) }
    val prefsName = if (com.exptracker.BuildConfig.DEBUG) "exptracker_prefs_test" else "exptracker_prefs"
    val prefs = remember { context.getSharedPreferences(prefsName, Context.MODE_PRIVATE) }
    var billingDay by remember { mutableStateOf(prefs.getInt("billing_day", 10)) }
    var saved by remember { mutableStateOf(true) }
    var rankings by remember { mutableStateOf<List<VendorTotal>?>(null) }
    var cycleTotal by remember { mutableStateOf<Int?>(null) }
    var statsLoading by remember { mutableStateOf(false) }
    var cyclePeriod by remember { mutableStateOf("") }

    // ── 이스터에그 ────────────────────────────────────────────────────────────
    val easterTarget1 = listOf("H","H","F","F","L","R","L","R")
    val easterTarget2 = listOf("H","H","F","F","FL","FR","FL","FR")
    val easterSeq = remember { mutableStateListOf<String>() }
    var showEasterEgg1 by remember { mutableStateOf(false) }
    var showEasterEgg2 by remember { mutableStateOf(false) }
    var isTestDb by remember { mutableStateOf(ExpenseDatabase.isTestDb(context)) }
    val easterTimeoutJob = remember { mutableStateOf<Job?>(null) }
    fun easterTap(key: String) {
        if (easterSeq.isEmpty()) {
            easterTimeoutJob.value?.cancel()
            easterTimeoutJob.value = scope.launch {
                delay(10_000)
                easterSeq.clear()
            }
        }
        easterSeq.add(key)
        if (easterSeq.size > 8) easterSeq.removeAt(0)
        val seq = easterSeq.toList()
        when {
            seq == easterTarget1 -> { easterTimeoutJob.value?.cancel(); showEasterEgg1 = true; easterSeq.clear() }
            seq == easterTarget2 -> {
                easterTimeoutJob.value?.cancel()
                isTestDb = ExpenseDatabase.toggleTestDb(context)
                showEasterEgg2 = true
                rankings = null; cycleTotal = null
                easterSeq.clear()
            }
        }
    }

    LaunchedEffect(Unit) {
        isRunning = isNotificationListenerEnabled(context)
//        // ── [PRD 실데이터 1회 삽입 — 확인 후 이 블록 전체 삭제] ──────────────
//        val seedPrefs = context.getSharedPreferences("exptracker_seed", Context.MODE_PRIVATE)
//        if (!seedPrefs.getBoolean("prd_real_seeded", false) && !ExpenseDatabase.isTestDb(context)) {
//            val dao = ExpenseDatabase.getDatabase(context).expenseDao()
//            listOf(
//                SimpleExpense(amount =  9320,  vendor = "세븐일레븐 금천롯데캐슬3차점",  date = "2026-04-11", cardName = "롯데카드"),
//                SimpleExpense(amount = 17770,  vendor = "Aliexpress.com",              date = "2026-04-12", cardName = "롯데카드"),
//                SimpleExpense(amount = 18400,  vendor = "쿠팡(쿠페이)",                 date = "2026-04-12", cardName = "롯데카드"),
//                SimpleExpense(amount =  1000,  vendor = "세븐일레븐 역삼태광점",         date = "2026-04-13", cardName = "롯데카드"),
//                SimpleExpense(amount = 14400,  vendor = "버거킹 금천구청입구삼거리점",    date = "2026-04-13", cardName = "롯데카드"),
//                SimpleExpense(amount =  4000,  vendor = "우정사업본부(우체국)",           date = "2026-04-13", cardName = "롯데카드"),
//                SimpleExpense(amount = 25832,  vendor = "네이버페이",                   date = "2026-04-13", cardName = "롯데카드"),
//                SimpleExpense(amount = 29475,  vendor = "네이버페이",                   date = "2026-04-13", cardName = "롯데카드"),
//                SimpleExpense(amount = 10300,  vendor = "네이버페이",                   date = "2026-04-13", cardName = "롯데카드"),
//                SimpleExpense(amount =  3700,  vendor = "씨유(CU)역삼타운점",           date = "2026-04-13", cardName = "롯데카드"),
//                SimpleExpense(amount =  8500,  vendor = "세븐일레븐 역삼태광점",         date = "2026-04-14", cardName = "롯데카드"),
//                SimpleExpense(amount =  4500,  vendor = "세븐일레븐 역삼태광점",         date = "2026-04-14", cardName = "롯데카드"),
//                SimpleExpense(amount = 38543,  vendor = "네이버페이",                   date = "2026-04-14", cardName = "롯데카드"),
//                SimpleExpense(amount =  4200,  vendor = "세븐일레븐 역삼태광점",         date = "2026-04-15", cardName = "롯데카드"),
//                SimpleExpense(amount = 16600,  vendor = "세븐일레븐 금천롯데캐슬3차점",  date = "2026-04-15", cardName = "롯데카드"),
//                SimpleExpense(amount = 30000,  vendor = "네이버페이",                   date = "2026-04-15", cardName = "롯데카드"),
//                SimpleExpense(amount =  3500,  vendor = "씨유 시흥베르빌점",            date = "2026-04-15", cardName = "롯데카드"),
//                SimpleExpense(amount =  2300,  vendor = "세븐일레븐 역삼태광점",         date = "2026-04-16", cardName = "롯데카드"),
//                SimpleExpense(amount =  7890,  vendor = "쿠팡(와우 멤버십)",             date = "2026-04-16", cardName = "롯데카드"),
//                SimpleExpense(amount =  2500,  vendor = "세븐일레븐 역삼태광점",         date = "2026-04-17", cardName = "롯데카드"),
//                SimpleExpense(amount = 37538,  vendor = "Aliexpress.com",              date = "2026-04-17", cardName = "롯데카드"),
//                SimpleExpense(amount = 11300,  vendor = "지에스25 역삼센트럴점",         date = "2026-04-17", cardName = "롯데카드"),
//                SimpleExpense(amount =  4900,  vendor = "지에스25 역삼혜성점",           date = "2026-04-17", cardName = "롯데카드"),
//                SimpleExpense(amount =  1500,  vendor = "GS25 독산골드파크3차점",        date = "2026-04-17", cardName = "롯데카드"),
//                SimpleExpense(amount =  2300,  vendor = "(주)지에스네트웍스",            date = "2026-04-17", cardName = "롯데카드"),
//                SimpleExpense(amount = 16350,  vendor = "Aliexpress.com",              date = "2026-04-18", cardName = "롯데카드"),
//                SimpleExpense(amount = 12493,  vendor = "Aliexpress.com",              date = "2026-04-19", cardName = "롯데카드"),
//                SimpleExpense(amount =  7000,  vendor = "세븐일레븐 역삼태광점",         date = "2026-04-20", cardName = "롯데카드"),
//                SimpleExpense(amount =  6600,  vendor = "세븐일레븐 역삼태광점",         date = "2026-04-20", cardName = "롯데카드"),
//                SimpleExpense(amount = 53250,  vendor = "Aliexpress.com",              date = "2026-04-20", cardName = "롯데카드"),
//                SimpleExpense(amount =  4000,  vendor = "우정사업본부(우체국)",           date = "2026-04-20", cardName = "롯데카드"),
//            ).forEach { dao.insert(it) }
//            seedPrefs.edit().putBoolean("prd_real_seeded", true).apply()
//        }
//        // ── [여기까지 삭제] ────────────────────────────────────────────────────
    }

    fun saveAndRefresh() {
        prefs.edit().putInt("billing_day", billingDay).apply()
        saved = true
        rankings = null
        cycleTotal = null
        scope.launch {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(ExpenseWidget::class.java)
            ids.forEach { ExpenseWidget().update(context, it) }
        }
    }

    fun loadStats() {
        scope.launch {
            statsLoading = true
            val dao = ExpenseDatabase.getDatabase(context).expenseDao()
            val now = LocalDate.now()
            val cycleStart = if (now.dayOfMonth >= billingDay) now.withDayOfMonth(billingDay)
                             else now.minusMonths(1).withDayOfMonth(billingDay)
            val cycleEnd = cycleStart.plusMonths(1).minusDays(1)
            val labelFmt = DateTimeFormatter.ofPattern("yyyy년 M월 d일")
            cyclePeriod = "${cycleStart.format(labelFmt)}~${cycleEnd.format(labelFmt)}"
            rankings = dao.getVendorRankings(cycleStart.format(fmt), cycleEnd.format(fmt))
            cycleTotal = dao.getTotalInRange(cycleStart.format(fmt), cycleEnd.format(fmt))
            statsLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── 헤더 ─────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 타이틀 행: 좌측 빈공간 / 텍스트 / 우측 빈공간 각각 클릭 가능
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.weight(1f).height(32.dp)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { easterTap("L") })
                Text(
                    text = "지출 추적기",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { easterTap("H") }
                )
                Box(modifier = Modifier.weight(1f).height(32.dp)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { easterTap("R") })
            }
            if (!isRunning) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("알림 접근 권한이 필요합니다", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                    Text("권한 설정하기")
                }
            }
        }

        HorizontalDivider()

        // ── 메인 콘텐츠 ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.Top
        ) {

            // 결제일 설정 (compact)
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("결제일", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { if (billingDay > 1) { billingDay--; saved = false } },
                        modifier = Modifier.size(36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("−", fontSize = 16.sp) }
                    Text(
                        text = "${billingDay}일",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    OutlinedButton(
                        onClick = { if (billingDay < 28) { billingDay++; saved = false } },
                        modifier = Modifier.size(36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("+", fontSize = 16.sp) }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { saveAndRefresh() },
                        enabled = !saved,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) { Text(if (saved) "저장됨" else "저장", fontSize = 13.sp) }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 통계 확인 버튼
            OutlinedButton(
                onClick = { loadStats() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !statsLoading
            ) {
                Text(if (statsLoading) "불러오는 중..." else "통계 확인")
            }

            // 통계 결과
            rankings?.let { list ->
                Spacer(modifier = Modifier.height(16.dp))

                // ── 1. 지출 순위 ──────────────────────────────────────────────
                Text("이번 결제기간 지출 순위", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(cyclePeriod, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(10.dp))
                if (list.isEmpty()) {
                    Text("데이터 없음", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                } else {
                    list.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                modifier = Modifier.width(30.dp),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (index) {
                                    0 -> Color(0xFFFFB300)
                                    1 -> Color(0xFF90A4AE)
                                    2 -> Color(0xFFBF8970)
                                    else -> MaterialTheme.colorScheme.outline
                                }
                            )
                            Text(item.vendor, modifier = Modifier.weight(1f), fontSize = 14.sp)
                            Text("%,d원".format(item.total), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        if (index < list.lastIndex) HorizontalDivider()
                    }
                }

                // ── 2. 지출 평가 ──────────────────────────────────────────────
                cycleTotal?.let { total ->
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("이번 달 지출 평가", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    val (msg, msgColor) = when {
                        total < 100_000  -> "부자되시겠어요! 이 기세 유지합시다!" to Color(0xFF2E7D32)
                        total <= 250_000 -> "적절합니다! 이렇게 유지합시다." to Color(0xFF1565C0)
                        total <= 500_000 -> "슬슬 과소비 냄새가 나는데요?" to Color(0xFFF57F17)
                        else             -> "지갑이 홀쭉해요! 개선이 필요합니다!" to Color(0xFFC62828)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = msgColor.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "총 %,d원".format(total),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = msgColor
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = msg, fontSize = 14.sp, color = msgColor)
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // ── 푸터 ─────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.weight(1f).height(20.dp)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { easterTap("FL") })
            Text(
                text = "made by rusty",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { easterTap("F") }
            )
            Box(modifier = Modifier.weight(1f).height(20.dp)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { easterTap("FR") })
        }
    }

    // ── 이스터에그 다이얼로그 ─────────────────────────────────────────────────
    if (showEasterEgg1) {
        AlertDialog(
            onDismissRequest = { showEasterEgg1 = false },
            title = { Text("!") },
            text = { Text("이건 코나미 커맨드인데?") },
            confirmButton = {
                TextButton(onClick = { showEasterEgg1 = false }) { Text("확인") }
            }
        )
    }
    if (showEasterEgg2) {
        AlertDialog(
            onDismissRequest = { showEasterEgg2 = false },
            title = { Text(if (isTestDb) "TEST DB 전환됨" else "PRD DB 전환됨") },
            text = { Text(if (isTestDb) "TEST DB로 전환됐습니다." else "PRD DB로 원복됐습니다.") },
            confirmButton = {
                TextButton(onClick = { showEasterEgg2 = false }) { Text("확인") }
            }
        )
    }
}

private fun prefsName() = if (com.exptracker.BuildConfig.DEBUG) "exptracker_prefs_test" else "exptracker_prefs"

private suspend fun seedTestData(context: Context) {
    val dao = ExpenseDatabase.getDatabase(context).expenseDao()
    val entries = listOf(
        // ── 3월 (이전 결제 주기) ──────────────────────────────────────────
        SimpleExpense(amount =   6_500, vendor = "스타벅스",        date = "2026-03-05", time = "08:32", cardName = "롯데카드"),
        SimpleExpense(amount =  23_000, vendor = "배달의민족",       date = "2026-03-07", time = "19:14", cardName = "롯데카드"),
        SimpleExpense(amount = 156_000, vendor = "이마트",           date = "2026-03-09", time = "15:40", cardName = "롯데카드"),
        SimpleExpense(amount = 680_000, vendor = "쿠팡",             date = "2026-03-12", time = "10:05", cardName = "롯데카드"),
        SimpleExpense(amount =  88_000, vendor = "주유소",           date = "2026-03-15", time = "11:23", cardName = "롯데카드"),
        SimpleExpense(amount = 234_000, vendor = "롯데마트",         date = "2026-03-18", time = "14:55", cardName = "롯데카드"),
        SimpleExpense(amount =  35_000, vendor = "올리브영",         date = "2026-03-22", time = "17:30", cardName = "롯데카드"),
        SimpleExpense(amount =   7_500, vendor = "스타벅스",         date = "2026-03-25", time = "09:10", cardName = "롯데카드"),
        SimpleExpense(amount =  18_500, vendor = "배달의민족",       date = "2026-03-28", time = "20:02", cardName = "롯데카드"),
        // ── 4월 일반 날짜 ─────────────────────────────────────────────────
        SimpleExpense(amount =   6_500, vendor = "스타벅스",         date = "2026-04-01", time = "08:45", cardName = "롯데카드"),
        SimpleExpense(amount =   3_200, vendor = "세븐일레븐",        date = "2026-04-01", time = "13:22", cardName = "롯데카드"),
        SimpleExpense(amount =  23_000, vendor = "배달의민족",        date = "2026-04-02", time = "19:05", cardName = "롯데카드"),
        SimpleExpense(amount =  87_000, vendor = "올리브영",          date = "2026-04-03", time = "16:40", cardName = "롯데카드"),
        SimpleExpense(amount = 156_000, vendor = "이마트",            date = "2026-04-04", time = "15:10", cardName = "롯데카드"),
        SimpleExpense(amount = 518_000, vendor = "무신사",            date = "2026-04-06", time = "14:22", cardName = "롯데카드"),
        SimpleExpense(amount =  95_000, vendor = "주유소",            date = "2026-04-08", time = "10:15", cardName = "롯데카드"),
        SimpleExpense(amount =   6_500, vendor = "스타벅스",          date = "2026-04-09", time = "09:00", cardName = "롯데카드"),
        SimpleExpense(amount = 178_000, vendor = "롯데마트",          date = "2026-04-10", time = "15:30", cardName = "롯데카드"),
        SimpleExpense(amount =  25_000, vendor = "병원",              date = "2026-04-11", time = "11:00", cardName = "롯데카드"),
        SimpleExpense(amount =   8_500, vendor = "약국",              date = "2026-04-11", time = "11:45", cardName = "롯데카드"),
        SimpleExpense(amount =  28_000, vendor = "CGV",               date = "2026-04-12", time = "18:20", cardName = "롯데카드"),
        SimpleExpense(amount =   6_000, vendor = "스타벅스",          date = "2026-04-12", time = "20:10", cardName = "롯데카드"),
        SimpleExpense(amount = 215_000, vendor = "무신사",            date = "2026-04-15", time = "13:45", cardName = "롯데카드"),
        SimpleExpense(amount =   7_500, vendor = "스타벅스",          date = "2026-04-16", time = "08:30", cardName = "롯데카드"),
        SimpleExpense(amount =  88_000, vendor = "주유소",            date = "2026-04-19", time = "09:40", cardName = "롯데카드"),
        SimpleExpense(amount =  19_000, vendor = "배달의민족",        date = "2026-04-21", time = "20:30", cardName = "롯데카드"),
        // ── 4월 5일 — 스크롤 테스트 (25건) ──────────────────────────────
        SimpleExpense(amount =   7_000, vendor = "스타벅스",          date = "2026-04-05", time = "08:05", cardName = "롯데카드"),
        SimpleExpense(amount =   4_500, vendor = "세븐일레븐",        date = "2026-04-05", time = "09:10", cardName = "롯데카드"),
        SimpleExpense(amount =  13_000, vendor = "GS25",              date = "2026-04-05", time = "09:45", cardName = "롯데카드"),
        SimpleExpense(amount =  32_000, vendor = "배달의민족",        date = "2026-04-05", time = "10:20", cardName = "롯데카드"),
        SimpleExpense(amount = 234_000, vendor = "쿠팡",              date = "2026-04-05", time = "11:00", cardName = "롯데카드"),
        SimpleExpense(amount =   8_900, vendor = "다이소",            date = "2026-04-05", time = "11:30", cardName = "롯데카드"),
        SimpleExpense(amount =  55_000, vendor = "올리브영",          date = "2026-04-05", time = "12:15", cardName = "롯데카드"),
        SimpleExpense(amount =  14_500, vendor = "버거킹",            date = "2026-04-05", time = "13:00", cardName = "롯데카드"),
        SimpleExpense(amount =   6_500, vendor = "스타벅스",          date = "2026-04-05", time = "14:10", cardName = "롯데카드"),
        SimpleExpense(amount =  78_000, vendor = "이마트",            date = "2026-04-05", time = "15:00", cardName = "롯데카드"),
        SimpleExpense(amount =  22_000, vendor = "CGV",               date = "2026-04-05", time = "15:50", cardName = "롯데카드"),
        SimpleExpense(amount =   3_800, vendor = "편의점",            date = "2026-04-05", time = "16:30", cardName = "롯데카드"),
        SimpleExpense(amount =  45_000, vendor = "홈플러스",          date = "2026-04-05", time = "17:10", cardName = "롯데카드"),
        SimpleExpense(amount =  11_000, vendor = "맥도날드",          date = "2026-04-05", time = "18:00", cardName = "롯데카드"),
        SimpleExpense(amount =  38_000, vendor = "배달의민족",        date = "2026-04-05", time = "19:00", cardName = "롯데카드"),
        SimpleExpense(amount =  92_000, vendor = "롯데마트",          date = "2026-04-05", time = "19:45", cardName = "롯데카드"),
        SimpleExpense(amount =   5_500, vendor = "투썸플레이스",      date = "2026-04-05", time = "20:30", cardName = "롯데카드"),
        SimpleExpense(amount =  29_000, vendor = "쿠팡이츠",          date = "2026-04-05", time = "21:00", cardName = "롯데카드"),
        SimpleExpense(amount =  67_000, vendor = "무신사",            date = "2026-04-05", time = "21:30", cardName = "롯데카드"),
        SimpleExpense(amount =   2_500, vendor = "세븐일레븐",        date = "2026-04-05", time = "22:00", cardName = "롯데카드"),
        SimpleExpense(amount =  16_000, vendor = "CGV팝콘",           date = "2026-04-05", time = "22:15", cardName = "롯데카드"),
        SimpleExpense(amount = 125_000, vendor = "쿠팡",              date = "2026-04-05", time = "22:40", cardName = "롯데카드"),
        SimpleExpense(amount =   9_500, vendor = "GS25",              date = "2026-04-05", time = "23:00", cardName = "롯데카드"),
        SimpleExpense(amount =  42_000, vendor = "배달의민족",        date = "2026-04-05", time = "23:20", cardName = "롯데카드"),
        SimpleExpense(amount =   7_800, vendor = "편의점",            date = "2026-04-05", time = "23:50", cardName = "롯데카드"),
        // ── 4월 14일 — 스크롤 테스트 (22건) ─────────────────────────────
        SimpleExpense(amount =   6_500, vendor = "스타벅스",          date = "2026-04-14", time = "08:00", cardName = "롯데카드"),
        SimpleExpense(amount =  18_000, vendor = "배달의민족",        date = "2026-04-14", time = "08:45", cardName = "롯데카드"),
        SimpleExpense(amount =  45_000, vendor = "쿠팡",              date = "2026-04-14", time = "09:30", cardName = "롯데카드"),
        SimpleExpense(amount =  89_000, vendor = "이마트",            date = "2026-04-14", time = "10:10", cardName = "롯데카드"),
        SimpleExpense(amount =   3_500, vendor = "GS25",              date = "2026-04-14", time = "11:00", cardName = "롯데카드"),
        SimpleExpense(amount =  72_000, vendor = "올리브영",          date = "2026-04-14", time = "11:40", cardName = "롯데카드"),
        SimpleExpense(amount =  15_000, vendor = "버거킹",            date = "2026-04-14", time = "12:30", cardName = "롯데카드"),
        SimpleExpense(amount = 340_000, vendor = "쿠팡",              date = "2026-04-14", time = "13:00", cardName = "롯데카드"),
        SimpleExpense(amount =  27_000, vendor = "CGV",               date = "2026-04-14", time = "14:00", cardName = "롯데카드"),
        SimpleExpense(amount =   8_000, vendor = "투썸플레이스",      date = "2026-04-14", time = "15:00", cardName = "롯데카드"),
        SimpleExpense(amount =  55_000, vendor = "무신사",            date = "2026-04-14", time = "15:45", cardName = "롯데카드"),
        SimpleExpense(amount =  11_500, vendor = "맥도날드",          date = "2026-04-14", time = "16:30", cardName = "롯데카드"),
        SimpleExpense(amount =  98_000, vendor = "롯데마트",          date = "2026-04-14", time = "17:20", cardName = "롯데카드"),
        SimpleExpense(amount =  33_000, vendor = "배달의민족",        date = "2026-04-14", time = "18:10", cardName = "롯데카드"),
        SimpleExpense(amount =   6_000, vendor = "스타벅스",          date = "2026-04-14", time = "19:00", cardName = "롯데카드"),
        SimpleExpense(amount =  19_500, vendor = "쿠팡이츠",          date = "2026-04-14", time = "19:45", cardName = "롯데카드"),
        SimpleExpense(amount = 560_000, vendor = "애플",              date = "2026-04-14", time = "20:30", cardName = "롯데카드"),
        SimpleExpense(amount =   4_200, vendor = "세븐일레븐",        date = "2026-04-14", time = "21:10", cardName = "롯데카드"),
        SimpleExpense(amount =  62_000, vendor = "홈플러스",          date = "2026-04-14", time = "21:50", cardName = "롯데카드"),
        SimpleExpense(amount =  14_000, vendor = "GS25",              date = "2026-04-14", time = "22:30", cardName = "롯데카드"),
        SimpleExpense(amount =  88_000, vendor = "주유소",            date = "2026-04-14", time = "23:00", cardName = "롯데카드"),
        SimpleExpense(amount =   7_000, vendor = "편의점",            date = "2026-04-14", time = "23:40", cardName = "롯데카드"),
        // ── 4월 20일 — 스크롤 테스트 (20건) ─────────────────────────────
        SimpleExpense(amount =   6_500, vendor = "스타벅스",          date = "2026-04-20", time = "07:50", cardName = "롯데카드"),
        SimpleExpense(amount =  24_000, vendor = "배달의민족",        date = "2026-04-20", time = "08:30", cardName = "롯데카드"),
        SimpleExpense(amount =  13_500, vendor = "GS25",              date = "2026-04-20", time = "09:20", cardName = "롯데카드"),
        SimpleExpense(amount = 245_000, vendor = "쿠팡",              date = "2026-04-20", time = "10:00", cardName = "롯데카드"),
        SimpleExpense(amount =  62_000, vendor = "올리브영",          date = "2026-04-20", time = "11:00", cardName = "롯데카드"),
        SimpleExpense(amount =  48_000, vendor = "홈플러스",          date = "2026-04-20", time = "12:00", cardName = "롯데카드"),
        SimpleExpense(amount =  10_500, vendor = "맥도날드",          date = "2026-04-20", time = "13:00", cardName = "롯데카드"),
        SimpleExpense(amount =  77_000, vendor = "이마트",            date = "2026-04-20", time = "14:00", cardName = "롯데카드"),
        SimpleExpense(amount =   5_000, vendor = "투썸플레이스",      date = "2026-04-20", time = "15:00", cardName = "롯데카드"),
        SimpleExpense(amount =  39_000, vendor = "배달의민족",        date = "2026-04-20", time = "16:00", cardName = "롯데카드"),
        SimpleExpense(amount = 128_000, vendor = "롯데마트",          date = "2026-04-20", time = "17:00", cardName = "롯데카드"),
        SimpleExpense(amount =   6_500, vendor = "스타벅스",          date = "2026-04-20", time = "18:00", cardName = "롯데카드"),
        SimpleExpense(amount =  21_000, vendor = "CGV",               date = "2026-04-20", time = "19:00", cardName = "롯데카드"),
        SimpleExpense(amount =  53_000, vendor = "무신사",            date = "2026-04-20", time = "20:00", cardName = "롯데카드"),
        SimpleExpense(amount =   3_900, vendor = "세븐일레븐",        date = "2026-04-20", time = "21:00", cardName = "롯데카드"),
        SimpleExpense(amount =  16_000, vendor = "쿠팡이츠",          date = "2026-04-20", time = "21:30", cardName = "롯데카드"),
        SimpleExpense(amount = 620_000, vendor = "삼성전자",          date = "2026-04-20", time = "22:00", cardName = "롯데카드"),
        SimpleExpense(amount =  44_000, vendor = "홈플러스",          date = "2026-04-20", time = "22:30", cardName = "롯데카드"),
        SimpleExpense(amount =   8_500, vendor = "GS25",              date = "2026-04-20", time = "23:00", cardName = "롯데카드"),
        SimpleExpense(amount =  31_000, vendor = "배달의민족",        date = "2026-04-20", time = "23:30", cardName = "롯데카드"),
    )
    entries.forEach { dao.insert(it) }
}

fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return enabled?.contains(context.packageName) == true
}
