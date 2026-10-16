package ru.rainedev.sirinmusic

import org.junit.Assert.*
import org.junit.Test

class PendingPlayerNavigationTest {
    @Test fun notificationWaitsUntilNavigationIsReadyAndIsConsumedOnce() {
        val pending = PendingPlayerNavigation()
        pending.offer()
        assertTrue(pending.requested.value)
        assertTrue(pending.consume())
        assertFalse(pending.consume())
    }

    @Test fun pendingRequestSurvivesRecreationButConsumedOneDoesNotReopenPlayer() {
        val pending = PendingPlayerNavigation()
        pending.restore(fromPlayerIntent = false, pendingAtSave = true)
        assertTrue(pending.consume())
        pending.restore(fromPlayerIntent = false, pendingAtSave = false)
        assertFalse(pending.requested.value)
    }

    @Test fun newTapOpensPlayerEvenWhenPreviousSavedRequestWasConsumed() {
        val pending = PendingPlayerNavigation()
        pending.restore(fromPlayerIntent = true, pendingAtSave = false)
        assertTrue(pending.consume())
        pending.offer()
        assertTrue(pending.consume())
    }
}
