package com.example.checklist_interactive.ui.checklist

import androidx.compose.ui.geometry.Offset

/**
 * A simple freehand annotation data model.
 * Points are stored normalized to [0..1] relative to the current view width/height
 * so they can be scaled for different screen sizes.
 */
data class AnnotationStroke(
    val page: Int,
    val color: Long, // ARGB
    val strokeWidth: Float,
    // Points are stored in PDF document coordinates (unscaled), not normalized screen coordinates.
    val points: List<Pair<Float, Float>>, // docX, docY
    val isHighlight: Boolean = false
)
