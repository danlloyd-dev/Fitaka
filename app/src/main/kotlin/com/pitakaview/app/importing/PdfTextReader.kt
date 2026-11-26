package com.pitakaview.app.importing

import android.content.Context
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** What came of trying to read the words of a PDF. */
sealed interface PdfText {
    /** The text of each page, in page order. */
    data class Pages(val pages: List<String>) : PdfText

    data object NeedsPassword : PdfText

    data object WrongPassword : PdfText

    /** Reading the words of a PDF is built into Android 15 and later only. */
    data object NeedsNewerAndroid : PdfText

    data object Unreadable : PdfText
}

/** Reads the words of a PDF. An interface so the screens around it can be tested with a stand-in. */
interface PdfTextReader {
    suspend fun read(bytes: ByteArray, password: String?): PdfText
}

/**
 * Android's own PDF reader (no PDF library in the app). It needs a real file, so the bytes are put in
 * the app's private cache for the moment of reading and removed again whatever happens.
 */
class AndroidPdfTextReader(private val context: Context) : PdfTextReader {
    override suspend fun read(bytes: ByteArray, password: String?): PdfText = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return@withContext PdfText.NeedsNewerAndroid
        val file = File.createTempFile("statement", ".pdf", context.cacheDir)
        try {
            file.writeBytes(bytes)
            pages(file, password)
        } catch (e: SecurityException) {
            // Android's answer to a locked file: no password given, or not the right one.
            if (password == null) PdfText.NeedsPassword else PdfText.WrongPassword
        } catch (e: IOException) {
            PdfText.Unreadable
        } finally {
            file.delete()
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun pages(file: File, password: String?): PdfText {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val params = LoadParams.Builder().apply { if (password != null) setPassword(password) }.build()
        val renderer = try {
            PdfRenderer(descriptor, params)
        } catch (e: Exception) {
            // The reader takes over the descriptor only once it has opened the file.
            descriptor.close()
            throw e
        }
        return renderer.use {
            PdfText.Pages(
                (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { page -> page.textContents.joinToString("\n") { it.text } }
                },
            )
        }
    }
}
