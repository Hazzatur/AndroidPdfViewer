package com.github.barteksc.pdfviewer.util

object Constants {

    var DEBUG_MODE: Boolean = false

    /**
     * Between 0 and 1, the thumbnails quality (default 0.3). Increasing this value may cause performance decrease
     */
    var THUMBNAIL_RATIO: Float = 0.3f

    /**
     * The size of the rendered parts (default 256)
     * Tinier: a little bit slower to have the whole page rendered but more reactive.
     * Bigger: user will have to wait longer to have the first visual results
     */
    var PART_SIZE: Float = 256f

    /**
     * Number of preloaded rows or columns
     */
    var PRELOAD_COUNT: Int = 1

    object Cache {

        /**
         * The size of the cache (number of bitmaps kept)
         */
        var CACHE_SIZE: Int = 120

        var THUMBNAILS_CACHE_SIZE: Int = 120
    }

    object Pinch {

        var MAXIMUM_ZOOM: Float = 10f

        var MINIMUM_ZOOM: Float = 1f
    }
}
