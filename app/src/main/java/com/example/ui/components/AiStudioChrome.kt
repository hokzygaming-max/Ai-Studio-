package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun AiStudioLogoBadge(sizeDp: Int = 32, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.img_ai_studio_icon),
        contentDescription = "AI Studio Logo",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape((sizeDp / 3).dp))
            .border(1.dp, ObsidianBorder, RoundedCornerShape((sizeDp / 3).dp))
    )
}

@Composable
fun AiStudioSplashOverlay(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(850)
        visible = false
        delay(250)
        onFinished()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(ObsidianBg, ObsidianSurface)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AiStudioLogoBadge(sizeDp = 88)
                Text(
                    text = "AI Studio",
                    style = MaterialTheme.typography.displaySmall,
                    color = SlateTextPrimary
                )
                Text(
                    text = "Open-Source Multi-Provider AI Workspace",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateTextSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    models: List<AiModel>,
    selectedModelKey: String?,
    onSelectModel: (AiModel) -> Unit,
    onNavigateToModels: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(models, query) {
        if (query.isBlank()) models
        else models.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                it.modelId.contains(query, ignoreCase = true) ||
                it.providerType.displayName.contains(query, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select AI Model", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = {
                    onDismiss()
                    onNavigateToModels()
                }) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Manage Models")
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search models or providers...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("model_picker_search")
            )

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Memory, contentDescription = null, modifier = Modifier.size(36.dp), tint = SlateTextMuted)
                    Text(
                        text = if (models.isEmpty()) "No models configured yet. Add a provider API key or local Ollama endpoint."
                        else "No matching models found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = {
                        onDismiss()
                        onNavigateToModels()
                    }) {
                        Text("Configure Providers & Models")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.key }) { model ->
                        val isSelected = model.key == selectedModelKey
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.surfaceContainer
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onSelectModel(model)
                                    onDismiss()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (model.isFavorite) {
                                        Icon(Icons.Filled.Star, contentDescription = "Favorite", tint = AmberSpark, modifier = Modifier.size(14.dp))
                                    }
                                    if (model.isDefault) {
                                        Surface(color = DeepCyanContainer, shape = RoundedCornerShape(4.dp)) {
                                            Text("DEFAULT", style = MaterialTheme.typography.labelSmall, color = ElectricCyan, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                }
                                Text(
                                    text = buildString {
                                        append(model.providerType.displayName)
                                        model.contextWindow?.let { append(" • ${it / 1000}k ctx") }
                                        if (model.capabilities.supportsVision) append(" • Vision")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationSettingsSheet(
    conversation: Conversation,
    modelCapabilities: ModelCapabilities,
    presets: List<SystemPromptPreset>,
    allSkills: List<Skill>,
    onSave: (String, ChatGenerationSettings, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var systemPrompt by remember { mutableStateOf(conversation.systemPrompt) }
    var temp by remember { mutableFloatStateOf(conversation.settings.temperature) }
    var maxTokensText by remember { mutableStateOf(conversation.settings.maxTokens.toString()) }
    var topP by remember { mutableFloatStateOf(conversation.settings.topP) }
    var freqPenalty by remember { mutableFloatStateOf(conversation.settings.frequencyPenalty) }
    var presPenalty by remember { mutableFloatStateOf(conversation.settings.presencePenalty) }
    var streaming by remember { mutableStateOf(conversation.settings.streamingEnabled) }
    var webSearch by remember { mutableStateOf(conversation.settings.webSearchEnabled) }
    var stopSeqText by remember { mutableStateOf(conversation.settings.stopSequences.joinToString(", ")) }
    var selectedSkills by remember { mutableStateOf(conversation.activeSkillIds.toSet()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Conversation Settings", style = MaterialTheme.typography.titleLarge)

            if (modelCapabilities.supportsSystemPrompt) {
                Text("System Instructions & Presets", style = MaterialTheme.typography.titleSmall)
                if (presets.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        presets.forEach { preset ->
                            FilterChip(
                                selected = systemPrompt == preset.content,
                                onClick = { systemPrompt = preset.content },
                                label = { Text(preset.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Prompt") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (modelCapabilities.supportsStreaming) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Stream Responses", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = streaming, onCheckedChange = { streaming = it })
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Live Web Search", style = MaterialTheme.typography.bodyLarge)
                    Text("Requires configured search provider in Settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = webSearch, onCheckedChange = { webSearch = it })
            }

            if (modelCapabilities.supportsTemperature) {
                Column {
                    Text("Temperature: ${String.format("%.2f", temp)}", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = temp, onValueChange = { temp = it }, valueRange = 0f..1.5f)
                }
            }

            OutlinedTextField(
                value = maxTokensText,
                onValueChange = { maxTokensText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Max Output Tokens") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (modelCapabilities.supportsTopP) {
                Column {
                    Text("Top P: ${String.format("%.2f", topP)}", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = topP, onValueChange = { topP = it }, valueRange = 0.1f..1.0f)
                }
            }

            if (modelCapabilities.supportsFrequencyPenalty) {
                Column {
                    Text("Frequency Penalty: ${String.format("%.2f", freqPenalty)}", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = freqPenalty, onValueChange = { freqPenalty = it }, valueRange = -2f..2f)
                }
            }

            if (modelCapabilities.supportsPresencePenalty) {
                Column {
                    Text("Presence Penalty: ${String.format("%.2f", presPenalty)}", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = presPenalty, onValueChange = { presPenalty = it }, valueRange = -2f..2f)
                }
            }

            if (modelCapabilities.supportsStopSequences) {
                OutlinedTextField(
                    value = stopSeqText,
                    onValueChange = { stopSeqText = it },
                    label = { Text("Stop Sequences (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text("Pinned Skills for This Chat", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                allSkills.forEach { skill ->
                    val active = skill.id in selectedSkills
                    FilterChip(
                        selected = active,
                        onClick = {
                            selectedSkills = if (active) selectedSkills - skill.id else selectedSkills + skill.id
                        },
                        label = { Text(skill.name) }
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val stops = stopSeqText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val maxTok = maxTokensText.toIntOrNull()?.coerceIn(64, 128000) ?: 4096
                        onSave(
                            systemPrompt,
                            ChatGenerationSettings(
                                temperature = temp,
                                maxTokens = maxTok,
                                topP = topP,
                                frequencyPenalty = freqPenalty,
                                presencePenalty = presPenalty,
                                streamingEnabled = streaming,
                                stopSequences = stops,
                                webSearchEnabled = webSearch
                            ),
                            selectedSkills.toList()
                        )
                        onDismiss()
                    },
                    modifier = Modifier.testTag("save_conv_settings_btn")
                ) {
                    Text("Apply")
                }
            }
        }
    }
}
