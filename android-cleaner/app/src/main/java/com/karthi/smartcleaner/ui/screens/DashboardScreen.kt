package com.karthi.smartcleaner.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karthi.smartcleaner.data.FileCategory
import com.karthi.smartcleaner.data.ScanPhase
import com.karthi.smartcleaner.data.ScanResult
import com.karthi.smartcleaner.data.ScanState
import com.karthi.smartcleaner.ui.MainViewModel
import com.karthi.smartcleaner.ui.components.CategoryBadge
import com.karthi.smartcleaner.ui.components.categoryColor
import com.karthi.smartcleaner.ui.formatBytes
import com.karthi.smartcleaner.ui.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: MainViewModel,
    snackbar: SnackbarHostState,
    onRequestStorage: () -> Unit,
    onOpenFilter: (FileFilter) -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.scanState.collectAsStateWithLifecycle()
    val hasAccess by vm.hasStorageAccess.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val result = state.result

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text("Smart Cleaner") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "permission") {
                AnimatedVisibility(visible = !hasAccess) { PermissionCard(onRequestStorage) }
            }
            item(key = "hero") { SummaryCard(state, enabled = hasAccess, onScan = vm::scan) }
            if (result != null) {
                item(key = "insights") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InsightCard(
                            icon = Icons.Outlined.Straighten,
                            title = "Large files",
                            value = "${result.largeFiles.size}",
                            caption = "≥ ${formatBytes(result.thresholdBytes)} · ${formatBytes(result.largeFiles.sumOf { it.size })}",
                            onClick = { onOpenFilter(FileFilter.LARGE) },
                            modifier = Modifier.weight(1f),
                        )
                        InsightCard(
                            icon = Icons.Outlined.ContentCopy,
                            title = "Duplicates",
                            value = "${result.duplicateFileCount}",
                            caption = "${formatBytes(result.reclaimableDuplicateBytes)} reclaimable",
                            onClick = onOpenDuplicates,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item(key = "categories-header") {
                    Text(
                        "Categories",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
                items(FileCategory.entries, key = { it.name }) { category ->
                    CategoryCard(category, result) { onOpenFilter(FileFilter.valueOf(category.name)) }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                "Storage access needed",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "To find photos, videos, documents and every other file, allow \"All files access\". " +
                    "Files never leave your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(onClick = onRequest) { Text("Grant access") }
        }
    }
}

@Composable
private fun SummaryCard(state: ScanState, enabled: Boolean, onScan: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(20.dp)) {
            val result = state.result
            Text("Scanned storage", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (result != null) formatBytes(result.totalBytes) else "—",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (result != null) {
                Text(
                    "${result.files.size} files · ${result.newFilesCount} new · ${formatDate(result.scannedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            AnimatedContent(
                targetState = state.phase,
                contentKey = { it::class },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "scan-phase",
            ) { phase ->
                when (phase) {
                    ScanPhase.Idle -> FilledTonalButton(onClick = onScan, enabled = enabled) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (result == null) "Scan now" else "Rescan")
                    }
                    is ScanPhase.Walking -> ScanProgress("Scanning… ${phase.filesFound} files found")
                    ScanPhase.FindingDuplicates -> ScanProgress("Looking for duplicates…")
                }
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text("Last scan failed: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ScanProgress(label: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun InsightCard(
    icon: ImageVector,
    title: String,
    value: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryCard(category: FileCategory, result: ScanResult, onClick: () -> Unit) {
    val stats = result.byCategory.getValue(category)
    val share = if (result.totalBytes > 0) stats.bytes.toFloat() / result.totalBytes else 0f
    val animatedShare by animateFloatAsState(share, tween(700), label = "share")
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(category)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(category.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(formatBytes(stats.bytes), style = MaterialTheme.typography.titleSmall)
                }
                Text("${stats.count} files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { animatedShare },
                    color = categoryColor(category),
                    modifier = Modifier.fillMaxWidth(),
                    drawStopIndicator = {},
                )
            }
        }
    }
}
