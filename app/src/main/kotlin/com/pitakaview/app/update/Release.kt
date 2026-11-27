package com.pitakaview.app.update

import org.json.JSONException
import org.json.JSONObject

/**
 * A version such as 1.1.0, read from the app ("1.1.0") or from a release's tag ("v1.1.0"). Compared part by
 * part as numbers, so 1.0.10 is newer than 1.0.9. A tag with anything after the numbers ("v1.2.0-beta") is not
 * a version here: such a release is never offered.
 */
class AppVersion private constructor(private val parts: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val difference = parts.getOrElse(i) { 0 }.compareTo(other.parts.getOrElse(i) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }

    override fun equals(other: Any?) = other is AppVersion && compareTo(other) == 0
    override fun hashCode() = parts.dropLastWhile { it == 0 }.hashCode()
    override fun toString() = parts.joinToString(".")

    companion object {
        private val SHAPE = Regex("""[vV]?(\d{1,6}(?:\.\d{1,6}){0,3})""")

        fun parse(text: String): AppVersion? =
            SHAPE.matchEntire(text.trim())?.let { match -> AppVersion(match.groupValues[1].split('.').map(String::toInt)) }
    }
}

/** One file attached to a release. [sha256] is GitHub's own checksum of it (64 hex digits), when GitHub gives one. */
data class ReleaseAsset(val name: String, val url: String, val size: Long, val sha256: String?)

/** The newest release on GitHub, cut down to what an update needs: its version, its APK and its list of checksums. */
data class LatestRelease(val version: AppVersion, val tag: String, val apk: ReleaseAsset, val sums: ReleaseAsset?)

/** Reads GitHub's answer about a release, and keeps a found release in settings in a small form of its own. */
object ReleaseJson {
    /** The file of checksums every release carries beside the APK, in `sha256sum`'s format. */
    const val SUMS_FILE = "SHA256SUMS.txt"

    private val HEX64 = Regex("[0-9a-f]{64}")

    /**
     * The release in GitHub's answer to "the latest release", or null when it is not one this app can update
     * to: a draft or a pre-release, a tag that is not a plain version, or no single APK to download.
     *
     * Only files under [downloadPrefix] (this repository's own release downloads) are taken, so the app
     * never downloads from anywhere else, whatever an answer might say.
     */
    fun fromGitHub(json: String, downloadPrefix: String): LatestRelease? = try {
        val release = JSONObject(json)
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
            null
        } else {
            val tag = release.optString("tag_name")
            val version = AppVersion.parse(tag)
            val assets = release.optJSONArray("assets")
            val files = (0 until (assets?.length() ?: 0)).mapNotNull { i -> assets!!.optJSONObject(i)?.let { asset(it, downloadPrefix) } }
            val apk = files.firstOrNull { it.name == "fitaka-$version.apk" } ?: files.filter { it.name.endsWith(".apk", ignoreCase = true) }.singleOrNull()
            if (version == null || apk == null) null else LatestRelease(version, tag, apk, files.firstOrNull { it.name == SUMS_FILE })
        }
    } catch (e: JSONException) {
        null
    }

    private fun asset(json: JSONObject, downloadPrefix: String): ReleaseAsset? {
        val url = json.optString("browser_download_url")
        // "uploaded" is a file that is all there; GitHub also lists ones still on their way up.
        if (json.optString("state") != "uploaded" || !url.startsWith(downloadPrefix)) return null
        val digest = json.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase()
        return ReleaseAsset(json.optString("name"), url, json.optLong("size", -1), digest?.takeIf { HEX64.matches(it) })
    }

    /** The found release, as kept in settings until it is installed. */
    fun toStored(release: LatestRelease): String = JSONObject()
        .put("tag", release.tag)
        .put("apk", stored(release.apk))
        .apply { release.sums?.let { put("sums", stored(it)) } }
        .toString()

    /** A release kept by [toStored]; null for anything that does not read as one. */
    fun fromStored(text: String): LatestRelease? = try {
        val json = JSONObject(text)
        val tag = json.getString("tag")
        val version = AppVersion.parse(tag)
        val apk = storedAsset(json.getJSONObject("apk"))
        version?.let { LatestRelease(it, tag, apk, json.optJSONObject("sums")?.let(::storedAsset)) }
    } catch (e: JSONException) {
        null
    }

    private fun stored(asset: ReleaseAsset) = JSONObject()
        .put("name", asset.name).put("url", asset.url).put("size", asset.size)
        .apply { asset.sha256?.let { put("sha256", it) } }

    private fun storedAsset(json: JSONObject) = ReleaseAsset(
        json.getString("name"), json.getString("url"), json.getLong("size"), json.optString("sha256").takeIf { HEX64.matches(it) },
    )
}

/** The lines of a `SHA256SUMS.txt` ("<64 hex digits>  <file name>"), as file name to checksum. */
object Sha256Sums {
    private val LINE = Regex("""^([0-9a-fA-F]{64}) [ *](.+)$""")

    fun parse(text: String): Map<String, String> = text.lineSequence()
        .mapNotNull { LINE.matchEntire(it.trimEnd('\r')) }
        .associate { it.groupValues[2].trim() to it.groupValues[1].lowercase() }
}
