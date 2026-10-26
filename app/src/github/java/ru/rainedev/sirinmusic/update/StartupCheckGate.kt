package ru.rainedev.sirinmusic.update

import java.util.concurrent.atomic.AtomicBoolean

/** Один запуск интерфейса за процесс, независимо от пересоздания Activity. */
internal class StartupCheckGate {
    private val firstLaunch = AtomicBoolean(true)
    fun take(enabled: Boolean): Boolean = firstLaunch.compareAndSet(true, false) && enabled
}
