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
import androidx.compose.material.icons.filled.Article
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
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.*
import android.widget.Toast
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.View
import com.example.checklist_interactive.data.quicknotes.QuickNoteManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.map
import java.net.URLDecoder
import java.net.URLEncoder

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

        val startIndex = builder.length
        builder.append(label)
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
            androidx.core.view.ViewCompat.getWindowInsetsController(view)?.let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                // systemBarsBehavior exists on WindowInsetsControllerCompat; try set if available
                try {
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } catch (_: Throwable) {
                }
            }

            // Try rootView as well in case the modal sheet uses a different attach point
            androidx.core.view.ViewCompat.getWindowInsetsController(view.rootView)?.let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                try {
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } catch (_: Throwable) {
                }
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

    // Save on dispose
    DisposableEffect(Unit) {
        onDispose {
            if (hasChanges) {
                noteManager.saveNote(currentNote)
            }
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
            val controller = ViewCompat.getWindowInsetsController(dialogView)
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
                    val c = ViewCompat.getWindowInsetsController(v)
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
                val dialogController = ViewCompat.getWindowInsetsController(dialogView)
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
                        Icons.Default.Article,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Quick Notes",
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
                                contentDescription = "Search"
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
                                val label = if (currentPageNumber != null) "${currentDocumentName} (Page ${currentPageNumber + 1})" else currentDocumentName
                                val linkText = "\n📌 [$label]($uri)\n"
                                val newContent = currentNote + linkText
                                currentNote = newContent
                                hasChanges = true
                            }
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = "Pin document")
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
                        text = "Opacity: ${(sheetOpacity * 100).toInt()}% (min 25%)",
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
                    placeholder = { Text("Search notes...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
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
                Text(text = "Flight Info", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = {
                    flightExpanded = !flightExpanded
                    noteManager.saveFlightInfoExpanded(flightExpanded)
                }) {
                    Icon(
                        imageVector = if (flightExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (flightExpanded) "Collapse" else "Expand"
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
                    Text(text = "${callsign.ifBlank { "-" }}  | Status: ${flightStatus.ifBlank { "Idle" }}  | COM1: ${com1.ifBlank { "-" }} ${com1Mode}  | COM2: ${com2.ifBlank { "-" }} ${com2Mode}", style = MaterialTheme.typography.bodyMedium)
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
                                    label = { Text("CALLSIGN") },
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
                                label = { Text("COM1") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(140.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(selected = com1Mode == "FM", onClick = { com1Mode = "FM" }, label = { Text("FM") })
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(selected = com1Mode == "AM", onClick = { com1Mode = "AM" }, label = { Text("AM") })
                        }

                        // COM2 column
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = com2,
                                onValueChange = { com2 = formatComInputToDot(it) },
                                label = { Text("COM2") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(140.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(selected = com2Mode == "FM", onClick = { com2Mode = "FM" }, label = { Text("FM") })
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(selected = com2Mode == "AM", onClick = { com2Mode = "AM" }, label = { Text("AM") })
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
                    title = { Text("Rename Note") },
                    text = {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text("Title") },
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
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = false }) {
                            Text("Cancel")
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
                    text = if (showAdvancedEditor) "Markdown Editor" else "Note",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = !showAdvancedEditor,
                        onClick = { showAdvancedEditor = false },
                        label = { Text("Normal", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = showAdvancedEditor,
                        onClick = { showAdvancedEditor = true },
                        label = { Text("Markdown", style = MaterialTheme.typography.labelSmall) }
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
                                "Markdown Editor\n\n" +
                                "Links: [Text](internal://open?file=...)\n" +
                                "**Bold**, *Italic*",
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
                    // Normal mode with clickable links
                    if (currentNote.isNotEmpty()) {
                        val displayNote = expandCallsignPlaceholder(currentNote, callsign)
                        val annotatedString = buildAnnotatedStringWithLinks(displayNote)
                        @Suppress("DEPRECATION")
                        ClickableText(
                            text = annotatedString,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                            onClick = { offset ->
                                annotatedString.getStringAnnotations("OPEN_LINK", offset, offset)
                                    .firstOrNull()?.let { annotation ->
                                        val parts = annotation.item.split("|")
                                        val filePath = URLDecoder.decode(
                                            parts.getOrNull(0) ?: "",
                                            "UTF-8"
                                        )
                                        // Normalize clicked page number to 0-based index (allow stored links to be 1-based)
                                        val rawPage = parts.getOrNull(1)?.toIntOrNull()
                                        val pageNumber = rawPage?.let { if (it > 0) it - 1 else it }

                                        onOpenDocument?.invoke(filePath, pageNumber)
                                        onDismiss()
                                    }
                            }
                        )
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
                                    Icons.Default.NoteAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "Note is empty",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Use quick input below",
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
                                    text = if (note.title.isNotBlank()) note.title else "Untitled",
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
                                        contentDescription = if (pendingDeleteNoteId == note.id) "Click again to confirm" else "Delete",
                                        modifier = Modifier
                                            .size(12.dp)
                                                .clickable {
                                                    if (pendingDeleteNoteId == note.id) {
                                                        noteManager.removeNote(note.id)
                                                        pendingDeleteNoteId = null
                                                    } else {
                                                        pendingDeleteNoteId = note.id
                                                        Toast.makeText(context, "Press again to delete", Toast.LENGTH_SHORT).show()
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
                                val id = noteManager.addNote(title = "New Note")
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
                                    contentDescription = "New note",
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "New",
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
                            label = { Text("Text", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = quickInputMode == "number",
                            onClick = {
                                if (quickInputMode != "number") {
                                    quickInputMode = "number"
                                    newTextInput = TextFieldValue("")
                                }
                            },
                            label = { Text("Numbers", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = quickInputMode == "freq",
                            onClick = {
                                if (quickInputMode != "freq") {
                                    quickInputMode = "freq"
                                    val tmpl = "COM: "
                                    newTextInput = TextFieldValue(tmpl, selection = TextRange(tmpl.length))
                                    focusRequester.requestFocus()
                                }
                            },
                            label = { Text("Frequency", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = quickInputMode == "coord",
                            onClick = {
                                if (quickInputMode != "coord") {
                                    quickInputMode = "coord"
                                    val tmpl = "N       E      "
                                    newTextInput = TextFieldValue(tmpl, selection = TextRange(2))
                                    focusRequester.requestFocus()
                                }
                            },
                            label = { Text("Coordinates", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = quickInputMode == "fluglage",
                            onClick = {
                                if (quickInputMode != "fluglage") {
                                    quickInputMode = "fluglage"
                                    val tmpl = "Alt: FL    / Speed:     / HDG:    "
                                    newTextInput = TextFieldValue(tmpl, selection = TextRange(9))
                                    focusRequester.requestFocus()
                                }
                            },
                            label = { Text("Flight State", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = quickInputMode == "time",
                            onClick = {
                                if (quickInputMode != "time") {
                                    quickInputMode = "time"
                                    val now = java.time.LocalTime.now()
                                    val tmpl = "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')} "
                                    newTextInput = TextFieldValue(tmpl, selection = TextRange(tmpl.length))
                                    focusRequester.requestFocus()
                                }
                            },
                            label = { Text("Time", style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    // Quick input field with smart keyboard
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val keyboardType = when (quickInputMode) {
                            "coord", "freq", "number", "fluglage" -> KeyboardType.Number
                            "time" -> KeyboardType.Number
                            else -> KeyboardType.Text
                        }

                        OutlinedTextField(
                            value = newTextInput,
                            onValueChange = { newVal ->
                                if (quickInputMode == "coord") {
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

                                    // Safe formatting as "N DD DD DD E DD DD DD"
                                    fun formatCoordPart(s: String): String {
                                        // Sicherstellen dass String mindestens 6 Zeichen hat
                                        val safe = s.padEnd(6, ' ')
                                        return buildString {
                                            append(safe.getOrNull(0) ?: ' ')
                                            append(safe.getOrNull(1) ?: ' ')
                                            append(' ')
                                            append(safe.getOrNull(2) ?: ' ')
                                            append(safe.getOrNull(3) ?: ' ')
                                            append(' ')
                                            append(safe.getOrNull(4) ?: ' ')
                                            append(safe.getOrNull(5) ?: ' ')
                                        }
                                    }

                                    val northFmt = formatCoordPart(northDigits)
                                    val eastFmt = formatCoordPart(eastDigits)
                                    val formatted = "N $northFmt E $eastFmt"

                                    // Cursor position based on number of entered digits
                                    val numDigits = digits.length
                                    val cursorPos = when (numDigits) {
                                        0 -> 2  // Nach "N "
                                        1 -> 3  // N X|
                                        2 -> 4  // N XX|
                                        3 -> 6  // N XX X|X (nach Leerzeichen)
                                        4 -> 7  // N XX XX|
                                        5 -> 9  // N XX XX X|X (nach Leerzeichen)
                                        6 -> 10 // N XX XX XX|
                                        7 -> formatted.indexOf('E') + 3  // E X|
                                        8 -> formatted.indexOf('E') + 4  // E XX|
                                        9 -> formatted.indexOf('E') + 6  // E XX X|X
                                        10 -> formatted.indexOf('E') + 7 // E XX XX|
                                        11 -> formatted.indexOf('E') + 9 // E XX XX X|X
                                        else -> formatted.indexOf('E') + 10 // E XX XX XX|
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

                                    // Maximum 11 digits (4 FL + 3 Speed + 3 HDG + 1 optional char)
                                    val digits = allDigits.take(10)

                                    // FL: first 4 digits (e.g., 0350)
                                    val flDigits = digits.take(4).padEnd(4, ' ')
                                    // Speed: next 3 digits
                                    val speedDigits = if (digits.length > 4) {
                                        digits.drop(4).take(3).padEnd(3, ' ')
                                    } else {
                                        "   "
                                    }
                                    // HDG: last 3 digits
                                    val hdgDigits = if (digits.length > 7) {
                                        digits.drop(7).take(3).padEnd(3, ' ')
                                    } else {
                                        "   "
                                    }

                                    // Format: "Alt: FL DDDD / Speed: DDD / HDG: DDD"
                                    val formatted = buildString {
                                        append("Alt: FL ")
                                        append(flDigits)
                                        append(" / Speed: ")
                                        append(speedDigits)
                                        append(" / HDG: ")
                                        append(hdgDigits)
                                    }

                                    // Cursor position based on number of digits
                                    val numDigits = digits.length
                                    val cursorPos = when {
                                        numDigits == 0 -> 9  // After "Alt: FL "
                                        numDigits <= 4 -> 9 + numDigits  // In FL area
                                        numDigits <= 7 -> 23 + (numDigits - 4)  // In speed area (after " / Speed: ")
                                        else -> 34 + (numDigits - 7)  // In HDG area (after " / HDG: ")
                                    }

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

                                                val pos = when (numDigits) {
                                                    0 -> 2  // After "N "
                                                    1 -> 3
                                                    2 -> 4
                                                    3 -> 6
                                                    4 -> 7
                                                    5 -> 9
                                                    6 -> 10
                                                    7 -> txt.indexOf('E') + 3
                                                    8 -> txt.indexOf('E') + 4
                                                    9 -> txt.indexOf('E') + 6
                                                    10 -> txt.indexOf('E') + 7
                                                    11 -> txt.indexOf('E') + 9
                                                    else -> txt.indexOf('E') + 10
                                                }

                                                newTextInput = newTextInput.copy(
                                                    selection = TextRange(pos.coerceIn(0, txt.length))
                                                )
                                            }
                                            "fluglage" -> {
                                                val txt = newTextInput.text
                                                val digits = txt.filter { it.isDigit() }
                                                val numDigits = digits.length

                                                val pos = when {
                                                    numDigits == 0 -> 9  // After "Alt: FL "
                                                    numDigits <= 4 -> 9 + numDigits
                                                    numDigits <= 7 -> 23 + (numDigits - 4)
                                                    else -> 34 + (numDigits - 7)
                                                }

                                                newTextInput = newTextInput.copy(
                                                    selection = TextRange(pos.coerceIn(0, txt.length))
                                                )
                                            }
                                        }
                                    }
                                },
                            placeholder = {
                                Text(when (quickInputMode) {
                                    "coord" -> "Enter numbers (e.g., 481234 for N48°12'34\")"
                                    "freq" -> "e.g., 122.500"
                                    "time" -> "e.g., 14:30"
                                    "number" -> "Enter numbers..."
                                    "fluglage" -> "Enter numbers (e.g., 0350250090 for FL350/250kt/HDG090)"
                                    else -> "Add text..."
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
                            Icon(Icons.Default.Add, contentDescription = "Add")
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
                                Toast.makeText(context, "Press again to delete", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        enabled = currentNote.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = if (pendingDeleteCurrentNoteConfirm) "Click again to confirm" else "Delete",
                            modifier = Modifier.size(14.dp),
                            tint = if (pendingDeleteCurrentNoteConfirm) MaterialTheme.colorScheme.error else LocalContentColor.current
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete", style = MaterialTheme.typography.labelSmall)
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
                        Text("Save", style = MaterialTheme.typography.labelSmall)
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
                                    text = "Not saved",
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
                                    text = "Saved",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }

                    // Note count
                    if (notes.isNotEmpty()) {
                        Text(
                            text = "${notes.size} ${if (notes.size == 1) "note" else "notes"}",
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
