package com.github.barteksc.pdfviewer.listener

/**
 * Implements this interface to receive events from PDFView
 * when a page has been scrolled
 */
fun interface OnPageScrollListener {

    /**
     * Called on every move while scrolling
     *
     * @param page           current page index
     * @param positionOffset see {@link com.github.barteksc.pdfviewer.PDFView#getPositionOffset()}
     */
    fun onPageScrolled(page: Int, positionOffset: Float)
}
