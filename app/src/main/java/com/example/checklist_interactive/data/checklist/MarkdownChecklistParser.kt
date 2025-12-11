package com.example.checklist_interactive.data.checklist

import org.commonmark.Extension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser

class MarkdownChecklistParser {
    private val extensions: List<Extension> = listOf(TaskListItemsExtension.create())
    private val parser: Parser = Parser.builder().extensions(extensions).build()

    fun parse(id: String, markdown: String): Checklist {
        val document = parser.parse(markdown)
        val sections = mutableListOf<ChecklistSection>()
        var currentSectionTitle = ""
        var itemIndex = 0

        // We'll traverse the top-level nodes in document
        var node: Node? = document.firstChild
        while (node != null) {
            when (node) {
                is Heading -> {
                    currentSectionTitle = collectText(node)
                    sections.add(ChecklistSection(currentSectionTitle, mutableListOf()))
                }
                is ListBlock -> {
                    // Iterate list items in this list block
                    var itemNode: Node? = node.firstChild
                    while (itemNode != null) {
                        if (itemNode is ListItem) {
                            val text = collectText(itemNode).trim()
                            val isTaskItem = hasTaskItem(itemNode)
                            val (cleanText, checkedFlag) = if (isTaskItem) extractTaskMarker(text) else Pair(text, false)
                            val itemId = "$id-$itemIndex"
                            val checklistItem = ChecklistItem(itemId, cleanText, 0, checkedFlag)
                            appendItemToLastSection(sections, checklistItem)
                            itemIndex++
                        }
                        itemNode = itemNode.next
                    }
                }
                is Paragraph -> {
                    // Plain paragraph; turn into a section heading if there hasn't been a heading
                    if (sections.isEmpty()) {
                        currentSectionTitle = collectText(node)
                        sections.add(ChecklistSection(currentSectionTitle, mutableListOf()))
                    }
                }
            }
            node = node.next
        }

        // Ensure at least one section
        if (sections.isEmpty()) {
            sections.add(ChecklistSection("Checklist", emptyList()))
        }

        // Convert mutables to immutables
        val fixedSections = sections.map { s -> s.copy(items = s.items.toList()) }
        val title = if (fixedSections.isNotEmpty()) fixedSections.first().title else "Checklist"
        return Checklist(id, title, fixedSections)
    }

    private fun appendItemToLastSection(sections: MutableList<ChecklistSection>, item: ChecklistItem) {
        if (sections.isEmpty()) {
            sections.add(ChecklistSection("Checklist", mutableListOf(item)))
        } else {
            val last = sections.last()
            val updated = ChecklistSection(last.title, (last.items.toMutableList().apply { add(item) }))
            sections[sections.size - 1] = updated
        }
    }

    private fun collectText(node: Node): String {
        val sb = StringBuilder()
        node.accept(object : AbstractVisitor() {
            override fun visit(text: Text) {
                sb.append(text.literal)
            }

            override fun visit(softLineBreak: SoftLineBreak) {
                sb.append(" ")
            }

            override fun visit(hardLineBreak: HardLineBreak) {
                sb.append(" ")
            }

            override fun visit(code: Code) {
                sb.append(code.literal)
            }

            override fun visit(heading: Heading) {
                visitChildren(heading)
            }

            override fun visit(paragraph: Paragraph) {
                visitChildren(paragraph)
            }

            override fun visit(listItem: ListItem) {
                visitChildren(listItem)
            }
        })
        return sb.toString()
    }

    private fun hasTaskItem(node: ListItem): Boolean {
        // CommonMark task list has a first child that is a Paragraph. The TaskListItems extension wraps it.
        // The extension doesn't add a dedicated node type but adds a TaskListItemMarker to the list item.
        var child = node.firstChild
        while (child != null) {
            if (child is Paragraph) {
                // Inspect literal children for a '[ ]' or '[x]' prefix
                val text = collectText(child)
                if (text.startsWith("[ ]") || text.startsWith("[x]") || text.startsWith("[X]")) {
                    return true
                }
            }
            child = child.next
        }
        return false
    }

    private fun extractTaskMarker(text: String): Pair<String, Boolean> {
        val trimmed = text.trimStart()
        return when {
            trimmed.startsWith("[x]", true) || trimmed.startsWith("[X]") -> Pair(trimmed.drop(3).trimStart(), true)
            trimmed.startsWith("[ ]") -> Pair(trimmed.drop(3).trimStart(), false)
            else -> Pair(text, false)
        }
    }
}
