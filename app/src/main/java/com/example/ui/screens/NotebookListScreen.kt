package com.example.ui.screens

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.model.Notebook
import com.example.data.model.PageTemplate
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentIceBlue
import com.example.ui.theme.AccentLavender
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.DarkSurfacePaper
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.InkPalette
import com.example.ui.theme.OnAccentLavender
import com.example.ui.viewmodel.InkLiteUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookListScreen(
    state: InkLiteUiState,
    onOpenNotebook: (String) -> Unit,
    onCreateNotebook: (String, Int, PageTemplate) -> Unit,
    onImportPdf: (Uri, String) -> Unit,
    onRenameNotebook: (String, String) -> Unit,
    onDeleteNotebook: (String) -> Unit,
    onSaveNotebookPdf: (String) -> Unit = {},
    onShareNotebookPdf: (String) -> Unit = {},
    onClearShareUri: () -> Unit = {},
    onClearStatusMessage: () -> Unit
) {
    val context = LocalContext.current
    var showNewNotebookDialog by remember { mutableStateOf(false) }
    var notebookToRename by remember { mutableStateOf<Notebook?>(null) }
    var notebookToDelete by remember { mutableStateOf<Notebook?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // PDF Import Launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val defaultTitle = uri.lastPathSegment?.substringAfterLast('/')
                ?.substringBeforeLast(".pdf") ?: "Imported PDF"
            onImportPdf(uri, defaultTitle)
        }
    }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearStatusMessage()
        }
    }

    LaunchedEffect(state.exportShareUri) {
        state.exportShareUri?.let { uri ->
            val mime = state.exportShareMime ?: "application/pdf"
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "InkLite Note")
                clipData = ClipData.newUri(context.contentResolver, "InkLite Note", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Share Note PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
            onClearShareUri()
        }
    }

    Scaffold(
        modifier = Modifier.testTag("notebook_list_screen"),
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceContainer)
                                .border(1.dp, DarkBorder.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                tint = AccentLavender,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "InkLite",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = DarkTextPrimary
                                )
                            )
                            Text(
                                text = "Vector Notes & PDF Markup",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = DarkTextSecondary
                                )
                            )
                        }
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("import_pdf_button"),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = DarkSurfaceContainer,
                            contentColor = DarkTextPrimary
                        ),
                        border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.UploadFile,
                            contentDescription = "Import PDF",
                            tint = AccentLavender,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import PDF", style = MaterialTheme.typography.labelLarge.copy(color = DarkTextPrimary))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = DarkTextPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewNotebookDialog = true },
                containerColor = AccentLavender,
                contentColor = OnAccentLavender,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("new_notebook_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "New Notebook")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Notebook", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Header performance stats banner
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = DarkSurfaceContainer,
                    border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Memory,
                                contentDescription = null,
                                tint = AccentLavender,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pure Vector Engine • Fully Offline",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = DarkTextPrimary
                                )
                            )
                        }
                        Text(
                            text = "${state.notebooks.size} Notebooks",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = DarkTextSecondary
                            )
                        )
                    }
                }

                if (state.notebooks.isEmpty() && !state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = DarkBorder
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Notebooks Yet",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = DarkTextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Create a handwritten notebook or import a PDF to start annotating.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = DarkTextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { showNewNotebookDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentLavender,
                                        contentColor = OnAccentLavender
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("New Notebook", fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = DarkTextPrimary
                                    ),
                                    border = BorderStroke(1.dp, AccentLavender.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("empty_state_import_pdf_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.UploadFile,
                                        contentDescription = null,
                                        tint = AccentLavender
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Annotate PDF", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.testTag("notebook_grid")
                    ) {
                        items(state.notebooks, key = { it.id }) { notebook ->
                            NotebookCard(
                                notebook = notebook,
                                onClick = { onOpenNotebook(notebook.id) },
                                onRename = { notebookToRename = notebook },
                                onDelete = { notebookToDelete = notebook },
                                onSavePdf = { onSaveNotebookPdf(notebook.id) },
                                onSharePdf = { onShareNotebookPdf(notebook.id) }
                            )
                        }
                    }
                }
            }

            if (state.isLoading) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.4f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentLavender)
                    }
                }
            }
        }
    }

    // New Notebook Dialog
    if (showNewNotebookDialog) {
        NewNotebookDialog(
            onDismiss = { showNewNotebookDialog = false },
            onConfirm = { title, color, template ->
                showNewNotebookDialog = false
                onCreateNotebook(title, color, template)
            }
        )
    }

    // Rename Dialog
    notebookToRename?.let { nb ->
        RenameNotebookDialog(
            notebook = nb,
            onDismiss = { notebookToRename = null },
            onConfirm = { newTitle ->
                onRenameNotebook(nb.id, newTitle)
                notebookToRename = null
            }
        )
    }

    // Delete Dialog
    notebookToDelete?.let { nb ->
        AlertDialog(
            onDismissRequest = { notebookToDelete = null },
            containerColor = DarkSurfaceContainer,
            title = { Text("Delete Notebook", color = DarkTextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${nb.title}'? All pages and stroke data will be permanently removed.", color = DarkTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteNotebook(nb.id)
                        notebookToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCoral, contentColor = Color.Black)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { notebookToDelete = null }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            }
        )
    }
}

@Composable
fun NotebookCard(
    notebook: Notebook,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSavePdf: () -> Unit = {},
    onSharePdf: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val coverColor = Color(notebook.coverColor)
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val updatedDateStr = remember(notebook.updatedAt) { dateFormat.format(Date(notebook.updatedAt)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("notebook_card_${notebook.id}"),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceContainer),
        border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.35f))
    ) {
        Column {
            // Notebook Cover Spine & Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(coverColor)
                    .padding(12.dp)
            ) {
                // Spine line decoration
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DarkBackground.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = if (notebook.isPdf) "PDF" else notebook.template.title,
                                color = AccentLavender,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.background(DarkSurfaceContainer)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Save as PDF", color = DarkTextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = AccentLavender) },
                                    onClick = {
                                        menuExpanded = false
                                        onSavePdf()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share PDF", color = DarkTextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = AccentIceBlue) },
                                    onClick = {
                                        menuExpanded = false
                                        onSharePdf()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename", color = DarkTextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = AccentLavender) },
                                    onClick = {
                                        menuExpanded = false
                                        onRename()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = DarkTextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = AccentCoral) },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = if (notebook.isPdf) Icons.Default.PictureAsPdf else Icons.Outlined.Description,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Card Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = notebook.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${notebook.pageCount} ${if (notebook.pageCount == 1) "page" else "pages"}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = AccentLavender,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = updatedDateStr,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = DarkTextSecondary
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun NewNotebookDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, color: Int, template: PageTemplate) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var selectedTemplate by remember { mutableStateOf(PageTemplate.LINED) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceContainer,
        title = { Text("New Notebook", fontWeight = FontWeight.Bold, color = DarkTextPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Notebook Title") },
                    placeholder = { Text("e.g., Biology Lab, Sketches") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DarkTextPrimary,
                        unfocusedTextColor = DarkTextPrimary,
                        focusedBorderColor = AccentLavender,
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = AccentLavender,
                        unfocusedLabelColor = DarkTextSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notebook_title_input")
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Cover Color", style = MaterialTheme.typography.labelMedium, color = DarkTextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InkPalette.NotebookCovers.forEachIndexed { index, (color, _) ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedColorIndex == index) 3.dp else 1.dp,
                                    color = if (selectedColorIndex == index) AccentLavender else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorIndex = index }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Initial Template", style = MaterialTheme.typography.labelMedium, color = DarkTextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PageTemplate.entries.forEach { tmpl ->
                        val isSelected = selectedTemplate == tmpl
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTemplate = tmpl },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) AccentLavender else DarkSurfacePaper,
                            border = BorderStroke(1.dp, if (isSelected) AccentLavender else DarkBorder.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = tmpl.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) OnAccentLavender else DarkTextPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = if (title.isBlank()) "Untitled Notebook" else title.trim()
                    val chosenColor = InkPalette.NotebookCovers[selectedColorIndex].first.toArgb()
                    onConfirm(finalTitle, chosenColor, selectedTemplate)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentLavender,
                    contentColor = OnAccentLavender
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("create_notebook_confirm_button")
            ) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DarkTextSecondary)
            }
        }
    )
}

@Composable
fun RenameNotebookDialog(
    notebook: Notebook,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newTitle by remember { mutableStateOf(notebook.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceContainer,
        title = { Text("Rename Notebook", color = DarkTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                label = { Text("Title") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DarkTextPrimary,
                    unfocusedTextColor = DarkTextPrimary,
                    focusedBorderColor = AccentLavender,
                    unfocusedBorderColor = DarkBorder,
                    focusedLabelColor = AccentLavender,
                    unfocusedLabelColor = DarkTextSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newTitle) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentLavender,
                    contentColor = OnAccentLavender
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DarkTextSecondary)
            }
        }
    )
}
