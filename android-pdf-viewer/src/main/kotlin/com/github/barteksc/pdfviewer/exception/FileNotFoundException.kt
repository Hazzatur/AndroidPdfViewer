package com.github.barteksc.pdfviewer.exception

@Deprecated("This exception class is deprecated")
class FileNotFoundException : RuntimeException {

    constructor(detailMessage: String) : super(detailMessage)

    constructor(detailMessage: String, throwable: Throwable) : super(detailMessage, throwable)
}
