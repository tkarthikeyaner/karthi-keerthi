package com.karthi.smartcleaner.data

enum class FileCategory(val label: String) {
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    OTHER("Other"),
}

data class ScannedFile(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val category: FileCategory,
)

enum class DuplicateKind { EXACT, SIMILAR_NAME }

data class DuplicateGroup(
    val id: String,
    val kind: DuplicateKind,
    val files: List<ScannedFile>,
    /** Path of the file suggested to keep: the original name if present, otherwise the oldest copy. */
    val suggestedKeep: String,
) {
    val reclaimableBytes: Long
        get() = files.filter { it.path != suggestedKeep }.sumOf { it.size }
}

data class CategoryStats(val count: Int = 0, val bytes: Long = 0)

data class ScanResult(
    val files: List<ScannedFile>,
    val largeFiles: List<ScannedFile>,
    val duplicateGroups: List<DuplicateGroup>,
    val newFilesCount: Int,
    val thresholdBytes: Long,
    val scannedAt: Long,
    val durationMs: Long,
) {
    val totalBytes: Long = files.sumOf { it.size }

    val byCategory: Map<FileCategory, CategoryStats> = FileCategory.entries.associateWith { category ->
        files.filter { it.category == category }
            .let { CategoryStats(it.size, it.sumOf { f -> f.size }) }
    }

    val duplicateFileCount: Int get() = duplicateGroups.sumOf { it.files.size - 1 }
    val reclaimableDuplicateBytes: Long get() = duplicateGroups.sumOf { it.reclaimableBytes }

    /** Returns a copy with [removedPaths] dropped from every list; groups left with < 2 files vanish. */
    fun without(removedPaths: Set<String>): ScanResult {
        if (removedPaths.isEmpty()) return this
        val groups = duplicateGroups.mapNotNull { group ->
            val remaining = group.files.filterNot { it.path in removedPaths }
            if (remaining.size < 2) return@mapNotNull null
            val keep = if (group.suggestedKeep in removedPaths) remaining.minBy { it.lastModified }.path else group.suggestedKeep
            group.copy(files = remaining, suggestedKeep = keep)
        }
        return copy(
            files = files.filterNot { it.path in removedPaths },
            largeFiles = largeFiles.filterNot { it.path in removedPaths },
            duplicateGroups = groups,
        )
    }
}

data class DeleteOutcome(val deleted: Set<String>, val failed: List<String>, val freedBytes: Long)
