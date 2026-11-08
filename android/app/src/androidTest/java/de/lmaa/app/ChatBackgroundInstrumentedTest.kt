package de.lmaa.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import de.lmaa.app.history.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Controls only the provider reply. Scheduling, context, Room and worker completion are real. */
internal class DelayedChatWorker(context: Context, params: WorkerParameters) : BriefingChatWorker(context, params) {
    override suspend fun generateAnswer(input: JSONArray): TextGenerationResult {
        started.complete(Unit)
        return TextGenerationResult.Success(reply.await())
    }

    companion object {
        var started = CompletableDeferred<Unit>()
        var reply = CompletableDeferred<String>()
    }
}

@RunWith(AndroidJUnit4::class)
class ChatBackgroundInstrumentedTest {
    @Test fun replyArrivesWhileActivityIsInBackground() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == "de.lmaa.app.testbed")
        val db = LmaaDatabase.getInstance(context)
        val manager = WorkManager.getInstance(context)
        val id = UUID.randomUUID().toString()
        val archive = """{"format":"lmaa-history","version":1,"briefings":[{"id":"$id","videoId":"abcdefghijk",
            "title":"Test","channel":"Test","ts":1,"model":"gpt-5.6-sol","markdown":"Test","styleName":"Test",
            "styleInstructions":"","styleOutputLanguage":"Deutsch","transcript":{"label":"TRANSKRIPT",
            "language":"de","provider":"primary","content":"Test"},"messages":[]}]}"""
        HistoryArchive(db).import(archive)
        val b = requireNotNull(db.briefingDao().findPortable(id))
        val dao = db.chatDao()
        val name = "lmaa-chat-$id"
        DelayedChatWorker.started = CompletableDeferred()
        DelayedChatWorker.reply = CompletableDeferred()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { screen ->
                dao.insert(ChatMessageEntity(id, b.id, "user", "Frage", 2, pending = true))
                val work = OneTimeWorkRequestBuilder<DelayedChatWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setInputData(workDataOf("message" to id))
                    .build()
                manager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, work).await()
                withTimeout(30000) {
                    DelayedChatWorker.started.await()
                    screen.onActivity { assertTrue(it.moveTaskToBack(true)) }
                    while (screen.state != androidx.lifecycle.Lifecycle.State.CREATED) delay(50)
                    DelayedChatWorker.reply.complete("Synthetische Antwort")
                    val messages = dao.observe(b.id).first { it.size == 2 && !it.first().pending }
                    assertEquals("Synthetische Antwort", messages.last().content)
                    assertEquals(WorkInfo.State.SUCCEEDED,
                        manager.getWorkInfoByIdFlow(work.id).first { it?.state?.isFinished == true }?.state)
                }
            }
        } finally {
            DelayedChatWorker.reply.complete("Test beendet")
            manager.cancelUniqueWork(name).await()
            db.briefingDao().deleteBriefingWithOwnedData(b.id, System.currentTimeMillis())
        }
        Unit
    }
}
