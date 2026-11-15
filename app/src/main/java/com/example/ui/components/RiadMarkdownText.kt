package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CodeBackground
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.MatrixGreenSubtle
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SyntaxComment
import com.example.ui.theme.SyntaxKeyword
import com.example.ui.theme.SyntaxNumber
import com.example.ui.theme.SyntaxString
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VoidBackground
import kotlinx.coroutines.delay

private sealed interface MarkdownBlock {
    data class TextParagraph(val lines: List<String>) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class TableBlock(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
}

@Composable
fun RiadMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.CodeBlock -> {
                    CodeBlockCard(
                        language = block.language,
                        code = block.code
                    )
                }
                is MarkdownBlock.TableBlock -> {
                    SecurityAuditTable(
                        headers = block.headers,
                        rows = block.rows
                    )
                }
                is MarkdownBlock.TextParagraph -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.lines.forEach { line ->
                            MarkdownLine(line)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownLine(line: String) {
    val trimmed = line.trimStart()
    when {
        trimmed.startsWith("### ") -> {
            Text(
                text = parseInlineMarkdown(trimmed.removePrefix("### ")),
                style = MaterialTheme.typography.titleSmall,
                color = MatrixGreen,
                fontWeight = FontWeight.Bold
            )
        }
        trimmed.startsWith("## ") -> {
            Text(
                text = parseInlineMarkdown(trimmed.removePrefix("## ")),
                style = MaterialTheme.typography.titleMedium,
                color = MatrixGreen,
                fontWeight = FontWeight.Bold
            )
        }
        trimmed.startsWith("# ") -> {
            Text(
                text = parseInlineMarkdown(trimmed.removePrefix("# ")),
                style = MaterialTheme.typography.headlineSmall,
                color = MatrixGreen,
                fontWeight = FontWeight.Bold
            )
        }
        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "▸",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MatrixGreen
                )
                Text(
                    text = parseInlineMarkdown(trimmed.drop(2)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
        }
        trimmed.startsWith("> ") -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MatrixGreenSubtle, RoundedCornerShape(4.dp))
                    .border(1.dp, MatrixGreen.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = parseInlineMarkdown(trimmed.removePrefix("> ")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
        }
        else -> {
            Text(
                text = parseInlineMarkdown(line),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun CodeBlockCard(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    Surface(
        color = CodeBackground,
        border = BorderStroke(1.dp, MatrixGreen.copy(alpha = 0.30f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ElevatedSurface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (language.ifBlank { "CODE" }).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MatrixGreen
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            clipboard.setText(AnnotatedString(code))
                            copied = true
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) MatrixGreen else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (copied) "COPIED" else "COPY",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (copied) MatrixGreen else TextMuted
                    )
                }
            }
            HorizontalDivider(color = SubtleBorder)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = highlightCode(code),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun SecurityAuditTable(
    headers: List<String>,
    rows: List<List<String>>
) {
    val scrollState = rememberScrollState()
    Surface(
        color = VoidBackground,
        border = BorderStroke(1.dp, MatrixGreen.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            Row(
                modifier = Modifier
                    .background(MatrixGreenSubtle)
                    .height(IntrinsicSize.Min)
            ) {
                headers.forEach { header ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelMedium,
                        color = MatrixGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .width(150.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.35f))
            rows.forEachIndexed { rIdx, row ->
                Row(
                    modifier = Modifier
                        .background(if (rIdx % 2 == 0) VoidBackground else ElevatedSurface)
                        .height(IntrinsicSize.Min)
                ) {
                    headers.indices.forEach { cIdx ->
                        val cell = row.getOrElse(cIdx) { "" }
                        Text(
                            text = parseInlineMarkdown(cell),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            modifier = Modifier
                                .width(150.dp)
                                .fillMaxHeight()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
                if (rIdx < rows.lastIndex) {
                    HorizontalDivider(color = SubtleBorder)
                }
            }
        }
    }
}

private fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MatrixGreen,
                                background = MatrixGreenSubtle,
                                fontSize = 12.sp
                            )
                        ) {
                            append(" ${text.substring(i + 1, end)} ")
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text[i] == '*' && (i + 1 < text.length && text[i + 1] != ' ') -> {
                    val end = text.indexOf('*', i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = TextPrimary)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

private fun highlightCode(code: String): AnnotatedString {
    val keywords = setOf(
        "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "UNION", "JOIN",
        "const", "let", "var", "function", "return", "if", "else", "for", "while",
        "import", "from", "def", "class", "try", "except", "fun", "val", "echo",
        "require", "prepare", "execute", "sudo", "nmap", "curl", "http", "https"
    )
    return buildAnnotatedString {
        val lines = code.lines()
        lines.forEachIndexed { idx, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.startsWith("--")) {
                withStyle(SpanStyle(color = SyntaxComment)) {
                    append(line)
                }
            } else {
                var i = 0
                while (i < line.length) {
                    val ch = line[i]
                    if (ch == '"' || ch == '\'') {
                        val quote = ch
                        var j = i + 1
                        while (j < line.length && line[j] != quote) {
                            if (line[j] == '\\' && j + 1 < line.length) j++
                            j++
                        }
                        val end = minOf(j + 1, line.length)
                        withStyle(SpanStyle(color = SyntaxString)) {
                            append(line.substring(i, end))
                        }
                        i = end
                    } else if (ch.isLetter() || ch == '_') {
                        var j = i
                        while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                        val token = line.substring(i, j)
                        if (token in keywords || token.uppercase() in keywords) {
                            withStyle(SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold)) {
                                append(token)
                            }
                        } else {
                            withStyle(SpanStyle(color = TextPrimary)) {
                                append(token)
                            }
                        }
                        i = j
                    } else if (ch.isDigit()) {
                        var j = i
                        while (j < line.length && (line[j].isDigit() || line[j] == '.')) j++
                        withStyle(SpanStyle(color = SyntaxNumber)) {
                            append(line.substring(i, j))
                        }
                        i = j
                    } else {
                        withStyle(SpanStyle(color = TextMuted)) {
                            append(ch)
                        }
                        i++
                    }
                }
            }
            if (idx < lines.lastIndex) append("\n")
        }
    }
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.lines()
    val result = mutableListOf<MarkdownBlock>()
    val paragraphBuffer = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            result.add(MarkdownBlock.TextParagraph(paragraphBuffer.toList()))
            paragraphBuffer.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            flushParagraph()
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            result.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            i++
        } else if (trimmed.startsWith("|") && trimmed.endsWith("|") &&
            i + 1 < lines.size && lines[i + 1].trim().matches(Regex("^\\|[\\s:\\-|]+\\|$"))
        ) {
            flushParagraph()
            val headers = trimmed.trim('|').split("|").map { it.trim() }
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                val cells = lines[i].trim().trim('|').split("|").map { it.trim() }
                rows.add(cells)
                i++
            }
            result.add(MarkdownBlock.TableBlock(headers, rows))
        } else {
            if (trimmed.isEmpty()) {
                flushParagraph()
            } else {
                paragraphBuffer.add(line)
            }
            i++
        }
    }
    flushParagraph()
    return result
}
