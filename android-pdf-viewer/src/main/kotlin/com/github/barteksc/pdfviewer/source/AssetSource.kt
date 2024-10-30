package com.github.barteksc.pdfviewer.source

import android.content.Context
import android.os.ParcelFileDescriptor
import com.github.barteksc.pdfviewer.util.FileUtils
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.io.File
import java.io.IOException

class AssetSource(private val assetName: String) : DocumentSource {

    @Throws(IOException::class)
    override fun createDocument(
        context: Context,
        core: PdfiumCore,
        password: String?
    ): PdfDocument {
        val file: File = FileUtils.fileFromAsset(context, assetName)
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return core.newDocument(pfd, password)
    }
}
