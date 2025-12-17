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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PowerSettingsNew
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.ui.platform.LocalDensity
import com.example.checklist_interactive.data.datapad.FlightData
import com.example.checklist_interactive.R
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
    val udpPort by manager.udpPort.collectAsState()
    val isEnabled by manager.isEnabled.collectAsState()
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // Calculate time since last update
    val context = LocalContext.current
    var timeSinceUpdate by remember { mutableStateOf("--") }
    LaunchedEffect(lastUpdateTime) {
        while (true) {
            val lastUpdate = lastUpdateTime
            timeSinceUpdate = if (lastUpdate != null) {
                val seconds = (System.currentTimeMillis() - lastUpdate) / 1000
                when {
                    seconds < 60 -> context.getString(R.string.datapad_time_seconds_ago, seconds)
                    seconds < 3600 -> context.getString(R.string.datapad_time_minutes_ago, seconds / 60)
                    else -> context.getString(R.string.datapad_time_hours_ago, seconds / 3600)
                }
            } else {
                "--"
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    // Persistable sheet fraction and pinned state (like QuickAccessSheet)
    val prefs = context.getSharedPreferences("datapad_prefs", Context.MODE_PRIVATE)
    val KEY_SHEET_FRACTION = "datapad_sheet_fraction"
    val savedFraction = prefs.getFloat(KEY_SHEET_FRACTION, 0.6f)
    val sheetMin = 0.2f
    val sheetMax = 0.95f
    var sheetFraction by rememberSaveable { mutableStateOf(savedFraction.coerceIn(sheetMin, sheetMax)) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    val configuration = LocalConfiguration.current
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

        // Keep applying every 100ms to override ModalBottomSheet's behavior only while the sheet is visible
        if (sheetState.isVisible) {
            while (isActive && sheetState.isVisible) {
                hideSystemUI()
                kotlinx.coroutines.delay(100L)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
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
                    kotlinx.coroutines.delay(100L)
                }
            }
        }

        // Box with draggable top handle to resize sheet height
        val density = LocalDensity.current

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
                            onDrag = { change: PointerInputChange, dragAmount: Offset ->
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
                                    onDismiss()
                                }
                                dragAccum = 0f
                            },
                            onDragCancel = {
                                dragAccum = 0f
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
                        text = stringResource(R.string.datapad_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_settings),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Connection Status
                ConnectionStatusCard(
                    isConnected = isConnected,
                    timeSinceUpdate = timeSinceUpdate,
                    deviceIpAddress = deviceIpAddress,
                    udpPort = udpPort,
                    isEnabled = isEnabled,
                    onToggleEnabled = { manager.toggleEnabled() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Flight Data Display
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    FlightDataDisplay(flightData)
                }
            }
        }
    }
    
    // Settings Dialog
    if (showSettingsDialog) {
        DataPadSettingsDialog(
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun ConnectionStatusCard(
    isConnected: Boolean,
    timeSinceUpdate: String,
    deviceIpAddress: String,
    udpPort: Int,
    isEnabled: Boolean,
    onToggleEnabled: () -> Unit
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
                    // Enabled indicator
                    Text(
                        text = stringResource(if (isEnabled) R.string.datapad_reception_on else R.string.datapad_reception_off),
                        color = if (isEnabled) Color(0xFFB9F6CA) else Color(0xFFB0BEC5),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isConnected) Color(0xFFB9F6CA) else Color.Gray,
                                shape = MaterialTheme.shapes.small
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(if (isConnected) R.string.datapad_connected else R.string.datapad_waiting_for_data),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleEnabled,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = stringResource(if (isEnabled) R.string.datapad_toggle_reception_on else R.string.datapad_toggle_reception_off),
                            tint = if (isEnabled) Color.White else Color.Gray
                        )
                    }

                    Text(
                        text = timeSinceUpdate,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.datapad_encrypted_connection, deviceIpAddress, udpPort),
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
    val udpPort by manager.udpPort.collectAsState()
    
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
                text = stringResource(R.string.datapad_no_data),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.datapad_setup_instructions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.datapad_expected_command),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.datapad_command_example, deviceIpAddress, udpPort),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.datapad_encryption_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun FlightDataDisplay(data: FlightData?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Aircraft & Pilot
        DataSection(title = stringResource(R.string.datapad_section_aircraft_pilot)) {
            DataRow(stringResource(R.string.datapad_aircraft), data?.aircraft ?: stringResource(R.string.datapad_not_available))
            DataRow(stringResource(R.string.datapad_pilot), data?.unitName ?: stringResource(R.string.datapad_not_available))
            DataRow(stringResource(R.string.datapad_coalition), data?.coalition ?: stringResource(R.string.datapad_not_available))
            DataRow(stringResource(R.string.datapad_group), data?.group ?: stringResource(R.string.datapad_not_available))
        }

        // Environment + Position side-by-side
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                DataSection(title = stringResource(R.string.datapad_environment)) {
                    data?.environment?.let { env ->
                        DataRow(stringResource(R.string.datapad_temperature), env.temperature?.let { String.format("%.1f", it) + "°C (${String.format("%.1f", celsiusToFahrenheit(it))}°F)" } ?: stringResource(R.string.datapad_not_available))
                        DataRow(stringResource(R.string.datapad_pressure), env.pressure?.let { String.format("%.1f", it) + " hPa (${String.format("%.2f", hpaToInHg(it))} inHg)" } ?: stringResource(R.string.datapad_not_available))
                        DataRow(stringResource(R.string.datapad_wind_speed), env.windSpeed?.let { String.format("%.1f", it) + " m/s (${String.format("%.1f", mpsToKts(it))} kt)" } ?: stringResource(R.string.datapad_not_available))
                        DataRow(stringResource(R.string.datapad_wind_direction), env.windDirection?.let { String.format("%.1f", it) + "°" } ?: stringResource(R.string.datapad_not_available))
                        env.visibility?.let { vis ->
                            DataRow(stringResource(R.string.datapad_visibility), String.format("%.0f", vis) + " m (${String.format("%.1f", metersToNm(vis))} nm)")
                        }
                        env.clouds?.let { clouds ->
                            DataRow(stringResource(R.string.datapad_clouds), clouds)
                        }
                    } ?: run {
                        DataRow(stringResource(R.string.datapad_temperature), stringResource(R.string.datapad_not_available))
                        DataRow(stringResource(R.string.datapad_pressure), stringResource(R.string.datapad_not_available))
                        DataRow(stringResource(R.string.datapad_wind_speed), stringResource(R.string.datapad_not_available))
                        DataRow(stringResource(R.string.datapad_wind_direction), stringResource(R.string.datapad_not_available))
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                DataSection(title = stringResource(R.string.datapad_section_position)) {
                    DataRow(stringResource(R.string.datapad_field_latitude), data?.latitude?.let { String.format("%.6f", it) } ?: stringResource(R.string.datapad_not_available))
                    DataRow(stringResource(R.string.datapad_field_longitude), data?.longitude?.let { String.format("%.6f", it) } ?: stringResource(R.string.datapad_not_available))
                    data?.position?.let { pos ->
                        DataRow(stringResource(R.string.datapad_field_x), String.format("%.2f", pos.x))
                        DataRow(stringResource(R.string.datapad_field_y), String.format("%.2f", pos.y))
                        DataRow(stringResource(R.string.datapad_field_z), String.format("%.2f", pos.z))
                    } ?: run {
                        DataRow(stringResource(R.string.datapad_field_x), stringResource(R.string.datapad_not_available))
                        DataRow(stringResource(R.string.datapad_field_y), stringResource(R.string.datapad_not_available))
                        DataRow(stringResource(R.string.datapad_field_z), stringResource(R.string.datapad_not_available))
                    }
                }
            }
        }

        // AoA & G-Load
        data?.angleOfAttack?.let { aoa ->
            DataSection(title = stringResource(R.string.datapad_section_flight_performance)) {
                DataRow(stringResource(R.string.datapad_field_angle_of_attack), String.format("%.2f°", aoa))
                data.gLoad?.let { g ->
                    DataRow(stringResource(R.string.datapad_field_g_load_x), String.format("%.2f G", g.x))
                    DataRow(stringResource(R.string.datapad_field_g_load_y), String.format("%.2f G", g.y))
                    DataRow(stringResource(R.string.datapad_field_g_load_z), String.format("%.2f G", g.z))
                }
            }
        }

        // Engine Data
        data?.engines?.let { eng ->
            DataSection(title = stringResource(R.string.datapad_section_engine)) {
                eng.rpm?.let { rpm ->
                    rpm.left?.let { DataRow(stringResource(R.string.datapad_field_rpm_left), String.format("%.1f%%", it)) }
                    rpm.right?.let { DataRow(stringResource(R.string.datapad_field_rpm_right), String.format("%.1f%%", it)) }
                }
                eng.egt?.let { egt ->
                    egt.left?.let { DataRow(stringResource(R.string.datapad_field_egt_left), String.format("%.0f°C", it)) }
                    egt.right?.let { DataRow(stringResource(R.string.datapad_field_egt_right), String.format("%.0f°C", it)) }
                }
                eng.throttle?.let { DataRow(stringResource(R.string.datapad_field_throttle), String.format("%.1f%%", it * 100)) }
                StatusRow(stringResource(R.string.datapad_field_afterburner), eng.afterburner)
            }
        }

        // Aircraft Mass
        data?.aircraftMass?.let { mass ->
            DataSection(title = stringResource(R.string.datapad_section_aircraft_mass)) {
                mass.total?.let { DataRow(stringResource(R.string.datapad_field_total_mass), String.format("%.0f kg", it)) }
                mass.empty?.let { DataRow(stringResource(R.string.datapad_field_empty_mass), String.format("%.0f kg", it)) }
                mass.payload?.let { DataRow(stringResource(R.string.datapad_field_payload), String.format("%.0f kg", it)) }
            }
        }

        // Flight Controls & Trim
        data?.flightControls?.let { ctrl ->
            DataSection(title = stringResource(R.string.datapad_section_flight_controls), initialExpanded = false) {
                ctrl.pitch?.let { DataRow(stringResource(R.string.datapad_field_pitch), String.format("%.2f", it)) }
                ctrl.roll?.let { DataRow(stringResource(R.string.datapad_field_roll), String.format("%.2f", it)) }
                ctrl.yaw?.let { DataRow(stringResource(R.string.datapad_field_yaw), String.format("%.2f", it)) }
                ctrl.trimPitch?.let { DataRow(stringResource(R.string.datapad_field_trim_pitch), String.format("%.2f", it)) }
                ctrl.trimRoll?.let { DataRow(stringResource(R.string.datapad_field_trim_roll), String.format("%.2f", it)) }
                ctrl.trimYaw?.let { DataRow(stringResource(R.string.datapad_field_trim_yaw), String.format("%.2f", it)) }
            }
        }

        // Mechanical (Gear, Flaps, etc.)
        data?.mechanical?.let { mech ->
            DataSection(title = stringResource(R.string.datapad_section_gear_config)) {
                mech.gear?.let { gear ->
                    gear.nose?.let { DataRow(stringResource(R.string.datapad_field_nose_gear), if (it > 0.9) stringResource(R.string.datapad_gear_down) else if (it < 0.1) stringResource(R.string.datapad_gear_up) else stringResource(R.string.datapad_gear_transit)) }
                    gear.left?.let { DataRow(stringResource(R.string.datapad_field_left_gear), if (it > 0.9) stringResource(R.string.datapad_gear_down) else if (it < 0.1) stringResource(R.string.datapad_gear_up) else stringResource(R.string.datapad_gear_transit)) }
                    gear.right?.let { DataRow(stringResource(R.string.datapad_field_right_gear), if (it > 0.9) stringResource(R.string.datapad_gear_down) else if (it < 0.1) stringResource(R.string.datapad_gear_up) else stringResource(R.string.datapad_gear_transit)) }
                }
                StatusRow(stringResource(R.string.datapad_field_weight_on_wheels), data.weightOnWheels)
                mech.flaps?.let { DataRow(stringResource(R.string.datapad_field_flaps), String.format("%.0f%%", it * 100)) }
                mech.speedbrake?.let { DataRow(stringResource(R.string.datapad_field_speedbrake), String.format("%.0f%%", it * 100)) }
                mech.canopy?.let { DataRow(stringResource(R.string.datapad_field_canopy), if (it > 0.9) stringResource(R.string.datapad_canopy_open) else if (it < 0.1) stringResource(R.string.datapad_canopy_closed) else stringResource(R.string.datapad_canopy_moving)) }
                mech.hook?.let { DataRow(stringResource(R.string.datapad_field_hook), if (it > 0.5) stringResource(R.string.datapad_hook_down) else stringResource(R.string.datapad_hook_up)) }
            }
        }

        // Lights
        data?.lights?.let { lights ->
            DataSection(title = stringResource(R.string.datapad_section_lights), initialExpanded = false) {
                lights.landing?.let { StatusRow(stringResource(R.string.datapad_field_landing_light), it > 0.5) }
                lights.taxi?.let { StatusRow(stringResource(R.string.datapad_field_taxi_light), it > 0.5) }
                lights.navigation?.let { StatusRow(stringResource(R.string.datapad_field_nav_light), it > 0.5) }
                lights.strobe?.let { StatusRow(stringResource(R.string.datapad_field_strobe_light), it > 0.5) }
                lights.formation?.let { StatusRow(stringResource(R.string.datapad_field_formation_light), it > 0.5) }
            }
        }

        // Systems Status
        data?.systems?.let { sys ->
            DataSection(title = stringResource(R.string.datapad_section_systems_status), initialExpanded = false) {
                sys.electrical?.let { DataRow(stringResource(R.string.datapad_field_electrical), it) }
                sys.hydraulic?.let { DataRow(stringResource(R.string.datapad_field_hydraulic), it) }
                sys.apuOn?.let { StatusRow(stringResource(R.string.datapad_field_apu), it) }
                sys.generatorOn?.let { StatusRow(stringResource(R.string.datapad_field_generator), it) }
            }
        }

        // Mission Time
        data?.missionTime?.let { mt ->
            DataSection(title = stringResource(R.string.datapad_section_mission_time), initialExpanded = false) {
                val hours = (mt / 3600).toInt()
                val minutes = ((mt % 3600) / 60).toInt()
                val seconds = (mt % 60).toInt()
                DataRow(stringResource(R.string.datapad_field_time), String.format("%02d:%02d:%02d", hours, minutes, seconds))
            }
        }

        // Nearby Units
        data?.nearbyUnits?.let { units ->
            if (units.isNotEmpty()) {
                DataSection(title = stringResource(R.string.datapad_section_nearby_units, units.size), initialExpanded = false) {
                    units.take(10).forEach { unit ->
                        DataRow(
                            unit.name,
                            "${unit.type} • ${unit.distance?.let { String.format("%.0f m", it) } ?: "?"}"
                        )
                    }
                }
            }
        }

        // Flight Parameters & Performance (side-by-side)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                DataSection(title = stringResource(R.string.datapad_section_flight_params)) {
                    DataRow(stringResource(R.string.datapad_field_altitude), data?.altitude?.let { "${String.format("%.1f", it)} m" } ?: stringResource(R.string.datapad_not_available))
                    DataRow(stringResource(R.string.datapad_field_heading), data?.heading?.let { "${String.format("%.1f", Math.toDegrees(it))}°" } ?: stringResource(R.string.datapad_not_available))
                    DataRow(stringResource(R.string.datapad_field_pitch), data?.pitch?.let { "${String.format("%.2f", Math.toDegrees(it))}°" } ?: stringResource(R.string.datapad_not_available))
                    DataRow(stringResource(R.string.datapad_field_bank), data?.bank?.let { "${String.format("%.2f", Math.toDegrees(it))}°" } ?: stringResource(R.string.datapad_not_available))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                DataSection(title = stringResource(R.string.datapad_section_performance)) {
                    DataRow(stringResource(R.string.datapad_field_indicated_airspeed), data?.indicatedAirspeed?.let { formatSpeedWithKnots(it) } ?: stringResource(R.string.datapad_not_available))
                    DataRow(stringResource(R.string.datapad_field_true_airspeed), data?.trueAirspeed?.let { formatSpeedWithKnots(it) } ?: stringResource(R.string.datapad_not_available))
                    DataRow(stringResource(R.string.datapad_field_vertical_speed), data?.verticalSpeed?.let { "${String.format("%.2f", it)} m/s (${String.format("%.0f", mpsToFpm(it))} ft/min)" } ?: stringResource(R.string.datapad_not_available))
                    DataRow(stringResource(R.string.datapad_field_mach), data?.mach?.let { String.format("%.3f", it) } ?: stringResource(R.string.datapad_not_available))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Weapons & Countermeasures & Systems Status (side-by-side)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                DataSection(title = stringResource(R.string.datapad_section_weapons)) {
                    data?.weapons?.let { w ->
                        DataRow(stringResource(R.string.datapad_field_master_arm), if (w.masterArm) stringResource(R.string.datapad_master_arm_armed) else stringResource(R.string.datapad_master_arm_safe))
                        DataRow(stringResource(R.string.datapad_field_stations), (w.stations?.size ?: 0).toString())
                        w.stations?.forEach { s ->
                            DataRow(stringResource(R.string.datapad_station_label, s.station), stringResource(R.string.datapad_type_count, s.type, s.count))
                        }
                        DataRow(stringResource(R.string.datapad_field_total_count), (w.totalCount ?: 0).toString())
                    } ?: run {
                        DataRow(stringResource(R.string.datapad_field_master_arm), stringResource(R.string.datapad_not_available))
                        DataRow(stringResource(R.string.datapad_field_stations), stringResource(R.string.datapad_not_available))
                        DataRow(stringResource(R.string.datapad_field_total_count), stringResource(R.string.datapad_not_available))
                    }
                    data?.countermeasures?.let { c ->
                        DataRow(stringResource(R.string.datapad_field_flares), c.flareCount.toString())
                        DataRow(stringResource(R.string.datapad_field_chaff), c.chaffCount.toString())
                    } ?: run {
                        DataRow(stringResource(R.string.datapad_field_flares), stringResource(R.string.datapad_not_available))
                        DataRow(stringResource(R.string.datapad_field_chaff), stringResource(R.string.datapad_not_available))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                DataSection(title = stringResource(R.string.datapad_section_systems_status)) {
                    StatusRow(stringResource(R.string.datapad_field_radar_active), data?.radarActive ?: false)
                    StatusRow(stringResource(R.string.datapad_field_jamming), data?.jamming ?: false)
                    StatusRow(stringResource(R.string.datapad_field_ir_jamming), data?.irJamming ?: false)
                    StatusRow(stringResource(R.string.datapad_field_ai_on), data?.aiOn ?: false)
                    StatusRow(stringResource(R.string.datapad_field_human), data?.isHuman ?: false)
                }
            }
        }

        // Radar Details
        data?.radar?.let { radar ->
            DataSection(title = stringResource(R.string.datapad_section_radar), initialExpanded = false) {
                radar.mode?.let { DataRow(stringResource(R.string.datapad_field_mode), it) }
                radar.range?.let { DataRow(stringResource(R.string.datapad_field_range), String.format("%.0f m", it)) }
                StatusRow(stringResource(R.string.datapad_field_locked), radar.locked)
                DataRow(stringResource(R.string.datapad_field_track_count), radar.trackCount.toString())
                radar.azimuth?.let { DataRow(stringResource(R.string.datapad_field_azimuth), String.format("%.1f°", it)) }
                radar.elevation?.let { DataRow(stringResource(R.string.datapad_field_elevation), String.format("%.1f°", it)) }
                radar.scan?.let { DataRow(stringResource(R.string.datapad_field_scan), it) }
                radar.tracks?.let { tracks ->
                    if (tracks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.datapad_radar_tracks),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        tracks.forEach { track ->
                            DataRow(
                                stringResource(R.string.datapad_track_label, track.id),
                                stringResource(R.string.datapad_track_detail,
                                    track.range?.let { String.format("%.0f m", it) } ?: "?",
                                    track.azimuth?.let { String.format("%.0f°", it) } ?: "?"
                                )
                            )
                        }
                    }
                }
            }
        }

        // RWR / Threats
        data?.rwr?.let { rwr ->
            if (rwr.threatsDetected > 0) {
                DataSection(title = stringResource(R.string.datapad_section_rwr_threats, rwr.threatsDetected), initialExpanded = true) {
                    rwr.contacts?.forEach { contact ->
                        DataRow(
                            contact.type,
                            "${stringResource(R.string.datapad_rwr_azimuth_prefix)} ${contact.azimuth?.let { String.format("%.0f°", it) } ?: "?"} • ${stringResource(R.string.datapad_rwr_priority_prefix)} ${contact.priority}"
                        )
                    }
                }
            }
        }



        // Additional Info
        DataSection(title = stringResource(R.string.datapad_section_additional_info)) {
            DataRow(stringResource(R.string.datapad_field_timestamp), data?.timestamp ?: stringResource(R.string.datapad_not_available))
            DataRow(stringResource(R.string.datapad_field_unit_id), data?.unitID ?: stringResource(R.string.datapad_not_available))
            DataRow(stringResource(R.string.datapad_field_streamer_version), data?.streamerVersion ?: stringResource(R.string.datapad_not_available))
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
                        contentDescription = if (expanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
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
                text = if (active) stringResource(R.string.state_on) else stringResource(R.string.state_off),
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

private fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0

private fun hpaToInHg(hpa: Double): Double = hpa * 0.02953

private fun metersToNm(meters: Double): Double = meters * 0.000539957

private fun formatSpeedWithKnots(value: Double): String =
    "${String.format("%.1f", value)} m/s (${String.format("%.1f", mpsToKts(value))} kt)"
