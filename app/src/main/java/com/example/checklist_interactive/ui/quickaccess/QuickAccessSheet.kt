package com.example.checklist_interactive.ui.quickaccess

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.text.ClickableText
import java.net.URLDecoder
import java.net.URLEncoder
import androidx.compose.ui.Alignment
import android.content.Intent
import android.net.Uri
import java.io.File
import androidx.core.content.FileProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.data.quicknotes.QuickNoteManager
import com.example.checklist_interactive.data.quicknotes.LinkedDocument
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color

// Helper to render markdown-style internal links as clickable annotated strings
private fun buildAnnotatedStringWithLinks(content: String): AnnotatedString {
    val regex = Regex("\\[([^]]+)]\\(internal://open\\?file=([^)&]+)(?:&page=(\\d+))?\\)")
    val builder = AnnotatedString.Builder()
    var last = 0
    regex.findAll(content).forEach { match ->
        val range = match.range
        if (range.first > last) builder.append(content.substring(last, range.first))
        val label = match.groups[1]?.value ?: ""
        val fileEnc = match.groups[2]?.value ?: ""
        val page = match.groups[3]?.value ?: ""
        val annotationValue = "$fileEnc|$page"
        val startIndex = builder.length
        builder.append(label)
        val endIndex = builder.length
        builder.addStringAnnotation(tag = "OPEN_LINK", annotation = annotationValue, start = startIndex, end = endIndex)
        builder.addStyle(SpanStyle(color = Color(0xFF0066CC), textDecoration = TextDecoration.Underline), startIndex, endIndex)
        last = range.last + 1
    }

    if (last < content.length) builder.append(content.substring(last))
    return builder.toAnnotatedString()
}

@Composable
private fun LinkedDocumentItem(
    document: LinkedDocument,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (document.fileName.endsWith(".pdf", ignoreCase = true))
                    Icons.Default.PictureAsPdf
                else
                    Icons.Default.Article,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (document.pageNumber != null) {
                    Text(
                        text = "Seite ${document.pageNumber + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Entfernen",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Quick Access Bottom Sheet with note-taking functionality
 * Appears as popup overlay while app continues in background
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAccessSheet(
    onDismiss: () -> Unit,
    onOpenDocument: ((filePath: String, pageNumber: Int?) -> Unit)? = null,
    currentDocumentPath: String? = null,
    currentDocumentName: String? = null,
    currentPageNumber: Int? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val noteManager = remember { QuickNoteManager(context) }
    val savedNote by noteManager.noteContent.collectAsState()
    val linkedDocuments by noteManager.linkedDocuments.collectAsState()
    val notes by noteManager.notes.collectAsState()
    val activeNoteId by noteManager.activeNoteId.collectAsState()
    var currentNote by remember { mutableStateOf(savedNote) }
    var hasChanges by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var showAdvancedEditor by remember { mutableStateOf(false) }
    var newTextInput by remember { mutableStateOf("") }

    // Update currentNote when savedNote changes
    LaunchedEffect(savedNote) {
        currentNote = savedNote
        hasChanges = false
    }

    // Auto-save: Speichere Notizen automatisch nach 2 Sekunden Inaktivität
    LaunchedEffect(currentNote, activeNoteId) {
        if (hasChanges) {
            kotlinx.coroutines.delay(2000L) // 2 Sekunden warten
            noteManager.saveNote(currentNote)
            hasChanges = false
        }
    }

    // Speichere beim Verlassen der Komponente
    DisposableEffect(Unit) {
        onDispose {
            if (hasChanges) {
                noteManager.saveNote(currentNote)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            // Speichere immer beim Schließen
            noteManager.saveNote(currentNote)
            onDismiss()
        },
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Notes,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Schnellnotizen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Pin-Button nur anzeigen, wenn ein aktuelles Dokument gesetzt ist
                    if (currentDocumentPath != null && currentDocumentName != null) {
                        IconButton(
                            onClick = {
                                // Append markdown-style internal link to the active note
                                val uri = "internal://open?file=${URLEncoder.encode(currentDocumentPath, "UTF-8")}${if (currentPageNumber != null) "&page=$currentPageNumber" else ""}"
                                val linkText = "\n🔖 [$currentDocumentName]($uri)\n"
                                val newContent = (currentNote ?: "") + linkText
                                noteManager.saveNoteContent(null, newContent)
                                currentNote = newContent
                                hasChanges = false
                            }
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pin")
                        }
                    }
                }
            }

            // Note Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                notes.forEach { note ->
                    OutlinedButton(
                        onClick = { noteManager.setActiveNote(note.id) },
                        modifier = Modifier
                            .height(36.dp)
                            .padding(end = 8.dp),
                    ) {
                        Text(text = if (note.title.isNotBlank()) note.title else "Notiz", maxLines = 1)
                        Spacer(modifier = Modifier.width(8.dp))
                        if (note.id == activeNoteId) {
                            IconButton(onClick = {
                                renameTargetId = note.id
                                renameText = note.title
                                showRenameDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Umbenennen", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { noteManager.removeNote(note.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Löschen", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                IconButton(onClick = {
                    val id = noteManager.addNote()
                    noteManager.setActiveNote(id)
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Neue Notiz")
                }
            }

            // Rename dialog
            if (showRenameDialog) {
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text("Notiz umbenennen") },
                    text = {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text("Titel") }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            renameTargetId?.let { noteManager.renameNote(it, renameText) }
                            showRenameDialog = false
                        }) { Text("Umbenennen") }
                    },
                    dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Abbrechen") } }
                )
            }

            // Quick Note Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Schnellnotiz",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row {
                    TextButton(onClick = { showAdvancedEditor = !showAdvancedEditor }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showAdvancedEditor) "Normal" else "Erweitert", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                if (showAdvancedEditor) {
                    // Advanced mode: full markdown editor with raw links
                    OutlinedTextField(
                        value = currentNote,
                        onValueChange = {
                            currentNote = it
                            hasChanges = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                        placeholder = {
                            Text("Markdown-Editor\n\nLinks: [Text](internal://open?file=...)")
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )
                } else {
                    // Normal mode: clickable links + editable text
                    val ann = buildAnnotatedStringWithLinks(currentNote)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ClickableText(
                            text = ann,
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp, max = 400.dp)
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                            onClick = { offset: Int ->
                                ann.getStringAnnotations("OPEN_LINK", offset, offset)
                                    .firstOrNull()?.let { span ->
                                        val value = span.item
                                        val parts = value.split("|")
                                        val file = URLDecoder.decode(parts.getOrNull(0) ?: "", "UTF-8")
                                        val page = parts.getOrNull(1)?.toIntOrNull()
                                        if (onOpenDocument != null) {
                                            onOpenDocument.invoke(file, page)
                                            onDismiss()
                                        } else {
                                            try {
                                                val f = File(file)
                                                val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
                                                val intent = Intent(Intent.ACTION_VIEW)
                                                intent.setDataAndType(uri, if (file.endsWith(".pdf", true)) "application/pdf" else "*/*")
                                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                // ignore
                                            }
                                        }
                                    }
                            }
                        )
                        // Editable text area below for adding new content
                        if (currentNote.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newTextInput,
                                onValueChange = { newTextInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Text hinzufügen...") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                maxLines = 3
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (newTextInput.isNotEmpty()) {
                                        currentNote = if (currentNote.isEmpty()) {
                                            newTextInput
                                        } else {
                                            "$currentNote\n$newTextInput"
                                        }
                                        newTextInput = ""
                                        hasChanges = true
                                    }
                                },
                                enabled = newTextInput.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Hinzufügen")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        currentNote = ""
                        hasChanges = true
                        noteManager.clearNote()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = currentNote.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Löschen")
                }

                Button(
                    onClick = {
                        noteManager.saveNote(currentNote)
                        hasChanges = false
                    },
                    modifier = Modifier.weight(1f),
                    enabled = hasChanges
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Speichern")
                }
            }

            // Info text
            if (hasChanges) {
                Text(
                    text = "Ungespeicherte Änderungen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else if (currentNote.isNotEmpty()) {
                Text(
                    text = "Notiz gespeichert",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Linked Documents Section
            if (linkedDocuments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Verlinkte Dokumente (${linkedDocuments.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = { noteManager.clearLinkedDocuments() }) {
                        Text("Alle löschen", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // List of linked documents
                linkedDocuments.forEach { doc ->
                    LinkedDocumentItem(
                        document = doc,
                        onOpen = {
                            onOpenDocument?.invoke(doc.filePath, doc.pageNumber)
                            onDismiss()
                        },
                        onDelete = { noteManager.removeLinkedDocument(doc.id) }
                    )
                }
            }
        }
    }
}


