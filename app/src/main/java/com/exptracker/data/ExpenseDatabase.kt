package com.exptracker.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.exptracker.BuildConfig
import java.io.File

@Database(entities = [SimpleExpense::class], version = 5, exportSchema = false)
abstract class ExpenseDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: ExpenseDatabase? = null

        private const val PREFS_DB = "exptracker_db_prefs"
        private const val KEY_USE_TEST = "use_test_db"

        fun isTestDb(context: Context) =
            context.getSharedPreferences(PREFS_DB, Context.MODE_PRIVATE)
                .getBoolean(KEY_USE_TEST, false)

        fun toggleTestDb(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_DB, Context.MODE_PRIVATE)
            val next = !prefs.getBoolean(KEY_USE_TEST, false)
            prefs.edit().putBoolean(KEY_USE_TEST, next).apply()
            INSTANCE?.close()
            INSTANCE = null
            return next
        }

        private fun dbName(context: Context) =
            if (isTestDb(context)) "expense_database_TEST.db" else "expense_database_PRD.db"

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN cardName TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 기존 레코드 전부 롯데카드로 설정 (현재 지원 카드가 롯데카드 하나뿐)
                db.execSQL("UPDATE expenses SET cardName = '롯데카드' WHERE cardName = ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 스키마 변경 없음 — 버전 번호만 증가
            }
        }

        fun getDatabase(context: Context): ExpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): ExpenseDatabase {
            val dbFile = resolveDbFile(context)
            Log.i("ExpTracker", "DB: ${dbFile.absolutePath}")
            return Room.databaseBuilder(
                context.applicationContext,
                ExpenseDatabase::class.java,
                dbFile.absolutePath
            ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
        }

        private fun resolveDbFile(context: Context): File {
            val name = dbName(context)
            val externalDir = context.getExternalFilesDir("databases")
            if (externalDir != null) {
                if (!externalDir.exists()) externalDir.mkdirs()
                return File(externalDir, name)
            }
            return context.getDatabasePath(name)
        }
    }
}
