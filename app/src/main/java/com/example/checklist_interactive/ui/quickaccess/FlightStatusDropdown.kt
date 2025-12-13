package com.example.checklist_interactive.ui.quickaccess

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reusable flight status dropdown component
 * Shows a dropdown menu with standard flight statuses
 */
@Composable
fun FlightStatusDropdown(
    currentStatus: String,
    onStatusChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val statusOptions = listOf(
        "Idle",
        "Startup",
        "Taxi",
        "Holding",
        "Start Navigation",
        "Navigation",
        "Landing",
        "Shutdown"
    )
    
    var expanded by remember { mutableStateOf(false) }
    val displayStatus = currentStatus.ifBlank { "Idle" }
    
    if (compact) {
        // Compact mode: clickable text with dropdown
        Box(modifier = modifier.wrapContentSize()) {
            Text(
                text = displayStatus,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.clickable { expanded = !expanded }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                statusOptions.forEach { status ->
                    DropdownMenuItem(
                        text = { Text(status) },
                        onClick = {
                            onStatusChange(status)
                            expanded = false
                        }
                    )
                }
            }
        }
    } else {
        // Full mode: OutlinedTextField with icon button
        Box(modifier = modifier.wrapContentSize()) {
            OutlinedTextField(
                value = displayStatus,
                onValueChange = {},
                label = { Text("Status") },
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }
                },
                modifier = Modifier.width(170.dp).clickable { expanded = true }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                statusOptions.forEach { status ->
                    DropdownMenuItem(
                        text = { Text(status) },
                        onClick = {
                            onStatusChange(status)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
