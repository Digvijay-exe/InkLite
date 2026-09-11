package com.example

import android.content.ComponentCallbacks2
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.NotebookListScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.InkLiteViewModel

class MainActivity : ComponentActivity(), ComponentCallbacks2 {

    private val viewModel: InkLiteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InkLiteApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Aggressive memory hygiene for 1GB RAM budget (PRD 5.3 & 6)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE
        ) {
            viewModel.onTrimMemory()
        }
    }
}

@Composable
fun InkLiteApp(viewModel: InkLiteViewModel) {
    val state by viewModel.uiState.collectAsState()

    if (state.currentNotebook != null) {
        BackHandler {
            viewModel.closeNotebook()
        }

        EditorScreen(
            state = state,
            onBack = { strokes ->
                viewModel.closeNotebook(strokes)
            },
            onSelectPage = { newIndex, strokes ->
                viewModel.selectPage(newIndex, strokes)
            },
            onAddNewPage = { template, strokes ->
                viewModel.addNewPage(template, strokes)
            },
            onDuplicatePage = { strokes ->
                viewModel.duplicateCurrentPage(strokes)
            },
            onDeletePage = {
                viewModel.deleteCurrentPage()
            },
            onUpdatePageTemplate = { template ->
                viewModel.updateCurrentPageTemplate(template)
            },
            onSetTool = { tool ->
                viewModel.setTool(tool)
            },
            onSetColor = { color ->
                viewModel.setColor(color)
            },
            onSetStrokeWidth = { width ->
                viewModel.setStrokeWidth(width)
            },
            onUpdateUndoRedoState = { canUndo, canRedo ->
                viewModel.updateUndoRedoState(canUndo, canRedo)
            },
            onExportPageAsImage = { strokes, isPng, transparent ->
                viewModel.exportCurrentPageAsImage(strokes, isPng, transparent)
            },
            onExportPageAsPdf = { strokes ->
                viewModel.exportCurrentPageAsPdf(strokes)
            },
            onExportNotebookAsPdf = { strokes ->
                viewModel.exportNotebookAsPdf(strokes)
            },
            onSaveNotebookPdfToDownloads = { strokes ->
                viewModel.saveCurrentNotebookPdfToDownloads(strokes)
            },
            onSavePagePdfToDownloads = { strokes ->
                viewModel.saveCurrentPagePdfToDownloads(strokes)
            },
            onWriteNotebookPdfToUri = { uri, strokes ->
                viewModel.writeCurrentNotebookPdfToUri(uri, strokes)
            },
            onWritePagePdfToUri = { uri, strokes ->
                viewModel.writeCurrentPagePdfToUri(uri, strokes)
            },
            onSetEraserMode = { mode ->
                viewModel.setEraserMode(mode)
            },
            onSetEraserRadius = { radius ->
                viewModel.setEraserRadius(radius)
            },
            onRefreshCacheStats = {
                viewModel.refreshCacheStats()
            },
            onClearCacheMemory = {
                viewModel.clearCacheMemory()
            },
            onClearStatusMessage = {
                viewModel.clearStatusMessage()
            },
            onClearShareUri = {
                viewModel.clearShareUri()
            },
            onImportPdf = { uri, title ->
                viewModel.importPdfNotebook(uri, title)
            }
        )
    } else {
        NotebookListScreen(
            state = state,
            onOpenNotebook = { notebookId ->
                viewModel.openNotebook(notebookId)
            },
            onCreateNotebook = { title, color, template ->
                viewModel.createNotebook(title, color, template)
            },
            onImportPdf = { uri, title ->
                viewModel.importPdfNotebook(uri, title)
            },
            onRenameNotebook = { notebookId, newTitle ->
                viewModel.renameNotebook(notebookId, newTitle)
            },
            onDeleteNotebook = { notebookId ->
                viewModel.deleteNotebook(notebookId)
            },
            onSaveNotebookPdf = { notebookId ->
                viewModel.saveNotebookPdfToDownloadsById(notebookId)
            },
            onShareNotebookPdf = { notebookId ->
                viewModel.exportNotebookPdfById(notebookId)
            },
            onClearShareUri = {
                viewModel.clearShareUri()
            },
            onClearStatusMessage = {
                viewModel.clearStatusMessage()
            }
        )
    }
}
