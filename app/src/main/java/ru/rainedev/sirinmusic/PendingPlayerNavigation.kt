package ru.rainedev.sirinmusic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** Notification navigation waits for login/NavHost and is consumed once. No playback command. */
class PendingPlayerNavigation {
    private val pending = MutableStateFlow(false)
    val requested = pending.asStateFlow()
    fun offer() { pending.value = true }
    fun consume(): Boolean = pending.getAndUpdate { false }
    fun restore(fromPlayerIntent: Boolean, pendingAtSave: Boolean?) {
        if (fromPlayerIntent || pendingAtSave == true) offer()
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "ru.rainedev.sirinmusic.OPEN_PLAYER"
    }
}
