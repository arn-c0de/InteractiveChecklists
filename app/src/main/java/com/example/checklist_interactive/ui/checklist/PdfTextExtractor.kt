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
 * Represents a text block with position on a PDF page
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
 * Extracts positioned text from PDF files
 */
class PdfTextExtractor(private val context: Context) {

    private var cachedDocument: PDDocument? = null
    private var cachedFilePath: String? = null
    private val documentMutex = Mutex()

    init {
        // Initialize PDFBox for Android
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Loads the PDF document, using a cache when possible
     */
    private suspend fun getDocument(pdfFile: File): PDDocument = documentMutex.withLock {
        val currentPath = pdfFile.absolutePath

            // If a different document is cached, close it first
        if (cachedFilePath != currentPath && cachedDocument != null) {
            try {
                cachedDocument?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            cachedDocument = null
            cachedFilePath = null
        }

        // Load a new document when needed
        if (cachedDocument == null) {
            cachedDocument = PDDocument.load(pdfFile)
            cachedFilePath = currentPath
        }

        cachedDocument!!
    }

    /**
     * Releases the cached document
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
     * Extracts text blocks for a specific page
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

                    // Group contiguous text positions
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
            // On OOM error, try to clear cache
            cleanup()
            System.gc()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        textBlocks
    }

    /**
     * Extracts the entire text of a page
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
            // On OOM error, try to clear cache
            cleanup()
            System.gc()
            ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
