package com.mazzlabs.sentinel.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * SettingsActivity - Hosts Compose-based settings screens.
 *
 * Coexists with the existing View-based MainActivity.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                SettingsNavHost(onFinish = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsNavHost(onFinish: () -> Unit) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "settings_home") {
        composable("settings_home") {
            SettingsHomeScreen(
                onNavigateToGateway = { navController.navigate("gateway") },
                onNavigateToPrivacy = { navController.navigate("privacy") },
                onNavigateToDevMode = { navController.navigate("dev_mode") },
                onNavigateBack = onFinish
            )
        }
        composable("gateway") {
            GatewaySettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("privacy") {
            PrivacySettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("dev_mode") {
            DevModeScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHomeScreen(
    onNavigateToGateway: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToDevMode: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        ) {
            SettingsItem(
                title = "Gateway Connection",
                subtitle = "Configure remote gateway URL and authentication",
                onClick = onNavigateToGateway
            )
            HorizontalDivider()
            SettingsItem(
                title = "Privacy & Data",
                subtitle = "Network features, PII blocking, screen sharing controls",
                onClick = onNavigateToPrivacy
            )
            HorizontalDivider()
            SettingsItem(
                title = "Dev Mode",
                subtitle = "Dev projects, remote tools, and workflow status",
                onClick = onNavigateToDevMode
            )
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(">", style = MaterialTheme.typography.bodyLarge)
    }
}
