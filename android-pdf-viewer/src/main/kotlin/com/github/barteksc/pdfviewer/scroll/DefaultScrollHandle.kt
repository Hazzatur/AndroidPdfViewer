package com.github.barteksc.pdfviewer.scroll

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.R
import com.github.barteksc.pdfviewer.util.Util

class DefaultScrollHandle @JvmOverloads constructor(
    private val context: Context,
    private val inverted: Boolean = false
) : RelativeLayout(context), ScrollHandle {

    private val textView: TextView = TextView(context).apply { visibility = INVISIBLE }
    private var relativeHandlerMiddle = 0f
    private lateinit var pdfView: PDFView
    private var currentPos = 0f

    private val handler = Handler(Looper.getMainLooper())
    private val hidePageScrollerRunnable = Runnable { hide() }

    init {
        setTextColor(Color.BLACK)
        setTextSize(DEFAULT_TEXT_SIZE)
        addView(textView)
    }

    override fun setupLayout(pdfView: PDFView) {
        val (width, height, align) = if (pdfView.swipeVertical) {
            Triple(
                HANDLE_LONG,
                HANDLE_SHORT,
                if (inverted) ALIGN_PARENT_LEFT else ALIGN_PARENT_RIGHT
            )
        } else {
            Triple(
                HANDLE_SHORT,
                HANDLE_LONG,
                if (inverted) ALIGN_PARENT_TOP else ALIGN_PARENT_BOTTOM
            )
        }

        val background = ContextCompat.getDrawable(
            context,
            if (pdfView.swipeVertical) {
                if (inverted) R.drawable.default_scroll_handle_left else R.drawable.default_scroll_handle_right
            } else {
                if (inverted) R.drawable.default_scroll_handle_top else R.drawable.default_scroll_handle_bottom
            }
        )

        background?.let { setBackground(it) }

        val lp = LayoutParams(Util.getDP(context, width), Util.getDP(context, height)).apply {
            setMargins(0, 0, 0, 0)
            addRule(align)
        }

        val tVLP = LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(CENTER_IN_PARENT, TRUE)
        }

        textView.layoutParams = tVLP
        pdfView.addView(this, lp)
        this.pdfView = pdfView
    }

    override fun destroyLayout() {
        pdfView.removeView(this)
    }

    override fun setScroll(position: Float) {
        if (!shown()) show() else handler.removeCallbacks(hidePageScrollerRunnable)
        setPosition(
            ((if (pdfView.swipeVertical) pdfView.height else pdfView.width).toFloat())
        )
    }

    private fun setPosition(pos: Float) {
        val pdfViewSize =
            if (pdfView.swipeVertical) pdfView.height else pdfView.width
        var adjustedPos = pos - relativeHandlerMiddle
        adjustedPos =
            adjustedPos.coerceIn(0f, pdfViewSize - Util.getDP(context, HANDLE_SHORT).toFloat())

        if (pdfView.swipeVertical) y = adjustedPos else x = adjustedPos

        calculateMiddle()
        invalidate()
    }

    private fun calculateMiddle() {
        val pos = if (pdfView.swipeVertical) y else x
        val viewSize = if (pdfView.swipeVertical) height else width
        val pdfViewSize = if (pdfView.swipeVertical) pdfView.height else pdfView.width
        relativeHandlerMiddle = (pos + relativeHandlerMiddle) / pdfViewSize * viewSize
    }

    override fun hideDelayed() {
        handler.postDelayed(hidePageScrollerRunnable, 1000)
    }

    override fun setPageNum(pageNum: Int) {
        val text = pageNum.toString()
        if (textView.text != text) textView.text = text
    }

    override fun shown(): Boolean = visibility == VISIBLE

    override fun show() {
        visibility = VISIBLE
    }

    override fun hide() {
        visibility = INVISIBLE
    }

    private fun setTextColor(color: Int) {
        textView.setTextColor(color)
    }

    /**
     * @param size text size in dp
     */
    private fun setTextSize(size: Int) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size.toFloat())
    }

    private fun isPDFViewReady(): Boolean {
        return pdfView.getPageCount() > 0 && !pdfView.documentFitsView()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isPDFViewReady()) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                pdfView.stopFling()
                handler.removeCallbacks(hidePageScrollerRunnable)
                currentPos =
                    if (pdfView.swipeVertical) event.rawY - y else event.rawX - x
            }

            MotionEvent.ACTION_MOVE -> {
                if (pdfView.swipeVertical) {
                    setPosition(event.rawY - currentPos + relativeHandlerMiddle)
                    pdfView.setPositionOffset(relativeHandlerMiddle / height, false)
                } else {
                    setPosition(event.rawX - currentPos + relativeHandlerMiddle)
                    pdfView.setPositionOffset(relativeHandlerMiddle / width, false)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                hideDelayed()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        private const val HANDLE_LONG = 65
        private const val HANDLE_SHORT = 40
        private const val DEFAULT_TEXT_SIZE = 16
    }
}
