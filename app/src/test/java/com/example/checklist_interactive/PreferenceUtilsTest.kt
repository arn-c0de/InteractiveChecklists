package com.example.checklist_interactive

import com.example.checklist_interactive.data.checklist.PreferenceUtils
import org.junit.Assert.*
import org.junit.Test

class PreferenceUtilsTest {
    @Test
    fun preferenceNameHasNoPathSeparator() {
        val path = "/data/user/0/com.example.checklist_interactive/files/documents/checklists/p51_startup.md.xml"
        val prefName = PreferenceUtils.getPreferenceNameForChecklist(path)
        assertFalse(prefName.contains("/"))
        assertFalse(prefName.contains("\\\\"))
        assertTrue(prefName.startsWith("checklist_state_"))
    }

    @Test
    fun preferenceNameIsDeterministic() {
        val path = "/some/random/path/checklist.md"
        val p1 = PreferenceUtils.getPreferenceNameForChecklist(path)
        val p2 = PreferenceUtils.getPreferenceNameForChecklist(path)
        assertEquals(p1, p2)
    }

    @Test
    fun preferenceNameLengthIsConstant() {
        val path1 = "/a"
        val path2 = "/very/long/path/with/many/components/and/file.md"
        val p1 = PreferenceUtils.getPreferenceNameForChecklist(path1)
        val p2 = PreferenceUtils.getPreferenceNameForChecklist(path2)
        assertEquals(p1.length, p2.length)
    }
}
