package com.example.checklist_interactive.ui.checklist

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot

import androidx.compose.ui.platform.LocalContext
import com.example.checklist_interactive.R
import com.example.checklist_interactive.data.shortcuts.ShortcutManager
import com.example.checklist_interactive.data.shortcuts.PageHighlightManager
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Star

/**
 * Eigener PDF-Viewer ohne externe Abhängigkeiten.
 * Verwendet Android's eingebauten PdfRenderer (verfügbar ab API 21).
 * Mit Annotation-Unterstützung (Zeichnen, Highlighten, Löschen).
 * Mit Zoom, Seiten-Highlights und Shortcuts.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var showShortcutDialog by remember { mutableStateOf(false) }
    var shortcutName by remember { mutableStateOf("") }

    val shortcutManager = remember { ShortcutManager(context) }
    val highlightManager = remember { PageHighlightManager(context) }
    var pageHighlights by remember { mutableStateOf<List<Int>>(emptyList()) }

    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)

    // PDF laden
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

                val fileDescriptor = ParcelFileDescriptor.open(
                    pdfFile,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
                val pdfRenderer = PdfRenderer(fileDescriptor)
                pageCount = pdfRenderer.pageCount

                // Alle Seiten rendern
                for (pageIndex in 0 until pageCount) {
                    val page = pdfRenderer.openPage(pageIndex)
                    val bitmap = Bitmap.createBitmap(
                        page.width * 2,
                        page.height * 2,
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pageBitmaps[pageIndex] = bitmap
                    page.close()
                }

                pdfRenderer.close()
                fileDescriptor.close()

                // Nur temporäre Dateien löschen
                if (!isInternalFile) {
                    pdfFile.delete()
                }
            }
            isLoading = false
        } catch (e: Exception) {
            errorMessage = context.getString(R.string.error_loading_pdf, e.message ?: "")
            isLoading = false
        }
    }

    // Annotationen laden
    LaunchedEffect(pdfPath) {
        strokes.clear()
        strokes.addAll(AnnotationsRepository.load(context, pdfPath))
        pageHighlights = highlightManager.getHighlightsForFile(pdfPath).map { it.pageNumber }
    }

    // Aktuelle Seite basierend auf Scroll-Position
    LaunchedEffect(listState.firstVisibleItemIndex) {
        currentPage = listState.firstVisibleItemIndex
        onPageChange?.invoke(currentPage)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("$title (Page ${currentPage + 1}/$pageCount)") }, navigationIcon = {
                IconButton(onClick = {
                    AnnotationsRepository.save(context, pdfPath, strokes.toList())
                    onBack()
                }) { Icon(Icons.Default.ArrowBack, contentDescription = context.getString(R.string.back)) }
            }, actions = {
                if (onToggleTheme != null) {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = context.getString(R.string.toggle_dark_mode)
                        )
                    }
                }
            })
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
                                    Icons.Default.Bookmark, 
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
                                    if (scale > 0.5f) {
                                        scale -= 0.5f
                                        offsetX = 0f
                                        offsetY = 0f
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
                                text = "${(scale * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(45.dp),
                                textAlign = TextAlign.Center
                            )
                            IconButton(
                                onClick = {
                                    if (scale < 3f) {
                                        scale += 0.5f
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

                    // Farb- und Strichbreiten-Kontrolle
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

                    // PDF-Seiten mit Annotationen
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        userScrollEnabled = true
                    ) {
                        items(pageCount) { pageIndex ->
                            val bitmap = pageBitmaps[pageIndex]
                            if (bitmap != null) {
                                PdfPageWithAnnotations(
                                    bitmap = bitmap,
                                    pageIndex = pageIndex,
                                    currentPage = currentPage,
                                    strokes = strokes.filter { it.page == pageIndex },
                                    annotateMode = annotateMode,
                                    eraseMode = eraseMode,
                                    selectedColor = selectedColor,
                                    strokeWidth = strokeWidth,
                                    highlightMode = highlightMode,
                                    eraseRadius = eraseRadius,
                                    scale = scale,
                                    offsetX = offsetX,
                                    offsetY = offsetY,
                                    isPageHighlighted = pageHighlights.contains(pageIndex),
                                    onStrokeAdd = { stroke ->
                                        strokes.add(stroke)
                                    },
                                    onStrokeUpdate = { oldStroke, newStroke ->
                                        val idx = strokes.indexOf(oldStroke)
                                        if (idx >= 0) {
                                            strokes[idx] = newStroke
                                        }
                                    },
                                    onStrokesErase = { erasedStrokes ->
                                        strokes.removeAll(erasedStrokes)
                                    },
                                    onScaleChange = { newScale ->
                                        scale = newScale
                                    },
                                    onOffsetChange = { dx, dy ->
                                        offsetX += dx
                                        offsetY += dy
                                    },
                                    onSave = {
                                        AnnotationsRepository.save(context, pdfPath, strokes.toList())
                                    }
                                )
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
    }
}

@Composable
private fun PdfPageWithAnnotations(
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
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
    onSave: () -> Unit
) {
    var overlaySize by remember { mutableStateOf(IntSize(0, 0)) }
    var currentStroke by remember { mutableStateOf<AnnotationStroke?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
            .padding(8.dp)
    ) {
        // PDF-Seite als Bitmap
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "PDF Seite ${pageIndex + 1}",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            contentScale = ContentScale.Fit
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
                .pointerInput(pageIndex, currentPage, annotateMode, eraseMode) {
                    // Zoom mit 2-Finger-Pinch (immer verfügbar wenn nicht im Zeichen/Radierer-Modus)
                    if (pageIndex == currentPage && !annotateMode && !eraseMode) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            // Nur zoomen wenn zoom != 1.0 (also echte Pinch-Geste)
                            if (zoom != 1f) {
                                onScaleChange((scale * zoom).coerceIn(0.5f, 3f))
                                onOffsetChange(pan.x, pan.y)
                            }
                        }
                    }
                }
                .pointerInput(annotateMode, eraseMode, pageIndex, currentPage) {
                    // Zeichnen/Radierer NUR wenn Modus aktiv ist
                    if (pageIndex != currentPage) return@pointerInput
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
                            if (eraseMode && pageIndex == currentPage) {
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

                            if (annotateMode && pageIndex == currentPage) {
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
                            if (annotateMode && pageIndex == currentPage) {
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
