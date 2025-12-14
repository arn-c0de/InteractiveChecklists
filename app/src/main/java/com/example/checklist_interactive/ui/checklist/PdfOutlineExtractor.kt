package com.example.checklist_interactive.ui.checklist

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Data class representing a PDF outline item (bookmark/chapter)
 * with title, page number, and hierarchical level
 */
data class PdfOutlineItem(
    val title: String,
    val pageNumber: Int,
    val level: Int
)

/**
 * Extracts the document outline (table of contents / bookmarks) from a PDF file.
 * Uses native PDF parsing without external dependencies.
 */
class PdfOutlineExtractor(private val context: Context) {

    /**
     * Extracts outline items from a PDF file.
     * Returns a list of outline items with titles, page numbers, and hierarchical levels.
     * If no outline is found, returns an empty list.
     */
    suspend fun extractOutline(pdfFile: File): List<PdfOutlineItem> = withContext(Dispatchers.IO) {
        val outlineItems = mutableListOf<PdfOutlineItem>()
        try {
            val parser = PdfStructureParser(pdfFile)
            val parsed = parser.parseOutline()
            outlineItems.addAll(parsed)
            
            if (outlineItems.isEmpty()) {
                Log.d("PdfOutlineExtractor", "No outline found in ${pdfFile.name}")
            } else {
                Log.d("PdfOutlineExtractor", "Found ${outlineItems.size} outline items in ${pdfFile.name}")
            }
        } catch (e: Exception) {
            Log.e("PdfOutlineExtractor", "Failed to extract outline: ${e.message}", e)
        }
        outlineItems
    }
}

// Remove all old PDFBox-based code below
/*
            PDDocument.load(pdfFile).use { document ->
                // OLD PDFBox code removed
                /*
                val outline = document.documentCatalog.documentOutline
                if (outline == null) {
                    Log.d("PdfOutlineExtractor", "Document outline is null for ${pdfFile.name}")
                    // Try to log named destinations if available
                    try {
                        val names = document.documentCatalog.names
                        if (names != null) {
                            Log.d("PdfOutlineExtractor", "Document has names: $names")
                            // Try to extract named destinations as fallback
                            try {
                                val dests = names.dests
                                if (dests != null) {
                                    val map = dests.names
                                    if (map != null) {
                                        map.forEach { (k, v) ->
                                            try {
                                                // v might be a destination; try to resolve page
                                                var pageIndex: Int? = null
                                                val pagesList = ArrayList<PDPage>()
                                                val iter = document.pages.iterator()
                                                while (iter.hasNext()) pagesList.add(iter.next())
                                                val dest = v
                                                val page = try { dest.javaClass.getMethod("getPage").invoke(dest) } catch (_: Exception) { null }
                                                if (page != null && page is PDPage) {
                                                    val targetCos = page.cosObject
                                                    for (i in pagesList.indices) {
                                                        if (pagesList[i].cosObject == targetCos) {
                                                            pageIndex = i
                                                            break
                                                        }
                                                    }
                                                } else {
                                                    val pageNum = try { dest.javaClass.getMethod("getPageNumber").invoke(dest) as? Int } catch (_: Exception) { null }
                                                    if (pageNum != null && pageNum >= 0) pageIndex = pageNum
                                                }
                                                if (pageIndex != null) {
                                                    outlineItems.add(PdfOutlineItem(k ?: "", pageIndex, 0))
                                                    Log.d("PdfOutlineExtractor", "Named dest: $k -> page $pageIndex")
                                                }
                                            } catch (e: Exception) {
                                                // ignore
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                }
                if (outline != null) {
                    Log.d("PdfOutlineExtractor", "Document outline found for ${pdfFile.name}")
                    val first = outline.firstChild
                    if (first != null) {
                        // Build list of pages for reliable index lookup
                        val pagesList = ArrayList<PDPage>()
                        val iter = document.pages.iterator()
                        while (iter.hasNext()) pagesList.add(iter.next())
                        processOutlineNode(first, document, pagesList, outlineItems, level = 0)
                    }
                }
            }
        } catch (e: Exception) {
            // If outline extraction fails, return empty list
            Log.d("PdfOutlineExtractor", "Failed to extract outline: ${e.message}")
        }
        outlineItems
    } // OLD CODE END
    */

    /**
     * OLD: Recursively processes outline nodes to build a flat list with level information
     * (replaced by PdfStructureParser)
     */
    /*
    private fun processOutlineNode(
        node: PDOutlineItem?,
        document: PDDocument,
        pagesList: List<PDPage>,
        items: MutableList<PdfOutlineItem>,
        level: Int
    ) {
        var current = node
        while (current != null) {
                try {
                val title = current.title?.trim().takeIf { !it.isNullOrEmpty() } ?: "Unnamed"
                Log.d("PdfOutlineExtractor", "Visiting outline item: \"$title\" level=$level")
                var pageNumber: Int? = null
                // 1. Versuche Destination direkt
                val destination = current.destination
                if (destination != null) {
                    try {
                        if (destination is PDPageDestination) {
                            val page = destination.page
                            if (page != null && page is PDPage) {
                                val targetCos = page.cosObject
                                for (i in pagesList.indices) {
                                    if (pagesList[i].cosObject == targetCos) {
                                        pageNumber = i
                                        break
                                    }
                                }
                            } else {
                                val pageNum = destination.pageNumber
                                if (pageNum >= 0) pageNumber = pageNum
                            }
                        } else if (destination is PDNamedDestination) {
                            // resolve named destination
                            val destName = destination.namedDestination
                            val named = document.documentCatalog.names
                            // leave fallback; resolving may not be straightforward
                        } else {
                            // unknown destination type, try reflection fallback
                            val page = try { destination.javaClass.getMethod("getPage").invoke(destination) } catch (_: Exception) { null }
                            if (page != null) {
                                pageNumber = pagesList.indexOf(page).takeIf { it >= 0 }
                            }
                        }
                    } catch (_: Exception) {}
                }
                // 2. Versuche Action (GoTo)
                if (pageNumber == null) {
                    val action = current.action
                    if (action != null) {
                        try {
                            if (action is PDActionGoTo) {
                                val destObj = action.destination
                                if (destObj is PDPageDestination) {
                                    val page = destObj.page
                                    if (page != null) pageNumber = pagesList.indexOf(page).takeIf { it >= 0 }
                                    else pageNumber = destObj.pageNumber
                                } else {
                                    // fallback via reflection
                                    val dest = try { action.javaClass.getMethod("getDestination").invoke(action) } catch (_: Exception) { null }
                                    if (dest != null) {
                                        val page = try { dest.javaClass.getMethod("getPage").invoke(dest) } catch (_: Exception) { null }
                                        if (page != null && page is PDPage) {
                                            val targetCos = page.cosObject
                                            for (i in pagesList.indices) {
                                                if (pagesList[i].cosObject == targetCos) {
                                                    pageNumber = i
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
                if (pageNumber != null && pageNumber >= 0) {
                    Log.d("PdfOutlineExtractor", "Outline item found: title=\"$title\" page=$pageNumber level=$level")
                    items.add(PdfOutlineItem(title, pageNumber, level))
                }
                if (current.hasChildren()) {
                    processOutlineNode(current.firstChild, document, pagesList, items, level + 1)
                }
            } catch (_: Exception) {
                // Skip problematic outline items
            }
            current = current.nextSibling
        }
    }
    */