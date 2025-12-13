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
            .then(if (onClick != null) Modifier.clickable { onClick.invoke() } else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = (callsign.ifBlank { "-" }), style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "Status:", style = MaterialTheme.typography.labelSmall)
            FlightStatusDropdown(
                currentStatus = status,
                onStatusChange = { noteManager.saveFlightStatus(it) },
                compact = true
            )
        }
        Text(text = "COM1: ${com1.ifBlank { "-" }.replace(',', '.')} ${com1Mode}", style = MaterialTheme.typography.labelSmall)
        Text(text = "COM2: ${com2.ifBlank { "-" }.replace(',', '.')} ${com2Mode}", style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.weight(1f))

        // Live clock (HH:mm:ss)
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
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
