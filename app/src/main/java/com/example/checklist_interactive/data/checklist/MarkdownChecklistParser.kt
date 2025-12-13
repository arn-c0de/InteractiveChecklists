package com.example.checklist_interactive.data.checklist

import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.ext.task.list.items.TaskListItemMarker

class MarkdownChecklistParser {

    private val parser: Parser = Parser.builder()
        .extensions(listOf(TaskListItemsExtension.create()))
        .build()

    fun parse(id: String, markdown: String): Checklist {
        val document = parser.parse(markdown.trim())

        var checklistTitle = findMainTitle(document)
        val sections = mutableListOf<ChecklistSection>()
        var currentSectionTitle = ""
        var currentSectionLevel = 2
        var currentItems = mutableListOf<ChecklistItem>()
        var itemCounter = 0

        // Default section if we start directly with items
        fun ensureSection() {
            if (currentSectionTitle.isEmpty()) {
                currentSectionTitle = checklistTitle.ifEmpty { "General" }
            }
        }

        document.accept(object : AbstractVisitor() {

            override fun visit(heading: Heading) {
                // Finish previous section
                if (currentItems.isNotEmpty()) {
                    sections.add(ChecklistSection(currentSectionTitle, currentItems.toList(), currentSectionLevel))
                    currentItems.clear()
                }

                val title = heading.collectText()
                if (heading.level == 1 && checklistTitle.isEmpty()) {
                    checklistTitle = title
                    // Don't create a section for the main title
                    return
                }

                // Create a section for H2 and H3 headings
                if (heading.level == 2 || heading.level == 3) {
                    currentSectionTitle = title
                    currentSectionLevel = heading.level
                }
            }

            override fun visit(listItem: ListItem) {
                ensureSection()

                // Determine indent level by counting ancestor ListItem nodes
                var ancestor: Node? = listItem.parent
                var indentLevel = 0
                while (ancestor != null) {
                    if (ancestor is ListItem) indentLevel++
                    ancestor = ancestor.parent
                }

                val (text, checked, isTask) = extractTaskItemTextAndState(listItem)

                if (text.isNotBlank()) {
                    val itemId = "$id-${itemCounter++}"
                    currentItems.add(ChecklistItem(itemId, text, indentLevel, checked, isTask))
                }

                // Continue traversal
                super.visit(listItem)
            }
        })

        // Add remaining items
        if (currentItems.isNotEmpty()) {
            sections.add(ChecklistSection(currentSectionTitle.ifEmpty { checklistTitle.ifEmpty { "General" } }, currentItems, currentSectionLevel))
        }

        // Fallback: if nothing was parsed, try line-by-line regex
        if (sections.isEmpty() || sections.all { it.items.isEmpty() }) {
            val fallback = parseWithSimpleRegex(id, markdown, checklistTitle)
            if (fallback.sections.isNotEmpty()) return fallback
        }

        // Ensure at least one section exists
        if (sections.isEmpty()) {
            val title = checklistTitle.ifEmpty { "Checklist" }
            sections.add(ChecklistSection(title, emptyList(), 2))
        }

        if (checklistTitle.isEmpty()) {
            checklistTitle = sections.firstOrNull()?.title ?: "Checklist"
        }

        android.util.Log.d("MarkdownChecklistParser", "Parsed checklist: id=$id, title=$checklistTitle, sections=${sections.size}, items=${sections.sumOf { it.items.size }}")

        return Checklist(id, checklistTitle, sections)
    }

    private fun findMainTitle(node: Node): String {
        val visitor = object : AbstractVisitor() {
            var title = ""
            override fun visit(heading: Heading) {
                if (heading.level == 1 && title.isEmpty()) {
                    title = heading.collectText()
                }
            }
        }
        node.accept(visitor)
        return visitor.title
    }

    private fun extractTaskItemTextAndState(listItem: ListItem): Triple<String, Boolean, Boolean> {
        // Preferred: TaskListItemMarker from the extension, but be robust and fallback to the full item text
        val marker = listItem.findDescendant<TaskListItemMarker>()
        val rawText = listItem.collectText()

        if (marker != null) {
            // Build a regex to extract the checkbox state and the remainder of the line
            val regex = Regex("""^\s*\[([ xX])\]\s*(.*)""")
            val match = regex.find(rawText)
            if (match != null) {
                val checked = match.groupValues[1].equals("x", ignoreCase = true)
                val text = match.groupValues[2].trim()
                return Triple(text, checked, true)
            }
            // Fallback if regex didn't match: use marker.isChecked and the collected text without the marker prefix
            val stripped = rawText.replaceFirst(Regex("""^\s*\[[ xX]\]\s*"""), "").trim()
            return Triple(stripped, marker.isChecked, true)
        }

        // Fallback: look for checkbox in the first paragraph's text
        val firstParagraph = listItem.firstChild as? Paragraph
        val paragraphText = firstParagraph?.collectText() ?: rawText

        val regex = Regex("""^\s*\[([ xX])\]\s*(.*)""")
        val match = regex.find(paragraphText)
        return if (match != null) {
            val checked = match.groupValues[1].equals("x", ignoreCase = true)
            val text = match.groupValues[2].trim()
            Triple(text, checked, true)
        } else {
            Triple(paragraphText.trim(), false, false)
        }
    }

    private fun parseWithSimpleRegex(id: String, markdown: String, suggestedTitle: String): Checklist {
        val items = mutableListOf<ChecklistItem>()
        var counter = 0

        markdown.lines().forEach { line ->
            val match = Regex("""^\s*[-*]?\s*\[([ xX])\]\s*(.*)""").find(line)
                ?: Regex("""^\s*\[([ xX])\]\s*(.*)""").find(line) ?: return@forEach

            val checked = match.groupValues[1].equals("x", ignoreCase = true)
            val text = match.groupValues[2].trim()
            if (text.isNotBlank()) {
                items.add(ChecklistItem("$id-fb-${counter++}", text, 0, checked, true))
            }
        }

        if (items.isEmpty()) return Checklist(id, suggestedTitle.ifEmpty { "Checklist" }, emptyList())

        val title = suggestedTitle.ifEmpty { "Checklist" }
        return Checklist(id, title, listOf(ChecklistSection(title, items, 2)))
    }
}

// Helper extensions
private fun Node.collectText(): String {
    val sb = StringBuilder()
    accept(object : AbstractVisitor() {
        override fun visit(text: Text) { sb.append(text.literal) }
        override fun visit(softLineBreak: SoftLineBreak) { sb.append(" ") }
        override fun visit(hardLineBreak: HardLineBreak) { sb.append(" ") }
        override fun visit(code: Code) { sb.append(code.literal) }
    })
    return sb.toString()
}

private inline fun <reified T : Node> Node.findDescendant(): T? {
    var cur: Node? = this
    while (cur != null) {
        if (cur is T) return cur
        // Prefer going down to children
        if (cur.firstChild != null) {
            cur = cur.firstChild
            continue
        }
        // Otherwise, walk horizontally to the next or up and to the next
        var next: Node? = cur
        while (next != null && next.next == null) {
            next = next.parent
        }
        cur = next?.next
    }
    return null
}