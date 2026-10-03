package com.karthi.smartcleaner.scanner

import com.karthi.smartcleaner.data.DuplicateKind
import com.karthi.smartcleaner.data.FileCategory
import com.karthi.smartcleaner.data.ScannedFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {

    /** Content is simulated by a path -> hash map; partial and full hashes agree unless overridden. */
    private class FakeHasher(
        private val full: Map<String, String>,
        private val partial: Map<String, String> = full,
    ) : ContentHasher {
        var fullCalls = 0
        override suspend fun partial(path: String) = partial[path]
        override suspend fun full(path: String): String? {
            fullCalls++
            return full[path]
        }
    }

    private fun file(path: String, size: Long, modified: Long = 0) = ScannedFile(
        path = path,
        name = path.substringAfterLast('/'),
        size = size,
        lastModified = modified,
        category = FileCategorizer.categorize(path),
    )

    @Test
    fun `normalizes copy markers`() {
        assertEquals("report.pdf", DuplicateDetector.normalizedName("Report (2).PDF"))
        assertEquals("song.mp3", DuplicateDetector.normalizedName("song - Copy.mp3"))
        assertEquals("song.mp3", DuplicateDetector.normalizedName("song - Copy (3).mp3"))
        assertEquals("img_2024.jpg", DuplicateDetector.normalizedName("IMG_2024.jpg"))
        assertTrue(DuplicateDetector.hasCopyMarker("name (1).ext"))
        assertFalse(DuplicateDetector.hasCopyMarker("IMG_2024.jpg"))
    }

    @Test
    fun `finds exact duplicates and keeps the original name`() = runTest {
        val a = file("/s/DCIM/photo.jpg", 500, modified = 10)
        val b = file("/s/Download/photo (1).jpg", 500, modified = 5)
        val c = file("/s/Download/other.jpg", 500)
        val hasher = FakeHasher(mapOf(a.path to "X", b.path to "X", c.path to "Y"))

        val groups = DuplicateDetector(hasher).find(listOf(a, b, c))

        assertEquals(1, groups.size)
        val group = groups.single()
        assertEquals(DuplicateKind.EXACT, group.kind)
        assertEquals(setOf(a.path, b.path), group.files.map { it.path }.toSet())
        assertEquals(a.path, group.suggestedKeep)
        assertEquals(500L, group.reclaimableBytes)
    }

    @Test
    fun `skips full hash when partial fingerprints differ`() = runTest {
        val a = file("/s/a.mp4", 1_000)
        val b = file("/s/b.mp4", 1_000)
        val hasher = FakeHasher(full = mapOf(a.path to "Z", b.path to "Z"), partial = mapOf(a.path to "1", b.path to "2"))

        val groups = DuplicateDetector(hasher).find(listOf(a, b))

        assertTrue(groups.isEmpty())
        assertEquals(0, hasher.fullCalls)
    }

    @Test
    fun `groups similar names with different content`() = runTest {
        val a = file("/s/Docs/invoice.pdf", 100)
        val b = file("/s/Download/invoice (1).pdf", 120)
        val c = file("/s/Download/unrelated.pdf", 130)
        val hasher = FakeHasher(mapOf(a.path to "1", b.path to "2", c.path to "3"))

        val groups = DuplicateDetector(hasher).find(listOf(a, b, c))

        assertEquals(1, groups.size)
        assertEquals(DuplicateKind.SIMILAR_NAME, groups.single().kind)
        assertEquals(a.path, groups.single().suggestedKeep)
    }

    @Test
    fun `same name in different folders without copy marker is not flagged`() = runTest {
        val a = file("/s/A/notes.txt", 10)
        val b = file("/s/B/notes.txt", 20)
        val groups = DuplicateDetector(FakeHasher(emptyMap())).find(listOf(a, b))
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `categorizer excludes app packages`() {
        assertTrue(FileCategorizer.isAppPackage("WhatsApp.apk"))
        assertTrue(FileCategorizer.isAppPackage("game.XAPK"))
        assertFalse(FileCategorizer.isAppPackage("report.pdf"))
        assertEquals(FileCategory.VIDEOS, FileCategorizer.categorize("clip.MKV"))
        assertEquals(FileCategory.DOCUMENTS, FileCategorizer.categorize("sheet.xlsx"))
    }
}
