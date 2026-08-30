package de.lmaa.app

import de.lmaa.app.history.StoredBriefing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingExportTest {
    @Test
    fun export_containsMetadataCanonicalUrlAndMarkdown() {
        val export = buildBriefingExport(
            StoredBriefing(
                briefingId = 7,
                canonicalUrl = "https://www.youtube.com/watch?v=Rq5iOD-mcEI",
                title = "Titel\nmit Zeilenumbruch",
                channelTitle = "Testkanal",
                model = "gpt-5.6-sol",
                styleName = "Standard",
                styleInstructions = "Test",
                styleOutputLanguage = "Deutsch",
                transcriptLanguage = "de",
                transcriptProvider = "primary",
                markdown = "# Kernaussage\n\nInhalt",
                createdAtEpochMillis = 0,
            ),
        )

        assertTrue(export.startsWith("Titel: Titel mit Zeilenumbruch\n"))
        assertTrue(export.contains("Kanal: Testkanal\n"))
        assertTrue(export.contains("URL: https://www.youtube.com/watch?v=Rq5iOD-mcEI\n"))
        assertTrue(export.endsWith("# Kernaussage\n\nInhalt"))
        assertEquals(1, "URL:".toRegex().findAll(export).count())
    }
}
