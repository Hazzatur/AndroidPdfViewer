package com.github.barteksc.pdfviewer.exception

class PageRenderingException(
    val page: Int,
    cause: Throwable
) : Exception(cause)
