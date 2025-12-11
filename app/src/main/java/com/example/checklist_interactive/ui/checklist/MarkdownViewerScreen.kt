package com.example.checklist_interactive.ui.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.checklist_interactive.data.checklist.Checklist

/**
 * MarkdownViewerScreen - Screen-Komponente für die Markdown-Anzeige
 * Zeigt eine Markdown-Datei mit optionaler Checkbox-Interaktion an
 * 
 * @param assetPath Pfad zur Markdown-Datei in den Assets
 * @param checklist Optional: Checklist-Daten für Checkbox-Stati
 * @param onBack Navigation zurück
 * @param onCheckboxChange Callback wenn eine Checkbox geändert wird
 * @param onSettings Optional: Settings-Button-Handler
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownViewerScreen(
    assetPath: String,
    checklist: Checklist? = null,
    onBack: () -> Unit,
    onCheckboxChange: ((itemId: String, checked: Boolean) -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onShowFileList: (() -> Unit)? = null
) {
    // Extrahiere Dateinamen für den Titel
    val fileName = remember(assetPath) {
        assetPath.substringAfterLast('/').substringBeforeLast('.')
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Zurück"
                        )
                    }
                },
                actions = {
                    if (onSettings != null) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Einstellungen"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (onShowFileList != null) {
                FloatingActionButton(
                    onClick = onShowFileList,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Dateiliste")
                }
            }
        }
    ) { paddingValues ->
        MarkdownViewer(
            assetPath = assetPath,
            checklist = checklist,
            onCheckboxChange = onCheckboxChange,
            modifier = Modifier.padding(paddingValues)
        )
    }
}
