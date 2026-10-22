package ru.rainedev.sirinmusic.playback

import ru.rainedev.sirinmusic.data.EventReply
import ru.rainedev.sirinmusic.data.Track

enum class AdvanceAction { PLAY_NEXT, STOP, START_RADIO, KEEP_CURRENT }

fun advanceAction(reply: EventReply, wasFixed: Boolean, current: Track? = null): AdvanceAction = when {
    // Older servers omit ignored but still identify the same played impression.
    !reply.ended && current != null && reply.next != null && !current.impressionId.isNullOrBlank() &&
        current.key == reply.next.key && current.impressionId == reply.next.impressionId -> AdvanceAction.KEEP_CURRENT
    reply.next != null && (!reply.ignored || current == null || !sameImpression(current, reply.next)) -> AdvanceAction.PLAY_NEXT
    reply.ignored && !reply.ended -> AdvanceAction.KEEP_CURRENT
    reply.ended || reply.fixed || wasFixed -> AdvanceAction.STOP
    else -> AdvanceAction.START_RADIO
}

private fun sameImpression(current: Track, next: Track): Boolean = current.key == next.key &&
    (current.impressionId.isNullOrBlank() || next.impressionId.isNullOrBlank() || current.impressionId == next.impressionId)
