package eu.filipnone.starbooru.ui.viewer

import androidx.lifecycle.SavedStateHandle
import eu.filipnone.starbooru.FakeApis
import eu.filipnone.starbooru.FakeServer
import eu.filipnone.starbooru.MainDispatcherRule
import eu.filipnone.starbooru.SERVER
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.Neighbors
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.data.Account
import eu.filipnone.starbooru.data.MemorySecretStore
import eu.filipnone.starbooru.post
import eu.filipnone.starbooru.preferencesIn
import eu.filipnone.starbooru.signedIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ViewerViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    private val backend = FakeServer(total = 10)
    private val apis = FakeApis(backend)
    private lateinit var account: Account

    private suspend fun viewer(
        id: Long = 5,
        query: String = "",
        seed: Long? = null,
        saved: SavedStateHandle = SavedStateHandle(),
        first: Post? = null,
    ): ViewerViewModel {
        account = Account(preferencesIn(tmp.root, main.scope), MemorySecretStore(), apis)
        account.load()
        account.signedIn(SERVER, signedIn())
        return ViewerViewModel(account, id, query, seed, saved, first)
    }

    @Test
    fun loadsThePostThenItsNeighboursThenTheirPosts() =
        runTest {
            val vm = viewer()
            advanceUntilIdle()

            val s = vm.state.value
            assertEquals(listOf("post 5", "neighbors 5", "post 4", "post 6"), backend.log)
            assertEquals(post(5), s.post)
            assertEquals(Neighbors(prev = 4, next = 6), s.neighbors)
            assertEquals(post(4), s.prev)
            assertEquals(post(6), s.next)
            assertNull(s.error)
        }

    @Test
    fun thePostShowsBeforeTheNeighboursAnswer() =
        runTest {
            val near = CompletableDeferred<Neighbors>()
            backend.neighbors = { _, _, _ -> near.await() }
            val vm = viewer()
            runCurrent()
            assertEquals(post(5), vm.state.value.post)
            assertNull(vm.state.value.neighbors)

            near.complete(Neighbors(prev = 4, next = 6))
            advanceUntilIdle()
            assertEquals(post(6), vm.state.value.next)
        }

    @Test
    fun theSearchAndSeedGoWithTheNeighbours() =
        runTest {
            var asked: Pair<String, Long?>? = null
            backend.neighbors = { _, q, seed ->
                asked = q to seed
                Neighbors(null, null)
            }
            viewer(query = "cat order:random", seed = 42)
            advanceUntilIdle()
            assertEquals("cat order:random" to 42L, asked)
        }

    @Test
    fun goingNextMovesAndIsKept() =
        runTest {
            val saved = SavedStateHandle()
            val vm = viewer(saved = saved)
            advanceUntilIdle()
            backend.log.clear()

            assertTrue(vm.go(Side.Next))
            assertEquals(6L, vm.state.value.id)
            assertEquals(6L, saved.get<Long>("id"))
            // Fetched ahead: it shows at once.
            assertEquals(post(6), vm.state.value.post)

            advanceUntilIdle()
            val s = vm.state.value
            assertEquals(Neighbors(prev = 5, next = 7), s.neighbors)
            assertEquals(post(5), s.prev)
            assertEquals(post(7), s.next)
            // Only what wasn't known yet.
            assertEquals(listOf("neighbors 6", "post 7"), backend.log)
        }

    @Test
    fun goingPreviousMovesBack() =
        runTest {
            val saved = SavedStateHandle()
            val vm = viewer(saved = saved)
            advanceUntilIdle()

            assertTrue(vm.go(Side.Prev))
            advanceUntilIdle()
            assertEquals(4L, vm.state.value.id)
            assertEquals(4L, saved.get<Long>("id"))
            assertEquals(post(3), vm.state.value.prev)
            assertEquals(post(5), vm.state.value.next)
        }

    @Test
    fun goingBackAndForthAsksNothingTwice() =
        runTest {
            val vm = viewer()
            advanceUntilIdle()
            vm.go(Side.Next)
            advanceUntilIdle()
            backend.log.clear()

            vm.go(Side.Prev)
            assertEquals(Neighbors(prev = 4, next = 6), vm.state.value.neighbors)
            advanceUntilIdle()
            assertTrue(backend.log.isEmpty())
            assertEquals(post(4), vm.state.value.prev)
        }

    @Test
    fun thereIsNothingAfterTheLastPost() =
        runTest {
            val saved = SavedStateHandle()
            val vm = viewer(id = 10, saved = saved)
            advanceUntilIdle()

            assertFalse(vm.go(Side.Next))
            assertEquals(10L, vm.state.value.id)
            assertNull(saved.get<Long>("id"))
            assertNull(vm.state.value.next)
            assertTrue(vm.go(Side.Prev))
        }

    @Test
    fun thereIsNothingBeforeTheFirstPost() =
        runTest {
            val vm = viewer(id = 1)
            advanceUntilIdle()
            assertFalse(vm.go(Side.Prev))
            assertEquals(1L, vm.state.value.id)
        }

    @Test
    fun nothingMovesBeforeTheNeighboursAreKnown() =
        runTest {
            val vm = viewer()
            assertFalse(vm.go(Side.Next))
            assertFalse(vm.go(Side.Prev))
        }

    @Test
    fun theKeptPostComesBackAfterTheProcess() =
        runTest {
            val vm = viewer(id = 5, saved = SavedStateHandle(mapOf("id" to 7L)))
            assertEquals(7L, vm.state.value.id)
            advanceUntilIdle()
            assertEquals(post(7), vm.state.value.post)
        }

    @Test
    fun aMissingPostShowsTheErrorAndCanBeRetried() =
        runTest {
            val vm = viewer(id = 99)
            advanceUntilIdle()
            val e = vm.state.value.error
            assertTrue(e is CoreException.Api)
            assertNull(vm.state.value.post)

            backend.total = 100
            vm.retry()
            advanceUntilIdle()
            assertNull(vm.state.value.error)
            assertEquals(post(99), vm.state.value.post)
        }

    @Test
    fun aChangedPostReplacesItWhereverItShows() =
        runTest {
            val vm = viewer()
            advanceUntilIdle()
            val liked = post(6).copy(score = 1, myVote = 1)
            vm.replace(liked)
            assertEquals(liked, vm.state.value.next)

            vm.go(Side.Next)
            assertEquals(liked, vm.state.value.post)
        }

    // ---------- when something fails ----------

    @Test
    fun aPreviousPostThatFailsLeavesTheNext() =
        runTest {
            backend.post = { id -> if (id == 4L) throw CoreException.Api(404u, "not_found", "gone") else post(id) }
            val vm = viewer()
            advanceUntilIdle()

            assertNull(vm.state.value.prev)
            assertEquals(post(6), vm.state.value.next)
            assertNull(vm.state.value.error)
        }

    @Test
    fun aNextPostThatFailsLeavesThePrevious() =
        runTest {
            backend.post = { id -> if (id == 6L) throw CoreException.Unreachable("timeout") else post(id) }
            val vm = viewer()
            advanceUntilIdle()

            assertEquals(post(4), vm.state.value.prev)
            assertNull(vm.state.value.next)
        }

    @Test
    fun neighboursThatFailAreAskedForAgainOnTheNextSwipe() =
        runTest {
            backend.neighbors = { _, _, _ -> throw CoreException.Unreachable("timeout") }
            val vm = viewer()
            advanceUntilIdle()
            assertEquals(post(5), vm.state.value.post)
            assertNull(vm.state.value.neighbors)

            backend.neighbors = { id, _, _ -> Neighbors(prev = id - 1, next = id + 1) }
            assertFalse(vm.go(Side.Next))
            advanceUntilIdle()

            assertEquals(listOf("post 5", "neighbors 5", "neighbors 5", "post 6"), backend.log.take(4))
            // The swipe that asked counts once they come, as on the web.
            assertEquals(6L, vm.state.value.id)
            assertEquals(Neighbors(prev = 5, next = 7), vm.state.value.neighbors)
            assertTrue(vm.go(Side.Next))
        }

    @Test
    fun aSwipeWhileTheNeighboursAreComingAsksNothingMore() =
        runTest {
            val near = CompletableDeferred<Neighbors>()
            backend.neighbors = { _, _, _ -> near.await() }
            val vm = viewer()
            runCurrent()

            assertFalse(vm.go(Side.Next))
            assertFalse(vm.go(Side.Prev))
            runCurrent()
            assertEquals(1, backend.log.count { it == "neighbors 5" })

            near.complete(Neighbors(prev = 4, next = 6))
            advanceUntilIdle()
            assertTrue(vm.go(Side.Next))
        }

    @Test
    fun aSwipeAfterAPostThatFailedAsksForItAgain() =
        runTest {
            backend.total = 4
            val vm = viewer()
            advanceUntilIdle()
            assertTrue(vm.state.value.error is CoreException.Api)

            backend.total = 10
            assertFalse(vm.go(Side.Next))
            advanceUntilIdle()
            assertEquals(post(5), vm.state.value.post)
            assertNull(vm.state.value.error)
        }

    // ---------- the post a tile hands over ----------

    @Test
    fun aPostHandedOverShowsBeforeAnyAnswer() =
        runTest {
            val handed = post(5).copy(score = 3)
            val vm = viewer(first = handed)
            // Before anything ran: the first frame already has it.
            assertEquals(handed, vm.state.value.post)
            assertNull(vm.state.value.error)

            advanceUntilIdle()
            // Then asked for again: the server's copy wins.
            assertEquals(post(5), vm.state.value.post)
            assertEquals("post 5", backend.log.first())
        }

    @Test
    fun aPostHandedOverStaysWhenTheServerCantBeReached() =
        runTest {
            backend.post = { id -> if (id == 5L) throw CoreException.Unreachable("timeout") else post(id) }
            val handed = post(5).copy(score = 3)
            val vm = viewer(first = handed)
            advanceUntilIdle()

            assertEquals(handed, vm.state.value.post)
            assertNull(vm.state.value.error)
            // The rest still comes.
            assertEquals(post(6), vm.state.value.next)
        }

    @Test
    fun aPostHandedOverForAnotherIsIgnored() =
        runTest {
            val vm = viewer(id = 5, first = post(6))
            assertNull(vm.state.value.post)
            advanceUntilIdle()
            assertEquals(post(5), vm.state.value.post)
        }

    @Test
    fun aPostHandedOverDoesntOverrideTheOneKept() =
        runTest {
            // Back after the system closed the app, on post 7 by then.
            val vm = viewer(id = 5, saved = SavedStateHandle(mapOf("id" to 7L)), first = post(5))
            assertEquals(7L, vm.state.value.id)
            assertNull(vm.state.value.post)
        }

    // ---------- swipes made before the sides are known ----------

    @Test
    fun aSwipeBeforeTheNeighboursAnswerMovesOnceTheyDo() =
        runTest {
            val near = CompletableDeferred<Neighbors>()
            backend.neighbors = { _, _, _ -> near.await() }
            val vm = viewer()
            runCurrent()

            assertFalse(vm.go(Side.Next))
            assertEquals(5L, vm.state.value.id)

            backend.neighbors = { id, _, _ -> Neighbors(prev = id - 1, next = id + 1) }
            near.complete(Neighbors(prev = 4, next = 6))
            advanceUntilIdle()
            assertEquals(6L, vm.state.value.id)
            assertEquals(post(6), vm.state.value.post)
        }

    @Test
    fun swipesBeforeTheNeighboursAnswerAllCount() =
        runTest {
            val near = CompletableDeferred<Neighbors>()
            backend.neighbors = { id, _, _ -> if (id == 5L) near.await() else Neighbors(prev = id - 1, next = id + 1) }
            val vm = viewer()
            runCurrent()

            vm.go(Side.Next)
            vm.go(Side.Next)
            vm.go(Side.Next)
            vm.go(Side.Prev)
            near.complete(Neighbors(prev = 4, next = 6))
            advanceUntilIdle()
            // Three on, one back.
            assertEquals(7L, vm.state.value.id)
        }

    @Test
    fun swipesWhileThePostIsStillComingCount() =
        runTest {
            // Swiped past the posts fetched ahead: the one on show is still on its way.
            val coming = CompletableDeferred<Unit>()
            backend.post = { id ->
                if (id == 5L) coming.await()
                post(id)
            }
            val vm = viewer()
            runCurrent()
            assertNull(vm.state.value.post)

            assertFalse(vm.go(Side.Next))
            assertFalse(vm.go(Side.Next))
            coming.complete(Unit)
            advanceUntilIdle()
            assertEquals(7L, vm.state.value.id)
        }

    @Test
    fun waitingSwipesStopAtTheEnd() =
        runTest {
            val near = CompletableDeferred<Neighbors>()
            backend.neighbors =
                { id, _, _ -> if (id == 9L) near.await() else Neighbors(prev = id - 1, next = (id + 1).takeIf { it <= 10 }) }
            val vm = viewer(id = 9)
            runCurrent()

            repeat(4) { vm.go(Side.Next) }
            near.complete(Neighbors(prev = 8, next = 10))
            advanceUntilIdle()
            assertEquals(10L, vm.state.value.id)

            // Nothing left over: going back now goes back one.
            assertTrue(vm.go(Side.Prev))
            advanceUntilIdle()
            assertEquals(9L, vm.state.value.id)
        }

    @Test
    fun waitingSwipesAreDroppedWhenTheNeighboursFail() =
        runTest {
            val near = CompletableDeferred<Neighbors>()
            backend.neighbors = { _, _, _ -> near.await() }
            val vm = viewer()
            runCurrent()

            vm.go(Side.Next)
            near.completeExceptionally(CoreException.Unreachable("timeout"))
            advanceUntilIdle()
            assertEquals(5L, vm.state.value.id)

            // A later answer doesn't replay the swipe.
            backend.neighbors = { id, _, _ -> Neighbors(prev = id - 1, next = id + 1) }
            vm.retry()
            advanceUntilIdle()
            assertEquals(5L, vm.state.value.id)
        }

    @Test
    fun aBurstOfSwipesIsCapped() =
        runTest {
            backend.total = 40
            val near = CompletableDeferred<Neighbors>()
            backend.neighbors = { id, _, _ -> if (id == 5L) near.await() else Neighbors(prev = id - 1, next = id + 1) }
            val vm = viewer()
            runCurrent()

            repeat(20) { vm.go(Side.Next) }
            near.complete(Neighbors(prev = 4, next = 6))
            advanceUntilIdle()
            assertEquals(5L + MOST_QUEUED, vm.state.value.id)
        }

    // ---------- what shows beside the post at once ----------

    @Test
    fun goingNextShowsThePostLeftBeforeIt() =
        runTest {
            val vm = viewer()
            advanceUntilIdle()

            vm.go(Side.Next)
            val s = vm.state.value
            assertEquals(post(6), s.post)
            assertEquals(post(5), s.prev)
            assertNull(s.next)
            assertNull(s.neighbors)

            advanceUntilIdle()
            assertEquals(post(5), vm.state.value.prev)
            assertEquals(post(7), vm.state.value.next)
        }

    @Test
    fun goingPreviousShowsThePostLeftAfterIt() =
        runTest {
            val vm = viewer()
            advanceUntilIdle()

            vm.go(Side.Prev)
            val s = vm.state.value
            assertEquals(post(4), s.post)
            assertEquals(post(5), s.next)
            assertNull(s.prev)
        }

    @Test
    fun goingBackToAKnownPostShowsBothSidesAtOnce() =
        runTest {
            val vm = viewer()
            advanceUntilIdle()
            vm.go(Side.Next)
            advanceUntilIdle()

            vm.go(Side.Prev)
            val s = vm.state.value
            assertEquals(post(5), s.post)
            assertEquals(post(4), s.prev)
            assertEquals(post(6), s.next)
            assertEquals(Neighbors(prev = 4, next = 6), s.neighbors)
        }

    @Test
    fun theServerHasTheLastWordOnTheSides() =
        runTest {
            // 5 has left the search since: 6 now follows 3.
            backend.neighbors = { id, _, _ -> if (id == 6L) Neighbors(prev = 3, next = 7) else Neighbors(prev = id - 1, next = id + 1) }
            val vm = viewer()
            advanceUntilIdle()

            vm.go(Side.Next)
            assertEquals(post(5), vm.state.value.prev)
            advanceUntilIdle()
            assertEquals(post(3), vm.state.value.prev)
            assertEquals(Neighbors(prev = 3, next = 7), vm.state.value.neighbors)
        }
}
