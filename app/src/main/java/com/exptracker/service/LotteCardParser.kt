package com.exptracker.service

import android.util.Log
import java.util.Calendar

/**
 * 롯데카드 푸시 알림 파서
 *
 * 실제 알림 구조:
 *   EXTRA_TITLE : 세븐일레븐 태광역삼점   ← 사용처
 *   EXTRA_BIG_TEXT:
 *     line 0: 2,500원 승인               ← 금액
 *     line 1: 텔로 T라이트(2*7*)         ← 카드명 (무시)
 *     line 2: 일시불, 04/22 12:34        ← 날짜·시간
 *     line 3: 누적금액 3,729,201원       ← 무시
 */
class LotteCardParser : CardParser {

    private val amountRegex = Regex("""(\d[\d,]+)원""")
    private val dateTimeRegex = Regex("""(\d{2}/\d{2})\s+(\d{2}:\d{2})""")

    override fun canParse(title: String, packageName: String): Boolean =
        packageName.contains("lotte", ignoreCase = true) || title.contains("롯데카드")

    override fun parse(title: String, body: String): ParsedExpense? {
        val lines = body.lines().map { it.trim() }.filter { it.isNotBlank() }

        if (lines.size < 3) {
            Log.w(TAG, "롯데카드: 줄 수 부족 (${lines.size}줄) body=$body")
            return null
        }

        val vendor = title   // EXTRA_TITLE = 가맹점명

        val amount = amountRegex.find(lines[0])
            ?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
            ?: run { Log.w(TAG, "롯데카드: 금액 파싱 실패 → ${lines[0]}"); return null }

        val dateTimeMatch = dateTimeRegex.find(lines[2])
            ?: run { Log.w(TAG, "롯데카드: 날짜 파싱 실패 → ${lines[2]}"); return null }

        val (mmdd, time) = dateTimeMatch.destructured          // "04/22", "12:34"
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val (month, day) = mmdd.split("/")
        val date = "%d-%s-%s".format(year, month, day)        // "2026-04-22"

        return ParsedExpense(vendor = vendor, amount = amount, date = date, time = time, cardName = "롯데카드")
    }

    companion object {
        private const val TAG = "ExpTracker"
    }
}
