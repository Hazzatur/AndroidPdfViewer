package com.github.barteksc.sample

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.shockwave.pdfium.PdfDocument

class PDFViewActivity : AppCompatActivity(), OnPageChangeListener, OnLoadCompleteListener,
    OnPageErrorListener {

    companion object {
        private const val TAG = "PDFViewActivity"
        private const val SAMPLE_FILE = "alice.pdf"
    }

    private lateinit var pdfView: PDFView
    private var uri: Uri? = null
    private var pageNumber = 0
    private var pdfFileName: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchPicker()
        } else {
            Toast.makeText(this, "Permission is required to access files", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            this.uri = it
            displayFromUri(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pdfView = findViewById(R.id.pdfView)
        pdfView.setBackgroundColor(Color.LTGRAY)

        if (uri != null) {
            displayFromUri(uri!!)
        } else {
            displayFromAsset(SAMPLE_FILE)
        }
    }

    private fun pickFile() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchPicker()
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) -> {
                Toast.makeText(this, "Permission is required to access files", Toast.LENGTH_SHORT)
                    .show()
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun launchPicker() {
        try {
            pickFileLauncher.launch("application/pdf")
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.toast_pick_file_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayFromAsset(assetFileName: String) {
        pdfFileName = assetFileName

        pdfView.fromAsset(SAMPLE_FILE)
            .startPage(pageNumber)
            .onPageChange(this)
            .renderAnnotations(true)
            .onLoadComplete(this)
            .scrollHandle(DefaultScrollHandle(this))
            .spacing(10)
            .onPageError(this)
            .load()
    }

    private fun displayFromUri(uri: Uri) {
        pdfFileName = getFileName(uri)

        pdfView.fromUri(uri)
            .startPage(pageNumber)
            .onPageChange(this)
            .renderAnnotations(true)
            .onLoadComplete(this)
            .scrollHandle(DefaultScrollHandle(this))
            .spacing(10)
            .onPageError(this)
            .load()
    }

    override fun onPageChanged(page: Int, pageCount: Int) {
        pageNumber = page
        title = String.format("%s %s / %s", pdfFileName, page + 1, pageCount)
    }

    override fun loadComplete(nbPages: Int) {
        pdfView.getDocumentMeta()?.let { meta ->
            Log.e(TAG, "title = ${meta.title}")
            Log.e(TAG, "author = ${meta.author}")
            Log.e(TAG, "subject = ${meta.subject}")
            Log.e(TAG, "keywords = ${meta.keywords}")
            Log.e(TAG, "creator = ${meta.creator}")
            Log.e(TAG, "producer = ${meta.producer}")
            Log.e(TAG, "creationDate = ${meta.creationDate}")
            Log.e(TAG, "modDate = ${meta.modDate}")
        }

        printBookmarksTree(pdfView.getTableOfContents(), "-")
    }

    private fun printBookmarksTree(tree: List<PdfDocument.Bookmark>, sep: String) {
        for (b in tree) {
            Log.e(TAG, String.format("%s %s, p %d", sep, b.title, b.pageIdx))
            if (b.hasChildren()) {
                printBookmarksTree(b.children, "$sep-")
            }
        }
    }

    override fun onPageError(page: Int, t: Throwable?) {
        Log.e(TAG, "Cannot load page $page", t)
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    result = cursor.getString(nameIndex)
                }
            }
        }
        if (result == null) {
            result = uri.lastPathSegment
        }
        return result ?: "Unknown"
    }
}
