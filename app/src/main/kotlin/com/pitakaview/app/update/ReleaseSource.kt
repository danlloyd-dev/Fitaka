package com.pitakaview.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Where releases come from. An interface so the rules around updates can be tested without the internet. */
interface ReleaseSource {
    /** The newest published release, or null when it is not one to update to. Throws [IOException] when the answer does not come. */
    suspend fun latest(): LatestRelease?

    /** A small text file of a release (its checksums), at most [maxBytes] long. */
    suspend fun text(asset: ReleaseAsset, maxBytes: Int): String

    /**
     * Downloads [asset] into [to], telling [progress] how many bytes have arrived, and returns the SHA-256 of
     * what arrived (64 hex digits). Throws [IOException] when the download breaks off or is not the size the
     * release says it is.
     */
    suspend fun download(asset: ReleaseAsset, to: File, progress: (Long) -> Unit): String
}

/**
 * The app's own repository on GitHub: one request to GitHub's API for the latest release (a few KB), and,
 * only when the owner taps Update, the checksums and the APK. No account and nothing about the owner is sent:
 * GitHub sees a request from this phone's internet address for a public page.
 */
class GitHubReleaseSource(
    private val latestUrl: String = "https://api.github.com/repos/$REPO/releases/latest",
    private val downloadPrefix: String = "https://github.com/$REPO/releases/download/",
) : ReleaseSource {

    override suspend fun latest(): LatestRelease? = withContext(Dispatchers.IO) {
        val connection = open(latestUrl)
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", API_VERSION)
        try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> ReleaseJson.fromGitHub(connection.inputStream.use { it.readAtMost(MAX_ANSWER) }.decodeToString(), downloadPrefix)
                // A repository with no published release yet.
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("GitHub answered $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun text(asset: ReleaseAsset, maxBytes: Int): String = withContext(Dispatchers.IO) {
        val connection = openAsset(asset)
        try {
            connection.inputStream.use { it.readAtMost(maxBytes) }.decodeToString()
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun download(asset: ReleaseAsset, to: File, progress: (Long) -> Unit): String = withContext(Dispatchers.IO) {
        val connection = openAsset(asset)
        try {
            val announced = connection.contentLengthLong
            if (announced >= 0 && announced != asset.size) throw IOException("GitHub is sending $announced bytes, the release says ${asset.size}")
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input ->
                to.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > asset.size) throw IOException("more arrived than the release says")
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        progress(total)
                    }
                }
            }
            if (total != asset.size) throw IOException("the download stopped at $total of ${asset.size} bytes")
            digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            connection.disconnect()
        }
    }

    private fun openAsset(asset: ReleaseAsset): HttpURLConnection {
        // Fixed when the release was read; checked again here so nothing else can ever be fetched.
        if (!asset.url.startsWith(downloadPrefix)) throw IOException("not one of this app's release files")
        val connection = open(asset.url)
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw IOException("GitHub answered $code for ${asset.name}")
        }
        return connection
    }

    // GitHub sends release files from another address of its own (a redirect), which this follows.
    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", USER_AGENT)
    }

    companion object {
        const val REPO = "danlloyd-dev/Fitaka"

        /** GitHub's API answers in the shape of this version (the one it picks when asked for none, checked on 2026-11-27). */
        const val API_VERSION = "2022-11-28"

        /** GitHub asks every caller to name itself; this names the app and nothing about the phone or the owner. */
        const val USER_AGENT = "Fitaka-update-check"

        /** The answer about one release is a few KB; anything far larger is not that answer. */
        private const val MAX_ANSWER = 1 shl 20
    }
}

/** Reads all of a stream that is at most [max] bytes long, and refuses a longer one. */
internal fun InputStream.readAtMost(max: Int): ByteArray {
    // By hand: InputStream.readNBytes is there only from Android 13, and the app runs from Android 8.
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) return out.toByteArray()
        if (out.size() + read > max) throw IOException("longer than $max bytes")
        out.write(buffer, 0, read)
    }
}
