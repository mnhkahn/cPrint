package com.cprint.app.data.local

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun printJobDao(): PrintJobDao
    abstract fun printerDao(): PrinterDao
    abstract fun recentDocumentDao(): RecentDocumentDao

    companion object {
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Keep the most recently opened record for each exact URI. Never merge by filename.
                db.execSQL("""
                    DELETE FROM recent_documents WHERE rowid NOT IN (
                        SELECT (SELECT newest.rowid FROM recent_documents newest
                            WHERE newest.uri = original.uri
                            ORDER BY newest.lastOpenedAt DESC, newest.rowid DESC LIMIT 1)
                        FROM recent_documents original
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recent_documents_uri ON recent_documents (uri)")
            }
        }

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
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
