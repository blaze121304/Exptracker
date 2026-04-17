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
import com.exptracker.data.ExpenseDatabase
import com.exptracker.data.SimpleExpense
import kotlinx.coroutines.Dispatchers
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
    var isRunning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isRunning = isNotificationListenerEnabled(context)
        insertSeedDataOnce(context)
        insertScrollTestDataOnce(context)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("지출 추적기", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        if (isRunning) {
            Text(
                text = "서비스 실행 중",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "카드 알림을 수신하고 있습니다.\n홈 화면 위젯에서 지출 내역을 확인하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            Text(
                text = "알림 접근 권한이 필요합니다",
                color = MaterialTheme.colorScheme.error,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) {
                Text("권한 설정하기")
            }
        }
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
