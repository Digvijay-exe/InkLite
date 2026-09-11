package com.example.data.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.InkPoint
import com.example.data.model.InkStroke
import com.example.data.model.Notebook
import com.example.data.model.NotebookPage
import com.example.data.model.PageTemplate
import com.example.data.pdf.PdfPageManager
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ExportManager {

    const val PAGE_WIDTH = 1200f
    const val PAGE_HEIGHT = 1600f

    fun renderPageToCanvas(
        canvas: Canvas,
        width: Float,
        height: Float,
        template: PageTemplate,
        pdfBitmap: Bitmap?,
        strokes: List<InkStroke>,
        transparentBackground: Boolean = false
    ) {
        if (!transparentBackground) {
            // 1. Draw page background
            val bgPaint = Paint().apply {
                color = if (pdfBitmap != null && !pdfBitmap.isRecycled) Color.WHITE else 0xFF1D1B20.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width, height, bgPaint)

            // 2. Draw PDF background if available
            if (pdfBitmap != null && !pdfBitmap.isRecycled) {
                val bmpW = pdfBitmap.width.toFloat()
                val bmpH = pdfBitmap.height.toFloat()
                val scale = minOf(width / bmpW, height / bmpH)
                val fitW = bmpW * scale
                val fitH = bmpH * scale
                val left = (width - fitW) / 2f
                val top = (height - fitH) / 2f
                val destRect = RectF(left, top, left + fitW, top + fitH)
                val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                canvas.drawBitmap(pdfBitmap, null, destRect, p)
            } else {
                // 3. Draw procedural template (vector lines / grid / dots)
                drawProceduralTemplate(canvas, width, height, template)
            }
        }

        // 4. Draw vector strokes
        drawStrokes(canvas, strokes)
    }

    private fun drawProceduralTemplate(
        canvas: Canvas,
        width: Float,
        height: Float,
        template: PageTemplate
    ) {
        when (template) {
            PageTemplate.BLANK -> {
                // Nothing to draw
            }
            PageTemplate.LINED -> {
                val linePaint = Paint().apply {
                    color = 0xFF2B2930.toInt() // Sophisticated dark subtle rule
                    strokeWidth = 2f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                val marginPaint = Paint().apply {
                    color = 0x66FF897D.toInt() // Sophisticated dark coral margin line
                    strokeWidth = 2.5f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }

                val lineSpacing = 48f
                val startY = 120f
                var y = startY
                while (y < height - 60f) {
                    canvas.drawLine(0f, y, width, y, linePaint)
                    y += lineSpacing
                }

                // Vertical margin line at 120px from left
                val marginX = 140f
                canvas.drawLine(marginX, 0f, marginX, height, marginPaint)
            }
            PageTemplate.GRID -> {
                val gridPaint = Paint().apply {
                    color = 0xFF2B2930.toInt() // Sophisticated dark subtle grid
                    strokeWidth = 1.5f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                val gridSize = 40f
                var x = gridSize
                while (x < width) {
                    canvas.drawLine(x, 0f, x, height, gridPaint)
                    x += gridSize
                }
                var y = gridSize
                while (y < height) {
                    canvas.drawLine(0f, y, width, y, gridPaint)
                    y += gridSize
                }
            }
            PageTemplate.DOT_GRID -> {
                val dotPaint = Paint().apply {
                    color = 0xFF49454F.toInt() // Sophisticated dark dot grid
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                val dotSpacing = 40f
                val dotRadius = 2.5f
                var x = dotSpacing
                while (x < width) {
                    var y = dotSpacing
                    while (y < height) {
                        canvas.drawCircle(x, y, dotRadius, dotPaint)
                        y += dotSpacing
                    }
                    x += dotSpacing
                }
            }
        }
    }

    private fun drawStrokes(canvas: Canvas, strokes: List<InkStroke>) {
        val strokePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        for (stroke in strokes) {
            val points = stroke.points
            if (points.isEmpty()) continue

            strokePaint.color = stroke.color
            strokePaint.strokeWidth = stroke.strokeWidth

            if (stroke.isHighlighter) {
                // Highlighter blending: semi-transparent alpha
                val c = stroke.color
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                strokePaint.color = Color.argb(100, r, g, b)
                strokePaint.strokeWidth = (stroke.strokeWidth * 2.5f).coerceAtLeast(18f)
            }

            if (points.size == 1) {
                val p = points[0]
                strokePaint.style = Paint.Style.FILL
                canvas.drawCircle(p.x, p.y, stroke.strokeWidth / 2f, strokePaint)
                strokePaint.style = Paint.Style.STROKE
                continue
            }

            val path = Path()
            path.moveTo(points[0].x, points[0].y)

            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2f
                val midY = (prev.y + curr.y) / 2f
                path.quadTo(prev.x, prev.y, midX, midY)
            }
            val last = points.last()
            path.lineTo(last.x, last.y)

            canvas.drawPath(path, strokePaint)
        }
    }

    private fun getExportsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun exportPageAsImage(
        context: Context,
        notebookTitle: String,
        page: NotebookPage,
        strokes: List<InkStroke>,
        isPng: Boolean = true
    ): Uri {
        return exportPageAsImage(
            context = context,
            notebookTitle = notebookTitle,
            page = page,
            pdfFilePath = null,
            strokes = strokes,
            isPng = isPng,
            transparentBackground = false
        )
    }

    fun exportPageAsImage(
        context: Context,
        notebookTitle: String,
        page: NotebookPage,
        pdfFilePath: String?,
        strokes: List<InkStroke>,
        isPng: Boolean = true,
        transparentBackground: Boolean = false
    ): Uri {
        val bmp = Bitmap.createBitmap(
            PAGE_WIDTH.toInt(),
            PAGE_HEIGHT.toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)

        val pdfBmp = if (page.pdfPageIndex >= 0 && !pdfFilePath.isNullOrBlank()) {
            PdfPageManager.getPageBitmap(context, pdfFilePath, page.pdfPageIndex)
        } else null

        renderPageToCanvas(
            canvas = canvas,
            width = PAGE_WIDTH,
            height = PAGE_HEIGHT,
            template = page.template,
            pdfBitmap = pdfBmp,
            strokes = strokes,
            transparentBackground = transparentBackground
        )

        val ext = if (isPng) "png" else "jpg"
        val cleanTitle = notebookTitle.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val file = File(getExportsDir(context), "${cleanTitle}_page_${page.pageNumber}_${System.currentTimeMillis()}.$ext")

        FileOutputStream(file).use { out ->
            if (isPng) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            } else {
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
        }
        bmp.recycle()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun exportPageAsPdf(
        context: Context,
        notebookTitle: String,
        page: NotebookPage,
        pdfFilePath: String?,
        strokes: List<InkStroke>
    ): Uri {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(
            PAGE_WIDTH.toInt(),
            PAGE_HEIGHT.toInt(),
            page.pageNumber
        ).create()
        val pdfPage = doc.startPage(pageInfo)

        val pdfBmp = if (page.pdfPageIndex >= 0 && !pdfFilePath.isNullOrBlank()) {
            PdfPageManager.getPageBitmap(context, pdfFilePath, page.pdfPageIndex)
        } else null

        renderPageToCanvas(pdfPage.canvas, PAGE_WIDTH, PAGE_HEIGHT, page.template, pdfBmp, strokes)
        doc.finishPage(pdfPage)

        val cleanTitle = notebookTitle.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val file = File(getExportsDir(context), "${cleanTitle}_page_${page.pageNumber}_${System.currentTimeMillis()}.pdf")

        FileOutputStream(file).use { out ->
            doc.writeTo(out)
        }
        doc.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun exportNotebookAsPdf(
        context: Context,
        notebook: Notebook,
        pages: List<NotebookPage>,
        strokeLoader: (NotebookPage) -> List<InkStroke>
    ): Uri {
        val doc = PdfDocument()

        for (page in pages) {
            val pageInfo = PdfDocument.PageInfo.Builder(
                PAGE_WIDTH.toInt(),
                PAGE_HEIGHT.toInt(),
                page.pageNumber
            ).create()
            val pdfPage = doc.startPage(pageInfo)

            val pdfBmp = if (page.pdfPageIndex >= 0 && !notebook.pdfFilePath.isNullOrBlank()) {
                PdfPageManager.getPageBitmap(context, notebook.pdfFilePath, page.pdfPageIndex)
            } else null

            val strokes = strokeLoader(page)
            renderPageToCanvas(pdfPage.canvas, PAGE_WIDTH, PAGE_HEIGHT, page.template, pdfBmp, strokes)
            doc.finishPage(pdfPage)
        }

        val cleanTitle = notebook.title.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val file = File(getExportsDir(context), "${cleanTitle}_full_${System.currentTimeMillis()}.pdf")

        FileOutputStream(file).use { out ->
            doc.writeTo(out)
        }
        doc.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun writeNotebookPdfToStream(
        context: Context,
        notebook: Notebook,
        pages: List<NotebookPage>,
        strokeLoader: (NotebookPage) -> List<InkStroke>,
        outputStream: OutputStream
    ) {
        val doc = PdfDocument()
        for (page in pages) {
            val pageInfo = PdfDocument.PageInfo.Builder(
                PAGE_WIDTH.toInt(),
                PAGE_HEIGHT.toInt(),
                page.pageNumber
            ).create()
            val pdfPage = doc.startPage(pageInfo)

            val pdfBmp = if (page.pdfPageIndex >= 0 && !notebook.pdfFilePath.isNullOrBlank()) {
                PdfPageManager.getPageBitmap(context, notebook.pdfFilePath, page.pdfPageIndex)
            } else null

            val strokes = strokeLoader(page)
            renderPageToCanvas(pdfPage.canvas, PAGE_WIDTH, PAGE_HEIGHT, page.template, pdfBmp, strokes)
            doc.finishPage(pdfPage)
        }
        doc.writeTo(outputStream)
        doc.close()
    }

    fun writePagePdfToStream(
        context: Context,
        notebookTitle: String,
        page: NotebookPage,
        pdfFilePath: String?,
        strokes: List<InkStroke>,
        outputStream: OutputStream
    ) {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(
            PAGE_WIDTH.toInt(),
            PAGE_HEIGHT.toInt(),
            page.pageNumber
        ).create()
        val pdfPage = doc.startPage(pageInfo)

        val pdfBmp = if (page.pdfPageIndex >= 0 && !pdfFilePath.isNullOrBlank()) {
            PdfPageManager.getPageBitmap(context, pdfFilePath, page.pdfPageIndex)
        } else null

        renderPageToCanvas(pdfPage.canvas, PAGE_WIDTH, PAGE_HEIGHT, page.template, pdfBmp, strokes)
        doc.finishPage(pdfPage)
        doc.writeTo(outputStream)
        doc.close()
    }

    fun saveNotebookPdfToDownloads(
        context: Context,
        notebook: Notebook,
        pages: List<NotebookPage>,
        strokeLoader: (NotebookPage) -> List<InkStroke>
    ): String {
        val cleanTitle = notebook.title.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val fileName = "InkLite_${cleanTitle}_${System.currentTimeMillis()}.pdf"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("Failed to create MediaStore download record")
            resolver.openOutputStream(uri)?.use { out ->
                writeNotebookPdfToStream(context, notebook, pages, strokeLoader, out)
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val destFile = File(downloadsDir, fileName)
            FileOutputStream(destFile).use { out ->
                writeNotebookPdfToStream(context, notebook, pages, strokeLoader, out)
            }
        }
        return fileName
    }

    fun savePagePdfToDownloads(
        context: Context,
        notebookTitle: String,
        page: NotebookPage,
        pdfFilePath: String?,
        strokes: List<InkStroke>
    ): String {
        val cleanTitle = notebookTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val fileName = "InkLite_${cleanTitle}_page_${page.pageNumber}_${System.currentTimeMillis()}.pdf"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("Failed to create MediaStore download record")
            resolver.openOutputStream(uri)?.use { out ->
                writePagePdfToStream(context, notebookTitle, page, pdfFilePath, strokes, out)
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val destFile = File(downloadsDir, fileName)
            FileOutputStream(destFile).use { out ->
                writePagePdfToStream(context, notebookTitle, page, pdfFilePath, strokes, out)
            }
        }
        return fileName
    }

    fun createShareIntent(context: Context, uri: Uri, mimeType: String, title: String): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = android.content.ClipData.newUri(context.contentResolver, title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun shareFile(context: Context, uri: Uri, mimeType: String, title: String) {
        val chooser = createShareIntent(context, uri, mimeType, title)
        context.startActivity(chooser)
    }
}
