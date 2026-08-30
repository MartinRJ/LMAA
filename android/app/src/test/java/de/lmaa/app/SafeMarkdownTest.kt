package de.lmaa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeMarkdownTest {
    @Test
    fun `required briefing structures are parsed`() {
        val blocks = parseMarkdown(
            """# Kernaussage

Text mit **Fettdruck** und [Quelle](https://example.com/source).

## Wichtigste Punkte
- Erster Punkt
1. Erster Schritt
> Unsicherheit
```
kein HTML
```
---
""",
        )

        assertEquals(MarkdownBlock.Heading(1, "Kernaussage"), blocks[0])
        assertTrue(blocks.any { it is MarkdownBlock.Bullet })
        assertTrue(blocks.any { it is MarkdownBlock.Numbered })
        assertTrue(blocks.any { it is MarkdownBlock.Quote })
        assertTrue(blocks.any { it is MarkdownBlock.Code })
        assertTrue(blocks.any { it is MarkdownBlock.Divider })
    }

    @Test
    fun `only safe https links are interactive`() {
        assertTrue(isAllowedMarkdownLink("https://www.youtube.com/watch?v=ABCDEFGHIJK"))
        assertFalse(isAllowedMarkdownLink("http://example.com"))
        assertFalse(isAllowedMarkdownLink("javascript:alert(1)"))
        assertFalse(isAllowedMarkdownLink("https://user:secret@example.com/path"))
        assertFalse(isAllowedMarkdownLink("https://example.com:8443/path"))
    }

    @Test
    fun `unsafe link with parentheses becomes label without trailing delimiter`() {
        val text = "[Unsicherer Link bleibt Text](javascript:alert(1))"

        assertEquals("Unsicherer Link bleibt Text", replaceUnsafeMarkdownLinksWithLabels(text))
    }

    @Test
    fun `safe link markup remains available for annotation`() {
        val text = "[Video öffnen](https://www.youtube.com/watch?v=ABCDEFGHIJK)"

        assertEquals(text, replaceUnsafeMarkdownLinksWithLabels(text))
    }

    @Test
    fun `html remains plain paragraph text`() {
        val blocks = parseMarkdown("<script>alert('x')</script>")

        assertEquals(listOf(MarkdownBlock.Paragraph("<script>alert('x')</script>")), blocks)
    }

    @Test
    fun `fenced code preserves indentation lines and html literals`() {
        val markdown = """```kotlin
data class Briefing(
    val title: String,
)
<script>plain code</script>
```"""

        val code = parseMarkdown(markdown).single() as MarkdownBlock.Code

        assertEquals(
            "data class Briefing(\n    val title: String,\n)\n<script>plain code</script>",
            code.text,
        )
    }
}
