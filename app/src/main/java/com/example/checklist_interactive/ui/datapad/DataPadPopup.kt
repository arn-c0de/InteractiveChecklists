package com.example.checklist_interactive.ui.datapad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.platform.LocalDensity
import com.example.checklist_interactive.data.datapad.FlightData
import kotlinx.coroutines.isActive
import kotlin.math.abs

/**
 * DataPad popup window displaying live flight information from UDP stream
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataPadPopup(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val manager = LocalDataPadManager.current
    val flightData by manager.flightData.collectAsState()
    val isConnected by manager.isConnected.collectAsState()
    val lastUpdateTime by manager.lastUpdateTime.collectAsState()
    val deviceIpAddress by manager.deviceIpAddress.collectAsState()
    
    // Calculate time since last update
    var timeSinceUpdate by remember { mutableStateOf("--") }
    LaunchedEffect(lastUpdateTime) {
        while (true) {
            val lastUpdate = lastUpdateTime
            timeSinceUpdate = if (lastUpdate != null) {
                val seconds = (System.currentTimeMillis() - lastUpdate) / 1000
                when {
                    seconds < 60 -> "${seconds}s ago"
                    seconds < 3600 -> "${seconds / 60}m ago"
                    else -> "${seconds / 3600}h ago"
                }
            } else {
                "--"
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    val context = LocalContext.current

    // Persistable sheet fraction and pinned state (like QuickAccessSheet)
    val prefs = context.getSharedPreferences("datapad_prefs", Context.MODE_PRIVATE)
    val KEY_SHEET_FRACTION = "datapad_sheet_fraction"
    val savedFraction = prefs.getFloat(KEY_SHEET_FRACTION, 0.6f)
    val sheetMin = 0.2f
    val sheetMax = 0.95f
    var sheetFraction by rememberSaveable { mutableStateOf(savedFraction.coerceIn(sheetMin, sheetMax)) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = LocalConfiguration.current
    val sheetHeightDp = (configuration.screenHeightDp.toFloat() * sheetFraction).dp
    val dialogView = LocalView.current

    // Attempt to hide system UI immediately before first draw to avoid a visible flash
    DisposableEffect(dialogView) {
        val window = (dialogView.context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, dialogView) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        try { controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE } catch (_: Throwable) {}
        onDispose { /* keep hidden while sheet logic manages visibility */ }
    }

    // Keep immersive fullscreen while sheet is visible
    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.isVisible) {
            val window = (dialogView.context as? android.app.Activity)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, dialogView) }
            while (isActive && sheetState.isVisible) {
                controller?.hide(WindowInsetsCompat.Type.systemBars())
                try { controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE } catch (_: Throwable) {}
                kotlinx.coroutines.delay(100)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        // Box with draggable top handle to resize sheet height
        val density = LocalDensity.current

        Box(modifier = Modifier
            .fillMaxWidth()
            .height(sheetHeightDp)
            .padding(horizontal = 16.dp)
        ) {
            // Drag handle at top (drag vertically to resize)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change: PointerInputChange, dragAmount: Offset ->
                                val screenPx = with(density) { configuration.screenHeightDp.dp.toPx() }
                                val fracDelta = dragAmount.y / screenPx
                                sheetFraction = (sheetFraction - fracDelta).coerceIn(sheetMin, sheetMax)
                            },
                            onDragEnd = {
                                prefs.edit().putFloat(KEY_SHEET_FRACTION, sheetFraction).apply()
                            }
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(width = 64.dp, height = 6.dp)
                        .background(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small)
                )
            }

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
                    Text(
                        text = "DataPad",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Connection Status
                ConnectionStatusCard(
                    isConnected = isConnected,
                    timeSinceUpdate = timeSinceUpdate,
                    deviceIpAddress = deviceIpAddress
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Flight Data Display
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (flightData != null) {
                        FlightDataDisplay(flightData!!)
                    } else {
                        NoDataCard()
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    isConnected: Boolean,
    timeSinceUpdate: String,
    deviceIpAddress: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) 
                Color(0xFF1B5E20) 
            else 
                Color(0xFF7B1FA2)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isConnected) Color.Green else Color.Gray,
                                shape = MaterialTheme.shapes.small
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) "Connected" else "Waiting for data",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = timeSinceUpdate,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Listening on: $deviceIpAddress:5010",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun NoDataCard() {
    val manager = LocalDataPadManager.current
    val deviceIpAddress by manager.deviceIpAddress.collectAsState()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No flight data received yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Make sure forward_parsed_udp.py is running",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Expected command:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "python forward_parsed_udp.py $deviceIpAddress 5010",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FlightDataDisplay(data: FlightData) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Aircraft & Pilot
        DataSection(title = "Aircraft & Pilot") {
            DataRow("Aircraft", data.aircraft)
            DataRow("Pilot", data.unitName)
            DataRow("Coalition", data.coalition)
            DataRow("Group", data.group)
        }

        // Environment (moved)
        data.environment?.let { env ->
            DataSection(title = "Environment") {
                env.windSpeed?.let { DataRow("Wind Speed", String.format("%.1f", it) + " m/s (${String.format("%.1f", mpsToKts(it))} kt)") }
                env.windDirection?.let { DataRow("Wind Direction", String.format("%.1f", it) + "°") }
            }
        }

        // Flight Parameters & Performance (side-by-side)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                DataSection(title = "Flight Parameters") {
                    DataRow("Altitude", "${String.format("%.1f", data.altitude)} m")
                    DataRow("Heading", "${String.format("%.1f", Math.toDegrees(data.heading))}°")
                    DataRow("Pitch", "${String.format("%.2f", Math.toDegrees(data.pitch))}°")
                    DataRow("Bank", "${String.format("%.2f", Math.toDegrees(data.bank))}°")
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                DataSection(title = "Performance") {
                    data.indicatedAirspeed?.let { DataRow("Indicated Airspeed", formatSpeedWithKnots(it)) }
                    data.trueAirspeed?.let { DataRow("True Airspeed", formatSpeedWithKnots(it)) }
                    data.verticalSpeed?.let { DataRow("Vertical Speed", "${String.format("%.2f", it)} m/s (${String.format("%.0f", mpsToFpm(it))} ft/min)") }
                    data.mach?.let { DataRow("Mach", String.format("%.3f", it)) }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Weapons & Countermeasures & Systems Status (side-by-side)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                DataSection(title = "Weapons & Countermeasures") {
                    data.weapons?.let { w ->
                        DataRow("Master Arm", if (w.masterArm) "ARMED" else "SAFE")
                        DataRow("Stations", (w.stations?.size ?: 0).toString())
                        w.stations?.forEach { s ->
                            DataRow("Station ${s.station}", "Type ${s.type} • Count ${s.count}")
                        }
                        DataRow("Total Count", (w.totalCount ?: 0).toString())
                    } ?: DataRow("Weapons", "None")
                    data.countermeasures?.let { c ->
                        DataRow("Flares", c.flareCount.toString())
                        DataRow("Chaff", c.chaffCount.toString())
                    } ?: DataRow("Countermeasures", "N/A")
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                DataSection(title = "Systems Status") {
                    StatusRow("Radar Active", data.radarActive)
                    StatusRow("Jamming", data.jamming)
                    StatusRow("IR Jamming", data.irJamming)
                    StatusRow("AI On", data.aiOn)
                    StatusRow("Human", data.isHuman)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Position (full width)
        DataSection(title = "Position") {
            DataRow("Latitude", String.format("%.6f", data.latitude))
            DataRow("Longitude", String.format("%.6f", data.longitude))
            data.position?.let { pos ->
                DataRow("X", String.format("%.2f", pos.x))
                DataRow("Y", String.format("%.2f", pos.y))
                DataRow("Z", String.format("%.2f", pos.z))
            }
        }


        // Additional Info
        DataSection(title = "Additional Info") {
            DataRow("Timestamp", data.timestamp)
            DataRow("Unit ID", data.unitID)
            DataRow("Streamer Version", data.streamerVersion)
        }
    }
}

@Composable
private fun DataSection(
    title: String,
    initialExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("datapad_prefs", Context.MODE_PRIVATE)
    val key = "section_expanded_${title.replace(" ", "_").lowercase()}"

    var expanded by rememberSaveable { mutableStateOf(prefs.getBoolean(key, initialExpanded)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = {
                    expanded = !expanded
                    prefs.edit().putBoolean(key, expanded).apply()
                }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    content()
                }
            }
        }
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StatusRow(label: String, active: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (active) Color.Green else Color.Gray,
                        shape = MaterialTheme.shapes.small
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (active) "ON" else "OFF",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (active) Color.Green else Color.Gray
            )
        }
    }
}

// --- Unit conversion helpers ---
private fun mpsToKts(mps: Double): Double = mps * 1.9438444924406

private fun mpsToFpm(mps: Double): Double = mps * 196.850393701

private fun formatSpeedWithKnots(value: Double): String =
    "${String.format("%.1f", value)} m/s (${String.format("%.1f", mpsToKts(value))} kt)"
