package com.cprint.app.driver

import android.app.Application
import com.cprint.app.domain.model.Orientation
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrintPageOrientationTest {
    @Test
    fun portraitSheetFitsWideDocumentWithoutStretching() {
        assertPageCorners(
            sourceWidth = 400,
            sourceHeight = 200,
            orientation = Orientation.PORTRAIT,
            expectedCorners = floatArrayOf(0f, 100f, 200f, 100f, 0f, 200f, 200f, 200f)
        )
    }

    @Test
    fun landscapeSheetRotatesWideDocumentIntoPaperFeedOrder() {
        assertPageCorners(
            sourceWidth = 300,
            sourceHeight = 200,
            orientation = Orientation.LANDSCAPE,
            expectedCorners = floatArrayOf(0f, 300f, 0f, 0f, 200f, 300f, 200f, 0f)
        )
    }

    @Test
    fun landscapeSheetCentersTallDocumentWithoutStretching() {
        assertPageCorners(
            sourceWidth = 200,
            sourceHeight = 400,
            orientation = Orientation.LANDSCAPE,
            expectedCorners = floatArrayOf(0f, 200f, 0f, 100f, 200f, 200f, 200f, 100f)
        )
    }

    private fun assertPageCorners(
        sourceWidth: Int,
        sourceHeight: Int,
        orientation: Orientation,
        expectedCorners: FloatArray
    ) {
        val width = sourceWidth.toFloat()
        val height = sourceHeight.toFloat()
        val corners = floatArrayOf(0f, 0f, width, 0f, 0f, height, width, height)
        PrintDriverEngine.pageToRasterMatrix(sourceWidth, sourceHeight, 200, 300, orientation)
            .mapPoints(corners)
        assertArrayEquals(expectedCorners, corners, 0.01f)
    }
}
