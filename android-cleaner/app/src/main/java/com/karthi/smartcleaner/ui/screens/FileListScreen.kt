package com.karthi.smartcleaner.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karthi.smartcleaner.data.FileCategory
import com.karthi.smartcleaner.data.ScannedFile
import com.karthi.smartcleaner.ui.MainViewModel
import com.karthi.smartcleaner.ui.components.ConfirmDeleteDialog
import com.karthi.smartcleaner.ui.components.FileRow
import com.karthi.smartcleaner.ui.formatBytes

enum class FileFilter(val title: String, val category: FileCategory?) {
    PHOTOS("Photos", FileCategory.PHOTOS),
    VIDEOS("Videos", FileCategory.VIDEOS),
    AUDIO("Audio", FileCategory.AUDIO),
    DOCUMENTS("Documents", FileCategory.DOCUMENTS),
    OTHER("Other files", FileCategory.OTHER),
    LARGE("Large files", null),
}

private enum class SortOrder(val label: String) { SIZE("Largest first"), DATE("Newest first"), NAME("Name") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    vm: MainViewModel,
    filter: FileFilter,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val state by vm.scanState.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    var sort by rememberSaveable { mutableStateOf(SortOrder.SIZE) }
    var sortMenu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<List<ScannedFile>?>(null) }

    LaunchedEffect(filter) { vm.setSelection(emptySet()) }

    val files = remember(state.result, filter, sort) {
        val result = state.result ?: return@remember emptyList<ScannedFile>()
        val base = if (filter == FileFilter.LARGE) result.largeFiles else result.files.filter { it.category == filter.category }
        when (sort) {
            SortOrder.SIZE -> base.sortedByDescending { it.size }
            SortOrder.DATE -> base.sortedByDescending { it.lastModified }
            SortOrder.NAME -> base.sortedBy { it.name.lowercase() }
        }
    }
    val selectedFiles = remember(files, selection) { files.filter { it.path in selection } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (selection.isEmpty()) filter.title else "${selection.size} selected")
                        Text(
                            "${files.size} files · ${formatBytes(files.sumOf { it.size })}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        val all = files.mapTo(HashSet()) { it.path }
                        vm.setSelection(if (selection.containsAll(all)) emptySet() else all)
                    }) { Icon(Icons.Outlined.SelectAll, "Select all") }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, "Sort") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    onClick = { sort = order; sortMenu = false },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selectedFiles.isNotEmpty(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { confirm = selectedFiles },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                    ) {
                        Icon(Icons.Outlined.Delete, null)
                        Text("  Delete ${selectedFiles.size} · ${formatBytes(selectedFiles.sumOf { it.size })}")
                    }
                }
            }
        },
    ) { padding ->
        if (files.isEmpty()) {
            EmptyState(
                text = if (state.result == null) "Run a scan to see files here." else "Nothing here — all clean!",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                items(files, key = { it.path }, contentType = { it.category }) { file ->
                    FileRow(
                        file = file,
                        checked = file.path in selection,
                        onToggle = { vm.toggleSelection(file.path) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    confirm?.let { candidates ->
        ConfirmDeleteDialog(
            candidates = candidates,
            onConfirm = { chosen -> vm.delete(chosen); confirm = null },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
