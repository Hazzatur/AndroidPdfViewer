package com.github.barteksc.pdfviewer

import android.content.Context
import com.github.barteksc.pdfviewer.source.DocumentSource
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class DecodingTask(
    private val docSource: DocumentSource,
    private val password: String? = null,
    pdfView: PDFView,
    private val pdfiumCore: PdfiumCore,
    private val firstPageIdx: Int
) {

    private var cancelled = false
    private val pdfViewRef = WeakReference(pdfView)
    private val context: Context = pdfView.context.applicationContext
    private lateinit var pdfDocument: PdfDocument
    private var pageWidth = 0
    private var pageHeight = 0

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    init {
        scope.launch {
            val error = decodeDocument()
            withContext(Dispatchers.Main) {
                if (cancelled) return@withContext
                if (error != null) {
                    pdfViewRef.get()?.loadError(error)
                } else {
                    pdfViewRef.get()?.loadComplete(pdfDocument, pageWidth, pageHeight)
                }
            }
        }
    }

    private fun decodeDocument(): Throwable? {
        return try {
            pdfDocument = docSource.createDocument(context, pdfiumCore, password)
            pdfiumCore.openPage(pdfDocument, firstPageIdx)
            pageWidth = pdfiumCore.getPageWidth(pdfDocument, firstPageIdx)
            pageHeight = pdfiumCore.getPageHeight(pdfDocument, firstPageIdx)
            null
        } catch (t: Throwable) {
            t
        }
    }

    fun cancel() {
        cancelled = true
        job.cancel()
    }
}
