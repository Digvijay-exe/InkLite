package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.InkStroke
import com.example.data.model.PageTemplate
import com.example.ui.canvas.InkCanvasView
import com.example.ui.canvas.InkTool
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentIceBlue
import com.example.ui.theme.AccentLavender
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfacePaper
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.InkPalette
import com.example.ui.theme.OnAccentLavender
import com.example.ui.viewmodel.InkLiteUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: InkLiteUiState,
    onBack: (List<InkStroke>) -> Unit,
    onSelectPage: (Int, List<InkStroke>) -> Unit,
    onAddNewPage: (PageTemplate, List<InkStroke>) -> Unit,
    onDuplicatePage: (List<InkStroke>) -> Unit,
    onDeletePage: () -> Unit,
    onUpdatePageTemplate: (PageTemplate) -> Unit,
    onSetTool: (InkTool) -> Unit,
    onSetColor: (Int) -> Unit,
    onSetStrokeWidth: (Float) -> Unit,
    onUpdateUndoRedoState: (Boolean, Boolean) -> Unit,
    onExportPageAsImage: (List<InkStroke>, Boolean) -> Unit,
    onExportPageAsPdf: (List<InkStroke>) -> Unit,
    onExportNotebookAsPdf: (List<InkStroke>) -> Unit,
    onSaveNotebookPdfToDownloads: (List<InkStroke>) -> Unit = {},
    onSavePagePdfToDownloads: (List<InkStroke>) -> Unit = {},
    onWriteNotebookPdfToUri: (Uri, List<InkStroke>) -> Unit = { _, _ -> },
    onWritePagePdfToUri: (Uri, List<InkStroke>) -> Unit = { _, _ -> },
    onClearStatusMessage: () -> Unit = {},
    onClearShareUri: () -> Unit
) {
    val context = LocalContext.current
    var canvasViewRef by remember { mutableStateOf<InkCanvasView?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog & Sheet States
    var showColorDialog by remember { mutableStateOf(false) }
    var showWidthDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showPagesSheet by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showClearPageConfirm by remember { mutableStateOf(false) }
    var pendingSaveTarget by remember { mutableStateOf<String?>(null) } // "notebook" or "page"

    // SAF Document Creator for saving PDF to custom user location
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            val strokes = canvasViewRef?.getStrokes() ?: state.currentPageStrokes
            if (pendingSaveTarget == "page") {
                onWritePagePdfToUri(uri, strokes)
            } else {
                onWriteNotebookPdfToUri(uri, strokes)
            }
        }
        pendingSaveTarget = null
    }

    // Show status snackbar (e.g. "PDF saved to Downloads: ...")
    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearStatusMessage()
        }
    }

    // Dispatch system share sheet when export completes
    LaunchedEffect(state.exportShareUri) {
        state.exportShareUri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = state.exportShareMime ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Note"))
            onClearShareUri()
        }
    }

    val notebook = state.currentNotebook ?: return
    val currentPage = state.currentPages.getOrNull(state.currentPageIndex)

    Scaffold(
        modifier = Modifier.testTag("editor_screen"),
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Sophisticated Dark Header: px-4 h-16 bg-[#1C1B1F]
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = notebook.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 18.sp,
                                color = DarkTextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (currentPage != null) "Page ${state.currentPageIndex + 1} of ${state.currentPages.size}" else "Editing",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                color = DarkTextSecondary
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            val strokes = canvasViewRef?.getStrokes() ?: state.currentPageStrokes
                            onBack(strokes)
                        },
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkTextPrimary
                        )
                    }
                },
                actions = {
                    // Undo Button
                    IconButton(
                        onClick = { canvasViewRef?.undo() },
                        enabled = state.canUndo,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .testTag("undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (state.canUndo) DarkTextPrimary else DarkTextSecondary.copy(alpha = 0.4f)
                        )
                    }

                    // Redo Button
                    IconButton(
                        onClick = { canvasViewRef?.redo() },
                        enabled = state.canRedo,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .testTag("redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (state.canRedo) DarkTextPrimary else DarkTextSecondary.copy(alpha = 0.4f)
                        )
                    }

                    // Pages Manager Button
                    IconButton(
                        onClick = { showPagesSheet = true },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .testTag("pages_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "Pages",
                            tint = DarkTextPrimary
                        )
                    }

                    // More Options / Export Menu
                    Box {
                        IconButton(
                            onClick = { showExportMenu = true },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .testTag("export_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = DarkTextPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false },
                            modifier = Modifier.background(DarkSurfaceContainer)
                        ) {
                            // 1. Save Notebook as PDF (Offline direct save)
                            DropdownMenuItem(
                                text = { Text("Save Notebook to Downloads (PDF)", color = DarkTextPrimary, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = AccentLavender) },
                                onClick = {
                                    showExportMenu = false
                                    val strokes = canvasViewRef?.getStrokes() ?: state.currentPageStrokes
                                    onSaveNotebookPdfToDownloads(strokes)
                                }
                            )

                            // 2. Save Notebook as PDF (Choose folder via system picker)
                            DropdownMenuItem(
                                text = { Text("Save Notebook As... (Select Folder)", color = DarkTextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = AccentLavender) },
                                onClick = {
                                    showExportMenu = false
                                    pendingSaveTarget = "notebook"
                                    val cleanTitle = (state.currentNotebook?.title ?: "Notes").replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                                    createDocumentLauncher.launch("${cleanTitle}.pdf")
                                }
                            )

                            // 3. Save Current Page as PDF to Downloads
                            DropdownMenuItem(
                                text = { Text("Save Page to Downloads (PDF)", color = DarkTextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = AccentCoral) },
                                onClick = {
                                    showExportMenu = false
                                    val strokes = canvasViewRef?.getStrokes() ?: state.currentPageStrokes
                                    onSavePagePdfToDownloads(strokes)
                                }
                            )

                            // 4. Save Current Page as PDF (Choose folder)
                            DropdownMenuItem(
                                text = { Text("Save Page As... (Select Folder)", color = DarkTextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null, tint = AccentCoral) },
                                onClick = {
                                    showExportMenu = false
                                    pendingSaveTarget = "page"
                                    val cleanTitle = (state.currentNotebook?.title ?: "Notes").replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                                    val pageNum = state.currentPageIndex + 1
                                    createDocumentLauncher.launch("${cleanTitle}_page_${pageNum}.pdf")
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = DarkBorder.copy(alpha = 0.4f)
                            )

                            // 5. Share PDF actions
                            DropdownMenuItem(
                                text = { Text("Share Entire Notebook PDF", color = DarkTextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = AccentIceBlue) },
                                onClick = {
                                    showExportMenu = false
                                    val strokes = canvasViewRef?.getStrokes() ?: state.currentPageStrokes
                                    onExportNotebookAsPdf(strokes)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Current Page PDF", color = DarkTextPrimary) },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AccentIceBlue) },
                                onClick = {
                                    showExportMenu = false
                                    val strokes = canvasViewRef?.getStrokes() ?: state.currentPageStrokes
                                    onExportPageAsPdf(strokes)
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = DarkBorder.copy(alpha = 0.4f)
                            )

                            // 6. Image Exports
                            DropdownMenuItem(
                                text = { Text("Export Page as PNG", color = DarkTextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = AccentLavender) },
                                onClick = {
                                    showExportMenu = false
                                    val strokes = canvasViewRef?.getStrokes() ?: state.currentPageStrokes
                                    onExportPageAsImage(strokes, true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Page as JPEG", color = DarkTextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = AccentCoral) },
                                onClick = {
                                    showExportMenu = false
                                    val strokes = canvasViewRef?.getStrokes() ?: state.currentPageStrokes
                                    onExportPageAsImage(strokes, false)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = DarkTextPrimary,
                    navigationIconContentColor = DarkTextPrimary,
                    actionIconContentColor = DarkTextPrimary
                )
            )
        },
        bottomBar = {
            // Sophisticated Dark Footer: h-20 px-6 bg-[#1C1B1F] border-t border-[#49454F]/20
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkBackground,
                border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Page indicator pill (text-sm font-medium text-[#D0BCFF] bg-[#D0BCFF]/10 px-4 py-2 rounded-full)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (state.currentPageIndex > 0) {
                                    val currentStrokes = canvasViewRef?.getStrokes() ?: state.currentPageStrokes
                                    onSelectPage(state.currentPageIndex - 1, currentStrokes)
                                }
                            },
                            enabled = state.currentPageIndex > 0,
                            modifier = Modifier.size(36.dp).testTag("prev_page_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Previous Page",
                                tint = if (state.currentPageIndex > 0) DarkTextPrimary else DarkTextSecondary.copy(alpha = 0.3f)
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { showPagesSheet = true },
                            color = AccentLavender.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "PAGE ${state.currentPageIndex + 1}/${state.currentPages.size}",
                                color = AccentLavender,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (state.currentPageIndex < state.currentPages.size - 1) {
                                    val currentStrokes = canvasViewRef?.getStrokes() ?: state.currentPageStrokes
                                    onSelectPage(state.currentPageIndex + 1, currentStrokes)
                                }
                            },
                            enabled = state.currentPageIndex < state.currentPages.size - 1,
                            modifier = Modifier.size(36.dp).testTag("next_page_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Next Page",
                                tint = if (state.currentPageIndex < state.currentPages.size - 1) DarkTextPrimary else DarkTextSecondary.copy(alpha = 0.3f)
                            )
                        }
                    }

                    // Right: Add Page '+' button and solid 'EXPORT' button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val currentStrokes = canvasViewRef?.getStrokes() ?: state.currentPageStrokes
                                onAddNewPage(state.currentNotebook?.template ?: PageTemplate.LINED, currentStrokes)
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceContainer)
                                .border(1.dp, DarkBorder.copy(alpha = 0.35f), CircleShape)
                                .testTag("add_page_quick_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Page",
                                tint = DarkTextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Button(
                            onClick = {
                                showExportMenu = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentLavender,
                                contentColor = OnAccentLavender
                            ),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("export_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "EXPORT",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground)
        ) {
            // Sophisticated Dark Nav Toolbar: bg-[#25232A] mx-4 rounded-2xl h-14 border border-[#49454F]/30
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .testTag("drawing_toolbar"),
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceContainer,
                border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Pen Tool
                    NavToolButton(
                        icon = Icons.Default.Edit,
                        label = "Pen",
                        isSelected = state.selectedTool == InkTool.PEN,
                        onClick = {
                            onSetTool(InkTool.PEN)
                            canvasViewRef?.currentTool = InkTool.PEN
                        },
                        tag = "tool_pen"
                    )

                    // Highlighter Tool
                    NavToolButton(
                        icon = Icons.Default.Highlight,
                        label = "Highlighter",
                        isSelected = state.selectedTool == InkTool.HIGHLIGHTER,
                        onClick = {
                            onSetTool(InkTool.HIGHLIGHTER)
                            canvasViewRef?.currentTool = InkTool.HIGHLIGHTER
                        },
                        tag = "tool_highlighter"
                    )

                    // Eraser Tool
                    NavToolButton(
                        icon = Icons.Default.CleaningServices,
                        label = "Eraser",
                        isSelected = state.selectedTool == InkTool.ERASER,
                        onClick = {
                            onSetTool(InkTool.ERASER)
                            canvasViewRef?.currentTool = InkTool.ERASER
                        },
                        tag = "tool_eraser"
                    )

                    // Pan / Zoom Tool
                    NavToolButton(
                        icon = Icons.Default.PanTool,
                        label = "Pan / Zoom",
                        isSelected = state.selectedTool == InkTool.PAN_ZOOM,
                        onClick = {
                            onSetTool(InkTool.PAN_ZOOM)
                            canvasViewRef?.currentTool = InkTool.PAN_ZOOM
                        },
                        tag = "tool_pan_zoom"
                    )

                    // Vertical Divider: w-px h-6 bg-[#49454F]
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(DarkBorder)
                    )

                    // Quick Curated Color Dots: w-6 h-6 rounded-full
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val quickColors = listOf(
                            AccentLavender to "Lavender",
                            AccentCoral to "Coral",
                            AccentIceBlue to "Ice Blue",
                            DarkTextPrimary to "White"
                        )
                        quickColors.forEach { (color, _) ->
                            val isSelected = (state.selectedColor == color.toArgb())
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        val argb = color.toArgb()
                                        onSetColor(argb)
                                        canvasViewRef?.currentColor = argb
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (color == AccentLavender) OnAccentLavender else Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        // More Colors
                        IconButton(
                            onClick = { showColorDialog = true },
                            modifier = Modifier.size(30.dp).testTag("color_picker_trigger")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "All Colors",
                                tint = DarkTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(DarkBorder)
                    )

                    // Stroke Thickness Pill
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = { showWidthDialog = true })
                            .testTag("width_picker_trigger"),
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurfaceContainer,
                        border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LineWeight,
                                contentDescription = null,
                                tint = DarkTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${state.selectedStrokeWidth.toInt()}pt",
                                style = MaterialTheme.typography.labelSmall.copy(color = DarkTextPrimary)
                            )
                        }
                    }

                    // Template Switcher
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = { showTemplateDialog = true })
                            .testTag("template_picker_trigger"),
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurfaceContainer,
                        border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = null,
                                tint = DarkTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currentPage?.template?.title ?: "Template",
                                style = MaterialTheme.typography.labelSmall.copy(color = DarkTextPrimary)
                            )
                        }
                    }

                    // Clear Page Button
                    IconButton(
                        onClick = { showClearPageConfirm = true },
                        modifier = Modifier.size(34.dp).testTag("clear_page_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear Page",
                            tint = AccentCoral,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Sophisticated Dark Canvas: flex-1 relative m-4 mt-3 bg-[#1D1B20] rounded-2xl border border-[#49454F]/50
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(BorderStroke(1.dp, DarkBorder.copy(alpha = 0.5f)), RoundedCornerShape(18.dp))
                    .background(DarkSurfacePaper)
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("ink_canvas_view"),
                    factory = { ctx ->
                        InkCanvasView(ctx).apply {
                            currentTool = state.selectedTool
                            currentColor = state.selectedColor
                            currentStrokeWidth = state.selectedStrokeWidth
                            currentTemplate = currentPage?.template ?: PageTemplate.LINED
                            pdfBackgroundBitmap = state.currentPdfBitmap
                            setStrokes(state.currentPageStrokes)

                            onStateChanged = { _, canUndo, canRedo ->
                                onUpdateUndoRedoState(canUndo, canRedo)
                            }
                            canvasViewRef = this
                        }
                    },
                    update = { view ->
                        view.currentTool = state.selectedTool
                        view.currentColor = state.selectedColor
                        view.currentStrokeWidth = state.selectedStrokeWidth
                        if (currentPage != null && view.currentTemplate != currentPage.template) {
                            view.currentTemplate = currentPage.template
                        }
                        if (view.pdfBackgroundBitmap != state.currentPdfBitmap) {
                            view.pdfBackgroundBitmap = state.currentPdfBitmap
                        }
                    }
                )

                // Floating Zoom / Fit Page Button in bottom-right: w-12 h-12 rounded-full bg-[#313033] border border-[#49454F]
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable { canvasViewRef?.fitPageToScreen() }
                        .testTag("fit_screen_button"),
                    shape = CircleShape,
                    color = DarkSurfaceElevated,
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FitScreen,
                            contentDescription = "Fit Page",
                            tint = DarkTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Sync strokes when page changes
            LaunchedEffect(state.currentPageIndex, state.currentPageStrokes) {
                canvasViewRef?.setStrokes(state.currentPageStrokes)
            }
        }
    }

    // Color Dialog with curated Sophisticated Dark Palette
    if (showColorDialog) {
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            containerColor = DarkSurfaceContainer,
            title = { Text("Ink Palette", fontWeight = FontWeight.Bold, color = DarkTextPrimary) },
            text = {
                Column {
                    Text(
                        "Curated for high legibility on dark surfaces:",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        InkPalette.CuratedColors.take(4).forEach { (color, name) ->
                            DarkColorSwatch(
                                color = color,
                                name = name,
                                isSelected = color.toArgb() == state.selectedColor,
                                onSelect = {
                                    val cArgb = color.toArgb()
                                    onSetColor(cArgb)
                                    canvasViewRef?.currentColor = cArgb
                                    showColorDialog = false
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        InkPalette.CuratedColors.drop(4).take(4).forEach { (color, name) ->
                            DarkColorSwatch(
                                color = color,
                                name = name,
                                isSelected = color.toArgb() == state.selectedColor,
                                onSelect = {
                                    val cArgb = color.toArgb()
                                    onSetColor(cArgb)
                                    canvasViewRef?.currentColor = cArgb
                                    showColorDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorDialog = false }) {
                    Text("Close", color = AccentLavender)
                }
            }
        )
    }

    // Stroke Thickness Dialog
    if (showWidthDialog) {
        AlertDialog(
            onDismissRequest = { showWidthDialog = false },
            containerColor = DarkSurfaceContainer,
            title = { Text("Stroke Thickness", fontWeight = FontWeight.Bold, color = DarkTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InkPalette.StrokeWidthPresets.forEach { (widthValue, label) ->
                        val isSelected = (state.selectedStrokeWidth == widthValue)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetStrokeWidth(widthValue)
                                    canvasViewRef?.currentStrokeWidth = widthValue
                                    showWidthDialog = false
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) AccentLavender.copy(alpha = 0.15f) else DarkSurfacePaper,
                            border = BorderStroke(1.dp, if (isSelected) AccentLavender else DarkBorder.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .width(44.dp)
                                            .height(widthValue.dp.coerceAtLeast(2.dp))
                                            .background(Color(state.selectedColor), RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = DarkTextPrimary
                                        )
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AccentLavender
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWidthDialog = false }) {
                    Text("Close", color = AccentLavender)
                }
            }
        )
    }

    // Template Switcher Dialog
    if (showTemplateDialog && currentPage != null) {
        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            containerColor = DarkSurfaceContainer,
            title = { Text("Page Template", color = DarkTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PageTemplate.entries.forEach { tmpl ->
                        val isSelected = currentPage.template == tmpl
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdatePageTemplate(tmpl)
                                    canvasViewRef?.currentTemplate = tmpl
                                    showTemplateDialog = false
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) AccentLavender.copy(alpha = 0.15f) else DarkSurfacePaper,
                            border = BorderStroke(1.dp, if (isSelected) AccentLavender else DarkBorder.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = tmpl.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = DarkTextPrimary
                                        )
                                    )
                                    Text(
                                        text = tmpl.description,
                                        style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary)
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AccentLavender
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplateDialog = false }) {
                    Text("Close", color = AccentLavender)
                }
            }
        )
    }

    // Clear Page Confirmation
    if (showClearPageConfirm) {
        AlertDialog(
            onDismissRequest = { showClearPageConfirm = false },
            containerColor = DarkSurfaceContainer,
            title = { Text("Clear Page?", color = DarkTextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to clear all ink strokes on this page? You can still Undo this action.", color = DarkTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearPageConfirm = false
                        canvasViewRef?.clearAllStrokes()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCoral, contentColor = Color.Black)
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearPageConfirm = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            }
        )
    }

    // Pages Sheet
    if (showPagesSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showPagesSheet = false },
            sheetState = sheetState,
            containerColor = DarkSurfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notebook Pages (${state.currentPages.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary
                        )
                    )
                    Button(
                        onClick = {
                            val currentStrokes = canvasViewRef?.getStrokes() ?: state.currentPageStrokes
                            onAddNewPage(state.currentNotebook?.template ?: PageTemplate.LINED, currentStrokes)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentLavender, contentColor = OnAccentLavender),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Page", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(state.currentPages) { index, page ->
                        val isCurrent = index == state.currentPageIndex
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val currentStrokes = canvasViewRef?.getStrokes() ?: state.currentPageStrokes
                                    onSelectPage(index, currentStrokes)
                                    showPagesSheet = false
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) AccentLavender.copy(alpha = 0.12f) else DarkSurfacePaper
                            ),
                            border = BorderStroke(1.dp, if (isCurrent) AccentLavender else DarkBorder.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isCurrent) AccentLavender else DarkBorder.copy(alpha = 0.4f)
                                    ) {
                                        Text(
                                            text = "#${page.pageNumber}",
                                            color = if (isCurrent) OnAccentLavender else DarkTextPrimary,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Page ${page.pageNumber}",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = DarkTextPrimary
                                            )
                                        )
                                        Text(
                                            text = if (page.pdfPageIndex >= 0) "PDF Page ${page.pdfPageIndex + 1}" else page.template.title,
                                            style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary)
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            val currentStrokes = canvasViewRef?.getStrokes() ?: state.currentPageStrokes
                                            onDuplicatePage(currentStrokes)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Duplicate Page",
                                            tint = DarkTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    if (state.currentPages.size > 1) {
                                        IconButton(
                                            onClick = {
                                                onDeletePage()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Page",
                                                tint = AccentCoral,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun NavToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(tag),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) AccentLavender else Color.Transparent,
        border = if (isSelected) null else BorderStroke(1.dp, DarkBorder.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) OnAccentLavender else DarkTextPrimary.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) OnAccentLavender else DarkTextPrimary.copy(alpha = 0.85f)
                )
            )
        }
    }
}

@Composable
fun DarkColorSwatch(
    color: Color,
    name: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onSelect)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = if (color == AccentLavender) OnAccentLavender else Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = DarkTextSecondary),
            maxLines = 1
        )
    }
}
