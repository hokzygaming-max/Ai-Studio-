package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Bullet(val text: String, val marker: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Heading -> {
                        val style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }
                        Text(
                            text = buildInlineAnnotated(block.text, textColor, MaterialTheme.colorScheme.primary),
                            style = style.copy(fontWeight = FontWeight.Bold),
                            color = textColor,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    is MdBlock.CodeBlock -> {
                        CodeBlockCard(language = block.language, code = block.code)
                    }
                    is MdBlock.Quote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(18.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            RichInlineText(
                                text = block.text,
                                textColor = textColor.copy(alpha = 0.9f)
                            )
                        }
                    }
                    is MdBlock.Bullet -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = block.marker,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.widthIn(min = 20.dp)
                            )
                            RichInlineText(text = block.text, textColor = textColor)
                        }
                    }
                    is MdBlock.Paragraph -> {
                        RichInlineText(text = block.text, textColor = textColor)
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun RichInlineText(text: String, textColor: Color) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, textColor, linkColor) {
        buildInlineAnnotated(text, textColor, linkColor)
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { ann ->
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ann.item))
                        context.startActivity(intent)
                    }
                }
        }
    )
}

@Composable
fun CodeBlockCard(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    val highlighted = remember(code, language) { highlightCodeSyntax(code) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CodeBg)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurfaceElevated)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "code" }.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(
                onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("code", code))
                    copied = true
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.heightIn(min = 32.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    contentDescription = "Copy code",
                    modifier = Modifier.size(14.dp),
                    tint = if (copied) EmeraldOk else SlateTextSecondary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (copied) "Copied" else "Copy",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (copied) EmeraldOk else SlateTextSecondary
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = highlighted,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            )
        }
    }
}

private fun parseMarkdownBlocks(input: String): List<MdBlock> {
    val result = mutableListOf<MdBlock>()
    val lines = input.lines()
    var i = 0
    val paraBuffer = mutableListOf<String>()

    fun flushPara() {
        if (paraBuffer.isNotEmpty()) {
            result.add(MdBlock.Paragraph(paraBuffer.joinToString("\n")))
            paraBuffer.clear()
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            flushPara()
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            result.add(MdBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            i++
            continue
        }
        when {
            trimmed.isBlank() -> flushPara()
            trimmed.startsWith("### ") -> {
                flushPara()
                result.add(MdBlock.Heading(3, trimmed.removePrefix("### ").trim()))
            }
            trimmed.startsWith("## ") -> {
                flushPara()
                result.add(MdBlock.Heading(2, trimmed.removePrefix("## ").trim()))
            }
            trimmed.startsWith("# ") -> {
                flushPara()
                result.add(MdBlock.Heading(1, trimmed.removePrefix("# ").trim()))
            }
            trimmed.startsWith("> ") -> {
                flushPara()
                result.add(MdBlock.Quote(trimmed.removePrefix("> ").trim()))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushPara()
                result.add(MdBlock.Bullet(trimmed.drop(2).trim(), "•"))
            }
            Regex("^\\d+\\.\\s+.*").matches(trimmed) -> {
                flushPara()
                val dot = trimmed.indexOf('.')
                val marker = trimmed.substring(0, dot + 1)
                result.add(MdBlock.Bullet(trimmed.substring(dot + 1).trim(), marker))
            }
            else -> paraBuffer.add(line)
        }
        i++
    }
    flushPara()
    return result
}

private fun buildInlineAnnotated(
    text: String,
    baseColor: Color,
    linkColor: Color
): AnnotatedString = buildAnnotatedString {
    val tokenRegex = Regex("(`[^`]+`|\\*\\*[^*]+\\*\\*|\\*[^*]+\\*|\\[([^\\]]+)\\]\\((https?://[^)]+)\\)|https?://\\S+)")
    var cursor = 0
    for (m in tokenRegex.findAll(text)) {
        if (m.range.first > cursor) {
            withStyle(SpanStyle(color = baseColor)) {
                append(text.substring(cursor, m.range.first))
            }
        }
        val tok = m.value
        when {
            tok.startsWith("`") && tok.endsWith("`") && tok.length >= 2 -> {
                withStyle(
                    SpanStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        background = ObsidianSurfaceHighest,
                        color = ElectricCyan,
                        fontSize = 13.sp
                    )
                ) {
                    append(" ${tok.substring(1, tok.length - 1)} ")
                }
            }
            tok.startsWith("**") && tok.endsWith("**") && tok.length >= 4 -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                    append(tok.substring(2, tok.length - 2))
                }
            }
            tok.startsWith("*") && tok.endsWith("*") && tok.length >= 2 -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                    append(tok.substring(1, tok.length - 1))
                }
            }
            tok.startsWith("[") && m.groups[2] != null && m.groups[3] != null -> {
                val label = m.groups[2]!!.value
                val url = m.groups[3]!!.value
                pushStringAnnotation(tag = "URL", annotation = url)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(label)
                }
                pop()
            }
            tok.startsWith("http") -> {
                pushStringAnnotation(tag = "URL", annotation = tok)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(tok)
                }
                pop()
            }
        }
        cursor = m.range.last + 1
    }
    if (cursor < text.length) {
        withStyle(SpanStyle(color = baseColor)) {
            append(text.substring(cursor))
        }
    }
}

private val codeKeywords = setOf(
    "fun", "val", "var", "class", "interface", "object", "data", "sealed", "enum", "return",
    "if", "else", "when", "for", "while", "suspend", "import", "package", "private", "public",
    "override", "def", "async", "await", "function", "const", "let", "export", "from", "try",
    "catch", "finally", "throw", "new", "true", "false", "null", "None", "SELECT", "FROM",
    "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE", "TABLE", "struct", "impl", "fn", "pub"
)

private fun highlightCodeSyntax(code: String): AnnotatedString = buildAnnotatedString {
    val lines = code.lines()
    lines.forEachIndexed { idx, line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("//") || trimmed.startsWith("# ") || trimmed.startsWith("-- ")) {
            withStyle(SpanStyle(color = CodeComment, fontStyle = FontStyle.Italic)) {
                append(line)
            }
        } else {
            val tokenRegex = Regex("(\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"|'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'|\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Za-z_][A-Za-z0-9_]*\\b)")
            var pos = 0
            for (m in tokenRegex.findAll(line)) {
                if (m.range.first > pos) {
                    withStyle(SpanStyle(color = SlateTextPrimary)) {
                        append(line.substring(pos, m.range.first))
                    }
                }
                val tok = m.value
                val color = when {
                    tok.startsWith("\"") || tok.startsWith("'") -> CodeString
                    tok.first().isDigit() -> CodeNumber
                    tok in codeKeywords -> CodeKeyword
                    tok.first().isUpperCase() -> CodeType
                    else -> SlateTextPrimary
                }
                withStyle(SpanStyle(color = color)) {
                    append(tok)
                }
                pos = m.range.last + 1
            }
            if (pos < line.length) {
                withStyle(SpanStyle(color = SlateTextPrimary)) {
                    append(line.substring(pos))
                }
            }
        }
        if (idx < lines.lastIndex) append("\n")
    }
}
