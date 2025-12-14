package com.example.checklist_interactive.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.data.tabs.TabManager

/**
 * QuickTabSwitcher - Bottom sheet for quickly switching between recent tabs
 * 
 * Features:
 * - Shows recently opened documents
 * - Quick access to all open tabs
 * - One-tap switching
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTabSwitcherSheet(
    tabs: List<TabManager.TabInfo>,
    activeTabIndex: Int,
    navigationHistory: List<String>,
    onTabSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Open tabs",
                    style = MaterialTheme.typography.titleLarge
                )
                
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Recently used section
            if (navigationHistory.isNotEmpty()) {
                Text(
                    text = "Recently used",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Show recent documents from history
                val recentTabs = tabs.filter { tab ->
                    navigationHistory.take(5).contains(tab.fileInfo.path)
                }.sortedBy { tab ->
                    navigationHistory.indexOf(tab.fileInfo.path)
                }
                
                recentTabs.forEachIndexed { _, tab ->
                    val tabIndex = tabs.indexOf(tab)
                    TabHistoryItem(
                        tabInfo = tab,
                        isActive = tabIndex == activeTabIndex,
                        onClick = {
                            onTabSelected(tabIndex)
                            onDismiss()
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // All tabs section
            Text(
                text = "All tabs (${tabs.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(tabs) { index, tab ->
                    TabHistoryItem(
                        tabInfo = tab,
                        isActive = index == activeTabIndex,
                        onClick = {
                            onTabSelected(index)
                            onDismiss()
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Individual tab history item
 */
@Composable
private fun TabHistoryItem(
    tabInfo: TabManager.TabInfo,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val icon = when (tabInfo.fileInfo.extension.lowercase()) {
        "pdf" -> Icons.Default.PictureAsPdf
        "md", "markdown" -> Icons.Default.Description
        else -> Icons.Default.Description
    }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tabInfo.fileInfo.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                
                if (tabInfo.pageNumber >= 0) {
                    Text(
                        text = "Page ${tabInfo.pageNumber + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            
            if (isActive) {
                    Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Floating Action Button for quick tab switching
 */
@Composable
fun QuickTabSwitchFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = "Switch tabs"
        )
    }
}
