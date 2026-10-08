package ru.rainedev.sirinmusic.update

internal data class ReleaseVersion(val major: Long, val minor: Long, val patch: Long, val pre: String?) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        for ((a, b) in listOf(major to other.major, minor to other.minor, patch to other.patch)) {
            val order = a.compareTo(b)
            if (order != 0) return order
        }
        if (pre == other.pre) return 0
        if (pre == null) return 1
        if (other.pre == null) return -1
        val a = pre.split('.'); val b = other.pre.split('.')
        for (i in 0 until minOf(a.size, b.size)) {
            val x = a[i].toLongOrNull(); val y = b[i].toLongOrNull()
            val order = when {
                x != null && y != null -> x.compareTo(y)
                x != null -> -1
                y != null -> 1
                else -> a[i].compareTo(b[i])
            }
            if (order != 0) return order
        }
        return a.size.compareTo(b.size)
    }
    companion object {
        private val pattern = Regex("^v?([0-9]+)\\.([0-9]+)(?:\\.([0-9]+))?(?:-([0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*))?$")
        fun parse(raw: String): ReleaseVersion? {
            // Development builds also use the project's compact RC label, e.g. 2.0rc0.1-dev.
            val normalized = raw.trim().replace(
                Regex("^(v?[0-9]+\\.[0-9]+)rc([0-9]+(?:\\.[0-9]+)*)(-dev)?$"),
                "$1.0-rc.$2$3",
            )
            val m = pattern.matchEntire(normalized) ?: return null
            return ReleaseVersion(m.groupValues[1].toLongOrNull() ?: return null,
                m.groupValues[2].toLongOrNull() ?: return null,
                m.groupValues[3].ifEmpty { "0" }.toLongOrNull() ?: return null,
                m.groupValues[4].ifEmpty { null })
        }
    }
}
