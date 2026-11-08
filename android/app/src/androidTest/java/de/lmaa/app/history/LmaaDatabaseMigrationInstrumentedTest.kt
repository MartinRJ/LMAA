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
    private val databaseName = "lmaa-migration-1-3-instrumented-test.db"

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
    fun migration1To3_addsJobsStylesAndUsageWithoutChangingHistory() {
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
            .addMigrations(LmaaDatabase.MIGRATION_1_2, LmaaDatabase.MIGRATION_2_3, LmaaDatabase.MIGRATION_3_4)
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
            database.query(
                "SELECT name, outputLanguage, isActive, isBuiltIn FROM briefing_styles",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("Standard", cursor.getString(0))
                assertEquals("Deutsch", cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals(1, cursor.getInt(3))
            }
            database.query("SELECT COUNT(*) FROM provider_usage").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migration3To4_preservesBriefingsAndAssignsStablePortableIds() {
        createVersion1Database(3).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("""INSERT INTO videos VALUES ('abcdefghijk',
                'https://www.youtube.com/watch?v=abcdefghijk', 'Bestand', NULL, 'Kanal',
                NULL, NULL, NULL, '', 1)""")
            db.execSQL("""INSERT INTO transcripts VALUES (1, 'abcdefghijk', 'primary', 'de', 0, '[]', 'Original', 1)""")
            db.execSQL("""INSERT INTO briefings (id, videoId, transcriptId, styleNameSnapshot,
                styleInstructionsSnapshot, styleOutputLanguageSnapshot, modelSnapshot, markdown,
                mapChunkCount, status, errorCode, createdAtEpochMillis)
                VALUES (1, 'abcdefghijk', 1, 'Eigener Stil', 'Ein Satz', 'Deutsch',
                'gpt-5.6-sol', 'Unverändert', 1, 'COMPLETED', NULL, 1)""")
        }
        var portableId = ""
        repeat(2) {
            Room.databaseBuilder(context, LmaaDatabase::class.java, databaseName)
                .addMigrations(LmaaDatabase.MIGRATION_3_4).build().use { migrated ->
                    val db = migrated.openHelper.writableDatabase
                    db.query("SELECT portableId, markdown, styleInstructionsSnapshot FROM briefings").use { cursor ->
                        cursor.moveToFirst()
                        if (portableId.isEmpty()) portableId = cursor.getString(0)
                        assertEquals(32, portableId.length)
                        assertEquals(portableId, cursor.getString(0))
                        assertEquals("Unverändert", cursor.getString(1))
                        assertEquals("Ein Satz", cursor.getString(2))
                    }
                    db.query("SELECT COUNT(*) FROM chat_messages").use { cursor ->
                        cursor.moveToFirst(); assertEquals(0, cursor.getInt(0))
                    }
                }
        }
    }

    private fun createVersion1Database(version: Int = 1): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion1Schema(db)
                        if (version == 3) {
                            LmaaDatabase.MIGRATION_1_2.migrate(db)
                            LmaaDatabase.MIGRATION_2_3.migrate(db)
                        }
                    }

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
