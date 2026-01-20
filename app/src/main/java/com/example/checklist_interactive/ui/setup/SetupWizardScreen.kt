package com.example.checklist_interactive.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.R
import com.example.checklist_interactive.data.prefs.PreferencesManager
import com.example.checklist_interactive.ui.datapad.LocalDataPadManager
import com.example.checklist_interactive.ui.datapad.DataPadSettingsDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    prefsManager: PreferencesManager,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    softwareVersion: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataPadManager = LocalDataPadManager.current

    // State for DataPad toggle
    val dpEnabled by dataPadManager.isEnabled.collectAsState()
    val entityTrackingEnabled by dataPadManager.isEntityTrackingEnabled.collectAsState()

    // State for DataPad settings dialog
    var showDataPadSettings by remember { mutableStateOf(false) }

    // State for aircraft selection
    var availableAircrafts by remember { mutableStateOf(listOf<String>()) }
    var selectedAircrafts by remember { mutableStateOf(emptySet<String>()) }

    // State for map selection
    var availableMaps by remember { mutableStateOf(listOf<String>()) }
    var selectedMaps by remember { mutableStateOf(emptySet<String>()) }

    // Load available aircraft folders
    LaunchedEffect(Unit) {
        val aircrafts = try {
            context.assets.list("Checklists")
                ?.filter { it.isNotBlank() && !it.contains('.') }
                ?.sorted() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        availableAircrafts = aircrafts
        // Default: all off as requested
        selectedAircrafts = emptySet()
    }

    // Load available maps from database
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val db = com.example.checklist_interactive.data.tactical.TacticalDatabase.getInstance(context)
                val mapsFlow = db.locationDao().getAllMaps()
                val mapsList: List<String> = mapsFlow.first()
                val maps = mapsList.filter { it.isNotBlank() }.sorted()
                availableMaps = maps

                // Default to Caucasus and Marianas if available
                val defaults: List<String> = maps.filter { mapName: String ->
                    mapName.contains("cauc", ignoreCase = true) ||
                    mapName.contains("marian", ignoreCase = true)
                }
                selectedMaps = if (defaults.isNotEmpty()) defaults.toSet() else emptySet()
            } catch (e: Exception) {
                availableMaps = emptyList()
                selectedMaps = emptySet()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.setup_wizard_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDarkTheme) stringResource(R.string.action_light_mode) else stringResource(R.string.action_dark_mode)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Welcome message (smaller banner with app name)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.CenterHorizontally),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.setup_wizard_welcome),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // App name line (smaller)
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.setup_wizard_description),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_version, softwareVersion),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // DataPad Settings
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dashboard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.datapad_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.setup_datapad_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dpEnabled,
                            onCheckedChange = { dataPadManager.setEnabled(it) }
                        )
                    }

                    // Tactical Units Tracking (shown when DataPad is enabled)
                    androidx.compose.animation.AnimatedVisibility(visible = dpEnabled) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.setup_tactical_tracking_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.setup_tactical_tracking_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = entityTrackingEnabled,
                                    onCheckedChange = { dataPadManager.setEntityTrackingEnabled(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Configure DataPad button
                            OutlinedButton(
                                onClick = { showDataPadSettings = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.setup_configure_datapad))
                            }
                        }
                    }
                }
            }

            // FAB Size recommendation and selection
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_fab_size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Compute recommended size based on screen width
                    val configuration = LocalConfiguration.current
                    val screenWidthDp = configuration.screenWidthDp
                    // Recommend tiny for small screens, small for medium, medium for larger screens
                    val recommended = when {
                        screenWidthDp >= 900 -> "medium"
                        screenWidthDp >= 600 -> "small"
                        else -> "tiny"
                    }
                    val recommendedLabel = when (recommended) {
                        "tiny" -> stringResource(R.string.settings_fab_size_tiny)
                        "small" -> stringResource(R.string.settings_fab_size_small)
                        else -> stringResource(R.string.settings_fab_size_medium)
                    }

                    Text(
                        text = stringResource(R.string.setup_fab_size_recommendation, recommendedLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    var currentFabSize by remember { mutableStateOf(prefsManager.getFabSize()) }
                    val fabSizes = listOf(
                        "tiny" to stringResource(R.string.settings_fab_size_tiny),
                        "small" to stringResource(R.string.settings_fab_size_small),
                        "medium" to stringResource(R.string.settings_fab_size_medium),
                        "large" to stringResource(R.string.settings_fab_size_large)
                    )

                    Column {
                        fabSizes.forEach { (size, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentFabSize = size
                                        prefsManager.setFabSize(size)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (currentFabSize == size),
                                    onClick = {
                                        currentFabSize = size
                                        prefsManager.setFabSize(size)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                    }
                }
            }

            // Aircraft Selection
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.setup_aircraft_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.setup_aircraft_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Select All / None buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { selectedAircrafts = availableAircrafts.toSet() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.settings_aircraft_select_all))
                        }
                        OutlinedButton(
                            onClick = { selectedAircrafts = emptySet() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.settings_aircraft_select_none))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Aircraft list (max 5 items visible, scrollable)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        if (availableAircrafts.isEmpty()) {
                            Text(
                                text = stringResource(R.string.common_loading),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        } else {
                            LazyColumn {
                                items(availableAircrafts) { aircraft ->
                                    val displayName = aircraft.replace('_', ' ')
                                    val checked = selectedAircrafts.contains(aircraft)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedAircrafts = if (checked) {
                                                    selectedAircrafts - aircraft
                                                } else {
                                                    selectedAircrafts + aircraft
                                                }
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { isChecked ->
                                                selectedAircrafts = if (isChecked) {
                                                    selectedAircrafts + aircraft
                                                } else {
                                                    selectedAircrafts - aircraft
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(displayName)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Map Selection
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.setup_maps_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.setup_maps_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Select All / None buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { selectedMaps = availableMaps.toSet() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.settings_map_select_all))
                        }
                        OutlinedButton(
                            onClick = { selectedMaps = emptySet() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.settings_map_select_none))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Map list (max 5 items visible, scrollable)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        if (availableMaps.isEmpty()) {
                            Text(
                                text = stringResource(R.string.common_loading),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        } else {
                            LazyColumn {
                                items(availableMaps) { mapName ->
                                    val displayName = mapName.replace("_", " ")
                                    val checked = selectedMaps.contains(mapName)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedMaps = if (checked) {
                                                    selectedMaps - mapName
                                                } else {
                                                    selectedMaps + mapName
                                                }
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { isChecked ->
                                                selectedMaps = if (isChecked) {
                                                    selectedMaps + mapName
                                                } else {
                                                    selectedMaps - mapName
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(displayName)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        // Skip: use defaults
                        prefsManager.setSetupWizardComplete(true)
                        onSkip()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.setup_wizard_skip))
                }

                Button(
                    onClick = {
                        scope.launch {
                            // Save selections
                            prefsManager.setVisibleAircrafts(selectedAircrafts)
                            prefsManager.setVisibleMaps(selectedMaps)
                            prefsManager.setSetupWizardComplete(true)
                            onComplete()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.setup_wizard_get_started))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // DataPad Settings Dialog
    if (showDataPadSettings) {
        DataPadSettingsDialog(
            onDismiss = { showDataPadSettings = false }
        )
    }
}
