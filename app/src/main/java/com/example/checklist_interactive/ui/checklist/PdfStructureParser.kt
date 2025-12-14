package com.example.checklist_interactive.ui.checklist

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Native PDF parser that extracts document structure without external libraries.
 * Parses PDF objects, catalog, outline tree, and destinations directly from PDF format.
 */
class PdfStructureParser(private val pdfFile: File) {
    
    private val TAG = "PdfStructureParser"
    
    // PDF cross-reference table: maps object numbers to byte offsets
    private val xrefTable = mutableMapOf<Int, Long>()
    
    // Cached parsed objects
    private val objectCache = mutableMapOf<Int, PdfObject>()
    
    /**
     * Parses the PDF document outline (bookmarks/table of contents)
     */
    fun parseOutline(): List<PdfOutlineItem> {
        val result = mutableListOf<PdfOutlineItem>()

        try {
            RandomAccessFile(pdfFile, "r").use { raf ->
                // 1. Find and parse xref table
                Log.d(TAG, "Step 1: Parsing xref table")
                if (!parseXrefTable(raf)) {
                    Log.w(TAG, "Failed to parse xref table")
                    return emptyList()
                }
                Log.d(TAG, "XRef table has ${xrefTable.size} entries")

                // 2. Get catalog object reference from trailer
                Log.d(TAG, "Step 2: Getting catalog reference")
                val catalogRef = getCatalogReference(raf)
                if (catalogRef == null) {
                    Log.w(TAG, "No catalog found")
                    return emptyList()
                }
                Log.d(TAG, "Catalog reference: $catalogRef")

                // 3. Parse catalog object
                Log.d(TAG, "Step 3: Parsing catalog object $catalogRef")
                val catalog = parseObject(raf, catalogRef)
                if (catalog == null) {
                    Log.w(TAG, "Failed to parse catalog")
                    return emptyList()
                }
                Log.d(TAG, "Catalog dict: ${catalog.dictContent.keys}")

                // 4. Get outline reference from catalog
                Log.d(TAG, "Step 4: Getting outline reference from catalog")
                val outlineValue = catalog.getDictValue("Outlines")
                Log.d(TAG, "Outlines raw value: $outlineValue (type: ${outlineValue?.javaClass?.simpleName})")
                val outlineRef = outlineValue as? Int
                if (outlineRef == null) {
                    Log.d(TAG, "No Outlines entry in catalog or wrong type (available keys: ${catalog.dictContent.keys})")
                    Log.d(TAG, "All catalog values: ${catalog.dictContent}")
                    return emptyList()
                }
                Log.d(TAG, "Outline reference: $outlineRef")

                // 5. Parse outline dictionary
                Log.d(TAG, "Step 5: Parsing outline dictionary $outlineRef")
                val outlineDict = parseObject(raf, outlineRef)
                if (outlineDict == null) {
                    Log.w(TAG, "Failed to parse outline dictionary $outlineRef")
                    val offset = xrefTable[outlineRef]
                    if (offset == null) {
                        Log.w(TAG, "No xref entry for object $outlineRef")
                    } else {
                        Log.d(TAG, "Outline object $outlineRef is at offset $offset")
                    }
                    return emptyList()
                }
                Log.d(TAG, "Outline dict: ${outlineDict.dictContent}")

                // 6. Get first outline item
                Log.d(TAG, "Step 6: Getting first outline item")
                val firstRef = outlineDict.getDictValue("First") as? Int
                if (firstRef == null) {
                    Log.d(TAG, "No First entry in outline (available keys: ${outlineDict.dictContent.keys})")
                    return emptyList()
                }
                Log.d(TAG, "First outline item reference: $firstRef")

                // 7. Build page list for page number resolution
                Log.d(TAG, "Step 7: Building page list")
                val pages = buildPageList(raf, catalog)
                Log.d(TAG, "Found ${pages.size} pages")

                // 8. Recursively parse outline tree
                Log.d(TAG, "Step 8: Parsing outline tree starting from $firstRef")
                parseOutlineItems(raf, firstRef, pages, result, level = 0)
                Log.d(TAG, "Parsed ${result.size} outline items")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing outline: ${e.message}", e)
        }

        return result
    }
    
    /**
     * Extracts text content from a specific page
     */
    fun extractPageText(pageIndex: Int): String {
        val textBuilder = StringBuilder()
        
        try {
            RandomAccessFile(pdfFile, "r").use { raf ->
                if (!parseXrefTable(raf)) return ""
                
                val catalogRef = getCatalogReference(raf) ?: return ""
                val catalog = parseObject(raf, catalogRef) ?: return ""
                val pages = buildPageList(raf, catalog)
                
                if (pageIndex !in pages.indices) return ""
                
                val pageRef = pages[pageIndex]
                val page = parseObject(raf, pageRef) ?: return ""
                
                // Get content stream
                val contentsRef = page.getDictValue("Contents") as? Int ?: return ""
                val contents = parseObject(raf, contentsRef) ?: return ""
                
                // Decode and parse content stream
                val streamData = contents.streamData ?: return ""
                parseContentStream(streamData, textBuilder)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text: ${e.message}", e)
        }
        
        return textBuilder.toString()
    }
    
    // ==================== Private Helper Methods ====================
    
    /**
     * Parses the cross-reference (xref) table at the end of the PDF
     */
    private fun parseXrefTable(raf: RandomAccessFile): Boolean {
        try {
            // Find "startxref" keyword from end of file
            val fileSize = raf.length()
            val searchSize = 1024L.coerceAtMost(fileSize)
            raf.seek(fileSize - searchSize)

            val buffer = ByteArray(searchSize.toInt())
            raf.read(buffer)
            val tail = String(buffer, StandardCharsets.ISO_8859_1)

            val startxrefIdx = tail.lastIndexOf("startxref")
            if (startxrefIdx < 0) {
                Log.w(TAG, "startxref keyword not found in PDF")
                return false
            }

            // Extract xref offset
            val afterStartxref = tail.substring(startxrefIdx + 9).trim()
            Log.d(TAG, "After startxref: '${afterStartxref.take(50)}'")
            val offsetStr = afterStartxref.split("\\s+".toRegex())[0]
            val xrefOffset = offsetStr.toLongOrNull()
            if (xrefOffset == null) {
                Log.w(TAG, "Failed to parse xref offset from: '$offsetStr'")
                return false
            }

            Log.d(TAG, "Found xref offset: $xrefOffset")

            // Parse xref table
            raf.seek(xrefOffset)
            val xrefLine = readLine(raf)
            Log.d(TAG, "First line at xref offset: '$xrefLine'")

            if (!xrefLine.trim().startsWith("xref")) {
                Log.w(TAG, "Expected 'xref' keyword, got: '$xrefLine'")
                // This might be an xref stream (PDF 1.5+), not a traditional table
                // Try to parse as object stream
                return parseXrefStream(raf, xrefOffset)
            }

            // Parse xref subsections
            var entryCount = 0
            while (true) {
                val line = readLine(raf).trim()
                if (line.startsWith("trailer")) break
                if (line.isEmpty()) continue

                val parts = line.split("\\s+".toRegex())
                if (parts.size == 2) {
                    val startObj = parts[0].toIntOrNull() ?: continue
                    val count = parts[1].toIntOrNull() ?: continue

                    Log.d(TAG, "Parsing xref subsection: start=$startObj count=$count")

                    // Read entries
                    for (i in 0 until count) {
                        val entry = readLine(raf)
                        if (entry.length >= 18) {
                            val offset = entry.substring(0, 10).trim().toLongOrNull()
                            val inUse = entry.substring(17, 18) == "n"
                            if (offset != null && inUse) {
                                xrefTable[startObj + i] = offset
                                entryCount++
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "Parsed $entryCount xref entries")
            return entryCount > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing xref: ${e.message}", e)
            return false
        }
    }

    /**
     * Attempts to parse an xref stream (PDF 1.5+)
     */
    private fun parseXrefStream(raf: RandomAccessFile, xrefOffset: Long): Boolean {
        try {
            Log.d(TAG, "Attempting to parse as xref stream at offset $xrefOffset")
            raf.seek(xrefOffset)

            // Read object header
            val header = readLine(raf)
            Log.d(TAG, "XRef stream object header: '$header'")

            // Check if it's an object definition (e.g., "123 0 obj")
            if (!header.matches(Regex("\\d+\\s+\\d+\\s+obj"))) {
                Log.w(TAG, "Not a valid object header for xref stream")
                return false
            }

            // Read the xref stream dictionary to check for /Prev
            raf.seek(xrefOffset)
            readLine(raf) // Skip header
            var attempts = 0
            while (attempts < 10) {
                val pos = raf.filePointer
                val line = readLine(raf)
                if (line.contains("<<")) {
                    raf.seek(pos)
                    break
                }
                attempts++
            }

            val dict = readDictionary(raf)
            Log.d(TAG, "XRef stream dict keys: ${dict.keys}")

            // Try to extract /Index to know which objects this stream defines
            val indexValue = dict["Index"]
            Log.d(TAG, "XRef stream /Index value: $indexValue")

            var objRanges = mutableListOf<Pair<Int, Int>>()
            if (indexValue is String) {
                // Parse [start count start count ...] format
                val nums = indexValue.replace("[", "").replace("]", "").trim()
                    .split("\\s+".toRegex())
                    .mapNotNull { it.toIntOrNull() }
                for (i in nums.indices step 2) {
                    if (i + 1 < nums.size) {
                        objRanges.add(Pair(nums[i], nums[i + 1]))
                    }
                }
            }

            Log.d(TAG, "Object ranges in this xref stream: $objRanges")

            // Scan file for objects in these ranges
            if (objRanges.isNotEmpty()) {
                var foundCount = 0
                for ((start, count) in objRanges) {
                    foundCount += scanForObjectRange(raf, start, start + count - 1)
                }
                Log.d(TAG, "Scanned and found $foundCount objects from /Index ranges")
            }

            // Check for /Prev pointing to an older xref table
            val prevOffset = (dict["Prev"] as? Int)?.toLong() ?: (dict["Prev"] as? String)?.toLongOrNull()
            if (prevOffset != null && prevOffset > 0) {
                Log.d(TAG, "Found /Prev pointing to offset $prevOffset, trying to parse previous xref")
                raf.seek(prevOffset)
                val prevLine = readLine(raf)
                Log.d(TAG, "Previous xref line: '$prevLine'")

                if (prevLine.trim().startsWith("xref")) {
                    // It's a traditional xref table! Parse it
                    Log.d(TAG, "Previous xref is traditional format, parsing it")
                    raf.seek(prevOffset)
                    parseTraditionalXref(raf)
                } else if (prevLine.matches(Regex("\\d+\\s+\\d+\\s+obj"))) {
                    // It's another xref stream, recurse
                    Log.d(TAG, "Previous xref is also a stream, recursing")
                    parseXrefStream(raf, prevOffset)
                }
            }

            return xrefTable.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing xref stream: ${e.message}", e)
            return false
        }
    }

    /**
     * Scans file for objects in a specific range using efficient chunk-based reading
     * Returns number of objects found
     */
    private fun scanForObjectRange(raf: RandomAccessFile, startObj: Int, endObj: Int): Int {
        try {
            Log.d(TAG, "Fast scanning for objects $startObj to $endObj")
            val fileSize = raf.length()
            val chunkSize = 524288 // 512KB chunks
            val overlap = 256 // Overlap to catch objects at boundaries
            val objPattern = Regex("(\\d+)\\s+\\d+\\s+obj")
            val foundObjects = mutableSetOf<Int>()

            var position = 0L
            raf.seek(0)

            while (position < fileSize) {
                val readSize = (fileSize - position).coerceAtMost(chunkSize.toLong()).toInt()
                val buffer = ByteArray(readSize)
                raf.read(buffer)
                val chunk = String(buffer, StandardCharsets.ISO_8859_1)

                // Find all object definitions in this chunk
                objPattern.findAll(chunk).forEach { match ->
                    val objNum = match.groupValues[1].toIntOrNull()
                    if (objNum != null && objNum in startObj..endObj && !foundObjects.contains(objNum)) {
                        // Calculate absolute file position
                        val objPos = position + match.range.first
                        xrefTable[objNum] = objPos
                        foundObjects.add(objNum)
                    }
                }

                // Progress
                if (foundObjects.size % 50 == 0 && foundObjects.isNotEmpty()) {
                    Log.d(TAG, "Found ${foundObjects.size} objects, ${position * 100 / fileSize}% scanned")
                }

                // Early exit if all found
                if (foundObjects.size >= (endObj - startObj + 1)) break

                // Move forward with overlap
                position += readSize - overlap
                raf.seek(position.coerceAtLeast(0))
            }

            Log.d(TAG, "Found ${foundObjects.size}/${endObj - startObj + 1} objects in range")
            return foundObjects.size
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning: ${e.message}", e)
            return 0
        }
    }

    /**
     * Parses a traditional xref table (for /Prev chains)
     */
    private fun parseTraditionalXref(raf: RandomAccessFile): Boolean {
        try {
            val xrefLine = readLine(raf)
            if (!xrefLine.trim().startsWith("xref")) {
                Log.w(TAG, "Not a traditional xref table")
                return false
            }

            var entryCount = 0
            while (true) {
                val line = readLine(raf).trim()
                if (line.startsWith("trailer")) break
                if (line.isEmpty()) continue

                val parts = line.split("\\s+".toRegex())
                if (parts.size == 2) {
                    val startObj = parts[0].toIntOrNull() ?: continue
                    val count = parts[1].toIntOrNull() ?: continue

                    for (i in 0 until count) {
                        val entry = readLine(raf)
                        if (entry.length >= 18) {
                            val offset = entry.substring(0, 10).trim().toLongOrNull()
                            val inUse = entry.substring(17, 18) == "n"
                            if (offset != null && inUse) {
                                xrefTable[startObj + i] = offset
                                entryCount++
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "Parsed traditional xref: $entryCount entries")
            return entryCount > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing traditional xref: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Gets catalog object reference from trailer
     */
    private fun getCatalogReference(raf: RandomAccessFile): Int? {
        try {
            // Find trailer
            val fileSize = raf.length()
            val searchSize = 2048L.coerceAtMost(fileSize)
            raf.seek(fileSize - searchSize)

            val buffer = ByteArray(searchSize.toInt())
            raf.read(buffer)
            val tail = String(buffer, StandardCharsets.ISO_8859_1)

            Log.d(TAG, "Last ${searchSize} bytes of file: '${tail.takeLast(200)}'")

            val trailerIdx = tail.lastIndexOf("trailer")
            if (trailerIdx < 0) {
                Log.w(TAG, "No 'trailer' keyword found in last $searchSize bytes")
                // For xref streams, the catalog might be in the xref stream object itself
                // Try to parse the xref stream object to get Root
                return getCatalogFromXrefStream(raf)
            }

            val trailerDict = tail.substring(trailerIdx + 7)
            Log.d(TAG, "Trailer dict content: '${trailerDict.take(200)}'")
            val catalogRef = extractReference(trailerDict, "Root")
            Log.d(TAG, "Extracted catalog ref: $catalogRef")
            return catalogRef
        } catch (e: Exception) {
            Log.e(TAG, "Error getting catalog: ${e.message}", e)
            return null
        }
    }

    /**
     * Tries to get catalog reference from xref stream object (PDF 1.5+)
     */
    private fun getCatalogFromXrefStream(raf: RandomAccessFile): Int? {
        try {
            Log.d(TAG, "Attempting to get catalog from xref stream")
            // Find startxref to locate the xref stream object
            val fileSize = raf.length()
            val searchSize = 1024L.coerceAtMost(fileSize)
            raf.seek(fileSize - searchSize)

            val buffer = ByteArray(searchSize.toInt())
            raf.read(buffer)
            val tail = String(buffer, StandardCharsets.ISO_8859_1)

            val startxrefIdx = tail.lastIndexOf("startxref")
            if (startxrefIdx < 0) return null

            val offsetStr = tail.substring(startxrefIdx + 9).trim().split("\\s+".toRegex())[0]
            val xrefOffset = offsetStr.toLongOrNull() ?: return null

            // Read the xref stream object
            raf.seek(xrefOffset)
            val objHeader = readLine(raf)
            Log.d(TAG, "XRef stream object header for catalog lookup: '$objHeader'")

            // Read the raw content to find Root reference
            // The dictionary might be on multiple lines or contain nested structures
            val dictContent = StringBuilder()
            var depth = 0
            var foundStart = false

            // Read up to 4KB to capture the full dictionary
            var bytesRead = 0
            while (bytesRead < 4096 && raf.filePointer < raf.length()) {
                val c = raf.read()
                if (c < 0) break
                val ch = c.toChar()
                dictContent.append(ch)
                bytesRead++

                if (ch == '<' && dictContent.length > 1 && dictContent[dictContent.length - 2] == '<') {
                    foundStart = true
                    depth++
                } else if (ch == '>' && dictContent.length > 1 && dictContent[dictContent.length - 2] == '>') {
                    depth--
                    if (foundStart && depth == 0) break
                }
            }

            val dictStr = dictContent.toString()
            Log.d(TAG, "XRef stream raw dict (first 500 chars): '${dictStr.take(500)}'")

            // Try to extract Root reference using regex
            val rootPattern = Regex("/Root\\s+(\\d+)\\s+\\d+\\s+R")
            val match = rootPattern.find(dictStr)
            if (match != null) {
                val rootRef = match.groupValues[1].toIntOrNull()
                Log.d(TAG, "Found Root reference in xref stream: $rootRef")
                return rootRef
            }

            Log.w(TAG, "Could not find /Root in xref stream dictionary")

            // Fallback: try parsing as dictionary
            raf.seek(xrefOffset)
            readLine(raf) // Skip header
            var attempts = 0
            while (attempts < 10) {
                val pos = raf.filePointer
                val line = readLine(raf)
                if (line.contains("<<")) {
                    raf.seek(pos)
                    break
                }
                attempts++
            }

            val dict = readDictionary(raf)
            Log.d(TAG, "XRef stream dict parsed: $dict")

            val rootRef = dict["Root"] as? Int
            Log.d(TAG, "Catalog ref from parsed dict: $rootRef")
            return rootRef
        } catch (e: Exception) {
            Log.e(TAG, "Error getting catalog from xref stream: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Parses a PDF object at given reference number
     */
    private fun parseObject(raf: RandomAccessFile, objNum: Int): PdfObject? {
        // Check cache first
        objectCache[objNum]?.let { return it }

        val offset = xrefTable[objNum]
        if (offset == null) {
            Log.w(TAG, "Object $objNum not found in xref table")
            return null
        }

        try {
            raf.seek(offset)
            val header = readLine(raf)

            // Skip to dictionary start
            var dictStart = -1L
            var line: String
            var attempts = 0
            while (attempts < 20) {
                val pos = raf.filePointer
                line = readLine(raf)
                if (line.contains("<<")) {
                    dictStart = pos
                    break
                }
                attempts++
            }

            if (dictStart < 0) {
                Log.w(TAG, "Object $objNum: No dictionary found within 20 lines (header: '$header')")
                return null
            }

            raf.seek(dictStart)

            // For debugging: read raw dictionary content first
            val rawDictBuilder = StringBuilder()
            var depth = 0
            var foundStart = false
            val startPos = raf.filePointer
            var bytesRead = 0

            while (bytesRead < 2048 && raf.filePointer < raf.length()) {
                val c = raf.read()
                if (c < 0) break
                val ch = c.toChar()
                rawDictBuilder.append(ch)
                bytesRead++

                if (ch == '<' && rawDictBuilder.length > 1 && rawDictBuilder[rawDictBuilder.length - 2] == '<') {
                    foundStart = true
                    depth++
                } else if (ch == '>' && rawDictBuilder.length > 1 && rawDictBuilder[rawDictBuilder.length - 2] == '>') {
                    depth--
                    if (foundStart && depth == 0) break
                }
            }

            val rawDict = rawDictBuilder.toString()
            if (objNum == 7966) { // Log catalog object specifically
                Log.d(TAG, "Raw catalog dict (obj $objNum): '$rawDict'")
            }

            // Reset to start and parse normally
            raf.seek(startPos)
            val dictContent = readDictionary(raf)

            val obj = PdfObject(objNum, dictContent)
            
            // Check if object has stream
            val streamPos = raf.filePointer
            val nextLine = readLine(raf).trim()
            if (nextLine == "stream") {
                val lengthRef = dictContent["Length"]
                val length = when (lengthRef) {
                    is Int -> {
                        // Indirect reference
                        val lengthObj = parseObject(raf, lengthRef)
                        lengthObj?.dictContent?.get("value") as? Int ?: 0
                    }
                    is String -> lengthRef.toIntOrNull() ?: 0
                    else -> 0
                }
                
                if (length > 0) {
                    raf.seek(streamPos + 7) // Skip "stream\n"
                    val streamBytes = ByteArray(length)
                    raf.read(streamBytes)
                    obj.streamData = streamBytes
                }
            }
            
            objectCache[objNum] = obj
            return obj
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing object $objNum: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Reads a PDF dictionary from current position
     */
    private fun readDictionary(raf: RandomAccessFile): Map<String, Any> {
        val dict = mutableMapOf<String, Any>()
        val buffer = StringBuilder()
        var depth = 0

        try {
            var c: Int
            while (raf.filePointer < raf.length()) {
                c = raf.read()
                if (c < 0) break

                val ch = c.toChar()
                buffer.append(ch)

                if (ch == '<' && raf.filePointer < raf.length()) {
                    val nextPos = raf.filePointer
                    val next = raf.read()
                    if (next >= 0 && next.toChar() == '<') {
                        buffer.append('<')
                        depth++
                        if (depth == 1) buffer.clear().append("<<")
                    } else {
                        // Not a second '<', seek back
                        raf.seek(nextPos)
                    }
                } else if (ch == '>' && raf.filePointer < raf.length()) {
                    val nextPos = raf.filePointer
                    val next = raf.read()
                    if (next >= 0 && next.toChar() == '>') {
                        buffer.append('>')
                        depth--
                        if (depth == 0) break
                    } else {
                        // Not a second '>', seek back
                        raf.seek(nextPos)
                    }
                }
            }

            // Parse dictionary content
            val content = buffer.toString()
            parseDictionaryContent(content, dict)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading dictionary: ${e.message}", e)
        }

        return dict
    }
    
    /**
     * Parses dictionary content string into key-value map
     */
    private fun parseDictionaryContent(content: String, dict: MutableMap<String, Any>) {
        try {
            val trimmed = content.removePrefix("<<").removeSuffix(">>").trim()

            // New robust parser using regex to find /Name value pairs
            // This handles cases where there's no space between entries (e.g., "R/Name")
            var pos = 0
            while (pos < trimmed.length) {
                // Skip whitespace
                while (pos < trimmed.length && trimmed[pos].isWhitespace()) pos++
                if (pos >= trimmed.length) break

                // Look for /Name
                if (trimmed[pos] != '/') {
                    pos++
                    continue
                }

                // Extract name (can contain colons, alphanumerics, etc., but not delimiters)
                val nameStart = pos + 1
                var nameEnd = nameStart
                while (nameEnd < trimmed.length && !trimmed[nameEnd].isWhitespace() && trimmed[nameEnd] != '/' && trimmed[nameEnd] != '<' && trimmed[nameEnd] != '[' && trimmed[nameEnd] != '>' && trimmed[nameEnd] != '(') {
                    nameEnd++
                }

                val name = trimmed.substring(nameStart, nameEnd)
                pos = nameEnd

                // Skip whitespace after name
                while (pos < trimmed.length && trimmed[pos].isWhitespace()) pos++
                if (pos >= trimmed.length) break

                // Extract value
                val valueStart = pos
                var valueEnd = valueStart
                var inString = false
                var bracketDepth = 0
                var angleDepth = 0
                var prevChar = ' '

                while (valueEnd < trimmed.length) {
                    val ch = trimmed[valueEnd]

                    when {
                        ch == '(' && prevChar != '\\' && !inString -> {
                            inString = true
                        }
                        ch == ')' && prevChar != '\\' && inString -> {
                            inString = false
                            valueEnd++
                            break
                        }
                        ch == '[' && !inString -> {
                            bracketDepth++
                        }
                        ch == ']' && !inString -> {
                            bracketDepth--
                            if (bracketDepth == 0) {
                                valueEnd++
                                break
                            }
                        }
                        ch == '<' && valueEnd + 1 < trimmed.length && trimmed[valueEnd + 1] == '<' && !inString -> {
                            angleDepth++
                            valueEnd++ // Skip second <
                        }
                        ch == '>' && valueEnd + 1 < trimmed.length && trimmed[valueEnd + 1] == '>' && !inString -> {
                            angleDepth--
                            valueEnd++ // Skip second >
                            if (angleDepth == 0) {
                                valueEnd++
                                break
                            }
                        }
                        ch == '/' && !inString && bracketDepth == 0 && angleDepth == 0 -> {
                            // Start of next key
                            break
                        }
                        ch.isWhitespace() && !inString && bracketDepth == 0 && angleDepth == 0 -> {
                            // Check if this ends an indirect reference (e.g., "123 0 R")
                            val beforeSpace = trimmed.substring(valueStart, valueEnd).trim()
                            if (beforeSpace.endsWith("R")) {
                                valueEnd++
                                break
                            }
                            // Check if value is a simple token (number, name, etc.)
                            if (!beforeSpace.contains(' ') || beforeSpace.matches(Regex("\\d+\\s+\\d+\\s+R"))) {
                                // It's a simple value or reference, continue
                            } else {
                                // End of value
                                break
                            }
                        }
                        !inString && bracketDepth == 0 && angleDepth == 0 -> {
                            // For simple values, check if we're at a word boundary
                            val soFar = trimmed.substring(valueStart, valueEnd + 1).trim()
                            // If it looks like a complete reference or simple value, check next char
                            if (soFar.matches(Regex("\\d+\\s+\\d+\\s+R"))) {
                                valueEnd++
                                break
                            } else if (valueEnd + 1 < trimmed.length && trimmed[valueEnd + 1] == '/') {
                                // Next key is coming
                                valueEnd++
                                break
                            }
                        }
                    }

                    prevChar = ch
                    valueEnd++
                }

                val value = trimmed.substring(valueStart, valueEnd).trim()
                if (value.isNotEmpty()) {
                    dict[name] = parseValue(value)
                }
                pos = valueEnd
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing dictionary content: ${e.message}", e)
        }
    }
    
    /**
     * Parses a value token (string, number, reference, array, etc.)
     */
    private fun parseValue(token: String): Any {
        return when {
            token.matches(Regex("\\d+\\s+\\d+\\s+R")) -> {
                // Indirect reference with R
                token.split("\\s+".toRegex())[0].toIntOrNull() ?: token
            }
            token.matches(Regex("\\d+\\s+\\d+")) -> {
                // Indirect reference without R (the R was parsed separately)
                token.split("\\s+".toRegex())[0].toIntOrNull() ?: token
            }
            token.startsWith("(") && token.endsWith(")") -> {
                // String literal
                token.substring(1, token.length - 1)
            }
            token.startsWith("/") -> {
                // Name
                token.substring(1)
            }
            token.toIntOrNull() != null -> token.toInt()
            token.toFloatOrNull() != null -> token.toFloat()
            else -> token
        }
    }
    
    /**
     * Builds a flat list of all page object references
     */
    private fun buildPageList(raf: RandomAccessFile, catalog: PdfObject): List<Int> {
        val pages = mutableListOf<Int>()

        try {
            val pagesValue = catalog.getDictValue("Pages")
            Log.d(TAG, "Pages value from catalog: $pagesValue (type: ${pagesValue?.javaClass?.simpleName})")
            val pagesRef = pagesValue as? Int
            if (pagesRef == null) {
                Log.w(TAG, "Pages reference is null or not an Int")
                return pages
            }

            val pagesObj = parseObject(raf, pagesRef)
            if (pagesObj == null) {
                Log.w(TAG, "Failed to parse Pages object $pagesRef")
                return pages
            }

            Log.d(TAG, "Pages object dict: ${pagesObj.dictContent}")
            collectPages(raf, pagesObj, pages)
        } catch (e: Exception) {
            Log.e(TAG, "Error building page list: ${e.message}", e)
        }

        return pages
    }
    
    /**
     * Recursively collects page references from page tree
     */
    private fun collectPages(raf: RandomAccessFile, node: PdfObject, pages: MutableList<Int>) {
        val type = node.getDictValue("Type") as? String
        val kids = node.dictContent["Kids"]
        Log.d(TAG, "collectPages: node ${node.objNum}, type=$type, hasKids=${kids != null}")

        // Check if this is a Pages node (has /Kids) or a Page node (leaf)
        if (kids != null) {
            // Pages node - recurse into kids
            Log.d(TAG, "Kids value: $kids (type: ${kids.javaClass.simpleName})")
            val kidsStr = kids.toString()
            val kidRefs = extractReferences(kidsStr)
            Log.d(TAG, "Extracted ${kidRefs.size} kid references: $kidRefs")

            for (kidRef in kidRefs) {
                val kid = parseObject(raf, kidRef) ?: continue
                collectPages(raf, kid, pages)
            }
        } else {
            // Leaf page (no Kids) - add to list
            pages.add(node.objNum)
            Log.d(TAG, "Added page ${node.objNum}, total pages: ${pages.size}")
        }
    }
    
    /**
     * Recursively parses outline items
     */
    private fun parseOutlineItems(
        raf: RandomAccessFile,
        itemRef: Int,
        pages: List<Int>,
        result: MutableList<PdfOutlineItem>,
        level: Int
    ) {
        val item = parseObject(raf, itemRef) ?: return

        // Get title
        Log.d(TAG, "Outline item $itemRef dict content: ${item.dictContent}")
        val titleStr = item.getDictValue("Title") as? String
        Log.d(TAG, "Extracted title string: '$titleStr'")
        val title = titleStr?.let { decodeTextString(it) } ?: "Untitled"
        Log.d(TAG, "Final decoded title: '$title'")

        // Resolve destination to page number
        val pageNum = resolveDestinationPage(raf, item, pages)

        if (pageNum >= 0) {
            result.add(PdfOutlineItem(title, pageNum, level))
            Log.d(TAG, "Added outline item: title='$title', page=$pageNum, level=$level")
        }
        
        // Process children
        val firstChild = item.getDictValue("First") as? Int
        if (firstChild != null) {
            parseOutlineItems(raf, firstChild, pages, result, level + 1)
        }
        
        // Process siblings
        val next = item.getDictValue("Next") as? Int
        if (next != null) {
            parseOutlineItems(raf, next, pages, result, level)
        }
    }
    
    /**
     * Resolves outline destination to page number
     */
    private fun resolveDestinationPage(raf: RandomAccessFile, item: PdfObject, pages: List<Int>): Int {
        try {
            // Try Dest first
            val destValue = item.dictContent["Dest"]
            if (destValue != null) {
                return resolveDestination(raf, destValue, pages)
            }
            
            // Try A (Action) -> D (Destination)
            val actionRef = item.getDictValue("A") as? Int
            if (actionRef != null) {
                val action = parseObject(raf, actionRef) ?: return -1
                val d = action.dictContent["D"]
                if (d != null) {
                    return resolveDestination(raf, d, pages)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving destination: ${e.message}")
        }
        
        return -1
    }
    
    /**
     * Resolves a destination value to page number
     */
    private fun resolveDestination(raf: RandomAccessFile, destValue: Any, pages: List<Int>): Int {
        try {
            when (destValue) {
                is Int -> {
                    // Indirect reference to destination
                    val destObj = parseObject(raf, destValue) ?: return -1
                    val destArray = destObj.dictContent["value"]?.toString() ?: return -1
                    return parseDestinationArray(destArray, pages)
                }
                is String -> {
                    // Direct destination array or name
                    if (destValue.startsWith("[")) {
                        return parseDestinationArray(destValue, pages)
                    }
                    // Named destination - would need to resolve via Names tree
                    return -1
                }
                else -> return -1
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving destination value: ${e.message}")
            return -1
        }
    }
    
    /**
     * Parses destination array [pageRef /FitType ...]
     */
    private fun parseDestinationArray(arrayStr: String, pages: List<Int>): Int {
        try {
            val refs = extractReferences(arrayStr)
            if (refs.isEmpty()) return -1
            
            val pageRef = refs[0]
            return pages.indexOf(pageRef).takeIf { it >= 0 } ?: -1
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing destination array: ${e.message}")
            return -1
        }
    }
    
    /**
     * Parses text content from PDF content stream
     */
    private fun parseContentStream(data: ByteArray, output: StringBuilder) {
        try {
            val content = String(data, StandardCharsets.ISO_8859_1)
            
            // Extract text between BT (Begin Text) and ET (End Text) operators
            val btPattern = Regex("BT\\s+(.*?)\\s+ET", RegexOption.DOT_MATCHES_ALL)
            val matches = btPattern.findAll(content)
            
            for (match in matches) {
                val textBlock = match.groupValues[1]
                
                // Extract text strings from Tj, TJ operators
                val tjPattern = Regex("\\((.*?)\\)\\s*Tj")
                val tjMatches = tjPattern.findAll(textBlock)
                
                for (tjMatch in tjMatches) {
                    val text = tjMatch.groupValues[1]
                    output.append(decodeTextString(text))
                    output.append(" ")
                }
                
                // TJ array operator
                val tjArrayPattern = Regex("\\[(.*?)\\]\\s*TJ")
                val tjArrayMatches = tjArrayPattern.findAll(textBlock)
                
                for (tjArrayMatch in tjArrayMatches) {
                    val arrayContent = tjArrayMatch.groupValues[1]
                    val stringPattern = Regex("\\((.*?)\\)")
                    val strings = stringPattern.findAll(arrayContent)
                    
                    for (str in strings) {
                        output.append(decodeTextString(str.groupValues[1]))
                        output.append(" ")
                    }
                }
                
                output.append("\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing content stream: ${e.message}", e)
        }
    }
    
    /**
     * Decodes PDF text string (handles escape sequences)
     */
    private fun decodeTextString(text: String): String {
        return text
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
            .replace("\\(", "(")
            .replace("\\)", ")")
    }
    
    /**
     * Extracts object reference numbers from string (e.g., "5 0 R")
     */
    private fun extractReference(text: String, key: String): Int? {
        try {
            val pattern = Regex("/$key\\s+(\\d+)\\s+\\d+\\s+R")
            val match = pattern.find(text)
            return match?.groupValues?.get(1)?.toIntOrNull()
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Extracts all object references from string
     */
    private fun extractReferences(text: String): List<Int> {
        val refs = mutableListOf<Int>()
        try {
            val pattern = Regex("(\\d+)\\s+\\d+\\s+R")
            val matches = pattern.findAll(text)
            for (match in matches) {
                match.groupValues[1].toIntOrNull()?.let { refs.add(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting references: ${e.message}")
        }
        return refs
    }
    
    /**
     * Reads a line from RAF
     */
    private fun readLine(raf: RandomAccessFile): String {
        val buffer = StringBuilder()
        var c: Int
        
        while (raf.filePointer < raf.length()) {
            c = raf.read()
            if (c < 0 || c == '\n'.code) break
            if (c == '\r'.code) {
                // Check for CRLF
                if (raf.filePointer < raf.length() && raf.read() != '\n'.code) {
                    raf.seek(raf.filePointer - 1)
                }
                break
            }
            buffer.append(c.toChar())
        }
        
        return buffer.toString()
    }
}

/**
 * Internal representation of a PDF object
 */
private data class PdfObject(
    val objNum: Int,
    val dictContent: Map<String, Any>,
    var streamData: ByteArray? = null
) {
    fun getDictValue(key: String): Any? = dictContent[key]
}
