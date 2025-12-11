package com.example.checklist_interactive.ui.checklist

import android.content.Context
import android.graphics.RectF
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    init {
        // PDFBox für Android initialisieren
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Extrahiert Text-Blöcke für eine bestimmte Seite
     */
    suspend fun extractTextBlocks(pdfFile: File, pageIndex: Int): List<PdfTextBlock> = withContext(Dispatchers.IO) {
        val textBlocks = mutableListOf<PdfTextBlock>()

        try {
            PDDocument.load(pdfFile).use { document ->
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
            }
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
            PDDocument.load(pdfFile).use { document ->
                val stripper = PDFTextStripper().apply {
                    startPage = pageIndex + 1
                    endPage = pageIndex + 1
                }
                stripper.getText(document)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
