package com.example.checklist_interactive.ui.checklist

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.checklist_interactive.data.checklist.Checklist
import com.example.checklist_interactive.data.checklist.ChecklistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * MarkdownViewer - Composable zur Anzeige von Markdown-Dateien mit Checkbox-Unterstützung
 * 
 * @param assetPath Pfad zur Markdown-Datei in den Assets
 * @param checklist Optional: Checklist mit Checkbox-Stati
 * @param onCheckboxChange Callback wenn eine Checkbox geändert wird
 */
@Composable
fun MarkdownViewer(
    assetPath: String,
    checklist: Checklist? = null,
    onCheckboxChange: ((itemId: String, checked: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    isInternalFile: Boolean = false
) {
    val context = LocalContext.current
    var markdownContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Markdown-Datei laden
    LaunchedEffect(assetPath, isInternalFile) {
        isLoading = true
        errorMessage = null
        try {
            val content = if (isInternalFile) {
                loadMarkdownFromFile(assetPath)
            } else {
                loadMarkdownFromAssets(context, assetPath)
            }
            markdownContent = content
        } catch (e: Exception) {
            errorMessage = "Fehler beim Laden: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "Unbekannter Fehler",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            else -> {
                if (checklist != null && onCheckboxChange != null) {
                    // Prüfe ob die Checklist Items hat
                    val hasItems = checklist.sections.any { it.items.isNotEmpty() }
                    if (hasItems) {
                        // Interaktive Ansicht mit Checkboxen
                        InteractiveMarkdownView(
                            markdownContent = markdownContent,
                            checklist = checklist,
                            onCheckboxChange = onCheckboxChange
                        )
                    } else {
                        // Keine Checkboxen gefunden, zeige einfache Ansicht
                        SimpleMarkdownView(markdownContent = markdownContent)
                    }
                } else {
                    // Einfache Markdown-Ansicht ohne Interaktion
                    SimpleMarkdownView(markdownContent = markdownContent)
                }
            }
        }
    }
}

/**
 * Einfache Markdown-Ansicht - rendert grundlegendes Markdown
 */
@Composable
private fun SimpleMarkdownView(markdownContent: String) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Zeile für Zeile durchgehen und rendern
        markdownContent.lines().forEach { line ->
            when {
                line.startsWith("# ") -> {
                    Text(
                        text = line.substring(2),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        text = line.substring(3),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                line.startsWith("### ") -> {
                    Text(
                        text = line.substring(4),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                line.trim().startsWith("- [ ]") || line.trim().startsWith("- [x]") || line.trim().startsWith("- [X]") -> {
                    // Checkbox-Item
                    val isChecked = line.trim().startsWith("- [x]") || line.trim().startsWith("- [X]")
                    val text = line.trim().substring(5).trim()
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = null, // Read-only in simple view
                            enabled = false
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp),
                            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically)
                        )
                    }
                }
                line.trim().startsWith("- ") -> {
                    // Normaler Listeneintrag
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("• ", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp))
                        Text(
                            text = line.trim().substring(2),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp)
                        )
                    }
                }
                line.isNotBlank() -> {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                else -> {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * Interaktive Markdown-Ansicht mit Checkbox-Unterstützung
 * Zeigt Checkboxen basierend auf dem Checklist-Status an
 */
@Composable
private fun InteractiveMarkdownView(
    markdownContent: String,
    checklist: Checklist,
    onCheckboxChange: (itemId: String, checked: Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Zeige Checklist-Titel als Hauptüberschrift
        if (checklist.title.isNotEmpty()) {
            Text(
                text = checklist.title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Sections mit Checkboxen und Überschriften
        checklist.sections.forEach { section ->
            // Section-Überschrift
            if (section.title.isNotEmpty() && section.title != checklist.title) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            // Checkbox-Items in dieser Section
            section.items.forEach { item ->
                ChecklistItemRow(
                    item = item,
                    onCheckboxChange = onCheckboxChange
                )
            }
        }
    }
}

/**
 * Einzelne Checkbox-Zeile für ein Checklist-Item
 */
@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    onCheckboxChange: (itemId: String, checked: Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = { isChecked ->
                onCheckboxChange(item.id, isChecked)
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically)
        )
    }
}

/**
 * Lädt Markdown-Inhalt aus den Assets
 */
private suspend fun loadMarkdownFromAssets(context: Context, assetPath: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream = context.assets.open(assetPath)
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.use { it.readText() }
        } catch (e: Exception) {
            throw Exception("Datei nicht gefunden: $assetPath", e)
        }
    }
}

/**
 * Lädt Markdown-Inhalt aus einer internen Datei
 */
private suspend fun loadMarkdownFromFile(filePath: String): String {
    return withContext(Dispatchers.IO) {
        try {
            java.io.File(filePath).readText()
        } catch (e: Exception) {
            throw Exception("Datei nicht gefunden: $filePath", e)
        }
    }
}
