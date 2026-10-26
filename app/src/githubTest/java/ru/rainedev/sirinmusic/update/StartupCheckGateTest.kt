package ru.rainedev.sirinmusic.update

import org.junit.Assert.*
import org.junit.Test

class StartupCheckGateTest {
    @Test fun bothAutomaticChecksAreOffByDefault() {
        val state = UpdateState()
        assertFalse(state.onLaunch)
        assertFalse(state.automatic)
    }
    @Test fun enabledCheckRunsOnceAcrossActivityRecreation() {
        val gate = StartupCheckGate()
        assertTrue(gate.take(true))
        repeat(20) { assertFalse(gate.take(true)) }
        assertTrue(StartupCheckGate().take(true))
    }
    @Test fun enablingAfterLaunchWaitsForNextProcess() {
        val gate = StartupCheckGate()
        assertFalse(gate.take(false))
        assertFalse(gate.take(true))
    }
}
