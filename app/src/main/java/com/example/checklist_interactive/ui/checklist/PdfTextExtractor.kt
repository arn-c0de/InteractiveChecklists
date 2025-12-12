package com.example.checklist_interactive.ui.checklist

import android.content.Context
import android.graphics.RectF
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.Writer

/**
 * Repräsentiert einen Text-Block mit Position auf einer PDF-Seite
 */
data class PdfTextBlock(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val fontSize: Float
)

/**
 * Extrahiert Text mit Positionen aus PDF-Dateien
 */
class PdfTextExtractor(private val context: Context) {

    private var cachedDocument: PDDocument? = null
    private var cachedFilePath: String? = null
    private val documentMutex = Mutex()

    init {
        // PDFBox für Android initialisieren
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Lädt das PDF-Dokument, verwendet Cache wenn möglich
     */
    private suspend fun getDocument(pdfFile: File): PDDocument = documentMutex.withLock {
        val currentPath = pdfFile.absolutePath

        // Wenn ein anderes Dokument gecacht ist, schließe es zuerst
        if (cachedFilePath != currentPath && cachedDocument != null) {
            try {
                cachedDocument?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            cachedDocument = null
            cachedFilePath = null
        }

        // Lade neues Dokument wenn nötig
        if (cachedDocument == null) {
            cachedDocument = PDDocument.load(pdfFile)
            cachedFilePath = currentPath
        }

        cachedDocument!!
    }

    /**
     * Gibt das gecachte Dokument frei
     */
    fun cleanup() {
        try {
            cachedDocument?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cachedDocument = null
        cachedFilePath = null
    }

    /**
     * Extrahiert Text-Blöcke für eine bestimmte Seite
     */
    suspend fun extractTextBlocks(pdfFile: File, pageIndex: Int): List<PdfTextBlock> = withContext(Dispatchers.IO) {
        val textBlocks = mutableListOf<PdfTextBlock>()

        try {
            val document = getDocument(pdfFile)

            val stripper = object : PDFTextStripper() {
                init {
                    startPage = pageIndex + 1
                    endPage = pageIndex + 1
                }

                override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                    if (text.trim().isEmpty()) return

                    // Gruppiere zusammenhängende Textpositionen
                    var currentText = ""
                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxX = Float.MIN_VALUE
                    var maxY = Float.MIN_VALUE
                    var fontSize = 0f

                    for (position in textPositions) {
                        currentText += position.unicode
                        val x = position.x
                        val y = position.y
                        val width = position.width
                        val height = position.height

                        minX = minOf(minX, x)
                        minY = minOf(minY, y)
                        maxX = maxOf(maxX, x + width)
                        maxY = maxOf(maxY, y + height)
                        fontSize = maxOf(fontSize, position.fontSize)
                    }

                    if (currentText.isNotBlank()) {
                        textBlocks.add(
                            PdfTextBlock(
                                text = currentText,
                                x = minX,
                                y = minY,
                                width = maxX - minX,
                                height = maxY - minY,
                                fontSize = fontSize
                            )
                        )
                    }
                }
            }

            stripper.getText(document)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            // Bei OOM-Fehler, versuche Cache zu leeren
            cleanup()
            System.gc()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        textBlocks
    }

    /**
     * Extrahiert den gesamten Text einer Seite
     */
    suspend fun extractPageText(pdfFile: File, pageIndex: Int): String = withContext(Dispatchers.IO) {
        try {
            val document = getDocument(pdfFile)
            val stripper = PDFTextStripper().apply {
                startPage = pageIndex + 1
                endPage = pageIndex + 1
            }
            stripper.getText(document)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            // Bei OOM-Fehler, versuche Cache zu leeren
            cleanup()
            System.gc()
            ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
