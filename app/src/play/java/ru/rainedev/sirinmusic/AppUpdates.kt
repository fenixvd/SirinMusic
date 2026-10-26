package ru.rainedev.sirinmusic

import androidx.work.WorkManager

/** Google Play handles application updates; no APK updater is included in this build. */
internal class AppUpdates(private val app: SirinApp) {
    fun initialize() {
        // Also cancel work restored from a previous build or backup.
        WorkManager.getInstance(app).cancelUniqueWork("sirin-update-check")
    }

    fun checkOnLaunch() = Unit
    fun openSettings() = Unit

    companion object { const val available = false }
}
