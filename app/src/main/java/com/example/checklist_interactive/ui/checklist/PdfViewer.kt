package com.example.checklist_interactive.ui.checklist

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
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

import kotlinx.coroutines.delay
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import com.example.checklist_interactive.R
import com.example.checklist_interactive.data.shortcuts.PageHighlightManager
import com.example.checklist_interactive.data.shortcuts.ShortcutManager
import com.example.checklist_interactive.data.shortcuts.LastPageManager
import com.example.checklist_interactive.data.prefs.InvertColorPrefManager
import java.io.File
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import com.example.checklist_interactive.ui.quickaccess.QuickAccessSheet
import com.example.checklist_interactive.data.quicknotes.QuickNoteManager

/**
 * Eigener PDF-Viewer ohne externe Abhängigkeiten.
 * Verwendet Android's eingebauten PdfRenderer (verfügbar ab API 21).
 * Mit Annotation-Unterstützung (Zeichnen, Highlighten, Löschen).
 * Mit Zoom, Seiten-Highlights und Shortcuts.
 */
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

    // Manager für Shortcuts, Highlights und letzte Seite
    val shortcutManager = remember { ShortcutManager(context) }
    val highlightManager = remember { PageHighlightManager(context) }
    val lastPageManager = remember { LastPageManager(context) }
    val quickNoteManager = remember { QuickNoteManager(context) }

    // Lade zuletzt geöffnete Seite, falls initialPage nicht explizit gesetzt wurde (< 0 = nicht gesetzt)
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
    var highlightMode by remember { mutableStateOf(false) }
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
    var pageHighlights by remember { mutableStateOf<List<Int>>(emptyList()) }
    val invertColorPrefManager = remember { InvertColorPrefManager(context) }
    var invertColors by remember { mutableStateOf(false) }
    var showTocDialog by remember { mutableStateOf(false) }
    var chapters by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var showQuickAccess by remember { mutableStateOf(false) }
    var showToolbar by remember { mutableStateOf(true) }

    // Text-Extraktion
    val textExtractor = remember { PdfTextExtractor(context) }
    val pageTextBlocks = remember { mutableStateMapOf<Int, List<PdfTextBlock>>() }
    var textSelectionEnabled by remember { mutableStateOf(true) }
    var showTextDialog by remember { mutableStateOf(false) }
    var dialogPageText by remember { mutableStateOf("") }
    val textExtractionJobs = remember { mutableMapOf<Int, Job>() }

    // Cleanup textExtractor beim Verlassen
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

    // PDF laden: open once, but render pages lazily.
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    val pdfRenderMutex = remember { Mutex() }

    LaunchedEffect(pdfPath, isInternalFile) {
        // Load invert state from prefs
        invertColors = invertColorPrefManager.isInverted(pdfPath)
        isLoading = true
        errorMessage = null
        try {
            withContext(Dispatchers.IO) {
                val loadedPdfFile = if (isInternalFile) {
                    // Interne Datei direkt verwenden
                    File(pdfPath)
                } else {
                    // Asset in temporäre Datei kopieren
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

                // Pre-calculate all page aspect ratios to prevent layout jumping on scroll
                (0 until pageCount).forEach { index ->
                    val page = pdfRenderer!!.openPage(index)
                    pageAspectRatios[index] = page.width.toFloat() / page.height.toFloat()
                    page.close()
                }

                // Render only an initial subset of pages (effectiveInitialPage and nearby), rest are loaded on demand.
                val initialIndices = listOf(effectiveInitialPage - 1, effectiveInitialPage, effectiveInitialPage + 1).distinct().filter { it in 0 until pageCount }
                for (pageIndex in initialIndices) {
                    // Use a helper function to render pages to a cache-friendly size.
                    val page = pdfRenderer!!.openPage(pageIndex)
                        val renderWidth = screenWidthPx
                        val renderHeight = (renderWidth.toFloat() * page.height / page.width).toInt()
                        // Try RGB_565 (lower memory) first; fallback to ARGB_8888 when necessary.
                        var bitmap: Bitmap? = null
                        try {
                            bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.RGB_565)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        } catch (e: Throwable) {
                            bitmap?.let { if (!it.isRecycled) it.recycle() }
                            bitmap = try { Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888) } catch (_: Throwable) { null }
                            bitmap?.let { page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                        }
                        bitmap?.let { bmp ->
                            bitmapCache.put(pageIndex, bmp)
                            renderScaleMap[pageIndex] = 1f
                            cacheKeys.add(pageIndex)
                            withContext(Dispatchers.Main) {
                                getPageBitmapState(pageIndex).value = bmp
                            }
                        }
                        if (bitmap == null) {
                            withContext(Dispatchers.Main) {
                                errorMessage = context.getString(R.string.error_loading_pdf, "Unsupported pixel format for page ${pageIndex + 1}")
                            }
                        }
                        page.close()
                }

                // Nur temporäre Dateien löschen
                if (!isInternalFile) {
                    loadedPdfFile.delete()
                }
            }
            isLoading = false
            // Populate fallback chapters (page list) after pages are loaded
            chapters = (0 until pageCount).map { idx -> "Seite ${idx + 1}" to idx }
        } catch (e: Exception) {
            errorMessage = context.getString(R.string.error_loading_pdf, e.message ?: "")
            isLoading = false
        }
    }

    // Close PDF renderer and file descriptor when composable leaves scope
    DisposableEffect(pdfPath) {
        onDispose {
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
        }
    }

    // Helper: render a page to bitmap cache at a target width in pixels
    suspend fun renderPageToCache(pageIndex: Int, targetWidthPx: Int, renderedScale: Float = 1f) {
        // Prevent multiple concurrent renders of same page
        if (renderingPages.contains(pageIndex)) return
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
                            errorMessage = context.getString(R.string.error_loading_pdf, "Unsupported pixel format for page ${pageIndex + 1}")
                        }
                    }
                }
            }
        }
        } finally {
            renderingPages.remove(pageIndex)
        }
    }

    // Only request a render if we do not already have a bitmap, and not already rendering
    fun maybeRequestRender(pageIndex: Int, widthPx: Int) {
        if (pageIndex !in 0 until pageCount) return
        if (getPageBitmapState(pageIndex).value != null) return
        if (bitmapCache.get(pageIndex) != null) return
        if (renderingPages.contains(pageIndex)) return
        coroutineScope.launch {
            try {
                renderPageToCache(pageIndex, widthPx)
            } catch (_: Exception) {}
        }
    }

    // Annotationen laden (async auf Background-Thread)
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

    // Aktuelle Seite basierend auf Scroll-Position — render current and neighbors on demand
    LaunchedEffect(listState.firstVisibleItemIndex) {
        currentPage = listState.firstVisibleItemIndex
        onPageChange?.invoke(currentPage)
        val cur = currentPage
        val indices = listOf(cur - 1, cur, cur + 1).filter { it in 0 until pageCount }
        for (idx in indices) maybeRequestRender(idx, screenWidthPx)

        // Extrahiere Text für sichtbare Seiten
        pdfFile?.let { file ->
            for (idx in indices) {
                if (!pageTextBlocks.containsKey(idx)) {
                    // Abbrechen eines laufenden Jobs für diese Seite
                    textExtractionJobs[idx]?.cancel()

                    val job = coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val textBlocks = textExtractor.extractTextBlocks(file, idx)
                            withContext(Dispatchers.Main) {
                                pageTextBlocks[idx] = textBlocks
                                textExtractionJobs.remove(idx)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            textExtractionJobs.remove(idx)
                        }
                    }
                    textExtractionJobs[idx] = job
                }
            }
        }
    }

    // Scrolle zur Seite wenn initialPage oder das Dokument (pdfPath/pageCount) sich ändert.
    // Dies sorgt dafür, dass beim Re-Entrypunkt in dieselbe Datei die zuletzt geöffnete Seite geladen wird.
    LaunchedEffect(pdfPath, pageCount, initialPage) {
        if (pageCount > 0) {
            val target = if (initialPage >= 0) initialPage else lastPageManager.getLastPage(docIdForLastPage)
            val clamped = target.coerceIn(0, pageCount - 1)
            if (clamped != currentPage) {
                listState.scrollToItem(clamped)
                currentPage = clamped
            }
        }
    }

    // Speichere die aktuelle Seite bei jedem Seitenwechsel
    LaunchedEffect(currentPage) {
        if (pageCount > 0) {
            lastPageManager.saveLastPage(docIdForLastPage, currentPage)
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
        zoomRenderJob = coroutineScope.launch {
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
                title = { Text("$title (Page ${currentPage + 1}/$pageCount)", style = MaterialTheme.typography.titleSmall) },
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
                        Icon(Icons.Default.ArrowBack, contentDescription = context.getString(R.string.back), modifier = Modifier.size(18.dp))
                    }
                },
                actions = {
                    if (onToggleTheme != null) {
                        HintIconButton(onClick = onToggleTheme, hint = context.getString(R.string.toggle_dark_mode), onHintChange = { hoveredHint = it }) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = context.getString(R.string.toggle_dark_mode),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    HintIconButton(
                        onClick = {
                            invertColors = !invertColors
                            invertColorPrefManager.setInverted(pdfPath, invertColors)
                        },
                        hint = "Farben invertieren",
                        onHintChange = { hoveredHint = it },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.InvertColors, contentDescription = "Invert Colors", modifier = Modifier.size(18.dp))
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Zoom Reset FAB - nur anzeigen wenn gezoomt (oben, damit es die Position der anderen nicht verschiebt)
                val currentScale = pageScales[currentPage] ?: 1f
                if (currentScale != 1f) {
                    FloatingActionButton(
                        onClick = {
                            pageScales[currentPage] = 1f
                            pageOffsetsX[currentPage] = 0f
                            pageOffsetsY[currentPage] = 0f
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.CenterFocusWeak, contentDescription = "Zoom zurücksetzen")
                    }
                }

                // Quick Access FAB - immer sichtbar an fester Position
                FloatingActionButton(
                    onClick = { showQuickAccess = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = "Schnellzugriff")
                }

                // Menu FAB - immer an fester Position (nur anzeigen wenn onShowFileList gesetzt ist)
                if (onShowFileList != null) {
                    FloatingActionButton(
                        onClick = onShowFileList,
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = context.getString(R.string.file_list))
                    }
                }
            }
        }
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
                    // Kompakte Toolbar mit Scroll - ein-/ausklappbar
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
                        // Erste Reihe: Zeichnen & Annotationen
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
                                hint = "Zeichnen",
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Create,
                                    contentDescription = "Zeichnen",
                                    tint = if (annotateMode) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Inhaltsverzeichnis / Kapitel (TOC) - opens a dialog with chapters
                            HintIconButton(
                                onClick = { showTocDialog = true },
                                hint = "Inhaltsverzeichnis",
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ViewList, // visible chapter/list icon
                                    contentDescription = "Inhaltsverzeichnis",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            HintIconButton(
                                onClick = {
                                    highlightMode = !highlightMode
                                    if (highlightMode) {
                                        annotateMode = true
                                        eraseMode = false
                                    }
                                },
                                hint = "Markieren",
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Highlight,
                                    contentDescription = "Markieren",
                                    tint = if (highlightMode) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            HintIconButton(
                                onClick = {
                                    val isHighlighted = highlightManager.togglePageHighlight(pdfPath, currentPage)
                                    pageHighlights = highlightManager.getHighlightsForFile(pdfPath).map { it.pageNumber }
                                },
                                hint = "Seite highlighten",
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "Seite highlighten",
                                    tint = if (pageHighlights.contains(currentPage)) Color.Yellow else LocalContentColor.current,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            HintIconButton(
                                onClick = {
                                    shortcutName = "$title - Seite ${currentPage + 1}"
                                    showShortcutDialog = true
                                },
                                hint = "Shortcut erstellen",
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.BookmarkAdd,
                                    contentDescription = "Shortcut",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Link zu Schnellnotiz
                            HintIconButton(
                                onClick = {
                                    quickNoteManager.addLinkedDocument(
                                        filePath = pdfPath,
                                        fileName = title,
                                        pageNumber = currentPage
                                    )
                                },
                                hint = "Zu Schnellnotiz verlinken",
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = "Link",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Text anzeigen Button
                            HintIconButton(
                                onClick = {
                                    pdfFile?.let { file ->
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val pageText = textExtractor.extractPageText(file, currentPage)
                                            withContext(Dispatchers.Main) {
                                                dialogPageText = pageText
                                                showTextDialog = true
                                            }
                                        }
                                    }
                                },
                                hint = "Text anzeigen",
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Text anzeigen",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            HintIconButton(
                                onClick = {
                                    val idx = strokes.indexOfLast { it.page == currentPage }
                                    if (idx >= 0) strokes.removeAt(idx)
                                },
                                hint = "Undo",
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Undo,
                                    contentDescription = "Undo",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            HintIconButton(
                                onClick = { showDeleteAllDialog = true },
                                hint = "Alle Anmerkungen löschen",
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            HintIconButton(
                                onClick = {
                                    AnnotationsRepository.save(context, pdfPath, strokes.toList())
                                },
                                hint = "Speichern",
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = "Save",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Delete All Dialog
                        if (showDeleteAllDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteAllDialog = false },
                                title = { Text("Alle Zeichnungen löschen?") },
                                text = { Text("Möchten Sie wirklich alle Zeichnungen/Annotationen auf dieser Seite unwiderruflich löschen?") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        strokes.removeAll { it.page == currentPage }
                                        showDeleteAllDialog = false
                                    }) {
                                        Text("Löschen")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteAllDialog = false }) {
                                        Text("Abbrechen")
                                    }
                                }
                            )
                        }

                        // Zweite Reihe: Zoom & Radierer
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
                                hint = "Verkleinern",
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ZoomOut,
                                    contentDescription = "Zoom Out",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "${(((pageScales[currentPage] ?: 1f) * 100).toInt())}%",
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
                                hint = "Vergrößern",
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ZoomIn,
                                    contentDescription = "Zoom In",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            HintIconButton(
                                onClick = {
                                    eraseMode = !eraseMode
                                    if (eraseMode) {
                                        annotateMode = false
                                        highlightMode = false
                                    }
                                },
                                hint = "Radierer",
                                onHintChange = { hoveredHint = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Radierer",
                                    tint = if (eraseMode) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = if (eraseMode) "Radierer"
                                       else if (annotateMode && highlightMode) "Highlight"
                                       else if (annotateMode) "Zeichnen"
                                       else "Ansicht",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    }

                    // Toolbar Toggle Button - schwebt über dem Inhalt
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    ) {
                        IconButton(
                            onClick = { showToolbar = !showToolbar },
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
                                contentDescription = if (showToolbar) "Werkzeugleiste ausblenden" else "Werkzeugleiste einblenden",
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

                    // Farb- und Strichbreiten-Kontrolle: nur anzeigen wenn Zeichnen aktiv ist
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
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .padding(4.dp)
                                    .background(c, CircleShape)
                                    .clickable {
                                        selectedColor = c
                                        eraseMode = false
                                    }
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        // Radierer-Button hier bei den Farben
                        HintIconButton(
                            onClick = {
                                eraseMode = !eraseMode
                                if (eraseMode) {
                                    highlightMode = false
                                }
                            },
                            hint = "Radierer",
                            onHintChange = { hoveredHint = it },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Radierer",
                                tint = if (eraseMode) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (eraseMode) {
                            Text("Radierer:", style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = eraseRadius,
                                onValueChange = { eraseRadius = it },
                                valueRange = 20f..100f,
                                modifier = Modifier.width(120.dp).padding(start = 8.dp)
                            )
                        } else {
                            Text("Breite:", style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = strokeWidth,
                                onValueChange = { strokeWidth = it },
                                valueRange = 1f..30f,
                                modifier = Modifier.width(120.dp).padding(start = 8.dp)
                            )
                        }
                        }
                    }

                    // Main content area: uses weight to fill remaining space, preventing overlap with toolbars.
                    Box(modifier = Modifier.weight(1f)) {
                        val curScale = pageScales[currentPage] ?: 1f
                        if (curScale != 1f) {
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
                                    eraseRadius = eraseRadius,
                                    scale = pageScales[pageIndex] ?: 1f,
                                    offsetX = pageOffsetsX[pageIndex] ?: 0f,
                                    offsetY = pageOffsetsY[pageIndex] ?: 0f,
                                        isPageHighlighted = pageHighlights.contains(pageIndex),
                                        invertColors = invertColors,
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
                                    if (pdfRenderer == null || renderingPages.contains(pageIndex) || bitmap != null) return@LaunchedEffect
                                    try { renderPageToCache(pageIndex, screenWidthPx) } catch (_: Exception) {}
                                }
                            }
                        } else {
                            // default: full list
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(), // Weight is now on the parent Box
                                userScrollEnabled = !(annotateMode || eraseMode),
                                flingBehavior = flingBehavior
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
                                        PdfPageWithAnnotations(
                                            modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()),
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
                                            scale = pageScale,
                                            offsetX = pageOffsetX,
                                            offsetY = pageOffsetY,
                                            isPageHighlighted = isHighlighted,
                                            invertColors = invertColors,
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
                                            onSave = { AnnotationsRepository.save(context, pdfPath, strokes.toList()) }
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(aspectRatio)
                                                .padding(8.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            // No progress indicator to prevent flickering
                                        }
                                        LaunchedEffect(Unit) {
                                            if (pdfRenderer != null && !isRendering && pageBitmap == null) {
                                                coroutineScope.launch {
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
            }
        }

        // Shortcut-Dialog
        if (showShortcutDialog) {
            AlertDialog(
                onDismissRequest = { showShortcutDialog = false },
                title = { Text("Shortcut erstellen") },
                text = {
                    Column {
                        Text("Erstelle einen Shortcut zu Seite ${currentPage + 1}")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = shortcutName,
                            onValueChange = { shortcutName = it },
                            label = { Text("Shortcut-Name") },
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
                        Text("Erstellen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showShortcutDialog = false }) {
                        Text("Abbrechen")
                    }
                }
            )
        }
        // Inhaltsverzeichnis / Kapitel-Dialog
        if (showTocDialog) {
            // LazyListState für TOC, startet bei aktueller Seite
            val tocListState = rememberLazyListState()

            // Scrolle beim Öffnen zur aktuellen Seite und zentriere sie
            LaunchedEffect(Unit) {
                if (chapters.isNotEmpty()) {
                    val targetIndex = currentPage.coerceIn(0, chapters.size - 1)
                    tocListState.scrollToItem(targetIndex)
                }
            }

            AlertDialog(
                onDismissRequest = { showTocDialog = false },
                title = { Text("Inhaltsverzeichnis") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (chapters.isEmpty()) {
                            Text("Keine Kapitel gefunden. Zeige Seitenliste als Fallback.")
                        }
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 400.dp),
                            state = tocListState
                        ) {
                            items(chapters) { (titleText, pageIndex) ->
                                val isCurrentPage = pageIndex == currentPage
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = titleText,
                                            fontWeight = if (isCurrentPage) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    modifier = Modifier
                                        .clickable {
                                            showTocDialog = false
                                            coroutineScope.launch {
                                                listState.scrollToItem(pageIndex)
                                            }
                                        }
                                        .background(
                                            if (isCurrentPage)
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                            else
                                                Color.Transparent
                                        )
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTocDialog = false }) { Text("Schließen") }
                }
            )
        }

        // Text-Anzeige-Dialog
        if (showTextDialog) {
            AlertDialog(
                onDismissRequest = { showTextDialog = false },
                title = { Text("Seitentext (Seite ${currentPage + 1})") },
                text = {
                    SelectionContainer {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                            item {
                                Text(
                                    text = dialogPageText.ifEmpty { "Kein Text auf dieser Seite gefunden." },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("PDF Text", dialogPageText)
                        clipboard.setPrimaryClip(clip)
                    }) {
                        Text("Kopieren")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTextDialog = false }) {
                        Text("Schließen")
                    }
                }
            )
        }
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
                        coroutineScope.launch {
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
    eraseRadius: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    isPageHighlighted: Boolean,
    invertColors: Boolean,
    onStrokeAdd: (AnnotationStroke) -> Unit,
    onStrokeUpdate: (AnnotationStroke, AnnotationStroke) -> Unit,
    onStrokesErase: (List<AnnotationStroke>) -> Unit,
    onScaleChange: (Float, Offset, IntSize) -> Unit,
    onOffsetChange: (Float, Float, IntSize) -> Unit,
    onScaleAndOffsetChange: (Float, Float, Float) -> Unit,
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

    Box(
        modifier = modifier
            .padding(8.dp)
            .background(if (invertColors) Color.Black else Color.White)
            .clip(RectangleShape)
    ) {
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
        // PDF-Seite als Bitmap
        Image(
            bitmap = remember(bitmap) { bitmap.asImageBitmap() },
            contentDescription = "PDF Seite ${pageIndex + 1}",
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

        // Seiten-Highlight (wenn aktiviert) - immer die Originalfarbe verwenden
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

                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.filter { it.pressed }

                            // Multi-touch: always handle zoom + pan (even from scale 1f)
                            if (pointers.size >= 2) {
                                // Early exit if overlay not initialized
                                if (overlaySize.width == 0 || overlaySize.height == 0) continue

                                val pos1 = pointers[0].position
                                val pos2 = pointers[1].position
                                val centroid = (pos1 + pos2) / 2f

                                var prevDist = hypot((pos1.x - pos2.x).toDouble(), (pos1.y - pos2.y).toDouble()).toFloat()
                                var prevCentroid = centroid

                                // Track the gesture
                                while (true) {
                                    val nextEvent = awaitPointerEvent()
                                    val changes = nextEvent.changes
                                    changes.forEach { it.consume() }
                                    val active = changes.filter { it.pressed }
                                    if (active.size < 2) break

                                    val newPos1 = active[0].position
                                    val newPos2 = if (active.size >= 2) active[1].position else newPos1
                                    val newDist = hypot((newPos1.x - newPos2.x).toDouble(), (newPos1.y - newPos2.y).toDouble()).toFloat()
                                    val newCentroid = (newPos1 + newPos2) / 2f

                                    val zoom = if (prevDist > 0f) newDist / prevDist else 1f
                                    val pan = newCentroid - prevCentroid

                                    // Calculate new scale
                                    val currentScale = transientScale
                                    val currentOffsetX = transientOffsetX
                                    val currentOffsetY = transientOffsetY
                                    val newScale = (currentScale * zoom).coerceIn(0.5f, 3f)

                                    // Calculate content point under centroid
                                    val contentX = (prevCentroid.x - currentOffsetX) / currentScale
                                    val contentY = (prevCentroid.y - currentOffsetY) / currentScale

                                    // Calculate new offsets
                                    var newOffsetX = newCentroid.x - contentX * newScale
                                    var newOffsetY = newCentroid.y - contentY * newScale

                                    // Apply pan
                                    newOffsetX += pan.x * 0.5f // Dampen pan during zoom
                                    newOffsetY += pan.y * 0.5f

                                    // Apply bounds
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

                                    // Update transient state
                                    transientScale = newScale
                                    transientOffsetX = newOffsetX
                                    transientOffsetY = newOffsetY
                                    onScaleAndOffsetChange(newScale, newOffsetX, newOffsetY)

                                    prevDist = newDist
                                    prevCentroid = newCentroid
                                }
                            } else if (pointers.size == 1 && transientScale != 1f) {
                                // Single-finger pan when zoomed
                                val pointerId = pointers[0].id
                                var prevPos = pointers[0].position

                                while (true) {
                                    val nextEvent = awaitPointerEvent()
                                    val change = nextEvent.changes.firstOrNull { it.id == pointerId }
                                    if (change == null || !change.pressed) break

                                    val newPos = change.position
                                    val drag = newPos - prevPos

                                    if (drag.x != 0f || drag.y != 0f) {
                                        change.consume()

                                        val currentScale = transientScale
                                        var newOffsetX = transientOffsetX + drag.x
                                        var newOffsetY = transientOffsetY + drag.y

                                        // Apply bounds
                                        val maxOffsetX = 0f
                                        val minOffsetX = overlaySize.width * (1f - currentScale)
                                        val maxOffsetY = 0f
                                        val minOffsetY = overlaySize.height * (1f - currentScale)

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

                                        transientOffsetX = newOffsetX
                                        transientOffsetY = newOffsetY
                                        onScaleAndOffsetChange(currentScale, newOffsetX, newOffsetY)
                                    }
                                    prevPos = newPos
                                }
                            }
                        }
                    }
                }
                // Custom pointer handling: Draw/Erase. Active when drawing or erase mode is enabled.
                .pointerInput(annotateMode, eraseMode, pageIndex, isCurrentPage) {
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
                                return@detectDragGestures
                            }

                            if (annotateMode) {
                                brushPosition = offset
                                // Transform screen coords to document coords accounting for bitmap bounds
                                val docX = (offset.x - bitmapDisplayOffset.x - renderOffsetX) / renderScale
                                val docY = (offset.y - bitmapDisplayOffset.y - renderOffsetY) / renderScale
                                val newStroke = AnnotationStroke(
                                    page = pageIndex,
                                    color = selectedColor.value.toLong(),
                                    strokeWidth = strokeWidth,
                                    points = listOf(Pair(docX, docY)),
                                    isHighlight = highlightMode
                                )
                                currentStroke = newStroke
                                // NICHT zur Liste hinzufügen während des Zeichnens - wird erst bei onDragEnd hinzugefügt
                            }
                        },
                        onDrag = { change, _ ->
                            if (eraseMode && isCurrentPage) {
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
                                return@detectDragGestures
                            }

                            if (annotateMode && isCurrentPage) {
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
                                    // Aktualisiere nur currentStroke, nicht die Liste
                                    currentStroke = updatedStroke
                                }
                            }
                        },
                        onDragEnd = {
                            if (eraseMode && isCurrentPage) {
                                eraserPosition = null
                            }
                            if (annotateMode && isCurrentPage) {
                                brushPosition = null
                                // Jetzt zur Liste hinzufügen nach dem Zeichnen
                                currentStroke?.let { onStrokeAdd(it) }
                                currentStroke = null
                                onSave()
                            }
                        }
                    )
                }
        ) {
            // Zeichne alle Annotationen für diese Seite
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
                // Annotations/Highlights/Drawing should NOT be affected by page color inversion
                drawPath(
                    path = path,
                    color = strokeBaseColor,
                    style = Stroke(width = widthPx),
                    alpha = if (stroke.isHighlight) 0.4f else 1f
                )
            }

            // Zeichne den aktuellen Stroke live während des Zeichnens
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
                    // Live stroke uses the original color as well
                    drawPath(
                        path = path,
                        color = strokeBaseColor,
                        style = Stroke(width = widthPx),
                        alpha = if (stroke.isHighlight) 0.4f else 1f
                    )
                }
            }

            // Zeichne den Radierer-Cursor wenn Radierer-Modus aktiv ist
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

            // Zeichne den Pinsel-Cursor wenn Zeichnen-Modus aktiv ist
            if (annotateMode && !eraseMode && brushPosition != null && isCurrentPage) {
                val brushRadius = if (highlightMode) {
                    // Highlight: dicker Pinsel
                    maxOf(7.5f, strokeWidth * renderScale * 2.5f)
                } else {
                    // Normal: normaler Pinsel
                    maxOf(2f, strokeWidth * renderScale / 2f)
                }

                // Äußerer Ring in der ausgewählten Farbe (Original-Farbe, nicht invertiert)
                val selOuter = selectedColor.copy(alpha = 0.5f)
                val selInner = selectedColor.copy(alpha = if (highlightMode) 0.3f else 0.2f)
                drawCircle(
                    color = selOuter,
                    radius = brushRadius,
                    center = brushPosition!!,
                    style = Stroke(width = 2f)
                )
                // Innerer gefüllter Kreis
                drawCircle(
                    color = selInner,
                    radius = brushRadius,
                    center = brushPosition!!
                )
            }
        }
    }
}
