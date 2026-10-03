package com.karthi.smartcleaner.scanner

import com.karthi.smartcleaner.data.DuplicateGroup
import com.karthi.smartcleaner.data.DuplicateKind
import com.karthi.smartcleaner.data.ScannedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Two-pass duplicate detection:
 *  1. EXACT: same size -> same head/tail fingerprint -> same full SHA-256. Only candidates that
 *     survive each cheaper pass are read in full, so large media is rarely hashed end-to-end.
 *  2. SIMILAR_NAME: remaining files whose names collapse to the same base once copy markers such as
 *     "name (1).ext" or "name - Copy.ext" are stripped.
 */
class DuplicateDetector(private val hasher: ContentHasher) {

    suspend fun find(files: List<ScannedFile>): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val exact = findExact(files)
        val claimed = exact.flatMapTo(HashSet()) { group -> group.files.map { it.path } }
        exact + findSimilarNames(files.filterNot { it.path in claimed })
    }

    private suspend fun findExact(files: List<ScannedFile>): List<DuplicateGroup> {
        val groups = mutableListOf<DuplicateGroup>()
        val sameSize = files.filter { it.size > 0 }.groupBy { it.size }.values.filter { it.size > 1 }
        for (sizeBucket in sameSize) {
            val samePartial = sizeBucket.groupByHash { hasher.partial(it.path) }
            for (partialBucket in samePartial) {
                for ((hash, bucket) in partialBucket.groupByHashKeyed { hasher.full(it.path) }) {
                    groups += DuplicateGroup(
                        id = "exact:$hash",
                        kind = DuplicateKind.EXACT,
                        files = bucket.sortedBy { it.lastModified },
                        suggestedKeep = suggestKeep(bucket),
                    )
                }
            }
        }
        return groups.sortedByDescending { it.reclaimableBytes }
    }

    private fun findSimilarNames(files: List<ScannedFile>): List<DuplicateGroup> =
        files.groupBy { normalizedName(it.name) }
            .filter { (_, bucket) -> bucket.size > 1 && bucket.any { hasCopyMarker(it.name) } }
            .map { (key, bucket) ->
                DuplicateGroup(
                    id = "name:$key",
                    kind = DuplicateKind.SIMILAR_NAME,
                    files = bucket.sortedBy { it.lastModified },
                    suggestedKeep = suggestKeep(bucket),
                )
            }
            .sortedByDescending { it.reclaimableBytes }

    private suspend fun List<ScannedFile>.groupByHash(hash: suspend (ScannedFile) -> String?): List<List<ScannedFile>> =
        groupByHashKeyed(hash).values.toList()

    private suspend fun List<ScannedFile>.groupByHashKeyed(
        hash: suspend (ScannedFile) -> String?,
    ): Map<String, List<ScannedFile>> {
        val out = LinkedHashMap<String, MutableList<ScannedFile>>()
        for (file in this) {
            val key = hash(file) ?: continue
            out.getOrPut(key) { mutableListOf() } += file
        }
        return out.filterValues { it.size > 1 }
    }

    companion object {
        private val COPY_MARKER = Regex(
            """^(.*?)(?:\s*\(\d+\)|\s*-\s*copy(?:\s*\(\d+\))?|\s+copy(?:\s+\d+)?)$""",
            RegexOption.IGNORE_CASE,
        )

        private fun splitName(name: String): Pair<String, String> {
            val dot = name.lastIndexOf('.')
            return if (dot > 0) name.substring(0, dot) to name.substring(dot + 1) else name to ""
        }

        fun hasCopyMarker(name: String): Boolean {
            val base = splitName(name).first
            return COPY_MARKER.matchEntire(base)?.groupValues?.get(1)?.let { it != base } ?: false
        }

        /** "Report (2).PDF" -> "report.pdf", "song - Copy.mp3" -> "song.mp3". */
        fun normalizedName(name: String): String {
            val (base, ext) = splitName(name)
            val core = COPY_MARKER.matchEntire(base)?.groupValues?.get(1) ?: base
            val cleaned = core.trim().lowercase()
            return if (ext.isEmpty()) cleaned else "$cleaned.${ext.lowercase()}"
        }

        fun suggestKeep(files: List<ScannedFile>): String =
            (files.filterNot { hasCopyMarker(it.name) }.minByOrNull { it.lastModified }
                ?: files.minBy { it.lastModified }).path
    }
}
