package com.example.checklist_interactive.ui.quickaccess

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import com.example.checklist_interactive.data.quicknotes.QuickNoteManager
import com.example.checklist_interactive.ui.datapad.LocalDataPadManager
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import com.example.checklist_interactive.R

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
fun BatteryLevelIndicator(
    modifier: Modifier = Modifier,
    pollIntervalMs: Long = 5 * 60 * 1000L // 5 minutes
) {
    val context = LocalContext.current
    val batteryManager = remember { context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager? }
    var percent by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(Unit) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    percent = if (level >= 0 && scale > 0) level * 100 / scale
                              else batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                }
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    if (pollIntervalMs > 0L) {
        LaunchedEffect(pollIntervalMs) {
            while (true) {
                delay(pollIntervalMs)
                val p = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (p != null && p >= 0) percent = p
            }
        }
    }

    percent?.let {
        Text(text = "$it%", style = MaterialTheme.typography.labelSmall, modifier = modifier.semantics { contentDescription = "Battery level $it percent" })
    }
}

@Composable
fun FlightMiniStatusBar(noteManager: QuickNoteManager, onClick: (() -> Unit)? = null, useBackground: Boolean = true) {
    val callsign by noteManager.callsign.collectAsState()
    val status by noteManager.flightStatus.collectAsState()
    val com1 by noteManager.com1.collectAsState()
    val com1Mode by noteManager.com1Mode.collectAsState()
    val com2 by noteManager.com2.collectAsState()
    val com2Mode by noteManager.com2Mode.collectAsState()
    
    // DataPad status for position display
    val dataPadManager = LocalDataPadManager.current
    val flightData by dataPadManager.flightData.collectAsState()
    val dpConnected by dataPadManager.isConnected.collectAsState()
    val dpEnabled by dataPadManager.isEnabled.collectAsState()
    val hasValidPosition = flightData?.let { it.latitude != 0.0 && it.longitude != 0.0 } ?: false



    val bgColor = if (useBackground) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (useBackground) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant

    CompositionLocalProvider(LocalContentColor provides contentColor) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(bgColor)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {

        
    Row(
        modifier = Modifier
            .weight(1f)
            .height(28.dp)
            .then(if (onClick != null) Modifier.clickable { onClick.invoke() } else Modifier)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = (callsign.ifBlank { stringResource(R.string.common_placeholder_dash) }), style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(R.string.quick_notes_status_label) + ":", style = MaterialTheme.typography.labelSmall)
            FlightStatusDropdown(
                currentStatus = status,
                onStatusChange = { noteManager.saveFlightStatus(it) },
                compact = true
            )
        }
        Text(text = stringResource(R.string.quick_notes_com1_label) + ": ${com1.ifBlank { stringResource(R.string.common_placeholder_dash) }.replace(',', '.')} ${com1Mode}", style = MaterialTheme.typography.labelSmall)
        Text(text = stringResource(R.string.quick_notes_com2_label) + ": ${com2.ifBlank { stringResource(R.string.common_placeholder_dash) }.replace(',', '.')} ${com2Mode}", style = MaterialTheme.typography.labelSmall)
        
        // DataPad connection status indicator (centered)
        Spacer(modifier = Modifier.weight(1f))
        
        if (dpEnabled) {
            val indicatorColor = when {
                !dpConnected -> Color(0xFFFF5252) // red - not connected
                !hasValidPosition -> Color(0xFFFFC107) // amber/yellow - connected but no position
                else -> Color(0xFF4CAF50) // green - connected and valid position
            }
            
            val statusText = when {
                !dpConnected -> stringResource(R.string.map_no_datapad_connection)
                !hasValidPosition -> stringResource(R.string.map_waiting_for_valid_position)
                else -> "Position OK"
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
                    .semantics { contentDescription = statusText }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Live clock (HH:mm:ss)
        val formatter = DateTimeFormatter.ofPattern(stringResource(R.string.format_time_hh_mm_ss))
        var timeText by remember { mutableStateOf(LocalTime.now().format(formatter)) }
        LaunchedEffect(Unit) {
            while (true) {
                timeText = LocalTime.now().format(formatter)
                delay(1000L)
            }
        }

        BatteryLevelIndicator(modifier = Modifier.padding(end = 6.dp))
        Text(text = timeText, style = MaterialTheme.typography.labelSmall)
    }
    }
    }
    

}
