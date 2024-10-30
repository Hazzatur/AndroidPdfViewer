package com.github.barteksc.pdfviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import android.widget.RelativeLayout
import com.github.barteksc.pdfviewer.exception.PageRenderingException
import com.github.barteksc.pdfviewer.listener.OnDrawListener
import com.github.barteksc.pdfviewer.listener.OnErrorListener
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener
import com.github.barteksc.pdfviewer.listener.OnPageScrollListener
import com.github.barteksc.pdfviewer.listener.OnRenderListener
import com.github.barteksc.pdfviewer.listener.OnTapListener
import com.github.barteksc.pdfviewer.model.PagePart
import com.github.barteksc.pdfviewer.scroll.ScrollHandle
import com.github.barteksc.pdfviewer.source.AssetSource
import com.github.barteksc.pdfviewer.source.ByteArraySource
import com.github.barteksc.pdfviewer.source.DocumentSource
import com.github.barteksc.pdfviewer.source.FileSource
import com.github.barteksc.pdfviewer.source.InputStreamSource
import com.github.barteksc.pdfviewer.source.UriSource
import com.github.barteksc.pdfviewer.util.ArrayUtils
import com.github.barteksc.pdfviewer.util.Constants
import com.github.barteksc.pdfviewer.util.MathUtils
import com.github.barteksc.pdfviewer.util.Util.getDP
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.io.File
import java.io.InputStream
import kotlin.math.floor

class PDFView(
    context: Context,
    set: AttributeSet?
) : RelativeLayout(context, set) {

    /**
     * The thread [renderingHandler] will run on
     */
    private var renderingHandlerThread: HandlerThread = HandlerThread("PDFRenderingThread")

    /**
     * Rendered parts go to the cache manager
     */
    var cacheManager: CacheManager? = null
        private set

    /**
     * Handler always waiting in the background for rendering tasks
     */
    var renderingHandler: RenderingHandler? = null
        private set

    var minZoom = DEFAULT_MIN_SCALE
        private set
    var midZoom = DEFAULT_MID_SCALE
        private set
    var maxZoom = DEFAULT_MAX_SCALE
        private set
    var scrollDir = ScrollDir.NONE
        private set

    /**
     * Manages all offset and zoom animations
     */
    private lateinit var animationManager: AnimationManager

    /**
     * Manages all touch events
     */
    private lateinit var dragPinchManager: DragPinchManager

    /**
     * Pages the user wants to display in order (e.g., 0, 2, 8, 1)
     */
    var originalUserPages: IntArray = intArrayOf()
        private set

    /**
     * Filtered pages to avoid repetition (e.g., 0, 2, 8, 1)
     */
    var filteredUserPages: IntArray = intArrayOf()
        private set

    /**
     * Filtered indexes to avoid repetition (e.g., 0, 1, 2, 3)
     */
    var filteredUserPageIndexes: IntArray = intArrayOf()
        private set

    /**
     * Number of pages in the loaded PDF document
     */
    var documentPageCount: Int = 0
        private set

    /**
     * Index of the current page sequence
     */
    var currentPage: Int = 0
        private set
    private var currentFilteredPage: Int = 0

    /**
     * Actual width and height of PDF pages
     */
    private var pageWidth: Int = 0
    private var pageHeight: Int = 0

    /**
     * Optimal width and height of pages to fit the component size
     */
    var optimalPageWidth: Float = 0f
        private set
    var optimalPageHeight: Float = 0f
        private set

    /**
     * Current horizontal offset considering the zoom level
     */
    var currentXOffset: Float = 0f
        private set

    /**
     * Current vertical offset considering the zoom level
     */
    var currentYOffset: Float = 0f
        private set

    /**
     * Zoom level, always >= 1
     */
    var zoom: Float = 1f
        private set
    val isZooming: Boolean
        get() = zoom != minZoom

    /**
     * True if PDFView has been recycled
     */
    var recycled: Boolean = true
        private set

    /**
     * Current state of the view
     */
    private var state: State = State.DEFAULT

    /**
     * Async task for decoding the PDF document during loading phase
     */
    private var decodingTask: DecodingTask? = null
    private lateinit var pagesLoader: PagesLoader

    /**
     * Callback when PDF is loaded
     */
    private var onLoadCompleteListener: OnLoadCompleteListener? = null
    private var onErrorListener: OnErrorListener? = null

    /**
     * Callback for page change events
     */
    var onPageChangeListener: OnPageChangeListener? = null
        private set

    /**
     * Callback for page scroll events
     */
    var onPageScrollListener: OnPageScrollListener? = null
        private set

    /**
     * Callback for drawing above layer
     */
    private var onDrawListener: OnDrawListener? = null
    private var onDrawAllListener: OnDrawListener? = null

    /**
     * Callback when document is initially rendered
     */
    var onRenderListener: OnRenderListener? = null
        private set

    /**
     * Callback when user taps
     */
    var onTapListener: OnTapListener? = null
        private set

    /**
     * Callback for page load errors
     */
    private var onPageErrorListener: OnPageErrorListener? = null

    /**
     * Paint object for drawing
     */
    private var paint = Paint()

    /**
     * Paint object for drawing debug information
     */
    private var debugPaint = Paint()

    /**
     * Paint for invalid pages
     */
    var invalidPageColor: Int = Color.WHITE
        private set

    private var defaultPage: Int = 0

    /**
     * True if vertical scrolling is enabled instead of horizontal
     */
    var swipeVertical: Boolean = true
        private set

    /**
     * Pdfium core for loading and rendering PDFs
     */
    private lateinit var pdfiumCore: PdfiumCore
    private var pdfDocument: PdfDocument? = null

    var scrollHandle: ScrollHandle? = null
        private set
    private var isScrollHandleInit: Boolean = false

    /**
     * True if bitmap should use ARGB_8888 for higher quality, false for RGB_565
     */
    var bestQuality: Boolean = false
        private set

    /**
     * True if annotations should be rendered
     */
    var annotationRendering: Boolean = false
        private set

    /**
     * True if rendering should occur during scaling
     */
    var renderDuringScale: Boolean = false
        private set

    /**
     * Enables antialiasing and bitmap filtering
     */
    var enableAntialiasing: Boolean = true
        private set
    private val antialiasFilter =
        PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * Spacing between pages in DP
     */
    var spacingPx: Int = 0
        private set

    /**
     * Page numbers for [onDrawAllListener] callback
     */
    private val onDrawPagesNums = mutableListOf<Int>()

    init {
        renderingHandlerThread = HandlerThread("PDF renderer")

        if (!isInEditMode) {
            cacheManager = CacheManager()
            animationManager = AnimationManager(this)
            dragPinchManager = DragPinchManager(this, animationManager)

            paint = Paint()
            debugPaint = Paint().apply {
                style = Paint.Style.STROKE
            }

            pdfiumCore = PdfiumCore(context)
            setWillNotDraw(false)
        }
    }


    private fun load(
        docSource: DocumentSource,
        password: String? = null,
        onLoadCompleteListener: OnLoadCompleteListener?,
        onErrorListener: OnErrorListener?,
        userPages: IntArray = intArrayOf()
    ) {
        if (!recycled) {
            throw IllegalStateException("Don't call load on a PDF View without recycling it first.")
        }

        // Manage userPages if provided
        if (userPages.isNotEmpty()) {
            originalUserPages = userPages
            filteredUserPages = ArrayUtils.deleteDuplicatedPages(originalUserPages)
            filteredUserPageIndexes = ArrayUtils.calculateIndexesInDuplicateArray(originalUserPages)
        }

        this.onLoadCompleteListener = onLoadCompleteListener
        this.onErrorListener = onErrorListener

        val firstPageIdx = originalUserPages.firstOrNull() ?: 0

        recycled = false
        // Start decoding the document
        decodingTask = DecodingTask(docSource, password, this, pdfiumCore, firstPageIdx)
    }

    /**
     * Go to the given page.
     *
     * @param page Page index.
     * @param withAnimation True if should scroll with animation
     */
    fun jumpTo(page: Int, withAnimation: Boolean = false) {
        val offset = -calculatePageOffset(page)
        if (swipeVertical) {
            if (withAnimation) {
                animationManager.startYAnimation(currentYOffset, offset)
            } else {
                moveTo(currentXOffset, offset)
            }
        } else {
            if (withAnimation) {
                animationManager.startXAnimation(currentXOffset, offset)
            } else {
                moveTo(offset, currentYOffset)
            }
        }
        showPage(page)
    }

    fun showPage(pageNb: Int) {
        if (recycled) return

        // Validate page number, considering UserPages vs. DocumentPages
        var validPageNb = determineValidPageNumberFrom(pageNb)
        currentPage = validPageNb
        currentFilteredPage = validPageNb

        filteredUserPageIndexes.let {
            if (validPageNb in it.indices) {
                validPageNb = it[validPageNb]
                currentFilteredPage = validPageNb
            }
        }

        loadPages()

        scrollHandle?.takeIf { !documentFitsView() }?.setPageNum(currentPage + 1)

        onPageChangeListener?.onPageChanged(currentPage, getPageCount())
    }

    /**
     * Get current position as a ratio of document length to visible area.
     * 0 means the document start is visible, 1 means the document end is visible.
     *
     * @return offset between 0 and 1
     */
    fun getPositionOffset(): Float {
        val offset = if (swipeVertical) {
            -currentYOffset / (calculateDocLength() - height)
        } else {
            -currentXOffset / (calculateDocLength() - width)
        }
        return MathUtils.limit(offset, 0f, 1f)
    }

    /**
     * Get current position as a ratio of document length to visible area.
     * 0 means the document start is visible, 1 means the document end is visible.
     *
     * @param progress scroll offset between 0 and 1
     * @param moveHandle Whether to move the scroll handle or not
     * @return offset between 0 and 1
     */
    fun setPositionOffset(progress: Float, moveHandle: Boolean = true) {
        if (swipeVertical) {
            moveTo(currentXOffset, (-calculateDocLength() + height) * progress, moveHandle)
        } else {
            moveTo((-calculateDocLength() + width) * progress, currentYOffset, moveHandle)
        }
        loadPageByOffset()
    }

    /**
     * Calculate the offset needed to center a specific page.
     *
     * @param page The page number.
     * @return The offset required to center the page.
     */
    private fun calculatePageOffset(page: Int): Float {
        return if (swipeVertical) {
            toCurrentScale(page * optimalPageHeight + page * spacingPx)
        } else {
            toCurrentScale(page * optimalPageWidth + page * spacingPx)
        }
    }

    /**
     * Calculate the total length of the document.
     *
     * @return The document length considering the zoom level and page spacing.
     */
    fun calculateDocLength(): Float {
        val pageCount = getPageCount()
        return if (swipeVertical) {
            toCurrentScale(pageCount * optimalPageHeight + (pageCount - 1) * spacingPx)
        } else {
            toCurrentScale(pageCount * optimalPageWidth + (pageCount - 1) * spacingPx)
        }
    }

    fun stopFling() {
        animationManager.stopFling()
    }

    fun getPageCount(): Int {
        return if (originalUserPages.isNotEmpty()) {
            originalUserPages.size
        } else {
            documentPageCount
        }
    }

    fun onPageError(ex: PageRenderingException) {
        onPageErrorListener?.onPageError(ex.page, ex.cause)
            ?: Log.e(TAG, "Cannot open page ${ex.page}", ex.cause)
    }


    fun recycle() {
        animationManager.stopAll()

        // Stop tasks
        renderingHandler?.apply {
            stop()
            removeMessages(RenderingHandler.MSG_RENDER_TASK)
        }

        decodingTask?.cancel()

        // Clear caches
        cacheManager?.recycle()

        scrollHandle?.takeIf { isScrollHandleInit }?.destroyLayout()

        pdfiumCore.let { core ->
            pdfDocument?.let { document ->
                core.closeDocument(document)
            }
        }

        renderingHandler = null
        originalUserPages = intArrayOf()
        filteredUserPages = intArrayOf()
        filteredUserPageIndexes = intArrayOf()
        pdfDocument = null
        scrollHandle = null
        isScrollHandleInit = false
        currentXOffset = 0f
        currentYOffset = 0f
        zoom = 1f
        recycled = true
        state = State.DEFAULT
    }

    /**
     * Handle fling animation.
     */
    override fun computeScroll() {
        super.computeScroll()
        if (isInEditMode) return
        animationManager.computeFling()
    }

    override fun onDetachedFromWindow() {
        recycle()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        if (isInEditMode || state != State.SHOWN) return

        animationManager.stopAll()
        calculateOptimalWidthAndHeight()

        if (swipeVertical) {
            moveTo(currentXOffset, -calculatePageOffset(currentPage))
        } else {
            moveTo(-calculatePageOffset(currentPage), currentYOffset)
        }
        loadPageByOffset()
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        return if (swipeVertical) {
            when {
                direction < 0 && currentXOffset < 0 -> true
                direction > 0 && currentXOffset + toCurrentScale(optimalPageWidth) > width -> true
                else -> false
            }
        } else {
            when {
                direction < 0 && currentXOffset < 0 -> true
                direction > 0 && currentXOffset + calculateDocLength() > width -> true
                else -> false
            }
        }
    }

    override fun canScrollVertically(direction: Int): Boolean {
        return if (swipeVertical) {
            when {
                direction < 0 && currentYOffset < 0 -> true
                direction > 0 && currentYOffset + calculateDocLength() > height -> true
                else -> false
            }
        } else {
            when {
                direction < 0 && currentYOffset < 0 -> true
                direction > 0 && currentYOffset + toCurrentScale(optimalPageHeight) > height -> true
                else -> false
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (isInEditMode) return

        // As I said in this class javadoc, we can think of this canvas as a huge
        // strip on which we draw all the images. We actually only draw the rendered
        // parts, of course, but we render them in the place they belong in this huge
        // strip.

        // That's where Canvas.translate(x, y) becomes very helpful.
        // This is the situation :
        //  _______________________________________________
        // |   			 |					 			   |
        // | the actual  |					The big strip  |
        // |	canvas	 | 								   |
        // |_____________|								   |
        // |_______________________________________________|
        //
        // If the rendered part is on the bottom right corner of the strip
        // we can draw it but we won't see it because the canvas is not big enough.

        // But if we call translate(-X, -Y) on the canvas just before drawing the object :
        //  _______________________________________________
        // |   			  					  _____________|
        // |   The big strip     			 |			   |
        // |		    					 |	the actual |
        // |								 |	canvas	   |
        // |_________________________________|_____________|
        //
        // The object will be on the canvas.
        // This technique is massively used in this method, and allows
        // abstraction of the screen position when rendering the parts.

        // Draws background

        // Set up anti-aliasing if enabled
        if (enableAntialiasing) {
            canvas.drawFilter = antialiasFilter
        }

        // Draw the background color or custom background if available
        background?.draw(canvas) ?: canvas.drawColor(Color.WHITE)

        if (recycled || state != State.SHOWN) return

        // Move the canvas before drawing elements
        val currentXOffset = this.currentXOffset
        val currentYOffset = this.currentYOffset
        canvas.translate(currentXOffset, currentYOffset)

        // Draw thumbnails
        cacheManager?.getThumbnails()?.forEach { part ->
            drawPart(canvas, part)
        }

        // Draw page parts and collect unique page numbers for `onDrawAllListener`
        cacheManager?.getPageParts()?.forEach { part ->
            drawPart(canvas, part)
            if (onDrawAllListener != null && part.userPage !in onDrawPagesNums) {
                onDrawPagesNums.add(part.userPage)
            }
        }

        // Draw with `onDrawAllListener` for collected pages
        onDrawPagesNums.forEach { page ->
            drawWithListener(canvas, page, onDrawAllListener)
        }
        onDrawPagesNums.clear()

        // Draw the current page with `onDrawListener`
        drawWithListener(canvas, currentPage, onDrawListener)

        // Restore the canvas position
        canvas.translate(-currentXOffset, -currentYOffset)
    }

    private fun drawWithListener(canvas: Canvas, page: Int, listener: OnDrawListener?) {
        listener?.let {
            val (translateX, translateY) = if (swipeVertical) {
                0f to calculatePageOffset(page)
            } else {
                calculatePageOffset(page) to 0f
            }

            canvas.translate(translateX, translateY)
            it.onLayerDrawn(
                canvas,
                toCurrentScale(optimalPageWidth),
                toCurrentScale(optimalPageHeight),
                page
            )
            canvas.translate(-translateX, -translateY)
        }
    }

    /**
     * Draw a given PagePart on the canvas.
     */
    private fun drawPart(canvas: Canvas, part: PagePart) {
        val pageRelativeBounds = part.pageRelativeBounds
        val renderedBitmap = part.renderedBitmap ?: return

        if (renderedBitmap.isRecycled) return

        // Move to the target page
        val (localTranslationX, localTranslationY) = if (swipeVertical) {
            0f to calculatePageOffset(part.userPage)
        } else {
            calculatePageOffset(part.userPage) to 0f
        }
        canvas.translate(localTranslationX, localTranslationY)

        val srcRect = Rect(0, 0, renderedBitmap.width, renderedBitmap.height)

        val offsetX = toCurrentScale(pageRelativeBounds.left * optimalPageWidth)
        val offsetY = toCurrentScale(pageRelativeBounds.top * optimalPageHeight)
        val width = toCurrentScale(pageRelativeBounds.width() * optimalPageWidth)
        val height = toCurrentScale(pageRelativeBounds.height() * optimalPageHeight)

        // Create destination rectangle, rounded to int to avoid gaps at high zoom levels
        val dstRect = RectF(
            offsetX.toInt().toFloat(),
            offsetY.toInt().toFloat(),
            (offsetX + width).toInt().toFloat(),
            (offsetY + height).toInt().toFloat()
        )

        // Check if the bitmap is on the screen
        val translationX = currentXOffset + localTranslationX
        val translationY = currentYOffset + localTranslationY
        if (translationX + dstRect.left >= width || translationX + dstRect.right <= 0 ||
            translationY + dstRect.top >= height || translationY + dstRect.bottom <= 0
        ) {
            canvas.translate(-localTranslationX, -localTranslationY)
            return
        }

        // Draw the bitmap onto the canvas
        canvas.drawBitmap(renderedBitmap, srcRect, dstRect, paint)

        // Optionally draw debug rectangles
        if (Constants.DEBUG_MODE) {
            debugPaint.color = if (part.userPage % 2 == 0) Color.RED else Color.BLUE
            canvas.drawRect(dstRect, debugPaint)
        }

        // Restore the canvas position
        canvas.translate(-localTranslationX, -localTranslationY)
    }

    /**
     * Load all parts around the center of the screen,
     * considering X and Y offsets, zoom level, and the current displayed page.
     */
    fun loadPages() {
        if (optimalPageWidth == 0f || optimalPageHeight == 0f || renderingHandler == null) return

        // Cancel all current tasks
        renderingHandler?.removeMessages(RenderingHandler.MSG_RENDER_TASK)
        cacheManager?.makeANewSet()

        pagesLoader.loadPages()
        redraw()
    }

    /**
     * Called when the PDF is loaded
     */
    fun loadComplete(pdfDocument: PdfDocument, pageWidth: Int, pageHeight: Int) {
        state = State.LOADED
        documentPageCount = pdfiumCore.getPageCount(pdfDocument)

        this.pdfDocument = pdfDocument
        this.pageWidth = pageWidth
        this.pageHeight = pageHeight
        calculateOptimalWidthAndHeight()

        pagesLoader = PagesLoader(this)

        if (!renderingHandlerThread.isAlive) {
            renderingHandlerThread.start()
        }
        renderingHandler =
            RenderingHandler(renderingHandlerThread.looper, this, pdfiumCore, pdfDocument)
        renderingHandler?.start()

        scrollHandle?.apply {
            setupLayout(this@PDFView)
            isScrollHandleInit = true
        }

        onLoadCompleteListener?.loadComplete(documentPageCount)

        jumpTo(defaultPage, false)
    }

    fun loadError(t: Throwable) {
        state = State.ERROR
        recycle()
        invalidate()
        onErrorListener?.onError(t) ?: Log.e("PDFView", "Failed to load PDF", t)
    }

    fun redraw() {
        invalidate()
    }

    /**
     * Called when a rendering task is over and a PagePart has been freshly created.
     *
     * @param part The created PagePart.
     */
    fun onBitmapRendered(part: PagePart) {
        // When it is the first rendered part
        if (state == State.LOADED) {
            state = State.SHOWN
            onRenderListener?.onInitiallyRendered(
                getPageCount(),
                optimalPageWidth,
                optimalPageHeight
            )
        }

        if (part.thumbnail) {
            cacheManager?.cacheThumbnail(part)
        } else {
            cacheManager?.cachePart(part)
        }
        redraw()
    }

    /**
     * Restricts the given user page number to a valid page within bounds.
     *
     * @param userPage A page number.
     * @return A restricted valid page number.
     */
    private fun determineValidPageNumberFrom(userPage: Int): Int {
        return when {
            userPage <= 0 -> 0
            originalUserPages.isNotEmpty() -> {
                if (userPage >= originalUserPages.size) originalUserPages.size - 1 else userPage
            }

            else -> {
                if (userPage >= documentPageCount) documentPageCount - 1 else userPage
            }
        }
    }

    /**
     * Calculate the offset needed to center the given page on the screen.
     *
     * @param pageNb The page number.
     * @return The offset to use to center the page.
     */
    private fun calculateCenterOffsetForPage(pageNb: Int): Float {
        return if (swipeVertical) {
            val imageY = -(pageNb * optimalPageHeight + pageNb * spacingPx) + height /
                    2f - optimalPageHeight / 2
            imageY
        } else {
            val imageX = -(pageNb * optimalPageWidth + pageNb * spacingPx) + width /
                    2f - optimalPageWidth / 2
            imageX
        }
    }

    /**
     * Calculate the optimal width and height of a page considering the area dimensions.
     */
    private fun calculateOptimalWidthAndHeight() {
        if (state == State.DEFAULT || width == 0) return

        val maxWidth = width.toFloat()
        val maxHeight = height.toFloat()
        val ratio = pageWidth / pageHeight.toFloat()
        var w = maxWidth
        var h = floor(maxWidth / ratio)

        if (h > maxHeight) {
            h = maxHeight
            w = floor(maxHeight * ratio)
        }

        optimalPageWidth = w
        optimalPageHeight = h
    }

    fun moveTo(offsetX: Float, offsetY: Float) {
        moveTo(offsetX, offsetY, moveHandle = true)
    }

    /**
     * Move to the given X and Y offsets, with checks to prevent moving outside the boundaries.
     *
     * @param offsetX    The X offset for the left border of the screen.
     * @param offsetY    The Y offset for the top border of the screen.
     * @param moveHandle Whether to move the scroll handle or not.
     */
    fun moveTo(offsetX: Float, offsetY: Float, moveHandle: Boolean) {
        var adjustedOffsetX = offsetX
        var adjustedOffsetY = offsetY

        if (swipeVertical) {
            // Check X offset
            val scaledPageWidth = toCurrentScale(optimalPageWidth)
            adjustedOffsetX = when {
                scaledPageWidth < width -> width / 2f - scaledPageWidth / 2
                adjustedOffsetX > 0 -> 0f
                adjustedOffsetX + scaledPageWidth < width -> width - scaledPageWidth
                else -> adjustedOffsetX
            }

            // Check Y offset
            val contentHeight = calculateDocLength()
            adjustedOffsetY = when {
                contentHeight < height -> (height - contentHeight) / 2
                adjustedOffsetY > 0 -> 0f
                adjustedOffsetY + contentHeight < height -> height - contentHeight
                else -> adjustedOffsetY
            }

            // Set scroll direction based on Y offset
            scrollDir = when {
                adjustedOffsetY < currentYOffset -> ScrollDir.END
                adjustedOffsetY > currentYOffset -> ScrollDir.START
                else -> ScrollDir.NONE
            }
        } else {
            // Check Y offset
            val scaledPageHeight = toCurrentScale(optimalPageHeight)
            adjustedOffsetY = when {
                scaledPageHeight < height -> height / 2f - scaledPageHeight / 2
                adjustedOffsetY > 0 -> 0f
                adjustedOffsetY + scaledPageHeight < height -> height - scaledPageHeight
                else -> adjustedOffsetY
            }

            // Check X offset
            val contentWidth = calculateDocLength()
            adjustedOffsetX = when {
                contentWidth < width -> (width - contentWidth) / 2
                adjustedOffsetX > 0 -> 0f
                adjustedOffsetX + contentWidth < width -> width - contentWidth
                else -> adjustedOffsetX
            }

            // Set scroll direction based on X offset
            scrollDir = when {
                adjustedOffsetX < currentXOffset -> ScrollDir.END
                adjustedOffsetX > currentXOffset -> ScrollDir.START
                else -> ScrollDir.NONE
            }
        }

        currentXOffset = adjustedOffsetX
        currentYOffset = adjustedOffsetY
        val positionOffset = getPositionOffset()

        // Move handle if necessary
        if (moveHandle && scrollHandle != null && !documentFitsView()) {
            scrollHandle?.setScroll(positionOffset)
        }

        // Notify scroll listener
        onPageScrollListener?.onPageScrolled(currentPage, positionOffset)

        redraw()
    }

    fun loadPageByOffset() {
        if (getPageCount() == 0) return

        val spacingPerPage = spacingPx - (spacingPx.toFloat() / getPageCount())
        val (offset, optimal, screenCenter) = if (swipeVertical) {
            Triple(currentYOffset, optimalPageHeight + spacingPerPage, height / 2f)
        } else {
            Triple(currentXOffset, optimalPageWidth + spacingPerPage, width / 2f)
        }

        val page = ((kotlin.math.abs(offset) + screenCenter) / toCurrentScale(optimal)).toInt()

        if (page in 0 until getPageCount() && page != currentPage) {
            showPage(page)
        } else {
            loadPages()
        }
    }

    /**
     * Move relatively to the current position.
     *
     * @param dx The X difference to apply.
     * @param dy The Y difference to apply.
     * @see moveTo
     */
    fun moveRelativeTo(dx: Float, dy: Float) {
        moveTo(currentXOffset + dx, currentYOffset + dy)
    }

    /**
     * Change the zoom level.
     */
    fun zoomTo(zoom: Float) {
        this.zoom = zoom
    }

    /**
     * Change the zoom level, centered on a pivot point to keep it in the middle.
     *
     * @param zoom The new zoom level.
     * @param pivot The point to remain centered.
     */
    fun zoomCenteredTo(zoom: Float, pivot: PointF) {
        val dZoom = zoom / this.zoom
        zoomTo(zoom)
        var baseX = currentXOffset * dZoom
        var baseY = currentYOffset * dZoom
        baseX += pivot.x - pivot.x * dZoom
        baseY += pivot.y - pivot.y * dZoom
        moveTo(baseX, baseY)
    }

    /**
     * Change zoom level relative to a pivot point.
     *
     * @see zoomCenteredTo
     */
    fun zoomCenteredRelativeTo(dZoom: Float, pivot: PointF) {
        zoomCenteredTo(zoom * dZoom, pivot)
    }

    /**
     * Checks if the whole document fits the view without zoom.
     *
     * @return true if the whole document can be displayed at once, false otherwise
     */
    fun documentFitsView(): Boolean {
        val pageCount = getPageCount()
        val spacing = (pageCount - 1) * spacingPx
        return if (swipeVertical) {
            pageCount * optimalPageHeight + spacing < height
        } else {
            pageCount * optimalPageWidth + spacing < width
        }
    }

    fun fitToWidth(page: Int) {
        if (state != State.SHOWN) {
            Log.e(TAG, "Cannot fit, document not rendered yet")
            return
        }
        fitToWidth()
        jumpTo(page)
    }

    fun fitToWidth() {
        if (state != State.SHOWN) {
            Log.e(TAG, "Cannot fit, document not rendered yet")
            return
        }
        zoomTo(width / optimalPageWidth)
        setPositionOffset(0f)
    }

    fun toRealScale(size: Float): Float = size / zoom

    fun toCurrentScale(size: Float): Float = size * zoom

    fun resetZoom() {
        zoomTo(minZoom)
    }

    fun resetZoomWithAnimation() {
        zoomWithAnimation(minZoom)
    }

    fun zoomWithAnimation(centerX: Float, centerY: Float, scale: Float) {
        animationManager.startZoomAnimation(centerX, centerY, zoom, scale)
    }

    fun zoomWithAnimation(scale: Float) {
        animationManager.startZoomAnimation(width / 2f, height / 2f, zoom, scale)
    }

    /**
     * Get page number at given offset
     *
     * @param positionOffset scroll offset between 0 and 1
     * @return page number at given offset, starting from 0
     */
    fun getPageAtPositionOffset(positionOffset: Float): Int {
        val page = floor(getPageCount() * positionOffset).toInt()
        return if (page == getPageCount()) page - 1 else page
    }

    fun enableSwipe(enableSwipe: Boolean) {
        dragPinchManager.setSwipeEnabled(enableSwipe)
    }

    fun enableDoubleTap(enableDoubleTap: Boolean) {
        dragPinchManager.enableDoubleTap(enableDoubleTap)
    }

    private fun setSpacing(spacing: Int) {
        this.spacingPx = getDP(context, spacing)
    }

    fun getDocumentMeta(): PdfDocument.Meta? {
        return pdfDocument?.let { pdfiumCore.getDocumentMeta(it) }
    }

    fun getTableOfContents(): List<PdfDocument.Bookmark> {
        return pdfDocument?.let { pdfiumCore.getTableOfContents(it) } ?: emptyList()
    }

    /**
     * Use an asset file as the PDF source
     */
    fun fromAsset(assetName: String): Configurator = Configurator(AssetSource(assetName))

    /**
     * Use a file as the PDF source
     */
    fun fromFile(file: File): Configurator = Configurator(FileSource(file))

    /**
     * Use URI as the PDF source, for use with content providers
     */
    fun fromUri(uri: Uri): Configurator = Configurator(UriSource(uri))

    /**
     * Use a byte array as the PDF source; the document is not saved
     */
    fun fromBytes(bytes: ByteArray): Configurator = Configurator(ByteArraySource(bytes))

    /**
     * Use an input stream as the PDF source
     */
    fun fromStream(stream: InputStream): Configurator = Configurator(InputStreamSource(stream))

    /**
     * Use a custom source as the PDF source
     */
    fun fromSource(docSource: DocumentSource): Configurator = Configurator(docSource)

    /**
     * START - scrolling in first page direction
     * END - scrolling in last page direction
     * NONE - not scrolling
     */
    enum class ScrollDir { START, END, NONE }

    private enum class State { DEFAULT, LOADED, SHOWN, ERROR }

    inner class Configurator(
        private val source: DocumentSource
    ) {
        private var pages: IntArray = intArrayOf()
        private var swipeEnabled: Boolean = true
        private var doubleTapEnabled: Boolean = true
        private var onDraw: OnDrawListener? = null
        private var onDrawAll: OnDrawListener? = null
        private var onLoadComplete: OnLoadCompleteListener? = null
        private var onError: OnErrorListener? = null
        private var onPageChange: OnPageChangeListener? = null
        private var onScroll: OnPageScrollListener? = null
        private var onRender: OnRenderListener? = null
        private var onTap: OnTapListener? = null
        private var onPageError: OnPageErrorListener? = null
        private var startPage: Int = 0
        private var horizontalSwipe: Boolean = false
        private var renderAnnotations: Boolean = false
        private var password: String? = null
        private var scrollHandle: ScrollHandle? = null
        private var antialiasing: Boolean = true
        private var spacing: Int = 0
        private var invalidPageColor: Int = Color.WHITE

        fun pages(vararg pages: Int) = apply { this.pages = pages }
        fun swipe(enabled: Boolean) = apply { this.swipeEnabled = enabled }
        fun doubleTap(enabled: Boolean) = apply { this.doubleTapEnabled = enabled }
        fun renderAnnotations(enabled: Boolean) = apply { this.renderAnnotations = enabled }
        fun onDraw(listener: OnDrawListener) = apply { this.onDraw = listener }
        fun onDrawAll(listener: OnDrawListener) = apply { this.onDrawAll = listener }
        fun onLoadComplete(listener: OnLoadCompleteListener) =
            apply { this.onLoadComplete = listener }

        fun onScroll(listener: OnPageScrollListener) = apply { this.onScroll = listener }
        fun onError(listener: OnErrorListener) = apply { this.onError = listener }
        fun onPageError(listener: OnPageErrorListener) = apply { this.onPageError = listener }
        fun onPageChange(listener: OnPageChangeListener) = apply { this.onPageChange = listener }
        fun onRender(listener: OnRenderListener) = apply { this.onRender = listener }
        fun onTap(listener: OnTapListener) = apply { this.onTap = listener }
        fun startPage(page: Int) = apply { this.startPage = page }
        fun horizontalSwipe(enabled: Boolean) = apply { this.horizontalSwipe = enabled }
        fun password(pass: String) = apply { this.password = pass }
        fun scrollHandle(handle: ScrollHandle) = apply { this.scrollHandle = handle }
        fun antialiasing(enabled: Boolean) = apply { this.antialiasing = enabled }
        fun spacing(spacing: Int) = apply { this.spacing = spacing }
        fun invalidPageColor(color: Int) = apply { this.invalidPageColor = color }

        fun load() {
            this@PDFView.recycle()
            this@PDFView.onDrawListener = onDraw
            this@PDFView.onDrawAllListener = onDrawAll
            this@PDFView.onPageChangeListener = onPageChange
            this@PDFView.onPageScrollListener = onScroll
            this@PDFView.onRenderListener = onRender
            this@PDFView.onTapListener = onTap
            this@PDFView.onPageErrorListener = onPageError
            this@PDFView.enableSwipe(swipeEnabled)
            this@PDFView.enableDoubleTap(doubleTapEnabled)
            this@PDFView.defaultPage = startPage
            this@PDFView.swipeVertical = !horizontalSwipe
            this@PDFView.annotationRendering = renderAnnotations
            this@PDFView.scrollHandle = scrollHandle
            this@PDFView.enableAntialiasing = antialiasing
            this@PDFView.setSpacing(spacing)
            this@PDFView.invalidPageColor = invalidPageColor
            this@PDFView.dragPinchManager.setSwipeVertical(!horizontalSwipe)

            this@PDFView.post {
                this@PDFView.load(source, password, onLoadComplete, onError, pages)
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_SCALE: Float = 3.0f
        const val DEFAULT_MID_SCALE: Float = 1.75f
        const val DEFAULT_MIN_SCALE: Float = 1.0f
        private val TAG: String = PDFView::class.java.simpleName
    }
}
