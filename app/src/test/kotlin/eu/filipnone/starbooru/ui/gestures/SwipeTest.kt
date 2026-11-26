package eu.filipnone.starbooru.ui.gestures

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The web's `logic/swipe.rs` and `logic/viewer.rs` tests, and the reference's pull down to close. */
class SwipeTest {
    private fun trail(vararg points: Pair<Long, Float>) = points.map { (t, at) -> TrailPoint(t, at, at) }

    // ---------- a gallery tile ----------

    @Test
    fun aFingerHasToGoSomewhereAndMostlySideways() {
        assertEquals(SwipeAxis.Unknown, TileSwipe.axis(3f, 4f))
        assertEquals(SwipeAxis.Sideways, TileSwipe.axis(20f, 4f))
        assertEquals(SwipeAxis.Sideways, TileSwipe.axis(-20f, 10f))
        // About as far down as along: the gallery's scroll.
        assertEquals(SwipeAxis.Scroll, TileSwipe.axis(12f, 11f))
        assertEquals(SwipeAxis.Scroll, TileSwipe.axis(0f, -30f))
        assertEquals(SwipeAxis.Unknown, TileSwipe.axis(Float.NaN, 40f))
    }

    @Test
    fun aSideWithNothingToDoHardlyGives() {
        assertEquals(-80f, TileSwipe.follow(-80f, true), 0f)
        assertEquals(-12f, TileSwipe.follow(-80f, false), 1e-5f)
    }

    @Test
    fun lettingGoFarEnoughOrWithAFlickDoesIt() {
        val both = { _: Side -> true }
        val leftOnly = { s: Side -> s == Side.Left }
        // A 200 dp tile needs 80 dp; a narrow one 56, a wide one 120.
        assertEquals(80f, TileSwipe.reach(200f), 1e-5f)
        assertEquals(56f, TileSwipe.reach(90f), 0f)
        assertEquals(120f, TileSwipe.reach(900f), 0f)
        assertEquals(Side.Left, TileSwipe.release(-85f, 200f, 0f, both))
        assertEquals(Side.Right, TileSwipe.release(85f, 200f, 0f, both))
        assertNull(TileSwipe.release(-60f, 200f, 0f, both))
        // A flick that way, past a little.
        assertEquals(Side.Left, TileSwipe.release(-30f, 200f, -0.8f, both))
        assertNull(TileSwipe.release(-20f, 200f, -0.8f, both))
        // A flick back the other way isn't one.
        assertNull(TileSwipe.release(-30f, 200f, 0.8f, both))
        // Nothing chosen for that side.
        assertNull(TileSwipe.release(85f, 200f, 0f, leftOnly))
        assertNull(TileSwipe.release(Float.NaN, 200f, Float.NaN, both))
    }

    @Test
    fun theSpeedIsTheLastTenthOfASecond() {
        assertEquals(0f, speedX(emptyList()), 0f)
        assertEquals(0f, speedX(trail(5L to 10f)), 0f)
        // A slow start doesn't count against a fast end.
        val points = trail(0L to 0f, 300L to 10f, 350L to 30f, 400L to 80f)
        assertEquals(70f / 100f, speedX(points), 1e-6f)
        assertEquals(70f / 100f, speedY(points), 1e-6f)
        // A clock that went back gives no speed.
        assertEquals(0f, speedX(trail(400L to 0f, 300L to 50f)), 0f)
    }

    @Test
    fun aTrailKeepsItsLastFew() {
        var points = emptyList<TrailPoint>()
        for (t in 0L until 20L) points = trailWith(points, TrailPoint(t, t.toFloat(), 0f))
        assertEquals(TRAIL_LENGTH, points.size)
        assertEquals(19L, points.last().timeMillis)
        assertEquals(12L, points.first().timeMillis)
    }

    @Test
    fun eachSideDoesItsOwnAction() {
        assertEquals(SwipeAction.Delete, TileSwipe.action(Side.Left, SwipeAction.Delete, SwipeAction.Like))
        assertEquals(SwipeAction.Like, TileSwipe.action(Side.Right, SwipeAction.Delete, SwipeAction.Like))
        assertEquals(Side.Left, Side.of(-0.5f))
        assertEquals(Side.Right, Side.of(0f))
    }

    @Test
    fun actionsAreStoredAsTheWebStoresThem() {
        assertEquals(listOf("off", "like", "select", "share", "download", "delete"), SwipeAction.entries.map { it.key })
        for (action in SwipeAction.entries) assertEquals(action, SwipeAction.of(action.key))
        // An unknown one, from a newer version, does nothing.
        assertEquals(SwipeAction.Off, SwipeAction.of("teleport"))
        assertEquals(SwipeAction.Off, SwipeAction.of(null))
    }

    // ---------- the viewer's stage ----------

    @Test
    fun aFingerOnTheStageHasToGoSomewhereFirst() {
        assertNull(ViewerSwipe.sideways(3f, 4f))
        assertEquals(true, ViewerSwipe.sideways(12f, 3f))
        assertEquals(false, ViewerSwipe.sideways(-3f, 12f))
        assertNull(ViewerSwipe.sideways(Float.NaN, 0f))
    }

    @Test
    fun theStageTakesSwipesAndPullsButNotScrolls() {
        assertEquals(StageDrag.Undecided, ViewerSwipe.drag(3f, 4f, zoomed = false, pullable = true))
        assertEquals(StageDrag.Sideways, ViewerSwipe.drag(-20f, 5f, zoomed = false, pullable = false))
        assertEquals(StageDrag.Down, ViewerSwipe.drag(5f, 20f, zoomed = false, pullable = true))
        // Further down the page, or upwards, the page scrolls.
        assertEquals(StageDrag.None, ViewerSwipe.drag(5f, 20f, zoomed = false, pullable = false))
        assertEquals(StageDrag.None, ViewerSwipe.drag(5f, -20f, zoomed = false, pullable = true))
        // Zoomed in, the picture pans instead.
        assertEquals(StageDrag.None, ViewerSwipe.drag(-20f, 5f, zoomed = true, pullable = true))
    }

    @Test
    fun aSwipeTurnsWhenFarEnoughOrFlung() {
        // 22% of the stage, at most 140 dp.
        assertEquals(Turn.Next, ViewerSwipe.turn(-90f, 0f, 400f))
        assertEquals(Turn.Previous, ViewerSwipe.turn(90f, 0f, 400f))
        assertNull(ViewerSwipe.turn(-80f, 0f, 400f))
        assertNull(ViewerSwipe.turn(-139f, 0f, 1200f))
        assertEquals(Turn.Next, ViewerSwipe.turn(-140f, 0f, 1200f))
        // Flicked: short, but fast and the same way.
        assertEquals(Turn.Next, ViewerSwipe.turn(-40f, -0.8f, 1200f))
        assertNull(ViewerSwipe.turn(-40f, 0.8f, 1200f))
        assertNull(ViewerSwipe.turn(-20f, -2f, 1200f))
        assertNull(ViewerSwipe.turn(Float.NaN, Float.NaN, 400f))
        // A stage not measured yet takes the most.
        assertEquals(140f, ViewerSwipe.reach(Float.NaN), 0f)
        assertEquals(-25f, ViewerSwipe.resist(-100f, true), 0f)
        assertEquals(-100f, ViewerSwipe.resist(-100f, false), 0f)
    }

    @Test
    fun theStagesSpeedIsTheLastMoments() {
        assertEquals(0f, speedX(emptyList()), 0f)
        assertEquals(0f, speedX(trail(5L to 10f)), 0f)
        // Slow at first, then 60 dp in the last 60 ms.
        val points = trail(0L to 0f, 90L to 5f, 140L to 10f, 200L to 70f)
        assertEquals(60f / 60f, speedX(points), 1e-6f)
    }

    // ---------- pulling down to close ----------

    @Test
    fun aPullFarEnoughOrFlungCloses() {
        // 18% of the stage, at most 160 dp.
        assertEquals(144f, PullToClose.reach(800f), 1e-4f)
        assertEquals(160f, PullToClose.reach(1200f), 0f)
        assertTrue(PullToClose.closes(150f, 0f, 800f))
        assertFalse(PullToClose.closes(100f, 0f, 800f))
        // Flicked down, past a little.
        assertTrue(PullToClose.closes(50f, 0.8f, 800f))
        assertFalse(PullToClose.closes(30f, 0.8f, 800f))
        assertFalse(PullToClose.closes(50f, -0.8f, 800f))
        assertFalse(PullToClose.closes(Float.NaN, Float.NaN, 800f))
    }

    @Test
    fun aPulledPictureFollowsDownFullyAndElsewhereLess() {
        assertEquals(Offset(20f, 100f), PullToClose.follow(40f, 100f))
        assertEquals(Offset(20f, -20f), PullToClose.follow(40f, -100f))
        // Caught on its way back: on from where it was.
        assertEquals(Offset(10f, -2f), PullToClose.follow(0f, -30f, base = Offset(10f, 20f)))
        assertEquals(Offset(30f, 50f), PullToClose.follow(40f, 30f, base = Offset(10f, 20f)))
    }

    @Test
    fun thePictureShrinksAndThePageFadesAsItComesDown() {
        assertEquals(0.5f, PullToClose.pull(200f, 800f), 1e-6f)
        assertEquals(0f, PullToClose.pull(-50f, 800f), 0f)
        assertEquals(1f, PullToClose.pull(1000f, 800f), 0f)
        assertEquals(1f, PullToClose.pull(10f, 0f), 0f)
        assertEquals(0f, PullToClose.pull(Float.NaN, 800f), 0f)
        assertEquals(1f, PullToClose.scale(0f), 0f)
        assertEquals(0.875f, PullToClose.scale(0.5f), 1e-6f)
        assertEquals(0.75f, PullToClose.scale(1f), 1e-6f)
        assertEquals(1f, PullToClose.scale(Float.NaN), 0f)
    }
}
