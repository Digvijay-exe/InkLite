package com.example.data.model

import android.graphics.RectF
import java.util.UUID

/**
 * Single sampled point with coordinates in virtual page space (e.g. 1200 x 1600)
 * and optional normalized pressure (0.0 .. 1.0).
 */
data class InkPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f
)

/**
 * Immutable vector ink stroke.
 * Stored as points, rendered live with Canvas/Path.
 */
data class InkStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<InkPoint>,
    val color: Int,
    val strokeWidth: Float,
    val isHighlighter: Boolean = false,
    val bounds: RectF = computeBounds(points, strokeWidth)
) {
    companion object {
        fun computeBounds(points: List<InkPoint>, width: Float): RectF {
            if (points.isEmpty()) return RectF(0f, 0f, 0f, 0f)
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            for (p in points) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }
            val halfW = (width / 2f).coerceAtLeast(2f)
            return RectF(minX - halfW, minY - halfW, maxX + halfW, maxY + halfW)
        }
    }
}

/**
 * Procedural paper templates drawn vectorially without raster bitmap memory.
 */
enum class PageTemplate(val title: String, val description: String) {
    BLANK("Blank", "Clean empty page"),
    LINED("Lined", "College-ruled notebook lines"),
    GRID("Grid", "Math / graph paper grid"),
    DOT_GRID("Dot Grid", "Subtle bullet-journal dots");

    companion object {
        fun fromString(value: String?): PageTemplate {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: BLANK
        }
    }
}

/**
 * Notebook entity.
 */
data class Notebook(
    val id: String,
    val title: String,
    val coverColor: Int,
    val template: PageTemplate,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int = 1,
    val isPdf: Boolean = false,
    val pdfFilePath: String? = null
)

/**
 * Page entity within a notebook.
 */
data class NotebookPage(
    val id: String,
    val notebookId: String,
    val pageNumber: Int,
    val template: PageTemplate,
    val pdfPageIndex: Int = -1,
    val strokeFilePath: String,
    val updatedAt: Long
)
