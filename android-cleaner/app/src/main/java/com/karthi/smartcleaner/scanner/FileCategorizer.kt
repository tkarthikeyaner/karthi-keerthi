package com.karthi.smartcleaner.scanner

import com.karthi.smartcleaner.data.FileCategory

object FileCategorizer {
    /** Installed-app packages and app data containers are out of scope and never scanned. */
    private val APP_EXTENSIONS = setOf("apk", "apks", "xapk", "apkm", "aab", "obb", "odex", "dex")

    private val PHOTO = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng", "raw", "tif", "tiff", "avif")
    private val VIDEO = setOf("mp4", "mkv", "mov", "avi", "3gp", "3g2", "webm", "m4v", "ts", "flv", "wmv", "mpeg", "mpg")
    private val AUDIO = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "oga", "opus", "amr", "wma", "mid", "midi")
    private val DOCUMENT = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "odp",
        "csv", "md", "epub", "mobi", "json", "xml", "html", "htm", "pages", "numbers", "key", "vcf",
    )

    fun extensionOf(name: String): String =
        name.substringAfterLast('.', missingDelimiterValue = "").lowercase()

    fun isAppPackage(name: String): Boolean = extensionOf(name) in APP_EXTENSIONS

    fun categorize(name: String): FileCategory = when (extensionOf(name)) {
        in PHOTO -> FileCategory.PHOTOS
        in VIDEO -> FileCategory.VIDEOS
        in AUDIO -> FileCategory.AUDIO
        in DOCUMENT -> FileCategory.DOCUMENTS
        else -> FileCategory.OTHER
    }
}
