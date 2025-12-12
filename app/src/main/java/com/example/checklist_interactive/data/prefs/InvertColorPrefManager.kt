package com.example.checklist_interactive.data.prefs

import android.content.Context
import android.content.SharedPreferences
import java.net.URLDecoder

class InvertColorPrefManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("invert_color_prefs", Context.MODE_PRIVATE)

    private fun normalizePath(path: String): String {
        var p = path
        try {
            p = URLDecoder.decode(p, "UTF-8")
        } catch (_: Exception) {}
        if (p.startsWith("asset://")) p = p.removePrefix("asset://")
        return p.trim()
    }

    fun isInverted(pdfPath: String): Boolean =
        prefs.getBoolean(normalizePath(pdfPath), false)

    fun setInverted(pdfPath: String, inverted: Boolean) {
        prefs.edit().putBoolean(normalizePath(pdfPath), inverted).apply()
    }
}
