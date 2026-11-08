package de.lmaa.app

import de.lmaa.app.history.ChatMessageEntity
import org.junit.Assert.*
import org.junit.Test

class ChatContextTest {
    @Test fun oldestMessagesDropButSourceAndNewestRemainVerbatim() {
        val messages = listOf(
            ChatMessageEntity("old", 1, "user", "X".repeat(2000), 1),
            ChatMessageEntity("answer", 1, "assistant", "Antwort 😃", 2),
            ChatMessageEntity("new", 1, "user", "Warum?", 3),
        )
        val result = chatContext("Ungekürztes Transkript 😃", messages, 1100)
        assertEquals(3, result.length())
        assertEquals("Ungekürztes Transkript 😃", result.getJSONObject(0).getString("content"))
        assertEquals("Antwort 😃", result.getJSONObject(1).getString("content"))
        assertEquals("Warum?", result.getJSONObject(2).getString("content"))
        assertEquals(2000, messages.first().content.length)
    }
    @Test(expected = IllegalArgumentException::class)
    fun oversizedSourceIsNotSilentlyTruncated() {
        chatContext("X".repeat(2000), listOf(ChatMessageEntity("new", 1, "user", "Frage", 1)), 1000)
    }
}
