package de.lmaa.app

import de.lmaa.app.history.validateArchive
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class HistoryArchiveValidationTest {
    private val fixture = """{"format":"lmaa-history","version":1,"styles":[],"briefings":[{
      "id":"briefing-1","videoId":"abcdefghijk","title":"Synthetisch","channel":"Test",
      "markdown":"[12:34] Inhalt","ts":1,"model":"gpt-5.6-sol","styleName":"Kurz",
      "styleInstructions":"Ein Satz.","styleOutputLanguage":"Deutsch",
      "transcript":{"content":"Vollständige Quelle 😃","label":"TRANSKRIPT","language":"de","provider":"primary"},
      "messages":[{"id":"message-1","role":"user","content":"Warum?","ts":2}]}]}"""
    @Test fun acceptsPortableExtensionFormat() {
        assertEquals("Vollständige Quelle 😃", validateArchive(fixture).getJSONArray("briefings")
            .getJSONObject(0).getJSONObject("transcript").getString("content"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownVersion() {
        validateArchive(fixture.replace("\"version\":1", "\"version\":2"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsSystemMessages() {
        validateArchive(fixture.replace("\"role\":\"user\"", "\"role\":\"system\""))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsDuplicateRecords() {
        val root = JSONObject(fixture)
        root.getJSONArray("briefings").put(root.getJSONArray("briefings").get(0))
        validateArchive(root.toString())
    }
}
