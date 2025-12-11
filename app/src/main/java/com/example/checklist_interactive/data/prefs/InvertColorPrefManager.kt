package com.example.checklist_interactive.data.prefs

import android.content.Context
import android.content.SharedPreferences

class InvertColorPrefManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("invert_color_prefs", Context.MODE_PRIVATE)

    fun isInverted(pdfPath: String): Boolean =
        prefs.getBoolean(pdfPath, false)

    fun setInverted(pdfPath: String, inverted: Boolean) {
        prefs.edit().putBoolean(pdfPath, inverted).apply()
    }
}
