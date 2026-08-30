package de.lmaa.app.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.lmaa.app.AnalysisStage
import de.lmaa.app.BriefingStyleSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisJobRepositoryInstrumentedTest {
    private lateinit var context: Context
    private val databaseName = "lmaa-analysis-job-instrumented-test.db"

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
    fun runningJob_survivesDatabaseReopen_andTerminalResultCanBeConsumed() = runBlocking {
        var database = openDatabase()
        var repository = AnalysisJobRepository(database.analysisJobDao())
        val style = BriefingStyleSnapshot("Code", "Fokus auf Implementierung", "Deutsch")
        val created = repository.create(
            canonicalUrl = "https://www.youtube.com/watch?v=Rq5iOD-mcEI",
            style = style,
            styleId = 42L,
        )

        repository.markRunning(created.jobId, AnalysisStage.BRIEFING)
        assertEquals(AnalysisJobStatus.RUNNING, repository.current.first()?.status)
        assertEquals(AnalysisStage.BRIEFING, repository.current.first()?.stage)

        database.close()
        database = openDatabase()
        repository = AnalysisJobRepository(database.analysisJobDao())

        val recovered = repository.findRecoverable().single()
        assertEquals(created.jobId, recovered.jobId)
        assertEquals(42L, recovered.styleId)
        assertEquals(style, recovered.style)
        repository.markFailed(created.jobId, "SYNTHETIC_ERROR")
        assertEquals("SYNTHETIC_ERROR", repository.current.first()?.errorCode)
        repository.consumeResult(created.jobId)
        assertNull(repository.current.first())
        database.close()
    }

    private fun openDatabase(): LmaaDatabase = Room.databaseBuilder(
        context,
        LmaaDatabase::class.java,
        databaseName,
    ).addMigrations(LmaaDatabase.MIGRATION_1_2).build()
}
