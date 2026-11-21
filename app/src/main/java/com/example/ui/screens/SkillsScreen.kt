package com.example.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.Skill
import com.example.ui.components.MarkdownText
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProjectsSkillsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SkillsScreen(
    viewModel: ProjectsSkillsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val banner by viewModel.bannerMessage.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var viewingSkill by remember { mutableStateOf<Skill?>(null) }
    var editingSkill by remember { mutableStateOf<Skill?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingExportSkill by remember { mutableStateOf<Skill?>(null) }
    var skillPendingTrust by remember { mutableStateOf<Skill?>(null) }

    val importSkillLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val pairs = uris.map { uri ->
            var name = "SKILL.md"
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) name = c.getString(idx) ?: name
            }
            uri to name
        }
        if (pairs.isNotEmpty()) viewModel.importSkillsFromUris(pairs)
    }

    val exportSkillLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val sk = pendingExportSkill
        if (uri != null && sk != null) {
            val bytes = viewModel.exportSkillZipBytes(sk)
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            pendingExportSkill = null
        }
    }

    val categories = remember(skills) {
        listOf("All") + skills.map { it.category }.distinct().sorted()
    }

    val filtered = remember(skills, searchQuery, selectedCategory) {
        skills.filter { s ->
            val catOk = selectedCategory == "All" || s.category.equals(selectedCategory, ignoreCase = true)
            val qOk = searchQuery.isBlank() ||
                s.name.contains(searchQuery, true) ||
                s.description.contains(searchQuery, true) ||
                s.category.contains(searchQuery, true)
            catOk && qOk
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Skills Library (${skills.count { it.isEnabled }}/${skills.size})") },
            actions = {
                IconButton(
                    onClick = { importSkillLauncher.launch(arrayOf("text/markdown", "text/plain", "application/zip", "*/*")) },
                    modifier = Modifier.testTag("import_skill_btn")
                ) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = "Import SKILL.md or ZIP")
                }
                IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("create_skill_btn")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Create new skill")
                }
            }
        )

        if (banner != null) {
            Surface(color = DeepCyanContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(banner!!, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearBanner() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed SKILL.md modules...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("skills_search_field")
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { skill ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, if (skill.isEnabled) ElectricCyan.copy(alpha = 0.6f) else ObsidianBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(skill.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Surface(color = DeepIndigoContainer, shape = RoundedCornerShape(4.dp)) {
                                            Text(skill.category, style = MaterialTheme.typography.labelSmall, color = IndigoPulse, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    Text(skill.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Switch(
                                    checked = skill.isEnabled,
                                    onCheckedChange = {
                                        if (!skill.isTrusted && !skill.isEnabled) {
                                            skillPendingTrust = skill
                                        } else {
                                            viewModel.toggleSkillEnabled(skill)
                                        }
                                    }
                                )
                            }

                            if (!skill.isTrusted) {
                                Surface(color = DeepAmberContainer, shape = RoundedCornerShape(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Outlined.Security, contentDescription = null, tint = AmberSpark, modifier = Modifier.size(18.dp))
                                        Text(
                                            "Untrusted imported skill contains scripts. Scripts are never auto-executed.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AmberSpark,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { skillPendingTrust = skill }) { Text("Review") }
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { viewingSkill = skill }) { Text("View SKILL.md") }
                                TextButton(onClick = { editingSkill = skill }) { Text("Edit") }
                                TextButton(onClick = {
                                    pendingExportSkill = skill
                                    val safe = skill.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                                    exportSkillLauncher.launch("$safe-skill.zip")
                                }) { Text("Export") }
                                if (!skill.isBuiltIn) {
                                    TextButton(onClick = { viewModel.deleteSkill(skill.id) }) {
                                        Text("Delete", color = RoseError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    viewingSkill?.let { skill ->
        AlertDialog(
            onDismissRequest = { viewingSkill = null },
            title = { Text(skill.name) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Permissions: ${skill.requiredPermissions.joinToString { it.label }}", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                    MarkdownText(markdown = skill.skillMdContent)
                    if (skill.supportingFiles.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Supporting Files (${skill.supportingFiles.size})", style = MaterialTheme.typography.titleSmall)
                        skill.supportingFiles.forEach { sf ->
                            Text(
                                text = if (sf.isScript) "${sf.relativePath} [SCRIPT - NOT EXECUTED]" else sf.relativePath,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (sf.isScript) AmberSpark else SlateTextPrimary
                            )
                            Text(sf.content.take(600), style = MaterialTheme.typography.labelSmall, color = SlateTextSecondary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingSkill = null }) { Text("Close") }
            }
        )
    }

    skillPendingTrust?.let { skill ->
        AlertDialog(
            onDismissRequest = { skillPendingTrust = null },
            title = { Text("Skill Permission & Security Review") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Skill '${skill.name}' was imported from external storage and includes script files:")
                    skill.supportingFiles.filter { it.isScript }.forEach {
                        Text("• ${it.relativePath}", style = MaterialTheme.typography.labelSmall, color = AmberSpark)
                    }
                    Text(
                        "AI Studio never auto-executes uploaded skill scripts. Approving trust only allows including this skill's Markdown instructions in prompts.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.approveSkillTrust(skill.id)
                    viewModel.toggleSkillEnabled(skill)
                    skillPendingTrust = null
                }) { Text("Approve & Enable") }
            },
            dismissButton = {
                TextButton(onClick = { skillPendingTrust = null }) { Text("Keep Disabled") }
            }
        )
    }

    if (showCreateDialog || editingSkill != null) {
        SkillEditorDialog(
            existing = editingSkill,
            onSave = { name, cat, desc, body ->
                viewModel.saveCustomSkill(editingSkill, name, cat, desc, body)
                showCreateDialog = false
                editingSkill = null
            },
            onDismiss = {
                showCreateDialog = false
                editingSkill = null
            }
        )
    }
}

@Composable
private fun SkillEditorDialog(
    existing: Skill?,
    onSave: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: "Custom") }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var body by remember { mutableStateOf(existing?.instructionsBody.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Create Skill (SKILL.md)" else "Edit Skill") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Skill Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Short Description") }, maxLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Markdown Instructions") }, minLines = 5, maxLines = 10, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank() && body.isNotBlank(), onClick = { onSave(name, category, description, body) }) {
                Text("Save Skill")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
