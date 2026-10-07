package com.cprint.app.data.local

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.cprint.app.data.model.entity.RecentDocumentEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class RecentDocumentDeduplicationTest {
    @Test fun `same URI cannot create duplicate recent documents or print jobs`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.recentDocumentDao()
            repeat(4) { index ->
                dao.insertDocument(RecentDocumentEntity("id-$index", "file.pdf", "content://docs/1",
                    "application/pdf", 100, 1, null, Date(index.toLong()), 1))
            }
            assertEquals(1, dao.getDocumentCount())
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM print_jobs").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            // Identical names at different addresses must remain distinct.
            dao.insertDocument(RecentDocumentEntity("other", "file.pdf", "content://docs/2",
                "application/pdf", 100, 1, null, Date(), 1))
            assertEquals(2, dao.getDocumentCount())
        } finally { db.close() }
    }

    @Test fun `migration merges old URI duplicates and preserves real print history`() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(
            RuntimeEnvironment.getApplication()).callback(object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE recent_documents (id TEXT, uri TEXT, lastOpenedAt INTEGER)")
                db.execSQL("CREATE TABLE print_jobs (id TEXT)")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        try {
            val db = helper.writableDatabase
            repeat(4) { db.execSQL("INSERT INTO recent_documents VALUES (?, 'content://docs/1', ?)", arrayOf("id-$it", it)) }
            db.execSQL("INSERT INTO print_jobs VALUES ('real-print')")
            AppDatabase.MIGRATION_1_2.migrate(db)
            db.query("SELECT id FROM recent_documents").use {
                assertEquals(1, it.count)
                assertTrue(it.moveToFirst())
                assertEquals("id-3", it.getString(0))
            }
            db.query("SELECT id FROM print_jobs").use { assertEquals(1, it.count) }
        } finally { helper.close() }
    }
}
