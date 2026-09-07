package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cache.AppMemoryCache
import com.example.data.db.InkLiteDatabaseHelper
import com.example.data.export.ExportManager
import com.example.data.io.StrokeBinarySerializer
import com.example.data.model.InkStroke
import com.example.data.model.Notebook
import com.example.data.model.NotebookPage
import com.example.data.model.PageTemplate
import com.example.data.pdf.PdfPageManager
import com.example.ui.canvas.EraserMode
import com.example.ui.canvas.InkTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class InkLiteUiState(
    val notebooks: List<Notebook> = emptyList(),
    val currentNotebook: Notebook? = null,
    val currentPages: List<NotebookPage> = emptyList(),
    val currentPageIndex: Int = 0,
    val currentPageStrokes: List<InkStroke> = emptyList(),
    val currentPdfBitmap: Bitmap? = null,
    val selectedTool: InkTool = InkTool.PEN,
    val selectedColor: Int = 0xFFD0BCFF.toInt(),
    val selectedStrokeWidth: Float = 5f,
    val selectedEraserMode: EraserMode = EraserMode.STROKE,
    val selectedEraserRadius: Float = 32f,
    val cacheStats: AppMemoryCache.CacheStats? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val exportShareUri: Uri? = null,
    val exportShareMime: String? = null
)

class InkLiteViewModel(application: Application) : AndroidViewModel(application) {

    private val db = InkLiteDatabaseHelper.getInstance(application)

    private val _uiState = MutableStateFlow(InkLiteUiState())
    val uiState: StateFlow<InkLiteUiState> = _uiState.asStateFlow()

    init {
        loadNotebooks()
        refreshCacheStats()
    }

    private fun loadPageStrokesCached(page: NotebookPage): List<InkStroke> {
        val cached = AppMemoryCache.getStrokes(page.id)
        if (cached != null) return cached
        val loaded = StrokeBinarySerializer.loadStrokes(File(page.strokeFilePath))
        AppMemoryCache.putStrokes(page.id, loaded)
        return loaded
    }

    fun loadNotebooks() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = db.getAllNotebooks()
            _uiState.update { it.copy(notebooks = list) }
        }
    }

    fun createNotebook(
        title: String,
        coverColor: Int,
        template: PageTemplate
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val cleanTitle = if (title.isBlank()) "Untitled Notebook" else title.trim()
            val nb = db.createNotebook(cleanTitle, coverColor, template, isPdf = false, initialPages = 1)
            val all = db.getAllNotebooks()
            _uiState.update { it.copy(notebooks = all, isLoading = false) }
            openNotebook(nb.id)
        }
    }

    fun importPdfNotebook(uri: Uri, suggestedTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Importing PDF pages...") }
            try {
                val cleanTitle = if (suggestedTitle.isBlank()) "Imported PDF" else suggestedTitle.trim()
                val importResult = PdfPageManager.importPdf(getApplication(), uri, cleanTitle)
                val nb = db.createNotebook(
                    title = cleanTitle,
                    coverColor = 0xFFDC2626.toInt(), // PDF Red accent
                    template = PageTemplate.BLANK,
                    isPdf = true,
                    pdfFilePath = importResult.pdfFilePath,
                    initialPages = importResult.pageCount
                )
                val all = db.getAllNotebooks()
                _uiState.update { it.copy(notebooks = all, isLoading = false, statusMessage = null) }
                openNotebook(nb.id)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(isLoading = false, statusMessage = "Failed to import PDF: ${e.localizedMessage}")
                }
            }
        }
    }

    fun openNotebook(notebookId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val notebook = db.getNotebook(notebookId)
            val pages = db.getPagesForNotebook(notebookId)
            if (notebook != null && pages.isNotEmpty()) {
                val initialPage = pages[0]
                val strokes = loadPageStrokesCached(initialPage)
                val pdfBmp = if (initialPage.pdfPageIndex >= 0 && !notebook.pdfFilePath.isNullOrBlank()) {
                    PdfPageManager.getPageBitmap(getApplication(), notebook.pdfFilePath, initialPage.pdfPageIndex)
                } else null

                // Prefetch adjacent page in cache memory if available
                if (!notebook.pdfFilePath.isNullOrBlank() && pages.size > 1) {
                    PdfPageManager.prefetchAdjacentPages(getApplication(), notebook.pdfFilePath, 0, pages.size)
                }

                _uiState.update {
                    it.copy(
                        currentNotebook = notebook,
                        currentPages = pages,
                        currentPageIndex = 0,
                        currentPageStrokes = strokes,
                        currentPdfBitmap = pdfBmp,
                        cacheStats = AppMemoryCache.getStats(),
                        isLoading = false,
                        canUndo = false,
                        canRedo = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun closeNotebook(currentCanvasStrokes: List<InkStroke>? = null) {
        if (currentCanvasStrokes != null) {
            saveCurrentPageStrokes(currentCanvasStrokes)
        }
        PdfPageManager.clearMemoryCache()
        _uiState.update {
            it.copy(
                currentNotebook = null,
                currentPages = emptyList(),
                currentPageIndex = 0,
                currentPageStrokes = emptyList(),
                currentPdfBitmap = null
            )
        }
        loadNotebooks()
    }

    fun renameNotebook(notebookId: String, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (newTitle.isNotBlank()) {
                db.updateNotebookTitle(notebookId, newTitle.trim())
                loadNotebooks()
                if (_uiState.value.currentNotebook?.id == notebookId) {
                    val updated = db.getNotebook(notebookId)
                    _uiState.update { it.copy(currentNotebook = updated) }
                }
            }
        }
    }

    fun deleteNotebook(notebookId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteNotebook(notebookId)
            if (_uiState.value.currentNotebook?.id == notebookId) {
                closeNotebook()
            }
            loadNotebooks()
        }
    }

    fun saveCurrentPageStrokes(strokes: List<InkStroke>) {
        val state = _uiState.value
        val pages = state.currentPages
        val idx = state.currentPageIndex
        if (idx in pages.indices) {
            val page = pages[idx]
            // Fast in-memory cache update
            AppMemoryCache.putStrokes(page.id, strokes)
            viewModelScope.launch(Dispatchers.IO) {
                StrokeBinarySerializer.saveStrokes(File(page.strokeFilePath), strokes)
            }
        }
    }

    fun selectPage(newIndex: Int, currentCanvasStrokes: List<InkStroke>) {
        val state = _uiState.value
        val pages = state.currentPages
        if (newIndex in pages.indices && newIndex != state.currentPageIndex) {
            // Save current page
            saveCurrentPageStrokes(currentCanvasStrokes)

            viewModelScope.launch(Dispatchers.IO) {
                val targetPage = pages[newIndex]
                val strokes = loadPageStrokesCached(targetPage)
                val pdfBmp = if (targetPage.pdfPageIndex >= 0 && !state.currentNotebook?.pdfFilePath.isNullOrBlank()) {
                    PdfPageManager.getPageBitmap(getApplication(), state.currentNotebook!!.pdfFilePath!!, targetPage.pdfPageIndex)
                } else null

                // Prefetch adjacent pages into memory cache for instant future transitions
                if (!state.currentNotebook?.pdfFilePath.isNullOrBlank()) {
                    PdfPageManager.prefetchAdjacentPages(
                        getApplication(),
                        state.currentNotebook!!.pdfFilePath!!,
                        newIndex,
                        pages.size
                    )
                }

                _uiState.update {
                    it.copy(
                        currentPageIndex = newIndex,
                        currentPageStrokes = strokes,
                        currentPdfBitmap = pdfBmp,
                        cacheStats = AppMemoryCache.getStats(),
                        canUndo = false,
                        canRedo = false
                    )
                }
            }
        }
    }

    fun addNewPage(template: PageTemplate, currentCanvasStrokes: List<InkStroke>) {
        val state = _uiState.value
        val nb = state.currentNotebook ?: return
        saveCurrentPageStrokes(currentCanvasStrokes)

        viewModelScope.launch(Dispatchers.IO) {
            val newPage = db.addPage(nb.id, template)
            val updatedPages = db.getPagesForNotebook(nb.id)
            val newIndex = updatedPages.indexOfFirst { it.id == newPage.id }
            _uiState.update {
                it.copy(
                    currentPages = updatedPages,
                    currentPageIndex = if (newIndex >= 0) newIndex else updatedPages.size - 1,
                    currentPageStrokes = emptyList(),
                    currentPdfBitmap = null,
                    canUndo = false,
                    canRedo = false
                )
            }
        }
    }

    fun duplicateCurrentPage(currentCanvasStrokes: List<InkStroke>) {
        val state = _uiState.value
        val nb = state.currentNotebook ?: return
        val pages = state.currentPages
        val idx = state.currentPageIndex
        if (idx !in pages.indices) return

        saveCurrentPageStrokes(currentCanvasStrokes)

        viewModelScope.launch(Dispatchers.IO) {
            val currentPage = pages[idx]
            val dup = db.duplicatePage(nb.id, currentPage.id)
            val updatedPages = db.getPagesForNotebook(nb.id)
            val newIdx = updatedPages.indexOfFirst { it.id == dup?.id }
            val dupStrokes = if (dup != null) StrokeBinarySerializer.loadStrokes(File(dup.strokeFilePath)) else emptyList()
            val pdfBmp = if (dup != null && dup.pdfPageIndex >= 0 && !nb.pdfFilePath.isNullOrBlank()) {
                PdfPageManager.getPageBitmap(getApplication(), nb.pdfFilePath, dup.pdfPageIndex)
            } else null

            _uiState.update {
                it.copy(
                    currentPages = updatedPages,
                    currentPageIndex = if (newIdx >= 0) newIdx else idx + 1,
                    currentPageStrokes = dupStrokes,
                    currentPdfBitmap = pdfBmp,
                    canUndo = false,
                    canRedo = false
                )
            }
        }
    }

    fun deleteCurrentPage() {
        val state = _uiState.value
        val nb = state.currentNotebook ?: return
        val pages = state.currentPages
        val idx = state.currentPageIndex
        if (pages.size <= 1) {
            _uiState.update { it.copy(statusMessage = "A notebook must have at least one page.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val targetPage = pages[idx]
            db.deletePage(nb.id, targetPage.id)
            val updatedPages = db.getPagesForNotebook(nb.id)
            val nextIdx = idx.coerceAtMost(updatedPages.size - 1)
            val nextLoadedStrokes = StrokeBinarySerializer.loadStrokes(File(updatedPages[nextIdx].strokeFilePath))
            val pdfBmp = if (updatedPages[nextIdx].pdfPageIndex >= 0 && !nb.pdfFilePath.isNullOrBlank()) {
                PdfPageManager.getPageBitmap(getApplication(), nb.pdfFilePath, updatedPages[nextIdx].pdfPageIndex)
            } else null

            _uiState.update {
                it.copy(
                    currentPages = updatedPages,
                    currentPageIndex = nextIdx,
                    currentPageStrokes = nextLoadedStrokes,
                    currentPdfBitmap = pdfBmp,
                    canUndo = false,
                    canRedo = false
                )
            }
        }
    }

    fun updateCurrentPageTemplate(template: PageTemplate) {
        val state = _uiState.value
        val pages = state.currentPages
        val idx = state.currentPageIndex
        if (idx in pages.indices) {
            val page = pages[idx]
            viewModelScope.launch(Dispatchers.IO) {
                db.updatePageTemplate(page.id, template)
                val updatedPages = pages.toMutableList().also {
                    it[idx] = page.copy(template = template)
                }
                _uiState.update { it.copy(currentPages = updatedPages) }
            }
        }
    }

    fun setTool(tool: InkTool) {
        _uiState.update { it.copy(selectedTool = tool) }
    }

    fun setEraserMode(mode: EraserMode) {
        _uiState.update { it.copy(selectedEraserMode = mode) }
    }

    fun setEraserRadius(radius: Float) {
        _uiState.update { it.copy(selectedEraserRadius = radius) }
    }

    fun refreshCacheStats() {
        val stats = AppMemoryCache.getStats()
        _uiState.update { it.copy(cacheStats = stats) }
    }

    fun clearCacheMemory() {
        AppMemoryCache.clearAll()
        val stats = AppMemoryCache.getStats()
        _uiState.update {
            it.copy(
                cacheStats = stats,
                statusMessage = "Cache memory cleared successfully"
            )
        }
    }

    fun setColor(color: Int) {
        _uiState.update { it.copy(selectedColor = color) }
    }

    fun setStrokeWidth(width: Float) {
        _uiState.update { it.copy(selectedStrokeWidth = width) }
    }

    fun updateUndoRedoState(canUndo: Boolean, canRedo: Boolean) {
        _uiState.update { it.copy(canUndo = canUndo, canRedo = canRedo) }
    }

    fun exportCurrentPageAsImage(
        currentStrokes: List<InkStroke>,
        isPng: Boolean,
        transparentBackground: Boolean = false
    ) {
        val state = _uiState.value
        val nb = state.currentNotebook ?: return
        val pages = state.currentPages
        val idx = state.currentPageIndex
        if (idx !in pages.indices) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Flattening vector strokes to ${if (isPng) "PNG" else "JPEG"}...") }
            try {
                val page = pages[idx]
                val uri = ExportManager.exportPageAsImage(
                    context = getApplication(),
                    notebookTitle = nb.title,
                    page = page,
                    pdfFilePath = nb.pdfFilePath,
                    strokes = currentStrokes,
                    isPng = isPng,
                    transparentBackground = transparentBackground
                )
                val mime = if (isPng) "image/png" else "image/jpeg"
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        exportShareUri = uri,
                        exportShareMime = mime
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, statusMessage = "Export failed: ${e.localizedMessage}") }
            }
        }
    }

    fun exportCurrentPageAsPdf(currentStrokes: List<InkStroke>) {
        val state = _uiState.value
        val nb = state.currentNotebook ?: return
        val pages = state.currentPages
        val idx = state.currentPageIndex
        if (idx !in pages.indices) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Flattening vector strokes to PDF...") }
            try {
                val page = pages[idx]
                val uri = ExportManager.exportPageAsPdf(
                    context = getApplication(),
                    notebookTitle = nb.title,
                    page = page,
                    pdfFilePath = nb.pdfFilePath,
                    strokes = currentStrokes
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        exportShareUri = uri,
                        exportShareMime = "application/pdf"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, statusMessage = "PDF export failed: ${e.localizedMessage}") }
            }
        }
    }

    fun exportNotebookAsPdf(currentStrokes: List<InkStroke>) {
        val state = _uiState.value
        val nb = state.currentNotebook ?: return
        val pages = state.currentPages
        if (pages.isEmpty()) return

        saveCurrentPageStrokes(currentStrokes)

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Flattening all pages to PDF...") }
            try {
                val uri = ExportManager.exportNotebookAsPdf(
                    context = getApplication(),
                    notebook = nb,
                    pages = pages,
                    strokeLoader = { p ->
                        if (p.id == pages[state.currentPageIndex].id) {
                            currentStrokes
                        } else {
                            loadPageStrokesCached(p)
                        }
                    }
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        exportShareUri = uri,
                        exportShareMime = "application/pdf"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, statusMessage = "Full PDF export failed: ${e.localizedMessage}") }
            }
        }
    }

    fun saveCurrentNotebookPdfToDownloads(currentStrokes: List<InkStroke>) {
        val state = _uiState.value
        val nb = state.currentNotebook ?: return
        val pages = state.currentPages
        if (pages.isEmpty()) return

        saveCurrentPageStrokes(currentStrokes)

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Saving PDF to Downloads...") }
            try {
                val fileName = ExportManager.saveNotebookPdfToDownloads(
                    context = getApplication(),
                    notebook = nb,
                    pages = pages,
                    strokeLoader = { p ->
                        if (p.id == pages[state.currentPageIndex].id) {
                            currentStrokes
                        } else {
                            StrokeBinarySerializer.loadStrokes(File(p.strokeFilePath))
                        }
                    }
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "PDF saved to Downloads: $fileName"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, statusMessage = "Failed to save PDF: ${e.localizedMessage}") }
            }
        }
    }

    fun saveCurrentPagePdfToDownloads(currentStrokes: List<InkStroke>) {
        val state = _uiState.value
        val nb = state.currentNotebook ?: return
        val pages = state.currentPages
        val idx = state.currentPageIndex
        if (idx !in pages.indices) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Saving page to Downloads...") }
            try {
                val page = pages[idx]
                val fileName = ExportManager.savePagePdfToDownloads(
                    context = getApplication(),
                    notebookTitle = nb.title,
                    page = page,
                    pdfFilePath = nb.pdfFilePath,
                    strokes = currentStrokes
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Page saved to Downloads: $fileName"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, statusMessage = "Failed to save page: ${e.localizedMessage}") }
            }
        }
    }

    fun writeCurrentNotebookPdfToUri(uri: Uri, currentStrokes: List<InkStroke>) {
        val state = _uiState.value
        val nb = state.currentNotebook ?: return
        val pages = state.currentPages
        if (pages.isEmpty()) return

        saveCurrentPageStrokes(currentStrokes)

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Writing PDF file...") }
            try {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    ExportManager.writeNotebookPdfToStream(
                        context = getApplication(),
                        notebook = nb,
                        pages = pages,
                        strokeLoader = { p ->
                            if (p.id == pages[state.currentPageIndex].id) {
                                currentStrokes
                            } else {
                                StrokeBinarySerializer.loadStrokes(File(p.strokeFilePath))
                            }
                        },
                        outputStream = out
                    )
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "PDF file successfully saved!"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, statusMessage = "Failed to write PDF: ${e.localizedMessage}") }
            }
        }
    }

    fun writeCurrentPagePdfToUri(uri: Uri, currentStrokes: List<InkStroke>) {
        val state = _uiState.value
        val nb = state.currentNotebook ?: return
        val pages = state.currentPages
        val idx = state.currentPageIndex
        if (idx !in pages.indices) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Writing page PDF...") }
            try {
                val page = pages[idx]
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    ExportManager.writePagePdfToStream(
                        context = getApplication(),
                        notebookTitle = nb.title,
                        page = page,
                        pdfFilePath = nb.pdfFilePath,
                        strokes = currentStrokes,
                        outputStream = out
                    )
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Page PDF successfully saved!"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, statusMessage = "Failed to write PDF: ${e.localizedMessage}") }
            }
        }
    }

    fun saveNotebookPdfToDownloadsById(notebookId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Generating and saving PDF...") }
            try {
                val nb = db.getNotebook(notebookId) ?: return@launch
                val pages = db.getPagesForNotebook(notebookId)
                val fileName = ExportManager.saveNotebookPdfToDownloads(
                    context = getApplication(),
                    notebook = nb,
                    pages = pages,
                    strokeLoader = { p ->
                        StrokeBinarySerializer.loadStrokes(File(p.strokeFilePath))
                    }
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "PDF saved to Downloads: $fileName"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, statusMessage = "Failed to save PDF: ${e.localizedMessage}") }
            }
        }
    }

    fun exportNotebookPdfById(notebookId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Generating PDF...") }
            try {
                val nb = db.getNotebook(notebookId) ?: return@launch
                val pages = db.getPagesForNotebook(notebookId)
                val uri = ExportManager.exportNotebookAsPdf(
                    context = getApplication(),
                    notebook = nb,
                    pages = pages,
                    strokeLoader = { p ->
                        StrokeBinarySerializer.loadStrokes(File(p.strokeFilePath))
                    }
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        exportShareUri = uri,
                        exportShareMime = "application/pdf"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, statusMessage = "Export failed: ${e.localizedMessage}") }
            }
        }
    }

    fun clearShareUri() {
        _uiState.update { it.copy(exportShareUri = null, exportShareMime = null) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun onTrimMemory() {
        PdfPageManager.clearMemoryCache()
    }
}
