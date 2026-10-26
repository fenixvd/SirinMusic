package ru.rainedev.sirinmusic

import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.rainedev.sirinmusic.update.UpdateManager

internal class AppUpdates(private val app: SirinApp) {
    val manager by lazy { UpdateManager(app) }

    fun initialize() {
        manager.schedule()
        CoroutineScope(Dispatchers.IO).launch { manager.cleanOldDownloads() }
    }

    fun checkOnLaunch() = manager.checkOnLaunch()

    fun openSettings() {
        app.startActivity(Intent(app, UpdatesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object { const val available = true }
}
