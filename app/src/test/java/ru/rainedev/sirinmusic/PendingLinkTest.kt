package ru.rainedev.sirinmusic

import org.junit.Assert.*
import org.junit.Test
import ru.rainedev.sirinmusic.data.ConnectLink

class PendingLinkTest {
    private val link = ConnectLink("http://b.local:8787", "t-b")

    @Test fun unusedLinkIsStillThereForARecreatedScreen() {
        val pending = PendingLink() // lives in AppViewModel, which outlives the Activity
        pending.offer(link)
        // The first screen goes away before using the link; the recreated one sees it.
        assertEquals(link, pending.value.value)
        assertEquals(link, pending.value.value)
        assertEquals(link, pending.consume())
    }

    @Test fun consumedLinkIsHandedOutOnlyOnce() {
        val pending = PendingLink()
        pending.offer(link)
        assertEquals(link, pending.consume())
        // A screen recreated after sign-in started must not sign in again.
        assertNull(pending.value.value)
        assertNull(pending.consume())
    }

    @Test fun newerLinkReplacesAnUnusedOne() {
        val pending = PendingLink()
        pending.offer(ConnectLink("http://a.local:8787", "t-a"))
        pending.offer(link)
        assertEquals(link, pending.consume())
    }

    // Process death: a new ViewModel, the same launch Intent, and the saved marker.

    @Test fun unusedLinkIsRestoredFromTheIntentAfterProcessDeath() {
        val before = PendingLink()
        before.offer(link)
        val saved = before.marker()
        val after = PendingLink()
        after.restore(link, saved)
        assertEquals(link, after.consume())
    }

    @Test fun usedLinkIsNotRestoredAfterProcessDeath() {
        val before = PendingLink()
        before.offer(link)
        before.consume()
        val saved = before.marker()
        assertNull(saved)
        val after = PendingLink()
        after.restore(link, saved)
        assertNull(after.consume())
    }

    @Test fun launchIntentIsNotRestoredWhenAnotherLinkWasPending() {
        val before = PendingLink()
        before.offer(ConnectLink("http://a.local:8787", "t-a")) // launch Intent, used
        before.consume()
        before.offer(link) // a newer link; the restored Intent still carries the old one
        val after = PendingLink()
        after.restore(ConnectLink("http://a.local:8787", "t-a"), before.marker())
        assertNull(after.consume())
    }

    @Test fun linkFromOnNewIntentIsRestoredFromTheUpdatedIntent() {
        val before = PendingLink()
        before.offer(ConnectLink("http://a.local:8787", "t-a")) // launch Intent, used
        before.consume()
        before.offer(link) // onNewIntent, which also makes it the Activity's Intent
        val after = PendingLink()
        after.restore(link, before.marker())
        assertEquals(link, after.consume())
    }

    @Test fun restoreKeepsALinkThatIsAlreadyThere() {
        val pending = PendingLink()
        pending.offer(link)
        val newer = ConnectLink("http://c.local:8787", "t-c")
        pending.offer(newer)
        pending.restore(link, PendingLink.markerOf(link))
        assertEquals(newer, pending.consume())
    }

    @Test fun markerDoesNotCarryTheToken() {
        val marker = PendingLink.markerOf(ConnectLink("http://b.local:8787", "secret-token"))
        assertFalse(marker.contains("secret-token"))
        assertNotEquals(marker, PendingLink.markerOf(ConnectLink("http://b.local:8787", "other-token")))
    }
}
