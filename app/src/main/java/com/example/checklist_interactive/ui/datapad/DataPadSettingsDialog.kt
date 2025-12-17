package com.example.checklist_interactive.ui.datapad

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.example.checklist_interactive.R
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.View
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

/**
 * Settings dialog for DataPad configuration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataPadSettingsDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val manager = LocalDataPadManager.current
    val currentPort by manager.udpPort.collectAsState()
    val currentBindIp by manager.bindIp.collectAsState()
    val currentServerIp by manager.serverIp.collectAsState()
    val currentKey by manager.preSharedKey.collectAsState()
    val useEcdh by manager.useEcdh.collectAsState()
    val deviceName by manager.deviceName.collectAsState()

    var portText by remember { mutableStateOf(currentPort.toString()) }
    var bindIpText by remember { mutableStateOf(currentBindIp) }
    var serverIpText by remember { mutableStateOf(currentServerIp) }
    var keyText by remember { mutableStateOf(currentKey) }
    var deviceNameText by remember { mutableStateOf(deviceName) }
    var showKeyWarning by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }
    var useEcdhLocal by remember { mutableStateOf(useEcdh) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        // Try to hide the system UI inside the dialog window
        val dialogView = LocalView.current

        // Immediately attempt to hide system UI before first draw to avoid flash.
        DisposableEffect(dialogView) {
            val dialogWindow = (dialogView.context as? android.app.Activity)?.window
            val controller = dialogWindow?.let { WindowCompat.getInsetsController(it, dialogView) }
            // Also set old-style flags for older API's
            @Suppress("DEPRECATION")
            dialogView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

            val preDrawListener = object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    controller?.hide(WindowInsetsCompat.Type.systemBars())
                    try {
                        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } catch (_: Throwable) {
                    }
                    dialogView.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            }
            dialogView.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            val attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    val vWindow = (v.context as? android.app.Activity)?.window
                    val c = vWindow?.let { WindowCompat.getInsetsController(it, v) }
                    c?.hide(WindowInsetsCompat.Type.systemBars())
                    try {
                        c?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } catch (_: Throwable) {
                    }
                    @Suppress("DEPRECATION")
                    v.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
                }

                override fun onViewDetachedFromWindow(v: View) {}
            }
            dialogView.addOnAttachStateChangeListener(attachListener)
            onDispose {
                try {
                    dialogView.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                    dialogView.removeOnAttachStateChangeListener(attachListener)
                } catch (_: Throwable) {
                }
            }
        }

        // Keep ensuring hide while dialog is visible
        LaunchedEffect(dialogView) {
            val dialogWindow = (dialogView.context as? android.app.Activity)?.window
            val dialogController = dialogWindow?.let { WindowCompat.getInsetsController(it, dialogView) }
            while (isActive) {
                dialogController?.hide(WindowInsetsCompat.Type.systemBars())
                delay(100L)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.datapad_settings_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close)
                        )
                    }
                }
                
                Divider()
                
                // ECDH Handshake Mode Toggle
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (useEcdhLocal) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ECDH Handshake Mode",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (useEcdhLocal) 
                                    MaterialTheme.colorScheme.onPrimaryContainer 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (useEcdhLocal)
                                    "Secure session-based encryption (Recommended)"
                                else
                                    "Using legacy PSK mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (useEcdhLocal) 
                                    MaterialTheme.colorScheme.onPrimaryContainer 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useEcdhLocal,
                            onCheckedChange = { useEcdhLocal = it }
                        )
                    }
                }
                
                // Device Name (only shown in ECDH mode)
                if (useEcdhLocal) {
                    OutlinedTextField(
                        value = deviceNameText,
                        onValueChange = { deviceNameText = it },
                        label = { Text("Device Name") },
                        placeholder = { Text("Android Tablet") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text("Used to identify this device during handshake")
                        }
                    )
                    
                    // Show Device ID
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Device ID (derived from key)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = manager.getDeviceId(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                
                Divider()
                
                // UDP Port Setting
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text(stringResource(R.string.datapad_settings_udp_port)) },
                    placeholder = { Text(stringResource(R.string.datapad_settings_udp_port_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(stringResource(R.string.datapad_settings_port_range))
                    },
                )

                // Bind IP Setting
                OutlinedTextField(
                    value = bindIpText,
                    onValueChange = { bindIpText = it },
                    label = { Text(stringResource(R.string.datapad_settings_bind_ip)) },
                    placeholder = { Text(stringResource(R.string.datapad_settings_bind_ip_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(stringResource(R.string.datapad_settings_bind_ip_hint))
                    }
                )

                // Server IP (Unicast - more secure than broadcast)
                if (useEcdhLocal) {
                    OutlinedTextField(
                        value = serverIpText,
                        onValueChange = { serverIpText = it },
                        label = { Text("Server IP (Unicast)") },
                        placeholder = { Text("192.168.178.100") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text("Leave empty for broadcast discovery (less secure)")
                        }
                    )
                }

                // Pre-Shared Key Setting (masked by default)
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { 
                        keyText = it
                        showKeyWarning = it.length != 32 && it.isNotEmpty()
                    },
                    label = { Text(stringResource(R.string.datapad_settings_psk) + if (useEcdhLocal) " (for handshake)" else "") },
                    placeholder = { Text(stringResource(R.string.datapad_settings_psk_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = showKeyWarning,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (keyVisible) stringResource(R.string.datapad_hide_key) else stringResource(R.string.datapad_show_key)
                            )
                        }
                    },
                    supportingText = {
                        if (showKeyWarning) {
                            Text(
                                text = stringResource(R.string.datapad_key_length_warning, keyText.length),
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                if (useEcdhLocal)
                                    "PSK used only for handshake encryption, data uses session key"
                                else
                                    stringResource(R.string.datapad_settings_psk_hint)
                            )
                        }
                    }
                )
                
                if (showKeyWarning) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.datapad_key_length_message),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Warn if current key is still the default (encourage changing it)
                if (currentKey == "DCS_DataPad_Secret_Key_32BYTES!!") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.datapad_default_key_warning),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                Divider()
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }

                    TextButton(onClick = {
                        // Reset to default key (manager will replace empty with default)
                        keyText = ""
                        showKeyWarning = false
                        manager.updatePreSharedKey("")
                    }) {
                        Text(stringResource(R.string.datapad_settings_reset_key))
                    }
                    
                    Button(
                        onClick = {
                            // Validate and save
                            val port = portText.toIntOrNull()
                            if (port != null && port in 1024..65535) {
                                manager.updatePort(port)
                            }
                            
                            manager.updateBindIp(bindIpText.trim())
                            manager.updateServerIp(serverIpText.trim())

                            if (keyText.length == 32 || keyText.isEmpty()) {
                                manager.updatePreSharedKey(keyText)
                            }

                            // Update ECDH mode and device name
                            manager.setUseEcdh(useEcdhLocal)
                            if (deviceNameText.isNotBlank()) {
                                manager.updateDeviceName(deviceNameText.trim())
                            }

                            onDismiss()
                        },
                        enabled = !showKeyWarning
                    ) {
                        Text(stringResource(R.string.datapad_settings_save_restart))
                    }
                }
                
                // Info Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (useEcdhLocal) "ECDH Mode Information" else stringResource(R.string.datapad_configuration_tips_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = if (useEcdhLocal)
                                "• Device must be added to authorized_devices.json on DCS server\n• Handshake establishes session key automatically\n• Session key changes each connection (Forward Secrecy)\n• PSK only protects handshake, not data"
                            else
                                stringResource(R.string.datapad_configuration_tips_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (useEcdhLocal) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚠️ ECDH requires Python server update (see docs/technical/ECDH_HANDSHAKE_PROPOSAL.md)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.datapad_security_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
