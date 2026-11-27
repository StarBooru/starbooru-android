package eu.filipnone.starbooru.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a tile's picture may move: only to or from the viewer. */
class OnStageTest {
    @Test
    fun aViewerOnTopCountsBeforeItsPictureIsDrawn() {
        assertTrue(OnStage { true }.active)
    }

    @Test
    fun aViewerLeavingCountsWhileItsPicturesAreStillDrawn() {
        val stage = OnStage { false }
        stage.pictures = 1
        assertTrue(stage.active)
        stage.pictures = 0
        assertFalse(stage.active)
    }

    @Test
    fun galleryToGalleryHasNoViewer() {
        assertFalse(OnStage { false }.active)
    }
}
