# Android PdfViewer

Library for displaying PDF documents on Android with support for `animations`, `gestures`, `zoom`,
and `double-tap`. It is based on [PdfiumAndroid](https://github.com/barteksc/PdfiumAndroid) for
decoding PDF files, compatible with API 26 (Android 8.0) and higher. Licensed under Apache License
2.0.

## What’s New in Version 3.0.0

* **Migration to Kotlin**: The core library and sample have been migrated to Kotlin, with Gradle
  files updated to KTS.
* **Updated Targets**: JavaVersion.VERSION_21, minSdk = 26, and targetSdk = 35.
* **Refactored Naming Conventions**: Revised the `Configurator` class and others for improved
  clarity and style consistency.

### Recent Updates (2.8.x)

* **2.8.0**: Added handling of invalid pages, inspired
  by [#433](https://github.com/barteksc/AndroidPdfViewer/pull/433). Invalid page color can be set
  with `Configurator#invalidPageColor()`. Introduced `canScrollVertically()` and
  `canScrollHorizontally()` for compatibility with views like `SwipeRefreshLayout`.
* **2.8.1**: Fixed bug with rendering in Android Studio’s Layout Editor.
* **2.8.2**: Fixed an issue where pages failed to load correctly with animated transitions in
  `PDFView#jumpTo()`.

## Installation

Add the following to your project’s `build.gradle`:

```kotlin
implementation 'com.github.hazzatur:android-pdf-viewer:3.0.0'
```

## Include PDFView in Your Layout

```xml

<com.github.barteksc.pdfviewer.PDFView android:id="@+id/pdfView" android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

## Load a PDF File

Example of loading a PDF with custom configurations:

```kotlin
pdfView.fromAsset("sample.pdf")
    .pages(0, 2, 1, 3) // Optional: Filter and order pages
    .enableSwipe(true)
    .swipeHorizontal(false)
    .enableDoubleTap(true)
    .defaultPage(0)
    .onDraw(onDrawListener) // Called on the current page
    .onDrawAll(onDrawAllListener) // Called on all visible pages
    .onLoad(onLoadCompleteListener) // Called after document is loaded
    .onPageChange(onPageChangeListener)
    .onPageScroll(onPageScrollListener)
    .onError(onErrorListener)
    .onPageError(onPageErrorListener)
    .onRender(onRenderListener) // Called after document is rendered
    .onTap(onTapListener)
    .enableAnnotationRendering(false) // Render annotations (e.g., comments, colors, forms)
    .password(null)
    .scrollHandle(null)
    .enableAntialiasing(true)
    .spacing(0)
    .invalidPageColor(Color.WHITE) // Color for invalid pages
    .load()
```

## Scroll Handle

The scroll handle replaces the **ScrollBar** from the 1.x branch. No longer restricted to *
*RelativeLayout** since 2.1.0, **ScrollHandle** can be added with `Configurator#scrollHandle()`. Use
the default implementation with `.scrollHandle(new DefaultScrollHandle(this))`.

You can create custom scroll handles by implementing the **ScrollHandle** interface.

## Document Sources

Version 2.3.0 introduced _document sources_, with predefined sources like:

```kotlin
pdfView.fromUri(Uri)
pdfView.fromFile(File)
pdfView.fromBytes(byteArrayOf())
pdfView.fromStream(InputStream)
pdfView.fromAsset(String)
```

Custom providers can be integrated with `pdfView.fromSource(DocumentSource)`.

## Additional Options

### Bitmap Quality

Bitmaps are compressed with `RGB_565` by default for memory efficiency. Switch to `ARGB_8888` with
`pdfView.useBestQuality(true)`.

### Double-Tap Zooming

Default zoom levels are min (1), mid (1.75), and max (3). Adjust these with:

```kotlin
pdfView.setMinZoom(1.0f)
pdfView.setMidZoom(1.75f)
pdfView.setMaxZoom(3.0f)
```

## FAQ

### Why is the resulting APK large?

The library relies on PdfiumAndroid, which includes multiple native libraries (16 MB) for different
architectures. Google Play’s multiple APK feature allows for APK splits to reduce size.

### Why can’t I open PDFs from a URL?

Loading from URLs requires handling long-running processes, activity lifecycle awareness,
configuration, and caching, which is out of scope for this library.

### How can I fit the document to screen width?

```kotlin
Configurator.onRender { pages, pageWidth, pageHeight ->
    pdfView.fitToWidth() // Optionally specify a page
}
```

## License

Created with the help of [Joan Zapata’s android-pdfview](http://joanzapata.com/).

```
Licensed under the Apache License, Version 2.0. See LICENSE file for more details.
```

---

Maintainer: **Hazzatur**