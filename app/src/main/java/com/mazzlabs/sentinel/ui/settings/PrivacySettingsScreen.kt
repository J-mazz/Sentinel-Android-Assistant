package com.mazzlabs.sentinel.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * PrivacySettingsScreen - Control network features, data sharing, and PII protection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("sentinel_privacy", android.content.Context.MODE_PRIVATE)
    }

    var networkEnabled by remember {
        mutableStateOf(prefs.getBoolean("network_enabled", true))
    }
    var piiBlockingEnabled by remember {
        mutableStateOf(prefs.getBoolean("pii_blocking_enabled", true))
    }
    var screenShareEnabled by remember {
        mutableStateOf(prefs.getBoolean("screen_share_enabled", false))
    }
    var sendAccessibilityTree by remember {
        mutableStateOf(prefs.getBoolean("send_accessibility_tree", true))
    }
    var sendScreenImages by remember {
        mutableStateOf(prefs.getBoolean("send_screen_images", false))
    }

    fun savePref(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Data") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Network features section
            Text(
                "Network Features",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            SettingsToggle(
                title = "Enable network features",
                subtitle = "Allow Sentinel to connect to the remote gateway",
                checked = networkEnabled,
                onCheckedChange = {
                    networkEnabled = it
                    savePref("network_enabled", it)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Data protection section
            Text(
                "Data Protection",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            SettingsToggle(
                title = "Block PII in outgoing data",
                subtitle = "Detect and redact phone numbers, emails, SSNs, and credit card numbers before sending to gateway",
                checked = piiBlockingEnabled,
                onCheckedChange = {
                    piiBlockingEnabled = it
                    savePref("pii_blocking_enabled", it)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Screen sharing section
            Text(
                "Screen Sharing",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            SettingsToggle(
                title = "Enable screen sharing to remote",
                subtitle = "Allow sending screen data to the remote agent",
                checked = screenShareEnabled,
                enabled = networkEnabled,
                onCheckedChange = {
                    screenShareEnabled = it
                    savePref("screen_share_enabled", it)
                }
            )

            SettingsToggle(
                title = "Send accessibility tree",
                subtitle = "Include text representation of screen elements",
                checked = sendAccessibilityTree,
                enabled = networkEnabled && screenShareEnabled,
                onCheckedChange = {
                    sendAccessibilityTree = it
                    savePref("send_accessibility_tree", it)
                }
            )

            SettingsToggle(
                title = "Send screen images",
                subtitle = "Include JPEG screenshots (downscaled to 720p)",
                checked = sendScreenImages,
                enabled = networkEnabled && screenShareEnabled,
                onCheckedChange = {
                    sendScreenImages = it
                    savePref("send_screen_images", it)
                }
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.let {
                    if (enabled) it else it.copy(alpha = 0.5f)
                }
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
