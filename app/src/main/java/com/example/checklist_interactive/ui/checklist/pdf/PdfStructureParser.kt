package com.example.checklist_interactive.ui.checklist.pdf

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

    init {
        android.util.Log.d(TAG, "Created PdfStructureParser for ${pdfFile.absolutePath}")
    }

    companion object {
        private val globalCache = mutableMapOf<String, PdfStructureParser>()
        // Track which file paths already had their xref parsed (global guard)
        private val parsedXrefFiles = mutableSetOf<String>()

        fun getInstance(file: File): PdfStructureParser {
            val path = file.absolutePath
            synchronized(globalCache) {
                return globalCache.getOrPut(path) {
                    PdfStructureParser(file)
                }
            }
        }

        fun markXrefParsedFor(filePath: String) {
            synchronized(parsedXrefFiles) {
                parsedXrefFiles.add(filePath)
            }
        }

        fun isXrefParsedFor(filePath: String): Boolean {
            synchronized(parsedXrefFiles) {
                return parsedXrefFiles.contains(filePath)
            }
        }

        fun clearCache() {
            synchronized(globalCache) {
                globalCache.clear()
            }
            synchronized(parsedXrefFiles) {
                parsedXrefFiles.clear()
            }
        }
    }
    
    // PDF cross-reference table: maps object numbers to byte offsets
    private val xrefTable = mutableMapOf<Int, Long>()

    // Compressed object references: maps object number to (object stream number, index)
    private val compressedObjects = mutableMapOf<Int, Pair<Int, Int>>()

    // Cached parsed objects
    private val objectCache = mutableMapOf<Int, PdfObject>()
    
    // Cached page list to avoid rebuilding on every text extraction
    private var cachedPageList: List<Int>? = null

    // Flag indicating whether the xref table has been parsed successfully
    @Volatile
    private var xrefParsed: Boolean = false
    
    // Recursion guards to prevent infinite loops
    private val scanningObjects = mutableSetOf<Int>()
    private val indexingStreams = mutableSetOf<Int>()
    private var scanDepth = 0
    private val maxScanDepth = 5
    private var failedStreamIndexingAttempts = 0
    private val maxFailedStreamAttempts = 20
    
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
                val outlineRef = resolveRef(outlineValue)
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
                val firstRef = resolveRef(outlineDict.getDictValue("First"))
                if (firstRef == null) {
                    Log.d(TAG, "No First entry in outline (available keys: ${outlineDict.dictContent.keys})")
                    return emptyList()
                }
                Log.d(TAG, "First outline item reference: $firstRef")

                // 7. Build page list for page number resolution (use cached if available)
                Log.d(TAG, "Step 7: Obtaining page list (cached=${cachedPageList != null})")
                val pages = getOrBuildPageList(raf, catalog)
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
                
                // Use cached page list if available to avoid rebuilding on every call
                Log.d(TAG, "Obtaining page list for extractPageText (cached=${cachedPageList != null})")
                val pages = getOrBuildPageList(raf, catalog)

                if (pageIndex !in pages.indices) return ""

                val pageRef = pages[pageIndex]
                val page = parseObject(raf, pageRef) ?: return ""
                
                // Get content stream
                val contentsRef = resolveRef(page.getDictValue("Contents")) ?: return ""
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
        // Return early if xref has already been parsed for this instance or globally
        if (xrefParsed || Companion.isXrefParsedFor(pdfFile.absolutePath)) {
            Log.d(TAG, "parseXrefTable: already parsed, skipping for ${pdfFile.absolutePath}")
            return true
        }

        synchronized(this) {
            if (xrefParsed || Companion.isXrefParsedFor(pdfFile.absolutePath)) {
                Log.d(TAG, "parseXrefTable: already parsed (sync), skipping for ${pdfFile.absolutePath}")
                return true
            }
            
            // Reset failure counters for new parsing session
            failedStreamIndexingAttempts = 0
            scanDepth = 0
            scanningObjects.clear()
            indexingStreams.clear()

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
            if (entryCount > 0) {
                xrefParsed = true
                Companion.markXrefParsedFor(pdfFile.absolutePath)
                Log.d(TAG, "Marked xref parsed for ${pdfFile.absolutePath}")
                return true
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing xref: ${e.message}", e)
            return false
        }
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
            var dictSeekAttempts = 0
            while (dictSeekAttempts < 10) {
                val pos = raf.filePointer
                val line = readLine(raf)
                if (line.contains("<<")) {
                    raf.seek(pos)
                    break
                }
                dictSeekAttempts++
            }

            val dict = readDictionary(raf)
            Log.d(TAG, "XRef stream dict keys: ${dict.keys}")
            Log.d(TAG, "XRef stream dict Filter entry: '${dict["Filter"]}' (type: ${dict["Filter"]?.javaClass?.simpleName})")

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

            // Attempt to read and decode the xref stream bytes (if present) to directly extract offsets.
            try {
                // Move RAF to after the dictionary and find 'stream' token
                var posAfterDict = raf.filePointer
                var streamSeekAttempts = 0
                var foundStream = false
                while (streamSeekAttempts < 20 && raf.filePointer < raf.length()) {
                    val linePos = raf.filePointer
                    val line = readLine(raf).trim()
                    if (line == "stream") {
                        // Position is at start of stream data (next byte)
                        posAfterDict = raf.filePointer
                        foundStream = true
                        break
                    }
                    streamSeekAttempts++
                }

                if (foundStream) {
                    val lengthVal = dict["Length"]
                    val length = when (lengthVal) {
                        is Int -> lengthVal
                        is String -> lengthVal.toIntOrNull() ?: -1
                        else -> -1
                    }

                    if (length > 0) {
                        // Read raw stream bytes
                        raf.seek(posAfterDict)
                        val raw = ByteArray(length)
                        raf.readFully(raw)

                        // If Filter says FlateDecode, decompress
                        val filterValue = dict["Filter"]
                        Log.d(TAG, "XRef stream Filter value: '$filterValue' (type: ${filterValue?.javaClass?.simpleName})")
                        val filters = when (filterValue) {
                            is String -> listOf(filterValue)
                            is List<*> -> filterValue.mapNotNull { it as? String }
                            else -> emptyList()
                        }
                        var decoded = raw
                        Log.d(TAG, "XRef stream: length=$length, raw size=${raw.size}, filters=$filters")
                        if (filters.any { it.contains("FlateDecode") || it == "FlateDecode" }) {
                            try {
                                val bais = java.io.ByteArrayInputStream(raw)
                                val baos = java.io.ByteArrayOutputStream()
                                java.util.zip.InflaterInputStream(bais).use { ins ->
                                    ins.copyTo(baos)
                                }
                                decoded = baos.toByteArray()
                                Log.d(TAG, "After FlateDecode: ${decoded.size} bytes")

                                // Apply PNG predictor if specified in DecodeParms
                                val decodeParmsValue = dict["DecodeParms"]
                                Log.d(TAG, "DecodeParms type: ${decodeParmsValue?.javaClass?.simpleName}, value: $decodeParmsValue")
                                
                                var predictor = 1
                                var columns = 1
                                
                                when (decodeParmsValue) {
                                    is String -> {
                                        // Parse DecodeParms dictionary string
                                        predictor = extractDictValue(decodeParmsValue, "Predictor")?.toIntOrNull() ?: 1
                                        columns = extractDictValue(decodeParmsValue, "Columns")?.toIntOrNull() ?: 1
                                    }
                                    is Map<*, *> -> {
                                        // DecodeParms is a dictionary object
                                        predictor = (decodeParmsValue["Predictor"] as? Int) ?: 1
                                        columns = (decodeParmsValue["Columns"] as? Int) ?: 1
                                    }
                                }

                                Log.d(TAG, "DecodeParms: Predictor=$predictor, Columns=$columns")
                                if (predictor >= 10) {
                                    // PNG predictor (10-15)
                                    val beforeSize = decoded.size
                                    val firstBytes = decoded.take(30).joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
                                    Log.d(TAG, "Before PNG predictor, first 30 bytes: $firstBytes")
                                    decoded = applyPngPredictor(decoded, columns)
                                    val firstBytesAfter = decoded.take(30).joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
                                    Log.d(TAG, "After PNG predictor, first 30 bytes: $firstBytesAfter")
                                    Log.d(TAG, "Applied PNG predictor $predictor with $columns columns: $beforeSize -> ${decoded.size} bytes")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to inflate xref stream: ${e.message}")
                                e.printStackTrace()
                            }
                        }

                        // Parse W array to get field widths
                        val wVal = dict["W"] as? String
                        val wArray = wVal?.replace("[", "")?.replace("]", "")?.trim()
                            ?.split("\\s+".toRegex())?.mapNotNull { it.toIntOrNull() } ?: listOf()

                        Log.d(TAG, "XRef stream W array: $wArray, decoded size: ${decoded.size} bytes")

                        val indexArray = objRanges.ifEmpty {
                            // If /Index missing, default 0..Size-1
                            val size = resolveRef(dict["Size"]) ?: 0
                            if (size > 0) listOf(Pair(0, size)) else listOf()
                        }
                        
                        Log.d(TAG, "XRef stream index ranges: $indexArray")

                        if (wArray.size >= 3 && indexArray.isNotEmpty()) {
                            var bytePos = 0
                            var uncompressedCount = 0
                            var compressedCount = 0
                            var freeCount = 0
                            
                            for ((startObj, count) in indexArray) {
                                for (i in 0 until count) {
                                    if (bytePos + wArray.sum() > decoded.size) {
                                        Log.w(TAG, "Ran out of xref stream data at object ${startObj + i} (pos=$bytePos, size=${decoded.size})")
                                        break
                                    }
                                    // Read fields per W
                                    var fieldValues = mutableListOf<Long>()
                                    for (w in wArray) {
                                        var v = 0L
                                        for (b in 0 until w) {
                                            v = (v shl 8) or (decoded[bytePos].toInt() and 0xFF).toLong()
                                            bytePos++
                                        }
                                        fieldValues.add(v)
                                    }

                                    val objNum = startObj + i
                                    // If W[0]=0, type defaults to 1 (uncompressed)
                                    val ftype = if (wArray[0] == 0) 1 else (fieldValues.getOrNull(0)?.toInt() ?: 1)
                                    val f2 = fieldValues.getOrNull(1) ?: 0L
                                    val f3 = fieldValues.getOrNull(2)?.toInt() ?: 0
                                    
                                    // Debug first few and problematic entries
                                    if (objNum <= 5 || objNum == 100 || objNum == 2) {
                                        Log.d(TAG, "XRef entry $objNum: type=$ftype, f2=$f2, f3=$f3, fieldValues=$fieldValues")
                                    }
                                    
                                    when (ftype) {
                                        0 -> {
                                            // free object
                                            freeCount++
                                        }
                                        1 -> {
                                            // uncompressed object, f2 is offset
                                            xrefTable[objNum] = f2
                                            uncompressedCount++
                                        }
                                        2 -> {
                                            // compressed in object stream
                                            // f2 = object stream number, f3 = index within stream
                                            compressedObjects[objNum] = Pair(f2.toInt(), f3)
                                            compressedCount++
                                            // Log first few compressed objects for debugging
                                            if (compressedCount <= 5) {
                                                Log.d(TAG, "Compressed object $objNum -> stream $f2, index $f3")
                                            }
                                        }
                                        else -> {
                                            // unknown type
                                            Log.w(TAG, "Unknown xref entry type $ftype for object $objNum")
                                        }
                                    }
                                }
                            }
                            Log.d(TAG, "Parsed xref stream: $uncompressedCount uncompressed + $compressedCount compressed + $freeCount free entries")

                            // If the xref stream declared object ranges but we failed to collect
                            // any compressed entries, try scanning for object streams as a
                            // fallback to index compressed objects (ObjStm).
                            if (objRanges.isNotEmpty() && compressedObjects.isEmpty()) {
                                var added = 0
                                for ((start, count) in objRanges) {
                                    val end = start + count - 1
                                    val found = scanForObjectStreamsInRange(raf, start, end)
                                    added += found
                                }
                                Log.d(TAG, "After ObjStm scan: discovered $added objects inside object streams")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse xref stream bytes: ${e.message}")
            }

            // Skip bulk scanning for missing objects - use on-demand scanning instead
            // Most missing objects are likely compressed (type 2) and won't be found by scanning
            // Individual objects will be scanned on-demand when needed
            if (objRanges.isNotEmpty()) {
                val totalMissing = objRanges.sumOf { (start, count) ->
                    (start until start + count).count { !xrefTable.containsKey(it) }
                }
                Log.d(TAG, "Skipping bulk scan for $totalMissing potentially compressed objects (will scan on-demand)")
            }

            // Check for /Prev pointing to an older xref table
            val prevOffset = resolveRef(dict["Prev"])?.toLong() ?: (dict["Prev"] as? String)?.toLongOrNull()
            if (prevOffset != null && prevOffset > 0) {
                Log.d(TAG, "Found /Prev pointing to offset $prevOffset, trying to parse previous xref")
                raf.seek(prevOffset)
                val prevLine = readLine(raf)
                Log.d(TAG, "Previous xref line: '$prevLine'")

                if (prevLine.trim().startsWith("xref")) {
                    // It's a traditional xref table! Parse it
                    Log.d(TAG, "Previous xref is traditional format, parsing it")
                    raf.seek(prevOffset)
                    val okPrev = parseTraditionalXref(raf)
                    if (okPrev) {
                        xrefParsed = true
                        Companion.markXrefParsedFor(pdfFile.absolutePath)
                        Log.d(TAG, "Marked xref parsed (prev traditional) for ${pdfFile.absolutePath}")
                    }
                } else if (prevLine.matches(Regex("\\d+\\s+\\d+\\s+obj"))) {
                    // It's another xref stream, recurse
                    Log.d(TAG, "Previous xref is also a stream, recursing")
                    val okPrev = parseXrefStream(raf, prevOffset)
                    if (okPrev) {
                        xrefParsed = true
                        Companion.markXrefParsedFor(pdfFile.absolutePath)
                        Log.d(TAG, "Marked xref parsed (prev stream) for ${pdfFile.absolutePath}")
                    }
                }
            }

            if (xrefTable.isNotEmpty()) {
                xrefParsed = true
                Companion.markXrefParsedFor(pdfFile.absolutePath)
                Log.d(TAG, "Marked xref parsed (stream path) for ${pdfFile.absolutePath}")
            }

            return xrefTable.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing xref stream: ${e.message}", e)
            return false
        }
    }

    /**
     * Parses a compressed object from an object stream
     */
    private fun parseCompressedObject(raf: RandomAccessFile, objNum: Int, streamObjNum: Int, index: Int): PdfObject? {
        try {
            // Parse the object stream
            val streamObj = parseObject(raf, streamObjNum)
            if (streamObj == null) {
                Log.w(TAG, "Could not find object stream $streamObjNum")
                return null
            }

            val streamData = streamObj.streamData
            if (streamData == null) {
                Log.w(TAG, "Object stream $streamObjNum has no stream data")
                return null
            }

            // Decompress if needed
            val filters = (streamObj.dictContent["Filter"] as? String)?.let { listOf(it) } ?: emptyList()
            var decodedData = streamData
            if (filters.any { it.contains("FlateDecode") }) {
                try {
                    val bais = java.io.ByteArrayInputStream(streamData)
                    val baos = java.io.ByteArrayOutputStream()
                    java.util.zip.InflaterInputStream(bais).use { it.copyTo(baos) }
                    decodedData = baos.toByteArray()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decompress object stream: ${e.message}")
                    return null
                }
            }

            // Parse object stream format:
            // /N = number of objects
            // /First = offset to first object data
            val n = (streamObj.dictContent["N"] as? Int) ?: 0
            val first = (streamObj.dictContent["First"] as? Int) ?: 0

            if (n == 0 || first == 0) {
                Log.w(TAG, "Object stream has invalid /N=$n or /First=$first")
                return null
            }

            // Parse the header (object numbers and offsets)
            val header = String(decodedData, 0, first.coerceAtMost(decodedData.size), StandardCharsets.ISO_8859_1)
            val tokens = header.trim().split("\\s+".toRegex())

            if (tokens.size < n * 2) {
                Log.w(TAG, "Object stream header has insufficient tokens: ${tokens.size} < ${n * 2}")
                return null
            }

            // Find our object in the header
            for (i in 0 until n) {
                val currentObjNum = tokens[i * 2].toIntOrNull() ?: continue
                val relativeOffset = tokens[i * 2 + 1].toIntOrNull() ?: continue

                if (currentObjNum == objNum) {
                    // Found it! Extract the object data
                    val absoluteOffset = first + relativeOffset
                    if (absoluteOffset >= decodedData.size) {
                        Log.w(TAG, "Object offset $absoluteOffset beyond stream size ${decodedData.size}")
                        return null
                    }

                    // Determine end offset (next object or end of stream)
                    val endOffset = if (i + 1 < n) {
                        val nextRelativeOffset = tokens[(i + 1) * 2 + 1].toIntOrNull() ?: decodedData.size
                        first + nextRelativeOffset
                    } else {
                        decodedData.size
                    }

                    // Extract object data
                    val objData = String(decodedData, absoluteOffset, endOffset - absoluteOffset, StandardCharsets.ISO_8859_1)
                    Log.d(TAG, "Extracted compressed object $objNum data (${objData.length} bytes): '${objData.take(200)}'")

                    // Parse as dictionary if it starts with <<
                    val trimmed = objData.trim()
                    if (trimmed.startsWith("<<")) {
                        val dict = mutableMapOf<String, Any>()
                        parseDictionaryContent(trimmed, dict)
                        val obj = PdfObject(objNum, dict)
                        objectCache[objNum] = obj
                        return obj
                    } else {
                        // Simple value object
                        val dict = mapOf("value" to parseValue(trimmed))
                        val obj = PdfObject(objNum, dict)
                        objectCache[objNum] = obj
                        return obj
                    }
                }
            }

            Log.w(TAG, "Object $objNum not found in object stream $streamObjNum (has $n objects)")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing compressed object $objNum: ${e.message}", e)
            return null
        }
    }

    /**
     * Scans for a specific object number in the PDF file
     * Returns true if found and added to xrefTable
     */
    private fun scanForSpecificObject(raf: RandomAccessFile, targetObj: Int): Boolean {
        // Guard against infinite recursion
        if (targetObj in scanningObjects) {
            Log.w(TAG, "Already scanning for object $targetObj, breaking recursion")
            return false
        }
        if (scanDepth >= maxScanDepth) {
            Log.w(TAG, "Max scan depth reached ($maxScanDepth), aborting scan for object $targetObj")
            return false
        }
        
        scanningObjects.add(targetObj)
        scanDepth++
        try {
            Log.d(TAG, "Scanning for specific object: $targetObj (depth: $scanDepth)")
            val fileSize = raf.length()
            val chunkSize = 524288 // 512KB chunks
            val overlap = 256
            val objPattern = Regex("^($targetObj)\\s+\\d+\\s+obj", RegexOption.MULTILINE)

            val startTime = System.currentTimeMillis()
            val timeoutMs = 15000L // 15 second timeout for single object (increase for large PDFs)

            // Helper to process a chunk
            fun processChunk(pos: Long, chunk: String): Boolean {
                val match = objPattern.find(chunk)
                if (match != null) {
                    val idx = match.range.first
                    val objPos = pos + idx
                    xrefTable[targetObj] = objPos
                    Log.d(TAG, "Found object $targetObj at offset $objPos")
                    return true
                }
                return false
            }

            // Scan backward from end (often faster for high object numbers)
            var position = (fileSize - chunkSize).coerceAtLeast(0)
            while (position >= 0) {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    Log.w(TAG, "Scan for object $targetObj timed out")
                    break
                }

                raf.seek(position)
                val readSize = (fileSize - position).coerceAtMost(chunkSize.toLong()).toInt()
                val buffer = ByteArray(readSize)
                raf.read(buffer)
                val chunk = String(buffer, StandardCharsets.ISO_8859_1)

                if (processChunk(position, chunk)) {
                    return true
                }

                if (position == 0L) break
                position = (position - (chunkSize - overlap)).coerceAtLeast(0)
            }

            // If not found backward, try forward scan
            if (!xrefTable.containsKey(targetObj)) {
                position = 0L
                while (position < fileSize) {
                    if (System.currentTimeMillis() - startTime > timeoutMs) {
                        Log.w(TAG, "Scan for object $targetObj timed out")
                        break
                    }

                    raf.seek(position)
                    val readSize = (fileSize - position).coerceAtMost(chunkSize.toLong()).toInt()
                    val buffer = ByteArray(readSize)
                    raf.read(buffer)
                    val chunk = String(buffer, StandardCharsets.ISO_8859_1)

                    if (processChunk(position, chunk)) {
                        return true
                    }

                    position += readSize - overlap
                }
            }

            // If still not found, attempt to index object streams in the file
            // Some objects are stored inside ObjStm (object streams) and won't appear
            // as "<obj> 0 obj" entries. Try scanning for ObjStm objects and index them.
            // Only do this if we're not too deep in recursion
            if (!xrefTable.containsKey(targetObj) && !compressedObjects.containsKey(targetObj) && scanDepth < maxScanDepth - 1) {
                Log.d(TAG, "Attempting to find object $targetObj inside object streams (depth: $scanDepth)")
                scanForObjectStreamsInRange(raf, targetObj, targetObj)
                if (compressedObjects.containsKey(targetObj)) {
                    Log.d(TAG, "Found object $targetObj inside an object stream (indexed)")
                    return true
                }
            }

            return xrefTable.containsKey(targetObj)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning for object $targetObj: ${e.message}", e)
            return false
        } finally {
            scanningObjects.remove(targetObj)
            scanDepth--
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
            // Require match at line start to avoid false positives inside streams
            val objPattern = Regex("^(\\d+)\\s+\\d+\\s+obj", RegexOption.MULTILINE)
            val foundObjects = mutableSetOf<Int>()

            // Timebox scanning to avoid pathological cases
            val startTime = System.currentTimeMillis()
            val timeoutMs = 15_000L

            // Helper to process a chunk at absolute `pos`
            fun processChunk(pos: Long, chunk: String) {
                objPattern.findAll(chunk).forEach { match ->
                    val group = match.groups[1]
                    val objNum = group?.value?.toIntOrNull()
                    val idx = group?.range?.first ?: match.range.first
                    if (objNum != null && objNum in startObj..endObj && !foundObjects.contains(objNum)) {
                        val objPos = pos + idx
                        xrefTable[objNum] = objPos
                        foundObjects.add(objNum)
                    }
                }
            }

            // First: scan backwards from the end (often faster for large doc object numbers)
            var position = (fileSize - chunkSize).coerceAtLeast(0)
            while (position >= 0) {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    Log.w(TAG, "Scanning timed out after ${timeoutMs}ms (backward)")
                    break
                }

                raf.seek(position)
                val readSize = (fileSize - position).coerceAtMost(chunkSize.toLong()).toInt()
                val buffer = ByteArray(readSize)
                raf.read(buffer)
                val chunk = String(buffer, StandardCharsets.ISO_8859_1)

                processChunk(position, chunk)

                if (foundObjects.size >= (endObj - startObj + 1)) break

                // Move backwards with overlap
                if (position == 0L) break
                position = (position - (chunkSize - overlap)).coerceAtLeast(0)
            }

            // If not all found, do a forward scan as a fallback (covers cases objects are earlier in file)
            if (foundObjects.size < (endObj - startObj + 1)) {
                position = 0L
                raf.seek(0)
                while (position < fileSize) {
                    if (System.currentTimeMillis() - startTime > timeoutMs) {
                        Log.w(TAG, "Scanning timed out after ${timeoutMs}ms (forward)")
                        break
                    }

                    val readSize = (fileSize - position).coerceAtMost(chunkSize.toLong()).toInt()
                    val buffer = ByteArray(readSize)
                    raf.read(buffer)
                    val chunk = String(buffer, StandardCharsets.ISO_8859_1)

                    processChunk(position, chunk)

                    // Progress log occasionally
                    if (foundObjects.size > 0 && foundObjects.size % 50 == 0) {
                        Log.d(TAG, "Found ${foundObjects.size} objects, ${position * 100 / fileSize}% scanned")
                    }

                    if (foundObjects.size >= (endObj - startObj + 1)) break

                    position += readSize - overlap
                    raf.seek(position.coerceAtLeast(0))
                }
            }

            Log.d(TAG, "Found ${foundObjects.size}/${endObj - startObj + 1} objects in range")
            return foundObjects.size
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning: ${e.message}", e)
            return 0
        }
    }

    /**
     * Indexes a parsed object stream (`ObjStm`) by reading its header and mapping
     * contained object numbers to (streamObjNum, index).
     */
    private fun indexObjectStream(raf: RandomAccessFile, streamObjNum: Int): Boolean {
        // Guard against re-indexing the same stream (prevents loops)
        if (streamObjNum in indexingStreams) {
            Log.w(TAG, "Already indexing stream $streamObjNum, breaking recursion")
            return false
        }
        
        indexingStreams.add(streamObjNum)
        try {
            // Ensure xref has an entry for this stream object so parseObject can read it
            val offset = xrefTable[streamObjNum]
            if (offset == null) {
                Log.d(TAG, "indexObjectStream: no xref entry for stream $streamObjNum, skipping")
                return false
            }

            val streamObj = parseObject(raf, streamObjNum) ?: return false
            val streamData = streamObj.streamData ?: return false

            val n = (streamObj.dictContent["N"] as? Int) ?: return false
            val first = (streamObj.dictContent["First"] as? Int) ?: return false
            if (n <= 0 || first < 0) return false
            
            // Sanity check - if N is huge or First is beyond stream size, skip it
            if (n > 10000 || first > streamData.size) {
                Log.w(TAG, "Object stream $streamObjNum has suspicious N=$n or First=$first (stream size=${streamData.size}), skipping")
                failedStreamIndexingAttempts++
                return false
            }

            val headerLen = first.coerceAtMost(streamData.size)
            val header = String(streamData, 0, headerLen, StandardCharsets.ISO_8859_1)
            val tokens = header.trim().split("\\s+".toRegex())

            if (tokens.size < n * 2) {
                Log.w(TAG, "Object stream $streamObjNum header has insufficient tokens: ${tokens.size} < ${n * 2}")
                failedStreamIndexingAttempts++
                return false
            }

            var addedCount = 0
            for (i in 0 until n) {
                val objNum = tokens.getOrNull(i * 2)?.toIntOrNull() ?: continue
                compressedObjects[objNum] = Pair(streamObjNum, i)
                addedCount++
            }

            if (addedCount > 0) {
                Log.d(TAG, "Indexed object stream $streamObjNum: added $addedCount entries")
                return true
            } else {
                failedStreamIndexingAttempts++
                return false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error indexing object stream $streamObjNum: ${e.message}")
            return false
        } finally {
            indexingStreams.remove(streamObjNum)
        }
    }

    /**
     * Reads a simple indirect integer object (e.g., object that contains plain '120')
     * Returns null if it cannot be resolved.
     */
    private fun readIndirectInteger(raf: RandomAccessFile, objNum: Int): Int? {
        try {
            var offset = xrefTable[objNum]
            if (offset == null) {
                // Don't scan if we're already in a deep scan - prevents infinite loops
                if (scanDepth >= maxScanDepth - 1) {
                    Log.w(TAG, "Skipping scan for indirect integer $objNum - too deep (depth: $scanDepth)")
                    return null
                }
                if (!scanForSpecificObject(raf, objNum)) return null
                offset = xrefTable[objNum] ?: return null
            }

            raf.seek(offset)
            // skip header line
            readLine(raf)

            // Read up to a short window until 'endobj'
            val sb = StringBuilder()
            var bytes = 0
            while (bytes < 512 && raf.filePointer < raf.length()) {
                val c = raf.read()
                if (c < 0) break
                sb.append(c.toChar())
                bytes++
                if (sb.endsWith("endobj")) break
            }

            val s = sb.toString()
            val match = Regex("\\b(\\d+)\\b").find(s)
            return match?.groupValues?.get(1)?.toIntOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read indirect integer for $objNum: ${e.message}")
            return null
        }
    }

    /**
     * Scans the file for object streams (ObjStm) and indexes them. If a numeric
     * range is provided, it will still index any object streams found and return
     * how many objects in the requested range were discovered.
     */
    private fun scanForObjectStreamsInRange(raf: RandomAccessFile, startObj: Int, endObj: Int): Int {
        try {
            Log.d(TAG, "Scanning for object streams (looking for ObjStm) to cover $startObj..$endObj (depth: $scanDepth)")

            val fileSize = raf.length()
            val chunkSize = 524288
            val overlap = 512
            val objHeader = Regex("^(\\d+)\\s+\\d+\\s+obj", RegexOption.MULTILINE)

            val startTime = System.currentTimeMillis()
            val timeoutMs = 10_000L // Reduced to 10 seconds
            val newlyIndexed = mutableSetOf<Int>()
            val maxStreamsToIndex = 20 // Reduced limit
            var consecutiveFailures = 0
            val maxConsecutiveFailures = 10

            fun processChunk(pos: Long, chunk: String) {
                // Stop if we've indexed enough streams, timed out, or too many failures
                if (newlyIndexed.size >= maxStreamsToIndex) return
                if (System.currentTimeMillis() - startTime > timeoutMs) return
                if (failedStreamIndexingAttempts >= maxFailedStreamAttempts) {
                    Log.w(TAG, "Aborting ObjStm scan - too many failed attempts ($failedStreamIndexingAttempts)")
                    return
                }
                if (consecutiveFailures >= maxConsecutiveFailures) {
                    Log.w(TAG, "Aborting ObjStm scan - too many consecutive failures ($consecutiveFailures)")
                    return
                }
                
                objHeader.findAll(chunk).forEach { match ->
                    if (newlyIndexed.size >= maxStreamsToIndex) return@forEach
                    
                    val num = match.groups[1]?.value?.toIntOrNull() ?: return@forEach
                    if (newlyIndexed.contains(num)) return@forEach

                    val afterPos = match.range.last + 1
                    val look = chunk.substring(afterPos.coerceAtMost(chunk.length - 1).coerceAtLeast(0),
                        (afterPos + 512).coerceAtMost(chunk.length))

                    if (look.contains("ObjStm") || look.contains("/ObjStm")) {
                        val absolute = pos + match.range.first
                        Log.d(TAG, "Found ObjStm candidate: $num at $absolute")
                        xrefTable[num] = absolute
                        val ok = indexObjectStream(raf, num)
                        if (ok) {
                            newlyIndexed.add(num)
                            consecutiveFailures = 0 // Reset on success
                        } else {
                            consecutiveFailures++
                        }
                    }
                }
            }

            // Backwards scan
            var position = (fileSize - chunkSize).coerceAtLeast(0)
            while (position >= 0 && newlyIndexed.size < maxStreamsToIndex) {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    Log.w(TAG, "ObjStm scan timed out (backward) after ${timeoutMs}ms")
                    break
                }
                if (failedStreamIndexingAttempts >= maxFailedStreamAttempts || consecutiveFailures >= maxConsecutiveFailures) {
                    Log.w(TAG, "Aborting ObjStm backward scan - too many failures")
                    break
                }
                raf.seek(position)
                val readSize = (fileSize - position).coerceAtMost(chunkSize.toLong()).toInt()
                val buffer = ByteArray(readSize)
                raf.read(buffer)
                val chunk = String(buffer, StandardCharsets.ISO_8859_1)
                processChunk(position, chunk)
                if (position == 0L) break
                position = (position - (chunkSize - overlap)).coerceAtLeast(0)
            }

            // Forward scan if still within time and limit
            if (System.currentTimeMillis() - startTime < timeoutMs && newlyIndexed.size < maxStreamsToIndex 
                && failedStreamIndexingAttempts < maxFailedStreamAttempts && consecutiveFailures < maxConsecutiveFailures) {
                position = 0L
                while (position < fileSize && newlyIndexed.size < maxStreamsToIndex) {
                    if (System.currentTimeMillis() - startTime > timeoutMs) {
                        Log.w(TAG, "ObjStm scan timed out (forward) after ${timeoutMs}ms")
                        break
                    }
                    if (failedStreamIndexingAttempts >= maxFailedStreamAttempts || consecutiveFailures >= maxConsecutiveFailures) {
                        Log.w(TAG, "Aborting ObjStm forward scan - too many failures")
                        break
                    }
                    raf.seek(position)
                    val readSize = (fileSize - position).coerceAtMost(chunkSize.toLong()).toInt()
                    val buffer = ByteArray(readSize)
                    raf.read(buffer)
                    val chunk = String(buffer, StandardCharsets.ISO_8859_1)
                    processChunk(position, chunk)
                    position += readSize - overlap
                }
            }

            // Count how many objects in requested range were mapped to compressedObjects
            val countInRange = (startObj..endObj).count { compressedObjects.containsKey(it) }
            Log.d(TAG, "Indexed ${newlyIndexed.size} object streams, found $countInRange objects in range")
            return countInRange
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning for object streams: ${e.message}")
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
            if (entryCount > 0) {
                xrefParsed = true
                Companion.markXrefParsedFor(pdfFile.absolutePath)
                Log.d(TAG, "Marked xref parsed (traditional) for ${pdfFile.absolutePath}")
                return true
            }
            return false
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

            val rootRef = resolveRef(dict["Root"])
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

        // Check if object is in an object stream (compressed)
        val compressedInfo = compressedObjects[objNum]
        if (compressedInfo != null) {
            Log.d(TAG, "Object $objNum is compressed in object stream ${compressedInfo.first}, index ${compressedInfo.second}")
            return parseCompressedObject(raf, objNum, compressedInfo.first, compressedInfo.second)
        }

        var offset = xrefTable[objNum]
        if (offset == null) {
            Log.w(TAG, "Object $objNum not found in xref table (size=${xrefTable.size}) or compressed objects (size=${compressedObjects.size}), attempting targeted scan")
            // Try to find this specific object by scanning
            val found = scanForSpecificObject(raf, objNum)
            if (!found) {
                Log.w(TAG, "Object $objNum not found even after scanning")
                return null
            }
            offset = xrefTable[objNum]
            if (offset == null) {
                Log.w(TAG, "Object $objNum still not in xref table after scan")
                return null
            }
        }

        try {
            raf.seek(offset)
            val header = readLine(raf)

            // Skip to dictionary start
            var dictStart = -1L
            var line: String
            var dictAttempts = 0
            while (dictAttempts < 20) {
                val pos = raf.filePointer
                line = readLine(raf)
                if (line.contains("<<")) {
                    dictStart = pos
                    break
                }
                dictAttempts++
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
            // Skip empty lines and look for "stream" keyword
            var streamPos = raf.filePointer
            var nextLine = ""
            var streamAttempts = 0
            while (streamAttempts < 10 && raf.filePointer < raf.length()) {
                streamPos = raf.filePointer
                nextLine = readLine(raf).trim()
                if (nextLine.isNotEmpty()) break
                streamAttempts++
            }
            
            if (objNum <= 5 || objNum == 1) {
                Log.d(TAG, "Object $objNum: nextLine after dict='$nextLine' (first 50 chars)")
            }
            if (nextLine == "stream") {
                val lengthRef = dictContent["Length"]
                val length = when (lengthRef) {
                    is IndirectRef -> {
                        // It's an indirect reference like '123 0 R'
                        if (scanDepth < maxScanDepth - 2) {
                            val indirect = readIndirectInteger(raf, lengthRef.ref)
                            indirect ?: 0
                        } else {
                            0
                        }
                    }
                    is Int -> lengthRef
                    is String -> lengthRef.toIntOrNull() ?: 0
                    else -> 0
                }

                // Sanity check on length (must be positive and reasonable)
                if (length > 0 && length < 100_000_000) {
                    // Move to the start of stream data. After reading the 'stream' line,
                    // the file pointer is at the end of that line so we need to position
                    // correctly. Look for the first byte after linebreak
                    raf.seek(streamPos)
                    // consume until 'stream' line consumed
                    var tmpLine = readLine(raf)
                    // consume the newline after stream marker if present
                    if (raf.filePointer < raf.length()) {
                        val b = raf.read()
                        if (b == '\n'.code) {
                            // ok
                        } else if (b == '\r'.code) {
                            // handle CRLF
                            if (raf.filePointer < raf.length() && raf.read() != '\n'.code) raf.seek(raf.filePointer - 1)
                        } else {
                            // not a newline, step back
                            raf.seek(raf.filePointer - 1)
                        }
                    }

                    val streamBytes = ByteArray(length)
                    raf.readFully(streamBytes)
                    obj.streamData = streamBytes
                    if (objNum <= 5 || objNum == 1) {
                        Log.d(TAG, "Object $objNum: read stream data successfully, size=$length")
                    }
                } else if (length <= 0) {
                    Log.w(TAG, "Object $objNum: stream length is zero or negative (Length=$lengthRef), falling back to searching for 'endstream'")

                    // Fallback: read until 'endstream' marker when Length is missing/invalid
                    try {
                        raf.seek(streamPos)
                        var tmpLine = readLine(raf)
                        // consume possible newline after 'stream' line
                        if (raf.filePointer < raf.length()) {
                            val b = raf.read()
                            if (b == '\n'.code) {
                                // ok
                            } else if (b == '\r'.code) {
                                if (raf.filePointer < raf.length() && raf.read() != '\n'.code) raf.seek(raf.filePointer - 1)
                            } else {
                                raf.seek(raf.filePointer - 1)
                            }
                        }

                        val fallback = readStreamUntilEnd(raf, maxLength = 50_000_000)
                        if (fallback != null) {
                            obj.streamData = fallback
                            Log.d(TAG, "Object $objNum: read stream via fallback (size=${'$'}{fallback.size})")
                        } else {
                            Log.w(TAG, "Object $objNum: fallback stream read failed or timed out")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Object $objNum: fallback stream read error: ${'$'}{e.message}")
                    }
                } else {
                    Log.w(TAG, "Object $objNum: stream length unreasonably large: $length (Length=$lengthRef)")
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
            if (content.contains("/Filter")) {
                Log.d(TAG, "parseDictionaryContent: raw content (first 300 chars): '${content.take(300)}'")
            }

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
                            // Could be a name value (e.g., /FlateDecode) or start of next key
                            if (valueEnd == valueStart) {
                                // This is the value itself (a name), continue scanning
                            } else {
                                // Already scanned some value, this must be next key
                                break
                            }
                        }
                        ch.isWhitespace() && !inString && bracketDepth == 0 && angleDepth == 0 -> {
                            // Check if this ends an indirect reference (e.g., "123 0 R")
                            val beforeSpace = trimmed.substring(valueStart, valueEnd).trim()
                            if (beforeSpace.endsWith("R")) {
                                valueEnd++
                                break
                            }
                            // For simple tokens (numbers, single names), whitespace ends the value
                            if (!beforeSpace.contains(' ')) {
                                // Simple token - break here
                                break
                            } else if (beforeSpace.matches(Regex("\\d+\\s+\\d+\\s+R"))) {
                                // Indirect reference - already complete
                                break
                            } else {
                                // Complex value - end here
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
                    val parsedValue = parseValue(value)
                    dict[name] = parsedValue
                    if (trimmed.contains("/Filter")) {
                        Log.d(TAG, "parseDictionaryContent: extracted key='$name', valueStart=$valueStart, valueEnd=$valueEnd, value='$value', parsed='$parsedValue'")
                    }
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
                    // Represent indirect references explicitly so callers can distinguish
                    val ref = token.split("\\s+".toRegex())[0].toIntOrNull()
                    if (ref != null) IndirectRef(ref) else token
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
         * Wrapper type representing an indirect reference like "123 0 R".
         */
        private data class IndirectRef(val ref: Int)

        /**
         * Resolves a value that may be an Int, IndirectRef, or String to an Int reference if possible.
         */
        private fun resolveRef(value: Any?): Int? {
            return when (value) {
                is IndirectRef -> value.ref
                is Int -> value
                is String -> value.toIntOrNull()
                else -> null
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
            val pagesRef = resolveRef(pagesValue)
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
     * Thread-safe get-or-build for cached page list to avoid concurrent rebuilds
     */
    private fun getOrBuildPageList(raf: RandomAccessFile, catalog: PdfObject): List<Int> {
        synchronized(this) {
            cachedPageList?.let { return it }
            Log.d(TAG, "Building page list (no cache present)")
            val pages = buildPageList(raf, catalog)
            cachedPageList = pages
            return pages
        }
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
        val firstChild = resolveRef(item.getDictValue("First"))
        if (firstChild != null) {
            parseOutlineItems(raf, firstChild, pages, result, level + 1)
        }
        
        // Process siblings
        val next = resolveRef(item.getDictValue("Next"))
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
            val actionRef = resolveRef(item.getDictValue("A"))
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
                is IndirectRef -> {
                    val destObj = parseObject(raf, destValue.ref) ?: return -1
                    val destArray = destObj.dictContent["value"]?.toString() ?: return -1
                    return parseDestinationArray(destArray, pages)
                }
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

    /**
     * Read raw stream bytes until the 'endstream' token is reached.
     * Returns null on timeout or if the marker could not be found within `maxLength`.
     */
    private fun readStreamUntilEnd(raf: RandomAccessFile, maxLength: Int = 50_000_000): ByteArray? {
        val pattern = "endstream".toByteArray(StandardCharsets.ISO_8859_1)
        val baos = java.io.ByteArrayOutputStream()
        var matched = 0
        val startPos = raf.filePointer

        try {
            val limit = minOf(raf.length(), startPos + maxLength)

            while (raf.filePointer < raf.length() && raf.filePointer < limit) {
                val r = raf.read()
                if (r < 0) break
                val b = r.toByte()
                baos.write(b.toInt())

                if (b == pattern[matched]) {
                    matched++
                    if (matched == pattern.size) {
                        // Found endstream - return everything before the marker
                        val all = baos.toByteArray()
                        val result = all.copyOf(all.size - pattern.size)

                        // Position RAF just after the endstream marker
                        // raf.filePointer is already after the last read byte
                        // consume possible newline after endstream
                        if (raf.filePointer < raf.length()) {
                            val c = raf.read()
                            if (c != '\n'.code) raf.seek(raf.filePointer - 1)
                        }

                        return result
                    }
                } else {
                    matched = if (b == pattern[0]) 1 else 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading stream until end: ${'$'}{e.message}")
            try {
                raf.seek(startPos)
            } catch (_: Exception) { }
        }

        return null
    }

    /**
     * Extracts a value from a dictionary string (e.g., "<</Key Value>>" -> extractDictValue(dict, "Key") = "Value")
     */
    private fun extractDictValue(dictStr: String, key: String): String? {
        val pattern = Regex("/$key\\s+(\\d+)")
        val match = pattern.find(dictStr)
        return match?.groupValues?.get(1)
    }

    /**
     * Applies PNG predictor algorithm to decompressed data.
     * PNG predictor adds prediction bytes at the start of each row.
     * Predictor types: 10=None, 11=Sub, 12=Up, 13=Average, 14=Paeth, 15=Optimum
     */
    private fun applyPngPredictor(data: ByteArray, columns: Int): ByteArray {
        if (data.isEmpty()) return data

        val rowSize = columns + 1 // +1 for predictor byte
        val rowCount = data.size / rowSize
        val output = ByteArray(rowCount * columns)

        var outputPos = 0
        for (row in 0 until rowCount) {
            val rowStart = row * rowSize
            val predictorByte = data[rowStart].toInt() and 0xFF
            
            // PNG predictor bytes use 0-4, but PDF DecodeParms use 10-14
            // Normalize: 0=None(10), 1=Sub(11), 2=Up(12), 3=Average(13), 4=Paeth(14)
            val predictor = if (predictorByte < 10) predictorByte + 10 else predictorByte

            for (col in 0 until columns) {
                val rawByte = data[rowStart + 1 + col].toInt() and 0xFF
                val a = if (col > 0) output[outputPos - 1].toInt() and 0xFF else 0
                val b = if (row > 0) output[outputPos - columns].toInt() and 0xFF else 0
                val c = if (row > 0 && col > 0) output[outputPos - columns - 1].toInt() and 0xFF else 0

                val decoded = when (predictor) {
                    0, 10 -> rawByte // None
                    11 -> (rawByte + a) and 0xFF // Sub
                    12 -> (rawByte + b) and 0xFF // Up
                    13 -> (rawByte + ((a + b) / 2)) and 0xFF // Average
                    14 -> (rawByte + paethPredictor(a, b, c)) and 0xFF // Paeth
                    else -> rawByte // Unknown predictor, use as-is
                }

                output[outputPos++] = decoded.toByte()
            }
        }

        return output
    }

    /**
     * Reads an integer from a byte array (big-endian)
     */
    private fun readIntFromBytes(data: ByteArray, offset: Int, numBytes: Int): Int {
        var result = 0
        for (i in 0 until numBytes) {
            result = (result shl 8) or (data[offset + i].toInt() and 0xFF)
        }
        return result
    }

    /**
     * Paeth predictor algorithm (used by PNG)
     */
    private fun paethPredictor(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)

        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
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
