package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.SystemPromptPreset
import com.example.domain.model.WebSearchProviderType
import com.example.domain.voice.VoiceManager
import com.example.ui.theme.*
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ProvidersModelsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: ProvidersModelsViewModel,
    chatViewModel: ChatViewModel,
    voiceManager: VoiceManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()
    val voices by voiceManager.availableVoices.collectAsStateWithLifecycle()

    var globalPrompt by remember(prefs.globalSystemPrompt) { mutableStateOf(prefs.globalSystemPrompt) }
    var temp by remember(prefs.defaultTemperature) { mutableFloatStateOf(prefs.defaultTemperature) }
    var maxTokensText by remember(prefs.defaultMaxTokens) { mutableStateOf(prefs.defaultMaxTokens.toString()) }
    var streaming by remember(prefs.defaultStreaming) { mutableStateOf(prefs.defaultStreaming) }

    var searchType by remember(prefs.webSearchProvider) { mutableStateOf(prefs.webSearchProvider) }
    var searchEndpoint by remember(prefs.webSearchCustomEndpoint) { mutableStateOf(prefs.webSearchCustomEndpoint) }
    var searchApiKey by remember { mutableStateOf("") }

    var speechRate by remember(prefs.ttsSpeechRate) { mutableFloatStateOf(prefs.ttsSpeechRate) }
    var selectedVoice by remember(prefs.selectedTtsVoice) { mutableStateOf(prefs.selectedTtsVoice) }

    var showPresetDialog by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<SystemPromptPreset?>(null) }
    var showDeleteAllChatsConfirm by remember { mutableStateOf(false) }
    var pendingJsonExport by remember { mutableStateOf<String?>(null) }

    val exportAllLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingJsonExport
        if (uri != null && json != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            pendingJsonExport = null
        }
    }

    val importChatsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (raw != null) chatViewModel.importChatsJson(raw)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings & Privacy") })

        if (statusMsg != null) {
            Surface(color = DeepCyanContainer, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(statusMsg!!, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearStatus() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Global Instructions & Presets
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Global Assistant Instructions & Presets", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = globalPrompt,
                        onValueChange = { globalPrompt = it },
                        label = { Text("Default System Prompt") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.updateGlobalSystemPrompt(globalPrompt) },
                        modifier = Modifier.testTag("save_global_prompt_btn")
                    ) { Text("Save Global Instructions") }

                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("System Prompt Presets", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { showPresetDialog = true }) { Text("+ Add Preset") }
                    }
                    presets.forEach { preset ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(preset.name, style = MaterialTheme.typography.bodyMedium)
                                Text(preset.content, style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary, maxLines = 1)
                            }
                            IconButton(onClick = { editingPreset = preset }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit preset", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.deletePromptPreset(preset.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete preset", tint = RoseError, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Defaults & Skills
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Generation & Interface Defaults", style = MaterialTheme.typography.titleMedium)
                    Text("Default Temperature: ${String.format("%.2f", temp)}", style = MaterialTheme.typography.bodySmall)
                    Slider(value = temp, onValueChange = { temp = it }, valueRange = 0f..1.5f)

                    OutlinedTextField(
                        value = maxTokensText,
                        onValueChange = { maxTokensText = it.filter { c -> c.isDigit() } },
                        label = { Text("Default Max Output Tokens") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Stream Responses by Default")
                        Switch(checked = streaming, onCheckedChange = { streaming = it })
                    }
                    Button(onClick = {
                        viewModel.updateDefaults(temp, maxTokensText.toIntOrNull() ?: 4096, streaming)
                    }) { Text("Save Generation Defaults") }

                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Discover Relevant Skills", style = MaterialTheme.typography.bodyMedium)
                            Text("Matches enabled SKILL.md modules to your prompt", style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary)
                        }
                        Switch(checked = prefs.autoDiscoverSkills, onCheckedChange = { viewModel.updateAutoDiscoverSkills(it) })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Dark Obsidian Interface", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = prefs.darkTheme, onCheckedChange = { viewModel.updateDarkTheme(it) })
                    }
                }
            }

            // Web Search Integration
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Live Web Search Provider", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (searchType == WebSearchProviderType.NONE) "Live web search is currently disabled. Configure Tavily, Brave, Serper, or a self-hosted SearXNG instance to ground answers with real citations."
                        else "Search queries are sent only when Web Search is toggled on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        WebSearchProviderType.entries.forEach { t ->
                            FilterChip(
                                selected = searchType == t,
                                onClick = {
                                    searchType = t
                                    if (searchEndpoint.isBlank()) searchEndpoint = t.placeholderEndpoint
                                },
                                label = { Text(t.displayName) }
                            )
                        }
                    }

                    if (searchType != WebSearchProviderType.NONE) {
                        OutlinedTextField(
                            value = searchEndpoint,
                            onValueChange = { searchEndpoint = it },
                            label = { Text("Search Endpoint URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (searchType.requiresKey) {
                            val masked = viewModel.maskedWebSearchKey()
                            OutlinedTextField(
                                value = searchApiKey,
                                onValueChange = { searchApiKey = it },
                                label = { Text(if (masked.isNotEmpty()) "Search API Key ($masked)" else "Search API Key") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Button(onClick = { viewModel.updateWebSearch(searchType, searchEndpoint, searchApiKey) }) {
                        Text("Save Web Search Settings")
                    }
                }
            }

            // Voice & Speech
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("On-Device Voice & Text-to-Speech", style = MaterialTheme.typography.titleMedium)
                    Text("Uses Android's built-in SpeechRecognizer and TextToSpeech engines with zero paid speech APIs.", style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary)
                    Text("Speech Rate: ${String.format("%.2fx", speechRate)}", style = MaterialTheme.typography.bodySmall)
                    Slider(value = speechRate, onValueChange = { speechRate = it }, valueRange = 0.6f..1.8f)

                    if (voices.isNotEmpty()) {
                        Text("Select TTS Voice (${voices.size} installed)", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            voices.take(8).forEach { v ->
                                FilterChip(
                                    selected = selectedVoice == v.name,
                                    onClick = { selectedVoice = v.name },
                                    label = { Text("${v.localeLabel}${if (v.isLocal) " (Offline)" else ""}") }
                                )
                            }
                        }
                    }
                    Button(onClick = { viewModel.updateVoiceSettings(prefs.ttsAutoRead, selectedVoice, speechRate) }) {
                        Text("Save Voice Settings")
                    }
                }
            }

            // Local AI Info
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Local AI & Hardware Notes", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Connect to Ollama or any local OpenAI-compatible server (llama.cpp, LM Studio, LocalAI) in Models -> Providers. Local inference speed and context capacity depend directly on your device or host machine RAM, CPU/GPU, and thermal limits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Privacy, Security & Data Export
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Privacy, Data & Open-Source Pledge", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "• API keys are encrypted with Android Keystore AES-256-GCM and never exported.\n" +
                            "• All chats, projects, and skills remain locally on your device.\n" +
                            "• 100% free and open source: no subscriptions, no ads, no locked features, no tracking.\n" +
                            "• Third-party API providers govern their own usage limits and billing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    pendingJsonExport = chatViewModel.exportAllChatsJson()
                                    exportAllLauncher.launch("ai-studio-chats-export.json")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Export Chats") }

                        OutlinedButton(
                            onClick = { importChatsLauncher.launch(arrayOf("application/json")) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Import Chats") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.clearCachedFiles() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Clear File Cache") }

                        OutlinedButton(
                            onClick = { showDeleteAllChatsConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                            modifier = Modifier.weight(1f)
                        ) { Text("Delete All Chats") }
                    }
                }
            }
        }
    }

    if (showPresetDialog || editingPreset != null) {
        var name by remember { mutableStateOf(editingPreset?.name.orEmpty()) }
        var content by remember { mutableStateOf(editingPreset?.content.orEmpty()) }
        AlertDialog(
            onDismissRequest = {
                showPresetDialog = false
                editingPreset = null
            },
            title = { Text(if (editingPreset == null) "New Prompt Preset" else "Edit Preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Preset Name") }, singleLine = true)
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Instructions") }, minLines = 4, maxLines = 8)
                }
            },
            confirmButton = {
                Button(enabled = name.isNotBlank() && content.isNotBlank(), onClick = {
                    viewModel.savePromptPreset(name, content, editingPreset?.id)
                    showPresetDialog = false
                    editingPreset = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPresetDialog = false
                    editingPreset = null
                }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteAllChatsConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllChatsConfirm = false },
            title = { Text("Delete All Chat History?") },
            text = { Text("This permanently removes all local conversations and messages. API keys and projects are kept.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllConversations()
                        showDeleteAllChatsConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError)
                ) { Text("Delete All") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAllChatsConfirm = false }) { Text("Cancel") } }
        )
    }
}
