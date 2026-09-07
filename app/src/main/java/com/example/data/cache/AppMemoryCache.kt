package com.example.data.cache

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import com.example.data.model.InkStroke

/**
 * Centralized, memory-bounded LRU Cache system for InkLite.
 * Provides caching for:
 * 1. PDF page bitmaps (high resolution display bitmaps)
 * 2. Rendered page thumbnail bitmaps (for drawer / grid browsing)
 * 3. Vector stroke data lists (parsed from binary storage)
 */
object AppMemoryCache {
    private const val TAG = "AppMemoryCache"

    // Dynamic memory budget: Allocate ~15% of available JVM heap to bitmap cache
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val bitmapCacheSizeKb = (maxMemoryKb / 7).coerceIn(16 * 1024, 64 * 1024) // 16MB to 64MB

    /**
     * Bitmap LRU Cache keyed by unique page identifier (e.g., "pdfFilePath_pageIndex")
     */
    private val bitmapCache = object : LruCache<String, Bitmap>(bitmapCacheSizeKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return (bitmap.allocationByteCount / 1024).coerceAtLeast(1)
        }

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && oldValue != newValue && !oldValue.isRecycled) {
                // Note: We avoid aggressive recycle() here if the bitmap is actively drawn,
                // but let the JVM GC manage evicted bitmap memory cleanly.
                Log.d(TAG, "Evicted bitmap from memory cache: $key")
            }
        }
    }

    /**
     * Thumbnail LRU Cache (max 60 thumbnails, ~4MB)
     */
    private val thumbnailCache = object : LruCache<String, Bitmap>(60) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return (bitmap.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    /**
     * Vector Stroke Data Cache (max 80 pages of strokes in memory)
     */
    private val strokeCache = object : LruCache<String, List<InkStroke>>(80) {}

    // Statistics tracking
    private var bitmapHits = 0
    private var bitmapMisses = 0

    // --- BITMAP CACHE OPERATIONS ---

    @Synchronized
    fun getBitmap(key: String): Bitmap? {
        val bmp = bitmapCache.get(key)
        if (bmp != null && !bmp.isRecycled) {
            bitmapHits++
            return bmp
        }
        bitmapMisses++
        if (bmp != null && bmp.isRecycled) {
            bitmapCache.remove(key)
        }
        return null
    }

    @Synchronized
    fun putBitmap(key: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            bitmapCache.put(key, bitmap)
        }
    }

    @Synchronized
    fun removeBitmap(key: String) {
        bitmapCache.remove(key)
    }

    // --- THUMBNAIL CACHE OPERATIONS ---

    @Synchronized
    fun getThumbnail(key: String): Bitmap? {
        val bmp = thumbnailCache.get(key)
        return if (bmp != null && !bmp.isRecycled) bmp else null
    }

    @Synchronized
    fun putThumbnail(key: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            thumbnailCache.put(key, bitmap)
        }
    }

    // --- STROKE CACHE OPERATIONS ---

    @Synchronized
    fun getStrokes(key: String): List<InkStroke>? {
        return strokeCache.get(key)
    }

    @Synchronized
    fun putStrokes(key: String, strokes: List<InkStroke>) {
        strokeCache.put(key, strokes)
    }

    @Synchronized
    fun removeStrokes(key: String) {
        strokeCache.remove(key)
    }

    // --- CACHE METRICS & TRIMMING ---

    /**
     * Memory cache diagnostic stats
     */
    data class CacheStats(
        val bitmapCacheSizeMb: Float,
        val bitmapMaxCacheSizeMb: Float,
        val bitmapEntryCount: Int,
        val strokeEntryCount: Int,
        val thumbnailEntryCount: Int,
        val hitCount: Int,
        val missCount: Int,
        val hitRatePercentage: Int
    ) {
        val bitmapCount: Int get() = bitmapEntryCount
        val strokeCount: Int get() = strokeEntryCount

        fun formatBitmapSize(): String = String.format(java.util.Locale.US, "%.1f MB", bitmapCacheSizeMb)
        fun formatMaxBitmapSize(): String = String.format(java.util.Locale.US, "%.1f MB", bitmapMaxCacheSizeMb)
    }

    @Synchronized
    fun getStats(): CacheStats {
        val sizeMb = bitmapCache.size() / 1024f
        val maxMb = bitmapCache.maxSize() / 1024f
        val totalRequests = bitmapHits + bitmapMisses
        val hitRate = if (totalRequests > 0) ((bitmapHits * 100) / totalRequests) else 100

        return CacheStats(
            bitmapCacheSizeMb = sizeMb,
            bitmapMaxCacheSizeMb = maxMb,
            bitmapEntryCount = bitmapCache.snapshot().size,
            strokeEntryCount = strokeCache.snapshot().size,
            thumbnailEntryCount = thumbnailCache.snapshot().size,
            hitCount = bitmapHits,
            missCount = bitmapMisses,
            hitRatePercentage = hitRate
        )
    }

    /**
     * Low memory handling: reduce cache by half or completely
     */
    @Synchronized
    fun trimMemory(level: Int) {
        Log.w(TAG, "trimMemory requested at level: $level")
        bitmapCache.trimToSize(bitmapCache.maxSize() / 2)
        thumbnailCache.trimToSize(thumbnailCache.maxSize() / 2)
        strokeCache.trimToSize(strokeCache.maxSize() / 2)
    }

    /**
     * Full cache flush
     */
    @Synchronized
    fun clearAll() {
        bitmapCache.evictAll()
        thumbnailCache.evictAll()
        strokeCache.evictAll()
        bitmapHits = 0
        bitmapMisses = 0
        Log.d(TAG, "All cache memory cleared")
    }
}
