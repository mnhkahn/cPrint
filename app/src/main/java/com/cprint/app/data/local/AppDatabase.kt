package com.cprint.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cprint.app.data.model.entity.PrintJobEntity
import com.cprint.app.data.model.entity.PrinterEntity
import com.cprint.app.data.model.entity.RecentDocumentEntity

/**
 * Room database for cPrint application
 */
@Database(
    entities = [
        PrintJobEntity::class,
        PrinterEntity::class,
        RecentDocumentEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun printJobDao(): PrintJobDao
    abstract fun printerDao(): PrinterDao
    abstract fun recentDocumentDao(): RecentDocumentDao

    companion object {
        private const val DATABASE_NAME = "cprint_database"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
