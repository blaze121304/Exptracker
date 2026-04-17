package com.exptracker.service

import android.util.Log
import java.util.Calendar

/**
 * 롯데카드 푸시 알림 파서
 *
 * 알림 포맷 (bigText 기준):
 *   line 0: 세븐일레븐 역삼태광점   ← 사용처
 *   line 1: 2,500원 승인            ← 금액
 *   line 2: 텔로 T라이트(2*7*)      ← 카드명 (무시)
 *   line 3: 일시불, 04/17 13:44     ← 날짜·시간
 *   line 4: 누적금액 4,042,232      ← 무시
 */
class LotteCardParser : CardParser {

    private val amountRegex = Regex("""(\d[\d,]+)원""")
    private val dateTimeRegex = Regex("""(\d{2}/\d{2})\s+(\d{2}:\d{2})""")

    override fun canParse(title: String, packageName: String): Boolean =
        title.contains("롯데카드")

    override fun parse(title: String, body: String): ParsedExpense? {
        val lines = body.lines().map { it.trim() }.filter { it.isNotBlank() }

        if (lines.size < 4) {
            Log.w(TAG, "롯데카드: 줄 수 부족 (${lines.size}줄) body=$body")
            return null
        }

        val vendor = lines[0]

        val amount = amountRegex.find(lines[1])
            ?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
            ?: run { Log.w(TAG, "롯데카드: 금액 파싱 실패 → ${lines[1]}"); return null }

        val dateTimeMatch = dateTimeRegex.find(lines[3])
            ?: run { Log.w(TAG, "롯데카드: 날짜 파싱 실패 → ${lines[3]}"); return null }

        val (mmdd, time) = dateTimeMatch.destructured          // "04/17", "13:44"
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val (month, day) = mmdd.split("/")
        val date = "%d-%s-%s".format(year, month, day)        // "2026-04-17"

        return ParsedExpense(vendor = vendor, amount = amount, date = date, time = time)
    }

    companion object {
        private const val TAG = "ExpTracker"
    }
}
