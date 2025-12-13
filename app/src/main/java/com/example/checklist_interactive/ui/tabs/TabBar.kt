package com.example.checklist_interactive.ui.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.data.tabs.TabManager
import kotlinx.coroutines.launch

/**
 * TabBar - Shows horizontal tabs for open documents
 * 
 * Features:
 * - Scrollable tab bar
 * - Active tab highlighting
 * - Close button on each tab
 * - File type icons (MD/PDF)
 * - Long press to close
 */
@Composable
fun TabBar(
    tabs: List<TabManager.TabInfo>,
    activeTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Scrollable tabs
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tabs.isEmpty()) {
                    Text(
                        text = "Keine Tabs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                } else {
                    tabs.forEachIndexed { index, tabInfo ->
                        TabItem(
                            tabInfo = tabInfo,
                            isActive = index == activeTabIndex,
                            onSelect = { onTabSelected(index) },
                            onClose = { onTabClosed(index) }
                        )
                    }
                }
            }
            
            // New Tab button
            IconButton(
                onClick = onNewTab,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Neuer Tab",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Individual tab item
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabItem(
    tabInfo: TabManager.TabInfo,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val fileName = remember(tabInfo.fileInfo.displayName) {
        tabInfo.fileInfo.displayName.take(20) + if (tabInfo.fileInfo.displayName.length > 20) "..." else ""
    }
    
    val fileExtension = remember(tabInfo.fileInfo.extension) {
        tabInfo.fileInfo.extension.lowercase()
    }
    
    val icon = when (fileExtension) {
        "pdf" -> Icons.Default.PictureAsPdf
        "md", "markdown" -> Icons.Default.Description
        else -> Icons.Default.Description
    }
    
    Card(
        modifier = Modifier
            .widthIn(min = 80.dp, max = 140.dp)
            .height(32.dp)
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onClose
            ),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 3.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon and filename
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            
            // Close button
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Tab schließen",
                    modifier = Modifier.size(12.dp),
                    tint = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

/**
 * TabbedDocumentViewer - Combines TabBar with HorizontalPager for swipeable tabs
 * 
 * Features:
 * - Horizontal swipe between tabs
 * - Sync with TabBar
 * - Smooth animations
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabbedDocumentViewer(
    tabs: List<TabManager.TabInfo>,
    activeTabIndex: Int,
    onTabChanged: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (TabManager.TabInfo) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = if (tabs.isEmpty()) 0 else activeTabIndex.coerceIn(0, tabs.size - 1),
        pageCount = { tabs.size }
    )
    
    // Sync pager state with active tab index
    LaunchedEffect(activeTabIndex, tabs.size) {
        if (tabs.isNotEmpty() && activeTabIndex != pagerState.currentPage && activeTabIndex in 0 until tabs.size) {
            pagerState.animateScrollToPage(activeTabIndex)
        }
    }
    
    // Notify when pager changes page
    LaunchedEffect(pagerState.currentPage) {
        if (tabs.isNotEmpty() && pagerState.currentPage != activeTabIndex) {
            onTabChanged(pagerState.currentPage)
        }
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        // Tab bar at top (always visible, even when empty)
        TabBar(
            tabs = tabs,
            activeTabIndex = activeTabIndex,
            onTabSelected = onTabChanged,
            onTabClosed = onTabClosed,
            onNewTab = onNewTab
        )
        
        // Content area
        if (tabs.isEmpty()) {
            // Show empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "Keine geöffneten Tabs",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    TextButton(onClick = onNewTab) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Datei öffnen")
                    }
                }
            }
        } else {
            // Horizontal pager for content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { index -> tabs.getOrNull(index)?.fileInfo?.path ?: index }
            ) { pageIndex ->
                val tab = tabs.getOrNull(pageIndex)
                if (tab != null) {
                    content(tab)
                }
            }
        }
    }
}

/**
 * Compact tab indicator - Shows tab count and current tab
 * Useful when tab bar is hidden or in compact mode
 */
@Composable
fun CompactTabIndicator(
    currentTab: Int,
    totalTabs: Int,
    onPreviousTab: () -> Unit,
    onNextTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (totalTabs <= 1) return
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousTab,
                enabled = currentTab > 0,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close, // Use appropriate icon
                    contentDescription = "Previous tab",
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Text(
                text = "${currentTab + 1}/$totalTabs",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            IconButton(
                onClick = onNextTab,
                enabled = currentTab < totalTabs - 1,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close, // Use appropriate icon
                    contentDescription = "Next tab",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
