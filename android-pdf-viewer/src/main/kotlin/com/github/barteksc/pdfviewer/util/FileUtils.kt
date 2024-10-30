package com.github.barteksc.pdfviewer.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

object FileUtils {

    @Throws(IOException::class)
    fun fileFromAsset(context: Context, assetName: String): File {
        val outFile = File(context.cacheDir, "$assetName-pdfview.pdf")
        if (assetName.contains("/")) {
            outFile.parentFile?.mkdirs()
        }
        context.assets.open(assetName).use { inputStream ->
            copy(inputStream, outFile)
        }
        return outFile
    }

    @Throws(IOException::class)
    private fun copy(inputStream: InputStream, output: File) {
        FileOutputStream(output).use { outputStream ->
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
        }
    }
}
