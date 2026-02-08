package com.mazzlabs.sentinel.ui.settings

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mazzlabs.sentinel.SentinelApplication
import com.mazzlabs.sentinel.gateway.GatewayConfig
import com.mazzlabs.sentinel.gateway.GatewayConnectionManager
import com.mazzlabs.sentinel.gateway.security.GatewayAuthManager
import com.mazzlabs.sentinel.gateway.security.NetworkSecurityPolicy
import com.mazzlabs.sentinel.service.GatewayService
import kotlinx.coroutines.launch

/**
 * GatewaySettingsScreen - Configure gateway URL, authentication token, and connection settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewaySettingsScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as SentinelApplication
    val authManager = remember { GatewayAuthManager(context) }
    val scope = rememberCoroutineScope()

    var gatewayUrl by remember { mutableStateOf(authManager.gatewayUrl.ifEmpty { GatewayConfig.Defaults.GATEWAY_URL }) }
    var authToken by remember { mutableStateOf(authManager.gatewayToken) }
    var showToken by remember { mutableStateOf(false) }
    var autoConnect by remember { mutableStateOf(authManager.isConfigured()) }

    val connectionState by app.gatewayConnectionManager?.state
        ?.collectAsState(initial = GatewayConnectionManager.ConnectionState.DISCONNECTED)
        ?: remember { mutableStateOf(GatewayConnectionManager.ConnectionState.DISCONNECTED) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gateway Settings") },
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
            // Connection status card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Connection Status", style = MaterialTheme.typography.labelMedium)
                        Text(
                            connectionState.name.replace("_", " "),
                            style = MaterialTheme.typography.bodyLarge,
                            color = when (connectionState) {
                                GatewayConnectionManager.ConnectionState.CONNECTED ->
                                    MaterialTheme.colorScheme.primary
                                GatewayConnectionManager.ConnectionState.ERROR ->
                                    MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }

                    val isConnected = connectionState == GatewayConnectionManager.ConnectionState.CONNECTED
                    Button(
                        onClick = {
                            scope.launch {
                                if (isConnected) {
                                    GatewayService.stop(context)
                                } else {
                                    // Save config first
                                    authManager.gatewayUrl = gatewayUrl
                                    authManager.authToken = authToken
                                    GatewayService.start(context)
                                }
                            }
                        }
                    ) {
                        Text(if (isConnected) "Disconnect" else "Connect")
                    }
                }
            }

            // Gateway URL
            OutlinedTextField(
                value = gatewayUrl,
                onValueChange = { gatewayUrl = it },
                label = { Text("Gateway URL") },
                placeholder = { Text(GatewayConfig.Defaults.GATEWAY_URL) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            // Auth token
            OutlinedTextField(
                value = authToken,
                onValueChange = { authToken = it },
                label = { Text("Auth Token") },
                placeholder = { Text("Enter gateway token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showToken = !showToken }) {
                        Text(if (showToken) "Hide" else "Show")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            // Save button
            Button(
                onClick = {
                    // Validate URL before saving
                    val policy = NetworkSecurityPolicy()
                    val validation = policy.validateGatewayUrl(gatewayUrl)
                    if (validation is NetworkSecurityPolicy.ValidationResult.Valid) {
                        authManager.gatewayUrl = gatewayUrl
                        authManager.gatewayToken = authToken
                        authManager.isGatewayEnabled = autoConnect
                    } else if (validation is NetworkSecurityPolicy.ValidationResult.Invalid) {
                        Log.e("GatewaySettings", "Invalid URL: ${validation.reason}")
                        // TODO: Show error to user via SnackBar or Alert
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Configuration")
            }

            // Auto-connect toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-connect on startup")
                Switch(
                    checked = autoConnect,
                    onCheckedChange = { 
                        autoConnect = it
                        authManager.isGatewayEnabled = it
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Clear credentials
            OutlinedButton(
                onClick = {
                    authManager.clearAll()
                    gatewayUrl = GatewayConfig.Defaults.GATEWAY_URL
                    authToken = ""
                    GatewayService.stop(context)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Clear Credentials")
            }
        }
    }
}
