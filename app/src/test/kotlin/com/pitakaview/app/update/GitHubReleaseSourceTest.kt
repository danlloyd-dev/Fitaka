package com.pitakaview.app.update

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.security.MessageDigest

/**
 * The real requests, against a small server on this computer that answers as GitHub does: the API's answer,
 * a release file behind a redirect (GitHub sends them from another address), and the ways a download goes wrong.
 */
@RunWith(RobolectricTestRunner::class)
class GitHubReleaseSourceTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: HttpServer
    private lateinit var base: String
    private val apk = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val asked = mutableListOf<HttpExchange>()
    private var latestCode = 200

    private val source get() = GitHubReleaseSource(latestUrl = "$base/repos/danlloyd-dev/Fitaka/releases/latest", downloadPrefix = "$base/download/")

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        base = "http://127.0.0.1:${server.address.port}"
        val answer = File("src/test/resources/update/latest-release-v1.0.0.json").readText()
            .replace("https://github.com/danlloyd-dev/Fitaka/releases/download/", "$base/download/")
            .replace("\"size\": 2864829", "\"size\": ${apk.size}")
        server.createContext("/repos/danlloyd-dev/Fitaka/releases/latest") { send(it, latestCode, answer.toByteArray()) }
        // Like GitHub: the file's address sends the caller on to where the bytes are.
        server.createContext("/download/v1.0.0/fitaka-1.0.0.apk") { it.responseHeaders.add("Location", "$base/assets/apk"); send(it, 302, ByteArray(0)) }
        server.createContext("/assets/apk") { send(it, 200, apk) }
        server.createContext("/download/short.apk") { send(it, 200, apk.copyOf(1000)) }
        server.createContext("/download/SHA256SUMS.txt") { send(it, 200, "${sha256(apk)}  fitaka-1.0.0.apk\n".toByteArray()) }
        server.createContext("/download/missing.apk") { send(it, 404, ByteArray(0)) }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun send(exchange: HttpExchange, code: Int, body: ByteArray) {
        synchronized(asked) { asked += exchange }
        exchange.sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun theLatestReleaseIsReadFromGithubsAnswerAndTheAppNamesItself() = runBlocking {
        val release = source.latest()!!
        assertEquals("v1.0.0", release.tag)
        assertEquals("$base/download/v1.0.0/fitaka-1.0.0.apk", release.apk.url)
        val request = asked.single().requestHeaders
        assertEquals("application/vnd.github+json", request.getFirst("Accept"))
        assertEquals(GitHubReleaseSource.API_VERSION, request.getFirst("X-GitHub-Api-Version"))
        assertEquals("Fitaka-update-check", request.getFirst("User-Agent"))
    }

    @Test
    fun aRepositoryWithNoReleaseYetHasNothingToOffer() = runBlocking {
        latestCode = 404
        assertNull(source.latest())
    }

    /** A limit reached, or GitHub down: no answer, which is not the same as "nothing new". */
    @Test
    fun anyOtherAnswerIsNoAnswer() = runBlocking {
        for (code in listOf(403, 429, 500)) {
            latestCode = code
            try {
                source.latest()
                fail("answer $code was taken as an answer")
            } catch (expected: IOException) {
            }
        }
    }

    @Test
    fun theApkIsDownloadedThroughTheRedirectWithItsChecksumAndProgress() = runBlocking {
        val release = source.latest()!!
        val to = temp.newFile("fitaka.apk")
        val seen = mutableListOf<Long>()
        val got = source.download(release.apk, to) { seen += it }
        assertEquals(sha256(apk), got)
        assertTrue(to.readBytes().contentEquals(apk))
        assertEquals(apk.size.toLong(), seen.last())
        assertTrue("progress only goes up", seen.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun aDownloadThatStopsShortIsRefused() = runBlocking {
        val short = ReleaseAsset("short.apk", "$base/download/short.apk", size = apk.size.toLong(), sha256 = null)
        try {
            source.download(short, temp.newFile()) { }
            fail("a short download was taken")
        } catch (expected: IOException) {
        }
    }

    @Test
    fun aMissingFileIsRefused() = runBlocking {
        try {
            source.download(ReleaseAsset("missing.apk", "$base/download/missing.apk", 10, null), temp.newFile()) { }
            fail("a 404 was taken as a file")
        } catch (expected: IOException) {
        }
    }

    @Test
    fun nothingOutsideTheReleaseDownloadsIsEverFetched() = runBlocking {
        try {
            source.download(ReleaseAsset("x.apk", "$base/assets/apk", apk.size.toLong(), null), temp.newFile()) { }
            fail("fetched a file from outside the release downloads")
        } catch (expected: IOException) {
        }
        assertTrue("not even asked for", asked.isEmpty())
    }

    @Test
    fun theChecksumFileIsReadAsTextAndATooLongOneRefused() = runBlocking {
        val sums = ReleaseAsset(ReleaseJson.SUMS_FILE, "$base/download/SHA256SUMS.txt", 100, null)
        assertEquals(sha256(apk), Sha256Sums.parse(source.text(sums, 1024))["fitaka-1.0.0.apk"])
        try {
            source.text(sums, maxBytes = 10)
            fail("a longer file than allowed was read")
        } catch (expected: IOException) {
        }
    }
}
