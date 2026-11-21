package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.domain.files.FileProcessor
import com.example.domain.model.AttachmentItem
import com.example.domain.model.ChatMessage
import com.example.domain.model.MessageRole
import com.example.ui.components.AiStudioLogoBadge
import com.example.ui.components.MarkdownText
import com.example.ui.theme.*
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatMessageCard(
    message: ChatMessage,
    isLastAssistant: Boolean,
    isGenerating: Boolean,
    isSpeakingThis: Boolean,
    onEditUserMessage: (ChatMessage, String) -> Unit,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
    onToggleSpeak: (ChatMessage) -> Unit
) {
    val context = LocalContext.current
    var isEditing by remember(message.id) { mutableStateOf(false) }
    var editBuffer by remember(message.id, message.content) { mutableStateOf(message.content) }
    var copiedNotice by remember { mutableStateOf(false) }

    LaunchedEffect(copiedNotice) {
        if (copiedNotice) {
            kotlinx.coroutines.delay(1500)
            copiedNotice = false
        }
    }

    if (message.role == MessageRole.USER) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                color = ObsidianSurfaceHighest,
                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.widthIn(max = 560.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (message.attachments.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            message.attachments.forEach { att ->
                                AttachmentBadge(att = att, onRemove = null)
                            }
                        }
                    }

                    if (isEditing) {
                        OutlinedTextField(
                            value = editBuffer,
                            onValueChange = { editBuffer = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { isEditing = false }) { Text("Cancel") }
                            Button(onClick = {
                                isEditing = false
                                onEditUserMessage(message, editBuffer)
                            }) { Text("Save & Resend") }
                        }
                    } else {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            if (!isEditing && !isGenerating) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { isEditing = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Edit message",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (message.isError) RoseContainer.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.surface
                )
                .border(
                    1.dp,
                    if (message.isError) RoseError.copy(alpha = 0.5f) else ObsidianBorder.copy(alpha = 0.7f),
                    RoundedCornerShape(16.dp)
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiStudioLogoBadge(sizeDp = 22)
                Text(
                    text = message.modelUsed.ifBlank { "AI Studio" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (message.isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                }
                Spacer(Modifier.weight(1f))
                if (message.tokenInfo.isNotBlank()) {
                    Text(
                        text = message.tokenInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (message.skillsUsed.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.skillsUsed.forEach { skillName ->
                        Surface(
                            color = DeepIndigoContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Skill: $skillName",
                                style = MaterialTheme.typography.labelSmall,
                                color = IndigoPulse,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            MarkdownText(
                markdown = message.content.ifBlank { if (message.isStreaming) "Thinking..." else "" },
                textColor = if (message.isError) RoseError else MaterialTheme.colorScheme.onSurface
            )

            if (message.citations.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Sources",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        message.citations.forEach { c ->
                            AssistChip(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.url)))
                                    }
                                },
                                label = {
                                    Text(
                                        text = "[${c.index}] ${c.title.ifBlank { c.url }}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 200.dp)
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                }
            }

            if (!message.isStreaming && message.content.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("AI Response", message.content))
                            copiedNotice = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            if (copiedNotice) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                            contentDescription = "Copy response",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (copiedNotice) "Copied" else "Copy", style = MaterialTheme.typography.labelMedium)
                    }

                    TextButton(
                        onClick = { onToggleSpeak(message) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            if (isSpeakingThis) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = if (isSpeakingThis) "Stop speaking" else "Read aloud",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isSpeakingThis) "Stop" else "Read", style = MaterialTheme.typography.labelMedium)
                    }

                    if (isLastAssistant && !isGenerating) {
                        TextButton(
                            onClick = onRegenerate,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("regenerate_btn")
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Regenerate", modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(
                            onClick = onContinue,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("continue_btn")
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = "Continue", modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Continue", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentBadge(
    att: AttachmentItem,
    onRemove: (() -> Unit)?
) {
    Surface(
        color = ObsidianSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (att.isImage && att.localCachePath.isNotBlank() && File(att.localCachePath).exists()) {
                Image(
                    painter = rememberAsyncImagePainter(File(att.localCachePath)),
                    contentDescription = att.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
                )
            } else {
                Icon(
                    imageVector = if (att.isPdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.Description,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.widthIn(max = 150.dp)) {
                Text(
                    text = att.fileName,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(FileProcessor.formatBytes(att.sizeBytes))
                        if (att.pageCount > 0) append(" • ${att.pageCount}p")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove attachment", modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}
