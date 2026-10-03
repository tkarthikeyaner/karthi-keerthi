package com.karthi.smartcleaner.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karthi.smartcleaner.data.DuplicateGroup
import com.karthi.smartcleaner.data.DuplicateKind
import com.karthi.smartcleaner.data.ScannedFile
import com.karthi.smartcleaner.ui.MainViewModel
import com.karthi.smartcleaner.ui.components.ConfirmDeleteDialog
import com.karthi.smartcleaner.ui.components.FileRow
import com.karthi.smartcleaner.ui.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(vm: MainViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val state by vm.scanState.collectAsStateWithLifecycle()
    val deletions by vm.duplicateDeletions.collectAsStateWithLifecycle()
    val groups = state.result?.duplicateGroups.orEmpty()
    var confirm by remember { mutableStateOf<List<ScannedFile>?>(null) }

    // Pre-select every copy except the suggested original whenever the set of groups changes.
    LaunchedEffect(state.result?.scannedAt) { vm.resetDuplicateDecisions(groups) }

    val marked = remember(groups, deletions) { groups.flatMap { g -> g.files.filter { it.path in deletions } } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Duplicates")
                        Text(
                            "${groups.size} groups",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { vm.resetDuplicateDecisions(groups) }) {
                        Icon(Icons.Outlined.Restore, "Reset suggestions")
                    }
                },
            )
        },
        bottomBar = {
            AnimatedVisibility(marked.isNotEmpty(), enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { confirm = marked },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    ) {
                        Icon(Icons.Outlined.CleaningServices, null)
                        Text("  Clean ${marked.size} copies · ${formatBytes(marked.sumOf { it.size })}")
                    }
                }
            }
        },
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyState(
                text = if (state.result == null) "Run a scan to find duplicates." else "No duplicates found 🎉",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(groups, key = { it.id }) { group ->
                DuplicateGroupCard(
                    group = group,
                    deletions = deletions,
                    onToggle = { path -> vm.toggleDuplicateDecision(group, path) },
                    modifier = Modifier.animateItem(),
                )
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
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    deletions: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = {},
                label = { Text(if (group.kind == DuplicateKind.EXACT) "Identical content" else "Similar name") },
            )
            Text(
                "  ${group.files.size} files · ${formatBytes(group.files.sumOf { it.size })}",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        group.files.forEach { file ->
            val delete = file.path in deletions
            FileRow(
                file = file,
                checked = delete,
                onToggle = { onToggle(file.path) },
                trailing = {
                    FilterChip(
                        selected = delete,
                        onClick = { onToggle(file.path) },
                        label = { Text(if (delete) "Delete" else "Keep") },
                        leadingIcon = {
                            Icon(if (delete) Icons.Outlined.Delete else Icons.Outlined.Verified, null)
                        },
                    )
                },
            )
        }
    }
}
