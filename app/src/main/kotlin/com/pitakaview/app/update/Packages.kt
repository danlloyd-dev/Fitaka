package com.pitakaview.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** An app as Android describes it: its id, its version and the keys it is signed with (SHA-256 of each certificate). */
data class AppIdentity(val packageName: String, val versionCode: Long, val versionName: String, val signers: Set<String>)

/** What Android says about the installed app and about a downloaded APK. An interface so tests can say it instead. */
interface PackageReader {
    fun installed(): AppIdentity

    /** What the APK file [file] is, or null when Android cannot read it as an app. */
    fun archive(file: File): AppIdentity?
}

class AndroidPackageReader(private val context: Context) : PackageReader {
    @Suppress("DEPRECATION") // GET_SIGNATURES only before Android 9, where GET_SIGNING_CERTIFICATES does not exist.
    private val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    override fun installed(): AppIdentity = identity(context.packageManager.getPackageInfo(context.packageName, flags))

    override fun archive(file: File): AppIdentity? = context.packageManager.getPackageArchiveInfo(file.path, flags)?.let(::identity)

    private fun identity(info: PackageInfo) = AppIdentity(
        packageName = info.packageName,
        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong(),
        versionName = info.versionName.orEmpty(),
        signers = signatures(info).mapTo(HashSet()) { sha256(it.toByteArray()) },
    )

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo?.apkContentsSigners.orEmpty().toList() else info.signatures.orEmpty().toList()

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
