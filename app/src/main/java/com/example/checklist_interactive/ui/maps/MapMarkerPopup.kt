package com.example.checklist_interactive.ui.maps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onRunwayClick: (RunwayEntity) -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxHeight(0.6f)
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)) {

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

                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!location.icao.isNullOrEmpty()) Text(text = "ICAO: ${location.icao}")
                if (!location.iata.isNullOrEmpty()) Text(text = "IATA: ${location.iata}")
                if (!location.country.isNullOrEmpty()) Text(text = location.country!!)
            }

            if (location.elevationM != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Elevation: ${location.elevationM} m", style = MaterialTheme.typography.bodyMedium)
            }

            if (location.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = location.description, style = MaterialTheme.typography.bodyMedium)
            }

            // Frequencies (try to parse JSON)
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
                Text(text = "Frequencies", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                if (freqs.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        freqs.forEach { (k, v) -> Text(text = "$k: $v") }
                    }
                } else {
                    Text(text = location.frequencies ?: "")
                }
            }

            // Runways
            if (runways.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Runways", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(runways, key = { it.id ?: 0 }) { rw ->
                        Column(modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRunwayClick(rw) }
                            .padding(bottom = 8.dp)) {
                            Text(text = rw.name, fontWeight = FontWeight.Bold)
                            val length = rw.lengthM?.toString() ?: rw.lengthFt?.toString() ?: "?"
                            val surf = rw.surface ?: "unknown"
                            val hdg = rw.headingDeg?.let { String.format("%.0f°", it) } ?: "n/a"
                            Text(text = "${length} m · ${surf} · HDG: ${hdg}")
                            if (!rw.ilsFrequency.isNullOrEmpty()) Text(text = "ILS: ${rw.ilsFrequency}")
                            if (rw.hasLighting == 1) Text(text = "Lighting: yes")
                            if (!rw.notes.isNullOrEmpty()) Text(text = rw.notes ?: "")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onManage, modifier = Modifier.weight(1f)) {
                    Text("Manage")
                }

                Button(onClick = onClose, modifier = Modifier.weight(1f)) {
                    Text("Close")
                }
            }
        }
    }
}