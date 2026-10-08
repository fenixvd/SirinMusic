package ru.rainedev.sirinmusic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import ru.rainedev.sirinmusic.data.ConnectLink
import java.security.MessageDigest

/**
 * A musik://connect link opened from the system camera, waiting for the login screen.
 *
 * AppViewModel owns it, so a link that has not been used yet survives Activity
 * recreation (rotation, theme change). [consume] hands it out once and clears it, so a
 * screen recreated after that does not sign in again.
 *
 * After process death the ViewModel is new and empty. The Intent comes back with the
 * Activity, so the saved-state Bundle keeps only [marker] (a hash, never the token) of
 * the link that was still unused, and [restore] takes the link from that Intent again
 * only if it is that link. A used link leaves no marker and is not restored.
 */
class PendingLink {
    private val link = MutableStateFlow<ConnectLink?>(null)
    val value: StateFlow<ConnectLink?> = link.asStateFlow()

    fun offer(next: ConnectLink) { link.value = next }

    fun consume(): ConnectLink? = link.getAndUpdate { null }

    /** Bundle-safe id of the unused link, or null when there is none. */
    fun marker(): String? = link.value?.let(::markerOf)

    /**
     * Re-offers [fromIntent] after process death when [savedMarker] says it was still
     * unused. MainActivity keeps a link from onNewIntent as its Intent (setIntent), so
     * that is the link read back here; a different one never matches the marker.
     */
    fun restore(fromIntent: ConnectLink?, savedMarker: String?) {
        if (fromIntent == null || savedMarker == null) return
        if (markerOf(fromIntent) == savedMarker) link.compareAndSet(null, fromIntent)
    }

    companion object {
        fun markerOf(link: ConnectLink): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("${link.baseUrl}\n${link.token.orEmpty()}".toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
