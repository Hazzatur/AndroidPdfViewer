package com.github.barteksc.pdfviewer.source

import android.content.Context
import android.net.Uri
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.io.IOException

class UriSource(private val uri: Uri) : DocumentSource {

    @Throws(IOException::class)
    override fun createDocument(
        context: Context,
        core: PdfiumCore,
        password: String?
    ): PdfDocument {
        context.contentResolver.openFileDescriptor(uri, "r")?.let {
            return core.newDocument(it, password)
        }
        throw IOException("Cannot open document")
    }
}
