package com.exptracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Step 2: Minimal entity — only Insert & GetAll needed per PRD
@Entity(tableName = "expenses")
data class SimpleExpense(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val amount: Int,       // 금액 (원 단위)
    val vendor: String,    // 상호명
    val date: String       // "yyyy-MM-dd" 형식
)
