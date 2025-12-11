package com.example.checklist_interactive.ui.checklist

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.R
import com.example.checklist_interactive.data.shortcuts.PageHighlightManager
import com.example.checklist_interactive.data.shortcuts.ShortcutManager
import java.io.File
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList

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
    initialPage: Int = 0,
    onPageChange: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var annotateMode by remember { mutableStateOf(false) }
    var highlightMode by remember { mutableStateOf(false) }
    var eraseMode by remember { mutableStateOf(false) }
    var strokeWidth by remember { mutableStateOf(4f) }
    var selectedColor by remember { mutableStateOf(Color.Red) }
    val strokes = remember { mutableStateListOf<AnnotationStroke>() }
    var currentPage by remember { mutableStateOf(initialPage) }
    var pageCount by remember { mutableStateOf(0) }
    var eraseRadius by remember { mutableStateOf(32f) }
    val pageScales = remember { mutableStateMapOf<Int, Float>() }
    val pageOffsetsX = remember { mutableStateMapOf<Int, Float>() }
    val pageOffsetsY = remember { mutableStateMapOf<Int, Float>() }
    var showShortcutDialog by remember { mutableStateOf(false) }
    var shortcutName by remember { mutableStateOf("") }
    val pageAspectRatios = remember { mutableStateMapOf<Int, Float>() }

    val shortcutManager = remember { ShortcutManager(context) }
    val highlightManager = remember { PageHighlightManager(context) }
    var pageHighlights by remember { mutableStateOf<List<Int>>(emptyList()) }
    var showTocDialog by remember { mutableStateOf(false) }
    var chapters by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }

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
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.roundToPx() }

    // PDF laden: open once, but render pages lazily.
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    val pdfRenderMutex = remember { Mutex() }

    LaunchedEffect(pdfPath, isInternalFile) {
        isLoading = true
        errorMessage = null
        try {
            withContext(Dispatchers.IO) {
                val pdfFile = if (isInternalFile) {
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

                fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(fileDescriptor!!)
                pageCount = pdfRenderer!!.pageCount

                // Pre-calculate all page aspect ratios to prevent layout jumping on scroll
                (0 until pageCount).forEach { index ->
                    val page = pdfRenderer!!.openPage(index)
                    pageAspectRatios[index] = page.width.toFloat() / page.height.toFloat()
                    page.close()
                }

                // Render only an initial subset of pages (initialPage and nearby), rest are loaded on demand.
                val initialIndices = listOf(initialPage - 1, initialPage, initialPage + 1).distinct().filter { it in 0 until pageCount }
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
                    pdfFile.delete()
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

    // Annotationen laden
    LaunchedEffect(pdfPath) {
        strokes.clear()
        strokes.addAll(AnnotationsRepository.load(context, pdfPath))
        pageHighlights = highlightManager.getHighlightsForFile(pdfPath).map { it.pageNumber }

        // Note: The main PDF loading coroutine populates `chapters` after pages are loaded.
    }

    // Aktuelle Seite basierend auf Scroll-Position — render current and neighbors on demand
    LaunchedEffect(listState.firstVisibleItemIndex) {
        currentPage = listState.firstVisibleItemIndex
        onPageChange?.invoke(currentPage)
        val cur = currentPage
        val indices = listOf(cur - 1, cur, cur + 1).filter { it in 0 until pageCount }
        for (idx in indices) maybeRequestRender(idx, screenWidthPx)
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
                    IconButton(onClick = {
                        AnnotationsRepository.save(context, pdfPath, strokes.toList())
                        onBack()
                    }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = context.getString(R.string.back), modifier = Modifier.size(18.dp))
                    }
                },
                actions = {
                    if (onToggleTheme != null) {
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = context.getString(R.string.toggle_dark_mode),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (onShowFileList != null) {
                FloatingActionButton(
                    onClick = onShowFileList,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Menu, contentDescription = context.getString(R.string.file_list))
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
                    // Kompakte Toolbar mit Scroll
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        // Erste Reihe: Zeichnen & Annotationen
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    annotateMode = !annotateMode
                                    if (annotateMode) {
                                        eraseMode = false
                                    }
                                },
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
                            IconButton(
                                onClick = { showTocDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ViewList, // visible chapter/list icon
                                    contentDescription = "Inhaltsverzeichnis",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    highlightMode = !highlightMode
                                    if (highlightMode) {
                                        annotateMode = true
                                        eraseMode = false
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Highlight,
                                    contentDescription = "Markieren",
                                    tint = if (highlightMode) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val isHighlighted = highlightManager.togglePageHighlight(pdfPath, currentPage)
                                    pageHighlights = highlightManager.getHighlightsForFile(pdfPath).map { it.pageNumber }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "Seite highlighten",
                                    tint = if (pageHighlights.contains(currentPage)) Color.Yellow else LocalContentColor.current,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    shortcutName = "$title - Seite ${currentPage + 1}"
                                    showShortcutDialog = true
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.BookmarkAdd,
                                    contentDescription = "Shortcut",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val idx = strokes.indexOfLast { it.page == currentPage }
                                    if (idx >= 0) strokes.removeAt(idx)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Undo,
                                    contentDescription = "Undo",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    strokes.removeAll { it.page == currentPage }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    AnnotationsRepository.save(context, pdfPath, strokes.toList())
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = "Save",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Zweite Reihe: Zoom & Radierer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val cur = pageScales[currentPage] ?: 1f
                                    if (cur > 0.5f) {
                                        pageScales[currentPage] = (cur - 0.5f).coerceAtLeast(0.5f)
                                        pageOffsetsX[currentPage] = 0f
                                        pageOffsetsY[currentPage] = 0f
                                    }
                                },
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
                            IconButton(
                                onClick = {
                                    val cur = pageScales[currentPage] ?: 1f
                                    if (cur < 3f) {
                                        pageScales[currentPage] = (cur + 0.5f).coerceAtMost(3f)
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ZoomIn,
                                    contentDescription = "Zoom In",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    eraseMode = !eraseMode
                                    if (eraseMode) {
                                        annotateMode = false
                                        highlightMode = false
                                    }
                                },
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

                    // Farb- und Strichbreiten-Kontrolle: nur anzeigen wenn Zeichnen aktiv ist
                    if (annotateMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                        val colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Magenta)
                        colors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .padding(4.dp)
                                    .background(c, CircleShape)
                                    .clickable { selectedColor = c }
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Breite:", modifier = Modifier.align(Alignment.CenterVertically))
                        Slider(
                            value = strokeWidth,
                            onValueChange = { strokeWidth = it },
                            valueRange = 1f..30f,
                            modifier = Modifier.width(120.dp).padding(start = 8.dp)
                        )
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
            AlertDialog(
                onDismissRequest = { showTocDialog = false },
                title = { Text("Inhaltsverzeichnis") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (chapters.isEmpty()) {
                            Text("Keine Kapitel gefunden. Zeige Seitenliste als Fallback.")
                        }
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            items(chapters) { (titleText, pageIndex) ->
                                ListItem(
                                    headlineContent = { Text(titleText) },
                                    modifier = Modifier.clickable {
                                        showTocDialog = false
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(pageIndex)
                                        }
                                    }
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
    onStrokeAdd: (AnnotationStroke) -> Unit,
    onStrokeUpdate: (AnnotationStroke, AnnotationStroke) -> Unit,
    onStrokesErase: (List<AnnotationStroke>) -> Unit,
    onScaleChange: (Float, Offset, IntSize) -> Unit,
    onOffsetChange: (Float, Float, IntSize) -> Unit,
    onSave: () -> Unit
) {
    var overlaySize by remember { mutableStateOf(IntSize(0, 0)) }
    var currentStroke by remember(pageIndex) { mutableStateOf<AnnotationStroke?>(null) }
    val isCurrentPage = pageIndex == currentPage

    Box(
        modifier = modifier
            .padding(8.dp)
            .background(Color.White) // PDF always on white background regardless of dark mode
            .clip(RectangleShape)
    ) {
        // PDF-Seite als Bitmap
        Image(
            bitmap = remember(bitmap) { bitmap.asImageBitmap() },
            contentDescription = "PDF Seite ${pageIndex + 1}",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                    transformOrigin = TransformOrigin(0f, 0f)
                ),
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center
        )

        // Seiten-Highlight (wenn aktiviert)
        if (isPageHighlighted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Yellow.copy(alpha = 0.2f))
            )
        }

        // Annotations Canvas Overlay
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { overlaySize = it }
                // Custom pointer handling: Pan/Zoom. Active for multi-touch, or single-touch when zoomed.
                .pointerInput(pageIndex, isCurrentPage, scale) {
                    if (!isCurrentPage) return@pointerInput

                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressedPointers = event.changes.filter { it.pressed }

                            // Multi-touch (>= 2 pointers): implement pinch zoom + pan
                            if (pressedPointers.size >= 2) {
                                // Initialize previous positions
                                var prevPos1 = pressedPointers[0].position
                                var prevPos2 = pressedPointers[1].position
                                var prevDist = hypot((prevPos1.x - prevPos2.x).toDouble(), (prevPos1.y - prevPos2.y).toDouble()).toFloat()
                                var prevCentroid = (prevPos1 + prevPos2) / 2f
                                // Loop until one of the pointers released
                                while (true) {
                                    val nextEvent = awaitPointerEvent()
                                    val changes = nextEvent.changes
                                    // Consume pointer changes so parent LazyColumn doesn't pick them up
                                    changes.forEach { it.consume() }
                                    val active = changes.filter { it.pressed }
                                    if (active.size < 2) break
                                    val pos1 = active[0].position
                                    val pos2 = if (active.size >= 2) active[1].position else active[0].position
                                    val newDist = hypot((pos1.x - pos2.x).toDouble(), (pos1.y - pos2.y).toDouble()).toFloat()
                                    val newCentroid = (pos1 + pos2) / 2f
                                    val zoom = if (prevDist != 0f) newDist / prevDist else 1f
                                    val pan = newCentroid - prevCentroid

                                    if (zoom != 1f) {
                                        val newScale = (scale * zoom).coerceIn(0.5f, 3f)
                                        onScaleChange(newScale, prevCentroid, overlaySize)
                                    }
                                    onOffsetChange(pan.x, pan.y, overlaySize)

                                    prevDist = newDist
                                    prevCentroid = newCentroid
                                }
                                continue
                            }

                                // Single-finger drag: only handle panning if zoomed (scale != 1f), otherwise let parent handle scroll
                            if (pressedPointers.size == 1 && scale != 1f) {
                                val pointerId = pressedPointers[0].id
                                var prevPos = pressedPointers[0].position
                                var active = true
                                while (active) {
                                    val nextEvent = awaitPointerEvent()
                                    val change = nextEvent.changes.firstOrNull { it.id == pointerId }
                                    if (change == null || !change.pressed) {
                                        active = false
                                        break
                                    }
                                    val newPos = change.position
                                    val drag = newPos - prevPos
                                    if (drag.x != 0f || drag.y != 0f) {
                                        change.consume()
                                        onOffsetChange(drag.x, drag.y, overlaySize)
                                    }
                                    prevPos = newPos
                                }
                                continue
                            }

                            // No multi-touch and either scale == 1f or no pointers pressed: do nothing to allow LazyColumn to handle scrolls.
                        }
                    }
                }
                // Custom pointer handling: Draw/Erase. Active for single-touch when NOT zoomed.
                .pointerInput(annotateMode, eraseMode, pageIndex, isCurrentPage, scale) {
                    // This block handles drawing/erasing. It should only be active when NOT zoomed.
                    if (!isCurrentPage || scale != 1f) return@pointerInput
                    if (!annotateMode && !eraseMode) return@pointerInput

                    detectDragGestures(
                        onDragStart = { offset ->
                            if (eraseMode) {
                                val toErase = strokes.filter { stroke ->
                                    stroke.points.any { p ->
                                        val px = p.first * scale + offsetX
                                        val py = p.second * scale + offsetY
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
                                val docX = (offset.x - offsetX) / scale
                                val docY = (offset.y - offsetY) / scale
                                val newStroke = AnnotationStroke(
                                    page = pageIndex,
                                    color = selectedColor.value.toLong(),
                                    strokeWidth = strokeWidth,
                                    points = listOf(Pair(docX, docY)),
                                    isHighlight = highlightMode
                                )
                                currentStroke = newStroke
                                onStrokeAdd(newStroke)
                            }
                        },
                        onDrag = { change, _ ->
                            if (eraseMode && isCurrentPage) {
                                val offset = change.position
                                val toErase = strokes.filter { stroke ->
                                    stroke.points.any { p ->
                                        val px = p.first * scale + offsetX
                                        val py = p.second * scale + offsetY
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
                                currentStroke?.let { stroke ->
                                    val offset = change.position
                                    val docX = (offset.x - offsetX) / scale
                                    val docY = (offset.y - offsetY) / scale
                                    val newPoints = stroke.points.toMutableList().apply {
                                        add(Pair(docX, docY))
                                    }
                                    val updatedStroke = stroke.copy(points = newPoints)
                                    onStrokeUpdate(stroke, updatedStroke)
                                    currentStroke = updatedStroke
                                }
                            }
                        },
                        onDragEnd = {
                            if (annotateMode && isCurrentPage) {
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
                        val x = p.first * scale + offsetX
                        val y = p.second * scale + offsetY
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                val widthPx = maxOf(1f, stroke.strokeWidth * scale)
                drawPath(
                    path = path,
                    color = Color(stroke.color.toULong()),
                    style = Stroke(width = widthPx),
                    alpha = if (stroke.isHighlight) 0.18f else 1f
                )
            }
        }
    }
}
