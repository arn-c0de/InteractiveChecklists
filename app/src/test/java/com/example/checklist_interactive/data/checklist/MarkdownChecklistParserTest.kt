package com.example.checklist_interactive.data.checklist

import org.junit.Test
import org.junit.Assert.*

class MarkdownChecklistParserTest {

    @Test
    fun testParseCheckboxItems() {
        val parser = MarkdownChecklistParser()
        val markdown = """
# Test Checklist

## Cold Start

- [ ] **Electrical Power ON:** Right Shift + L
- [ ] **Engine Start (Left):** Right Shift + Home
  - Monitor RPM.
- [x] **Radar ON:** I
- [ ] **HUD ON:** Optional
""".trimIndent()

        val checklist = parser.parse("test-id", markdown)

        // Assert sections
        assertEquals(1, checklist.sections.size)
        val section = checklist.sections[0]
        assertEquals("Cold Start", section.title)
        assertEquals(4, section.items.size)

        val radarItem = section.items.find { it.text.contains("Radar ON", true) }
        assertNotNull("Radar ON item should be parsed", radarItem)
        assertTrue("Radar ON should be checked", radarItem!!.isChecked)
    }
}
