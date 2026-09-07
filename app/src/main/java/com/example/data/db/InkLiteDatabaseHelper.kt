package com.example.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.data.model.InkPoint
import com.example.data.model.InkStroke
import com.example.data.model.Notebook
import com.example.data.model.NotebookPage
import com.example.data.model.PageTemplate
import com.example.data.io.StrokeBinarySerializer
import java.io.File
import java.util.UUID

class InkLiteDatabaseHelper private constructor(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "inklite.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: InkLiteDatabaseHelper? = null

        fun getInstance(context: Context): InkLiteDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InkLiteDatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE notebooks (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                cover_color INTEGER NOT NULL,
                template TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                is_pdf INTEGER NOT NULL DEFAULT 0,
                pdf_file_path TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE pages (
                id TEXT PRIMARY KEY,
                notebook_id TEXT NOT NULL,
                page_number INTEGER NOT NULL,
                template TEXT NOT NULL,
                pdf_page_index INTEGER NOT NULL DEFAULT -1,
                stroke_file_path TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY (notebook_id) REFERENCES notebooks (id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_pages_notebook ON pages (notebook_id, page_number)")

        // Seed with a welcoming starter notebook
        seedStarterNotebook(db)
    }

    private fun seedStarterNotebook(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val notebookId = UUID.randomUUID().toString()
        val page1Id = UUID.randomUUID().toString()
        val page2Id = UUID.randomUUID().toString()

        val strokeFile1 = getStrokeFile(page1Id)
        val strokeFile2 = getStrokeFile(page2Id)

        // Seed sample strokes on page 1: Hand-drawn "Welcome to InkLite" & star
        val sampleStrokesPage1 = mutableListOf<InkStroke>()
        // Draw a neat cursive underline or sample line
        val underlinePoints = mutableListOf<InkPoint>()
        for (i in 0..50) {
            val progress = i / 50f
            val x = 200f + progress * 800f
            val y = 500f + kotlin.math.sin(progress * Math.PI.toFloat() * 2f) * 15f
            underlinePoints.add(InkPoint(x, y, 0.8f + progress * 0.4f))
        }
        sampleStrokesPage1.add(
            InkStroke(
                points = underlinePoints,
                color = 0xFF2563EB.toInt(), // Nice vibrant blue
                strokeWidth = 6f
            )
        )
        StrokeBinarySerializer.saveStrokes(strokeFile1, sampleStrokesPage1)

        val nbValues = ContentValues().apply {
            put("id", notebookId)
            put("title", "Quick Start Guide")
            put("cover_color", 0xFF0284C7.toInt())
            put("template", PageTemplate.LINED.name)
            put("created_at", now)
            put("updated_at", now)
            put("is_pdf", 0)
            putNull("pdf_file_path")
        }
        db.insert("notebooks", null, nbValues)

        val p1Values = ContentValues().apply {
            put("id", page1Id)
            put("notebook_id", notebookId)
            put("page_number", 1)
            put("template", PageTemplate.LINED.name)
            put("pdf_page_index", -1)
            put("stroke_file_path", strokeFile1.absolutePath)
            put("updated_at", now)
        }
        db.insert("pages", null, p1Values)

        val p2Values = ContentValues().apply {
            put("id", page2Id)
            put("notebook_id", notebookId)
            put("page_number", 2)
            put("template", PageTemplate.GRID.name)
            put("pdf_page_index", -1)
            put("stroke_file_path", strokeFile2.absolutePath)
            put("updated_at", now)
        }
        db.insert("pages", null, p2Values)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migration logic for future schema changes
    }

    fun getStrokeFile(pageId: String): File {
        val dir = File(context.filesDir, "strokes")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "page_$pageId.ink")
    }

    fun getAllNotebooks(): List<Notebook> {
        val list = mutableListOf<Notebook>()
        val db = readableDatabase
        val query = """
            SELECT n.id, n.title, n.cover_color, n.template, n.created_at, n.updated_at, n.is_pdf, n.pdf_file_path,
                   (SELECT COUNT(*) FROM pages p WHERE p.notebook_id = n.id) as page_count
            FROM notebooks n
            ORDER BY n.updated_at DESC
        """.trimIndent()

        db.rawQuery(query, null).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("id")
            val titleIdx = cursor.getColumnIndexOrThrow("title")
            val colorIdx = cursor.getColumnIndexOrThrow("cover_color")
            val tmplIdx = cursor.getColumnIndexOrThrow("template")
            val createdIdx = cursor.getColumnIndexOrThrow("created_at")
            val updatedIdx = cursor.getColumnIndexOrThrow("updated_at")
            val isPdfIdx = cursor.getColumnIndexOrThrow("is_pdf")
            val pdfPathIdx = cursor.getColumnIndexOrThrow("pdf_file_path")
            val pageCountIdx = cursor.getColumnIndexOrThrow("page_count")

            while (cursor.moveToNext()) {
                list.add(
                    Notebook(
                        id = cursor.getString(idIdx),
                        title = cursor.getString(titleIdx),
                        coverColor = cursor.getInt(colorIdx),
                        template = PageTemplate.fromString(cursor.getString(tmplIdx)),
                        createdAt = cursor.getLong(createdIdx),
                        updatedAt = cursor.getLong(updatedIdx),
                        pageCount = cursor.getInt(pageCountIdx).coerceAtLeast(1),
                        isPdf = cursor.getInt(isPdfIdx) == 1,
                        pdfFilePath = cursor.getString(pdfPathIdx)
                    )
                )
            }
        }
        return list
    }

    fun getNotebook(notebookId: String): Notebook? {
        val db = readableDatabase
        val query = """
            SELECT n.id, n.title, n.cover_color, n.template, n.created_at, n.updated_at, n.is_pdf, n.pdf_file_path,
                   (SELECT COUNT(*) FROM pages p WHERE p.notebook_id = n.id) as page_count
            FROM notebooks n
            WHERE n.id = ?
        """.trimIndent()

        db.rawQuery(query, arrayOf(notebookId)).use { cursor ->
            if (cursor.moveToNext()) {
                return Notebook(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    coverColor = cursor.getInt(cursor.getColumnIndexOrThrow("cover_color")),
                    template = PageTemplate.fromString(cursor.getString(cursor.getColumnIndexOrThrow("template"))),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                    pageCount = cursor.getInt(cursor.getColumnIndexOrThrow("page_count")).coerceAtLeast(1),
                    isPdf = cursor.getInt(cursor.getColumnIndexOrThrow("is_pdf")) == 1,
                    pdfFilePath = cursor.getString(cursor.getColumnIndexOrThrow("pdf_file_path"))
                )
            }
        }
        return null
    }

    fun createNotebook(
        title: String,
        coverColor: Int,
        template: PageTemplate,
        isPdf: Boolean = false,
        pdfFilePath: String? = null,
        initialPages: Int = 1
    ): Notebook {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val notebookId = UUID.randomUUID().toString()

        val values = ContentValues().apply {
            put("id", notebookId)
            put("title", title)
            put("cover_color", coverColor)
            put("template", template.name)
            put("created_at", now)
            put("updated_at", now)
            put("is_pdf", if (isPdf) 1 else 0)
            put("pdf_file_path", pdfFilePath)
        }
        db.insert("notebooks", null, values)

        val pageCount = initialPages.coerceAtLeast(1)
        for (i in 1..pageCount) {
            val pageId = UUID.randomUUID().toString()
            val strokeFile = getStrokeFile(pageId)
            val pValues = ContentValues().apply {
                put("id", pageId)
                put("notebook_id", notebookId)
                put("page_number", i)
                put("template", template.name)
                put("pdf_page_index", if (isPdf) (i - 1) else -1)
                put("stroke_file_path", strokeFile.absolutePath)
                put("updated_at", now)
            }
            db.insert("pages", null, pValues)
        }

        return Notebook(
            id = notebookId,
            title = title,
            coverColor = coverColor,
            template = template,
            createdAt = now,
            updatedAt = now,
            pageCount = pageCount,
            isPdf = isPdf,
            pdfFilePath = pdfFilePath
        )
    }

    fun updateNotebookTitle(notebookId: String, newTitle: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("title", newTitle)
            put("updated_at", System.currentTimeMillis())
        }
        db.update("notebooks", values, "id = ?", arrayOf(notebookId))
    }

    fun deleteNotebook(notebookId: String) {
        val pages = getPagesForNotebook(notebookId)
        for (page in pages) {
            val file = File(page.strokeFilePath)
            if (file.exists()) file.delete()
        }
        val db = writableDatabase
        db.delete("pages", "notebook_id = ?", arrayOf(notebookId))
        db.delete("notebooks", "id = ?", arrayOf(notebookId))
    }

    fun getPagesForNotebook(notebookId: String): List<NotebookPage> {
        val pages = mutableListOf<NotebookPage>()
        val db = readableDatabase
        db.rawQuery(
            "SELECT * FROM pages WHERE notebook_id = ? ORDER BY page_number ASC",
            arrayOf(notebookId)
        ).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("id")
            val pNumIdx = cursor.getColumnIndexOrThrow("page_number")
            val tmplIdx = cursor.getColumnIndexOrThrow("template")
            val pdfIdx = cursor.getColumnIndexOrThrow("pdf_page_index")
            val strokePathIdx = cursor.getColumnIndexOrThrow("stroke_file_path")
            val updatedIdx = cursor.getColumnIndexOrThrow("updated_at")

            while (cursor.moveToNext()) {
                pages.add(
                    NotebookPage(
                        id = cursor.getString(idIdx),
                        notebookId = notebookId,
                        pageNumber = cursor.getInt(pNumIdx),
                        template = PageTemplate.fromString(cursor.getString(tmplIdx)),
                        pdfPageIndex = cursor.getInt(pdfIdx),
                        strokeFilePath = cursor.getString(strokePathIdx),
                        updatedAt = cursor.getLong(updatedIdx)
                    )
                )
            }
        }
        return pages
    }

    fun addPage(notebookId: String, template: PageTemplate, pdfPageIndex: Int = -1): NotebookPage {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val currentPages = getPagesForNotebook(notebookId)
        val nextPageNumber = currentPages.size + 1
        val pageId = UUID.randomUUID().toString()
        val strokeFile = getStrokeFile(pageId)

        val values = ContentValues().apply {
            put("id", pageId)
            put("notebook_id", notebookId)
            put("page_number", nextPageNumber)
            put("template", template.name)
            put("pdf_page_index", pdfPageIndex)
            put("stroke_file_path", strokeFile.absolutePath)
            put("updated_at", now)
        }
        db.insert("pages", null, values)

        // Update notebook timestamp
        val nbValues = ContentValues().apply {
            put("updated_at", now)
        }
        db.update("notebooks", nbValues, "id = ?", arrayOf(notebookId))

        return NotebookPage(
            id = pageId,
            notebookId = notebookId,
            pageNumber = nextPageNumber,
            template = template,
            pdfPageIndex = pdfPageIndex,
            strokeFilePath = strokeFile.absolutePath,
            updatedAt = now
        )
    }

    fun updatePageTemplate(pageId: String, template: PageTemplate) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("template", template.name)
            put("updated_at", System.currentTimeMillis())
        }
        db.update("pages", values, "id = ?", arrayOf(pageId))
    }

    fun deletePage(notebookId: String, pageId: String): Boolean {
        val currentPages = getPagesForNotebook(notebookId)
        if (currentPages.size <= 1) {
            // Cannot delete the only page in a notebook
            return false
        }
        val target = currentPages.find { it.id == pageId } ?: return false
        val file = File(target.strokeFilePath)
        if (file.exists()) file.delete()

        val db = writableDatabase
        db.delete("pages", "id = ?", arrayOf(pageId))

        // Renumber remaining pages sequentially
        val remaining = currentPages.filter { it.id != pageId }
        for (i in remaining.indices) {
            val p = remaining[i]
            val values = ContentValues().apply {
                put("page_number", i + 1)
            }
            db.update("pages", values, "id = ?", arrayOf(p.id))
        }

        val nbValues = ContentValues().apply {
            put("updated_at", System.currentTimeMillis())
        }
        db.update("notebooks", nbValues, "id = ?", arrayOf(notebookId))
        return true
    }

    fun duplicatePage(notebookId: String, pageId: String): NotebookPage? {
        val currentPages = getPagesForNotebook(notebookId)
        val source = currentPages.find { it.id == pageId } ?: return null
        val now = System.currentTimeMillis()
        val newPageId = UUID.randomUUID().toString()
        val newStrokeFile = getStrokeFile(newPageId)

        // Copy source stroke file to new stroke file
        val srcFile = File(source.strokeFilePath)
        if (srcFile.exists()) {
            srcFile.copyTo(newStrokeFile, overwrite = true)
        }

        val db = writableDatabase
        val insertPosition = source.pageNumber + 1

        // Shift subsequent pages up
        db.execSQL(
            "UPDATE pages SET page_number = page_number + 1 WHERE notebook_id = ? AND page_number >= ?",
            arrayOf(notebookId, insertPosition)
        )

        val values = ContentValues().apply {
            put("id", newPageId)
            put("notebook_id", notebookId)
            put("page_number", insertPosition)
            put("template", source.template.name)
            put("pdf_page_index", source.pdfPageIndex)
            put("stroke_file_path", newStrokeFile.absolutePath)
            put("updated_at", now)
        }
        db.insert("pages", null, values)

        db.execSQL("UPDATE notebooks SET updated_at = ? WHERE id = ?", arrayOf(now, notebookId))

        return NotebookPage(
            id = newPageId,
            notebookId = notebookId,
            pageNumber = insertPosition,
            template = source.template,
            pdfPageIndex = source.pdfPageIndex,
            strokeFilePath = newStrokeFile.absolutePath,
            updatedAt = now
        )
    }

    fun reorderPages(notebookId: String, orderedPageIds: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((index, id) in orderedPageIds.withIndex()) {
                val values = ContentValues().apply {
                    put("page_number", index + 1)
                }
                db.update("pages", values, "id = ?", arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
