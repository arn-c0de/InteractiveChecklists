package com.example.checklist_interactive.ui.datapad

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.filled.ContentCopy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.View
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.checklist_interactive.data.datapad.KeyManager
import com.example.checklist_interactive.data.datapad.QrRegistrationManager
import com.example.checklist_interactive.data.datapad.RegistrationResult

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val currentPort by manager.udpPort.collectAsState()
    val currentBindIp by manager.bindIp.collectAsState()
    val currentServerIp by manager.serverIp.collectAsState()
    val deviceName by manager.deviceName.collectAsState()

    var portText by remember { mutableStateOf(currentPort.toString()) }
    var bindIpText by remember { mutableStateOf(currentBindIp) }
    var serverIpText by remember { mutableStateOf(currentServerIp) }
    var deviceNameText by remember { mutableStateOf(deviceName) }

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

        // Limit dialog height on small screens and make contents scrollable
        val configuration = LocalConfiguration.current
        val maxDialogHeight = (configuration.screenHeightDp.dp * 0.8f)
        val scrollState = rememberScrollState()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
                    .verticalScroll(scrollState)
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
                
                HorizontalDivider()

                // Device Name for ECDH identification
                OutlinedTextField(
                        value = deviceNameText,
                        onValueChange = { deviceNameText = it },
                        label = { Text(stringResource(R.string.datapad_device_name_label)) },
                        placeholder = { Text(stringResource(R.string.datapad_device_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text(stringResource(R.string.datapad_device_name_support))
                        }
                    )
                    
                        val clipboard = LocalClipboardManager.current
                        var copiedDeviceId by remember { mutableStateOf(false) }
                        var copiedPublicKey by remember { mutableStateOf(false) }
                        var copiedBoth by remember { mutableStateOf(false) }
                        val publicKey = try { manager.getPublicKey() } catch (_: Throwable) { "" }

                        // Show Device ID (copyable)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.datapad_device_id_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = manager.getDeviceId(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    if (copiedDeviceId) {
                                        Text(
                                            text = stringResource(R.string.datapad_device_id_copied_feedback),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    val id = manager.getDeviceId()
                                    if (id.isNotEmpty()) {
                                        clipboard.setText(AnnotatedString(id))
                                        copiedDeviceId = true
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.datapad_cd_copy_device_id)
                                    )
                                }
                            }
                        }

                        LaunchedEffect(copiedDeviceId) {
                            if (copiedDeviceId) {
                                delay(2000L)
                                copiedDeviceId = false
                            }
                        }

                        // Show Base64 Public Key with copy action
                        OutlinedTextField(
                            value = publicKey,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.datapad_public_key_label)) },
                            singleLine = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp, max = 160.dp),
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (publicKey.isNotEmpty()) {
                                        clipboard.setText(AnnotatedString(publicKey))
                                        copiedPublicKey = true
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.datapad_cd_copy_public_key)
                                    )
                                }
                            },
                            supportingText = {
                                when {
                                    copiedBoth -> Text(stringResource(R.string.datapad_copy_both_copied_feedback))
                                    copiedPublicKey -> Text(stringResource(R.string.datapad_public_key_copied_feedback))
                                    else -> Text(stringResource(R.string.datapad_public_key_copy_hint))
                                }
                            }
                        )

                        // Button to copy both Device ID and Public Key together
                        val combinedDeviceLabel = stringResource(R.string.datapad_combined_device_id_label)
                        val combinedPublicKeyLabel = stringResource(R.string.datapad_combined_public_key_label)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val id = manager.getDeviceId()
                                    if (publicKey.isNotEmpty() && id.isNotEmpty()) {
                                        val combined = buildString {
                                            append(combinedDeviceLabel)
                                            append(id)
                                            append("\n\n")
                                            append(combinedPublicKeyLabel)
                                            append(publicKey)
                                        }
                                        clipboard.setText(AnnotatedString(combined))
                                        copiedBoth = true
                                        copiedPublicKey = true
                                        copiedDeviceId = true
                                    }
                                },
                                enabled = publicKey.isNotEmpty() && manager.getDeviceId().isNotEmpty()
                            ) {
                                Text(stringResource(R.string.datapad_copy_both))
                            }
                        }

                        LaunchedEffect(copiedBoth) {
                            if (copiedBoth) {
                                delay(2000L)
                                copiedBoth = false
                                copiedPublicKey = false
                                copiedDeviceId = false
                            }
                        }

                        LaunchedEffect(copiedPublicKey) {
                            if (copiedPublicKey) {
                                delay(2000L)
                                copiedPublicKey = false
                            }
                        }

                HorizontalDivider()
                
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
                OutlinedTextField(
                    value = serverIpText,
                    onValueChange = { serverIpText = it },
                    label = { Text(stringResource(R.string.datapad_server_ip_label)) },
                    placeholder = { Text(stringResource(R.string.datapad_server_ip_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(stringResource(R.string.datapad_server_ip_hint))
                    }
                )

                HorizontalDivider()
                
                // QR Code Registration Section
                var showQrScannerDialog by remember { mutableStateOf(false) }
                var registrationMessage by remember { mutableStateOf<String?>(null) }
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Quick Setup",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Scan QR code from server to auto-configure",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = { showQrScannerDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Scan QR Code"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan QR Code")
                        }
                        
                        registrationMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (msg.contains("success", ignoreCase = true)) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
                
                if (showQrScannerDialog) {
                    // Full-screen camera QR scanner
                    QrCodeScannerScreen(
                        onQrCodeScanned = { qrData ->
                            showQrScannerDialog = false
                            // Process QR code with QrRegistrationManager
                            scope.launch {
                                registrationMessage = "⏳ Processing registration..."
                                try {
                                    // Initialize KeyManager and QrRegistrationManager
                                    val keyManager = KeyManager(context)
                                    val qrRegistrationManager = QrRegistrationManager(context, keyManager)
                                    
                                    // Complete registration flow
                                    when (val result = qrRegistrationManager.completeRegistration(
                                        qrData = qrData,
                                        deviceName = deviceNameText.ifEmpty { "Android-${android.os.Build.MODEL}" },
                                        dataPadManager = manager
                                    )) {
                                        is RegistrationResult.Success -> {
                                            registrationMessage = "✅ Registration successful! Device authorized."
                                            // Settings already updated by applyTokenSettings
                                        }
                                        is RegistrationResult.Error -> {
                                            registrationMessage = "❌ Registration failed: ${result.message}"
                                        }
                                        is RegistrationResult.Timeout -> {
                                            registrationMessage = "⏱️ Registration timeout - server not responding"
                                        }
                                    }
                                } catch (e: Exception) {
                                    registrationMessage = "❌ Error: ${e.message}"
                                    android.util.Log.e("DataPadSettings", "QR Registration error", e)
                                }
                            }
                        },
                        onDismiss = { showQrScannerDialog = false }
                    )
                }
                
                HorizontalDivider()
                
                // Security Management Section
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🔒 Server Key Management",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "If the server restarts or you see 'SERVER KEY MISMATCH' errors, clear the pinned server key to reconnect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        
                        var showClearConfirmation by remember { mutableStateOf(false) }
                        
                        OutlinedButton(
                            onClick = { showClearConfirmation = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("🗑️ Clear Pinned Server Key")
                        }
                        
                        if (showClearConfirmation) {
                            AlertDialog(
                                onDismissRequest = { showClearConfirmation = false },
                                title = { Text("Clear Pinned Server Key?") },
                                text = { 
                                    Text("This will remove the saved server key fingerprint. You'll need to re-establish trust on next connection.\n\nUse this if the server restarted with new keys or if you're seeing MITM warnings.")
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            manager.clearPinnedServerKey()
                                            showClearConfirmation = false
                                            registrationMessage = "✅ Pinned server key cleared - reconnect to trust new key"
                                        }
                                    ) {
                                        Text("Clear Key")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showClearConfirmation = false }) {
                                        Text(stringResource(R.string.action_cancel))
                                    }
                                }
                            )
                        }
                    }
                }
                
                HorizontalDivider()
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
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

                            // Update device name
                            if (deviceNameText.isNotBlank()) {
                                manager.updateDeviceName(deviceNameText.trim())
                            }

                            onDismiss()
                        }
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
                            text = stringResource(R.string.datapad_ecdh_info_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(R.string.datapad_ecdh_info_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.datapad_ecdh_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * Simple QR Scanner Dialog
 * Uses a text input as placeholder until ML Kit barcode scanner is integrated
 */
@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onQrScanned: (String) -> Unit
) {
    var qrInput by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan QR Code") },
        text = {
            Column {
                Text(
                    text = "Paste QR code data or scan using camera",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = qrInput,
                    onValueChange = { qrInput = it },
                    label = { Text("QR Code Data") },
                    placeholder = { Text("{\"type\":\"datapad_registration\"...}") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tip: Generate token on server with:\npython registration_token.py generate --server-ip <IP> --port 5010",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (qrInput.isNotBlank()) {
                        onQrScanned(qrInput.trim())
                    }
                },
                enabled = qrInput.isNotBlank()
            ) {
                Text("Process")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
