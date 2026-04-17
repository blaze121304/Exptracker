package com.exptracker.service

import com.exptracker.data.SimpleExpense

data class ParsedExpense(
    val vendor: String,
    val amount: Int,
    val date: String,   // "yyyy-MM-dd"
    val time: String    // "HH:mm"
)

interface CardParser {
    /** 이 파서가 처리할 수 있는 알림인지 확인 */
    fun canParse(title: String, packageName: String): Boolean

    /** 알림 본문에서 지출 정보 추출. 파싱 실패 시 null 반환 */
    fun parse(title: String, body: String): ParsedExpense?
}
