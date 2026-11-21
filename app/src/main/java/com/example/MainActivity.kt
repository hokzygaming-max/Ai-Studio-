package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.Conversation
import com.example.ui.components.AiStudioLogoBadge
import com.example.ui.components.AiStudioSplashOverlay
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ProjectsSkillsViewModel
import com.example.ui.viewmodel.ProvidersModelsViewModel
import kotlinx.coroutines.launch

enum class MainDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CHAT("chat", "Chat", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    PROJECTS("projects", "Projects", Icons.Filled.Folder, Icons.Outlined.FolderOpen),
    SKILLS("skills", "Skills", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    CODING_AGENT("coding_agent", "Code", Icons.Filled.Terminal, Icons.Outlined.Terminal),
    MODELS("models", "Models", Icons.Filled.Memory, Icons.Outlined.Memory),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AiStudioApplication

        setContent {
            val chatVm: ChatViewModel = viewModel(factory = ChatViewModel.Factory(app.repository))
            val provVm: ProvidersModelsViewModel = viewModel(factory = ProvidersModelsViewModel.Factory(app.repository))
            val projSkillsVm: ProjectsSkillsViewModel = viewModel(factory = ProjectsSkillsViewModel.Factory(app.repository))

            val prefs by provVm.preferences.collectAsStateWithLifecycle()
            var showSplash by remember { mutableStateOf(true) }

            AIStudioTheme(darkTheme = prefs.darkTheme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AiStudioAppShell(
                        chatVm = chatVm,
                        provVm = provVm,
                        projSkillsVm = projSkillsVm,
                        app = app
                    )
                    if (showSplash) {
                        AiStudioSplashOverlay(onFinished = { showSplash = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun AiStudioAppShell(
    chatVm: ChatViewModel,
    provVm: ProvidersModelsViewModel,
    projSkillsVm: ProjectsSkillsViewModel,
    app: AiStudioApplication
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: MainDestination.CHAT.route

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val conversations by chatVm.conversations.collectAsStateWithLifecycle()
    val activeConvId by chatVm.activeConversationId.collectAsStateWithLifecycle()
    val searchQuery by chatVm.searchQuery.collectAsStateWithLifecycle()
    val showArchived by chatVm.showArchived.collectAsStateWithLifecycle()

    var renamingConv by remember { mutableStateOf<Conversation?>(null) }

    fun navigateTo(dest: MainDestination) {
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                ConversationSidebarContent(
                    conversations = conversations,
                    activeConvId = activeConvId,
                    searchQuery = searchQuery,
                    showArchived = showArchived,
                    currentRoute = currentRoute,
                    onSearchChange = { chatVm.setSearchQuery(it) },
                    onToggleArchived = { chatVm.setShowArchived(it) },
                    onNewChat = {
                        chatVm.startNewConversation()
                        navigateTo(MainDestination.CHAT)
                        scope.launch { drawerState.close() }
                    },
                    onSelectConversation = { conv ->
                        chatVm.selectConversation(conv.id)
                        navigateTo(MainDestination.CHAT)
                        scope.launch { drawerState.close() }
                    },
                    onRename = { renamingConv = it },
                    onTogglePin = { chatVm.togglePin(it) },
                    onToggleArchive = { chatVm.toggleArchive(it) },
                    onDelete = { chatVm.deleteConversation(it.id) },
                    onNavigateDest = { dest ->
                        navigateTo(dest)
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 680.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp
                        ) {
                            MainDestination.entries.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = dest.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            header = {
                                AiStudioLogoBadge(sizeDp = 36, modifier = Modifier.padding(vertical = 8.dp))
                            }
                        ) {
                            MainDestination.entries.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag("rail_${dest.route}")
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = MainDestination.CHAT.route,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        composable(MainDestination.CHAT.route) {
                            ChatScreen(
                                viewModel = chatVm,
                                voiceManager = app.voiceManager,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onNavigateToModels = { navigateTo(MainDestination.MODELS) }
                            )
                        }
                        composable(MainDestination.PROJECTS.route) {
                            BackHandler { navigateTo(MainDestination.CHAT) }
                            ProjectsScreen(
                                viewModel = projSkillsVm,
                                onOpenConversation = { conv ->
                                    chatVm.selectConversation(conv.id)
                                    navigateTo(MainDestination.CHAT)
                                },
                                onStartProjectConversation = { projectId ->
                                    chatVm.startNewConversation(projectId)
                                    navigateTo(MainDestination.CHAT)
                                },
                                onOpenInCodingAgent = { projectId ->
                                    projSkillsVm.selectProject(projectId)
                                    navigateTo(MainDestination.CODING_AGENT)
                                }
                            )
                        }
                        composable(MainDestination.SKILLS.route) {
                            BackHandler { navigateTo(MainDestination.CHAT) }
                            SkillsScreen(viewModel = projSkillsVm)
                        }
                        composable(MainDestination.CODING_AGENT.route) {
                            BackHandler { navigateTo(MainDestination.CHAT) }
                            CodingAgentScreen(
                                viewModel = projSkillsVm,
                                onNavigateToModels = { navigateTo(MainDestination.MODELS) }
                            )
                        }
                        composable(MainDestination.MODELS.route) {
                            BackHandler { navigateTo(MainDestination.CHAT) }
                            ModelsProvidersScreen(viewModel = provVm)
                        }
                        composable(MainDestination.SETTINGS.route) {
                            BackHandler { navigateTo(MainDestination.CHAT) }
                            SettingsScreen(
                                viewModel = provVm,
                                chatViewModel = chatVm,
                                voiceManager = app.voiceManager
                            )
                        }
                    }
                }
            }
        }
    }

    renamingConv?.let { conv ->
        var newTitle by remember(conv.id) { mutableStateOf(conv.title) }
        AlertDialog(
            onDismissRequest = { renamingConv = null },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    chatVm.renameConversation(conv.id, newTitle)
                    renamingConv = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingConv = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ConversationSidebarContent(
    conversations: List<Conversation>,
    activeConvId: String?,
    searchQuery: String,
    showArchived: Boolean,
    currentRoute: String,
    onSearchChange: (String) -> Unit,
    onToggleArchived: (Boolean) -> Unit,
    onNewChat: () -> Unit,
    onSelectConversation: (Conversation) -> Unit,
    onRename: (Conversation) -> Unit,
    onTogglePin: (Conversation) -> Unit,
    onToggleArchive: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
    onNavigateDest: (MainDestination) -> Unit
) {
    val filtered = remember(conversations, searchQuery, showArchived) {
        conversations.filter { c ->
            c.isArchived == showArchived &&
                (searchQuery.isBlank() || c.title.contains(searchQuery, ignoreCase = true))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AiStudioLogoBadge(sizeDp = 34)
            Column(modifier = Modifier.weight(1f)) {
                Text("AI Studio", style = MaterialTheme.typography.titleLarge)
                Text("Free & Open Source", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
            }
        }

        Button(
            onClick = onNewChat,
            modifier = Modifier.fillMaxWidth().testTag("drawer_new_chat_btn")
        ) {
            Icon(Icons.Outlined.AddComment, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("New Conversation")
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search chats...") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showArchived, onClick = { onToggleArchived(false) }, label = { Text("Active") })
            FilterChip(selected = showArchived, onClick = { onToggleArchived(true) }, label = { Text("Archived") })
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = if (showArchived) "No archived chats." else "No conversations yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextMuted,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else {
                items(filtered, key = { it.id }) { conv ->
                    var menuOpen by remember { mutableStateOf(false) }
                    val selected = conv.id == activeConvId && currentRoute == MainDestination.CHAT.route
                    Surface(
                        onClick = { onSelectConversation(conv) },
                        color = if (selected) DeepCyanContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (selected) ElectricCyan else ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (conv.isPinned) {
                                Icon(Icons.Filled.PushPin, contentDescription = "Pinned", modifier = Modifier.size(14.dp), tint = AmberSpark)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = conv.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box {
                                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Outlined.MoreHoriz, contentDescription = "Chat actions", modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename(conv) })
                                    DropdownMenuItem(text = { Text(if (conv.isPinned) "Unpin" else "Pin") }, onClick = { menuOpen = false; onTogglePin(conv) })
                                    DropdownMenuItem(text = { Text(if (conv.isArchived) "Unarchive" else "Archive") }, onClick = { menuOpen = false; onToggleArchive(conv) })
                                    DropdownMenuItem(text = { Text("Delete", color = RoseError) }, onClick = { menuOpen = false; onDelete(conv) })
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Text("Workspaces", style = MaterialTheme.typography.labelMedium, color = SlateTextSecondary)
        MainDestination.entries.forEach { dest ->
            NavigationDrawerItem(
                selected = currentRoute == dest.route,
                onClick = { onNavigateDest(dest) },
                icon = { Icon(dest.unselectedIcon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(dest.label) },
                modifier = Modifier.height(42.dp)
            )
        }
    }
}
