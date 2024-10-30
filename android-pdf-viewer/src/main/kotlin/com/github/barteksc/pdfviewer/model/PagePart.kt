package com.github.barteksc.pdfviewer.model

import android.graphics.Bitmap
import android.graphics.RectF

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
                other.width == width &&
                other.height == height &&
                other.pageRelativeBounds.left == pageRelativeBounds.left &&
                other.pageRelativeBounds.right == pageRelativeBounds.right &&
                other.pageRelativeBounds.top == pageRelativeBounds.top &&
                other.pageRelativeBounds.bottom == pageRelativeBounds.bottom
    }

    override fun hashCode(): Int {
        var result = userPage
        result = 31 * result + page
        result = 31 * result + renderedBitmap.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + pageRelativeBounds.hashCode()
        result = 31 * result + thumbnail.hashCode()
        result = 31 * result + cacheOrder
        return result
    }
}
