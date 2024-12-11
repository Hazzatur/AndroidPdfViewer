package com.github.barteksc.pdfviewer.model

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.abs

data class PagePart(
    val userPage: Int,
    val page: Int,
    val renderedBitmap: Bitmap?,
    val width: Float,
    val height: Float,
    val pageRelativeBounds: RectF,
    val thumbnail: Boolean,
    var cacheOrder: Int
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PagePart) return false

        return other.page == page &&
                other.userPage == userPage &&
                abs(other.width - width) < EPSILON &&
                abs(other.height - height) < EPSILON &&
                abs(other.pageRelativeBounds.left - pageRelativeBounds.left) < EPSILON &&
                abs(other.pageRelativeBounds.right - pageRelativeBounds.right) < EPSILON &&
                abs(other.pageRelativeBounds.top - pageRelativeBounds.top) < EPSILON &&
                abs(other.pageRelativeBounds.bottom - pageRelativeBounds.bottom) < EPSILON
    }

    override fun hashCode(): Int {
        var result = userPage
        result = 31 * result + page
        result = 31 * result + width.hashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + pageRelativeBounds.left.hashCode()
        result = 31 * result + pageRelativeBounds.right.hashCode()
        result = 31 * result + pageRelativeBounds.top.hashCode()
        result = 31 * result + pageRelativeBounds.bottom.hashCode()
        result = 31 * result + thumbnail.hashCode()
        result = 31 * result + cacheOrder
        return result
    }

    companion object {
        private const val EPSILON = 0.0001f
    }
}
