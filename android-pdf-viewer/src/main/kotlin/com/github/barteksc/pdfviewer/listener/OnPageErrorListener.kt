package com.github.barteksc.pdfviewer.listener

fun interface OnPageErrorListener {

    /**
     * Called if error occurred while loading PDF page
     *
     * @param page the page number that caused the error
     * @param t    Throwable with error
     */
    fun onPageError(page: Int, t: Throwable?)
}
