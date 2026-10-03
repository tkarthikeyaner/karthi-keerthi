package com.karthi.smartcleaner.scanner

import com.karthi.smartcleaner.data.ScannedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

/**
 * Iterative (stack-based) walk of shared storage. Skips hidden entries, symlinks, app containers
 * (Android/data, Android/obb) and app package files. Cancellation is checked per directory.
 */
class FileScanner(private val root: File) {

    private val excludedDirs: Set<String> = setOf(
        File(root, "Android/data").absolutePath,
        File(root, "Android/obb").absolutePath,
    )

    suspend fun scan(onProgress: (filesFound: Int) -> Unit = {}): List<ScannedFile> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<ScannedFile>(8_192)
            val stack = ArrayDeque<File>().apply { add(root) }
            var lastReported = 0

            while (stack.isNotEmpty()) {
                ensureActive()
                val dir = stack.removeLast()
                val children = try {
                    dir.listFiles()
                } catch (_: SecurityException) {
                    null
                } ?: continue

                for (child in children) {
                    val name = child.name
                    if (name.startsWith('.')) continue
                    if (Files.isSymbolicLink(child.toPath())) continue
                    when {
                        child.isDirectory -> if (child.absolutePath !in excludedDirs) stack.addLast(child)
                        child.isFile && !FileCategorizer.isAppPackage(name) -> out += ScannedFile(
                            path = child.absolutePath,
                            name = name,
                            size = child.length(),
                            lastModified = child.lastModified(),
                            category = FileCategorizer.categorize(name),
                        )
                    }
                }
                if (out.size - lastReported >= PROGRESS_STEP) {
                    lastReported = out.size
                    onProgress(out.size)
                }
            }
            onProgress(out.size)
            out
        }

    private companion object {
        const val PROGRESS_STEP = 250
    }
}
