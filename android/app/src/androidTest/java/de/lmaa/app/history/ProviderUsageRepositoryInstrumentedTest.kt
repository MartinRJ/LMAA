package de.lmaa.app.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderUsageRepositoryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: LmaaDatabase
    private val databaseName = "lmaa-provider-usage-instrumented-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
        database = Room.databaseBuilder(context, LmaaDatabase::class.java, databaseName).build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun attemptsCountExactlyOnce_andSuccessesSeparately() = runBlocking {
        val repository = ProviderUsageRepository(
            dao = database.providerUsageDao(),
            monthProvider = { "2030-01" },
            clock = { 42L },
        )
        assertEquals(0, repository.rapidApiCurrentMonth.first().attempts)

        repository.recordRapidApiAttempt(success = false, status = "QUOTA")
        repository.recordRapidApiAttempt(success = true, status = "SUCCESS")

        val usage = repository.rapidApiCurrentMonth.first()
        assertEquals(2, usage.attempts)
        assertEquals(1, usage.successes)
        assertEquals("SUCCESS", usage.lastStatus)
        assertEquals(98, usage.remaining)
    }

    @Test
    fun developmentBaseline_isIdempotent() = runBlocking {
        val repository = ProviderUsageRepository(
            dao = database.providerUsageDao(),
            monthProvider = { "2026-08" },
            clock = { 42L },
        )

        repository.ensureDevelopmentBaseline()
        repository.ensureDevelopmentBaseline()
        assertEquals(3, repository.rapidApiCurrentMonth.first().attempts)

        repository.recordRapidApiAttempt(success = false, status = "HTTP_500")
        assertEquals(4, repository.rapidApiCurrentMonth.first().attempts)
    }
}
