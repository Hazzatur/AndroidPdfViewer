package com.github.barteksc.pdfviewer.listener

/**
 * Implements this interface to receive events from PDFView
 * when a page has changed through swipe
 */
fun interface OnPageChangeListener {

    /**
     * Called when the user uses swipe to change page
     *
     * @param page      the new page displayed, starting from 0
     * @param pageCount the total page count
     */
    fun onPageChanged(page: Int, pageCount: Int)
}
