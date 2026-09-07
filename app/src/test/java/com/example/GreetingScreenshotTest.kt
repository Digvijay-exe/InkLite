package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.Notebook
import com.example.data.model.PageTemplate
import com.example.ui.screens.NotebookListScreen
import com.example.ui.theme.InkPalette
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.InkLiteUiState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleNotebooks = listOf(
      Notebook(
        id = "1",
        title = "Quick Start Guide",
        coverColor = 0xFF0284C7.toInt(),
        template = PageTemplate.LINED,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        pageCount = 2
      )
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        NotebookListScreen(
          state = InkLiteUiState(notebooks = sampleNotebooks),
          onOpenNotebook = {},
          onCreateNotebook = { _, _, _ -> },
          onImportPdf = { _, _ -> },
          onRenameNotebook = { _, _ -> },
          onDeleteNotebook = {},
          onClearStatusMessage = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
