package com.github.barteksc.pdfviewer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.PointF
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller

/**
 * This manager is used by the PDFView to launch animations.
 * It uses the ValueAnimator introduced in API 11 to start
 * an animation, and calls moveTo() on the PDFView as a result
 * of each animation update.
 */
class AnimationManager(private val pdfView: PDFView) {

    private var animation: ValueAnimator? = null
    private val scroller = OverScroller(pdfView.context)
    private var flinging = false

    fun startXAnimation(xFrom: Float, xTo: Float) {
        stopAll()
        animation = ValueAnimator.ofFloat(xFrom, xTo).apply {
            interpolator = DecelerateInterpolator()
            addUpdateListener(XAnimation())
            addListener(XAnimation())
            duration = 400
            start()
        }
    }

    fun startYAnimation(yFrom: Float, yTo: Float) {
        stopAll()
        animation = ValueAnimator.ofFloat(yFrom, yTo).apply {
            interpolator = DecelerateInterpolator()
            addUpdateListener(YAnimation())
            addListener(YAnimation())
            duration = 400
            start()
        }
    }

    fun startZoomAnimation(centerX: Float, centerY: Float, zoomFrom: Float, zoomTo: Float) {
        stopAll()
        animation = ValueAnimator.ofFloat(zoomFrom, zoomTo).apply {
            interpolator = DecelerateInterpolator()
            val zoomAnim = ZoomAnimation(centerX, centerY)
            addUpdateListener(zoomAnim)
            addListener(zoomAnim)
            duration = 400
            start()
        }
    }

    fun startFlingAnimation(
        startX: Int, startY: Int, velocityX: Int, velocityY: Int,
        minX: Int, maxX: Int, minY: Int, maxY: Int
    ) {
        stopAll()
        flinging = true
        scroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY)
    }

    fun computeFling() {
        if (scroller.computeScrollOffset()) {
            pdfView.moveTo(scroller.currX.toFloat(), scroller.currY.toFloat())
            pdfView.loadPageByOffset()
        } else if (flinging) { // fling finished
            flinging = false
            pdfView.loadPages()
            hideHandle()
        }
    }

    fun stopAll() {
        animation?.cancel()
        animation = null
        stopFling()
    }

    fun stopFling() {
        flinging = false
        scroller.forceFinished(true)
    }

    private fun hideHandle() {
        pdfView.scrollHandle?.hideDelayed()
    }

    inner class XAnimation : AnimatorListenerAdapter(), ValueAnimator.AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            val offset = animation.animatedValue as Float
            pdfView.moveTo(offset, pdfView.currentYOffset)
            pdfView.loadPageByOffset()
        }

        override fun onAnimationCancel(animation: Animator) {
            pdfView.loadPages()
        }

        override fun onAnimationEnd(animation: Animator) {
            pdfView.loadPages()
        }
    }

    inner class YAnimation : AnimatorListenerAdapter(), ValueAnimator.AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            val offset = animation.animatedValue as Float
            pdfView.moveTo(pdfView.currentXOffset, offset)
            pdfView.loadPageByOffset()
        }

        override fun onAnimationCancel(animation: Animator) {
            pdfView.loadPages()
        }

        override fun onAnimationEnd(animation: Animator) {
            pdfView.loadPages()
        }
    }

    inner class ZoomAnimation(private val centerX: Float, private val centerY: Float) :
        ValueAnimator.AnimatorUpdateListener, Animator.AnimatorListener {

        override fun onAnimationUpdate(animation: ValueAnimator) {
            val zoom = animation.animatedValue as Float
            pdfView.zoomCenteredTo(zoom, PointF(centerX, centerY))
        }

        override fun onAnimationCancel(animation: Animator) {}

        override fun onAnimationEnd(animation: Animator) {
            pdfView.loadPages()
            hideHandle()
        }

        override fun onAnimationRepeat(animation: Animator) {}

        override fun onAnimationStart(animation: Animator) {}
    }
}
