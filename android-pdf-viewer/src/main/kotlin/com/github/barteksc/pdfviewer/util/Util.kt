package com.github.barteksc.pdfviewer.util

import android.content.Context
import android.util.TypedValue
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

object Util {
    private const val DEFAULT_BUFFER_SIZE = 1024 * 4

    fun getDP(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    @Throws(IOException::class)
    fun toByteArray(inputStream: InputStream): ByteArray {
        ByteArrayOutputStream().use { os ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                os.write(buffer, 0, bytesRead)
            }
            return os.toByteArray()
        }
    }
}
