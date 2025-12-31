package com.example.checklist_interactive.ui.checklist.pdf

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AnnotationsRepository {
    private fun annFile(context: Context, pdfPath: String): File {
        val dir = File(context.filesDir, "annotations")
        if (!dir.exists()) dir.mkdirs()
        // sanitize path
        val name = pdfPath.replace('/', '_')
        return File(dir, "$name.json")
    }

    fun save(context: Context, pdfPath: String, strokes: List<AnnotationStroke>) {
        val file = annFile(context, pdfPath)
        val arr = JSONArray()
        for (s in strokes) {
            val o = JSONObject()
            o.put("page", s.page)
            o.put("color", s.color)
            o.put("strokeWidth", s.strokeWidth)
            o.put("isHighlight", s.isHighlight)
            val pts = JSONArray()
            for (p in s.points) {
                // store doc coordinates
                val pp = JSONObject().put("x", p.first).put("y", p.second)
                pts.put(pp)
            }
            o.put("points", pts)
            arr.put(o)
        }
        file.writeText(arr.toString())
    }

    fun load(context: Context, pdfPath: String): List<AnnotationStroke> {
        val file = annFile(context, pdfPath)
        if (!file.exists()) return emptyList()
        return try {
            val s = file.readText()
            val arr = JSONArray(s)
            val out = mutableListOf<AnnotationStroke>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val page = o.getInt("page")
                val color = o.getLong("color")
                val strokeWidth = o.getDouble("strokeWidth").toFloat()
                val isHighlight = o.optBoolean("isHighlight", false)
                val pts = o.getJSONArray("points")
                val points = (0 until pts.length()).map { j ->
                    val p = pts.getJSONObject(j)
                    // doc coordinates
                    Pair(p.getDouble("x").toFloat(), p.getDouble("y").toFloat())
                }
                out.add(AnnotationStroke(page, color, strokeWidth, points, isHighlight))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }
}
