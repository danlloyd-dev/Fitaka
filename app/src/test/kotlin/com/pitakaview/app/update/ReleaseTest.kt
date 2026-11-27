package com.pitakaview.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Reading GitHub's answer about a release. The sample is GitHub's real answer for v1.0.0 (fetched 2026-11-27,
 * the uploader's details and the notes cut out), so a change in GitHub's shape shows up here first.
 * Robolectric, for the phone's own JSON reader.
 */
@RunWith(RobolectricTestRunner::class)
class ReleaseTest {
    private val github = File("src/test/resources/update/latest-release-v1.0.0.json").readText()
    private val prefix = "https://github.com/danlloyd-dev/Fitaka/releases/download/"

    private fun v(text: String) = requireNotNull(AppVersion.parse(text)) { text }

    @Test
    fun versionsCompareAsNumbersPartByPart() {
        assertTrue(v("1.0.10") > v("1.0.9"))
        assertTrue(v("v1.1.0") > v("1.0.0"))
        assertTrue(v("2.0") > v("1.9.9"))
        assertEquals(v("1.1"), v("1.1.0"))
        assertEquals("1.1.0", v("v1.1.0").toString())
    }

    @Test
    fun aTagThatIsNotAPlainVersionIsNone() {
        for (tag in listOf("v1.2.0-beta", "latest", "", "1..2", "v", "1.2.3.4.5")) assertNull(tag, AppVersion.parse(tag))
    }

    @Test
    fun githubsRealAnswerGivesTheApkItsSizeAndBothChecksums() {
        val release = requireNotNull(ReleaseJson.fromGitHub(github, prefix)) { "GitHub's real answer was not read as a release" }
        assertEquals("v1.0.0", release.tag)
        assertEquals(v("1.0.0"), release.version)
        assertEquals("fitaka-1.0.0.apk", release.apk.name)
        assertEquals(2_864_829L, release.apk.size)
        assertEquals("1e2336a9757c9cc67da1b57508bc3bd897a599094252ce3d3b9e4ac4eff303c6", release.apk.sha256)
        assertEquals(prefix + "v1.0.0/fitaka-1.0.0.apk", release.apk.url)
        assertEquals(ReleaseJson.SUMS_FILE, release.sums?.name)
    }

    @Test
    fun draftsAndPreReleasesAreNeverOffered() {
        assertNull(ReleaseJson.fromGitHub(github.replace("\"prerelease\": false", "\"prerelease\": true"), prefix))
        assertNull(ReleaseJson.fromGitHub(github.replace("\"draft\": false", "\"draft\": true"), prefix))
        assertNull(ReleaseJson.fromGitHub(github.replace("\"tag_name\": \"v1.0.0\"", "\"tag_name\": \"v1.1.0-rc1\""), prefix))
    }

    /** Nothing is ever downloaded from outside this repository's releases, whatever an answer says. */
    @Test
    fun filesFromAnywhereElseAreLeftOut() {
        val elsewhere = github.replace("https://github.com/danlloyd-dev/Fitaka/releases/download/v1.0.0/fitaka-1.0.0.apk", "https://example.com/fitaka-1.0.0.apk")
        assertNull("no APK of this repository: nothing to offer", ReleaseJson.fromGitHub(elsewhere, prefix))
    }

    @Test
    fun aFileStillOnItsWayUpIsLeftOut() {
        val apkAt = github.indexOf("\"name\": \"fitaka-1.0.0.apk\"")
        val uploading = github.substring(0, apkAt) + github.substring(apkAt).replaceFirst("\"state\": \"uploaded\"", "\"state\": \"starter\"")
        assertNull(ReleaseJson.fromGitHub(uploading, prefix))
    }

    @Test
    fun withoutGithubsChecksumTheApkStillComesButWithNone() {
        val release = ReleaseJson.fromGitHub(github.replace("sha256:1e2336", "md5:1e2336"), prefix)!!
        assertNull(release.apk.sha256)
    }

    @Test
    fun anAnswerThatIsNotJsonIsNoRelease() {
        assertNull(ReleaseJson.fromGitHub("<html>rate limited</html>", prefix))
        assertNull(ReleaseJson.fromGitHub("{}", prefix))
    }

    @Test
    fun aFoundReleaseIsKeptAndReadBackTheSame() {
        val release = ReleaseJson.fromGitHub(github, prefix)!!
        assertEquals(release, ReleaseJson.fromStored(ReleaseJson.toStored(release)))
        assertNull(ReleaseJson.fromStored("not kept by this app"))
    }

    /** In `sha256sum`'s own format, as the release's SHA256SUMS.txt is written (two spaces, or a star for binary). */
    @Test
    fun theChecksumFileIsReadByFileName() {
        val sums = Sha256Sums.parse(
            "1E2336A9757C9CC67DA1B57508BC3BD897A599094252CE3D3B9E4AC4EFF303C6  fitaka-1.0.0.apk\r\n" +
                "0cca5d450b9918dda250d19e8e0e1c30022b9526472b33e6c334cddb49c8e58b *fitaka-1.0.0.aab\n" +
                "not a line\n",
        )
        assertEquals(
            mapOf(
                "fitaka-1.0.0.apk" to "1e2336a9757c9cc67da1b57508bc3bd897a599094252ce3d3b9e4ac4eff303c6",
                "fitaka-1.0.0.aab" to "0cca5d450b9918dda250d19e8e0e1c30022b9526472b33e6c334cddb49c8e58b",
            ),
            sums,
        )
    }

    @Test
    fun sizesAreShownInMegabytes() {
        assertEquals("2.9 MB", UpdateText.megabytes(2_864_829))
    }
}
