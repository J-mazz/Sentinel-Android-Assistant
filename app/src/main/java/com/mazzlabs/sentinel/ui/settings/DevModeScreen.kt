package com.mazzlabs.sentinel.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mazzlabs.sentinel.SentinelApplication
import com.mazzlabs.sentinel.gateway.GatewayConnectionManager
import com.mazzlabs.sentinel.tools.framework.Tools
import com.mazzlabs.sentinel.tools.modules.dev.ProjectManagerModule
import kotlinx.coroutines.launch

/**
 * DevModeScreen - Dev project list, status, and quick actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevModeScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as SentinelApplication
    val scope = rememberCoroutineScope()

    val connectionState by app.gatewayConnectionManager?.connectionState
        ?.collectAsState(initial = GatewayConnectionManager.ConnectionState.DISCONNECTED)
        ?: remember { mutableStateOf(GatewayConnectionManager.ConnectionState.DISCONNECTED) }

    val isConnected = connectionState == GatewayConnectionManager.ConnectionState.CONNECTED

    var projects by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeProject by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Load projects on first composition
    LaunchedEffect(Unit) {
        isLoading = true
        val toolExecutor = Tools.getInstance(context)
        val listResult = toolExecutor.execute("project_manager.list_projects", emptyMap())
        val activeResult = toolExecutor.execute("project_manager.get_active_project", emptyMap())

        projects = when (listResult) {
            is com.mazzlabs.sentinel.tools.framework.ToolResponse.Success -> {
                @Suppress("UNCHECKED_CAST")
                (listResult.data["projects"] as? List<String>) ?: emptyList()
            }
            else -> emptyList()
        }

        activeProject = when (activeResult) {
            is com.mazzlabs.sentinel.tools.framework.ToolResponse.Success -> {
                activeResult.data["name"] as? String
            }
            else -> null
        }

        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dev Mode") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("<")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Gateway status
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Gateway", style = MaterialTheme.typography.labelMedium)
                        Text(
                            if (isConnected) "Connected" else "Disconnected",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isConnected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    if (activeProject != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Active Project", style = MaterialTheme.typography.labelMedium)
                            Text(
                                activeProject ?: "None",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            // Available dev tools
            Text("Dev Tools", style = MaterialTheme.typography.titleMedium)

            val devModules = listOf("remote_fs", "remote_terminal", "project_manager", "code_review")
            val availableModules = Tools.getInstance(context).getAvailableModules()

            devModules.forEach { moduleId ->
                val isAvailable = moduleId in availableModules
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAvailable)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            moduleId.replace("_", " ").replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            if (isAvailable) "Active" else "Unavailable",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isAvailable)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Projects list
            Text("Projects", style = MaterialTheme.typography.titleMedium)

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else if (projects.isEmpty()) {
                Text(
                    "No dev projects yet. Use voice command to start one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(projects) { projectName ->
                        val isActive = projectName == activeProject
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        projectName,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (isActive) {
                                        Text(
                                            "Active",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (!isActive) {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                Tools.getInstance(context).execute(
                                                    "project_manager.set_active_project",
                                                    mapOf("name" to projectName)
                                                )
                                                activeProject = projectName
                                            }
                                        }
                                    ) {
                                        Text("Set Active")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
