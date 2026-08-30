package de.lmaa.app.history

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LmaaDatabaseMigrationInstrumentedTest {
    private lateinit var context: Context
    private val databaseName = "lmaa-migration-1-2-instrumented-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migration1To2_addsPersistentAnalysisJobsWithoutChangingHistory() {
        createVersion1Database().use { helper ->
            helper.writableDatabase.execSQL(
                """
                INSERT INTO videos (
                    videoId, canonicalUrl, title, channelId, channelTitle,
                    publishedAtEpochMillis, durationIso8601, durationSeconds,
                    thumbnailUrl, fetchedAtEpochMillis
                ) VALUES (
                    'Rq5iOD-mcEI',
                    'https://www.youtube.com/watch?v=Rq5iOD-mcEI',
                    'Bestand', NULL, 'Kanal', NULL, NULL, NULL,
                    'https://example.test/thumb.jpg', 1
                )
                """.trimIndent(),
            )
        }

        val migrated = Room.databaseBuilder(context, LmaaDatabase::class.java, databaseName)
            .addMigrations(LmaaDatabase.MIGRATION_1_2)
            .build()
        try {
            val database = migrated.openHelper.writableDatabase
            database.query("SELECT COUNT(*) FROM videos").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM analysis_jobs").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            migrated.close()
        }
    }

    private fun createVersion1Database(): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createVersion1Schema(db)

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                },
            )
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).also {
            it.writableDatabase
        }
    }

    private fun createVersion1Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS videos (
                videoId TEXT NOT NULL,
                canonicalUrl TEXT NOT NULL,
                title TEXT NOT NULL,
                channelId TEXT,
                channelTitle TEXT NOT NULL,
                publishedAtEpochMillis INTEGER,
                durationIso8601 TEXT,
                durationSeconds INTEGER,
                thumbnailUrl TEXT NOT NULL,
                fetchedAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(videoId)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS transcripts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                videoId TEXT NOT NULL,
                provider TEXT NOT NULL,
                languageCode TEXT NOT NULL,
                isGenerated INTEGER NOT NULL,
                segmentsJson TEXT NOT NULL,
                plainText TEXT NOT NULL,
                fetchedAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(videoId) REFERENCES videos(videoId)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS briefings (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                videoId TEXT NOT NULL,
                transcriptId INTEGER NOT NULL,
                styleNameSnapshot TEXT NOT NULL,
                styleInstructionsSnapshot TEXT NOT NULL,
                modelSnapshot TEXT NOT NULL,
                markdown TEXT NOT NULL,
                mapChunkCount INTEGER NOT NULL,
                status TEXT NOT NULL,
                errorCode TEXT,
                createdAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(videoId) REFERENCES videos(videoId)
                    ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(transcriptId) REFERENCES transcripts(id)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transcripts_videoId ON transcripts(videoId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_briefings_videoId ON briefings(videoId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_briefings_transcriptId ON briefings(transcriptId)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_briefings_createdAtEpochMillis " +
                "ON briefings(createdAtEpochMillis)",
        )
    }
}
