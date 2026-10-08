package ru.rainedev.sirinmusic.update

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private fun release(tag: String = "v1.5.0"): GitHubRelease {
        val name = "SirinMusic-${tag.removePrefix("v")}.apk"
        return GitHubRelease(tag, assets = listOf(GitHubAsset(name, 3000000, "sha256:" + "a".repeat(64),
            "https://github.com/fenixvd/SirinMusic/releases/download/$tag/$name")))
    }
    @Test fun versionComparisonHandlesDevAndNumericOrdering() {
        assertTrue(ReleaseVersion.parse("1.10.0")!! > ReleaseVersion.parse("1.9.9")!!)
        assertTrue(ReleaseVersion.parse("1.5.0")!! > ReleaseVersion.parse("1.5-dev")!!)
        assertTrue(ReleaseVersion.parse("1.5.0-rc.10")!! > ReleaseVersion.parse("1.5.0-rc.2")!!)
        listOf("", "garbage", "1", "1.5/evil", "9999999999999999999999.0.0").forEach { assertNull(ReleaseVersion.parse(it)) }
    }
    @Test fun stableUpdateCannotDowngradeDevOrReinstallSameVersion() {
        assertNull(selectUpdate(release("v1.0.0"), "1.5-dev"))
        assertNull(selectUpdate(release(), "1.5.0"))
        assertEquals("1.5.0", selectUpdate(release(), "1.5-dev")?.version)
    }
    @Test fun compactRcLabelKeepsUpdateOrdering() {
        assertEquals(ReleaseVersion.parse("2.0.0-rc.0.1-dev"), ReleaseVersion.parse("2.0rc0.1-dev"))
        assertTrue(ReleaseVersion.parse("2.0rc0.2-dev")!! > ReleaseVersion.parse("2.0rc0.1-dev")!!)
        assertNull(selectUpdate(release("v1.5.0"), "2.0rc0.1-dev"))
        assertEquals("2.0.0", selectUpdate(release("v2.0.0"), "2.0rc0.1-dev")?.version)
    }
    @Test fun draftAndPrereleaseAreIgnored() {
        assertNull(selectUpdate(release().copy(draft = true), "1.0.0"))
        assertNull(selectUpdate(release().copy(prerelease = true), "1.0.0"))
        assertNull(selectUpdate(release("v2.0.0-beta"), "1.0.0"))
    }
    @Test fun unknownSourcesMissingDigestAndOversizedAssetsAreRejected() {
        val r = release(); val asset = r.assets.single()
        for (invalid in listOf(asset.copy(url = "https://example.com/update.apk"),
            asset.copy(url = asset.url.replace("fenixvd", "someone")),
            asset.copy(digest = null), asset.copy(digest = "sha256:bad"),
            asset.copy(size = MAX_APK_BYTES + 1), asset.copy(size = 0), asset.copy(name = "other.apk"))) {
            assertNull(selectUpdate(r.copy(assets = listOf(invalid)), "1.0.0"))
        }
    }
}
