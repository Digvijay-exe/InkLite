package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Manages PDF import, capped-resolution rasterization, on-disk caching,
 * and low-memory single-page bitmap lifecycle.
 */
object PdfPageManager {
    private const val TAG = "PdfPageManager"
    private const val MAX_LONG_EDGE = 1600

    // Single active page bitmap cache to stay within 150MB heap budget
    private var activePageKey: String? = null
    private var activePageBitmap: Bitmap? = null

    data class ImportResult(
        val pdfFilePath: String,
        val pageCount: Int,
        val title: String
    )

    fun importPdf(context: Context, uri: Uri, suggestedTitle: String): ImportResult {
        val pdfDir = File(context.filesDir, "pdfs")
        if (!pdfDir.exists()) pdfDir.mkdirs()

        val pdfId = UUID.randomUUID().toString()
        val destFile = File(pdfDir, "pdf_$pdfId.pdf")

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Failed to open PDF stream from URI" }
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }

        var pageCount = 1
        ParcelFileDescriptor.open(destFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                pageCount = renderer.pageCount
            }
        }

        return ImportResult(
            pdfFilePath = destFile.absolutePath,
            pageCount = pageCount,
            title = suggestedTitle
        )
    }

    /**
     * Loads a rendered bitmap of the given PDF page.
     * Uses disk cache if available. Keeps only 1 loaded page in memory.
     */
    @Synchronized
    fun getPageBitmap(context: Context, pdfFilePath: String, pageIndex: Int): Bitmap? {
        if (pdfFilePath.isBlank() || pageIndex < 0) return null
        val cacheKey = "${pdfFilePath.hashCode()}_$pageIndex"

        if (activePageKey == cacheKey && activePageBitmap != null && !activePageBitmap!!.isRecycled) {
            return activePageBitmap
        }

        val cacheDir = File(context.cacheDir, "pdf_pages")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val diskCacheFile = File(cacheDir, "cache_$cacheKey.jpg")

        // 1. Try disk cache first
        if (diskCacheFile.exists() && diskCacheFile.length() > 0) {
            try {
                val opts = BitmapFactory.Options().apply {
                    // RGB_565 uses 2 bytes/pixel (half of ARGB_8888) to protect 1GB RAM budget
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bmp = BitmapFactory.decodeFile(diskCacheFile.absolutePath, opts)
                if (bmp != null) {
                    swapActiveBitmap(cacheKey, bmp)
                    return bmp
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed loading cached page from disk", e)
            }
        }

        // 2. Rasterize on demand if not in cache
        val pdfFile = File(pdfFilePath)
        if (!pdfFile.exists()) return null

        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex >= renderer.pageCount) return null
                    renderer.openPage(pageIndex).use { page ->
                        val srcW = page.width
                        val srcH = page.height

                        // Calculate scale to cap long edge at 1600px
                        val maxSrc = maxOf(srcW, srcH)
                        val scale = if (maxSrc > MAX_LONG_EDGE) {
                            MAX_LONG_EDGE.toFloat() / maxSrc.toFloat()
                        } else {
                            1.0f
                        }

                        val targetW = (srcW * scale).toInt().coerceAtLeast(100)
                        val targetH = (srcH * scale).toInt().coerceAtLeast(100)

                        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        // Fill white background before rendering PDF page content
                        bmp.eraseColor(Color.WHITE)

                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        // Cache to disk as compressed JPEG
                        try {
                            FileOutputStream(diskCacheFile).use { out ->
                                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to write disk cache for page $pageIndex", e)
                        }

                        swapActiveBitmap(cacheKey, bmp)
                        return bmp
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error rendering PDF page $pageIndex", e)
            return null
        }
    }

    @Synchronized
    private fun swapActiveBitmap(newKey: String, newBmp: Bitmap) {
        if (activePageBitmap != null && activePageBitmap != newBmp && !activePageBitmap!!.isRecycled) {
            try {
                activePageBitmap!!.recycle()
            } catch (e: Exception) {
                // Ignore
            }
        }
        activePageKey = newKey
        activePageBitmap = newBmp
    }

    @Synchronized
    fun clearMemoryCache() {
        if (activePageBitmap != null && !activePageBitmap!!.isRecycled) {
            try {
                activePageBitmap!!.recycle()
            } catch (e: Exception) {
                // Ignore
            }
        }
        activePageBitmap = null
        activePageKey = null
    }
}
