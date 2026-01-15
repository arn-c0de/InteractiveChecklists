package com.example.checklist_interactive.ui.checklist.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.example.checklist_interactive.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.detectTapGestures

import kotlinx.coroutines.delay
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import com.example.checklist_interactive.data.shortcuts.PageHighlightManager
import com.example.checklist_interactive.data.shortcuts.ShortcutManager
import com.example.checklist_interactive.data.shortcuts.LastPageManager
import com.example.checklist_interactive.data.prefs.InvertColorPrefManager
import com.example.checklist_interactive.data.prefs.PreferencesManager
import java.io.File
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Flight
import android.content.ClipboardManager
import com.example.checklist_interactive.ui.datapad.LocalDataPadManager
import com.example.checklist_interactive.ui.datapad.DataPadPopup
import android.content.ClipData
import android.content.Context
import android.content.SharedPreferences
import com.example.checklist_interactive.ui.quickaccess.QuickAccessSheet
import com.example.checklist_interactive.data.quicknotes.QuickNoteManager
import com.example.checklist_interactive.ui.common.DraggableFab
import com.example.checklist_interactive.ui.common.FABOverlay
import com.example.checklist_interactive.ui.common.PdfViewerFABs
import com.example.checklist_interactive.ui.checklist.pdf.AnnotationsRepository
import com.example.checklist_interactive.ui.checklist.pdf.AnnotationStroke

/**
 * Custom PDF viewer with no external heavy dependencies.
 * Uses Android's built-in PdfRenderer (available since API 21).
 * Supports annotations (draw, highlight, delete).
 * Includes zoom, page highlights and shortcuts.
 */
enum class BrushType { Pen, Marker, Special }

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PdfViewer(
    pdfPath: String,
    title: String,
    onBack: () -> Unit,
    onShowFileList: (() -> Unit)? = null,
    isInternalFile: Boolean = false,
    isDarkTheme: Boolean = false,
    onToggleTheme: (() -> Unit)? = null,
    initialPage: Int = -1,
    onPageChange: ((Int) -> Unit)? = null,
    onOpenLinkedDocument: ((filePath: String, pageNumber: Int?) -> Unit)? = null,
    documentId: String? = null
) {
    // Optional stable document id used for saving/loading last-page info.
    // If not provided, the actual pdfPath is used as key. Callers that pass a temp path
    // (e.g., InternalFileViewer) can provide the original path as `documentId` so
    // LastPageManager keeps a stable key across container lifecycles.
    val context = LocalContext.current

    // Managers for shortcuts, highlights and last-page state
    val shortcutManager = remember { ShortcutManager(context) }
    val highlightManager = remember { PageHighlightManager(context) }
    val lastPageManager = remember { LastPageManager(context) }
    val providedNoteManager = com.example.checklist_interactive.ui.quickaccess.LocalQuickNoteManager.current
    val quickNoteManager = providedNoteManager ?: remember { QuickNoteManager(context) }

    // Load last-opened page unless initialPage is explicitly set (<0 = not set)
    // Use a stable ID for storing last-page info; allow callers to pass an explicit documentId
    // so that temporary / cache paths (e.g., asset temp files) map to a stable identifier.
    val docIdForLastPage = documentId ?: pdfPath
    val effectiveInitialPage = remember(pdfPath, initialPage) {
        if (initialPage < 0) {
            lastPageManager.getLastPage(docIdForLastPage).coerceAtLeast(0)
        } else {
            initialPage
        }
    }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var annotateMode by remember { mutableStateOf(false) }
    var brushType by remember { mutableStateOf(BrushType.Pen) }
    // Backwards-compatible derived flag used by drawing/rendering logic
    val highlightMode by remember(brushType) { derivedStateOf { brushType == BrushType.Marker } }
    var eraseMode by remember { mutableStateOf(false) }
    var strokeWidth by remember { mutableStateOf(4f) }
    var selectedColor by remember { mutableStateOf(Color.Red) }
    val strokes = remember { mutableStateListOf<AnnotationStroke>() }
    var currentPage by remember { mutableStateOf(effectiveInitialPage) }
    // Hover / long-press hint text shown for toolbar icons
    var hoveredHint by remember { mutableStateOf<String?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var eraseRadius by remember { mutableStateOf(50f) }
    val pageScales = remember { mutableStateMapOf<Int, Float>() }
    val pageOffsetsX = remember { mutableStateMapOf<Int, Float>() }
    val pageOffsetsY = remember { mutableStateMapOf<Int, Float>() }
    var showShortcutDialog by remember { mutableStateOf(false) }
    var shortcutName by remember { mutableStateOf("") }
    val pageAspectRatios = remember { mutableStateMapOf<Int, Float>() }
    // Transient scale cache per page so UI (e.g., percent label) can show live values without committing to persistent state
    val transientScaleMap = remember { mutableStateMapOf<Int, Float>() }
    var pageHighlights by remember { mutableStateOf<List<Int>>(emptyList()) }
    val invertColorPrefManager = remember { InvertColorPrefManager(context) }
    var invertColors by remember { mutableStateOf(false) }
    var showTocDialog by remember { mutableStateOf(false) }
    var chapters by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var outlineItems by remember { mutableStateOf<List<PdfOutlineItem>>(emptyList()) }
    val outlineExtractor = remember { PdfOutlineExtractor(context) }
    var showQuickAccess by remember { mutableStateOf(false) }
    var showDataPad by remember { mutableStateOf(false) }
    val datapadManager = LocalDataPadManager.current
    val datapadEnabled by datapadManager.isEnabled.collectAsState()
    val prefsManager = remember { PreferencesManager(context) }
    var showToolbar by remember { mutableStateOf(prefsManager.isPdfToolbarVisible()) }

    // Listen to preference changes so toolbar state stays in sync across multiple open PDFs
    DisposableEffect(prefsManager) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "pdf_toolbar_visible") {
                showToolbar = prefsManager.isPdfToolbarVisible()
            }
        }
        prefsManager.registerOnChangeListener(listener)
        onDispose {
            prefsManager.unregisterOnChangeListener(listener)
        }
    }

    // Track if outline has been extracted for this document to avoid re-extracting on every page change
    var outlineExtracted by remember(pdfPath) { mutableStateOf(false) }
    // Sharpening feature
    var sharpenEnabled by remember { mutableStateOf(false) }
    var sharpenIntensity by remember { mutableStateOf(1.5f) }
    // When true: always apply sharpening to the currently visible page
    // even when switching pages. When false: sharpening is disabled
    // and the slider is only used to set intensity.
    var sharpenApplyToCurrentPage by remember { mutableStateOf(false) }

    // Text extraction
    val textExtractor = remember { PdfTextExtractor(context) }
    val pageTextBlocks = remember { mutableStateMapOf<Int, List<PdfTextBlock>>() }
    var textSelectionEnabled by remember { mutableStateOf(true) }
    var showTextDialog by remember { mutableStateOf(false) }
    var dialogPageText by remember { mutableStateOf("") }
    val textExtractionJobs = remember { mutableMapOf<Int, Job>() }

    // Cleanup textExtractor on dispose
    DisposableEffect(Unit) {
        onDispose {
            textExtractionJobs.values.forEach { it.cancel() }
            textExtractionJobs.clear()
            textExtractor.cleanup()
        }
    }

    // Use individual states per page instead of StateMap to prevent global recomposition
    val pageBitmapStates = remember { mutableMapOf<Int, MutableState<Bitmap?>>() }
    fun getPageBitmapState(pageIndex: Int): MutableState<Bitmap?> {
        return pageBitmapStates.getOrPut(pageIndex) { mutableStateOf(null) }
    }

    val renderingPages = remember { mutableStateSetOf<Int>() }
    val coroutineScope = rememberCoroutineScope()
    // Dedicated background scope for long-running operations (indexing, batch renders)
    val backgroundJob: Job = remember { Job() }
    val backgroundScope: CoroutineScope = remember { CoroutineScope(Dispatchers.Default + backgroundJob) }
    // LruCache for rendered bitmaps (in KB) - no automatic removal, just caching
    val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    val cacheSizeKb = maxMemoryKb / 8
    val cacheKeys = remember { mutableStateListOf<Int>() }
    val bitmapCache = remember {
        object : LruCache<Int, Bitmap>(cacheSizeKb) {
            override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
            override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
                // Don't clear the page state immediately - let it be cleared only when we explicitly re-render
                // This prevents flickering during scroll when cache evicts items
                key?.let { k ->
                    cacheKeys.remove(k)
                }
                // Never recycle bitmaps - let GC handle cleanup
            }
        }
    }
    // Track the scale at which we last rendered each page
    val renderScaleMap = remember { mutableStateMapOf<Int, Float>() }
    // Debounce mechanism for re-rendering on zoom
    var zoomRenderJob by remember { mutableStateOf<Job?>(null) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = effectiveInitialPage)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.roundToPx() }

    // Preferences manager for storing FAB positions (already initialized above)

    // PDF laden: open once, but render pages lazily.
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    val pdfRenderMutex = remember { Mutex() }

    // Helper: render a page to bitmap cache at a target width in pixels
    suspend fun renderPageToCache(pageIndex: Int, targetWidthPx: Int, renderedScale: Float = 1f) {
        // Prevent multiple concurrent renders of same page
        if (renderingPages.contains(pageIndex)) return
        if (!backgroundJob.isActive) return
        renderingPages.add(pageIndex)
        try {
            withContext(Dispatchers.IO) {
                val pdf = pdfRenderer ?: return@withContext
                // Serialize calls to PdfRenderer to avoid crashes (PdfRenderer isn't guaranteed to be thread-safe across multiple concurrent page rendering)
                pdfRenderMutex.withLock {
                    val page = try { pdf.openPage(pageIndex) } catch (_: Exception) { null }
                    page?.let {
                        val renderWidth = targetWidthPx.coerceAtLeast(1)
                        val renderHeight = (renderWidth.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                        // Try RGB_565 (lower memory) first; fallback to ARGB_8888 when necessary.
                        var bitmap: Bitmap? = null
                        try {
                            bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.RGB_565)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        } catch (e: Throwable) {
                            // If rendering fails due to unsupported pixel format, try ARGB_8888
                            bitmap?.let { if (!it.isRecycled) it.recycle() }
                            bitmap = try { Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888) } catch (_: Throwable) { null }
                            bitmap?.let { page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                        }
                        page.close()
                        bitmap?.let { bmp ->
                            bitmapCache.put(pageIndex, bmp)
                            renderScaleMap[pageIndex] = renderedScale
                            cacheKeys.add(pageIndex)
                            withContext(Dispatchers.Main) {
                                getPageBitmapState(pageIndex).value = bmp
                            }
                        }
                        if (bitmap == null) {
                            withContext(Dispatchers.Main) {
                                errorMessage = context.getString(R.string.pdf_error_unsupported_pixel_format, pageIndex + 1)
                            }
                        }
                    }
                }
            }
        } finally {
            renderingPages.remove(pageIndex)
        }
    }

    LaunchedEffect(pdfPath, isInternalFile) {
        // Load invert state from prefs
        invertColors = invertColorPrefManager.isInverted(pdfPath)
        isLoading = true
        errorMessage = null
        try {
            withContext(Dispatchers.IO) {
                val loadedPdfFile = if (isInternalFile) {
                    // Use internal file directly
                    File(pdfPath)
                } else {
                    // Copy asset to temporary file
                    val assetManager = context.assets
                    val inputStream = assetManager.open(pdfPath)
                    val tempFile = File(context.cacheDir, "temp_${pdfPath.hashCode()}.pdf")
                    tempFile.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    tempFile
                }

                pdfFile = loadedPdfFile
                fileDescriptor = ParcelFileDescriptor.open(loadedPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(fileDescriptor!!)
                pageCount = pdfRenderer!!.pageCount

                // Pre-calculate only a small set of page aspect ratios to prevent layout jumping
                // for the first visible pages. Compute the remaining ones asynchronously
                // in small batches to avoid long blocking times on large PDFs.
                if (pageCount > 0) {
                    try {
                        val firstPage = pdfRenderer!!.openPage(0)
                        val ar = firstPage.width.toFloat() / firstPage.height.toFloat()
                        firstPage.close()
                        withContext(Dispatchers.Main) {
                            pageAspectRatios[0] = ar
                        }
                    } catch (_: Exception) {}
                }

                    // Extract PDF outline (bookmarks) asynchronously to avoid blocking
                    // Only extract once per document to avoid re-extraction on every page change
                    if (!outlineExtracted) {
                        backgroundScope.launch(Dispatchers.IO) {
                            try {
                                val extracted = outlineExtractor.extractOutline(loadedPdfFile)
                                withContext(Dispatchers.Main) {
                                    outlineItems = extracted
                                    outlineExtracted = true
                                    android.util.Log.d("PdfViewer", "Extracted outline items: ${outlineItems.size} -> ${outlineItems.joinToString(", ") { it.title + ":" + it.pageNumber }}")
                                    // Recompute chapters if no outline was previously available
                                    if (outlineItems.isNotEmpty()) {
                                        chapters = outlineItems.map { it.title to it.pageNumber }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.d("PdfViewer", "Outline extraction failed: ${e.message}")
                                withContext(Dispatchers.Main) {
                                    outlineExtracted = true // Mark as extracted even on failure to prevent retries
                                }
                            }
                        }
                    }

                // Render only an initial subset of pages (effectiveInitialPage and nearby), rest are loaded on demand.
                val initialIndices = listOf(effectiveInitialPage - 1, effectiveInitialPage, effectiveInitialPage + 1).distinct().filter { it in 0 until pageCount }
                // Also pre-calc aspect ratios for the first few pages we'll render immediately
                initialIndices.forEach { idx ->
                    try {
                        if (!pageAspectRatios.containsKey(idx)) {
                            val p = pdfRenderer!!.openPage(idx)
                            val ar = p.width.toFloat() / p.height.toFloat()
                            p.close()
                            withContext(Dispatchers.Main) {
                                pageAspectRatios[idx] = ar
                            }
                        }
                    } catch (_: Exception) {}
                }
                for (pageIndex in initialIndices) {
                    // render initial pages via the centralized helper (it serializes PdfRenderer access)
                    backgroundScope.launch {
                        try {
                            renderPageToCache(pageIndex, screenWidthPx, renderedScale = 1f)
                        } catch (_: Exception) {}
                    }
                }

                // Start async computation of remaining page aspect ratios in the background
                // to avoid long UI-blocking loads on large documents. Do this after initial
                // rendering is kicked off so the user sees content quickly.
                backgroundScope.launch(Dispatchers.Default) {
                    val batchSize = 32
                    var start = 0
                    while (start < pageCount) {
                        if (!backgroundJob.isActive) break
                        val end = (start + batchSize).coerceAtMost(pageCount)
                        for (i in start until end) {
                            if (!pageAspectRatios.containsKey(i)) {
                                try {
                                    val curPdf = pdfRenderer ?: continue
                                    if (!backgroundJob.isActive) break
                                    val p = curPdf.openPage(i)
                                    val ar = p.width.toFloat() / p.height.toFloat()
                                    p.close()
                                    withContext(Dispatchers.Main) {
                                        pageAspectRatios[i] = ar
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                        start = end
                        kotlinx.coroutines.delay(50L)
                    }
                }

                // Do not delete the temporary file here; delete it later on composable disposal
                // to avoid races with background processing (outline extraction, async aspect ratio computation, etc.).
            }
            isLoading = false
            // Populate chapters: use outline if available, otherwise fallback to page list
            chapters = if (outlineItems.isNotEmpty()) {
                outlineItems.map { it.title to it.pageNumber }
            } else {
                (0 until pageCount).map { idx -> context.getString(R.string.tab_page_number, idx + 1) to idx }
            }
        } catch (e: Exception) {
            errorMessage = context.getString(R.string.error_loading_pdf, e.message ?: "")
            isLoading = false
        }
    }

    // Close PDF renderer and file descriptor when composable leaves scope
    DisposableEffect(pdfPath) {
        onDispose {
            // Cancel background tasks before closing renderer or deleting files
            try { backgroundJob.cancel() } catch (_: Exception) {}
            try {
                pdfRenderer?.close()
            } catch (_: Exception) {}
            try {
                fileDescriptor?.close()
            } catch (_: Exception) {}
            // Clear cache and bitmap states
            bitmapCache.evictAll()
            cacheKeys.clear()
            pageBitmapStates.values.forEach { it.value = null }
            pageBitmapStates.clear()
            // Delete temporary cached file if we created one from assets
            try {
                if (!isInternalFile) {
                    pdfFile?.delete()
                }
            } catch (_: Exception) {}
        }
    }


    // Only request a render if we do not already have a bitmap, and not already rendering
    fun maybeRequestRender(pageIndex: Int, widthPx: Int) {
        if (pageIndex !in 0 until pageCount) return
        if (getPageBitmapState(pageIndex).value != null) return
        if (bitmapCache.get(pageIndex) != null) return
        if (renderingPages.contains(pageIndex)) return
        backgroundScope.launch {
            try {
                renderPageToCache(pageIndex, widthPx)
            } catch (_: Exception) {}
        }
    }

    // Load annotations (async on background thread)
    LaunchedEffect(pdfPath) {
        withContext(Dispatchers.IO) {
            val loadedStrokes = AnnotationsRepository.load(context, pdfPath)
            val loadedHighlights = highlightManager.getHighlightsForFile(pdfPath).map { it.pageNumber }
            withContext(Dispatchers.Main) {
                strokes.clear()
                strokes.addAll(loadedStrokes)
                pageHighlights = loadedHighlights
            }
        }

        // Note: The main PDF loading coroutine populates `chapters` after pages are loaded.
    }

    // Current page based on scroll position — render current and neighbors on demand
    // Use debouncing to avoid rapid page changes during scroll
    var pageUpdateJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.isScrollInProgress) {
        val visibleIndex = listState.firstVisibleItemIndex
        // Ignore invalid indices
        if (visibleIndex !in 0 until pageCount) return@LaunchedEffect
        
        // Cancel previous update job
        pageUpdateJob?.cancel()
        
        // If scrolling, debounce the update
        if (listState.isScrollInProgress) {
            pageUpdateJob = coroutineScope.launch {
                delay(100L) // Wait for scroll to stabilize
                if (visibleIndex == listState.firstVisibleItemIndex && visibleIndex != currentPage) {
                    currentPage = visibleIndex
                    onPageChange?.invoke(currentPage)
                }
            }
        } else {
            // Scroll finished, update immediately if changed
            if (visibleIndex != currentPage) {
                currentPage = visibleIndex
                onPageChange?.invoke(currentPage)
            }
        }
        
        // Render visible pages
        val cur = visibleIndex
        val indices = listOf(cur - 1, cur, cur + 1).filter { it in 0 until pageCount }
        for (idx in indices) maybeRequestRender(idx, screenWidthPx)

        // Extract text for visible pages
        // Only extract if not already cached or being extracted
        pdfFile?.let { file ->
            for (idx in indices) {
                // Skip if already extracted or currently extracting
                if (pageTextBlocks.containsKey(idx) || textExtractionJobs.containsKey(idx)) continue

                val job = backgroundScope.launch(Dispatchers.IO) {
                    try {
                        val textBlocks = textExtractor.extractTextBlocks(file, idx)
                        withContext(Dispatchers.Main) {
                            pageTextBlocks[idx] = textBlocks
                            textExtractionJobs.remove(idx)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Mark as extracted even on error to prevent retry spam
                        withContext(Dispatchers.Main) {
                            pageTextBlocks[idx] = emptyList()
                            textExtractionJobs.remove(idx)
                        }
                    }
                }
                textExtractionJobs[idx] = job
            }
        }
    }

    // Scroll to page when initialPage or the document (pdfPath/pageCount) changes.
    // This ensures the last opened page is loaded when re-entering the same file.
    var initialScrollDone by remember(pdfPath) { mutableStateOf(false) }
    LaunchedEffect(pdfPath, pageCount, initialPage) {
        if (pageCount > 0 && !initialScrollDone) {
            val target = if (initialPage >= 0) initialPage else lastPageManager.getLastPage(docIdForLastPage)
            val clamped = target.coerceIn(0, pageCount - 1)
            
            // Wait for layout to be ready
            delay(100L)
            
            // Retry scroll up to 3 times if needed
            var scrollSuccess = false
            for (attempt in 1..3) {
                try {
                    listState.scrollToItem(clamped)
                    delay(50L)
                    
                    // Verify scroll was successful
                    if (listState.firstVisibleItemIndex == clamped) {
                        scrollSuccess = true
                        break
                    }
                } catch (e: Exception) {
                    if (attempt == 3) {
                        android.util.Log.w("PdfViewer", "Failed to scroll to page $clamped after $attempt attempts")
                    } else {
                        delay(100L) // Wait before retry
                    }
                }
            }
            
            // Update currentPage only after successful scroll
            if (scrollSuccess || listState.firstVisibleItemIndex in 0 until pageCount) {
                currentPage = listState.firstVisibleItemIndex.coerceIn(0, pageCount - 1)
                initialScrollDone = true
            }
        }
    }

    // Save the current page on every page change (debounced)
    var savePageJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(currentPage) {
        if (pageCount > 0 && currentPage in 0 until pageCount) {
            // Cancel previous save job
            savePageJob?.cancel()
            
            // Debounce saves to avoid excessive writes during rapid scrolling
            savePageJob = coroutineScope.launch {
                delay(300L)
                try {
                    lastPageManager.saveLastPage(docIdForLastPage, currentPage)
                } catch (e: Exception) {
                    android.util.Log.w("PdfViewer", "Failed to save last page: ${e.message}")
                }
            }
        }
    }

    // Debounced re-render on zoom change for current page
    LaunchedEffect(pageScales[currentPage], currentPage) {
        // We re-render only if scale increased enough relative to the last rendered scale for this page.
        zoomRenderJob?.cancel()
        val currentScale = pageScales[currentPage] ?: 1f
        // Only attempt to re-render if we have a renderer ready and a page visible
        val renderedScale = renderScaleMap[currentPage] ?: 0f
        // If scale is close to the renderedScale, skip
        if (renderedScale > 0f && currentScale <= renderedScale * 1.15f) return@LaunchedEffect
        if (pdfRenderer == null) return@LaunchedEffect
        // Wait a bit to avoid over-rendering while the user is still pinching
        zoomRenderJob = backgroundScope.launch {
            kotlinx.coroutines.delay(350L)
            // Determine desired pixel width to render for the current scale
            val currentScale = pageScales[currentPage] ?: 1f
            val baseBitmap = getPageBitmapState(currentPage).value ?: bitmapCache.get(currentPage)
            val baseWidth = baseBitmap?.width ?: screenWidthPx
            val targetWidth = (baseWidth * currentScale).toInt().coerceAtMost(screenWidthPx * 3)
            // Only render if significant increase in required resolution
            val prevScale = renderScaleMap[currentPage] ?: 1f
            val requestedScaleForRender = targetWidth.toFloat() / baseWidth
            if (requestedScaleForRender > prevScale * 1.15f) {
                try {
                    renderPageToCache(currentPage, targetWidth, renderedScale = requestedScaleForRender)
                } catch (_: Exception) {}
            }
        }
    }

    Scaffold(
        topBar = {
            // Use a smaller TopAppBar variant by setting height and reduced sizes
            TopAppBar(
                modifier = Modifier.height(48.dp),
                // Build a short chapter title for the currently visible page (if available)
                title = {
                    val currentChapterTitle = remember(currentPage, chapters) {
                        chapters.lastOrNull { it.second <= currentPage }?.first ?: ""
                    }
                    val titleText = if (currentChapterTitle.isNotBlank()) {
                        "$title — $currentChapterTitle (${stringResource(R.string.pdf_page_progress, currentPage + 1, pageCount)})"
                    } else {
                        "$title (${stringResource(R.string.pdf_page_progress, currentPage + 1, pageCount)})"
                    }
                    Text(titleText, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    HintIconButton(
                        onClick = {
                            AnnotationsRepository.save(context, pdfPath, strokes.toList())
                            lastPageManager.saveLastPage(docIdForLastPage, currentPage)
                            onBack()
                        },
                        hint = context.getString(R.string.back),
                        onHintChange = { hoveredHint = it },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.back), modifier = Modifier.size(18.dp))
                    }
                },
                actions = {
                    // Outline button next to the page number
                    HintIconButton(
                        onClick = { showTocDialog = true },
                        hint = if (outlineItems.isNotEmpty()) stringResource(R.string.pdf_outline) else stringResource(R.string.pdf_no_chapters),
                        onHintChange = { hoveredHint = it },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = stringResource(R.string.pdf_outline),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // Sharpen toggle moved to top bar
                    HintIconButton(
                        onClick = { sharpenEnabled = !sharpenEnabled },
                        hint = stringResource(R.string.pdf_sharpen),
                        onHintChange = { hoveredHint = it },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusWeak,
                            contentDescription = stringResource(R.string.pdf_sharpen),
                            tint = if (sharpenEnabled) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    HintIconButton(
                        onClick = {
                            invertColors = !invertColors
                            invertColorPrefManager.setInverted(pdfPath, invertColors)
                        },
                        hint = stringResource(R.string.pdf_invert_colors),
                        onHintChange = { hoveredHint = it },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.InvertColors, contentDescription = stringResource(R.string.pdf_invert_colors), modifier = Modifier.size(18.dp))
                    }
                }
            )
        },
        floatingActionButton = { /* moved to overlay so positions are draggable */ }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Compact toolbar with scroll - collapsible
                    AnimatedVisibility(
                        visible = showToolbar,
                        enter = expandVertically() + slideInVertically(),
                        exit = shrinkVertically() + slideOutVertically()
                    ) {
                        Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        // First row: drawing & annotations
                        var showDeleteAllDialog by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HintIconButton(
                                onClick = {
                                    annotateMode = !annotateMode
                                    if (annotateMode) eraseMode = false
                                },
                                hint = stringResource(R.string.pdf_hint_draw),
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Create,
                                    contentDescription = stringResource(R.string.pdf_draw),
                                    tint = if (annotateMode) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // (Outline button present in the top bar; removed duplicate here.)
                            HintIconButton(
                                onClick = {
                                    brushType = if (brushType == BrushType.Marker) BrushType.Pen else BrushType.Marker
                                    if (brushType == BrushType.Marker) {
                                        annotateMode = true
                                        eraseMode = false
                                    }
                                },
                                hint = stringResource(R.string.pdf_brush_type_highlight),
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Highlight,
                                    contentDescription = stringResource(R.string.pdf_marker),
                                    tint = if (highlightMode) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            HintIconButton(
                                onClick = {
                                    val isHighlighted = highlightManager.togglePageHighlight(pdfPath, currentPage)
                                    pageHighlights = highlightManager.getHighlightsForFile(pdfPath).map { it.pageNumber }
                                },
                                hint = stringResource(R.string.pdf_highlight_page),
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = stringResource(R.string.pdf_highlight_page),
                                    tint = if (pageHighlights.contains(currentPage)) Color.Yellow else LocalContentColor.current,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            HintIconButton(
                                onClick = {
                                    shortcutName = context.getString(R.string.pdf_shortcut_name_format, title, currentPage + 1)
                                    showShortcutDialog = true
                                },
                                hint = stringResource(R.string.pdf_shortcut_create),
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.BookmarkAdd,
                                    contentDescription = stringResource(R.string.pdf_shortcut),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Link to quick note
                            HintIconButton(
                                onClick = {
                                    quickNoteManager.addMarkdownLink(
                                        filePath = pdfPath,
                                        fileName = title,
                                        pageNumber = currentPage
                                    )
                                },
                                hint = stringResource(R.string.pdf_link),
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = stringResource(R.string.pdf_link),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Text anzeigen Button
                            HintIconButton(
                                onClick = {
                                    pdfFile?.let { file ->
                                        backgroundScope.launch(Dispatchers.IO) {
                                            val pageText = textExtractor.extractPageText(file, currentPage)
                                            withContext(Dispatchers.Main) {
                                                dialogPageText = pageText
                                                showTextDialog = true
                                            }
                                        }
                                    }
                                },
                                hint = stringResource(R.string.pdf_show_text),
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.pdf_show_text),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            HintIconButton(
                                onClick = {
                                    val idx = strokes.indexOfLast { it.page == currentPage }
                                    if (idx >= 0) strokes.removeAt(idx)
                                },
                                hint = stringResource(R.string.pdf_hint_undo),
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = stringResource(R.string.cd_undo),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            HintIconButton(
                                onClick = { showDeleteAllDialog = true },
                                hint = stringResource(R.string.pdf_delete_all_message),
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.cd_clear),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            HintIconButton(
                                onClick = {
                                    AnnotationsRepository.save(context, pdfPath, strokes.toList())
                                },
                                hint = stringResource(R.string.pdf_hint_save),
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = stringResource(R.string.action_save),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Delete All Dialog
                        if (showDeleteAllDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteAllDialog = false },
                                title = { Text(stringResource(R.string.pdf_delete_all_title)) },
                                text = { Text(stringResource(R.string.pdf_delete_all_message)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        strokes.removeAll { it.page == currentPage }
                                        showDeleteAllDialog = false
                                    }) {
                                        Text(stringResource(R.string.action_delete))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteAllDialog = false }) {
                                        Text(stringResource(R.string.action_cancel))
                                    }
                                }
                            )
                        }

                        // Second row: Zoom & Eraser
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HintIconButton(
                                onClick = {
                                    val cur = pageScales[currentPage] ?: 1f
                                    if (cur > 0.5f) {
                                        pageScales[currentPage] = (cur - 0.5f).coerceAtLeast(0.5f)
                                        pageOffsetsX[currentPage] = 0f
                                        pageOffsetsY[currentPage] = 0f
                                    }
                                },
                                hint = stringResource(R.string.pdf_zoom_out),
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ZoomOut,
                                    contentDescription = stringResource(R.string.pdf_zoom_out),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            val displayScale = transientScaleMap[currentPage] ?: (pageScales[currentPage] ?: 1f)
                            Text(
                                text = "${((displayScale * 100).toInt())}%",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(45.dp),
                                textAlign = TextAlign.Center
                            )
                            HintIconButton(
                                onClick = {
                                    val cur = pageScales[currentPage] ?: 1f
                                    if (cur < 3f) {
                                        pageScales[currentPage] = (cur + 0.5f).coerceAtMost(3f)
                                    }
                                },
                                hint = stringResource(R.string.pdf_zoom_in),
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ZoomIn,
                                    contentDescription = stringResource(R.string.pdf_zoom_in),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            HintIconButton(
                                onClick = {
                                    eraseMode = !eraseMode
                                    if (eraseMode) {
                                        if (brushType == BrushType.Marker) brushType = BrushType.Pen
                                    }
                                },
                                hint = stringResource(R.string.pdf_hint_eraser),
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.pdf_eraser),
                                    tint = if (eraseMode) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = if (eraseMode) stringResource(R.string.pdf_brush_type_eraser)
                                       else if (annotateMode && brushType == BrushType.Marker) stringResource(R.string.pdf_brush_type_highlight)
                                       else if (annotateMode && brushType == BrushType.Special) stringResource(R.string.pdf_brush_type_special)
                                       else if (annotateMode) stringResource(R.string.pdf_brush_type_draw)
                                       else stringResource(R.string.pdf_brush_type_view),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    }

                    // Toolbar Toggle Button - floats over content
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    ) {
                        IconButton(
                            onClick = {
                                showToolbar = !showToolbar
                                prefsManager.setPdfToolbarVisible(showToolbar)
                            },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(32.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (showToolbar) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showToolbar) stringResource(R.string.pdf_hide_toolbar) else stringResource(R.string.pdf_show_toolbar),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Hover / Long-press hint anzeigen
                    hoveredHint?.let { hintText ->
                        Text(
                            text = hintText,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        )
                    }

                    // Sharpening slider: only show when sharpen mode is active
                    if (sharpenEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.pdf_sharpness_label),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(70.dp)
                            )
                            Slider(
                                value = sharpenIntensity,
                                onValueChange = { sharpenIntensity = it },
                                valueRange = 0.5f..4.0f,
                                steps = 6,
                                modifier = Modifier.weight(1f)
                            )

                            // Checkbox to control whether sharpening should be applied
                            // automatically to the current page when switching pages.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = sharpenApplyToCurrentPage,
                                    onCheckedChange = { sharpenApplyToCurrentPage = it }
                                )
                                Text(
                                    stringResource(R.string.pdf_apply_to_current_page),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(start = 4.dp).widthIn(max = 140.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                        Text(
                            stringResource(R.string.pdf_sharpen_label, sharpenIntensity.toInt()),
                            style = MaterialTheme.typography.bodySmall
                        )
                        }
                    }

                    // Color and stroke width controls: only show when draw mode is active
                    if (annotateMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        val colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Magenta)
                        colors.forEach { c ->
                            val isSelected = selectedColor == c
                            val borderColor = if (c.luminance() > 0.6f) Color.Black else Color.White
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(32.dp)
                                    .border(width = if (isSelected) 2.dp else 0.dp, color = if (isSelected) borderColor else Color.Transparent, shape = CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent, CircleShape)
                                    .clickable {
                                        selectedColor = c
                                        eraseMode = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(24.dp).background(c, CircleShape))
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        // Brush type buttons (Pen, Marker, Special)
                        HintIconButton(
                            onClick = {
                                brushType = BrushType.Pen
                                annotateMode = true
                                eraseMode = false
                            },
                            hint = stringResource(R.string.pdf_hint_pen),
                            onHintChange = { hoveredHint = it },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Create,
                                contentDescription = stringResource(R.string.pdf_pen),
                                tint = if (brushType == BrushType.Pen) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        HintIconButton(
                            onClick = {
                                brushType = BrushType.Marker
                                annotateMode = true
                                eraseMode = false
                            },
                            hint = stringResource(R.string.pdf_hint_marker),
                            onHintChange = { hoveredHint = it },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Highlight,
                                contentDescription = stringResource(R.string.pdf_marker),
                                tint = if (brushType == BrushType.Marker) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        HintIconButton(
                            onClick = {
                                brushType = BrushType.Special
                                annotateMode = true
                                eraseMode = false
                            },
                            hint = stringResource(R.string.pdf_hint_special),
                            onHintChange = { hoveredHint = it },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = stringResource(R.string.pdf_special_brush),
                                tint = if (brushType == BrushType.Special) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // Eraser button here near the colors
                        HintIconButton(
                            onClick = {
                                eraseMode = !eraseMode
                                if (eraseMode) {
                                    // Wenn Draw-Tools nicht offen, beim Aktivieren des Radierers öffnen
                                    if (!annotateMode) annotateMode = true
                                    if (brushType == BrushType.Marker) brushType = BrushType.Pen
                                }
                            },
                            hint = stringResource(R.string.pdf_hint_eraser),
                            onHintChange = { hoveredHint = it },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.pdf_eraser),
                                tint = if (eraseMode) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (eraseMode) {
                            Text(stringResource(R.string.pdf_eraser_label), style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = eraseRadius,
                                onValueChange = { eraseRadius = it },
                                valueRange = 20f..100f,
                                modifier = Modifier.width(120.dp).padding(start = 8.dp)
                            )
                        } else {
                            Text(stringResource(R.string.pdf_width), style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                                Slider(
                                    value = strokeWidth,
                                    onValueChange = { strokeWidth = it },
                                    valueRange = 1f..30f,
                                    modifier = Modifier.width(120.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val visualWidthPx = if (brushType == BrushType.Marker) strokeWidth * 4f else strokeWidth * 1.8f
                                val previewSize = with(LocalDensity.current) { visualWidthPx.coerceIn(8f, 64f).toDp() }
                                Box(
                                    modifier = Modifier
                                        .size(previewSize)
                                        .background(selectedColor, CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape)
                                )
                            }
                        }
                        }
                    }

                    // Main content area: uses weight to fill remaining space, preventing overlap with toolbars.
                    Box(modifier = Modifier.weight(1f)) {
                        val curScale = pageScales[currentPage] ?: 1f
                        val isZoomed = kotlin.math.abs(curScale - 1f) > 0.01f
                        if (isZoomed) {
                            // Show only the current page when zoomed — prevents other pages from showing partially.
                            val pageIndex = currentPage
                            val pageBitmapState = getPageBitmapState(pageIndex)
                            val bitmap = remember(pageIndex, pageBitmapState.value) {
                                pageBitmapState.value ?: bitmapCache.get(pageIndex)?.also {
                                    pageBitmapState.value = it
                                }
                            }
                            val pageStrokes = remember(strokes.size, pageIndex) { strokes.filter { it.page == pageIndex } }
                            
                            if (bitmap != null) {
                                PdfPageWithAnnotations(
                                    modifier = Modifier.fillMaxSize(),
                                    bitmap = bitmap,
                                    pageIndex = pageIndex,
                                    currentPage = currentPage,
                                    strokes = pageStrokes,
                                    annotateMode = annotateMode,
                                    eraseMode = eraseMode,
                                    selectedColor = selectedColor,
                                    strokeWidth = strokeWidth,
                                    highlightMode = highlightMode,
                                    brushType = brushType,
                                    eraseRadius = eraseRadius,
                                    scale = pageScales[pageIndex] ?: 1f,
                                    offsetX = pageOffsetsX[pageIndex] ?: 0f,
                                    offsetY = pageOffsetsY[pageIndex] ?: 0f,
                                        isPageHighlighted = pageHighlights.contains(pageIndex),
                                        invertColors = invertColors,
                                    sharpenEnabled = if (sharpenApplyToCurrentPage) pageIndex == currentPage else false,
                                    sharpenIntensity = sharpenIntensity,
                                    onStrokeAdd = { stroke -> strokes.add(stroke) },
                                    onStrokeUpdate = { oldStroke, newStroke ->
                                        val idx = strokes.indexOf(oldStroke)
                                        if (idx >= 0) strokes[idx] = newStroke
                                    },
                                    onStrokesErase = { erasedStrokes -> strokes.removeAll(erasedStrokes) },
                                    onScaleChange = { newScale, centroid, overlaySize ->
                                        val oldScale = pageScales[pageIndex] ?: 1f
                                        val oldOffsetX = pageOffsetsX[pageIndex] ?: 0f
                                        val oldOffsetY = pageOffsetsY[pageIndex] ?: 0f
                                        val contentX = (centroid.x - oldOffsetX) / oldScale
                                        val contentY = (centroid.y - oldOffsetY) / oldScale
                                        var newOffsetX = centroid.x - contentX * newScale
                                        var newOffsetY = centroid.y - contentY * newScale
                                        val maxOffsetX = 0f
                                        val minOffsetX = overlaySize.width * (1f - newScale)
                                        val maxOffsetY = 0f
                                        val minOffsetY = overlaySize.height * (1f - newScale)
                                        newOffsetX = newOffsetX.coerceIn(minOffsetX, maxOffsetX)
                                        newOffsetY = newOffsetY.coerceIn(minOffsetY, maxOffsetY)
                                        pageScales[pageIndex] = newScale
                                        pageOffsetsX[pageIndex] = newOffsetX
                                        pageOffsetsY[pageIndex] = newOffsetY
                                    },
                                    onOffsetChange = { dx, dy, overlaySize ->
                                        val currentScale = pageScales[pageIndex] ?: 1f
                                        val currentOffsetX = pageOffsetsX[pageIndex] ?: 0f
                                        val currentOffsetY = pageOffsetsY[pageIndex] ?: 0f
                                        var newOffsetX = currentOffsetX + dx
                                        var newOffsetY = currentOffsetY + dy
                                        val maxOffsetX = 0f
                                        val minOffsetX = overlaySize.width * (1f - currentScale)
                                        val maxOffsetY = 0f
                                        val minOffsetY = overlaySize.height * (1f - currentScale)
                                        newOffsetX = newOffsetX.coerceIn(minOffsetX, maxOffsetX)
                                        newOffsetY = newOffsetY.coerceIn(minOffsetY, maxOffsetY)
                                        pageOffsetsX[pageIndex] = newOffsetX
                                        pageOffsetsY[pageIndex] = newOffsetY
                                    },
                                    onScaleAndOffsetChange = { newScale, newOffsetX, newOffsetY ->
                                        pageScales[pageIndex] = newScale
                                        pageOffsetsX[pageIndex] = newOffsetX
                                        pageOffsetsY[pageIndex] = newOffsetY
                                    },
                                    onTransientScaleChange = { s -> transientScaleMap[pageIndex] = s },
                                    onSave = { AnnotationsRepository.save(context, pdfPath, strokes.toList()) }
                                )
                            } else {
                                val aspectRatio = pageAspectRatios[pageIndex] ?: (1f / 1.41f) // A4 fallback
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .aspectRatio(aspectRatio)
                                                                        .padding(8.dp)
                                                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                                                        .align(Alignment.Center)
                                                                ) {
                                                                    // No progress indicator to prevent flickering
                                                                }
                                LaunchedEffect(pageIndex) {
                                    // Only run if pdfRenderer is available and this page is not already rendering
                                    if (pdfRenderer == null || renderingPages.contains(pageIndex)) return@LaunchedEffect
                                    try { renderPageToCache(pageIndex, screenWidthPx) } catch (_: Exception) {}
                                }
                            }
                        } else {
                            // default: full list
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(), // Weight is now on the parent Box
                                userScrollEnabled = !(annotateMode || eraseMode),
                                flingBehavior = flingBehavior,
                                // Ensure each page takes exactly one screen height for reliable snapping
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                items(
                                    count = pageCount,
                                    key = { it },
                                    contentType = { "pdf_page" }
                                ) { pageIndex ->
                                    // Use individual page state to avoid global recomposition
                                    val pageBitmapState = getPageBitmapState(pageIndex)
                                    val pageBitmap by pageBitmapState

                                    // Get bitmap from state first, then from cache if state is null
                                    val bitmap = remember(pageIndex, pageBitmap) {
                                        pageBitmap ?: bitmapCache.get(pageIndex)?.also {
                                            // If we found it in cache but not in state, update state to prevent future lookups
                                            pageBitmapState.value = it
                                        }
                                    }

                                    val pageScale by remember(pageIndex) {
                                        derivedStateOf { pageScales[pageIndex] ?: 1f }
                                    }
                                    val pageOffsetX by remember(pageIndex) {
                                        derivedStateOf { pageOffsetsX[pageIndex] ?: 0f }
                                    }
                                    val pageOffsetY by remember(pageIndex) {
                                        derivedStateOf { pageOffsetsY[pageIndex] ?: 0f }
                                    }
                                    val aspectRatio by remember(pageIndex) {
                                        derivedStateOf { pageAspectRatios[pageIndex] ?: (1f / 1.41f) }
                                    }
                                    val isHighlighted by remember(pageIndex) {
                                        derivedStateOf { pageHighlights.contains(pageIndex) }
                                    }
                                    val isRendering by remember(pageIndex) {
                                        derivedStateOf { renderingPages.contains(pageIndex) }
                                    }
                                    val pageStrokes = remember(strokes.size, pageIndex) {
                                        strokes.filter { it.page == pageIndex }
                                    }

                                    if (bitmap != null) {
                                        // Each page should fill parent height for reliable snapping
                                        Box(
                                            modifier = Modifier
                                                .fillParentMaxHeight()
                                                .fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            PdfPageWithAnnotations(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()),
                                                bitmap = bitmap,
                                            pageIndex = pageIndex,
                                            currentPage = currentPage,
                                            strokes = pageStrokes,
                                            annotateMode = annotateMode,
                                            eraseMode = eraseMode,
                                            selectedColor = selectedColor,
                                            strokeWidth = strokeWidth,
                                            highlightMode = highlightMode,
                                            eraseRadius = eraseRadius,
                                            brushType = brushType,
                                            scale = pageScale,
                                            offsetX = pageOffsetX,
                                            offsetY = pageOffsetY,
                                            isPageHighlighted = isHighlighted,
                                            invertColors = invertColors,
                                            sharpenEnabled = if (sharpenApplyToCurrentPage) pageIndex == currentPage else false,
                                            sharpenIntensity = sharpenIntensity,
                                            onStrokeAdd = { strokes.add(it) },
                                            onStrokeUpdate = { old, new ->
                                                val idx = strokes.indexOf(old)
                                                if (idx >= 0) strokes[idx] = new
                                            },
                                            onStrokesErase = { strokes.removeAll(it) },
                                            onScaleChange = { newScale, centroid, overlaySize ->
                                                val oldScale = pageScales[pageIndex] ?: 1f
                                                val oldOffsetX = pageOffsetsX[pageIndex] ?: 0f
                                                val oldOffsetY = pageOffsetsY[pageIndex] ?: 0f
                                                val contentX = (centroid.x - oldOffsetX) / oldScale
                                                val contentY = (centroid.y - oldOffsetY) / oldScale
                                                var newOffsetX = centroid.x - contentX * newScale
                                                var newOffsetY = centroid.y - contentY * newScale
                                                val maxOffsetX = 0f
                                                val minOffsetX = overlaySize.width * (1f - newScale)
                                                val maxOffsetY = 0f
                                                val minOffsetY = overlaySize.height * (1f - newScale)
                                                newOffsetX = newOffsetX.coerceIn(minOffsetX, maxOffsetX)
                                                newOffsetY = newOffsetY.coerceIn(minOffsetY, maxOffsetY)
                                                pageScales[pageIndex] = newScale
                                                pageOffsetsX[pageIndex] = newOffsetX
                                                pageOffsetsY[pageIndex] = newOffsetY
                                            },
                                            onOffsetChange = { dx, dy, overlaySize ->
                                                val currentScale = pageScales[pageIndex] ?: 1f
                                                val currentOffsetX = pageOffsetsX[pageIndex] ?: 0f
                                                val currentOffsetY = pageOffsetsY[pageIndex] ?: 0f
                                                var newOffsetX = currentOffsetX + dx
                                                var newOffsetY = currentOffsetY + dy
                                                val maxOffsetX = 0f
                                                val minOffsetX = overlaySize.width * (1f - currentScale)
                                                val maxOffsetY = 0f
                                                val minOffsetY = overlaySize.height * (1f - currentScale)
                                                newOffsetX = newOffsetX.coerceIn(minOffsetX, maxOffsetX)
                                                newOffsetY = newOffsetY.coerceIn(minOffsetY, maxOffsetY)
                                                pageOffsetsX[pageIndex] = newOffsetX
                                                pageOffsetsY[pageIndex] = newOffsetY
                                            },
                                            onScaleAndOffsetChange = { newScale, newOffsetX, newOffsetY ->
                                                pageScales[pageIndex] = newScale
                                                pageOffsetsX[pageIndex] = newOffsetX
                                                pageOffsetsY[pageIndex] = newOffsetY
                                            },
                                            onTransientScaleChange = { s -> transientScaleMap[pageIndex] = s },
                                            onSave = { AnnotationsRepository.save(context, pdfPath, strokes.toList()) }
                                        )
                                        }
                                    } else {
                                        // Each loading placeholder should also fill parent height
                                        Box(
                                            modifier = Modifier
                                                .fillParentMaxHeight()
                                                .fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(aspectRatio)
                                                    .padding(8.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            ) {
                                                // No progress indicator to prevent flickering
                                            }
                                        }
                                        LaunchedEffect(Unit) {
                                            if (pdfRenderer != null && !isRendering && pageBitmap == null) {
                                                backgroundScope.launch {
                                                    try {
                                                        renderPageToCache(pageIndex, screenWidthPx)
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                    // Draggable floating action buttons overlay using centralized FABOverlay
                    val datapadManager = LocalDataPadManager.current
                    val datapadEnabled by datapadManager.isEnabled.collectAsState()
                    
                    FABOverlay(
                        prefsManager = prefsManager,
                        fabs = PdfViewerFABs.create(
                            onZoomReset = {
                                pageScales[currentPage] = 1f
                                pageOffsetsX[currentPage] = 0f
                                pageOffsetsY[currentPage] = 0f
                                transientScaleMap[currentPage] = 1f
                            },
                            onMenuOpen = onShowFileList,
                            onDataPadOpen = { if (datapadEnabled) showDataPad = true },
                            onQuickAccessOpen = { showQuickAccess = true },
                            zoomResetVisible = kotlin.math.abs((pageScales[currentPage] ?: 1f) - 1f) > 0.01f,
                            datapadEnabled = datapadEnabled,
                            containerColorPrimary = MaterialTheme.colorScheme.primaryContainer,
                            containerColorTertiary = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
            }
        }

        // Shortcut-Dialog
        if (showShortcutDialog) {
            AlertDialog(
                onDismissRequest = { showShortcutDialog = false },
                title = { Text(stringResource(R.string.pdf_shortcut_dialog_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.pdf_shortcut_dialog_message, currentPage + 1))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = shortcutName,
                            onValueChange = { shortcutName = it },
                            label = { Text(stringResource(R.string.pdf_shortcut_name_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (shortcutName.isNotBlank()) {
                            shortcutManager.createShortcut(
                                name = shortcutName,
                                filePath = pdfPath,
                                pageNumber = currentPage,
                                isHighlighted = pageHighlights.contains(currentPage)
                            )
                            showShortcutDialog = false
                        }
                    }) {
                        Text(stringResource(R.string.action_create))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showShortcutDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
        // Table of contents / chapters dialog with hierarchical outline display
        if (showTocDialog) {
            // LazyListState for TOC, starts at current page
            val tocListState = rememberLazyListState()
            var tocSearchQuery by remember { mutableStateOf("") }
            // Build a unified list of (title, page, level) for displaying in the TOC
            val tocDisplayList = remember(outlineItems, chapters, tocSearchQuery) {
                val raw = if (outlineItems.isNotEmpty()) {
                    outlineItems.map { Triple(it.title, it.pageNumber, it.level) }
                } else {
                    chapters.map { Triple(it.first, it.second, 0) }
                }
                if (tocSearchQuery.isBlank()) raw
                else raw.filter { it.first.contains(tocSearchQuery, ignoreCase = true) }
            }

            // Scroll to current page on open and center it
            LaunchedEffect(Unit) {
                if (chapters.isNotEmpty()) {
                    val targetIndex = currentPage.coerceIn(0, chapters.size - 1)
                    tocListState.scrollToItem(targetIndex)
                }
            }
            // Reset search when the dialog opens
            LaunchedEffect(showTocDialog) {
                if (showTocDialog) tocSearchQuery = ""
            }
            // When the search term changes, optionally scroll to the start of the filtered list
            LaunchedEffect(tocSearchQuery) {
                if (tocDisplayList.isNotEmpty()) {
                    tocListState.scrollToItem(0)
                }
            }

            AlertDialog(
                onDismissRequest = { showTocDialog = false },
                title = { Text(if (outlineItems.isNotEmpty()) stringResource(R.string.pdf_outline_title) else stringResource(R.string.pdf_toc_title)) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (chapters.isEmpty()) {
                            Text(stringResource(R.string.pdf_no_chapters))
                        }
                        // Search field
                        OutlinedTextField(
                            value = tocSearchQuery,
                            onValueChange = { tocSearchQuery = it },
                            label = { Text(stringResource(R.string.pdf_search_chapters)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.heightIn(max = 400.dp),
                            state = tocListState
                        ) {
                            items(tocDisplayList) { itemTriple ->
                                val (titleText, pageIndex, level) = itemTriple
                                val isCurrentPage = pageIndex == currentPage
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showTocDialog = false
                                            backgroundScope.launch(Dispatchers.Main) {
                                                listState.scrollToItem(pageIndex)
                                            }
                                        }
                                        .background(
                                            if (isCurrentPage)
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                            else
                                                Color.Transparent
                                        )
                                        .padding(vertical = 8.dp)
                                ) {
                                    Spacer(modifier = Modifier.width((level * 16).dp + 16.dp))
                                            Column {
                                        val displayTitle = titleText.trim().takeIf { it.isNotBlank() } ?: (stringResource(R.string.pdf_page_prefix) + (pageIndex + 1))
                                        val safeTitle = displayTitle.replace(Regex("[\u0000-\u001F\u007F]+"), " ").trim()
                                        Text(
                                            text = safeTitle,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (level == 0) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                        Text(
                                            text = stringResource(R.string.tab_page_number, pageIndex + 1),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTocDialog = false }) { Text(stringResource(R.string.action_close)) }
                }
            )
        }

        // Text-Anzeige-Dialog
        if (showTextDialog) {
            AlertDialog(
                onDismissRequest = { showTextDialog = false },
                title = { Text(stringResource(R.string.pdf_text) + " (" + stringResource(R.string.tab_page_number, currentPage + 1) + ")") },
                text = {
                    SelectionContainer {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                            item {
                                Text(
                                    text = dialogPageText.ifEmpty { stringResource(R.string.pdf_no_text_on_page) },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(context.getString(R.string.pdf_clipboard_label), dialogPageText)
                        clipboard.setPrimaryClip(clip)
                    }) {
                        Text(stringResource(R.string.action_copy))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTextDialog = false }) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            )
        }
    }

    // DataPad Popup
    if (showDataPad && datapadEnabled) {
        DataPadPopup(onDismiss = { showDataPad = false })
    }

    // Quick Access Bottom Sheet
    if (showQuickAccess) {
        QuickAccessSheet(
            onDismiss = { showQuickAccess = false },
            currentDocumentPath = pdfPath,
            currentDocumentName = title,
            currentPageNumber = currentPage,
            onOpenDocument = onOpenLinkedDocument
        )
    }
}

// Memory-friendly CPU unsharp mask that preserves hue by operating on luminance
private fun sharpenBitmap(src: Bitmap, amount: Float): Bitmap {
    // Memory-friendly, tile-based separable unsharp mask.
    // Strategy:
    // 1) Downscale if the image is extremely large (quick safety).
    // 2) For moderate/large images, operate in horizontal tiles to limit peak memory.
    // 3) Use a separable kernel (3x3 or 5x5) for efficient Gaussian-like blur.
    // 4) On any OOM or unexpected error, return the original bitmap and log a warning.
    val amt = amount.coerceIn(0.5f, 4.0f)
    val w = src.width
    val h = src.height

    // Safety threshold for full-image processing in-memory. Tweakable.
    val maxPixels = 2_000_000
    if (w.toLong() * h.toLong() > maxPixels) {
        try {
            val scale = kotlin.math.sqrt(maxPixels.toDouble() / (w.toDouble() * h.toDouble()))
            val sw = (w * scale).coerceAtLeast(1.0).toInt()
            val sh = (h * scale).coerceAtLeast(1.0).toInt()
            val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
            try {
                val sharpenedSmall = sharpenBitmap(scaled, amount)
                val result = Bitmap.createScaledBitmap(sharpenedSmall, w, h, true)
                if (sharpenedSmall !== scaled) sharpenedSmall.recycle()
                scaled.recycle()
                return result
            } catch (e: OutOfMemoryError) {
                android.util.Log.w("PdfViewer", "OOM while sharpening downscaled bitmap, skipping sharpen: ${e.message}")
                scaled.recycle()
                return src
            }
        } catch (e: Throwable) {
            android.util.Log.w("PdfViewer", "Skipping sharpen due to error: ${e.message}")
            return src
        }
    }

    try {
        // Choose kernel size based on requested strength for slightly more aggressive sharpening when amount is high.
        val useLargeKernel = amt > 2.0f
        val kernel: IntArray
        val pad: Int
        if (useLargeKernel) {
            kernel = intArrayOf(1, 4, 6, 4, 1) // 5x5 separable
            pad = 2
        } else {
            kernel = intArrayOf(1, 2, 1) // 3x3 separable
            pad = 1
        }

        // Target tile size in pixels (tweakable). Keeps per-tile memory modest.
        val tileMaxPixels = 600_000
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        // Helper to process a tile defined by [y0, y1) (exclusive y1).
        fun processTile(y0: Int, y1: Int) {
            val innerHeight = y1 - y0
            val readTop = (y0 - pad).coerceAtLeast(0)
            val readBottom = (y1 + pad).coerceAtMost(h)
            val readHeight = readBottom - readTop

            // Read the tightly-bounded pixel block (including padding rows for blur kernel)
            val readPixels = IntArray(w * readHeight)
            src.getPixels(readPixels, 0, w, 0, readTop, w, readHeight)

            // Convert to luminance (float) for the working block
            val lum = FloatArray(w * readHeight)
            for (row in 0 until readHeight) {
                val base = row * w
                for (x in 0 until w) {
                    val c = readPixels[base + x]
                    val r = (c shr 16 and 0xff).toFloat()
                    val g = (c shr 8 and 0xff).toFloat()
                    val b = (c and 0xff).toFloat()
                    lum[base + x] = 0.2126f * r + 0.7152f * g + 0.0722f * b
                }
            }

            // Separable blur: horizontal pass -> temp, then vertical pass -> blurred
            val temp = FloatArray(w * readHeight)

            // Horizontal
            for (row in 0 until readHeight) {
                val base = row * w
                for (x in 0 until w) {
                    var s = 0f
                    var wsum = 0
                    for (kx in -pad..pad) {
                        val xx = (x + kx).coerceIn(0, w - 1)
                        val kval = kernel[kx + pad]
                        s += kval * lum[base + xx]
                        wsum += kval
                    }
                    temp[base + x] = s / wsum
                }
            }

            // Vertical
            val blurred = FloatArray(w * readHeight)
            for (x in 0 until w) {
                for (row in 0 until readHeight) {
                    var s = 0f
                    var wsum = 0
                    for (ky in -pad..pad) {
                        val yy = (row + ky).coerceIn(0, readHeight - 1)
                        val kval = kernel[ky + pad]
                        s += kval * temp[yy * w + x]
                        wsum += kval
                    }
                    blurred[row * w + x] = s / wsum
                }
            }

            // Compose output pixels for the inner rows (excluding padding). Write them into the result bitmap.
            val outRows = innerHeight
            val outPixels = IntArray(w * outRows)
            for (row in 0 until outRows) {
                val readRow = row + (readTop - y0 + pad).coerceAtLeast(0) // map to readPixels index
                val readBase = (readRow) * w
                val outBase = row * w
                for (x in 0 until w) {
                    val c = readPixels[readBase + x]
                    val a = (c ushr 24) and 0xff
                    val r = (c shr 16 and 0xff).toFloat()
                    val g = (c shr 8 and 0xff).toFloat()
                    val b = (c and 0xff).toFloat()
                    val oLum = lum[readBase + x]
                    val bLum = blurred[readBase + x]
                    val newLum = (oLum + amt * (oLum - bLum)).coerceIn(0f, 255f)
                    val ratio = if (oLum > 0.001f) (newLum / oLum) else 1f
                    val nr = (r * ratio).coerceIn(0f, 255f).toInt()
                    val ng = (g * ratio).coerceIn(0f, 255f).toInt()
                    val nb = (b * ratio).coerceIn(0f, 255f).toInt()
                    outPixels[outBase + x] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
                }
            }

            // Write the processed stripe back into the result bitmap
            result.setPixels(outPixels, 0, w, 0, y0, w, outRows)
        }

        // Decide tiling layout
        if (w * h <= tileMaxPixels) {
            // Small enough to do in a single tile
            processTile(0, h)
        } else {
            val tileHeight = (tileMaxPixels / w).coerceAtLeast(64)
            var y = 0
            while (y < h) {
                val y1 = (y + tileHeight).coerceAtMost(h)
                try {
                    processTile(y, y1)
                } catch (e: OutOfMemoryError) {
                    android.util.Log.w("PdfViewer", "OOM while sharpening tile [$y,$y1), skipping sharpen: ${e.message}")
                    return src
                }
                y = y1
            }
        }

        return result
    } catch (e: OutOfMemoryError) {
        android.util.Log.w("PdfViewer", "OOM while sharpening bitmap, skipping sharpen: ${e.message}")
        return src
    }
}

@Composable
private fun HintIconButton(
    onClick: () -> Unit,
    hint: String,
    onHintChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var showLocalTooltip by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .pointerInput(hint) {
                val wasLongPressRef = booleanArrayOf(false)
                detectTapGestures(
                    onPress = {
                        wasLongPressRef[0] = false
                        onHintChange(hint)
                        // Ensure we don't show a local tooltip on a single tap release
                        showLocalTooltip = false
                        try {
                            awaitRelease()
                        } finally {
                            if (!wasLongPressRef[0]) onHintChange(null)
                            showLocalTooltip = false
                        }
                    },
                    onLongPress = {
                        wasLongPressRef[0] = true
                        onHintChange(hint)
                        // Show bubble tooltip locally
                        showLocalTooltip = true
                        onLongClick?.invoke()
                        coroutineScope.launch(Dispatchers.Main) {
                            delay(1500L)
                            onHintChange(null)
                            showLocalTooltip = false
                        }
                    }
                )
            }
    ) {
        IconButton(onClick = onClick) {
            content()
        }

        // Local tooltip bubble for long-press: appears above the icon
        if (showLocalTooltip) {
            Box(
                modifier = Modifier
                    .offset(y = (-40).dp)
                    .align(Alignment.TopCenter)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PdfPageWithAnnotations(
    modifier: Modifier = Modifier,
    bitmap: Bitmap,
    pageIndex: Int,
    currentPage: Int,
    strokes: List<AnnotationStroke>,
    annotateMode: Boolean,
    eraseMode: Boolean,
    selectedColor: Color,
    strokeWidth: Float,
    highlightMode: Boolean,
    brushType: BrushType,
    eraseRadius: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    isPageHighlighted: Boolean,
    invertColors: Boolean,
    sharpenEnabled: Boolean,
    sharpenIntensity: Float,
    onStrokeAdd: (AnnotationStroke) -> Unit,
    onStrokeUpdate: (AnnotationStroke, AnnotationStroke) -> Unit,
    onStrokesErase: (List<AnnotationStroke>) -> Unit,
    onScaleChange: (Float, Offset, IntSize) -> Unit,
    onOffsetChange: (Float, Float, IntSize) -> Unit,
    onScaleAndOffsetChange: (Float, Float, Float) -> Unit,
    onTransientScaleChange: ((Float) -> Unit)? = null,
    onSave: () -> Unit
) {
    var overlaySize by remember { mutableStateOf(IntSize(0, 0)) }
    var currentStroke by remember(pageIndex) { mutableStateOf<AnnotationStroke?>(null) }
    var eraserPosition by remember(pageIndex) { mutableStateOf<Offset?>(null) }
    var brushPosition by remember(pageIndex) { mutableStateOf<Offset?>(null) }
    val isCurrentPage = pageIndex == currentPage

    // Transient zoom state for smooth visual feedback without recomposition
    var transientScale by remember { mutableStateOf(1f) }
    var transientOffsetX by remember { mutableStateOf(0f) }
    var transientOffsetY by remember { mutableStateOf(0f) }

    // Gesture commit debounce job: we avoid writing to parent state on every motion
    var commitGestureJob by remember { mutableStateOf<Job?>(null) }
    val gestureScope = rememberCoroutineScope()

    // Sync transient state with persistent state when not gesturing
    LaunchedEffect(scale, offsetX, offsetY) {
        transientScale = scale
        transientOffsetX = offsetX
        transientOffsetY = offsetY
    }

    // Use transient state for rendering to avoid recomposition lag
    val renderScale = transientScale
    val renderOffsetX = transientOffsetX
    val renderOffsetY = transientOffsetY

    // Calculate actual bitmap display bounds within the overlay (accounting for ContentScale.Fit)
    val bitmapDisplayBounds = remember(bitmap, overlaySize, renderScale) {
        if (overlaySize.width == 0 || overlaySize.height == 0) {
            return@remember IntSize(0, 0) to Offset(0f, 0f)
        }

        val containerWidth = overlaySize.width.toFloat()
        val containerHeight = overlaySize.height.toFloat()
        val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val containerAspect = containerWidth / containerHeight

        val (displayWidth, displayHeight, paddingX, paddingY) = if (bitmapAspect > containerAspect) {
            // Bitmap is wider - fit to width, pad top/bottom
            val w = containerWidth
            val h = containerWidth / bitmapAspect
            val px = 0f
            val py = (containerHeight - h) / 2f
            listOf(w, h, px, py)
        } else {
            // Bitmap is taller - fit to height, pad left/right
            val h = containerHeight
            val w = containerHeight * bitmapAspect
            val px = (containerWidth - w) / 2f
            val py = 0f
            listOf(w, h, px, py)
        }

        IntSize(displayWidth.toInt(), displayHeight.toInt()) to Offset(paddingX, paddingY)
    }

    val (bitmapDisplaySize, bitmapDisplayOffset) = bitmapDisplayBounds

    // Sharpened bitmap cache per page
    val sharpenedBitmapStates = remember { mutableStateMapOf<Int, Bitmap?>() }

    Box(
        modifier = modifier
            .padding(8.dp)
            .background(if (invertColors) Color.Black else Color.White)
            .clip(RectangleShape)
    ) {
        // Only handle color inversion here; sharpening is done by processing the bitmap
        val colorFilter = remember(invertColors) {
            if (invertColors) ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            ) else null
        }
        // PDF page as bitmap
        // Choose sharpened bitmap when enabled and available, otherwise use base bitmap
        val displayedBitmap = remember(bitmap, sharpenEnabled, sharpenIntensity) {
            bitmap
        }

        val effectiveBitmapState = remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
        // Kick off sharpening job when needed; LaunchedEffect will be cancelled/restarted automatically
        LaunchedEffect(bitmap, sharpenEnabled, sharpenIntensity) {
            sharpenedBitmapStates.remove(pageIndex)
            effectiveBitmapState.value = bitmap

            if (sharpenEnabled && bitmap != null) {
                try {
                    val sb = withContext(Dispatchers.Default) { sharpenBitmap(bitmap, sharpenIntensity) }
                    sharpenedBitmapStates[pageIndex] = sb
                    effectiveBitmapState.value = sb
                } catch (e: Exception) {
                    // Let cancellations propagate, only log other exceptions
                    if (e !is kotlinx.coroutines.CancellationException) e.printStackTrace()
                }
            } else {
                effectiveBitmapState.value = bitmap
            }
        }

        val toDisplayBitmap by remember(pageIndex) { derivedStateOf { sharpenedBitmapStates[pageIndex] ?: effectiveBitmapState.value } }

        Image(
            bitmap = remember(toDisplayBitmap ?: bitmap) { (toDisplayBitmap ?: bitmap).asImageBitmap() },
            contentDescription = stringResource(R.string.pdf_page_number, pageIndex + 1),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = renderScale,
                    scaleY = renderScale,
                    translationX = renderOffsetX,
                    translationY = renderOffsetY,
                    transformOrigin = TransformOrigin(0f, 0f)
                ),
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
            colorFilter = colorFilter
        )

        // Page highlight (when enabled) - always use the original color
        if (isPageHighlighted) {
            val highlightCol = Color.Yellow.copy(alpha = 0.2f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(highlightCol)
            )
        }

        // Annotations Canvas Overlay
        val touchSlop = with(LocalDensity.current) { 8.dp.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { overlaySize = it }
                // Zoom/Pan handling - detects multi-touch or panning when zoomed
                .pointerInput(pageIndex, isCurrentPage, annotateMode, eraseMode) {
                    if (!isCurrentPage) return@pointerInput
                    // Don't handle zoom/pan in drawing/erase mode
                    if (annotateMode || eraseMode) return@pointerInput

                    // Custom multi-touch detection: only handle pinch zoom, let single-finger pass through for scrolling
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.filter { it.pressed }
                            
                            // Only handle multi-touch gestures (pinch zoom)
                            if (pointers.size >= 2) {
                                // Early exit if overlay not initialized
                                if (overlaySize.width == 0 || overlaySize.height == 0) continue
                                
                                // Consume the event to prevent other handlers from processing it
                                pointers.forEach { it.consume() }
                                
                                val pos1 = pointers[0].position
                                val pos2 = pointers[1].position
                                val centroid = (pos1 + pos2) / 2f
                                var prevDist = hypot((pos1.x - pos2.x).toDouble(), (pos1.y - pos2.y).toDouble()).toFloat()
                                var prevCentroid = centroid
                                
                                // Track the multi-touch gesture
                                while (true) {
                                    val nextEvent = awaitPointerEvent()
                                    val active = nextEvent.changes.filter { it.pressed }
                                    if (active.size < 2) break
                                    
                                    active.forEach { it.consume() }
                                    
                                    val newPos1 = active[0].position
                                    val newPos2 = active[1].position
                                    val newDist = hypot((newPos1.x - newPos2.x).toDouble(), (newPos1.y - newPos2.y).toDouble()).toFloat()
                                    val newCentroid = (newPos1 + newPos2) / 2f
                                    
                                    val zoom = if (prevDist > 0f) newDist / prevDist else 1f
                                    val pan = newCentroid - prevCentroid

                                    val currentScale = transientScale
                                    // Only allow zoom out to 1.0 (100%), never below, but allow zoom-in up to 8x
                                    val newScale = (currentScale * zoom).coerceAtLeast(1.0f).coerceAtMost(8f)

                                    // If user is pinching out (zooming towards 1f) and we're very close to 1.0, snap to 1.0 immediately
                                    if (zoom < 1f && kotlin.math.abs(newScale - 1f) < 0.05f) {
                                        // snap to exact 100%
                                        transientScale = 1f
                                        transientOffsetX = 0f
                                        transientOffsetY = 0f

                                        // Notify parent/persistent state so scrolling is enabled immediately
                                        onTransientScaleChange?.invoke(1f)
                                        onScaleAndOffsetChange(1f, 0f, 0f)

                                        // Cancel any pending commit and stop tracking this multi-touch gesture
                                        commitGestureJob?.cancel()
                                        commitGestureJob = null
                                        break
                                    }

                                    // Keep the content point under the gesture centroid stable while scaling
                                    val contentX = (prevCentroid.x - transientOffsetX) / currentScale
                                    val contentY = (prevCentroid.y - transientOffsetY) / currentScale

                                    var newOffsetX = newCentroid.x - contentX * newScale + pan.x * 0.5f
                                    var newOffsetY = newCentroid.y - contentY * newScale + pan.y * 0.5f

                                    // Apply bounds immediately for visual feedback (soft clamp)
                                    val maxOffsetX = 0f
                                    val minOffsetX = overlaySize.width * (1f - newScale)
                                    val maxOffsetY = 0f
                                    val minOffsetY = overlaySize.height * (1f - newScale)

                                    newOffsetX = if (minOffsetX <= maxOffsetX) {
                                        newOffsetX.coerceIn(minOffsetX, maxOffsetX)
                                    } else {
                                        newOffsetX.coerceAtMost(maxOffsetX).coerceAtLeast(minOffsetX)
                                    }
                                    newOffsetY = if (minOffsetY <= maxOffsetY) {
                                        newOffsetY.coerceIn(minOffsetY, maxOffsetY)
                                    } else {
                                        newOffsetY.coerceAtMost(maxOffsetY).coerceAtLeast(minOffsetY)
                                    }

                                    // Update transient (visual) state immediately for smooth response
                                    transientScale = newScale
                                    transientOffsetX = newOffsetX
                                    transientOffsetY = newOffsetY
                                    onTransientScaleChange?.invoke(transientScale)
                                    
                                    prevDist = newDist
                                    prevCentroid = newCentroid
                                }

                                // Debounce committing the transform to parent state to avoid heavy recomposition/stutter
                                commitGestureJob?.cancel()
                                // Capture the transient values at the time of scheduling
                                val snapshotScale = transientScale
                                val snapshotOffsetX = transientOffsetX
                                val snapshotOffsetY = transientOffsetY
                                commitGestureJob = gestureScope.launch {
                                    // Wait for gesture to settle
                                    kotlinx.coroutines.delay(120L)

                                    // Recompute clamped final offsets (ensure in-bounds) using snapshotScale
                                    val finalMaxOffsetX = 0f
                                    val finalMinOffsetX = overlaySize.width * (1f - snapshotScale)
                                    val finalMaxOffsetY = 0f
                                    val finalMinOffsetY = overlaySize.height * (1f - snapshotScale)

                                    val targetOffsetX = if (finalMinOffsetX <= finalMaxOffsetX) {
                                        snapshotOffsetX.coerceIn(finalMinOffsetX, finalMaxOffsetX)
                                    } else {
                                        snapshotOffsetX.coerceAtMost(finalMaxOffsetX).coerceAtLeast(finalMinOffsetX)
                                    }
                                    val targetOffsetY = if (finalMinOffsetY <= finalMaxOffsetY) {
                                        snapshotOffsetY.coerceIn(finalMinOffsetY, finalMaxOffsetY)
                                    } else {
                                        snapshotOffsetY.coerceAtMost(finalMaxOffsetY).coerceAtLeast(finalMinOffsetY)
                                    }

                                    // Smoothly animate offsets to the final clamped values if needed
                                    if (kotlin.math.abs(transientOffsetX - targetOffsetX) > 0.5f || kotlin.math.abs(transientOffsetY - targetOffsetY) > 0.5f) {
                                        val animX = androidx.compose.animation.core.Animatable(transientOffsetX)
                                        val animY = androidx.compose.animation.core.Animatable(transientOffsetY)
                                        // Snap to current to avoid jumps
                                        animX.snapTo(transientOffsetX)
                                        animY.snapTo(transientOffsetY)
                                        // Animate to targets using a spring for natural feel
                                        try {
                                            val aJob = launch {
                                                animX.animateTo(targetOffsetX, animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 600f)) {
                                                    transientOffsetX = value
                                                }
                                            }
                                            val bJob = launch {
                                                animY.animateTo(targetOffsetY, animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 600f)) {
                                                    transientOffsetY = value
                                                }
                                            }
                                            aJob.join(); bJob.join()
                                        } catch (_: Exception) {
                                            transientOffsetX = targetOffsetX
                                            transientOffsetY = targetOffsetY
                                        }
                                    } else {
                                        transientOffsetX = targetOffsetX
                                        transientOffsetY = targetOffsetY
                                    }

                                    // If scale returned to 1f, also reset offsets to zero for a clean state
                                    val finalScale = if (kotlin.math.abs(snapshotScale - 1f) < 0.015f) 1f else snapshotScale
                                    if (finalScale == 1f) {
                                        transientOffsetX = 0f
                                        transientOffsetY = 0f
                                    }

                                    // Commit to parent (persistent) state once gesture settles
                                    onScaleAndOffsetChange(finalScale, transientOffsetX, transientOffsetY)
                                    commitGestureJob = null
                                }
                            }
                        }
                    }
                }
                // Custom pointer handling: Draw/Erase. Active when drawing or erase mode is enabled.
                .pointerInput(annotateMode, eraseMode, pageIndex, isCurrentPage, selectedColor, strokeWidth, brushType, eraseRadius) {
                    // This block handles drawing/erasing. Active when drawing/erase mode is on.
                    if (!isCurrentPage) return@pointerInput
                    if (!annotateMode && !eraseMode) return@pointerInput

                    detectDragGestures(
                        onDragStart = { offset ->
                            if (eraseMode) {
                                eraserPosition = offset
                                val toErase = strokes.filter { stroke ->
                                    stroke.points.any { p ->
                                        // Transform document coords to screen coords with bitmap bounds
                                        val px = p.first * renderScale + renderOffsetX + bitmapDisplayOffset.x
                                        val py = p.second * renderScale + renderOffsetY + bitmapDisplayOffset.y
                                        hypot(
                                            (px - offset.x).toDouble(),
                                            (py - offset.y).toDouble()
                                        ) < eraseRadius
                                    }
                                }
                                onStrokesErase(toErase)
                                if (toErase.isNotEmpty()) {
                                    // Use the provided onSave callback so saving is done by the parent with correct context/path
                                    onSave()
                                }
                                return@detectDragGestures
                            }

                            if (annotateMode) {
                                brushPosition = offset
                                // Transform screen coords to document coords accounting for bitmap bounds
                                val docX = (offset.x - bitmapDisplayOffset.x - renderOffsetX) / renderScale
                                val docY = (offset.y - bitmapDisplayOffset.y - renderOffsetY) / renderScale
                                val colorValue = when (brushType) {
                                    BrushType.Special -> selectedColor.copy(alpha = 0.85f).value.toLong()
                                    else -> selectedColor.value.toLong()
                                }
                                val initWidth = when (brushType) {
                                    BrushType.Marker -> strokeWidth
                                    BrushType.Special -> (strokeWidth * 1.5f).coerceAtMost(60f)
                                    else -> strokeWidth
                                }
                                val newStroke = AnnotationStroke(
                                    page = pageIndex,
                                    color = colorValue,
                                    strokeWidth = initWidth,
                                    points = listOf(Pair(docX, docY)),
                                    isHighlight = (brushType == BrushType.Marker)
                                )
                                currentStroke = newStroke
                                // DO NOT add to list while drawing - add only on onDragEnd
                            }
                        },
                        onDrag = { change, _ ->
                            if (eraseMode) {
                                val offset = change.position
                                eraserPosition = offset
                                val toErase = strokes.filter { stroke ->
                                    stroke.points.any { p ->
                                        // Transform document coords to screen coords with bitmap bounds
                                        val px = p.first * renderScale + renderOffsetX + bitmapDisplayOffset.x
                                        val py = p.second * renderScale + renderOffsetY + bitmapDisplayOffset.y
                                        hypot(
                                            (px - offset.x).toDouble(),
                                            (py - offset.y).toDouble()
                                        ) < eraseRadius
                                    }
                                }
                                onStrokesErase(toErase)
                                if (toErase.isNotEmpty()) {
                                    // Use the provided onSave callback so saving is done by the parent with correct context/path
                                    onSave()
                                }
                                return@detectDragGestures
                            }

                            if (annotateMode) {
                                change.consume()
                                val offset = change.position
                                brushPosition = offset
                                currentStroke?.let { stroke ->
                                    // Transform screen coords to document coords accounting for bitmap bounds
                                    val docX = (offset.x - bitmapDisplayOffset.x - renderOffsetX) / renderScale
                                    val docY = (offset.y - bitmapDisplayOffset.y - renderOffsetY) / renderScale
                                    val newPoints = stroke.points.toMutableList().apply {
                                        add(Pair(docX, docY))
                                    }
                                    val updatedStroke = stroke.copy(points = newPoints)
                                    // Update only currentStroke, not the list
                                    currentStroke = updatedStroke
                                }
                            }
                        },
                        onDragEnd = {
                            if (eraseMode) {
                                eraserPosition = null
                            }
                            if (annotateMode) {
                                brushPosition = null
                                // Now add to the list after drawing
                                currentStroke?.let { onStrokeAdd(it) }
                                currentStroke = null
                                onSave()
                            }
                        }
                    )
                }
        ) {
            // Draw all annotations for this page
            for (stroke in strokes) {
                val path = Path().apply {
                    stroke.points.forEachIndexed { i, p ->
                        // Transform document coords to screen coords with bitmap bounds
                        val x = p.first * renderScale + renderOffsetX + bitmapDisplayOffset.x
                        val y = p.second * renderScale + renderOffsetY + bitmapDisplayOffset.y
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                // Highlight-Modus: dicker Pinsel (5x breiter), halbtransparent
                val widthPx = if (stroke.isHighlight) {
                    maxOf(15f, stroke.strokeWidth * renderScale * 5f)
                } else {
                    maxOf(1f, stroke.strokeWidth * renderScale)
                }
                val strokeBaseColor = Color(stroke.color.toULong())
                // Compute alpha: highlights use fixed low alpha; special brush may have reduced alpha encoded in color
                val strokeAlpha = if (stroke.isHighlight) 0.4f else strokeBaseColor.alpha.coerceIn(0.1f, 1f)
                // Annotations/Highlights/Drawing should NOT be affected by page color inversion
                drawPath(
                    path = path,
                    color = strokeBaseColor,
                    style = Stroke(width = widthPx),
                    alpha = strokeAlpha
                )
            }

            // Draw the current stroke live while drawing
            currentStroke?.let { stroke ->
                if (stroke.points.isNotEmpty()) {
                    val path = Path().apply {
                        stroke.points.forEachIndexed { i, p ->
                            // Transform document coords to screen coords with bitmap bounds
                            val x = p.first * renderScale + renderOffsetX + bitmapDisplayOffset.x
                            val y = p.second * renderScale + renderOffsetY + bitmapDisplayOffset.y
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                    }
                    // Highlight-Modus: dicker Pinsel (5x breiter), halbtransparent
                    val widthPx = if (stroke.isHighlight) {
                        maxOf(15f, stroke.strokeWidth * renderScale * 5f)
                    } else {
                        maxOf(1f, stroke.strokeWidth * renderScale)
                    }
                    val strokeBaseColor = Color(stroke.color.toULong())
                    val strokeAlpha = if (stroke.isHighlight) 0.4f else strokeBaseColor.alpha.coerceIn(0.1f, 1f)
                    // Live stroke uses the original color as well
                    drawPath(
                        path = path,
                        color = strokeBaseColor,
                        style = Stroke(width = widthPx),
                        alpha = strokeAlpha
                    )
                }
            }

            // Draw the eraser cursor when erase mode is active
            if (eraseMode && eraserPosition != null && isCurrentPage) {
                // Eraser cursor should keep its original color regardless of inversion
                val eraserOuter = Color.Red.copy(alpha = 0.3f)
                val eraserInner = Color.Red.copy(alpha = 0.1f)
                drawCircle(
                    color = eraserOuter,
                    radius = eraseRadius,
                    center = eraserPosition!!,
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = eraserInner,
                    radius = eraseRadius,
                    center = eraserPosition!!
                )
            }

            // Draw the brush cursor when annotate mode is active
            if (annotateMode && !eraseMode && brushPosition != null && isCurrentPage) {
                val brushRadius = if (highlightMode) {
                    // Highlight: thicker brush
                    maxOf(7.5f, strokeWidth * renderScale * 2.5f)
                } else {
                    // Normal: regular brush
                    maxOf(2f, strokeWidth * renderScale / 2f)
                }

                // Outer ring in the selected color (original color, not inverted)
                val selOuter = selectedColor.copy(alpha = 0.5f)
                val selInner = selectedColor.copy(alpha = if (highlightMode) 0.3f else 0.2f)
                drawCircle(
                    color = selOuter,
                    radius = brushRadius,
                    center = brushPosition!!,
                    style = Stroke(width = 2f)
                )
                // Inner filled circle
                drawCircle(
                    color = selInner,
                    radius = brushRadius,
                    center = brushPosition!!
                )
            }
        }
    }
}
