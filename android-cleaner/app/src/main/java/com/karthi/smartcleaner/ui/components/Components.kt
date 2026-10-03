package com.karthi.smartcleaner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.karthi.smartcleaner.data.FileCategory
import com.karthi.smartcleaner.data.ScannedFile
import com.karthi.smartcleaner.ui.formatBytes
import com.karthi.smartcleaner.ui.formatDate
import com.karthi.smartcleaner.ui.theme.accent
import java.io.File

val FileCategory.icon: ImageVector
    get() = when (this) {
        FileCategory.PHOTOS -> Icons.Outlined.Image
        FileCategory.VIDEOS -> Icons.Outlined.Movie
        FileCategory.AUDIO -> Icons.Outlined.AudioFile
        FileCategory.DOCUMENTS -> Icons.Outlined.Description
        FileCategory.OTHER -> Icons.Outlined.InsertDriveFile
    }

@Composable
fun categoryColor(category: FileCategory): Color =
    category.accent(MaterialTheme.colorScheme.background.luminanceIsDark())

private fun Color.luminanceIsDark(): Boolean = (0.299 * red + 0.587 * green + 0.114 * blue) < 0.5

@Composable
fun CategoryBadge(category: FileCategory, modifier: Modifier = Modifier, size: Int = 48) {
    val tint = categoryColor(category)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.icon, contentDescription = null, tint = tint, modifier = Modifier.size((size / 2).dp))
    }
}

@Composable
fun FileThumbnail(file: ScannedFile, modifier: Modifier = Modifier) {
    if (file.category != FileCategory.PHOTOS && file.category != FileCategory.VIDEOS) {
        CategoryBadge(file.category, modifier)
        return
    }
    Box(modifier = modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        SubcomposeAsyncImage(
            model = File(file.path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp),
            loading = { CategoryBadge(file.category) },
            error = { CategoryBadge(file.category) },
        )
        if (file.category == FileCategory.VIDEOS) {
            Icon(Icons.Outlined.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun FileRow(
    file: ScannedFile,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "${formatBytes(file.size)} · ${formatDate(file.lastModified)}",
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileThumbnail(file)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                file.path.substringBeforeLast('/'),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) trailing() else Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

/**
 * Final confirmation before anything is removed. Every candidate starts checked; unchecking a file
 * keeps it. Nothing is deleted until the user presses the destructive button.
 */
@Composable
fun ConfirmDeleteDialog(
    candidates: List<ScannedFile>,
    onConfirm: (List<ScannedFile>) -> Unit,
    onDismiss: () -> Unit,
) {
    var keep by remember(candidates) { mutableStateOf(emptySet<String>()) }
    val toDelete = candidates.filterNot { it.path in keep }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${toDelete.size} of ${candidates.size} files?") },
        text = {
            Column {
                Text(
                    "Untick anything you want to keep. Deleted files can't be recovered.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(8.dp))
                HorizontalDivider()
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(candidates, key = { it.path }) { file ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { keep = if (file.path in keep) keep - file.path else keep + file.path }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = file.path !in keep,
                                onCheckedChange = { del -> keep = if (del) keep - file.path else keep + file.path },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatBytes(file.size), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(toDelete) },
                enabled = toDelete.isNotEmpty(),
            ) {
                Text(
                    "Delete · ${formatBytes(toDelete.sumOf { it.size })}",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
