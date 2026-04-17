package com.exptracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

data class VendorTotal(val vendor: String, val total: Int)

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

    // Calendar — all expenses for a given month (yearMonth = "yyyy-MM")
    @Query("SELECT * FROM expenses WHERE date LIKE :yearMonth || '-%' ORDER BY date, id")
    suspend fun getByMonth(yearMonth: String): List<SimpleExpense>

    // Calendar — all expenses for a specific date
    @Query("SELECT * FROM expenses WHERE date = :date ORDER BY id DESC")
    suspend fun getByDate(date: String): List<SimpleExpense>

    // 청구 기간 합계 (결제일 기준: startDate ~ endDate)
    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE date >= :startDate AND date <= :endDate")
    suspend fun getTotalInRange(startDate: String, endDate: String): Int

    // 결제처별 합계 순위
    @Query("SELECT vendor, SUM(amount) as total FROM expenses WHERE date >= :startDate AND date <= :endDate GROUP BY vendor ORDER BY total DESC")
    suspend fun getVendorRankings(startDate: String, endDate: String): List<VendorTotal>
}
