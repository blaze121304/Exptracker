package com.exptracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ExpenseDao {

    // Step 2: Insert only — no update/delete per PRD
    @Insert
    suspend fun insert(expense: SimpleExpense)

    // Step 3: Widget — today's total amount
    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE date = :date")
    suspend fun getTodayTotal(date: String): Int

    // Step 3: Widget — most recent 3 entries for today
    @Query("SELECT * FROM expenses WHERE date = :date ORDER BY id DESC LIMIT 3")
    suspend fun getRecentByDate(date: String): List<SimpleExpense>
}
