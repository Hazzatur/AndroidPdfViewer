package com.github.barteksc.pdfviewer

import android.graphics.RectF
import com.github.barteksc.pdfviewer.model.PagePart
import com.github.barteksc.pdfviewer.util.Constants.Cache.CACHE_SIZE
import com.github.barteksc.pdfviewer.util.Constants.Cache.THUMBNAILS_CACHE_SIZE
import java.util.PriorityQueue

class CacheManager {

    private val passiveCache = PriorityQueue(CACHE_SIZE, PagePartComparator())
    private val activeCache = PriorityQueue(CACHE_SIZE, PagePartComparator())
    private val thumbnails = mutableListOf<PagePart>()
    private val passiveActiveLock = Any()

    fun cachePart(part: PagePart) {
        synchronized(passiveActiveLock) {
            makeAFreeSpace()
            activeCache.offer(part)
        }
    }

    fun makeANewSet() {
        synchronized(passiveActiveLock) {
            passiveCache.addAll(activeCache)
            activeCache.clear()
        }
    }

    private fun makeAFreeSpace() {
        synchronized(passiveActiveLock) {
            while ((activeCache.size + passiveCache.size) >= CACHE_SIZE && passiveCache.isNotEmpty()) {
                passiveCache.poll()?.renderedBitmap?.recycle()
            }
            while ((activeCache.size + passiveCache.size) >= CACHE_SIZE && activeCache.isNotEmpty()) {
                activeCache.poll()?.renderedBitmap?.recycle()
            }
        }
    }

    fun cacheThumbnail(part: PagePart) {
        synchronized(thumbnails) {
            if (thumbnails.size >= THUMBNAILS_CACHE_SIZE) {
                thumbnails.removeAt(0).renderedBitmap?.recycle()
            }
            thumbnails.add(part)
        }
    }

    fun upPartIfContained(
        userPage: Int, page: Int, width: Float, height: Float,
        pageRelativeBounds: RectF, toOrder: Int
    ): Boolean {
        val fakePart = PagePart(userPage, page, null, width, height, pageRelativeBounds, false, 0)
        synchronized(passiveActiveLock) {
            find(passiveCache, fakePart)?.let { found ->
                passiveCache.remove(found)
                found.cacheOrder = toOrder
                activeCache.offer(found)
                return true
            }
            return find(activeCache, fakePart) != null
        }
    }

    fun containsThumbnail(
        userPage: Int, page: Int, width: Float, height: Float,
        pageRelativeBounds: RectF
    ): Boolean {
        val fakePart = PagePart(userPage, page, null, width, height, pageRelativeBounds, true, 0)
        synchronized(thumbnails) {
            return thumbnails.any { it == fakePart }
        }
    }

    fun getPageParts(): List<PagePart> {
        synchronized(passiveActiveLock) {
            return ArrayList<PagePart>().apply {
                addAll(passiveCache)
                addAll(activeCache)
            }
        }
    }

    fun getThumbnails(): List<PagePart> {
        synchronized(thumbnails) {
            return ArrayList(thumbnails)
        }
    }

    fun recycle() {
        synchronized(passiveActiveLock) {
            passiveCache.forEach { it.renderedBitmap?.recycle() }
            passiveCache.clear()
            activeCache.forEach { it.renderedBitmap?.recycle() }
            activeCache.clear()
        }
        synchronized(thumbnails) {
            thumbnails.forEach { it.renderedBitmap?.recycle() }
            thumbnails.clear()
        }
    }

    private fun find(vector: PriorityQueue<PagePart>, fakePart: PagePart): PagePart? {
        return vector.find { it == fakePart }
    }

    inner class PagePartComparator : Comparator<PagePart> {
        override fun compare(part1: PagePart, part2: PagePart): Int {
            return part1.cacheOrder.compareTo(part2.cacheOrder)
        }
    }
}
