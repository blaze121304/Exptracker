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

@Database(entities = [SimpleExpense::class], version = 4, exportSchema = false)
abstract class ExpenseDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: ExpenseDatabase? = null

        // Debug 빌드 → TEST DB / Release 빌드 → PRD DB
        private val DB_NAME = if (BuildConfig.DEBUG) "expense_database_TEST.db"
                              else                   "expense_database_PRD.db"

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
            ).addMigrations(MIGRATION_2_3, MIGRATION_3_4).fallbackToDestructiveMigration().build()
        }

        private fun resolveDbFile(context: Context): File {
            val externalDir = context.getExternalFilesDir("databases")
            if (externalDir != null) {
                if (!externalDir.exists()) externalDir.mkdirs()
                return File(externalDir, DB_NAME)
            }
            return context.getDatabasePath(DB_NAME)
        }
    }
}
