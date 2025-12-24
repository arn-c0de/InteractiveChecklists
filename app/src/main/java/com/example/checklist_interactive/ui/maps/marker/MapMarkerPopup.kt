package com.example.checklist_interactive.ui.maps.marker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.*
import androidx.compose.material3.AssistChip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.example.checklist_interactive.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import kotlinx.coroutines.isActive
import com.example.checklist_interactive.data.tactical.LocationEntity
import com.example.checklist_interactive.data.tactical.RunwayEntity
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapMarkerPopup(
    location: LocationEntity,
    runways: List<RunwayEntity>,
    onClose: () -> Unit,
    onManage: () -> Unit,
    onRunwayClick: (RunwayEntity) -> Unit = {},
    onSetRoute: (LocationEntity) -> Unit = {}
) {
    val context = LocalContext.current
    // Persisted sheet fraction + opacity like DataPadPopup
    val prefs = context.getSharedPreferences("marker_popup_prefs", android.content.Context.MODE_PRIVATE)
    val KEY_SHEET_FRACTION = "marker_sheet_fraction"
    val savedFraction = prefs.getFloat(KEY_SHEET_FRACTION, 0.6f)
    val sheetMin = 0.25f
    val sheetMax = 0.95f
    var sheetFraction by rememberSaveable { mutableStateOf(savedFraction.coerceIn(sheetMin, sheetMax)) }

    val KEY_SHEET_OPACITY = "marker_sheet_opacity"
    val savedOpacity = prefs.getFloat(KEY_SHEET_OPACITY, 1.0f)
    var sheetOpacity by rememberSaveable { mutableStateOf(savedOpacity.coerceIn(0.25f, 1.0f)) }
    var showOpacitySlider by remember { mutableStateOf(false) }

    // Persist opacity when changed
    LaunchedEffect(sheetOpacity) {
        prefs.edit().putFloat(KEY_SHEET_OPACITY, sheetOpacity).apply()
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val sheetHeightDp = (configuration.screenHeightDp.toFloat() * sheetFraction).dp
    val view = LocalView.current

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

        // Keep applying occasionally to override ModalBottomSheet's behavior only while the sheet is visible
        if (sheetState.isVisible) {
            while (isActive && sheetState.isVisible) {
                hideSystemUI()
                kotlinx.coroutines.delay(750L)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
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

        // Also keep ensuring hide while it's visible (loop for resilience).
        LaunchedEffect(dialogView, sheetState.isVisible) {
            if (sheetState.isVisible) {
                val dialogWindow = (dialogView.context as? android.app.Activity)?.window
                val dialogController = dialogWindow?.let { WindowCompat.getInsetsController(it, dialogView) }
                while (isActive && sheetState.isVisible) {
                    dialogController?.hide(WindowInsetsCompat.Type.systemBars())
                    kotlinx.coroutines.delay(750L)
                }
            }
        }

        Box(modifier = Modifier
            .fillMaxWidth()
            .height(sheetHeightDp)
            .padding(horizontal = 16.dp)
        ) {
            // Drag handle at top (drag vertically to resize and swipe down from the handle to dismiss)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(Unit) {
                        var dragAccum = 0f
                        detectDragGestures(
                            onDragStart = { dragAccum = 0f },
                            onDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Offset ->
                                // accumulate vertical displacement (positive = downward)
                                dragAccum += dragAmount.y
                                val screenPx = with(density) { configuration.screenHeightDp.dp.toPx() }
                                val fracDelta = dragAmount.y / screenPx
                                sheetFraction = (sheetFraction - fracDelta).coerceIn(sheetMin, sheetMax)
                            },
                            onDragEnd = {
                                // persist saved fraction
                                prefs.edit().putFloat(KEY_SHEET_FRACTION, sheetFraction).apply()
                                // If user swiped down sufficiently on the handle, dismiss the sheet
                                val threshold = with(density) { 64.dp.toPx() } // about 64dp downward to dismiss
                                if (dragAccum > threshold) {
                                    onClose()
                                }
                                dragAccum = 0f
                            },
                            onDragCancel = {
                                dragAccum = 0f
                            }
                        )
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(width = 64.dp, height = 6.dp)
                        .background(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small)
                )
            }

            // Main content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = location.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}", style = MaterialTheme.typography.bodySmall)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = { showOpacitySlider = !showOpacitySlider }, modifier = Modifier.padding(end = 8.dp)) {
                            Text(text = "${(sheetOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                        }

                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    }
                }

                AnimatedVisibility(visible = showOpacitySlider, enter = fadeIn(), exit = fadeOut()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
                        Text(text = stringResource(R.string.map_nav_transparency) + ": ${(sheetOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = sheetOpacity,
                            onValueChange = { sheetOpacity = it },
                            valueRange = 0.25f..1.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Extract string resources before remember block
                val strMarkerType = stringResource(R.string.map_marker_type)
                val strIcao = stringResource(R.string.map_marker_icao)
                val strIata = stringResource(R.string.map_marker_iata)
                val strCountry = stringResource(R.string.map_marker_country)
                val strRegion = stringResource(R.string.map_marker_region)
                val strTimezone = stringResource(R.string.map_marker_timezone)
                val strElevation = stringResource(R.string.map_marker_elevation)
                val strUnit = stringResource(R.string.map_marker_unit)
                val strThreat = stringResource(R.string.map_marker_threat)
                val strStrength = stringResource(R.string.map_marker_strength)
                val strSource = stringResource(R.string.map_marker_source)
                val strVerified = stringResource(R.string.map_marker_verified)
                val strVerifiedYes = stringResource(R.string.map_marker_verified_yes)
                val strVerifiedNo = stringResource(R.string.map_marker_verified_no)
                val strVerifiedAt = stringResource(R.string.map_marker_verified_at)
                val strTags = stringResource(R.string.map_marker_tags)
                val strRunways = stringResource(R.string.map_marker_runways)

                // Info grid (two columns) — expanded to include tactical/admin metadata, source and tags
                val infoItems = remember(location, runways, strMarkerType, strIcao, strIata, strCountry, strRegion, strTimezone, strElevation, strUnit, strThreat, strStrength, strSource, strVerified, strVerifiedYes, strVerifiedNo, strVerifiedAt, strTags, strRunways) {
                    mutableListOf<Pair<String, String>>().apply {
                        // Basic identifiers
                        location.markerType?.takeIf { it.isNotEmpty() }?.let { add(strMarkerType to it.replace('_', ' ')) }
                        location.icao?.takeIf { it.isNotEmpty() }?.let { add(strIcao to it) }
                        location.iata?.takeIf { it.isNotEmpty() }?.let { add(strIata to it) }

                        // Geography / admin
                        location.country?.takeIf { it.isNotEmpty() }?.let { add(strCountry to it) }
                        location.region?.takeIf { it.isNotEmpty() }?.let { add(strRegion to it) }
                        location.timezone?.takeIf { it.isNotEmpty() }?.let { add(strTimezone to it) }

                        // Elevation
                        location.elevationM?.let { add(strElevation to context.getString(R.string.map_marker_elevation_m, String.format(java.util.Locale.getDefault(), "%.0f", it))) }

                        // Tactical / unit fields (if present)
                        location.unitType?.takeIf { it.isNotEmpty() }?.let { add(strUnit to it) }
                        location.threatLevel?.let { add(strThreat to it.toString()) }
                        location.strength?.let { add(strStrength to it.toString()) }

                        // Source and verification
                        location.source?.takeIf { it.isNotEmpty() }?.let { add(strSource to it) }
                        location.verified?.let { add(strVerified to if (it == 1) strVerifiedYes else strVerifiedNo) }
                        location.lastVerifiedAt?.takeIf { it.isNotEmpty() }?.let { add(strVerifiedAt to it) }

                        // Tags (try JSON array, fallback to comma-separated string)
                        location.tags?.takeIf { it.isNotEmpty() }?.let { rawTags ->
                            val parsed = try {
                                val arr = org.json.JSONArray(rawTags)
                                (0 until arr.length()).map { i -> arr.optString(i) }.filter { it.isNotEmpty() }
                            } catch (_: Exception) {
                                rawTags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                            }
                            if (parsed.isNotEmpty()) add(strTags to parsed.joinToString(", "))
                        }

                        // Runways count if provided by the caller
                        if (runways.isNotEmpty()) add(strRunways to runways.size.toString())
                    }
                }

                if (infoItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column {
                        infoItems.chunked(2).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { (k, v) ->
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(text = v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    }
                                }
                                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }

                if (location.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = location.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Frequencies (parsed JSON -> chips, fallback to raw text)
                val freqs = remember(location.frequencies) {
                    try {
                        val f = location.frequencies?.trim()
                        if (f != null && f.startsWith("{")) {
                            val obj = JSONObject(f)
                            val keys = obj.keys().asSequence().toList()
                            keys.map { k -> k to obj.optString(k) }
                        } else emptyList()
                    } catch (_: Exception) {
                        emptyList<Pair<String, String>>()
                    }
                }

                if (freqs.isNotEmpty() || (!location.frequencies.isNullOrEmpty())) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = stringResource(R.string.map_marker_frequencies), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    if (freqs.isNotEmpty()) {
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            freqs.forEach { (k, v) ->
                                AssistChip(onClick = { /*noop*/ }, label = { Text(text = "$k: $v") })
                            }
                        }
                    } else {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(text = location.frequencies ?: "", modifier = Modifier.padding(8.dp), fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                // Runways - compact cards list
                if (runways.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = stringResource(R.string.map_marker_runways), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        runways.forEach { rw ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRunwayClick(rw) },
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = rw.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        val length = rw.lengthM?.toString() ?: rw.lengthFt?.toString() ?: stringResource(R.string.datapad_na_symbol)
                                        Text(text = stringResource(R.string.map_marker_elevation_m, length), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(text = rw.surface ?: stringResource(R.string.datapad_not_available), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(text = stringResource(R.string.map_marker_hdg_format, rw.headingDeg?.let { String.format("%.0f°", it) } ?: stringResource(R.string.map_marker_hdg_na)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        if (!rw.ilsFrequency.isNullOrEmpty()) {
                                            AssistChip(onClick = { /*noop*/ }, label = { Text(text = stringResource(R.string.map_marker_ils_format, rw.ilsFrequency)) })
                                        }

                                        if (rw.hasLighting == 1) {
                                            AssistChip(onClick = { /*noop*/ }, label = { Text(text = stringResource(R.string.map_marker_lighting)) })
                                        }
                                    }

                                    if (!rw.notes.isNullOrEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = rw.notes ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onManage, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.map_marker_manage))
                    }

                    Button(onClick = onClose, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            }
        }
    }
}