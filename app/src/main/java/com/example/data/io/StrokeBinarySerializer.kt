package com.example.data.io

import android.graphics.RectF
import com.example.data.model.InkPoint
import com.example.data.model.InkStroke
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/**
 * Compact binary serializer for vector strokes.
 * Uses native DataStreams to avoid JSON/XML parsing overhead and memory spikes.
 */
object StrokeBinarySerializer {
    private const val MAGIC_HEADER = 0x494E4B31 // "INK1"
    private const val FORMAT_VERSION: Short = 1

    fun saveStrokes(file: File, strokes: List<InkStroke>) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val tempFile = File(file.parent, file.name + ".tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tempFile), 8192)).use { out ->
            out.writeInt(MAGIC_HEADER)
            out.writeShort(FORMAT_VERSION.toInt())
            out.writeInt(strokes.size)

            for (stroke in strokes) {
                out.writeUTF(stroke.id)
                out.writeInt(stroke.color)
                out.writeFloat(stroke.strokeWidth)
                out.writeBoolean(stroke.isHighlighter)

                val points = stroke.points
                out.writeInt(points.size)
                for (p in points) {
                    out.writeFloat(p.x)
                    out.writeFloat(p.y)
                    out.writeFloat(p.pressure)
                }
            }
            out.flush()
        }
        if (file.exists()) {
            file.delete()
        }
        tempFile.renameTo(file)
    }

    fun loadStrokes(file: File): List<InkStroke> {
        if (!file.exists() || file.length() < 10) {
            return emptyList()
        }

        val strokes = mutableListOf<InkStroke>()
        try {
            DataInputStream(BufferedInputStream(FileInputStream(file), 8192)).use { input ->
                val magic = input.readInt()
                if (magic != MAGIC_HEADER) {
                    // Invalid file header
                    return emptyList()
                }
                val version = input.readShort()
                val count = input.readInt()

                for (i in 0 until count) {
                    val id = input.readUTF()
                    val color = input.readInt()
                    val strokeWidth = input.readFloat()
                    val isHighlighter = input.readBoolean()

                    val pointCount = input.readInt()
                    val points = ArrayList<InkPoint>(pointCount)
                    for (p in 0 until pointCount) {
                        val x = input.readFloat()
                        val y = input.readFloat()
                        val pressure = input.readFloat()
                        points.add(InkPoint(x, y, pressure))
                    }
                    val bounds = InkStroke.computeBounds(points, strokeWidth)
                    strokes.add(InkStroke(id, points, color, strokeWidth, isHighlighter, bounds))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return strokes
    }
}
