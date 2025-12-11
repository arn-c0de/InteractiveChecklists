package com.example.checklist_interactive.ui.checklist

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.checklist_interactive.data.checklist.Checklist
import com.example.checklist_interactive.data.prefs.PreferencesManager
import com.example.checklist_interactive.data.checklist.ChecklistItem
import kotlinx.coroutines.Dispatchers
import android.content.SharedPreferences
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
    , prefsManager: PreferencesManager? = null
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

    val effectivePrefs = prefsManager ?: remember { PreferencesManager(context) }
    val initialFontSize = effectivePrefs.getMarkdownFontSize()
    var fontSizeState by remember { mutableStateOf(initialFontSize) }

    // Expand/Collapse all sections state
    val initialExpandState = effectivePrefs.areMarkdownSectionsExpandedByDefault()
    var expandAllState by remember { mutableStateOf(initialExpandState) }

    // Listen for preference changes and update font size and expand state in real time
    DisposableEffect(effectivePrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            val updatedFontSize = effectivePrefs.getMarkdownFontSize()
            if (updatedFontSize != fontSizeState) fontSizeState = updatedFontSize

            val updatedExpandState = effectivePrefs.areMarkdownSectionsExpandedByDefault()
            if (updatedExpandState != expandAllState) expandAllState = updatedExpandState
        }
        effectivePrefs.registerOnChangeListener(listener)
        onDispose {
            effectivePrefs.unregisterOnChangeListener(listener)
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
                            onCheckboxChange = onCheckboxChange,
                            bodyFontSize = fontSizeState,
                            expandAll = expandAllState
                        )
                    } else {
                        // Keine Checkboxen gefunden, zeige einfache Ansicht
                        SimpleMarkdownView(markdownContent = markdownContent, bodyFontSize = fontSizeState, expandAll = expandAllState)
                    }
                } else {
                    // Einfache Markdown-Ansicht ohne Interaktion
                    SimpleMarkdownView(markdownContent = markdownContent, bodyFontSize = fontSizeState, expandAll = expandAllState)
                }
            }
        }
    }
}

/**
 * Datenklasse für eine Markdown-Sektion mit ### Überschrift
 */
private data class MarkdownSection(
    val heading: String,
    val content: List<String>
)

/**
 * Einfache Markdown-Ansicht - rendert grundlegendes Markdown
 * Gruppiert Inhalte zwischen ### Überschriften in Containern
 */
@Composable
private fun SimpleMarkdownView(markdownContent: String, bodyFontSize: Int, expandAll: Boolean = false) {
    val scrollState = rememberScrollState()

    // Parse Markdown in Sektionen
    val sections = parseMarkdownSections(markdownContent)

    // State für expandierte Sektionen - Key ist der Index der Sektion
    val expandedSections = remember { mutableStateMapOf<Int, Boolean>() }

    // Wenn expandAll sich ändert, aktualisiere alle Sektionen
    LaunchedEffect(expandAll) {
        sections.indices.forEach { index ->
            if (sections[index].heading.startsWith("### ")) {
                expandedSections[index] = expandAll
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        sections.forEachIndexed { index, section ->
            when {
                // Hauptüberschrift (# oder ##) - nicht in Card
                section.heading.startsWith("# ") || section.heading.startsWith("## ") -> {
                    // Render Überschrift
                    RenderMarkdownLine(section.heading, bodyFontSize)

                    // Render Inhalt
                    section.content.forEach { line ->
                        RenderMarkdownLine(line, bodyFontSize)
                    }
                }
                // ### Überschrift - in Card gruppieren mit Collapsible-Funktion
                section.heading.startsWith("### ") -> {
                    val isExpanded = expandedSections[index] ?: false // Default: collapsed

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            // Clickable Header mit Icon
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedSections[index] = !isExpanded }
                                    .padding(bottom = if (isExpanded) 8.dp else 0.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = section.heading.substring(4),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "Einklappen" else "Ausklappen"
                                )
                            }

                            // Render Inhalt der Sektion nur wenn expanded
                            if (isExpanded) {
                                section.content.forEach { line ->
                                    RenderMarkdownLine(line, bodyFontSize)
                                }
                            }
                        }
                    }
                }
                // Inhalt ohne Überschrift
                else -> {
                    section.content.forEach { line ->
                        RenderMarkdownLine(line, bodyFontSize)
                    }
                }
            }
        }
    }
}

/**
 * Rendert eine einzelne Markdown-Zeile
 */
@Composable
private fun RenderMarkdownLine(line: String, bodyFontSize: Int) {
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
            Column(modifier = Modifier.fillMaxWidth()) {
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
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = bodyFontSize.sp),
                        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically)
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            }
        }
        line.trim().startsWith("- ") -> {
            // Normaler Listeneintrag
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text("• ", style = MaterialTheme.typography.bodyMedium.copy(fontSize = bodyFontSize.sp))
                Text(
                    text = line.trim().substring(2),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = bodyFontSize.sp)
                )
            }
        }
        line.isNotBlank() -> {
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = bodyFontSize.sp),
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        else -> {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * Parst Markdown-Inhalt in Sektionen basierend auf Überschriften
 */
private fun parseMarkdownSections(markdownContent: String): List<MarkdownSection> {
    val lines = markdownContent.lines()
    val sections = mutableListOf<MarkdownSection>()
    var currentHeading = ""
    var currentContent = mutableListOf<String>()

    lines.forEach { line ->
        when {
            // Neue Überschrift gefunden
            line.startsWith("# ") || line.startsWith("## ") || line.startsWith("### ") -> {
                // Speichere vorherige Sektion wenn vorhanden
                if (currentHeading.isNotEmpty() || currentContent.isNotEmpty()) {
                    sections.add(MarkdownSection(currentHeading, currentContent.toList()))
                }
                // Starte neue Sektion
                currentHeading = line
                currentContent = mutableListOf()
            }
            // Normaler Inhalt
            else -> {
                currentContent.add(line)
            }
        }
    }

    // Füge letzte Sektion hinzu
    if (currentHeading.isNotEmpty() || currentContent.isNotEmpty()) {
        sections.add(MarkdownSection(currentHeading, currentContent.toList()))
    }

    return sections
}

/**
 * Interaktive Markdown-Ansicht mit Checkbox-Unterstützung
 * Zeigt Checkboxen basierend auf dem Checklist-Status an
 * Gruppiert ### Sektionen in Containern
 */
@Composable
private fun InteractiveMarkdownView(
    markdownContent: String,
    checklist: Checklist,
    onCheckboxChange: (itemId: String, checked: Boolean) -> Unit
    , bodyFontSize: Int
    , expandAll: Boolean = false
) {
    val scrollState = rememberScrollState()

    // State für expandierte Sektionen - Key ist der Section-Title
    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }

    // Wenn expandAll sich ändert, aktualisiere alle Sektionen
    LaunchedEffect(expandAll) {
        checklist.sections.forEach { section ->
            if (section.title.isNotEmpty()) {
                val isSubSection = section.title.length > 3 && !section.title.contains("Case") && !section.title.contains("Recovery")
                if (isSubSection) {
                    expandedSections[section.title] = expandAll
                }
            }
        }
    }

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

        // Sections mit Checkboxen und Überschriften - gruppiert in Cards
        checklist.sections.forEach { section ->
            // Section-Überschrift (## level) - ohne Card
            if (section.title.isNotEmpty() && section.title != checklist.title) {
                // Prüfe ob es eine ### Überschrift ist (Sub-Section)
                val isSubSection = section.title.length > 3 && !section.title.contains("Case") && !section.title.contains("Recovery")

                if (isSubSection) {
                    val isExpanded = expandedSections[section.title] ?: false // Default: collapsed

                    // ### Überschrift - in Card gruppieren mit Collapsible-Funktion
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            // Clickable Header mit Icon
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedSections[section.title] = !isExpanded }
                                    .padding(bottom = if (isExpanded) 8.dp else 0.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "Einklappen" else "Ausklappen"
                                )
                            }

                            // Checkbox-Items in dieser Section nur wenn expanded
                            if (isExpanded) {
                                section.items.forEach { item ->
                                    ChecklistItemRow(
                                        item = item,
                                        onCheckboxChange = onCheckboxChange,
                                        bodyFontSize = bodyFontSize
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ## Überschrift - ohne Card
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )

                    // Checkbox-Items in dieser Section
                    section.items.forEach { item ->
                        ChecklistItemRow(
                            item = item,
                            onCheckboxChange = onCheckboxChange,
                            bodyFontSize = bodyFontSize
                        )
                    }
                }
            } else {
                // Keine Überschrift oder Haupttitel
                section.items.forEach { item ->
                    ChecklistItemRow(
                        item = item,
                        onCheckboxChange = onCheckboxChange,
                        bodyFontSize = bodyFontSize
                    )
                }
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
    , bodyFontSize: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = bodyFontSize.sp),
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
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
