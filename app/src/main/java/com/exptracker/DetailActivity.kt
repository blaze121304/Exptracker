package com.exptracker

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

class DetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val date = intent.getStringExtra(EXTRA_DATE) ?: return finish()

        // 홈 화면 위에 팝업처럼 뜨도록 — 외부 터치 시 닫힘
        setFinishOnTouchOutside(true)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.BOTTOM)
        window.setBackgroundDrawableResource(android.R.color.transparent)

        setContent {
            MaterialTheme {
                DetailPopup(date = date, onDismiss = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_DATE = "date"
    }
}

@Composable
private fun DetailPopup(date: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var expenses by remember { mutableStateOf<List<SimpleExpense>>(emptyList()) }

    LaunchedEffect(date) {
        withContext(Dispatchers.IO) {
            expenses = ExpenseDatabase.getDatabase(context).expenseDao().getByDate(date)
        }
    }

    val total = expenses.sumOf { it.amount }

    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {

            // 핸들 바
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    modifier = Modifier.size(width = 36.dp, height = 4.dp)
                ) {}
            }

            // 날짜 + 합계
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "합계 %,d원".format(total),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider()

            if (expenses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("지출 내역 없음", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(expenses) { expense ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(expense.vendor, fontSize = 14.sp)
                                Text(
                                    expense.time,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Text(
                                text = "%,d원".format(expense.amount),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }
}
