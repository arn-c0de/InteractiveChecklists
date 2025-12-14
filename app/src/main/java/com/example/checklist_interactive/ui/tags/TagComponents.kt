package com.example.checklist_interactive.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.data.tags.FileTagManager

/**
 * Dialog for editing tags on a file
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTagEditorDialog(
    fileName: String,
    currentTags: Set<String>,
    allUsedTags: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var selectedTags by remember { mutableStateOf(currentTags.toMutableSet()) }
    var showAddCustomTag by remember { mutableStateOf(false) }
    var customTagText by remember { mutableStateOf("") }
    
    // Combine suggested tags with all used tags
    val allAvailableTags = (FileTagManager.SUGGESTED_TAGS + allUsedTags).distinct().sorted()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text("Edit tags")
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Currently selected tags as chips
                if (selectedTags.isNotEmpty()) {
                    Text(
                        text = "Selected tags:",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .height(32.dp) // fixed height to avoid intrinsic measurement on SubcomposeLayout
                    ) {
                        items(selectedTags.sorted()) { tag ->
                            FilterChip(
                                selected = true,
                                onClick = { selectedTags.remove(tag) },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
                
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                // Available tags
                Text(
                    text = "Available tags:",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(allAvailableTags) { tag ->
                        val isSelected = selectedTags.contains(tag)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) {
                                        selectedTags.remove(tag)
                                    } else {
                                        selectedTags.add(tag)
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedTags.add(tag)
                                    } else {
                                        selectedTags.remove(tag)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tag)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Add custom tag section
                if (showAddCustomTag) {
                    OutlinedTextField(
                        value = customTagText,
                        onValueChange = { customTagText = it },
                        label = { Text("Custom tag") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            Row {
                                IconButton(
                                    onClick = {
                                        val trimmed = customTagText.trim().lowercase()
                                        if (trimmed.isNotEmpty()) {
                                            selectedTags.add(trimmed)
                                            customTagText = ""
                                            showAddCustomTag = false
                                        }
                                    },
                                    enabled = customTagText.trim().isNotEmpty()
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Add")
                                }
                                IconButton(onClick = { 
                                    showAddCustomTag = false
                                    customTagText = ""
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                                }
                            }
                        }
                    )
                } else {
                    TextButton(
                        onClick = { showAddCustomTag = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add custom tag")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedTags) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Compact tag filter chip selector
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFilterBar(
    availableTags: Set<String>,
    selectedTags: Set<String>,
    filterMode: String,
    onTagToggle: (String) -> Unit,
    onClearAll: () -> Unit,
    onFilterModeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Filter by tags:",
                style = MaterialTheme.typography.labelMedium
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Filter mode toggle
                if (selectedTags.size > 1) {
                    FilterChip(
                        selected = filterMode == "any",
                        onClick = { onFilterModeChange(if (filterMode == "any") "all" else "any") },
                        label = { Text(if (filterMode == "any") "ANY" else "ALL") }
                    )
                }
                
                if (selectedTags.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("Clear all")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Spacer(modifier = Modifier.height(8.dp))

        // Display tags in 3 vertical columns so more tags are visible at once.
        // Important tags are shown first according to preferred order.
        val preferredOrder = listOf("startup", "start", "navigation", "landing", "combat", "takeoff", "taxi", "preflight", "postflight")
        val ordered = (preferredOrder + availableTags).map { it.lowercase() }.distinct()
        val normalizedAvailable = availableTags.map { it.lowercase() }.toSet()
        val filteredOrdered = ordered.filter { normalizedAvailable.contains(it) }

        val columns = 3
        val buckets = List(columns) { mutableListOf<String>() }
        filteredOrdered.forEachIndexed { idx, tag ->
            buckets[idx % columns].add(tag)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            buckets.forEach { col ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    col.forEach { tag ->
                        val originalTag = availableTags.firstOrNull { it.equals(tag, ignoreCase = true) } ?: tag
                        FilterChip(
                            selected = selectedTags.contains(originalTag),
                            onClick = { onTagToggle(originalTag) },
                            label = { Text(originalTag) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Simple tag display as chips
 */
@Composable
fun TagChips(
    tags: Set<String>,
    modifier: Modifier = Modifier,
    maxVisible: Int = 3
) {
    if (tags.isEmpty()) return
    
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.height(28.dp) // chips have fixed height, ensure row has fixed height too
    ) {
        val visibleTags = tags.sorted().take(maxVisible)
        items(visibleTags) { tag ->
            AssistChip(
                onClick = { },
                label = { 
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                modifier = Modifier.height(24.dp)
            )
        }
        
        if (tags.size > maxVisible) {
            item {
                AssistChip(
                    onClick = { },
                    label = { 
                        Text(
                            text = "+${tags.size - maxVisible}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.height(24.dp)
                )
            }
        }
    }
}
