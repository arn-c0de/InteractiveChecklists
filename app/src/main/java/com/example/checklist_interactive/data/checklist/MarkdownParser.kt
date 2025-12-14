package com.example.checklist_interactive.data.checklist

/**
 * Markdown Parser - Custom implementation to replace org.commonmark
 * Parses Markdown documents into a node tree structure
 */

// Node types
sealed class MdNode {
    var parent: MdNode? = null
    var firstChild: MdNode? = null
    var lastChild: MdNode? = null
    var next: MdNode? = null
    var previous: MdNode? = null

    fun appendChild(child: MdNode) {
        child.parent = this
        if (firstChild == null) {
            firstChild = child
            lastChild = child
        } else {
            lastChild?.next = child
            child.previous = lastChild
            lastChild = child
        }
    }

    fun accept(visitor: MdVisitor) {
        visitor.visit(this)
        var child = firstChild
        while (child != null) {
            child.accept(visitor)
            child = child.next
        }
    }
}

class Document : MdNode()
class Heading(val level: Int) : MdNode()
class Paragraph : MdNode()
class ListItem : MdNode()
class BulletList : MdNode()
class Text(val literal: String) : MdNode()
class SoftLineBreak : MdNode()
class HardLineBreak : MdNode()
class Code(val literal: String) : MdNode()
class TaskListItemMarker(val isChecked: Boolean) : MdNode()

// Visitor interface
interface MdVisitor {
    fun visit(node: MdNode) {
        when (node) {
            is Document -> visit(node)
            is Heading -> visit(node)
            is Paragraph -> visit(node)
            is ListItem -> visit(node)
            is BulletList -> visit(node)
            is Text -> visit(node)
            is SoftLineBreak -> visit(node)
            is HardLineBreak -> visit(node)
            is Code -> visit(node)
            is TaskListItemMarker -> visit(node)
        }
    }

    fun visit(document: Document) {}
    fun visit(heading: Heading) {}
    fun visit(paragraph: Paragraph) {}
    fun visit(listItem: ListItem) {}
    fun visit(bulletList: BulletList) {}
    fun visit(text: Text) {}
    fun visit(softLineBreak: SoftLineBreak) {}
    fun visit(hardLineBreak: HardLineBreak) {}
    fun visit(code: Code) {}
    fun visit(taskListItemMarker: TaskListItemMarker) {}
}

abstract class AbstractMdVisitor : MdVisitor {
    override fun visit(node: MdNode) {
        when (node) {
            is Document -> visit(node)
            is Heading -> visit(node)
            is Paragraph -> visit(node)
            is ListItem -> visit(node)
            is BulletList -> visit(node)
            is Text -> visit(node)
            is SoftLineBreak -> visit(node)
            is HardLineBreak -> visit(node)
            is Code -> visit(node)
            is TaskListItemMarker -> visit(node)
        }
    }
}

/**
 * Markdown Parser
 * Supports: Headings, Paragraphs, Lists, Task Lists
 */
class MarkdownParser {

    fun parse(markdown: String): Document {
        val document = Document()
        val lines = markdown.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                // Empty line - skip
                trimmed.isEmpty() -> {
                    i++
                }

                // Heading
                trimmed.startsWith("#") -> {
                    val heading = parseHeading(line)
                    if (heading != null) {
                        document.appendChild(heading)
                    }
                    i++
                }

                // List item (bullet or task list)
                isListItem(trimmed) -> {
                    val listResult = parseList(lines, i)
                    document.appendChild(listResult.first)
                    i = listResult.second
                }

                // Paragraph
                else -> {
                    val paragraphResult = parseParagraph(lines, i)
                    document.appendChild(paragraphResult.first)
                    i = paragraphResult.second
                }
            }
        }

        return document
    }

    private fun parseHeading(line: String): Heading? {
        val match = Regex("^(#{1,6})\\s+(.*)$").find(line.trim()) ?: return null
        val level = match.groupValues[1].length
        val text = match.groupValues[2].trim()

        val heading = Heading(level)
        val paragraph = Paragraph()
        parseInlineContent(text, paragraph)
        heading.appendChild(paragraph)
        return heading
    }

    private fun isListItem(line: String): Boolean {
        return line.matches(Regex("^[-*+]\\s+.*")) ||
               line.matches(Regex("^[-*+]?\\s*\\[[ xX]\\]\\s+.*"))
    }

    private fun parseList(lines: List<String>, startIndex: Int): Pair<BulletList, Int> {
        val list = BulletList()
        var i = startIndex

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                i++
                // Check if next line continues the list
                if (i < lines.size && isListItem(lines[i].trim())) {
                    continue
                } else {
                    break
                }
            }

            if (!isListItem(trimmed)) {
                break
            }

            val listItem = parseListItem(trimmed)
            list.appendChild(listItem)
            i++
        }

        return list to i
    }

    private fun parseListItem(line: String): ListItem {
        val listItem = ListItem()

        // Check for task list item: [ ], [x], [X]
        val taskMatch = Regex("^[-*+]?\\s*\\[([xX ])]\\s+(.*)$").find(line.trim())
        if (taskMatch != null) {
            val checkChar = taskMatch.groupValues[1]
            val isChecked = checkChar.equals("x", ignoreCase = true)
            val text = taskMatch.groupValues[2].trim()

            val marker = TaskListItemMarker(isChecked)
            listItem.appendChild(marker)

            val paragraph = Paragraph()
            parseInlineContent(text, paragraph)
            listItem.appendChild(paragraph)
        } else {
            // Regular list item
            val textMatch = Regex("^[-*+]\\s+(.*)$").find(line.trim())
            val text = textMatch?.groupValues?.get(1)?.trim() ?: line.trim()

            val paragraph = Paragraph()
            parseInlineContent(text, paragraph)
            listItem.appendChild(paragraph)
        }

        return listItem
    }

    private fun parseParagraph(lines: List<String>, startIndex: Int): Pair<Paragraph, Int> {
        val paragraph = Paragraph()
        val text = StringBuilder()
        var i = startIndex

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Stop at empty line, heading, or list item
            if (trimmed.isEmpty() || trimmed.startsWith("#") || isListItem(trimmed)) {
                break
            }

            if (text.isNotEmpty()) {
                text.append(" ")
            }
            text.append(trimmed)
            i++
        }

        parseInlineContent(text.toString(), paragraph)
        return paragraph to i
    }

    private fun parseInlineContent(text: String, parent: MdNode) {
        var remaining = text
        val codePattern = Regex("`([^`]+?)`")

        while (remaining.isNotEmpty()) {
            val codeMatch = codePattern.find(remaining)

            if (codeMatch == null) {
                // No more inline code, add remaining text
                if (remaining.isNotEmpty()) {
                    parent.appendChild(Text(remaining))
                }
                break
            }

            // Add text before code
            if (codeMatch.range.first > 0) {
                val before = remaining.substring(0, codeMatch.range.first)
                parent.appendChild(Text(before))
            }

            // Add code node
            val codeContent = codeMatch.groupValues[1]
            parent.appendChild(Code(codeContent))

            // Continue with remaining text
            remaining = remaining.substring(codeMatch.range.last + 1)
        }
    }
}

// Extension functions for compatibility
fun MdNode.collectText(): String {
    val sb = StringBuilder()
    accept(object : AbstractMdVisitor() {
        override fun visit(text: Text) { sb.append(text.literal) }
        override fun visit(softLineBreak: SoftLineBreak) { sb.append(" ") }
        override fun visit(hardLineBreak: HardLineBreak) { sb.append(" ") }
        override fun visit(code: Code) { sb.append(code.literal) }
    })
    return sb.toString()
}

inline fun <reified T : MdNode> MdNode.findDescendant(): T? {
    var cur: MdNode? = this
    while (cur != null) {
        if (cur is T) return cur
        // Prefer going down to children
        if (cur.firstChild != null) {
            cur = cur.firstChild
            continue
        }
        // Otherwise, walk horizontally to the next or up and to the next
        var next: MdNode? = cur
        while (next != null && next.next == null) {
            next = next.parent
        }
        cur = next?.next
    }
    return null
}
