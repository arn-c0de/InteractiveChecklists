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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
    val currentKey by manager.preSharedKey.collectAsState()
    
    var portText by remember { mutableStateOf(currentPort.toString()) }
    var bindIpText by remember { mutableStateOf(currentBindIp) }
    var keyText by remember { mutableStateOf(currentKey) }
    var showKeyWarning by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
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
                        text = "DataPad Settings",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }
                
                Divider()
                
                // UDP Port Setting
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("UDP Port") },
                    placeholder = { Text("5010") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("Port range: 1024-65535")
                    }
                )
                
                // Bind IP Setting
                OutlinedTextField(
                    value = bindIpText,
                    onValueChange = { bindIpText = it },
                    label = { Text("Bind IP Address") },
                    placeholder = { Text("0.0.0.0 (all interfaces)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("Empty = listen on all interfaces")
                    }
                )
                
                // Pre-Shared Key Setting (masked by default)
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { 
                        keyText = it
                        showKeyWarning = it.length != 32 && it.isNotEmpty()
                    },
                    label = { Text("Pre-Shared Key (AES-256)") },
                    placeholder = { Text("32 characters (masked)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = showKeyWarning,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (keyVisible) "Hide key" else "Show key"
                            )
                        }
                    },
                    supportingText = {
                        if (showKeyWarning) {
                            Text(
                                text = "⚠️ Key must be exactly 32 characters (${keyText.length}/32)",
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text("Must match Python script key")
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
                            text = "The Pre-Shared Key must be exactly 32 bytes (256 bits) for AES-256 encryption.",
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
                            text = "⚠️ Default key detected. For production, reset and replace with a securely generated key (see docs/technical/AES_GCM_ENCRYPTION.md).",
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
                        Text("Cancel")
                    }

                    TextButton(onClick = {
                        // Reset to default key (manager will replace empty with default)
                        keyText = ""
                        showKeyWarning = false
                        manager.updatePreSharedKey("")
                    }) {
                        Text("Reset Key")
                    }
                    
                    Button(
                        onClick = {
                            // Validate and save
                            val port = portText.toIntOrNull()
                            if (port != null && port in 1024..65535) {
                                manager.updatePort(port)
                            }
                            
                            manager.updateBindIp(bindIpText.trim())
                            
                            if (keyText.length == 32 || keyText.isEmpty()) {
                                manager.updatePreSharedKey(keyText)
                                onDismiss()
                            }
                        },
                        enabled = !showKeyWarning
                    ) {
                        Text("Save & Restart")
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
                            text = "ℹ️ Configuration Tips",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "• Changing settings will restart the UDP socket\n" +
                                    "• The Pre-Shared Key must match on both sides\n" +
                                    "• Reset Key restores the default key (change it for production)\n" +
                                    "• Leave Bind IP empty to listen on all interfaces\n" +
                                    "• Default port: 5010",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚠️ Security note: For production, change the Pre-Shared Key and distribute it securely. Do NOT use the default key.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}
