package com.github.barteksc.pdfviewer

import android.annotation.SuppressLint
import android.graphics.PointF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.github.barteksc.pdfviewer.util.Constants.Pinch.MAXIMUM_ZOOM
import com.github.barteksc.pdfviewer.util.Constants.Pinch.MINIMUM_ZOOM

/**
 * This Manager takes care of moving the PDFView,
 * setting its zoom to track user actions.
 */
@SuppressLint("ClickableViewAccessibility")
class DragPinchManager(
    private val pdfView: PDFView,
    private val animationManager: AnimationManager
) : GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener,
    ScaleGestureDetector.OnScaleGestureListener, View.OnTouchListener {

    private val gestureDetector = GestureDetector(pdfView.context, this)
    private val scaleGestureDetector = ScaleGestureDetector(pdfView.context, this)
    private var isSwipeEnabled = false
    private var swipeVertical = pdfView.swipeVertical
    private var scrolling = false
    private var scaling = false

    init {
        pdfView.setOnTouchListener(this)
    }

    fun enableDoubleTap(enableDoubleTap: Boolean) {
        gestureDetector.setOnDoubleTapListener(if (enableDoubleTap) this else null)
    }

    private val isZooming: Boolean
        get() = pdfView.isZooming

    private fun isPageChange(distance: Float): Boolean {
        return kotlin.math.abs(distance) > kotlin.math.abs(
            pdfView.toCurrentScale(
                if (swipeVertical) pdfView.optimalPageHeight else pdfView.optimalPageWidth
            ) / 2
        )
    }

    fun setSwipeEnabled(isSwipeEnabled: Boolean) {
        this.isSwipeEnabled = isSwipeEnabled
    }

    fun setSwipeVertical(swipeVertical: Boolean) {
        this.swipeVertical = swipeVertical
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        val onTapListener = pdfView.onTapListener
        if (onTapListener == null || !onTapListener.onTap(e)) {
            pdfView.scrollHandle?.let { ps ->
                if (!pdfView.documentFitsView()) {
                    if (!ps.shown()) ps.show() else ps.hide()
                }
            }
        }
        pdfView.performClick()
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        when {
            pdfView.zoom < pdfView.midZoom -> pdfView.zoomWithAnimation(e.x, e.y, pdfView.midZoom)
            pdfView.zoom < pdfView.maxZoom -> pdfView.zoomWithAnimation(e.x, e.y, pdfView.maxZoom)
            else -> pdfView.resetZoomWithAnimation()
        }
        return true
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean = false

    override fun onDown(e: MotionEvent): Boolean {
        animationManager.stopFling()
        return true
    }

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean = false

    override fun onScroll(
        p0: MotionEvent?,
        e1: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        scrolling = true
        if (isZooming || isSwipeEnabled) {
            pdfView.moveRelativeTo(-distanceX, -distanceY)
        }
        if (!scaling || pdfView.renderDuringScale) {
            pdfView.loadPageByOffset()
        }
        return true
    }

    private fun onScrollEnd(event: MotionEvent) {
        pdfView.loadPages()
        hideHandle()
    }

    override fun onLongPress(e: MotionEvent) {}

    override fun onFling(
        p0: MotionEvent?,
        e1: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        val xOffset = pdfView.currentXOffset.toInt()
        val yOffset = pdfView.currentYOffset.toInt()

        val (minX, minY) = if (pdfView.swipeVertical) {
            Pair(
                -(pdfView.toCurrentScale(pdfView.optimalPageWidth) - pdfView.width),
                -(pdfView.calculateDocLength() - pdfView.height)
            )
        } else {
            Pair(
                -(pdfView.calculateDocLength() - pdfView.width),
                -(pdfView.toCurrentScale(pdfView.optimalPageHeight) - pdfView.height)
            )
        }

        animationManager.startFlingAnimation(
            xOffset, yOffset, velocityX.toInt(), velocityY.toInt(),
            minX.toInt(), 0, minY.toInt(), 0
        )

        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        var dr = detector.scaleFactor
        val wantedZoom = pdfView.zoom * dr
        dr = when {
            wantedZoom < MINIMUM_ZOOM -> MINIMUM_ZOOM / pdfView.zoom
            wantedZoom > MAXIMUM_ZOOM -> MAXIMUM_ZOOM / pdfView.zoom
            else -> dr
        }
        pdfView.zoomCenteredRelativeTo(dr, PointF(detector.focusX, detector.focusY))
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        scaling = true
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        pdfView.loadPages()
        hideHandle()
        scaling = false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        var retVal = scaleGestureDetector.onTouchEvent(event)
        retVal = gestureDetector.onTouchEvent(event) || retVal

        if (event.action == MotionEvent.ACTION_UP) {
            if (scrolling) {
                scrolling = false
                onScrollEnd(event)
            }
        }
        return retVal
    }

    private fun hideHandle() {
        pdfView.scrollHandle?.takeIf { it.shown() }?.hideDelayed()
    }
}
