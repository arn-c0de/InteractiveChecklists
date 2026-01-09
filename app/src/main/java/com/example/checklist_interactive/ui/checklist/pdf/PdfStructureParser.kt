package com.example.checklist_interactive.ui.checklist.pdf

import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/**
 * PDF parser implementation using PDFBox-Android library.
 * Provides Android-compatible PDF parsing without AWT dependencies.
 *
 * This uses PDFBox-Android (tom-roush fork), which is specifically ported for Android
 * and has proper 16 KB page alignment for Android 15+ compatibility.
 */
class PdfStructureParser(private val context: Context, private val pdfFile: File) {

    private val TAG = "PdfStructureParser"

    init {
        // Initialize PDFBox-Android (required once per application)
        try {
            PDFBoxResourceLoader.init(context)
        } catch (e: Exception) {
            // Already initialized, ignore
        }
        Log.d(TAG, "Created PdfStructureParser for ${pdfFile.absolutePath}")
    }

    companion object {
        private val globalCache = mutableMapOf<String, PdfStructureParser>()

        /**
         * Returns a cached instance of the parser for the given file.
         * This helps avoid re-loading the same PDF multiple times.
         */
        fun getInstance(context: Context, file: File): PdfStructureParser {
            val path = file.absolutePath
            synchronized(globalCache) {
                return globalCache.getOrPut(path) {
                    PdfStructureParser(context, file)
                }
            }
        }

        /**
         * Clears the parser cache. Useful for memory management.
         */
        fun clearCache() {
            synchronized(globalCache) {
                globalCache.clear()
            }
        }
    }

    /**
     * Parses the PDF document outline (bookmarks/table of contents) using PDFBox-Android.
     * Returns a list of outline items with their titles, page numbers, and hierarchical levels.
     */
    fun parseOutline(): List<PdfOutlineItem> {
        val result = mutableListOf<PdfOutlineItem>()

        try {
            PDDocument.load(pdfFile).use { document ->
                val outline = document.documentCatalog.documentOutline
                if (outline == null) {
                    Log.d(TAG, "No outline found in ${pdfFile.name}")
                    return emptyList()
                }

                // Recursively traverse the outline tree
                processOutlineItem(outline.firstChild, document, result, level = 0)

                Log.d(TAG, "Parsed ${result.size} outline items from ${pdfFile.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing outline: ${e.message}", e)
        }

        return result
    }

    /**
     * Recursively processes outline items and their children.
     */
    private fun processOutlineItem(
        item: PDOutlineItem?,
        document: PDDocument,
        result: MutableList<PdfOutlineItem>,
        level: Int
    ) {
        var current = item
        while (current != null) {
            try {
                val title = current.title ?: "Untitled"

                // Get the page number (0-based index)
                val pageNumber = try {
                    val dest = current.destination
                    if (dest != null) {
                        when (dest) {
                            is com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination -> {
                                val page = dest.page
                                if (page != null) {
                                    document.pages.indexOf(page)
                                } else {
                                    -1
                                }
                            }
                            else -> -1
                        }
                    } else {
                        // Try action destination
                        val action = current.action
                        if (action != null && action is com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo) {
                            val gotoAction = action
                            val gotoDest = gotoAction.destination
                            if (gotoDest is com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination) {
                                val page = gotoDest.page
                                if (page != null) {
                                    document.pages.indexOf(page)
                                } else {
                                    -1
                                }
                            } else {
                                -1
                            }
                        } else {
                            -1
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error getting page number for outline item '$title': ${e.message}")
                    -1
                }

                // Add to result if we have a valid page number
                if (pageNumber >= 0) {
                    result.add(PdfOutlineItem(
                        title = title,
                        pageNumber = pageNumber,
                        level = level
                    ))
                    Log.d(TAG, "Outline item: level=$level, page=$pageNumber, title='$title'")
                } else {
                    Log.w(TAG, "Skipping outline item with invalid page: '$title'")
                }

                // Process children recursively
                if (current.hasChildren()) {
                    processOutlineItem(current.firstChild, document, result, level + 1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing outline item: ${e.message}", e)
            }

            // Move to next sibling
            current = current.nextSibling
        }
    }

    /**
     * Extracts text from a specific page using PDFBox-Android.
     * @param pageIndex 0-based page index
     * @return The extracted text from the page
     */
    fun extractPageText(pageIndex: Int): String {
        return try {
            PDDocument.load(pdfFile).use { document ->
                if (pageIndex < 0 || pageIndex >= document.numberOfPages) {
                    Log.w(TAG, "Invalid page index: $pageIndex (document has ${document.numberOfPages} pages)")
                    return ""
                }

                val stripper = PDFTextStripper()
                // PDFTextStripper uses 1-based page numbers
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1

                val text = stripper.getText(document)
                Log.d(TAG, "Extracted ${text.length} characters from page $pageIndex")
                text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from page $pageIndex: ${e.message}", e)
            ""
        }
    }
}
