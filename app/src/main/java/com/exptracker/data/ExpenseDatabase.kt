package com.exptracker.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.exptracker.BuildConfig
import java.io.File

@Database(entities = [SimpleExpense::class], version = 2, exportSchema = false)
abstract class ExpenseDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: ExpenseDatabase? = null

        // Debug 빌드 → TEST DB / Release 빌드 → PRD DB
        private val DB_NAME = if (BuildConfig.DEBUG) "expense_database_TEST.db"
                              else                   "expense_database_PRD.db"

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
            ).fallbackToDestructiveMigration().build()
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
