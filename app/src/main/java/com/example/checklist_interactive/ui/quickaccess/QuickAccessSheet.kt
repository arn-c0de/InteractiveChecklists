package com.example.checklist_interactive.ui.quickaccess

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material3.*
import android.widget.Toast
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.example.checklist_interactive.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.background
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.View
import com.example.checklist_interactive.data.quicknotes.QuickNoteManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

// Replace [Callsign] placeholders (case-insensitive) with provided callsign value when rendering.
private fun expandCallsignPlaceholder(content: String, callsign: String): String {
    if (callsign.isBlank()) return content
    val regex = Regex("\\[Callsign\\]", RegexOption.IGNORE_CASE)
    return content.replace(regex, callsign)
}

// Format a com input as 3-digit whole + '.' + up to 3-digit fractional while typing
private fun formatComInputToDot(input: String): String {
    // Extract digits only
    val digitsOnly = input.filter { it.isDigit() }

    // Maximum 6 digits (3 whole + 3 fractional)
    val maxDigits = 6
    val trimmed = digitsOnly.take(maxDigits)

    return when {
        trimmed.isEmpty() -> ""
        trimmed.length <= 3 -> trimmed
        else -> {
            val whole = trimmed.take(3)
            val fraction = trimmed.drop(3).take(3)
            "$whole.$fraction"
        }
    }
}

/**
 * Builds annotated string with clickable internal links
 */
private fun buildAnnotatedStringWithLinks(content: String): AnnotatedString {
    val regex = Regex("\\[([^]]+)]\\(internal://open\\?file=([^)&]+)(?:&page=(\\d+))?\\)")
    val builder = AnnotatedString.Builder()
    var last = 0

    regex.findAll(content).forEach { match ->
        val range = match.range
        if (range.first > last) {
            builder.append(content.substring(last, range.first))
        }

        val label = match.groups[1]?.value ?: ""
        val fileEnc = match.groups[2]?.value ?: ""
        val page = match.groups[3]?.value ?: ""
        val annotationValue = "$fileEnc|$page"

        // Show only pin emoji for clickable link in overlay
        val display = "📌"
        val startIndex = builder.length
        builder.append(display)
        val endIndex = builder.length

        builder.addStringAnnotation(
            tag = "OPEN_LINK",
            annotation = annotationValue,
            start = startIndex,
            end = endIndex
        )
        builder.addStyle(
            SpanStyle(
                color = Color(0xFF0066CC),
                textDecoration = TextDecoration.Underline
            ),
            startIndex,
            endIndex
        )
        last = range.last + 1
    }

    if (last < content.length) {
        builder.append(content.substring(last))
    }

    return builder.toAnnotatedString()
}

// Build a transformed display string and compute link ranges for visual mapping
private data class LinkRange(val originalStart: Int, val originalEnd: Int, val transformedStart: Int, val transformedEnd: Int)

private fun buildTransformedStringAndRanges(content: String): Pair<String, List<LinkRange>> {
    val regex = Regex("\\[([^]]+)]\\(internal://open\\?file=([^)&]+)(?:&page=(\\d+))?\\)")
    val sb = StringBuilder()
    val ranges = mutableListOf<LinkRange>()
    var last = 0
    var transIndex = 0

    regex.findAll(content).forEach { match ->
        val range = match.range
        if (range.first > last) {
            val before = content.substring(last, range.first)
            sb.append(before)
            transIndex += before.length
        }

        // Replace entire link markdown with a single SPACE so the raw text shows nothing
        val display = " "
        val tStart = transIndex
        sb.append(display)
        transIndex += display.length
        val tEnd = transIndex

        ranges.add(LinkRange(range.first, range.last + 1, tStart, tEnd))
        last = range.last + 1
    }

    if (last < content.length) {
        val tail = content.substring(last)
        sb.append(tail)
    }

    return sb.toString() to ranges
}

private class LinksVisualTransformation(private val content: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val (transformed, ranges) = buildTransformedStringAndRanges(content)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (ranges.isEmpty()) return offset.coerceIn(0, transformed.length)
                var delta = 0
                for (r in ranges) {
                    if (offset < r.originalStart) {
                        // Before this link
                        return (offset - delta).coerceIn(0, transformed.length)
                    }
                    if (offset < r.originalEnd) {
                        // Inside this link
                        return r.transformedStart
                    }
                    // After this link, accumulate removed length
                    delta += (r.originalEnd - r.originalStart) - (r.transformedEnd - r.transformedStart)
                }
                // After all links
                val mapped = (offset - delta)
                return mapped.coerceIn(0, transformed.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (ranges.isEmpty()) return offset.coerceIn(0, content.length)
                var delta = 0
                for (r in ranges) {
                    if (offset < r.transformedStart) {
                        // Before this link
                        return (offset + delta).coerceIn(0, content.length)
                    }
                    if (offset < r.transformedEnd) {
                        // Inside this link
                        return r.originalStart
                    }
                    // After this link, accumulate removed length
                    delta += (r.originalEnd - r.originalStart) - (r.transformedEnd - r.transformedStart)
                }
                // After all links
                val mapped = (offset + delta)
                return mapped.coerceIn(0, content.length)
            }
        }

        return TransformedText(AnnotatedString(transformed), offsetMapping)
    }
}

/**
 * Quick Access Bottom Sheet with modern note-taking functionality
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAccessSheet(
    onDismiss: () -> Unit,
    onOpenDocument: ((filePath: String, pageNumber: Int?) -> Unit)? = null,
    currentDocumentPath: String? = null,
    currentDocumentName: String? = null,
    currentPageNumber: Int? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val providedNoteManager = com.example.checklist_interactive.ui.quickaccess.LocalQuickNoteManager.current
    val noteManager = providedNoteManager ?: remember { QuickNoteManager(context) }
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Force immersive fullscreen mode continuously while bottom sheet is shown
    LaunchedEffect(sheetState.currentValue) {
        val activity = view.context as? android.app.Activity
        val window = activity?.window

        val hideSystemUI = {
            // Hide for activity window (if available)
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
                val controller = WindowInsetsControllerCompat(it, it.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            // Also try to hide for the current view's window (covers dialog window from ModalBottomSheet)
            try {
                val viewWindow = (view.context as? android.app.Activity)?.window
                if (viewWindow != null) {
                    val controller = WindowCompat.getInsetsController(viewWindow, view)
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } catch (_: Throwable) {
            }

            // Try rootView as well in case the modal sheet uses a different attach point
            try {
                val rootWindow = (view.rootView.context as? android.app.Activity)?.window
                if (rootWindow != null) {
                    val controller = WindowCompat.getInsetsController(rootWindow, view.rootView)
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } catch (_: Throwable) {
            }
        }

        // Keep applying every 100ms to override ModalBottomSheet's behavior only while the sheet is visible
        if (sheetState.isVisible) {
            while (isActive && sheetState.isVisible) {
                hideSystemUI()
                delay(100L)
            }
        }
    }

    // State flows
    val notes by noteManager.notesSummary.collectAsState()
    val activeNoteId by noteManager.activeNoteId.collectAsState()

    // Get content for active note (reactive to activeNoteId changes)
    val savedNote by noteManager.noteContent.collectAsState()

    // Local UI state
    var currentNote by remember { mutableStateOf("") }
    // Radio toolbar state
    val callsignFlow by noteManager.callsign.collectAsState()
    val com1Flow by noteManager.com1.collectAsState()
    val com1ModeFlow by noteManager.com1Mode.collectAsState()
    val com2Flow by noteManager.com2.collectAsState()
    val com2ModeFlow by noteManager.com2Mode.collectAsState()
    val flightStatusFlow by noteManager.flightStatus.collectAsState()
    var callsign by remember { mutableStateOf("") }
    var com1 by remember { mutableStateOf("") }
    var com1Mode by remember { mutableStateOf("FM") }
    var com2 by remember { mutableStateOf("") }
    var com2Mode by remember { mutableStateOf("FM") }
    var flightStatus by remember { mutableStateOf("") }
    var hasChanges by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var showAdvancedEditor by remember { mutableStateOf(false) }
    var newTextInput by remember { mutableStateOf(TextFieldValue("")) }
    var quickInputMode by remember { mutableStateOf("text") }
    var wasInDrawMode by remember { mutableStateOf(false) }
    var showDrawMode by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Pending delete confirmations (double-press to confirm)
    var pendingDeleteNoteId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteCurrentNoteConfirm by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp >= 600 || configuration.screenHeightDp >= 800

    // Sheet height control (persisted)
    val prefs = context.getSharedPreferences("quick_notes", android.content.Context.MODE_PRIVATE)
    val KEY_SHEET_FRACTION = "quick_access_sheet_fraction"
    val savedFraction = prefs.getFloat(KEY_SHEET_FRACTION, 0.7f)
    var sheetFraction by rememberSaveable { mutableStateOf(savedFraction.coerceIn(0.2f, 0.95f)) }
    val sheetMin = 0.2f
    val sheetMax = 0.95f

    // Transparency control (persisted)
    val KEY_SHEET_OPACITY = "quick_access_sheet_opacity"
    val savedOpacity = prefs.getFloat(KEY_SHEET_OPACITY, 1.0f)
    // Ensure minimum opacity of 25% so sheet never becomes invisible
    var sheetOpacity by rememberSaveable { mutableStateOf(savedOpacity.coerceIn(0.25f, 1.0f)) }
    var showOpacitySlider by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    // Flight info expanded state
    val flightExpandedFlow by noteManager.flightInfoExpanded.collectAsState()
    var flightExpanded by remember { mutableStateOf(flightExpandedFlow) }


    // Sync currentNote with savedNote and activeNoteId (Tab-Wechsel)
    LaunchedEffect(savedNote, activeNoteId) {
        currentNote = savedNote
        hasChanges = false
    }

    // Initialize toolbar state from flows
    LaunchedEffect(callsignFlow, com1Flow, com1ModeFlow, com2Flow, com2ModeFlow, flightStatusFlow) {
        callsign = callsignFlow
        com1 = com1Flow.replace(',', '.')
        com1Mode = com1ModeFlow
        com2 = com2Flow.replace(',', '.')
        com2Mode = com2ModeFlow
        flightStatus = flightStatusFlow
    }

    // Auto-save callsign when it changes (debounced via LaunchedEffect cancellations)
    LaunchedEffect(callsign) {
        if (callsign != callsignFlow) {
            kotlinx.coroutines.delay(600L)
            noteManager.saveCallsign(callsign)
        }
    }

    // Auto-save COM1 and mode when either changes
    LaunchedEffect(com1, com1Mode) {
        if (com1 != com1Flow || com1Mode != com1ModeFlow) {
            kotlinx.coroutines.delay(300L)
            noteManager.saveCom1(com1, com1Mode)
        }
    }

    // Auto-save COM2 and mode when either changes
    LaunchedEffect(com2, com2Mode) {
        if (com2 != com2Flow || com2Mode != com2ModeFlow) {
            kotlinx.coroutines.delay(300L)
            noteManager.saveCom2(com2, com2Mode)
        }
    }

    // Auto-save flight status
    LaunchedEffect(flightStatus) {
        if (flightStatus != flightStatusFlow) {
            kotlinx.coroutines.delay(600L)
            noteManager.saveFlightStatus(flightStatus)
        }
    }
    LaunchedEffect(flightExpandedFlow) { flightExpanded = flightExpandedFlow }

    // Auto-save opacity when it changes
    LaunchedEffect(sheetOpacity) {
        prefs.edit().putFloat(KEY_SHEET_OPACITY, sheetOpacity).apply()
    }

    // Reset pending delete confirmations after timeout
    LaunchedEffect(pendingDeleteNoteId) {
        if (pendingDeleteNoteId != null) {
            kotlinx.coroutines.delay(3000L)
            pendingDeleteNoteId = null
        }
    }
    LaunchedEffect(pendingDeleteCurrentNoteConfirm) {
        if (pendingDeleteCurrentNoteConfirm) {
            kotlinx.coroutines.delay(3000L)
            pendingDeleteCurrentNoteConfirm = false
        }
    }

    // Auto-save after 2 seconds of inactivity
    LaunchedEffect(currentNote, hasChanges) {
        if (hasChanges) {
            kotlinx.coroutines.delay(2000L)
            noteManager.saveNote(currentNote)
            hasChanges = false
        }
    }

    // Drawing state: strokes are lists of points
    @Serializable
    data class PointDto(val x: Float, val y: Float)

    val json = remember { Json { ignoreUnknownKeys = true } }
    val strokesState = remember { mutableStateListOf<List<PointDto>>() }
    var currentStroke by remember { mutableStateOf<List<PointDto>>(emptyList()) }
    var drawingCleared by remember { mutableStateOf(false) }
    var drawingDirty by remember { mutableStateOf(false) }
    var eraseMode by remember { mutableStateOf(false) }

    // Track previous active note ID to save before switching
    var previousActiveNoteId by remember { mutableStateOf<String?>(null) }

    // Load existing drawing when active note changes (one-time load, not reactive)
    LaunchedEffect(activeNoteId) {
        val id = activeNoteId

        // Save previous note's drawing before switching to new note
        if (previousActiveNoteId != null && previousActiveNoteId != id && strokesState.isNotEmpty()) {
            try {
                // Include current stroke if present
                if (currentStroke.isNotEmpty()) {
                    strokesState.add(currentStroke)
                    currentStroke = emptyList()
                }
                val drawingJson = json.encodeToString(strokesState.toList())
                noteManager.saveDrawing(previousActiveNoteId, drawingJson)
                android.util.Log.d("QuickAccessSheet", "Saved previous note drawing before tab switch: $previousActiveNoteId (len=${drawingJson.length})")
            } catch (e: Exception) {
                android.util.Log.e("QuickAccessSheet", "Error saving previous note drawing", e)
            }
        }

        // Update previous ID tracker
        previousActiveNoteId = id

        if (id != null) {
            // Load once, then strokes persist in memory until explicitly cleared
            val drawingJson = noteManager.getDrawingFlow(id).first()
            android.util.Log.d("QuickAccessSheet", "Loaded drawing for $id, len=${drawingJson?.length ?: 0}")
            if (drawingJson.isNullOrBlank()) {
                // Try backup from SharedPreferences if DB returned nothing
                val backup = noteManager.getDrawingBackup(id)
                if (!backup.isNullOrBlank()) {
                    try {
                        val strokes: List<List<PointDto>> = json.decodeFromString(backup)
                        strokesState.clear()
                        strokesState.addAll(strokes)
                        android.util.Log.d("QuickAccessSheet", "Initialized strokes from backup count=${strokesState.size} for $id")
                        // Restore backup into DB to ensure persistence
                        noteManager.saveDrawing(id, backup)
                    } catch (_: Exception) {
                        strokesState.clear()
                    }
                } else {
                    strokesState.clear()
                }
            } else {
                try {
                    val strokes: List<List<PointDto>> = json.decodeFromString(drawingJson)
                    strokesState.clear()
                    strokesState.addAll(strokes)
                    android.util.Log.d("QuickAccessSheet", "Initialized strokes count=${strokesState.size} for $id")
                } catch (_: Exception) {
                    strokesState.clear()
                }
            }
        } else {
            strokesState.clear()
        }
    }

    // Auto-save drawing strokes when leaving draw mode
    LaunchedEffect(quickInputMode) {
        // Track mode transitions
        if (quickInputMode == "draw") {
            wasInDrawMode = true
        } else {
            // leaving draw mode: save current strokes persistently (include in-flight stroke)
            if (wasInDrawMode) {
                // include currentStroke if present
                if (currentStroke.isNotEmpty()) {
                    strokesState.add(currentStroke)
                    currentStroke = emptyList()
                    drawingDirty = true
                }

                val id = activeNoteId
                // If the user explicitly cleared, propagate the clear; otherwise avoid saving null (to prevent accidental deletes)
                if (drawingCleared) {
                    if (id != null) noteManager.saveDrawing(id, null)
                    drawingCleared = false
                } else {
                    if (strokesState.isNotEmpty()) {
                        val drawingJson = json.encodeToString(strokesState.toList())
                        if (id != null) noteManager.saveDrawing(id, drawingJson)
                            drawingDirty = false
                    }
                }

                wasInDrawMode = false
            }
        }
    }

    // Periodic autosave when drawings changed while in draw mode
    LaunchedEffect(drawingDirty, quickInputMode) {
        if (quickInputMode == "draw" && drawingDirty) {
            // debounce 3s
            kotlinx.coroutines.delay(3000L)
            if (quickInputMode == "draw" && drawingDirty) {
                try {
                    if (currentStroke.isNotEmpty()) {
                        strokesState.add(currentStroke)
                        currentStroke = emptyList()
                    }
                    val id = activeNoteId
                    if (id != null) {
                        val drawingJson = if (strokesState.isEmpty()) null else json.encodeToString(strokesState.toList())
                        noteManager.saveDrawing(id, drawingJson)
                        drawingDirty = false
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    // Save on dispose
    DisposableEffect(Unit) {
        onDispose {
            if (hasChanges) {
                noteManager.saveNote(currentNote)
            }
            // Ensure drawing is saved when sheet disposes
            try {
                if (currentStroke.isNotEmpty()) {
                    strokesState.add(currentStroke)
                    currentStroke = emptyList()
                }
                val id = activeNoteId
                if (id != null) {
                    if (drawingCleared) {
                        noteManager.saveDrawing(id, null)
                    } else if (strokesState.isNotEmpty()) {
                        val drawingJson = json.encodeToString(strokesState.toList())
                        noteManager.saveDrawing(id, drawingJson)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (hasChanges) {
                noteManager.saveNote(currentNote)
            }
            onDismiss()
        },
        modifier = modifier,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = sheetOpacity)
    ) {
        // Try to hide the system UI inside the dialog window that hosts the sheet.
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

        // Also keep ensuring hide while it's visible (already present; keep loop for resilience).
        LaunchedEffect(dialogView, sheetState.isVisible) {
            if (sheetState.isVisible) {
                val dialogWindow = (dialogView.context as? android.app.Activity)?.window
                val dialogController = dialogWindow?.let { WindowCompat.getInsetsController(it, dialogView) }
                while (isActive && sheetState.isVisible) {
                    dialogController?.hide(WindowInsetsCompat.Type.systemBars())
                    delay(100L)
                }
            }
        }
        // Restrict sheet height to a fraction of screen height and allow dragging the handle to resize
        val density = LocalDensity.current
        val sheetHeightDp = (configuration.screenHeightDp.toFloat() * sheetFraction).dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeightDp)
                .alpha(sheetOpacity)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Drag handle at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                // dragAmount.y > 0 means dragging down -> reduce sheet fraction
                                val screenPx = with(density) { configuration.screenHeightDp.dp.toPx() }
                                val fracDelta = dragAmount.y / screenPx
                                sheetFraction = (sheetFraction - fracDelta).coerceIn(sheetMin, sheetMax)
                            },
                            onDragEnd = {
                                prefs.edit().putFloat(KEY_SHEET_FRACTION, sheetFraction).apply()
                            }
                        )
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                // visual handle
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(width = 64.dp, height = 6.dp)
                        .background(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small)
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
            // Header with title and actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Article,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.quick_notes_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    // Transparency button
                    FilledTonalIconButton(
                        onClick = { showOpacitySlider = !showOpacitySlider },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = "${(sheetOpacity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Search button
                    if (notes.size > 1) {
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(
                                if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = stringResource(R.string.action_search)
                            )
                        }
                    }

                    // Pin button (only if document is open)
                    if (currentDocumentPath != null && currentDocumentName != null) {
                        FilledTonalIconButton(
                            onClick = {
                                val uri = "internal://open?file=${URLEncoder.encode(currentDocumentPath, "UTF-8")}${
                                    if (currentPageNumber != null) "&page=${currentPageNumber + 1}" else ""
                                }"
                                val label = if (currentPageNumber != null) "$currentDocumentName (${context.getString(R.string.tab_page_number, currentPageNumber + 1)})" else currentDocumentName
                                val linkText = "\n📌 [$label]($uri)\n"
                                val newContent = currentNote + linkText
                                currentNote = newContent
                                hasChanges = true
                            }
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = stringResource(R.string.quick_notes_pin_document))
                        }
                    }
                }
            }

            // Opacity slider
            AnimatedVisibility(visible = showOpacitySlider) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.quick_notes_opacity_label, (sheetOpacity * 100).toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Slider(
                        value = sheetOpacity,
                        onValueChange = { sheetOpacity = it },
                        valueRange = 0.25f..1.0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Search bar
            if (showSearchBar) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    placeholder = { Text(stringResource(R.string.quick_notes_search_placeholder)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    },
                    singleLine = true
                )
            }

            // Flight Info header + Radio / Callsign toolbar (small)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(R.string.quick_notes_section_flight_info), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = {
                    flightExpanded = !flightExpanded
                    noteManager.saveFlightInfoExpanded(flightExpanded)
                }) {
                    Icon(
                        imageVector = if (flightExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (flightExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand)
                    )
                }
            }

            // Compact view when collapsed
            AnimatedVisibility(visible = !flightExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "${callsign.ifBlank { "-" }}  | ${stringResource(R.string.quick_notes_status_label)}: ${flightStatus.ifBlank { stringResource(R.string.flight_status_idle) }}  | ${stringResource(R.string.quick_notes_com1_label)}: ${com1.ifBlank { "-" }} ${com1Mode}  | ${stringResource(R.string.quick_notes_com2_label)}: ${com2.ifBlank { "-" }} ${com2Mode}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            AnimatedVisibility(visible = flightExpanded) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top row: CALLSIGN
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = callsign,
                                    onValueChange = { callsign = it },
                                    label = { Text(stringResource(R.string.quick_notes_callsign_label)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                // Flight status dropdown using shared component
                                FlightStatusDropdown(
                                    currentStatus = flightStatus,
                                    onStatusChange = { flightStatus = it },
                                    compact = false
                                )
                            }
                    }

                    // Second row: COM1 and COM2 side-by-side with their modes and the action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // COM1 column
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = com1,
                                onValueChange = { com1 = formatComInputToDot(it) },
                                label = { Text(stringResource(R.string.quick_notes_com1_label)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(140.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val comFmLabel = stringResource(R.string.quick_notes_fm)
                            val comAmLabel = stringResource(R.string.quick_notes_am)
                            FilterChip(selected = com1Mode == "FM", onClick = { com1Mode = "FM" }, label = { Text(comFmLabel) })
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(selected = com1Mode == "AM", onClick = { com1Mode = "AM" }, label = { Text(comAmLabel) })
                        }

                        // COM2 column
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = com2,
                                onValueChange = { com2 = formatComInputToDot(it) },
                                label = { Text(stringResource(R.string.quick_notes_com2_label)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(140.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val comFmLabel2 = stringResource(R.string.quick_notes_fm)
                            val comAmLabel2 = stringResource(R.string.quick_notes_am)
                            FilterChip(selected = com2Mode == "FM", onClick = { com2Mode = "FM" }, label = { Text(comFmLabel2) })
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(selected = com2Mode == "AM", onClick = { com2Mode = "AM" }, label = { Text(comAmLabel2) })
                        }

                        // no manual save/load buttons (auto-save/auto-load enabled)
                    }
                }
            }

            // Rename dialog
            if (showRenameDialog) {
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    icon = {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    },
                    title = { Text(stringResource(R.string.quick_notes_rename_title)) },
                    text = {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text(stringResource(R.string.quick_notes_title_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        FilledTonalButton(
                            onClick = {
                                renameTargetId?.let {
                                    noteManager.renameNote(it, renameText)
                                }
                                showRenameDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.action_save))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }

            // Editor mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showAdvancedEditor) stringResource(R.string.quick_notes_editor_markdown) else stringResource(R.string.quick_notes_editor_note),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = !showAdvancedEditor,
                        onClick = { showAdvancedEditor = false },
                        label = { Text(stringResource(R.string.quick_notes_mode_normal_chip), style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = showAdvancedEditor,
                        onClick = { showAdvancedEditor = true },
                        label = { Text(stringResource(R.string.quick_notes_mode_markdown_chip), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Note editor card (only the text display/editor)
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 200.dp)
                    .weight(1f, fill = isLargeScreen),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            ) {
                if (showAdvancedEditor) {
                    // Advanced markdown editor
                    val editorMax = if (isLargeScreen) 900.dp else 500.dp
                    OutlinedTextField(
                        value = currentNote,
                        onValueChange = {
                            currentNote = it
                            hasChanges = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 300.dp, max = editorMax),
                        placeholder = {
                            Text(
                                stringResource(R.string.quick_notes_markdown_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                } else {
                    // Normal mode: editable text field with clickable links overlay
                    if (currentNote.isNotEmpty()) {
                        val displayNote = expandCallsignPlaceholder(currentNote, callsign)
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // Editable text field (background layer) with visual transformation
                            OutlinedTextField(
                                value = currentNote,
                                onValueChange = {
                                    currentNote = it
                                    hasChanges = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 200.dp),
                                placeholder = {
                                    Text(
                                        stringResource(R.string.quick_notes_placeholder),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = 20.sp
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                visualTransformation = LinksVisualTransformation(currentNote)
                            )
                            
                            // Clickable links overlay (foreground layer)
                            // Build overlay using the transformed text structure (with pin emoji replaced)
                            val (transformedText, _) = buildTransformedStringAndRanges(displayNote)
                            val overlayString = buildAnnotatedString {
                                val regex = Regex("\\[([^]]+)]\\(internal://open\\?file=([^)&]+)(?:&page=(\\d+))?\\)")
                                var transformedIndex = 0
                                var originalIndex = 0
                                
                                regex.findAll(displayNote).forEach { match ->
                                    val range = match.range
                                    // Add spacing for text before this link
                                    if (range.first > originalIndex) {
                                        val beforeText = displayNote.substring(originalIndex, range.first)
                                        append(beforeText)
                                        transformedIndex += beforeText.length
                                    }
                                    
                                    // Add single space where pin emoji is (TextField shows emoji, overlay shows space + link)
                                    append(" ")
                                    transformedIndex += 1
                                    
                                    // Add clickable link label right after the space
                                    val label = match.groups[1]?.value ?: ""
                                    val fileEnc = match.groups[2]?.value ?: ""
                                    val page = match.groups[3]?.value ?: ""
                                    val annotationValue = "$fileEnc|$page"
                                    val start = length
                                    append(label)
                                    val end = length
                                    addStringAnnotation(
                                        tag = "OPEN_LINK",
                                        annotation = annotationValue,
                                        start = start,
                                        end = end
                                    )
                                    addStyle(
                                        SpanStyle(
                                            color = Color(0xFF0066CC),
                                            textDecoration = TextDecoration.Underline
                                        ),
                                        start,
                                        end
                                    )
                                    originalIndex = range.last + 1
                                }
                                
                                // Add remaining text after last link
                                if (originalIndex < displayNote.length) {
                                    append(displayNote.substring(originalIndex))
                                }
                            }
                            
                            if (overlayString.text.trim().isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    @Suppress("DEPRECATION")
                                    ClickableText(
                                        text = overlayString,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 20.sp
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { offset ->
                                            overlayString.getStringAnnotations("OPEN_LINK", offset, offset)
                                                .firstOrNull()?.let { annotation ->
                                                    val parts = annotation.item.split("|")
                                                    val filePath = URLDecoder.decode(
                                                        parts.getOrNull(0) ?: "",
                                                        "UTF-8"
                                                    )
                                                    val rawPage = parts.getOrNull(1)?.toIntOrNull()
                                                    val pageNumber = rawPage?.let { if (it > 0) it - 1 else it }

                                                    onOpenDocument?.invoke(filePath, pageNumber)
                                                    onDismiss()
                                                }
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // Empty state
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp)
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.NoteAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = stringResource(R.string.quick_notes_empty_state),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.quick_notes_empty_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            // Note tabs (horizontal scrollable) - styled as sticky note tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp)
                    .offset(y = (-1).dp),
                horizontalArrangement = Arrangement.Start
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    // Filter notes based on search query
                    val filteredNotes = if (searchQuery.isBlank()) {
                        notes
                    } else {
                        notes.filter { note ->
                            note.title.contains(searchQuery, ignoreCase = true) ||
                            note.content.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    items(filteredNotes) { note ->
                        Surface(
                            onClick = { noteManager.setActiveNote(note.id) },
                            modifier = Modifier
                                .height(32.dp),
                            shape = MaterialTheme.shapes.small.copy(
                                topStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                                topEnd = androidx.compose.foundation.shape.CornerSize(0.dp)
                            ),
                            color = if (note.id == activeNoteId) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            tonalElevation = if (note.id == activeNoteId) 3.dp else 1.dp,
                            shadowElevation = if (note.id == activeNoteId) 2.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (note.title.isNotBlank()) note.title else stringResource(R.string.quick_notes_untitled),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (note.id == activeNoteId) FontWeight.Bold else FontWeight.Normal
                                )
                                if (note.id == activeNoteId) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable {
                                                renameTargetId = note.id
                                                renameText = note.title
                                                showRenameDialog = true
                                            }
                                    )
                                }
                                if (note.id == activeNoteId && notes.size > 1) {
                                    // Two-step delete: first click arms, second click confirms
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = if (pendingDeleteNoteId == note.id) stringResource(R.string.quick_notes_delete_confirm) else stringResource(R.string.action_delete),
                                        modifier = Modifier
                                            .size(12.dp)
                                                .clickable {
                                                    if (pendingDeleteNoteId == note.id) {
                                                        noteManager.removeNote(note.id)
                                                        pendingDeleteNoteId = null
                                                    } else {
                                                        pendingDeleteNoteId = note.id
                                                        Toast.makeText(context, context.getString(R.string.quick_notes_delete_confirm), Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                        tint = if (pendingDeleteNoteId == note.id) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }

                    // Add note button
                    item {
                        Surface(
                            onClick = {
                                val id = noteManager.addNote(title = context.getString(R.string.quick_notes_new_note))
                                noteManager.setActiveNote(id)
                            },
                            modifier = Modifier
                                .height(32.dp),
                            shape = MaterialTheme.shapes.small.copy(
                                topStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                                topEnd = androidx.compose.foundation.shape.CornerSize(0.dp)
                            ),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.quick_notes_new_note),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    stringResource(R.string.quick_notes_new_button),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Quick input section (separate card, always visible)
            Spacer(modifier = Modifier.height(12.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // Template chips row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = quickInputMode == "text",
                            onClick = {
                                if (quickInputMode != "text") {
                                    quickInputMode = "text"
                                    newTextInput = TextFieldValue("")
                                }
                            },
                            label = { Text(stringResource(R.string.quick_notes_field_text), style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = quickInputMode == "number",
                            onClick = {
                                if (quickInputMode != "number") {
                                    quickInputMode = "number"
                                    newTextInput = TextFieldValue("")
                                }
                            },
                            label = { Text(stringResource(R.string.quick_notes_field_numbers), style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = quickInputMode == "freq",
                            onClick = {
                                if (quickInputMode != "freq") {
                                    quickInputMode = "freq"
                                    val tmpl = context.getString(R.string.quick_notes_com_prefix)
                                    newTextInput = TextFieldValue(tmpl, selection = TextRange(tmpl.length))
                                    focusRequester.requestFocus()
                                }
                            },
                            label = { Text(stringResource(R.string.quick_notes_field_frequency), style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = quickInputMode == "coord",
                            onClick = {
                                if (quickInputMode != "coord") {
                                    quickInputMode = "coord"
                                    val tmpl = context.getString(R.string.quick_notes_coord_template)
                                    newTextInput = TextFieldValue(tmpl, selection = TextRange(2))
                                    focusRequester.requestFocus()
                                }
                            },
                            label = { Text(stringResource(R.string.quick_notes_field_coordinates), style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = quickInputMode == "fluglage",
                            onClick = {
                                if (quickInputMode != "fluglage") {
                                    quickInputMode = "fluglage"
                                    val tmpl = context.getString(R.string.quick_notes_flight_template)
                                    newTextInput = TextFieldValue(tmpl, selection = TextRange(9))
                                    focusRequester.requestFocus()
                                }
                            },
                            label = { Text(stringResource(R.string.quick_notes_field_flight_state), style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = quickInputMode == "time",
                            onClick = {
                                if (quickInputMode != "time") {
                                    quickInputMode = "time"
                                    val now = java.time.LocalTime.now()
                                    val tmpl = "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')} "
                                    newTextInput = TextFieldValue(tmpl, selection = TextRange(0, tmpl.length))
                                    focusRequester.requestFocus()
                                }
                            },
                            label = { Text(stringResource(R.string.quick_notes_field_time), style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = quickInputMode == "draw",
                            onClick = {
                                if (quickInputMode != "draw") {
                                    quickInputMode = "draw"
                                }
                            },
                            label = { Text(stringResource(R.string.quick_notes_field_draw), style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    // Quick input field with smart keyboard
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val keyboardType = when (quickInputMode) {
                            stringResource(R.string.quick_notes_field_coordinates),
                            stringResource(R.string.quick_notes_field_frequency),
                            stringResource(R.string.quick_notes_field_numbers),
                            stringResource(R.string.quick_notes_field_flight_state) -> KeyboardType.Number
                            stringResource(R.string.quick_notes_field_time) -> KeyboardType.Number
                            else -> KeyboardType.Text
                        }

                        OutlinedTextField(
                            value = newTextInput,
                            onValueChange = { newVal ->
                                if (quickInputMode == "time") {
                                    // Allow only digits and colon for time input
                                    val filtered = newVal.text.filter { it.isDigit() || it == ':' }
                                    // Format as HH:MM
                                    val digits = filtered.filter { it.isDigit() }
                                    val formatted = when {
                                        digits.isEmpty() -> ""
                                        digits.length == 1 -> digits
                                        digits.length == 2 -> digits
                                        digits.length == 3 -> "${digits.take(2)}:${digits.drop(2)}"
                                        else -> "${digits.take(2)}:${digits.drop(2).take(2)}"
                                    }
                                    newTextInput = TextFieldValue(formatted, selection = TextRange(formatted.length))
                                } else if (quickInputMode == "coord") {
                                    // Extract all digits from the new input
                                    val allDigits = newVal.text.filter { it.isDigit() }

                                    // Maximum 12 digits (6 for N, 6 for E), minimum 0
                                    val digits = allDigits.take(12).ifEmpty { "" }

                                    // North: first 6 digits, pad with spaces
                                    val northDigits = digits.take(6).padEnd(6, ' ')
                                    // East: next 6 digits, pad with spaces
                                    val eastDigits = if (digits.length > 6) {
                                        digits.drop(6).take(6).padEnd(6, ' ')
                                    } else {
                                        "      " // 6 Leerzeichen
                                    }

                                    // Format coordinates as N 48°12'34" E 012°34'56"
                                    fun formatCoordPart(s: String): String {
                                        val safe = s.padEnd(6, ' ')
                                        val deg = safe.substring(0, 2)
                                        val min = safe.substring(2, 4)
                                        val sec = safe.substring(4, 6)

                                        val sb = StringBuilder()

                                        // Degrees (show ° when two digits present)
                                        if (deg.trim().isNotEmpty()) {
                                            sb.append(deg.filter { it != ' ' })
                                            if (!deg.contains(' ')) sb.append('°')
                                        } else {
                                            sb.append("  ")
                                        }

                                        // Minutes (show ' when two digits present)
                                        if (min.trim().isNotEmpty()) {
                                            sb.append(min.filter { it != ' ' })
                                            if (!min.contains(' ')) sb.append('\'')
                                        }

                                        // Seconds (show " when two digits present)
                                        if (sec.trim().isNotEmpty()) {
                                            sb.append(sec.filter { it != ' ' })
                                            if (!sec.contains(' ')) sb.append('"')
                                        }

                                        return sb.toString().padEnd(9, ' ')
                                    }

                                    val northFmt = formatCoordPart(northDigits)
                                    val eastFmt = formatCoordPart(eastDigits)
                                    val formatted = context.getString(R.string.quick_notes_coord_format, northFmt, eastFmt)

                                    // Determine cursor: after last entered digit; if symbol follows it, place after symbol
                                    val numDigits = digits.length
                                    val lastDigitIndex = if (numDigits > 0) {
                                        var count = 0
                                        var idx = -1
                                        for (i in formatted.indices) {
                                            if (formatted[i].isDigit()) {
                                                count++
                                                if (count == numDigits) {
                                                    idx = i
                                                    break
                                                }
                                            }
                                        }
                                        idx
                                    } else -1

                                    val cursorPos = if (lastDigitIndex >= 0) {
                                        val next = if (lastDigitIndex + 1 < formatted.length) formatted[lastDigitIndex + 1] else '\u0000'
                                        if (next == '°' || next == '\'' || next == '"') lastDigitIndex + 2 else lastDigitIndex + 1
                                    } else {
                                        2 // after "N "
                                    }

                                    newTextInput = TextFieldValue(
                                        text = formatted,
                                        selection = TextRange(cursorPos.coerceIn(0, formatted.length))
                                    )
                                } else if (quickInputMode == "freq") {
                                    val formatted = formatComInputToDot(newVal.text)
                                    newTextInput = TextFieldValue(formatted, selection = TextRange(formatted.length))
                                } else if (quickInputMode == "number") {
                                    val filtered = newVal.text.filter { it.isDigit() }
                                    newTextInput = TextFieldValue(filtered, selection = TextRange(filtered.length))
                                } else if (quickInputMode == "fluglage") {
                                    // Extract all digits
                                    val allDigits = newVal.text.filter { it.isDigit() }

                                    // Maximum 11 digits (5 FL + 3 Speed + 3 HDG)
                                    val digits = allDigits.take(11)

                                    // FL: first 5 digits (e.g., 02000 for 20000ft)
                                    val flDigits = digits.take(5).padEnd(5, ' ')
                                    // Speed: next 3 digits
                                    val speedDigits = if (digits.length > 5) {
                                        digits.drop(5).take(3).padEnd(3, ' ')
                                    } else {
                                        "   "
                                    }
                                    // HDG: last 3 digits
                                    val hdgDigits = if (digits.length > 8) {
                                        digits.drop(8).take(3).padEnd(3, ' ')
                                    } else {
                                        "   "
                                    }

                                    // Format: "Alt: FL DDDDD / Speed: DDD / HDG: DDD"
                                    val formatted = buildString {
                                        append(context.getString(R.string.quick_notes_alt_prefix))
                                        append(flDigits)
                                        append(context.getString(R.string.quick_notes_speed_separator))
                                        append(speedDigits)
                                        append(context.getString(R.string.quick_notes_hdg_separator))
                                        append(hdgDigits)
                                    }

                                    // Cursor: after last entered digit (robust)
                                    val numDigits = digits.length
                                    val lastDigitIndex = if (numDigits > 0) {
                                        var count = 0
                                        var idx = -1
                                        for (i in formatted.indices) {
                                            if (formatted[i].isDigit()) {
                                                count++
                                                if (count == numDigits) {
                                                    idx = i
                                                    break
                                                }
                                            }
                                        }
                                        idx
                                    } else -1

                                    val altPrefix = context.getString(R.string.quick_notes_alt_prefix)
                                    val cursorPos = if (lastDigitIndex >= 0) lastDigitIndex + 1 else formatted.indexOf(altPrefix) + altPrefix.length

                                    newTextInput = TextFieldValue(
                                        text = formatted,
                                        selection = TextRange(cursorPos.coerceIn(0, formatted.length))
                                    )
                                } else {
                                    newTextInput = newVal
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        when (quickInputMode) {
                                            "coord" -> {
                                                val txt = newTextInput.text
                                                val digits = txt.filter { it.isDigit() }
                                                val numDigits = digits.length

                                                val lastDigitIndex = if (numDigits > 0) {
                                                    var count = 0
                                                    var idx = -1
                                                    for (i in txt.indices) {
                                                        if (txt[i].isDigit()) {
                                                            count++
                                                            if (count == numDigits) {
                                                                idx = i
                                                                break
                                                            }
                                                        }
                                                    }
                                                    idx
                                                } else -1

                                                val pos = if (lastDigitIndex >= 0) {
                                                    val next = if (lastDigitIndex + 1 < txt.length) txt[lastDigitIndex + 1] else '\u0000'
                                                    if (next == '°' || next == '\'' || next == '"') lastDigitIndex + 2 else lastDigitIndex + 1
                                                } else {
                                                    2
                                                }

                                                newTextInput = newTextInput.copy(
                                                    selection = TextRange(pos.coerceIn(0, txt.length))
                                                )
                                            }
                                            "fluglage" -> {
                                                val txt = newTextInput.text
                                                val digits = txt.filter { it.isDigit() }
                                                val numDigits = digits.length

                                                val lastDigitIndex = if (numDigits > 0) {
                                                    var count = 0
                                                    var idx = -1
                                                    for (i in txt.indices) {
                                                        if (txt[i].isDigit()) {
                                                            count++
                                                            if (count == numDigits) {
                                                                idx = i
                                                                break
                                                            }
                                                        }
                                                    }
                                                    idx
                                                } else -1

                                                val altPrefix2 = context.getString(R.string.quick_notes_alt_prefix)
                                                val pos = if (lastDigitIndex >= 0) lastDigitIndex + 1 else txt.indexOf(altPrefix2) + altPrefix2.length

                                                newTextInput = newTextInput.copy(
                                                    selection = TextRange(pos.coerceIn(0, txt.length))
                                                )
                                            }
                                        }
                                    }
                                },
                            placeholder = {
                                Text(when (quickInputMode) {
                                    stringResource(R.string.quick_notes_field_coordinates) -> stringResource(R.string.quick_notes_coord_placeholder)
                                    stringResource(R.string.quick_notes_field_frequency) -> stringResource(R.string.quick_notes_freq_placeholder)
                                    stringResource(R.string.quick_notes_field_time) -> stringResource(R.string.quick_notes_time_placeholder)
                                    stringResource(R.string.quick_notes_field_numbers) -> stringResource(R.string.quick_notes_numbers_placeholder)
                                    stringResource(R.string.quick_notes_field_flight_state) -> stringResource(R.string.quick_notes_flight_placeholder)
                                    else -> stringResource(R.string.quick_notes_text_placeholder)
                                })
                            },
                            maxLines = 2,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = keyboardType,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (newTextInput.text.isNotEmpty()) {
                                        currentNote = if (currentNote.isEmpty()) {
                                            newTextInput.text
                                        } else {
                                            "$currentNote\n${newTextInput.text}"
                                        }
                                        newTextInput = TextFieldValue("")
                                        hasChanges = true
                                        focusManager.clearFocus()
                                    }
                                }
                            )
                        )

                        // Drawing canvas area when draw mode is active
                        if (quickInputMode == "draw") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .height(220.dp)
                                        .fillMaxWidth()
                                        .background(Color(0xFFFDFDFD))
                                        .pointerInput(eraseMode) {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    if (!eraseMode) {
                                                        currentStroke = listOf(PointDto(offset.x, offset.y))
                                                    } else {
                                                        // Start erase path
                                                        currentStroke = listOf(PointDto(offset.x, offset.y))
                                                    }
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    currentStroke = currentStroke + PointDto(change.position.x, change.position.y)
                                                    
                                                    if (eraseMode) {
                                                        // Erase strokes that intersect with touch path
                                                        val eraseRadius = 30f
                                                        val touchPoint = PointDto(change.position.x, change.position.y)
                                                        strokesState.removeAll { stroke ->
                                                            stroke.any { point ->
                                                                val dx = point.x - touchPoint.x
                                                                val dy = point.y - touchPoint.y
                                                                kotlin.math.sqrt(dx * dx + dy * dy) < eraseRadius
                                                            }
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    if (!eraseMode && currentStroke.isNotEmpty()) {
                                                        strokesState.add(currentStroke)
                                                        currentStroke = emptyList()
                                                        try {
                                                            // Save immediately on finger lift to avoid data loss
                                                            val id = activeNoteId
                                                            if (id != null) {
                                                                val drawingJson = if (strokesState.isEmpty()) null else json.encodeToString(strokesState.toList())
                                                                noteManager.saveDrawing(id, drawingJson)
                                                                drawingDirty = false
                                                            } else {
                                                                drawingDirty = true
                                                            }
                                                        } catch (_: Exception) {
                                                            drawingDirty = true
                                                        }
                                                    } else if (eraseMode) {
                                                        // Save after erasing
                                                        currentStroke = emptyList()
                                                        try {
                                                            val id = activeNoteId
                                                            if (id != null) {
                                                                val drawingJson = if (strokesState.isEmpty()) null else json.encodeToString(strokesState.toList())
                                                                noteManager.saveDrawing(id, drawingJson)
                                                                drawingDirty = false
                                                            }
                                                        } catch (_: Exception) {
                                                            drawingDirty = true
                                                        }
                                                    }
                                                },
                                                onDragCancel = {
                                                    currentStroke = emptyList()
                                                }
                                            )
                                        }
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val strokeStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                                        // Draw persisted strokes
                                        strokesState.forEach { stroke ->
                                            if (stroke.isNotEmpty()) {
                                                val path = androidx.compose.ui.graphics.Path().apply {
                                                    moveTo(stroke[0].x, stroke[0].y)
                                                    for (p in stroke.drop(1)) lineTo(p.x, p.y)
                                                }
                                                drawPath(path, color = Color.Black, style = strokeStyle)
                                            }
                                        }

                                        // Draw current stroke (different color in erase mode for visual feedback)
                                        if (currentStroke.isNotEmpty() && !eraseMode) {
                                            val path = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(currentStroke[0].x, currentStroke[0].y)
                                                for (p in currentStroke.drop(1)) lineTo(p.x, p.y)
                                            }
                                            drawPath(path, color = Color.Black, style = strokeStyle)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        val id = activeNoteId
                                        val drawingJson = if (strokesState.isEmpty()) null else json.encodeToString(strokesState.toList())
                                        if (id != null) {
                                            noteManager.saveDrawing(id, drawingJson)
                                            drawingDirty = false
                                            Toast.makeText(context, context.getString(R.string.quick_notes_drawing_saved), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.quick_notes_no_active_note), Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.action_save))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.action_save))
                                    }

                                    FilledTonalButton(onClick = {
                                        eraseMode = !eraseMode
                                    }) {
                                        Icon(
                                            if (eraseMode) Icons.Default.Edit else Icons.Default.Delete,
                                            contentDescription = if (eraseMode) stringResource(R.string.quick_notes_draw_button) else stringResource(R.string.quick_notes_erase_button)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (eraseMode) stringResource(R.string.quick_notes_draw_button) else stringResource(R.string.quick_notes_erase_button))
                                    }

                                    OutlinedButton(onClick = {
                                        strokesState.clear()
                                        currentStroke = emptyList()
                                        drawingCleared = true
                                        drawingDirty = false
                                        noteManager.clearDrawing(activeNoteId)
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.action_clear))
                                    }
                                }
                            }
                        }

                        FilledTonalIconButton(
                            onClick = {
                                if (newTextInput.text.isNotEmpty()) {
                                    currentNote = if (currentNote.isEmpty()) {
                                        newTextInput.text
                                    } else {
                                        "$currentNote\n${newTextInput.text}"
                                    }
                                    newTextInput = TextFieldValue("")
                                    hasChanges = true
                                }
                            },
                            enabled = newTextInput.text.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column {
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Delete button with two-step confirmation
                        OutlinedButton(
                        onClick = {
                            if (pendingDeleteCurrentNoteConfirm) {
                                currentNote = ""
                                hasChanges = false
                                noteManager.clearNote()
                                pendingDeleteCurrentNoteConfirm = false
                            } else {
                                pendingDeleteCurrentNoteConfirm = true
                                Toast.makeText(context, context.getString(R.string.quick_notes_delete_confirm), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        enabled = currentNote.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = if (pendingDeleteCurrentNoteConfirm) stringResource(R.string.quick_notes_delete_confirm) else stringResource(R.string.action_delete),
                            modifier = Modifier.size(14.dp),
                            tint = if (pendingDeleteCurrentNoteConfirm) MaterialTheme.colorScheme.error else LocalContentColor.current
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.labelSmall)
                    }

                    // Smaller save button
                    Button(
                        onClick = {
                            noteManager.saveNote(currentNote)
                            hasChanges = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        enabled = hasChanges
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_save), style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Status indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        hasChanges -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.quick_notes_not_saved),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        currentNote.isNotEmpty() -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.quick_notes_saved),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }

                    // Note count
                    if (notes.isNotEmpty()) {
                        Text(
                            text = "${notes.size} ${if (notes.size == 1) stringResource(R.string.quick_notes_count_singular) else stringResource(R.string.quick_notes_count_plural)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            }
        }
    }
}