package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.Conversation
import com.example.domain.model.MessageRole
import com.example.domain.model.ModelCapabilities
import com.example.domain.voice.VoiceManager
import com.example.ui.components.AiStudioLogoBadge
import com.example.ui.components.ConversationSettingsSheet
import com.example.ui.components.ModelPickerSheet
import com.example.ui.theme.*
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    voiceManager: VoiceManager,
    onOpenDrawer: () -> Unit,
    onNavigateToModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConv by viewModel.activeConversation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val presets by viewModel.promptPresets.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val banner by viewModel.uiBanner.collectAsStateWithLifecycle()

    val isListening by voiceManager.isListening.collectAsStateWithLifecycle()
    val partialTranscript by voiceManager.partialTranscript.collectAsStateWithLifecycle()
    val isSpeaking by voiceManager.isSpeaking.collectAsStateWithLifecycle()
    val speakingMsgId by voiceManager.speakingMessageId.collectAsStateWithLifecycle()
    val voiceError by voiceManager.voiceError.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    var showConvSettings by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var pendingMarkdownExport by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    val activeModel = remember(activeConv, models) {
        models.firstOrNull { it.key == activeConv?.modelKey }
            ?: models.firstOrNull { it.isDefault }
            ?: models.firstOrNull()
    }

    // Auto-scroll when messages stream
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // File pickers
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.attachFiles(uris)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        viewModel.attachFiles(uris)
    }

    val exportMarkdownLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        val content = pendingMarkdownExport
        if (uri != null && content != null) {
            scope.launch {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(content.toByteArray())
                }
                pendingMarkdownExport = null
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (raw != null) viewModel.importChatsJson(raw)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceManager.startListening { spoken ->
                inputText = if (inputText.isBlank()) spoken else "$inputText $spoken"
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top App Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.testTag("chat_drawer_button")
                ) {
                    Icon(Icons.Filled.Menu, contentDescription = "Open conversations drawer")
                }

                // Model Selector Pill
                Surface(
                    onClick = { showModelPicker = true },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag("chat_model_selector")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiStudioLogoBadge(sizeDp = 22)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeModel?.displayName ?: "Select Model",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeModel?.providerType?.displayName ?: "Tap to configure provider",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Choose model",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.startNewConversation() },
                    modifier = Modifier.testTag("new_chat_button")
                ) {
                    Icon(Icons.Outlined.AddComment, contentDescription = "New chat")
                }

                IconButton(
                    onClick = { showConvSettings = true },
                    modifier = Modifier.testTag("conv_settings_button")
                ) {
                    Icon(Icons.Outlined.Tune, contentDescription = "Conversation settings")
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Chat options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Export Chat (.md)") },
                            leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                            enabled = activeConv != null,
                            onClick = {
                                showMenu = false
                                activeConv?.let { conv ->
                                    scope.launch {
                                        val md = viewModel.exportConversationMarkdown(conv.id)
                                        if (md != null) {
                                            pendingMarkdownExport = md
                                            val safe = conv.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)
                                            exportMarkdownLauncher.launch("$safe.md")
                                        }
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import Chats (.json)") },
                            leadingIcon = { Icon(Icons.Outlined.UploadFile, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                importJsonLauncher.launch(arrayOf("application/json"))
                            }
                        )
                    }
                }
            }
        }

        // Banners (errors / warnings / voice errors)
        val activeBanner = banner ?: voiceError
        if (activeBanner != null) {
            Surface(
                color = DeepAmberContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = AmberSpark, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = activeBanner,
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            viewModel.dismissBanner()
                            voiceManager.clearError()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Main Chat Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                ChatEmptyState(
                    hasModels = models.isNotEmpty(),
                    activeModelName = activeModel?.displayName,
                    onConfigureProvider = onNavigateToModels,
                    onPromptChipClick = { starter -> inputText = starter },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val lastAssistantId = messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatMessageCard(
                            message = msg,
                            isLastAssistant = msg.id == lastAssistantId,
                            isGenerating = isGenerating,
                            isSpeakingThis = isSpeaking && speakingMsgId == msg.id,
                            onEditUserMessage = { m, newText -> viewModel.editUserMessageAndResend(m.id, newText) },
                            onRegenerate = { viewModel.regenerateLastResponse() },
                            onContinue = { viewModel.continueGeneration() },
                            onToggleSpeak = { m ->
                                if (isSpeaking && speakingMsgId == m.id) {
                                    voiceManager.stopSpeaking()
                                } else {
                                    voiceManager.speak(m.content, m.id, prefs.selectedTtsVoice, prefs.ttsSpeechRate)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Composer Area
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pendingAttachments.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pendingAttachments.forEach { att ->
                            AttachmentBadge(
                                att = att,
                                onRemove = { viewModel.removeAttachment(att.id) }
                            )
                        }
                    }
                }

                if (isListening) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DeepCyanContainer.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Text(
                            text = partialTranscript.ifBlank { "Listening..." },
                            style = MaterialTheme.typography.bodySmall,
                            color = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { voiceManager.stopListening() }) {
                            Text("Stop")
                        }
                    }
                }

                // Quick action chips above input
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = {
                            docPickerLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "text/*",
                                    "application/json",
                                    "application/xml",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "*/*"
                                )
                            )
                        },
                        label = { Text("Files") },
                        leadingIcon = { Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("attach_files_btn")
                    )
                    AssistChip(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        label = { Text("Images") },
                        leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("attach_images_btn")
                    )
                    val webOn = activeConv?.settings?.webSearchEnabled == true
                    FilterChip(
                        selected = webOn,
                        onClick = {
                            val current = activeConv ?: return@FilterChip
                            viewModel.updateConversationSettings(
                                current.systemPrompt,
                                current.settings.copy(webSearchEnabled = !webOn),
                                current.activeSkillIds
                            )
                        },
                        label = { Text("Web Search") },
                        leadingIcon = { Icon(Icons.Outlined.TravelExplore, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask AI Studio anything...") },
                        maxLines = 5,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field")
                    )

                    IconButton(
                        onClick = {
                            if (isListening) {
                                voiceManager.stopListening()
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    voiceManager.startListening { spoken ->
                                        inputText = if (inputText.isBlank()) spoken else "$inputText $spoken"
                                    }
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isListening) RoseContainer else ObsidianSurfaceElevated)
                            .testTag("voice_input_btn")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                            contentDescription = "Voice input",
                            tint = if (isListening) RoseError else ElectricCyan
                        )
                    }

                    if (isGenerating) {
                        FilledIconButton(
                            onClick = { viewModel.stopGeneration() },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = RoseError),
                            modifier = Modifier.size(48.dp).testTag("stop_generation_btn")
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = "Stop generation", tint = Color.White)
                        }
                    } else {
                        FilledIconButton(
                            onClick = {
                                val toSend = inputText
                                inputText = ""
                                viewModel.sendMessage(toSend)
                            },
                            enabled = inputText.isNotBlank() || pendingAttachments.isNotEmpty(),
                            modifier = Modifier.size(48.dp).testTag("send_message_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
                        }
                    }
                }
            }
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            models = models,
            selectedModelKey = activeModel?.key,
            onSelectModel = { model -> viewModel.updateConversationModel(model.key) },
            onNavigateToModels = onNavigateToModels,
            onDismiss = { showModelPicker = false }
        )
    }

    if (showConvSettings && activeConv != null) {
        ConversationSettingsSheet(
            conversation = activeConv!!,
            modelCapabilities = activeModel?.capabilities ?: ModelCapabilities(),
            presets = presets,
            allSkills = skills,
            onSave = { sys, settings, activeIds ->
                viewModel.updateConversationSettings(sys, settings, activeIds)
            },
            onDismiss = { showConvSettings = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatEmptyState(
    hasModels: Boolean,
    activeModelName: String?,
    onConfigureProvider: () -> Unit,
    onPromptChipClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, ObsidianBorder, RoundedCornerShape(20.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_ai_studio_hero),
                contentDescription = "AI Studio Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, ObsidianBg.copy(alpha = 0.92f))
                        )
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AiStudioLogoBadge(sizeDp = 42)
                    Column {
                        Text("AI Studio", style = MaterialTheme.typography.headlineSmall, color = SlateTextPrimary)
                        Text(
                            text = if (activeModelName != null) "Ready with $activeModelName"
                            else "Free, open-source, bring-your-own-key workspace",
                            style = MaterialTheme.typography.bodySmall,
                            color = ElectricCyan
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        if (!hasModels) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.widthIn(max = 520.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Connect an AI Provider to Begin", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Add your API key for OpenAI, Anthropic Claude, Google Gemini, DeepSeek, Groq, OpenRouter, or point to a local Ollama endpoint. Keys are encrypted locally with Android Keystore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onConfigureProvider,
                        modifier = Modifier.testTag("empty_configure_provider_btn")
                    ) {
                        Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Configure Provider & Models")
                    }
                }
            }
        } else {
            val starters = listOf(
                "Explain and review an attached file",
                "Design a Kotlin Jetpack Compose screen",
                "Compare two documents or APIs",
                "Debug a stack trace step by step"
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.widthIn(max = 520.dp)
            ) {
                starters.forEach { prompt ->
                    SuggestionChip(
                        onClick = { onPromptChipClick(prompt) },
                        label = { Text(prompt) }
                    )
                }
            }
        }
    }
}
