package com.example.checklist_interactive.ui.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import com.example.checklist_interactive.R
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
 * - Long press to reorder (drag)
 */
@Composable
fun TabBar(
    tabs: List<TabManager.TabInfo>,
    activeTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
    onNewTab: () -> Unit,
    onInternalFileViewerOpen: () -> Unit = {},
    onTabsReordered: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
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
            // Scrollable tabs (include New Tab button inside so it sits beside last tab)
            // Use LazyRow so items can report drag gestures and be reordered
            val scope = rememberCoroutineScope()

            // Fixed small button on the very left to open the internal file viewer ✅
            IconButton(
                onClick = onInternalFileViewerOpen,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = stringResource(R.string.open_internal_viewer),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            if (tabs.isEmpty()) {
                Text(
                    text = stringResource(R.string.tab_no_tabs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
            } else {
                // Shared drag state for reorder behavior
                val draggingIndex = remember { mutableStateOf(-1) }
                val dragOffset = remember { mutableStateOf(0f) }
                val targetIndex = remember { mutableStateOf(-1) }
                val itemWidthDp = 110.dp
                val itemWidthPx = with(LocalDensity.current) { itemWidthDp.toPx() }

                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(tabs, key = { index, t -> t.fileInfo.path ?: index }) { index, tabInfo ->
                        // Per-item animated translation based on drag state
                        val isDragging = index == draggingIndex.value

                        val translationForOthers = remember(draggingIndex.value, targetIndex.value) {
                            if (draggingIndex.value == -1) 0f
                            else {
                                val from = draggingIndex.value
                                val to = targetIndex.value
                                when {
                                    from < to && index in (from + 1)..to -> -itemWidthPx
                                    from > to && index in to..(from - 1) -> itemWidthPx
                                    else -> 0f
                                }
                            }
                        }

                        val translation = if (isDragging) dragOffset.value else translationForOthers
                        val animatedX by animateFloatAsState(targetValue = translation, animationSpec = tween(durationMillis = 150))

                        val reorderModifier = Modifier
                            .offset { IntOffset(animatedX.roundToInt(), 0) }
                            .zIndex(if (isDragging) 1f else 0f)
                            .pointerInput(index, tabs) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        // begin dragging this item
                                        draggingIndex.value = index
                                        targetIndex.value = index
                                        dragOffset.value = 0f
                                    },
                                    onDragEnd = {
                                        val from = draggingIndex.value
                                        val to = targetIndex.value
                                        if (from >= 0 && to >= 0 && from != to) {
                                            onTabsReordered(from, to)
                                        }
                                        // reset
                                        draggingIndex.value = -1
                                        dragOffset.value = 0f
                                        targetIndex.value = -1
                                    },
                                    onDragCancel = {
                                        draggingIndex.value = -1
                                        dragOffset.value = 0f
                                        targetIndex.value = -1
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset.value += dragAmount.x

                                        val from = draggingIndex.value
                                        if (from >= 0) {
                                            val deltaIndex = (dragOffset.value / itemWidthPx).roundToInt()
                                            val newTarget = (from + deltaIndex).coerceIn(0, tabs.size - 1)
                                            if (newTarget != targetIndex.value) targetIndex.value = newTarget
                                        }
                                    }
                                )
                            }

                        Box(modifier = reorderModifier) {
                            TabItem(
                                tabInfo = tabInfo,
                                isActive = index == activeTabIndex,
                                onSelect = { onTabSelected(index) },
                                onClose = { onTabClosed(index) }
                            )
                        }
                    }

                    // New Tab button inside lazy row so it appears right after the last tab
                    item {
                        IconButton(
                            onClick = onNewTab,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.tab_new),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
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
    val ellipsis = stringResource(R.string.common_ellipsis)
    val fileName = remember(tabInfo.fileInfo.displayName, ellipsis) {
        tabInfo.fileInfo.displayName.take(20) + if (tabInfo.fileInfo.displayName.length > 20) ellipsis else ""
    }
    
    val fileExtension = remember(tabInfo.fileInfo.extension) {
        tabInfo.fileInfo.extension.lowercase()
    }
    
    val icon = when {
        tabInfo.content is TabManager.TabContent.MapTab -> Icons.Default.Map
        fileExtension == "pdf" -> Icons.Default.PictureAsPdf
        fileExtension == "md" || fileExtension == "markdown" -> Icons.Default.Description
        else -> Icons.Default.Description
    }
    
    Card(
        modifier = Modifier
            .widthIn(min = 80.dp, max = 140.dp)
            .height(32.dp),
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
                .clickable(onClick = onSelect)
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
                    contentDescription = stringResource(R.string.tab_close),
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
    onInternalFileViewerOpen: () -> Unit = {},
    onTabsReordered: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    isScreenLocked: Boolean = false,
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
            // Avoid heavy animations when switching to resource-intensive tabs (Map/PDF) or when many tabs exist
            val targetTab = tabs.getOrNull(activeTabIndex)
            val isHeavy = when (targetTab?.content) {
                is TabManager.TabContent.MapTab -> true
                is TabManager.TabContent.DocumentTab -> targetTab.fileInfo.extension.lowercase() == "pdf"
                else -> false
            }
            val tooManyTabs = tabs.size > 6
            if (isHeavy || tooManyTabs) {
                // Immediate scroll avoids animation jank while heavy composables initialize
                pagerState.scrollToPage(activeTabIndex)
            } else {
                // Keep nice animation for light-weight tabs
                pagerState.animateScrollToPage(activeTabIndex)
            }
        }
    }

    // Notify when pager changes page
    LaunchedEffect(pagerState.currentPage) {
        if (tabs.isNotEmpty() && pagerState.currentPage != activeTabIndex) {
            onTabChanged(pagerState.currentPage)
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Content area (pager or empty state) - apply top padding so content does not overlap the TabBar
        if (tabs.isEmpty()) {
            // Show empty state
            Box(
                modifier = Modifier.fillMaxSize().padding(top = 40.dp),
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
                        text = stringResource(R.string.tab_no_open_tabs),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    TextButton(onClick = onNewTab) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.file_open))
                    }
                }
            }
        } else {
            // Horizontal pager for content
            // Maintain a small set of 'loaded' pages (current and neighbors) to defer heavy composables
            val loadedPages = remember { androidx.compose.runtime.mutableStateListOf<Int>() }

            // Ensure current page and neighbors are marked for loading
            LaunchedEffect(pagerState.currentPage, tabs.size) {
                val cur = pagerState.currentPage
                val toLoad = listOf(cur - 1, cur, cur + 1).filter { it in 0 until tabs.size }
                toLoad.forEach { idx -> if (!loadedPages.contains(idx)) loadedPages.add(idx) }

                // Optionally pre-load a bit further when there are few tabs
                if (tabs.size <= 4) {
                    (0 until tabs.size).forEach { i -> if (!loadedPages.contains(i)) loadedPages.add(i) }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp),
                userScrollEnabled = !isScreenLocked,
                key = { index -> tabs.getOrNull(index)?.fileInfo?.path ?: index }
            ) { pageIndex ->
                val tab = tabs.getOrNull(pageIndex)
                if (tab != null) {
                    if (loadedPages.contains(pageIndex)) {
                        // Compose the actual heavy content only when page is marked loaded
                        content(tab)
                    } else {
                        // Lightweight placeholder keeps the pager responsive
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                androidx.compose.material3.CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                androidx.compose.material3.Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.checklist_interactive.R.string.loading),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // Kick off loading of this page after a short delay so quick swipes still feel smooth
                        LaunchedEffect(pageIndex) {
                            kotlinx.coroutines.delay(120L)
                            if (!loadedPages.contains(pageIndex)) loadedPages.add(pageIndex)
                        }
                    }
                }
            }
        }

        // Overlayed Tab bar at top so it stays visible above native MapView (AndroidView)
        TabBar(
            tabs = tabs,
            activeTabIndex = activeTabIndex,
            onTabSelected = onTabChanged,
            onTabClosed = onTabClosed,
            onNewTab = onNewTab,
            onInternalFileViewerOpen = onInternalFileViewerOpen,
            onTabsReordered = onTabsReordered,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .zIndex(1f)
        )
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
                    contentDescription = stringResource(R.string.tab_previous),
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
                    contentDescription = stringResource(R.string.tab_next),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
