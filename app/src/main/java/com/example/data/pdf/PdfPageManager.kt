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
import com.example.data.cache.AppMemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Manages PDF import, capped-resolution rasterization, on-disk caching,
 * and multi-page LRU in-memory cache lifecycle with prefetching.
 */
object PdfPageManager {
    private const val TAG = "PdfPageManager"
    private const val MAX_LONG_EDGE = 1600

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
     * Checks fast memory LRU cache first, then disk cache, then on-demand rasterization.
     */
    @Synchronized
    fun getPageBitmap(context: Context, pdfFilePath: String, pageIndex: Int): Bitmap? {
        if (pdfFilePath.isBlank() || pageIndex < 0) return null
        val cacheKey = "${pdfFilePath.hashCode()}_$pageIndex"

        // 1. Fast Memory LRU Cache Hit (0ms)
        val memBitmap = AppMemoryCache.getBitmap(cacheKey)
        if (memBitmap != null && !memBitmap.isRecycled) {
            return memBitmap
        }

        val cacheDir = File(context.cacheDir, "pdf_pages")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val diskCacheFile = File(cacheDir, "cache_$cacheKey.jpg")

        // 2. Try disk cache if available
        if (diskCacheFile.exists() && diskCacheFile.length() > 0) {
            try {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bmp = BitmapFactory.decodeFile(diskCacheFile.absolutePath, opts)
                if (bmp != null) {
                    AppMemoryCache.putBitmap(cacheKey, bmp)
                    return bmp
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed loading cached page from disk", e)
            }
        }

        // 3. Rasterize on demand if not in cache
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
                        bmp.eraseColor(Color.WHITE)

                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        // Save compressed JPEG to disk cache
                        try {
                            FileOutputStream(diskCacheFile).use { out ->
                                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to write disk cache for page $pageIndex", e)
                        }

                        // Store in AppMemoryCache
                        AppMemoryCache.putBitmap(cacheKey, bmp)
                        return bmp
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error rendering PDF page $pageIndex", e)
            return null
        }
    }

    /**
     * Asynchronously prefetch adjacent pages into LRU cache memory for seamless flipping.
     */
    suspend fun prefetchAdjacentPages(
        context: Context,
        pdfFilePath: String,
        currentPageIndex: Int,
        totalPages: Int
    ) = withContext(Dispatchers.IO) {
        val targets = listOf(currentPageIndex - 1, currentPageIndex + 1)
            .filter { it in 0 until totalPages }
        for (idx in targets) {
            val key = "${pdfFilePath.hashCode()}_$idx"
            if (AppMemoryCache.getBitmap(key) == null) {
                getPageBitmap(context, pdfFilePath, idx)
            }
        }
    }

    @Synchronized
    fun clearMemoryCache() {
        AppMemoryCache.clearAll()
    }
}
