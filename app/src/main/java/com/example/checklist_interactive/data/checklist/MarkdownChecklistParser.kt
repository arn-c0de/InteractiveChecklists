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
                    sections.add(ChecklistSection(currentSectionTitle, currentItems.toList()))
                    currentItems.clear()
                }

                val title = heading.collectText()
                if (heading.level == 1 && checklistTitle.isEmpty()) {
                    checklistTitle = title
                }

                // Only create a section for H2+ (or H1 if not used as global title)
                if (heading.level >= 2 || (heading.level == 1 && sections.isNotEmpty())) {
                    currentSectionTitle = title
                }
            }

            override fun visit(listItem: ListItem) {
                ensureSection()

                val (text, checked) = extractTaskItemTextAndState(listItem)

                if (text.isNotBlank()) {
                    val itemId = "$id-${itemCounter++}"
                    currentItems.add(ChecklistItem(itemId, text, 0, checked))
                }

                // Continue traversal
                super.visit(listItem)
            }
        })

        // Add remaining items
        if (currentItems.isNotEmpty()) {
            sections.add(ChecklistSection(currentSectionTitle.ifEmpty { checklistTitle.ifEmpty { "General" } }, currentItems))
        }

        // Fallback: if nothing was parsed, try line-by-line regex
        if (sections.isEmpty() || sections.all { it.items.isEmpty() }) {
            val fallback = parseWithSimpleRegex(id, markdown, checklistTitle)
            if (fallback.sections.isNotEmpty()) return fallback
        }

        // Ensure at least one section exists
        if (sections.isEmpty()) {
            val title = checklistTitle.ifEmpty { "Checklist" }
            sections.add(ChecklistSection(title, emptyList()))
        }

        if (checklistTitle.isEmpty()) {
            checklistTitle = sections.firstOrNull()?.title ?: "Checklist"
        }

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

    private fun extractTaskItemTextAndState(listItem: ListItem): Pair<String, Boolean> {
        // 1. Preferred: TaskListItemMarker from the extension
        val marker = listItem.findDescendant<TaskListItemMarker>()
        if (marker != null) {
            val textNode = marker.next
            val text = textNode?.collectText() ?: ""
            return text.trim() to marker.isChecked
        }

        // 2. Fallback: look for checkbox in the first paragraph's text
        val firstParagraph = listItem.firstChild as? Paragraph
        val rawText = firstParagraph?.collectText() ?: listItem.collectText()

        val regex = Regex("""^\s*\[([ xX])\]\s*(.*)""")
        val match = regex.find(rawText)
        return if (match != null) {
            val checked = match.groupValues[1].equals("x", ignoreCase = true)
            val text = match.groupValues[2].trim()
            text to checked
        } else {
            rawText.trim() to false
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
                items.add(ChecklistItem("$id-fb-${counter++}", text, 0, checked))
            }
        }

        if (items.isEmpty()) return Checklist(id, suggestedTitle.ifEmpty { "Checklist" }, emptyList())

        val title = suggestedTitle.ifEmpty { "Checklist" }
        return Checklist(id, title, listOf(ChecklistSection(title, items)))
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