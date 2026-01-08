package com.example.checklist_interactive.ui.checklist.pdf

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
 * Extracts positioned text from PDF files using Apache PDFBox library
 */
class PdfTextExtractor(private val context: Context) {

    private val TAG = "PdfTextExtractor"

    /**
     * Cleanup resources used by the extractor.
     * Currently clears the global `PdfStructureParser` cache so parsers are re-created
     * on next use (helps free memory and resets state if needed).
     */
    fun cleanup() {
        try {
            PdfStructureParser.clearCache()
            android.util.Log.d(TAG, "PdfTextExtractor.cleanup: cleared PdfStructureParser cache")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "PdfTextExtractor.cleanup failed: ${e.message}")
        }
    }

    /**
     * Extracts text blocks for a specific page
     * Note: Native parser extracts text only, not precise positions
     * Returns single text block with full page text
     */
    suspend fun extractTextBlocks(pdfFile: File, pageIndex: Int): List<PdfTextBlock> = withContext(Dispatchers.IO) {
        val textBlocks = mutableListOf<PdfTextBlock>()

        try {
            // Reuse cached parser to avoid rebuilding page list on every extraction
            val parser = PdfStructureParser.getInstance(pdfFile)
            android.util.Log.d(TAG, "Using PdfStructureParser instance for ${pdfFile.absolutePath}")
            val pageText = parser.extractPageText(pageIndex)
            
            if (pageText.isNotBlank()) {
                // Return single block with all text (positions are approximate)
                textBlocks.add(
                    PdfTextBlock(
                        text = pageText,
                        x = 0f,
                        y = 0f,
                        width = 500f,
                        height = 700f,
                        fontSize = 12f
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text blocks: ${e.message}", e)
        }

        textBlocks
    }

    /**
     * Extracts the entire text of a page
     */
    suspend fun extractPageText(pdfFile: File, pageIndex: Int): String = withContext(Dispatchers.IO) {
        try {
            // Reuse cached parser to avoid rebuilding page list on every extraction
            val parser = PdfStructureParser.getInstance(pdfFile)
            android.util.Log.d(TAG, "Using PdfStructureParser instance for ${pdfFile.absolutePath}")
            parser.extractPageText(pageIndex)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting page text: ${e.message}", e)
            ""
        }
    }
}
