package com.github.barteksc.pdfviewer.listener

fun interface OnRenderListener {

    /**
     * Called only once, when document is rendered
     *
     * @param nbPages    number of pages
     * @param pageWidth  width of page
     * @param pageHeight height of page
     */
    fun onInitiallyRendered(nbPages: Int, pageWidth: Float, pageHeight: Float)
}
