package com.example.checklist_interactive.ui.checklist

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
 * Extracts positioned text from PDF files using native parsing
 */
class PdfTextExtractor(private val context: Context) {

    // Native parser - no caching needed (stateless)
    private val TAG = "PdfTextExtractor"

    /**
     * No cleanup needed - stateless parser
     */
    fun cleanup() {
        // Nothing to clean up
    }

    /**
     * Extracts text blocks for a specific page
     * Note: Native parser extracts text only, not precise positions
     * Returns single text block with full page text
     */
    suspend fun extractTextBlocks(pdfFile: File, pageIndex: Int): List<PdfTextBlock> = withContext(Dispatchers.IO) {
        val textBlocks = mutableListOf<PdfTextBlock>()

        try {
            val parser = PdfStructureParser(pdfFile)
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
            val parser = PdfStructureParser(pdfFile)
            parser.extractPageText(pageIndex)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting page text: ${e.message}", e)
            ""
        }
    }
}
