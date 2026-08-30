package de.lmaa.app.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BriefingStyleRepositoryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: LmaaDatabase
    private lateinit var repository: BriefingStyleRepository
    private val databaseName = "lmaa-style-instrumented-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
        database = Room.databaseBuilder(context, LmaaDatabase::class.java, databaseName).build()
        repository = BriefingStyleRepository(database.briefingStyleDao()) { 1234L }
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun customStyle_crudActivationAndBuiltInProtection() = runBlocking {
        val builtIn = repository.ensureDefault()
        assertTrue(builtIn.isBuiltIn)
        assertTrue(builtIn.isActive)

        val custom = repository.create("Technisch", "Fokus auf Code", "Deutsch")
        assertFalse(custom.isActive)
        repository.setActive(custom.id)
        assertEquals("Technisch", repository.active.first()?.name)

        repository.update(custom.id, "Technisch kompakt", "Code und Risiken", "Deutsch")
        assertEquals("Technisch kompakt", repository.active.first()?.name)
        assertFailsWithMessage("ACTIVE_STYLE_PROTECTED") { repository.delete(custom.id) }
        assertFailsWithMessage("BUILT_IN_STYLE_PROTECTED") {
            repository.update(builtIn.id, "Standard 2", "Test", "Deutsch")
        }

        repository.setActive(builtIn.id)
        repository.delete(custom.id)
        assertEquals(listOf("Standard"), repository.styles.first().map { it.name })
    }

    @Test
    fun duplicateNames_areRejectedCaseInsensitively() = runBlocking {
        repository.ensureDefault()
        repository.create("Code", "Details", "Deutsch")

        assertFailsWithMessage("STYLE_NAME_EXISTS") {
            repository.create(" code ", "Andere Details", "Englisch")
        }
    }

    private suspend fun assertFailsWithMessage(expected: String, block: suspend () -> Unit) {
        val actual = try {
            block()
            null
        } catch (exception: IllegalStateException) {
            exception.message
        }
        assertEquals(expected, actual)
    }
}
