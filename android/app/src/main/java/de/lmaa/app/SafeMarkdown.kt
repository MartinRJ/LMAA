package de.lmaa.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    data class Table(val header: List<String>, val align: List<TextAlign>, val rows: List<List<String>>) : MarkdownBlock
    data object Divider : MarkdownBlock
}

private val headingPattern = Regex("^(#{1,6})\\s+(.+)$")
private val numberedPattern = Regex("^(\\d{1,9})\\.\\s+(.+)$")
private const val linkDestinationPattern = """(?:[^()\s]+|\([^()\s]*\))+"""
private val markdownLinkPattern = Regex("""\[([^]]+)]\(($linkDestinationPattern)\)""")
private val inlinePattern = Regex(
    """\[([^]]+)]\(($linkDestinationPattern)\)|\*\*([^*]+)\*\*|`([^`]+)`|\*([^*]+)\*|(?<!\w)__([^_]+)__(?!\w)|(?<!\w)_([^_]+)_(?!\w)|~~([^~]+)~~""",
)

private val tablePipe = Regex("""(?<!\\)\|""")
private val tableDash = Regex(":?-+:?")
private val thematicBreak = Regex(""" {0,3}(?:(?:-\s*){3,}|(?:\*\s*){3,}|(?:_\s*){3,})""")

private fun cells(line: String): List<String> =
    line.trim().removePrefix("|").let { if (it.endsWith("\\|")) it else it.removeSuffix("|") }
        .split(tablePipe).map { it.trim().replace("\\|", "|") }

private fun tableAlignment(lines: List<String>, index: Int): List<TextAlign>? {
    val separator = lines.getOrNull(index + 1) ?: return null
    if ('|' !in lines[index] || '|' !in separator) return null
    val cols = cells(separator)
    if (!cols.all { tableDash.matches(it) } || cols.size != cells(lines[index]).size) return null
    return cols.map { when {
        it.startsWith(':') && it.endsWith(':') -> TextAlign.Center
        it.endsWith(':') -> TextAlign.End
        else -> TextAlign.Start
    } }
}

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

        val align = tableAlignment(lines, index)
        if (align != null) {
            val header = cells(line)
            index += 2
            val rows = mutableListOf<List<String>>()
            while (index < lines.size && '|' in lines[index] && !startsBlock(lines[index])) {
                rows += cells(lines[index++]).take(header.size)
            }
            blocks += MarkdownBlock.Table(header, align, rows)
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
            thematicBreak.matches(line) -> blocks += MarkdownBlock.Divider
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> {
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
                while (index < lines.size && lines[index].isNotBlank() && !startsBlock(lines[index]) && tableAlignment(lines, index) == null) {
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
        line.startsWith("+ ") || thematicBreak.matches(line)

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
    val blocks = remember(markdown) { parseMarkdown(markdown) }
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
        is MarkdownBlock.Table -> {
            val widths = block.header.mapIndexed { col, label ->
                val length = maxOf(label.length, block.rows.maxOfOrNull { it.getOrNull(col)?.length ?: 0 } ?: 0)
                (24 + 8 * length.coerceAtMost(32)).coerceIn(88, 280).dp
            }
            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                (listOf(block.header) + block.rows).forEachIndexed { row, cells ->
                    Row {
                        widths.forEachIndexed { col, width ->
                            Text(inlineMarkdown(cells.getOrNull(col).orEmpty()),
                                modifier = Modifier.width(width).border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    .padding(8.dp),
                                textAlign = block.align[col],
                                fontWeight = if (row == 0) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
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
                match.groupValues[6].isNotEmpty() -> pushStyled(match.groupValues[6], SpanStyle(fontWeight = FontWeight.Bold))
                match.groupValues[7].isNotEmpty() -> pushStyled(match.groupValues[7], SpanStyle(fontStyle = FontStyle.Italic))
                match.groupValues[8].isNotEmpty() -> pushStyled(match.groupValues[8], SpanStyle(textDecoration = TextDecoration.LineThrough))
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
