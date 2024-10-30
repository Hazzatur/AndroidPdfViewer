package com.github.barteksc.pdfviewer

import android.graphics.RectF
import android.util.Pair
import com.github.barteksc.pdfviewer.util.Constants
import com.github.barteksc.pdfviewer.util.MathUtils
import kotlin.math.abs

class PagesLoader(private val pdfView: PDFView) {

    private val thumbnailRect = RectF(0f, 0f, 1f, 1f)

    private var cacheOrder = 0
    private var scaledHeight = 0f
    private var scaledWidth = 0f
    private lateinit var colsRows: Pair<Int, Int>
    private var xOffset = 0f
    private var yOffset = 0f
    private var rowHeight = 0f
    private var colWidth = 0f
    private var pageRelativePartWidth = 0f
    private var pageRelativePartHeight = 0f
    private var partRenderWidth = 0f
    private var partRenderHeight = 0f
    private var thumbnailWidth = 0
    private var thumbnailHeight = 0
    private var scaledSpacingPx = 0f

    private fun getPageColsRows(): Pair<Int, Int> {
        val ratioX = 1f / pdfView.optimalPageWidth
        val ratioY = 1f / pdfView.optimalPageHeight
        val partHeight = Constants.PART_SIZE * ratioY / pdfView.zoom
        val partWidth = Constants.PART_SIZE * ratioX / pdfView.zoom
        val nbRows = MathUtils.ceil(1f / partHeight)
        val nbCols = MathUtils.ceil(1f / partWidth)
        return Pair(nbCols, nbRows)
    }

    private fun documentPage(userPage: Int): Int {
        val originalPages = pdfView.originalUserPages
        val docPage = originalPages.getOrNull(userPage) ?: userPage
        return if (docPage in 0 until pdfView.documentPageCount) docPage else -1
    }

    private fun getPageAndCoordsByOffset(offset: Float, endOffset: Boolean): Holder {
        val holder = Holder()
        val fixOffset = -MathUtils.max(offset, 0f)
        val row: Float
        val col: Float

        if (pdfView.swipeVertical) {
            holder.page = MathUtils.floor(fixOffset / (scaledHeight + scaledSpacingPx))
            row = abs(fixOffset - (scaledHeight + scaledSpacingPx) * holder.page) / rowHeight
            col = xOffset / colWidth
        } else {
            holder.page = MathUtils.floor(fixOffset / (scaledWidth + scaledSpacingPx))
            col = abs(fixOffset - (scaledWidth + scaledSpacingPx) * holder.page) / colWidth
            row = yOffset / rowHeight
        }

        holder.row = if (endOffset) MathUtils.ceil(row) else MathUtils.floor(row)
        holder.col = if (endOffset) MathUtils.ceil(col) else MathUtils.floor(col)
        return holder
    }

    private fun loadThumbnail(userPage: Int, documentPage: Int) {
        pdfView.cacheManager?.let {
            if (!it.containsThumbnail(
                    userPage,
                    documentPage,
                    thumbnailWidth.toFloat(),
                    thumbnailHeight.toFloat(),
                    thumbnailRect
                )
            ) {
                pdfView.renderingHandler?.addRenderingTask(
                    userPage, documentPage,
                    thumbnailWidth.toFloat(), thumbnailHeight.toFloat(), thumbnailRect,
                    true, 0, pdfView.bestQuality, pdfView.annotationRendering
                )
            }
        }
    }

    private fun loadRelative(number: Int, nbOfPartsLoadable: Int, belowView: Boolean): Int {
        var loaded = 0
        val newOffset = if (pdfView.swipeVertical) {
            pdfView.currentYOffset - (if (belowView) pdfView.height else 0) - rowHeight * number
        } else {
            pdfView.currentXOffset - (if (belowView) pdfView.width else 0) - colWidth * number
        }

        val holder = getPageAndCoordsByOffset(newOffset, false)
        val documentPage = documentPage(holder.page)
        if (documentPage < 0) return 0

        loadThumbnail(holder.page, documentPage)

        if (pdfView.swipeVertical) {
            val firstCol = MathUtils.min(MathUtils.floor(xOffset / colWidth) - 1, 0)
            val lastCol = MathUtils.max(
                MathUtils.ceil((xOffset + pdfView.width) / colWidth) + 1,
                colsRows.first
            )
            for (col in firstCol..lastCol) {
                if (loadCell(holder.page, documentPage, holder.row, col)) {
                    loaded++
                }
                if (loaded >= nbOfPartsLoadable) return loaded
            }
        } else {
            val firstRow = MathUtils.min(MathUtils.floor(yOffset / rowHeight) - 1, 0)
            val lastRow = MathUtils.max(
                MathUtils.ceil((yOffset + pdfView.height) / rowHeight) + 1,
                colsRows.second
            )
            for (row in firstRow..lastRow) {
                if (loadCell(holder.page, documentPage, row, holder.col)) {
                    loaded++
                }
                if (loaded >= nbOfPartsLoadable) return loaded
            }
        }
        return loaded
    }

    private fun loadVisible(): Int {
        var parts = 0
        val firstHolder: Holder
        val lastHolder: Holder

        if (pdfView.swipeVertical) {
            firstHolder = getPageAndCoordsByOffset(pdfView.currentYOffset, false)
            lastHolder = getPageAndCoordsByOffset(pdfView.currentYOffset - pdfView.height + 1, true)
            val visibleRows = calculateVisibleSpan(firstHolder, lastHolder, colsRows.second)
            for (i in 0 until visibleRows) {
                if (parts >= Constants.Cache.CACHE_SIZE) break
                parts += loadRelative(i, Constants.Cache.CACHE_SIZE - parts, false)
            }
        } else {
            firstHolder = getPageAndCoordsByOffset(pdfView.currentXOffset, false)
            lastHolder = getPageAndCoordsByOffset(pdfView.currentXOffset - pdfView.width + 1, true)
            val visibleCols = calculateVisibleSpan(firstHolder, lastHolder, colsRows.first)
            for (i in 0 until visibleCols) {
                if (parts >= Constants.Cache.CACHE_SIZE) break
                parts += loadRelative(i, Constants.Cache.CACHE_SIZE - parts, false)
            }
        }

        listOf(firstHolder.page - 1, firstHolder.page + 1).forEach { page ->
            documentPage(page).takeIf { it >= 0 }?.let { loadThumbnail(page, it) }
        }

        return parts
    }

    private fun calculateVisibleSpan(first: Holder, last: Holder, dimension: Int): Int {
        return if (first.page == last.page) last.row - first.row + 1
        else dimension - first.row + (last.page - first.page - 1) * dimension + last.row + 1
    }

    private fun loadCell(userPage: Int, documentPage: Int, row: Int, col: Int): Boolean {
        val relX = pageRelativePartWidth * col
        val relY = pageRelativePartHeight * row
        var relWidth = pageRelativePartWidth
        var relHeight = pageRelativePartHeight
        var renderWidth = partRenderWidth
        var renderHeight = partRenderHeight

        if (relX + relWidth > 1) relWidth = 1 - relX
        if (relY + relHeight > 1) relHeight = 1 - relY

        renderWidth *= relWidth
        renderHeight *= relHeight
        val pageRelativeBounds = RectF(relX, relY, relX + relWidth, relY + relHeight)

        if (renderWidth > 0 && renderHeight > 0) {
            pdfView.cacheManager?.let {
                if (!it.upPartIfContained(
                        userPage,
                        documentPage,
                        renderWidth,
                        renderHeight,
                        pageRelativeBounds,
                        cacheOrder
                    )
                ) {
                    pdfView.renderingHandler?.addRenderingTask(
                        userPage, documentPage, renderWidth, renderHeight, pageRelativeBounds,
                        false, cacheOrder, pdfView.bestQuality, pdfView.annotationRendering
                    )
                }
            }
            cacheOrder++
            return true
        }
        return false
    }

    fun loadPages() {
        scaledHeight = pdfView.toCurrentScale(pdfView.optimalPageHeight)
        scaledWidth = pdfView.toCurrentScale(pdfView.optimalPageWidth)
        thumbnailWidth = (pdfView.optimalPageWidth * Constants.THUMBNAIL_RATIO).toInt()
        thumbnailHeight = (pdfView.optimalPageHeight * Constants.THUMBNAIL_RATIO).toInt()
        colsRows = getPageColsRows()
        xOffset = -MathUtils.max(pdfView.currentXOffset, 0f)
        yOffset = -MathUtils.max(pdfView.currentYOffset, 0f)
        rowHeight = scaledHeight / colsRows.second
        colWidth = scaledWidth / colsRows.first
        pageRelativePartWidth = 1f / colsRows.first
        pageRelativePartHeight = 1f / colsRows.second
        partRenderWidth = Constants.PART_SIZE / pageRelativePartWidth
        partRenderHeight = Constants.PART_SIZE / pageRelativePartHeight
        cacheOrder = 1
        scaledSpacingPx =
            pdfView.toCurrentScale(pdfView.spacingPx.toFloat()) - pdfView.toCurrentScale(pdfView.spacingPx.toFloat()) / pdfView.getPageCount()

        var loaded = loadVisible()
        when (pdfView.scrollDir) {
            PDFView.ScrollDir.END -> repeat(Constants.PRELOAD_COUNT) {
                loaded += loadRelative(
                    it,
                    loaded,
                    true
                )
            }

            else -> repeat(Constants.PRELOAD_COUNT) { loaded += loadRelative(-it, loaded, false) }
        }
    }

    private data class Holder(
        var page: Int = 0,
        var row: Int = 0,
        var col: Int = 0
    )
}
