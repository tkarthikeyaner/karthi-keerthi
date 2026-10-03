package com.karthi.smartcleaner.scanner

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

interface ContentHasher {
    /** Cheap fingerprint of head + tail; null if the file can't be read. */
    suspend fun partial(path: String): String?

    /** SHA-256 of the whole file; null if the file can't be read. */
    suspend fun full(path: String): String?
}

class Sha256ContentHasher : ContentHasher {

    override suspend fun partial(path: String): String? = readOrNull {
        RandomAccessFile(path, "r").use { raf ->
            val digest = MessageDigest.getInstance("SHA-256")
            val length = raf.length()
            val buffer = ByteArray(SAMPLE_BYTES)
            val head = raf.read(buffer).coerceAtLeast(0)
            digest.update(buffer, 0, head)
            if (length > SAMPLE_BYTES * 2) {
                raf.seek(length - SAMPLE_BYTES)
                val tail = raf.read(buffer).coerceAtLeast(0)
                digest.update(buffer, 0, tail)
            }
            digest.digest().toHex()
        }
    }

    override suspend fun full(path: String): String? = readOrNull {
        File(path).inputStream().buffered(CHUNK_BYTES).use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(CHUNK_BYTES)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().toHex()
        }
    }

    private inline fun readOrNull(block: () -> String): String? = try {
        block()
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val SAMPLE_BYTES = 16 * 1024
        const val CHUNK_BYTES = 64 * 1024
    }
}
