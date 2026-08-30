package de.lmaa.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import java.net.URI

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Numbered(val number: Int, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

private val headingPattern = Regex("^(#{1,6})\\s+(.+)$")
private val numberedPattern = Regex("^(\\d+)\\.\\s+(.+)$")
private const val linkDestinationPattern = """(?:[^()\s]+|\([^()\s]*\))+"""
private val markdownLinkPattern = Regex("""\[([^]]+)]\(($linkDestinationPattern)\)""")
private val inlinePattern = Regex(
    """\[([^]]+)]\(($linkDestinationPattern)\)|\*\*([^*]+)\*\*|`([^`]+)`|\*([^*]+)\*""",
)

internal fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        if (line.isBlank()) {
            index += 1
            continue
        }
        if (line.trimStart().startsWith("```")) {
            val code = mutableListOf<String>()
            index += 1
            while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                code += lines[index]
                index += 1
            }
            if (index < lines.size) index += 1
            blocks += MarkdownBlock.Code(code.joinToString("\n"))
            continue
        }

        val headingMatch = headingPattern.matchEntire(line)
        if (headingMatch != null) {
            blocks += MarkdownBlock.Heading(
                headingMatch.groupValues[1].length,
                headingMatch.groupValues[2],
            )
            index += 1
            continue
        }

        when {
            line == "---" || line == "***" -> blocks += MarkdownBlock.Divider
            line.startsWith("- ") || line.startsWith("* ") -> {
                blocks += MarkdownBlock.Bullet(line.drop(2))
            }
            numberedPattern.matches(line) -> {
                val match = numberedPattern.matchEntire(line)!!
                blocks += MarkdownBlock.Numbered(match.groupValues[1].toInt(), match.groupValues[2])
            }
            line.startsWith("> ") -> blocks += MarkdownBlock.Quote(line.drop(2))
            else -> {
                val paragraph = mutableListOf(line)
                index += 1
                while (index < lines.size && lines[index].isNotBlank() && !startsBlock(lines[index])) {
                    paragraph += lines[index]
                    index += 1
                }
                blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" "))
                continue
            }
        }
        index += 1
    }
    return blocks
}

private fun startsBlock(line: String): Boolean =
    headingPattern.matches(line) ||
        numberedPattern.matches(line) ||
        line.startsWith("- ") ||
        line.startsWith("* ") ||
        line.startsWith("> ") ||
        line.trimStart().startsWith("```") ||
        line == "---" ||
        line == "***"

internal fun isAllowedMarkdownLink(rawUrl: String): Boolean = runCatching {
    val uri = URI(rawUrl)
    uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo == null &&
        uri.port == -1
}.getOrDefault(false)

internal fun replaceUnsafeMarkdownLinksWithLabels(text: String): String =
    markdownLinkPattern.replace(text) { match ->
        if (isAllowedMarkdownLink(match.groupValues[2])) match.value else match.groupValues[1]
    }

@Composable
internal fun SafeMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = parseMarkdown(markdown)
    SelectionContainer {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            blocks.forEach { block -> MarkdownBlockView(block) }
        }
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock) {
    when (block) {
        is MarkdownBlock.Heading -> Text(
            text = inlineMarkdown(block.text),
            style = headingStyle(block.level),
            fontWeight = FontWeight.Bold,
        )
        is MarkdownBlock.Paragraph -> Text(
            text = inlineMarkdown(block.text),
            style = MaterialTheme.typography.bodyLarge,
        )
        is MarkdownBlock.Bullet -> ListRow("•", block.text)
        is MarkdownBlock.Numbered -> ListRow("${block.number}.", block.text)
        is MarkdownBlock.Quote -> Text(
            text = inlineMarkdown(block.text),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic,
        )
        is MarkdownBlock.Code -> Text(
            text = block.text,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
        )
        MarkdownBlock.Divider -> androidx.compose.material3.HorizontalDivider()
    }
}

@Composable
private fun ListRow(marker: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(marker, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text(
            text = inlineMarkdown(text),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> MaterialTheme.typography.headlineMedium
    2 -> MaterialTheme.typography.headlineSmall
    3 -> MaterialTheme.typography.titleLarge
    else -> MaterialTheme.typography.titleMedium
}

@Composable
private fun inlineMarkdown(text: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val sanitizedText = replaceUnsafeMarkdownLinksWithLabels(text)
    return buildAnnotatedString {
        var cursor = 0
        inlinePattern.findAll(sanitizedText).forEach { match ->
            append(sanitizedText.substring(cursor, match.range.first))
            when {
                match.groupValues[1].isNotEmpty() -> {
                    val label = match.groupValues[1]
                    val url = match.groupValues[2]
                    if (isAllowedMarkdownLink(url)) {
                        withLink(
                            LinkAnnotation.Url(
                                url,
                                TextLinkStyles(
                                    style = SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                ),
                            ),
                        ) {
                            append(label)
                        }
                    } else {
                        append(label)
                    }
                }
                match.groupValues[3].isNotEmpty() -> pushStyled(
                    match.groupValues[3],
                    SpanStyle(fontWeight = FontWeight.Bold),
                )
                match.groupValues[4].isNotEmpty() -> pushStyled(
                    match.groupValues[4],
                    SpanStyle(fontFamily = FontFamily.Monospace, background = Color.LightGray),
                )
                match.groupValues[5].isNotEmpty() -> pushStyled(
                    match.groupValues[5],
                    SpanStyle(fontStyle = FontStyle.Italic),
                )
            }
            cursor = match.range.last + 1
        }
        append(sanitizedText.substring(cursor))
    }
}

private fun AnnotatedString.Builder.pushStyled(text: String, style: SpanStyle) {
    pushStyle(style)
    append(text)
    pop()
}
