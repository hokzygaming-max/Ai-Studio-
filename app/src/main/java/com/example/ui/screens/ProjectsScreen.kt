package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.files.FileProcessor
import com.example.domain.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProjectsSkillsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectsScreen(
    viewModel: ProjectsSkillsViewModel,
    onOpenConversation: (Conversation) -> Unit,
    onStartProjectConversation: (String) -> Unit,
    onOpenInCodingAgent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val selectedProjectId by viewModel.selectedProjectId.collectAsStateWithLifecycle()
    val projectFiles by viewModel.selectedProjectFiles.collectAsStateWithLifecycle()
    val projectConvs by viewModel.selectedProjectConversations.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val banner by viewModel.bannerMessage.collectAsStateWithLifecycle()

    val selectedProject = projects.firstOrNull { it.id == selectedProjectId }

    var editingProject by remember { mutableStateOf<ProjectWorkspace?>(null) }
    var showCreateProject by remember { mutableStateOf(false) }
    var pendingProjectExportJson by remember { mutableStateOf<String?>(null) }

    val importProjectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (raw != null) viewModel.importProjectJson(raw)
        }
    }

    val exportProjectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingProjectExportJson
        if (uri != null && json != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            pendingProjectExportJson = null
        }
    }

    if (selectedProject != null) {
        BackHandler { viewModel.selectProject(null) }
        ProjectDetailView(
            project = selectedProject,
            files = projectFiles,
            conversations = projectConvs,
            models = models,
            skills = skills,
            onBack = { viewModel.selectProject(null) },
            onEditProject = { editingProject = selectedProject },
            onDeleteProject = { viewModel.deleteProject(selectedProject.id) },
            onExportProject = {
                scope.launch {
                    pendingProjectExportJson = viewModel.exportProjectJson(selectedProject)
                    val safe = selectedProject.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    exportProjectLauncher.launch("$safe-project.json")
                }
            },
            onUploadFiles = { uris -> viewModel.uploadFilesToProject(selectedProject.id, uris) },
            onSaveFile = { path, content -> viewModel.saveProjectFile(selectedProject.id, path, content) },
            onDeleteFile = { fileId -> viewModel.deleteProjectFile(fileId) },
            onOpenConversation = onOpenConversation,
            onNewConversation = { onStartProjectConversation(selectedProject.id) },
            onOpenCodingAgent = { onOpenInCodingAgent(selectedProject.id) },
            modifier = modifier
        )
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Project Workspaces") },
                actions = {
                    IconButton(onClick = { importProjectLauncher.launch(arrayOf("application/json")) }) {
                        Icon(Icons.Outlined.UploadFile, contentDescription = "Import project JSON")
                    }
                    IconButton(
                        onClick = { showCreateProject = true },
                        modifier = Modifier.testTag("create_project_btn")
                    ) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = "Create project")
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

            if (projects.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.widthIn(max = 460.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Outlined.Workspaces, contentDescription = null, modifier = Modifier.size(40.dp), tint = ElectricCyan)
                            Text("Create Your First Project Workspace", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Organize project instructions, uploaded reference files, dedicated conversations, coding agent workspaces, and pinned skills together.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { showCreateProject = true }) {
                                Icon(Icons.Outlined.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("New Project")
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
                    items(projects, key = { it.id }) { proj ->
                        Card(
                            onClick = { viewModel.selectProject(proj.id) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = BorderStroke(1.dp, ObsidianBorder)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.FolderSpecial, contentDescription = null, tint = ElectricCyan)
                                    Spacer(Modifier.width(10.dp))
                                    Text(proj.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SlateTextSecondary)
                                }
                                if (proj.description.isNotBlank()) {
                                    Text(proj.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (proj.selectedSkillIds.isNotEmpty()) {
                                    Text("${proj.selectedSkillIds.size} pinned skill(s)", style = MaterialTheme.typography.labelSmall, color = IndigoPulse)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateProject || editingProject != null) {
        ProjectEditorDialog(
            existing = editingProject,
            models = models,
            skills = skills,
            onSave = { name, desc, instr, modelKey, skillIds ->
                viewModel.createOrUpdateProject(editingProject, name, desc, instr, modelKey, skillIds)
                showCreateProject = false
                editingProject = null
            },
            onDismiss = {
                showCreateProject = false
                editingProject = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectDetailView(
    project: ProjectWorkspace,
    files: List<ProjectFile>,
    conversations: List<Conversation>,
    models: List<AiModel>,
    skills: List<Skill>,
    onBack: () -> Unit,
    onEditProject: () -> Unit,
    onDeleteProject: () -> Unit,
    onExportProject: () -> Unit,
    onUploadFiles: (List<android.net.Uri>) -> Unit,
    onSaveFile: (String, String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    onNewConversation: () -> Unit,
    onOpenCodingAgent: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingFile by remember { mutableStateOf<ProjectFile?>(null) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<ProjectFile?>(null) }
    var showDeleteProjectConfirm by remember { mutableStateOf(false) }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) onUploadFiles(uris) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to projects")
                }
            },
            actions = {
                IconButton(onClick = onOpenCodingAgent) {
                    Icon(Icons.Outlined.Code, contentDescription = "Open in Coding Agent")
                }
                IconButton(onClick = onExportProject) {
                    Icon(Icons.Outlined.Download, contentDescription = "Export project JSON")
                }
                IconButton(onClick = onEditProject) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit project")
                }
                IconButton(onClick = { showDeleteProjectConfirm = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete project", tint = RoseError)
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (project.description.isNotBlank()) {
                            Text(project.description, style = MaterialTheme.typography.bodyMedium)
                        }
                        val modelName = models.firstOrNull { it.key == project.defaultModelKey }?.displayName ?: "Global Default"
                        Text("Model: $modelName", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                        if (project.instructions.isNotBlank()) {
                            Text("Instructions: ${project.instructions}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                        if (project.selectedSkillIds.isNotEmpty()) {
                            val names = skills.filter { it.id in project.selectedSkillIds }.joinToString { it.name }
                            Text("Skills: $names", style = MaterialTheme.typography.labelSmall, color = IndigoPulse)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Project Conversations (${conversations.size})", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onNewConversation, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                        Icon(Icons.Outlined.AddComment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Chat")
                    }
                }
            }

            items(conversations, key = { it.id }) { conv ->
                Surface(
                    onClick = { onOpenConversation(conv) },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = ElectricCyan)
                        Spacer(Modifier.width(10.dp))
                        Text(conv.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Workspace Files (${files.size})", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { uploadLauncher.launch(arrayOf("*/*")) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                            Text("Upload")
                        }
                        OutlinedButton(onClick = { showNewFileDialog = true }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                            Text("+ File")
                        }
                    }
                }
            }

            items(files, key = { it.id }) { file ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth().clickable { editingFile = file }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Description, contentDescription = null, tint = IndigoPulse, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.path, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(FileProcessor.formatBytes(file.sizeBytes), style = MaterialTheme.typography.labelSmall, color = SlateTextSecondary)
                        }
                        IconButton(onClick = { fileToDelete = file }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete file", tint = RoseError)
                        }
                    }
                }
            }
        }
    }

    if (showNewFileDialog || editingFile != null) {
        ProjectFileEditorDialog(
            existing = editingFile,
            onSave = { path, content ->
                onSaveFile(path, content)
                showNewFileDialog = false
                editingFile = null
            },
            onDismiss = {
                showNewFileDialog = false
                editingFile = null
            }
        )
    }

    fileToDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete File?") },
            text = { Text("Remove '${f.path}' from this project workspace?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteFile(f.id)
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { fileToDelete = null }) { Text("Cancel") } }
        )
    }

    if (showDeleteProjectConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteProjectConfirm = false },
            title = { Text("Delete Project?") },
            text = { Text("Delete '${project.name}' and all its workspace files?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteProjectConfirm = false
                        onDeleteProject()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteProjectConfirm = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectEditorDialog(
    existing: ProjectWorkspace?,
    models: List<AiModel>,
    skills: List<Skill>,
    onSave: (String, String, String, String?, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var desc by remember { mutableStateOf(existing?.description.orEmpty()) }
    var instr by remember { mutableStateOf(existing?.instructions.orEmpty()) }
    var modelKey by remember { mutableStateOf(existing?.defaultModelKey) }
    var selectedSkills by remember { mutableStateOf(existing?.selectedSkillIds?.toSet() ?: emptySet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Project Workspace" else "Edit Project") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Project Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, maxLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = instr, onValueChange = { instr = it }, label = { Text("Project System Instructions") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())

                if (models.isNotEmpty()) {
                    Text("Default Model", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        models.take(8).forEach { m ->
                            FilterChip(
                                selected = modelKey == m.key,
                                onClick = { modelKey = if (modelKey == m.key) null else m.key },
                                label = { Text(m.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }

                Text("Project Skills", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    skills.forEach { sk ->
                        val active = sk.id in selectedSkills
                        FilterChip(
                            selected = active,
                            onClick = { selectedSkills = if (active) selectedSkills - sk.id else selectedSkills + sk.id },
                            label = { Text(sk.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = { onSave(name, desc, instr, modelKey, selectedSkills.toList()) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ProjectFileEditorDialog(
    existing: ProjectFile?,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var path by remember { mutableStateOf(existing?.path.orEmpty()) }
    var content by remember { mutableStateOf(existing?.content.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Create Project File" else "Edit ${existing.path}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Relative File Path (e.g. src/Main.kt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("File Content") },
                    minLines = 8,
                    maxLines = 14,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(enabled = path.isNotBlank(), onClick = { onSave(path, content) }) { Text("Save File") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
