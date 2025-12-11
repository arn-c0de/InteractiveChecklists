package com.example.checklist_interactive

import com.example.checklist_interactive.data.checklist.MarkdownChecklistParser
import org.junit.Assert.*
import org.junit.Test

class MarkdownChecklistParserTest {
    @Test
    fun parsesTaskList() {
        val md = """
        # Example
        - [ ] item one
        - [x] item two
        """.trimIndent()

        val parser = MarkdownChecklistParser()
        val checklist = parser.parse("sample", md)

        assertEquals("sample", checklist.id)
        assertTrue(checklist.sections.isNotEmpty())
        val items = checklist.sections.flatMap { it.items }
        assertEquals(2, items.size)
        assertEquals("item one", items[0].text)
        assertFalse(items[0].isChecked)
        assertEquals("item two", items[1].text)
        assertTrue(items[1].isChecked)
    }

    @Test
    fun parsesCheckedAndHeadings() {
        val md = """
        # Startup

        - [ ] preflight
        - [x] engines

        ## Taxi
        - [ ] flaps
        """.trimIndent()

        val parser = MarkdownChecklistParser()
        val checklist = parser.parse("sample2", md)

        assertEquals("sample2", checklist.id)
        assertTrue(checklist.sections.isNotEmpty())
        // Should include sections with titles "Startup" and "Taxi"
        assertTrue(checklist.sections.any { it.title.contains("Startup") })
        assertTrue(checklist.sections.any { it.title.contains("Taxi") })
        val items = checklist.sections.flatMap { it.items }
        assertTrue(items.any { it.text.contains("preflight") })
        assertTrue(items.any { it.text.contains("engines") })
    }
}
