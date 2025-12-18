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

@Composable
fun FlightMiniStatusBar(noteManager: QuickNoteManager, onClick: (() -> Unit)? = null, useBackground: Boolean = true) {
    val callsign by noteManager.callsign.collectAsState()
    val status by noteManager.flightStatus.collectAsState()
    val com1 by noteManager.com1.collectAsState()
    val com1Mode by noteManager.com1Mode.collectAsState()
    val com2 by noteManager.com2.collectAsState()
    val com2Mode by noteManager.com2Mode.collectAsState()



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
        // DataPad connection status indicator: green = connected, yellow = enabled but not connected, red = disabled
        val dataPadManager = LocalDataPadManager.current
        val dpConnected by dataPadManager.isConnected.collectAsState()
        val dpEnabled by dataPadManager.isEnabled.collectAsState()
        val indicatorColor = when {
            dpConnected -> Color(0xFF4CAF50) // green
            dpEnabled -> Color(0xFFFFC107) // amber/yellow
            else -> Color(0xFFF44336) // red
        }

        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(indicatorColor)
                .semantics { contentDescription = when {
                    dpConnected -> "DataPad verbunden"
                    dpEnabled -> "DataPad aktiviert, aber nicht verbunden"
                    else -> "DataPad deaktiviert"
                } }
        )

        Spacer(modifier = Modifier.width(6.dp))

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

        Text(text = timeText, style = MaterialTheme.typography.labelSmall)
    }
    }
    }
    

}
