package eu.filipnone.starbooru.ui.viewer

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A drag on the player's controls stays theirs: the stage around them never turns. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class OwnsDragsTest {
    @get:Rule val compose = createComposeRule()

    /** What a stage around the control heard: moves it could have taken, and moves it was kept from. */
    private class Heard {
        var free = 0
        var taken = 0
    }

    private fun stage(
        owns: Boolean,
        onTap: () -> Unit = {},
    ): Heard {
        val heard = Heard()
        compose.setContent {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    // As the viewer's stage does: a move taken below is left alone.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            if (change.isConsumed) heard.taken++ else heard.free++
                        }
                    }
                },
            ) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(240.dp, 48.dp)
                        .testTag("bar")
                        .then(if (owns) Modifier.ownsDrags() else Modifier)
                        .clickable(onClick = onTap),
                )
            }
        }
        return heard
    }

    @Test
    fun aDragOnTheControlsIsTheirs() {
        val heard = stage(owns = true)
        compose.onNodeWithTag("bar").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertTrue("the stage was kept from the drag", heard.taken > 0)
        // Only the first moves, within the touch slop, reach it free.
        assertTrue("free moves: ${heard.free}", heard.free <= 2)
    }

    @Test
    fun withoutItTheStageWouldTakeTheDrag() {
        val heard = stage(owns = false)
        compose.onNodeWithTag("bar").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(0, heard.taken)
        assertFalse(heard.free == 0)
    }

    @Test
    fun aTapStillReachesTheButton() {
        var taps = 0
        stage(owns = true) { taps++ }
        compose.onNodeWithTag("bar").performClick()
        compose.waitForIdle()
        assertEquals(1, taps)
    }
}
