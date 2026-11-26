package eu.filipnone.starbooru.ui.viewer

import androidx.lifecycle.SavedStateHandle
import eu.filipnone.starbooru.FakeApis
import eu.filipnone.starbooru.FakeServer
import eu.filipnone.starbooru.MainDispatcherRule
import eu.filipnone.starbooru.SERVER
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.Neighbors
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
    ): ViewerViewModel {
        account = Account(preferencesIn(tmp.root, main.scope), MemorySecretStore(), apis)
        account.load()
        account.signedIn(SERVER, signedIn())
        return ViewerViewModel(account, id, query, seed, saved)
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

            assertEquals(listOf("post 5", "neighbors 5", "neighbors 5", "post 4", "post 6"), backend.log)
            assertEquals(Neighbors(prev = 4, next = 6), vm.state.value.neighbors)
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
