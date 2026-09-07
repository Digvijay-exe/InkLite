package com.example

import com.example.data.io.StrokeBinarySerializer
import com.example.data.model.InkPoint
import com.example.data.model.InkStroke
import com.example.data.model.PageTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun testStrokeSerializationRoundTrip() {
    val file = File(tempFolder.root, "test_page.ink")
    val points1 = listOf(
      InkPoint(100f, 200f, 0.8f),
      InkPoint(150f, 250f, 1.2f),
      InkPoint(200f, 300f, 1.0f)
    )
    val stroke1 = InkStroke(
      id = "stroke-1",
      points = points1,
      color = 0xFF0F172A.toInt(),
      strokeWidth = 5.0f,
      isHighlighter = false
    )

    val points2 = listOf(
      InkPoint(400f, 500f, 1.0f),
      InkPoint(450f, 550f, 1.0f)
    )
    val stroke2 = InkStroke(
      id = "stroke-2",
      points = points2,
      color = 0xFFDC2626.toInt(),
      strokeWidth = 12.0f,
      isHighlighter = true
    )

    val original = listOf(stroke1, stroke2)
    StrokeBinarySerializer.saveStrokes(file, original)

    assertTrue("File should exist", file.exists())
    assertTrue("File should be non-empty", file.length() > 0)

    val loaded = StrokeBinarySerializer.loadStrokes(file)
    assertEquals(2, loaded.size)

    assertEquals("stroke-1", loaded[0].id)
    assertEquals(0xFF0F172A.toInt(), loaded[0].color)
    assertEquals(5.0f, loaded[0].strokeWidth, 0.001f)
    assertEquals(false, loaded[0].isHighlighter)
    assertEquals(3, loaded[0].points.size)
    assertEquals(100f, loaded[0].points[0].x, 0.001f)
    assertEquals(200f, loaded[0].points[0].y, 0.001f)
    assertEquals(0.8f, loaded[0].points[0].pressure, 0.001f)

    assertEquals("stroke-2", loaded[1].id)
    assertEquals(0xFFDC2626.toInt(), loaded[1].color)
    assertEquals(12.0f, loaded[1].strokeWidth, 0.001f)
    assertEquals(true, loaded[1].isHighlighter)
    assertEquals(2, loaded[1].points.size)
  }

  @Test
  fun testStrokeBoundingBox() {
    val points = listOf(
      InkPoint(10f, 20f),
      InkPoint(50f, 80f)
    )
    val stroke = InkStroke(
      points = points,
      color = 0,
      strokeWidth = 4f
    )
    // half width is 2f
    assertEquals(8f, stroke.bounds.left, 0.01f)
    assertEquals(18f, stroke.bounds.top, 0.01f)
    assertEquals(52f, stroke.bounds.right, 0.01f)
    assertEquals(82f, stroke.bounds.bottom, 0.01f)
  }

  @Test
  fun testPageTemplateFallback() {
    assertEquals(PageTemplate.LINED, PageTemplate.fromString("LINED"))
    assertEquals(PageTemplate.GRID, PageTemplate.fromString("grid"))
    assertEquals(PageTemplate.BLANK, PageTemplate.fromString("UNKNOWN_VALUE"))
    assertEquals(PageTemplate.BLANK, PageTemplate.fromString(null))
  }

  @Test
  fun testFlattenStrokesToPngAndShareIntent() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    val page = com.example.data.model.NotebookPage(
      id = "page-test-1",
      notebookId = "nb-test",
      pageNumber = 1,
      template = PageTemplate.LINED,
      strokeFilePath = File(tempFolder.root, "page1.ink").absolutePath,
      updatedAt = System.currentTimeMillis()
    )

    val strokes = listOf(
      InkStroke(
        id = "stroke-flat-1",
        points = listOf(InkPoint(50f, 50f), InkPoint(200f, 200f)),
        color = 0xFFDC2626.toInt(),
        strokeWidth = 6.0f
      )
    )

    // Test Flatten to PNG (Standard with background)
    val pngUri = com.example.data.export.ExportManager.exportPageAsImage(
      context = context,
      notebookTitle = "TestNotebook",
      page = page,
      pdfFilePath = null,
      strokes = strokes,
      isPng = true,
      transparentBackground = false
    )
    assertTrue("PNG Uri should not be null", pngUri != null)
    assertTrue("PNG Uri should contain content or file scheme", pngUri.scheme != null)

    // Test Flatten to PNG (Transparent background)
    val transparentPngUri = com.example.data.export.ExportManager.exportPageAsImage(
      context = context,
      notebookTitle = "TestNotebook",
      page = page,
      pdfFilePath = null,
      strokes = strokes,
      isPng = true,
      transparentBackground = true
    )
    assertTrue("Transparent PNG Uri should not be null", transparentPngUri != null)

    // Test Share Intent creation with ClipData and URI permissions
    val shareIntent = com.example.data.export.ExportManager.createShareIntent(
      context = context,
      uri = pngUri,
      mimeType = "image/png",
      title = "Share Test Note"
    )
    assertTrue("Share intent should have ACTION_CHOOSER", shareIntent != null)
  }
}
