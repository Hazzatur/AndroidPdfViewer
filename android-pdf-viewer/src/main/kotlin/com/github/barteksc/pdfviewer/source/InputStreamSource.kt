package com.github.barteksc.pdfviewer.source

import android.content.Context
import com.github.barteksc.pdfviewer.util.Util
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.io.IOException
import java.io.InputStream

class InputStreamSource(private val inputStream: InputStream) : DocumentSource {

    @Throws(IOException::class)
    override fun createDocument(
        context: Context,
        core: PdfiumCore,
        password: String?
    ): PdfDocument {
        return core.newDocument(Util.toByteArray(inputStream), password)
    }
}
