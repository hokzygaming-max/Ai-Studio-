package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.coding.CodingScaffoldType
import com.example.domain.model.ProjectFile
import com.example.ui.components.MarkdownText
import com.example.ui.components.ModelPickerSheet
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProjectsSkillsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CodingAgentScreen(
    viewModel: ProjectsSkillsViewModel,
    onNavigateToModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val selectedProjectId by viewModel.selectedProjectId.collectAsStateWithLifecycle()
    val files by viewModel.selectedProjectFiles.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val codingOutput by viewModel.codingOutput.collectAsStateWithLifecycle()
    val isBusy by viewModel.isCodingBusy.collectAsStateWithLifecycle()
    val proposedFiles by viewModel.proposedFiles.collectAsStateWithLifecycle()
    val diagnosticsReport by viewModel.diagnosticsReport.collectAsStateWithLifecycle()
    val banner by viewModel.bannerMessage.collectAsStateWithLifecycle()

    val selectedProject = projects.firstOrNull { it.id == selectedProjectId } ?: projects.firstOrNull()
    LaunchedEffect(projects, selectedProjectId) {
        if (selectedProjectId == null && projects.isNotEmpty()) {
            viewModel.selectProject(projects.first().id)
        }
    }

    var selectedModelKey by remember { mutableStateOf<String?>(null) }
    val activeModel = models.firstOrNull { it.key == (selectedModelKey ?: selectedProject?.defaultModelKey) }
        ?: models.firstOrNull { it.isDefault }
        ?: models.firstOrNull()

    var actionMode by remember { mutableStateOf("Generate") }
    var taskInput by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    var showScaffoldDialog by remember { mutableStateOf(false) }
    var editingFile by remember { mutableStateOf<ProjectFile?>(null) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<ProjectFile?>(null) }
    var showVerifyPermissionDialog by remember { mutableStateOf(false) }
    var showNewProjectQuickDialog by remember { mutableStateOf(false) }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            val bytes = viewModel.exportProjectZipBytes(files)
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Coding Agent") },
            actions = {
                IconButton(onClick = { showModelPicker = true }, modifier = Modifier.testTag("coding_model_btn")) {
                    Icon(Icons.Outlined.Memory, contentDescription = "Select coding model")
                }
                if (selectedProject != null) {
                    IconButton(onClick = { showScaffoldDialog = true }, modifier = Modifier.testTag("coding_scaffold_btn")) {
                        Icon(Icons.Outlined.AutoFixHigh, contentDescription = "Generate project scaffold")
                    }
                    IconButton(onClick = { showVerifyPermissionDialog = true }, modifier = Modifier.testTag("coding_verify_btn")) {
                        Icon(Icons.Outlined.VerifiedUser, contentDescription = "Run local workspace verification")
                    }
                    IconButton(onClick = {
                        val safe = selectedProject.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        exportZipLauncher.launch("$safe.zip")
                    }) {
                        Icon(Icons.Outlined.FolderZip, contentDescription = "Export project ZIP")
                    }
                }
            }
        )

        if (banner != null) {
            Surface(color = DeepCyanContainer, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(banner!!, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearBanner() }, modifier = Modifier.size(28.dp)) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Active Workspace & Model Bar
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Workspace: ${selectedProject?.name ?: "None selected"}", style = MaterialTheme.typography.titleSmall)
                            Text("Model: ${activeModel?.displayName ?: "Configure in Models tab"}", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                        }
                        TextButton(onClick = { showNewProjectQuickDialog = true }) {
                            Text("+ Workspace")
                        }
                    }

                    if (projects.size > 1) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            projects.forEach { p ->
                                FilterChip(
                                    selected = p.id == selectedProject?.id,
                                    onClick = { viewModel.selectProject(p.id) },
                                    label = { Text(p.name) }
                                )
                            }
                        }
                    }

                    if (selectedProject != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Project Files (${files.size})", style = MaterialTheme.typography.labelLarge)
                            TextButton(onClick = { showNewFileDialog = true }) { Text("+ New File") }
                        }
                        if (files.isNotEmpty()) {
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                files.forEach { file ->
                                    InputChip(
                                        selected = false,
                                        onClick = { editingFile = file },
                                        label = { Text(file.path) },
                                        trailingIcon = {
                                            IconButton(onClick = { fileToDelete = file }, modifier = Modifier.size(18.dp)) {
                                                Icon(Icons.Outlined.Close, contentDescription = "Delete file", modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Action Mode Pills
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Generate", "Explain", "Debug", "Refactor", "Review", "Scaffold").forEach { mode ->
                    FilterChip(
                        selected = actionMode == mode,
                        onClick = { actionMode = mode },
                        label = { Text(mode) }
                    )
                }
            }

            OutlinedTextField(
                value = taskInput,
                onValueChange = { taskInput = it },
                label = { Text("Describe coding task ($actionMode)") },
                placeholder = { Text("e.g. Add Room repository caching or refactor auth state handling...") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag("coding_task_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.runCodingAgentTask(
                            taskPrompt = taskInput,
                            actionLabel = actionMode,
                            modelKey = activeModel?.key,
                            projectId = selectedProject?.id
                        )
                    },
                    enabled = !isBusy && taskInput.isNotBlank(),
                    modifier = Modifier.weight(1f).testTag("run_coding_agent_btn")
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Run Coding Agent")
                }
                if (isBusy) {
                    FilledIconButton(
                        onClick = { viewModel.stopCodingAgent() },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = RoseError)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop")
                    }
                }
            }

            if (diagnosticsReport != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    border = BorderStroke(1.dp, EmeraldOk.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Workspace Verification Report", style = MaterialTheme.typography.titleSmall, color = EmeraldOk)
                        Text(diagnosticsReport!!, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (proposedFiles.isNotEmpty() && selectedProject != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DeepIndigoContainer.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, IndigoPulse)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Proposed File Changes (${proposedFiles.size})", style = MaterialTheme.typography.titleSmall)
                            Button(
                                onClick = { viewModel.applyAllProposedFiles(selectedProject.id) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text("Apply All") }
                        }
                        proposedFiles.forEach { pf ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(pf.path, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                OutlinedButton(
                                    onClick = { viewModel.applyProposedFile(selectedProject.id, pf) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) { Text("Write File") }
                            }
                        }
                    }
                }
            }

            if (codingOutput.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        MarkdownText(markdown = codingOutput)
                    }
                }
            }
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            models = models,
            selectedModelKey = activeModel?.key,
            onSelectModel = { selectedModelKey = it.key },
            onNavigateToModels = onNavigateToModels,
            onDismiss = { showModelPicker = false }
        )
    }

    if (showScaffoldDialog && selectedProject != null) {
        AlertDialog(
            onDismissRequest = { showScaffoldDialog = false },
            title = { Text("Generate Starter Project Scaffold") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CodingScaffoldType.entries.forEach { type ->
                        Surface(
                            onClick = {
                                viewModel.generateScaffold(selectedProject.id, type, selectedProject.name)
                                showScaffoldDialog = false
                            },
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(type.label, style = MaterialTheme.typography.titleSmall)
                                Text(type.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showScaffoldDialog = false }) { Text("Close") } }
        )
    }

    if (showVerifyPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showVerifyPermissionDialog = false },
            title = { Text("Permission: Local Workspace Check") },
            text = {
                Text("Allow AI Studio to run on-device static checks (JSON syntax & code delimiter balance) on '${selectedProject?.name}'? Note: Android APKs are not compiled on-device.")
            },
            confirmButton = {
                Button(onClick = {
                    showVerifyPermissionDialog = false
                    viewModel.runSafeDiagnostics(files)
                }) { Text("Run Check") }
            },
            dismissButton = { TextButton(onClick = { showVerifyPermissionDialog = false }) { Text("Cancel") } }
        )
    }

    if (showNewFileDialog || editingFile != null) {
        selectedProject?.let { proj ->
            ProjectFileEditorDialog(
                existing = editingFile,
                onSave = { path, content ->
                    viewModel.saveProjectFile(proj.id, path, content)
                    showNewFileDialog = false
                    editingFile = null
                },
                onDismiss = {
                    showNewFileDialog = false
                    editingFile = null
                }
            )
        }
    }

    fileToDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete File?") },
            text = { Text("Delete '${f.path}' from '${selectedProject?.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProjectFile(f.id)
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { fileToDelete = null }) { Text("Cancel") } }
        )
    }

    if (showNewProjectQuickDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewProjectQuickDialog = false },
            title = { Text("New Coding Workspace") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Workspace Name") }, singleLine = true)
            },
            confirmButton = {
                Button(enabled = name.isNotBlank(), onClick = {
                    viewModel.createOrUpdateProject(null, name, "Coding Agent Workspace", "", activeModel?.key, emptyList())
                    showNewProjectQuickDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewProjectQuickDialog = false }) { Text("Cancel") } }
        )
    }
}
