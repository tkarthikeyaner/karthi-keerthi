package com.karthi.smartcleaner

import android.content.Context
import com.karthi.smartcleaner.data.ScanRepository
import com.karthi.smartcleaner.data.SettingsRepository
import com.karthi.smartcleaner.scanner.DuplicateDetector
import com.karthi.smartcleaner.scanner.Sha256ContentHasher

/** Manual DI: one instance per process, shared by UI and background work. */
class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context)
    val scanRepository = ScanRepository(
        context = context.applicationContext,
        settings = settingsRepository,
        detector = DuplicateDetector(Sha256ContentHasher()),
    )
}
