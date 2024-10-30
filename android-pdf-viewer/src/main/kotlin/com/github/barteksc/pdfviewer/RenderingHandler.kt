package com.github.barteksc.pdfviewer

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.SparseBooleanArray
import com.github.barteksc.pdfviewer.RenderingHandler.RenderingTask
import com.github.barteksc.pdfviewer.exception.PageRenderingException
import com.github.barteksc.pdfviewer.model.PagePart
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore

/**
 * A [Handler] that processes incoming [RenderingTask] messages
 * and alerts [PDFView.onBitmapRendered] when a portion of the
 * PDF is ready to render.
 */
class RenderingHandler(
    looper: Looper,
    private val pdfView: PDFView,
    private val pdfiumCore: PdfiumCore,
    private val pdfDocument: PdfDocument
) : Handler(looper) {

    private val openedPages = SparseBooleanArray()
    private val renderBounds = RectF()
    private val roundedRenderBounds = Rect()
    private val renderMatrix = Matrix()
    private var running = false

    fun addRenderingTask(
        userPage: Int, page: Int, width: Float, height: Float,
        bounds: RectF, thumbnail: Boolean, cacheOrder: Int,
        bestQuality: Boolean, annotationRendering: Boolean
    ) {
        val task = RenderingTask(
            width,
            height,
            bounds,
            userPage,
            page,
            thumbnail,
            cacheOrder,
            bestQuality,
            annotationRendering
        )
        val msg = obtainMessage(MSG_RENDER_TASK, task)
        sendMessage(msg)
    }

    override fun handleMessage(message: Message) {
        val task = message.obj as RenderingTask
        try {
            val part = proceed(task)
            if (part != null) {
                if (running) {
                    pdfView.post { pdfView.onBitmapRendered(part) }
                } else {
                    part.renderedBitmap?.recycle()
                }
            }
        } catch (ex: PageRenderingException) {
            pdfView.post { pdfView.onPageError(ex) }
        }
    }

    @Throws(PageRenderingException::class)
    private fun proceed(renderingTask: RenderingTask): PagePart? {
        if (openedPages.indexOfKey(renderingTask.page) < 0) {
            try {
                pdfiumCore.openPage(pdfDocument, renderingTask.page)
                openedPages.put(renderingTask.page, true)
            } catch (e: Exception) {
                openedPages.put(renderingTask.page, false)
                throw PageRenderingException(renderingTask.page, e)
            }
        }

        val w = renderingTask.width.toInt()
        val h = renderingTask.height.toInt()
        val render: Bitmap = try {
            Bitmap.createBitmap(
                w,
                h,
                if (renderingTask.bestQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
            )
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            return null
        }

        calculateBounds(w, h, renderingTask.bounds)

        if (openedPages[renderingTask.page]) {
            pdfiumCore.renderPageBitmap(
                pdfDocument, render, renderingTask.page,
                roundedRenderBounds.left, roundedRenderBounds.top,
                roundedRenderBounds.width(), roundedRenderBounds.height(),
                renderingTask.annotationRendering
            )
        } else {
            render.eraseColor(pdfView.invalidPageColor)
        }

        return PagePart(
            renderingTask.userPage, renderingTask.page, render,
            renderingTask.width, renderingTask.height,
            renderingTask.bounds, renderingTask.thumbnail,
            renderingTask.cacheOrder
        )
    }

    private fun calculateBounds(width: Int, height: Int, pageSliceBounds: RectF) {
        renderMatrix.reset()
        renderMatrix.postTranslate(-pageSliceBounds.left * width, -pageSliceBounds.top * height)
        renderMatrix.postScale(1 / pageSliceBounds.width(), 1 / pageSliceBounds.height())
        renderBounds.set(0f, 0f, width.toFloat(), height.toFloat())
        renderMatrix.mapRect(renderBounds)
        renderBounds.round(roundedRenderBounds)
    }

    fun stop() {
        running = false
    }

    fun start() {
        running = true
    }

    private data class RenderingTask(
        val width: Float,
        val height: Float,
        val bounds: RectF,
        val userPage: Int,
        val page: Int,
        val thumbnail: Boolean,
        val cacheOrder: Int,
        val bestQuality: Boolean,
        val annotationRendering: Boolean
    )

    companion object {
        const val MSG_RENDER_TASK = 1
        private val TAG = RenderingHandler::class.java.name
    }
}
