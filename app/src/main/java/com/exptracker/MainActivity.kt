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
import kotlinx.coroutines.withContext
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
    val prefs = remember { context.getSharedPreferences("exptracker_prefs", Context.MODE_PRIVATE) }
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
            seq == easterTarget2 -> { easterTimeoutJob.value?.cancel(); showEasterEgg2 = true; easterSeq.clear() }
        }
    }

    LaunchedEffect(Unit) {
        isRunning = isNotificationListenerEnabled(context)
        insertSeedDataOnce(context)
        insertScrollTestDataOnce(context)
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
            title = { Text("!!") },
            text = { Text("이건 두번째 코나미 커맨드인데?") },
            confirmButton = {
                TextButton(onClick = { showEasterEgg2 = false }) { Text("확인") }
            }
        )
    }
}

private suspend fun insertSeedDataOnce(context: Context) {
    val prefs = context.getSharedPreferences("exptracker_prefs", Context.MODE_PRIVATE)
    if (prefs.getBoolean("seed_v2_inserted", false)) return

    withContext(Dispatchers.IO) {
        val dao = ExpenseDatabase.getDatabase(context).expenseDao()
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val seed = listOf(
            // D-7: 8,300원 → 파랑
            Triple(today.minusDays(7), "GS25 역삼점",           3500 to "09:12"),
            Triple(today.minusDays(7), "투썸플레이스",            4800 to "14:35"),
            // D-6: 67,000원 → 검정
            Triple(today.minusDays(6), "이마트 역삼점",          55000 to "11:20"),
            Triple(today.minusDays(6), "주유소 SK",              12000 to "18:47"),
            // D-5: 43,000원 → 빨강
            Triple(today.minusDays(5), "배달의민족",             28000 to "12:05"),
            Triple(today.minusDays(5), "CGV 강남",               15000 to "20:30"),
            // D-4: 지출 없음 → 초록 자동
            // D-3: 22,400원 → 노랑
            Triple(today.minusDays(3), "스타벅스 역삼R점",       13500 to "08:55"),
            Triple(today.minusDays(3), "올리브영 강남점",          8900 to "16:20"),
            // D-2: 지출 없음 → 초록 자동
            // D-1: 7,200원 → 파랑
            Triple(today.minusDays(1), "세븐일레븐 선릉점",       7200 to "10:30"),
            // 오늘: 2,500원 → 파랑
            Triple(today,              "세븐일레븐 역삼태광점",    2500 to "13:44"),
        )

        seed.forEach { (date, vendor, amountTime) ->
            dao.insert(SimpleExpense(
                amount = amountTime.first,
                vendor = vendor,
                date   = date.format(fmt),
                time   = amountTime.second
            ))
        }
    }
    prefs.edit().putBoolean("seed_v2_inserted", true).apply()
}

// D-2에 20건 삽입 — 스크롤 테스트용
private suspend fun insertScrollTestDataOnce(context: Context) {
    val prefs = context.getSharedPreferences("exptracker_prefs", Context.MODE_PRIVATE)
    if (prefs.getBoolean("seed_scroll_inserted", false)) return

    withContext(Dispatchers.IO) {
        val dao = ExpenseDatabase.getDatabase(context).expenseDao()
        val date = LocalDate.now().minusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        val records = listOf(
            "07:55" to ("편의점 CU 역삼점"     to  1800),
            "08:30" to ("스타벅스 역삼R점"      to  6500),
            "09:10" to ("지하철 교통카드"        to  1400),
            "10:20" to ("올리브영 강남점"        to 23500),
            "11:45" to ("맥도날드 역삼점"        to  8900),
            "12:30" to ("배달의민족"             to 18000),
            "13:15" to ("GS25 선릉점"           to  2300),
            "14:00" to ("카카오택시"             to  9800),
            "14:50" to ("다이소 강남점"          to  5000),
            "15:30" to ("쿠팡 결제"             to 34500),
            "16:10" to ("세븐일레븐 역삼태광점"  to  1500),
            "17:00" to ("버스 교통카드"          to  1400),
            "17:40" to ("이디야커피 역삼점"      to  4500),
            "18:20" to ("홈플러스 역삼점"        to 42000),
            "19:05" to ("BBQ 역삼점"            to 21000),
            "19:50" to ("GS25 역삼점"           to  3200),
            "20:30" to ("CGV 강남"              to 14000),
            "21:00" to ("팝콘 스낵"             to  6000),
            "21:45" to ("편의점 CU 강남점"       to  2700),
            "22:30" to ("카카오택시 귀가"         to 12500),
        )

        records.forEach { (time, vendorAmount) ->
            dao.insert(SimpleExpense(
                amount = vendorAmount.second,
                vendor = vendorAmount.first,
                date   = date,
                time   = time
            ))
        }
    }
    prefs.edit().putBoolean("seed_scroll_inserted", true).apply()
}

fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return enabled?.contains(context.packageName) == true
}
