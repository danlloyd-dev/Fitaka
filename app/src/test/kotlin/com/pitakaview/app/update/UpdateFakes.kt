package com.pitakaview.app.update

import android.content.Intent
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Stand-ins for GitHub, Android's installer and package reader, the phone's notifications and WorkManager. */

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

object UpdateSamples {
    /** The certificate fingerprint of the owner's key (the app's real one, as written in the project's notes). */
    const val OWNERS_KEY = "de24906043bd7e8df57a56d1bc13f7630ee9cc722da502980cafad3743a35827"
    const val PREFIX = "https://github.com/danlloyd-dev/Fitaka/releases/download/"

    val installed = AppIdentity("com.pitakaview.app", versionCode = 3, versionName = "1.1.0", signers = setOf(OWNERS_KEY))

    /** Made-up APK bytes: what Android would read is said by [FakePackages.archive]. */
    fun apkBytes(seed: Int = 7) = ByteArray(50_000) { (it * seed % 253).toByte() }

    fun release(version: String, apk: ByteArray, githubChecksum: String? = sha256(apk), withSums: Boolean = true) = LatestRelease(
        AppVersion.parse(version)!!, "v$version",
        ReleaseAsset("fitaka-$version.apk", "${PREFIX}v$version/fitaka-$version.apk", apk.size.toLong(), githubChecksum),
        if (withSums) ReleaseAsset(ReleaseJson.SUMS_FILE, "${PREFIX}v$version/${ReleaseJson.SUMS_FILE}", 100, null) else null,
    )

    fun sums(version: String, apk: ByteArray) = "${sha256(apk)}  fitaka-$version.apk\n"

    /** What a good download of [version] reads as. */
    fun identity(versionCode: Long, versionName: String) = AppIdentity("com.pitakaview.app", versionCode, versionName, setOf(OWNERS_KEY))
}

class FakeReleaseSource : ReleaseSource {
    var release: LatestRelease? = null
    var reachable = true
    var sums = ""
    var apk = ByteArray(0)
    var breakDownload = false
    /** Every request, in order: "latest", "text:<name>", "download:<name>". */
    val asked = mutableListOf<String>()

    /** GitHub has [version] out, with [apk] as its file and a checksum file that matches it. */
    fun publish(version: String, apk: ByteArray, githubChecksum: String? = sha256(apk), withSums: Boolean = true) {
        this.apk = apk
        sums = UpdateSamples.sums(version, apk)
        release = UpdateSamples.release(version, apk, githubChecksum, withSums)
    }

    override suspend fun latest(): LatestRelease? {
        synchronized(asked) { asked += "latest" }
        if (!reachable) throw IOException("no internet")
        return release
    }

    override suspend fun text(asset: ReleaseAsset, maxBytes: Int): String {
        synchronized(asked) { asked += "text:${asset.name}" }
        if (!reachable) throw IOException("no internet")
        return sums
    }

    override suspend fun download(asset: ReleaseAsset, to: File, progress: (Long) -> Unit): String {
        synchronized(asked) { asked += "download:${asset.name}" }
        if (!reachable || breakDownload) throw IOException("broke off")
        to.writeBytes(apk)
        progress(apk.size / 2L)
        progress(apk.size.toLong())
        return sha256(apk)
    }
}

class FakePackages(var installed: AppIdentity = UpdateSamples.installed) : PackageReader {
    /** What Android reads the downloaded file as; null = not an app. */
    var archive: AppIdentity? = null
    val archivesRead = mutableListOf<File>()

    override fun installed() = installed

    override fun archive(file: File): AppIdentity? {
        archivesRead += file
        return archive
    }
}

class FakeInstaller : UpdateInstaller {
    var allowed = true
    /** The bytes of each APK handed to "Android", read at the moment it was handed over. */
    val handedOver = mutableListOf<ByteArray>()

    override fun allowed() = allowed
    override fun allowIntent(): Intent = Intent(ALLOW)

    override suspend fun install(apk: File) {
        handedOver += apk.readBytes()
    }

    companion object {
        const val ALLOW = "test.ALLOW_INSTALLING"
    }
}

class FakeNotifier : UpdateNotifier {
    var can = true
    val shown = mutableListOf<String>()
    var cleared = 0

    override fun canNotify() = can
    override fun show(version: AppVersion) {
        shown += "out $version"
    }

    override fun showUpdated(version: String) {
        shown += "updated $version"
    }

    override fun clear() {
        cleared++
    }
}

class FakeSchedule : UpdateSchedule {
    var lined = false
    var ensured = 0

    override suspend fun ensure() {
        ensured++
        lined = true
    }

    override fun cancel() {
        lined = false
    }
}
