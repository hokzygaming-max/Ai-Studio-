package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProvidersModelsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelsProvidersScreen(
    viewModel: ProvidersModelsViewModel,
    modifier: Modifier = Modifier
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val busyProviderId by viewModel.busyProviderId.collectAsStateWithLifecycle()
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var modelFilter by remember { mutableStateOf("ALL") } // ALL, FAVORITES, VISION, RECENT

    var editingProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var addingProviderType by remember { mutableStateOf<ProviderType?>(null) }
    var showAddProviderPicker by remember { mutableStateOf(false) }

    var editingModel by remember { mutableStateOf<AiModel?>(null) }
    var showManualModelDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Models & Providers") },
            actions = {
                TextButton(
                    onClick = {
                        if (selectedTab == 0 && providers.isNotEmpty()) {
                            showManualModelDialog = true
                        } else {
                            showAddProviderPicker = true
                        }
                    },
                    modifier = Modifier.testTag("add_provider_or_model_btn")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (selectedTab == 0 && providers.isNotEmpty()) "Add Model" else "Add Provider")
                }
            }
        )

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Models (${models.size})") },
                modifier = Modifier.testTag("tab_models")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Providers (${providers.size})") },
                modifier = Modifier.testTag("tab_providers")
            )
        }

        if (statusMsg != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(statusMsg!!, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearStatus() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (selectedTab == 0) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search models by ID or provider...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("models_search_field")
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL" to "All", "FAVORITES" to "Favorites", "VISION" to "Vision", "RECENT" to "Recent").forEach { (key, label) ->
                        FilterChip(
                            selected = modelFilter == key,
                            onClick = { modelFilter = key },
                            label = { Text(label) }
                        )
                    }
                }

                val filtered = remember(models, searchQuery, modelFilter) {
                    models.filter { m ->
                        val matchesQuery = searchQuery.isBlank() ||
                            m.displayName.contains(searchQuery, true) ||
                            m.modelId.contains(searchQuery, true) ||
                            m.providerType.displayName.contains(searchQuery, true)
                        val matchesFilter = when (modelFilter) {
                            "FAVORITES" -> m.isFavorite
                            "VISION" -> m.capabilities.supportsVision
                            "RECENT" -> m.lastUsedAt > 0L
                            else -> true
                        }
                        matchesQuery && matchesFilter
                    }.let { list ->
                        if (modelFilter == "RECENT") list.sortedByDescending { it.lastUsedAt } else list
                    }
                }

                if (filtered.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("No Models Available", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (providers.isEmpty()) "Add a provider configuration (API key or local Ollama endpoint) to automatically discover available models or add model IDs manually."
                                else "Sync models from a configured provider or tap Add Model to enter a custom model ID.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = {
                                if (providers.isEmpty()) showAddProviderPicker = true else showManualModelDialog = true
                            }) {
                                Text(if (providers.isEmpty()) "Add AI Provider" else "Add Manual Model ID")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.key }) { model ->
                            ModelCard(
                                model = model,
                                onToggleFavorite = { viewModel.toggleFavoriteModel(model) },
                                onSetDefault = { viewModel.setDefaultModel(model.key) },
                                onEdit = { editingModel = model },
                                onDelete = { viewModel.deleteModel(model.key) }
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    OutlinedButton(
                        onClick = { showAddProviderPicker = true },
                        modifier = Modifier.fillMaxWidth().testTag("open_provider_catalog_btn")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add New Provider Configuration (16+ Supported)")
                    }
                }

                if (providers.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = BorderStroke(1.dp, ObsidianBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Bring Your Own API Keys or Local AI", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Supported: OpenAI, Anthropic Claude, DeepSeek, Google Gemini, OpenRouter, Groq, Mistral AI, Together AI, Fireworks AI, Perplexity, xAI, Cerebras, Cohere, Hugging Face, Ollama, and Custom OpenAI-compatible APIs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(providers, key = { it.id }) { provider ->
                        ProviderCard(
                            provider = provider,
                            maskedKey = viewModel.maskedApiKey(provider.id),
                            isBusy = busyProviderId == provider.id,
                            onTest = { viewModel.testProvider(provider.id) },
                            onSyncModels = { viewModel.refreshModels(provider.id) },
                            onEdit = { editingProvider = provider },
                            onDelete = { viewModel.deleteProvider(provider.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddProviderPicker) {
        AlertDialog(
            onDismissRequest = { showAddProviderPicker = false },
            title = { Text("Choose AI Provider") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(ProviderType.entries) { type ->
                        Surface(
                            onClick = {
                                showAddProviderPicker = false
                                addingProviderType = type
                            },
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(type.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    if (type.isLocalDefault) {
                                        Surface(color = EmeraldContainer, shape = RoundedCornerShape(4.dp)) {
                                            Text("LOCAL", style = MaterialTheme.typography.labelSmall, color = EmeraldOk, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                        }
                                    }
                                }
                                Text(type.pricingAndTierNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddProviderPicker = false }) { Text("Close") }
            }
        )
    }

    val provTypeToEdit = editingProvider?.providerType ?: addingProviderType
    if (provTypeToEdit != null) {
        ProviderEditorDialog(
            existing = editingProvider,
            providerType = provTypeToEdit,
            hasExistingKey = editingProvider?.let { viewModel.hasApiKey(it.id) } ?: false,
            onSave = { name, url, key, headers ->
                viewModel.saveProvider(
                    existingId = editingProvider?.id,
                    providerType = provTypeToEdit,
                    displayName = name,
                    baseUrl = url,
                    apiKeyInput = key,
                    customHeadersText = headers
                )
                editingProvider = null
                addingProviderType = null
            },
            onDismiss = {
                editingProvider = null
                addingProviderType = null
            }
        )
    }

    if (showManualModelDialog || editingModel != null) {
        ManualModelDialog(
            existing = editingModel,
            providers = providers,
            onSave = { provider, modelId, displayName, ctx, caps ->
                viewModel.addOrUpdateManualModel(
                    existingKey = editingModel?.key,
                    provider = provider,
                    modelId = modelId,
                    displayName = displayName,
                    contextWindow = ctx,
                    capabilities = caps
                )
                showManualModelDialog = false
                editingModel = null
            },
            onDismiss = {
                showManualModelDialog = false
                editingModel = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelCard(
    model: AiModel,
    onToggleFavorite: () -> Unit,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, if (model.isDefault) ElectricCyan else ObsidianBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${model.providerType.displayName} • ${model.modelId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (model.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite model",
                        tint = if (model.isFavorite) AmberSpark else SlateTextSecondary
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (model.isDefault) {
                    AssistChip(onClick = {}, label = { Text("Default") })
                }
                model.contextWindow?.let {
                    SuggestionChip(onClick = {}, label = { Text("${it / 1000}k ctx") })
                }
                if (model.capabilities.supportsVision) {
                    SuggestionChip(onClick = {}, label = { Text("Vision") })
                }
                if (model.capabilities.supportsPdfNative) {
                    SuggestionChip(onClick = {}, label = { Text("Native PDF") })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!model.isDefault) {
                    OutlinedButton(onClick = onSetDefault, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("Set Default", style = MaterialTheme.typography.labelMedium)
                    }
                }
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete", color = RoseError) }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderConfig,
    maskedKey: String,
    isBusy: Boolean,
    onTest: () -> Unit,
    onSyncModels: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (provider.lastStatus) {
        ConnectionStatus.CONNECTED -> EmeraldOk
        ConnectionStatus.ERROR -> RoseError
        ConnectionStatus.UNTESTED -> AmberSpark
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, ObsidianBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(provider.baseUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = provider.lastStatus.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (maskedKey.isNotEmpty()) {
                Text("Key (Android Keystore): $maskedKey", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
            } else if (provider.providerType.requiresApiKey) {
                Text("API Key: Not set", style = MaterialTheme.typography.labelSmall, color = RoseError)
            } else {
                Text("Local endpoint • No API key required", style = MaterialTheme.typography.labelSmall, color = EmeraldOk)
            }

            if (provider.lastStatusMessage.isNotBlank()) {
                Text(provider.lastStatusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(provider.providerType.pricingAndTierNote, style = MaterialTheme.typography.labelSmall, color = SlateTextMuted)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onTest, enabled = !isBusy, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    if (isBusy) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    else Text("Test")
                }
                if (provider.providerType.supportsModelListing) {
                    OutlinedButton(onClick = onSyncModels, enabled = !isBusy, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("Sync Models")
                    }
                }
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete", color = RoseError) }
            }
        }
    }
}

@Composable
private fun ProviderEditorDialog(
    existing: ProviderConfig?,
    providerType: ProviderType,
    hasExistingKey: Boolean,
    onSave: (String, String, String?, String) -> Unit,
    onDismiss: () -> Unit
) {
    var displayName by remember { mutableStateOf(existing?.displayName ?: providerType.displayName) }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl ?: providerType.defaultBaseUrl) }
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var customHeaders by remember {
        mutableStateOf(existing?.customHeaders?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add ${providerType.displayName}" else "Edit ${existing.displayName}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(providerType.pricingAndTierNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Configuration Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = {
                        Text(
                            when {
                                !providerType.requiresApiKey -> "Optional Bearer Token"
                                hasExistingKey -> "API Key (leave blank to keep existing)"
                                else -> "API Key"
                            }
                        )
                    },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = "Toggle key visibility")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("provider_api_key_input")
                )
                OutlinedTextField(
                    value = customHeaders,
                    onValueChange = { customHeaders = it },
                    label = { Text("Custom Headers (Key: Value per line)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(displayName, baseUrl, apiKey.ifBlank { null }, customHeaders) },
                modifier = Modifier.testTag("save_provider_btn")
            ) {
                Text("Save & Test")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ManualModelDialog(
    existing: AiModel?,
    providers: List<ProviderConfig>,
    onSave: (ProviderConfig, String, String, Int?, ModelCapabilities) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedProvider by remember {
        mutableStateOf(
            providers.firstOrNull { it.id == existing?.providerConfigId } ?: providers.firstOrNull()
        )
    }
    var modelId by remember { mutableStateOf(existing?.modelId.orEmpty()) }
    var displayName by remember { mutableStateOf(existing?.displayName.orEmpty()) }
    var contextText by remember { mutableStateOf(existing?.contextWindow?.toString().orEmpty()) }
    var vision by remember { mutableStateOf(existing?.capabilities?.supportsVision ?: false) }
    var streaming by remember { mutableStateOf(existing?.capabilities?.supportsStreaming ?: true) }
    var freqPenalty by remember { mutableStateOf(existing?.capabilities?.supportsFrequencyPenalty ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Model Manually" else "Edit Model") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (existing == null && providers.isNotEmpty()) {
                    Text("Provider", style = MaterialTheme.typography.labelLarge)
                    providers.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedProvider?.id == p.id, onClick = { selectedProvider = p })
                            Text(p.displayName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    enabled = existing == null,
                    label = { Text("Model ID (e.g. gpt-4o, sonar-pro, llama3.2)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contextText,
                    onValueChange = { contextText = it.filter { c -> c.isDigit() } },
                    label = { Text("Context Window Tokens (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = vision, onCheckedChange = { vision = it })
                    Text("Supports Vision / Images")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = streaming, onCheckedChange = { streaming = it })
                    Text("Supports Streaming")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = freqPenalty, onCheckedChange = { freqPenalty = it })
                    Text("Supports Frequency / Presence Penalty")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedProvider != null && modelId.isNotBlank(),
                onClick = {
                    selectedProvider?.let { prov ->
                        onSave(
                            prov,
                            modelId,
                            displayName,
                            contextText.toIntOrNull(),
                            (existing?.capabilities ?: ModelCapabilities()).copy(
                                supportsVision = vision,
                                supportsStreaming = streaming,
                                supportsFrequencyPenalty = freqPenalty,
                                supportsPresencePenalty = freqPenalty
                            )
                        )
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
