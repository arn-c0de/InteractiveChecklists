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
import com.example.checklist_interactive.data.quicknotes.QuickNoteManager

@Composable
fun FlightMiniStatusBar(noteManager: QuickNoteManager, onClick: (() -> Unit)? = null) {
    val callsign by noteManager.callsign.collectAsState()
    val status by noteManager.flightStatus.collectAsState()
    val com1 by noteManager.com1.collectAsState()
    val com1Mode by noteManager.com1Mode.collectAsState()
    val com2 by noteManager.com2.collectAsState()
    val com2Mode by noteManager.com2Mode.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable { onClick.invoke() } else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = (callsign.ifBlank { "-" }), style = MaterialTheme.typography.labelLarge)
        Text(text = "Status: ${status.ifBlank { "Idle" }}", style = MaterialTheme.typography.labelMedium)
        Text(text = "COM1: ${com1.ifBlank { "-" }} ${com1Mode}", style = MaterialTheme.typography.labelMedium)
        Text(text = "COM2: ${com2.ifBlank { "-" }} ${com2Mode}", style = MaterialTheme.typography.labelMedium)
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

        Text(text = timeText, style = MaterialTheme.typography.labelMedium)
    }
}
