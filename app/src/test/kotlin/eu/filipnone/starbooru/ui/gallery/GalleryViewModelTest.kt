package eu.filipnone.starbooru.ui.gallery

import androidx.lifecycle.SavedStateHandle
import eu.filipnone.starbooru.FakeApis
import eu.filipnone.starbooru.FakeServer
import eu.filipnone.starbooru.MainDispatcherRule
import eu.filipnone.starbooru.PostsCall
import eu.filipnone.starbooru.SERVER
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.TagCorrection
import eu.filipnone.starbooru.data.Account
import eu.filipnone.starbooru.data.AccountState
import eu.filipnone.starbooru.data.MemorySecretStore
import eu.filipnone.starbooru.page
import eu.filipnone.starbooru.posts
import eu.filipnone.starbooru.preferencesIn
import eu.filipnone.starbooru.signedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    private val backend = FakeServer(total = 150)
    private val apis = FakeApis(backend)
    private lateinit var account: Account

    private suspend fun gallery(
        query: String = "",
        saved: SavedStateHandle = SavedStateHandle(),
    ): GalleryViewModel {
        account = Account(preferencesIn(tmp.root, main.scope), MemorySecretStore(), apis)
        account.load()
        account.signedIn(SERVER, signedIn())
        return GalleryViewModel(account, query, saved)
    }

    private val GalleryViewModel.ids get() = state.value.posts.map { it.id }

    private fun call(
        page: UInt,
        limit: UInt = 60u,
        query: String = "",
        seed: Long? = null,
    ) = PostsCall(query, seed, page, limit)

    @Test
    fun theFirstPageLoadsOnCreation() =
        runTest {
            val saved = SavedStateHandle()
            val vm = gallery(query = "cat", saved = saved)
            assertFalse(vm.state.value.loaded)
            advanceUntilIdle()

            val s = vm.state.value
            assertEquals((1L..60L).toList(), vm.ids)
            assertTrue(s.loaded)
            assertFalse(s.loading)
            assertTrue(s.hasMore)
            assertNull(s.error)
            assertEquals(listOf(call(1u, query = "cat")), backend.postsCalls)
            assertEquals(60, saved.get<Int>("loaded"))
        }

    @Test
    fun loadMoreAppendsTheNextPage() =
        runTest {
            val vm = gallery()
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()

            assertEquals((1L..120L).toList(), vm.ids)
            assertEquals(listOf(call(1u), call(2u)), backend.postsCalls)
        }

    @Test
    fun loadMoreSkipsPostsAlreadyShown() =
        runTest {
            // A new upload pushes the next page back by a few posts.
            backend.posts = { c ->
                if (c.page == 1u) page(posts(1L..60L), hasMore = true) else page(posts(55L..114L), page = 2u, hasMore = false)
            }
            val vm = gallery()
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()

            assertEquals((1L..114L).toList(), vm.ids)
        }

    @Test
    fun loadMoreWhileLoadingAsksOnce() =
        runTest {
            val vm = gallery()
            vm.loadMore()
            vm.loadMore()
            advanceUntilIdle()
            assertEquals(listOf(call(1u)), backend.postsCalls)
        }

    @Test
    fun theLastPageStopsPaging() =
        runTest {
            backend.total = 100
            val vm = gallery()
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()
            assertFalse(vm.state.value.hasMore)
            assertEquals(100, vm.ids.size)

            vm.loadMore()
            advanceUntilIdle()
            assertEquals(2, backend.postsCalls.size)
        }

    @Test
    fun aFailureKeepsThePostsAndCanBeRetried() =
        runTest {
            val vm = gallery()
            advanceUntilIdle()
            val down = CoreException.Unreachable("timeout")
            backend.posts = { throw down }
            vm.loadMore()
            advanceUntilIdle()

            val s = vm.state.value
            assertEquals(down, s.error)
            assertEquals((1L..60L).toList(), vm.ids)
            assertFalse(s.loading)
            assertTrue(s.hasMore)

            backend.posts = { c -> backend.slice(c) }
            vm.loadMore()
            advanceUntilIdle()
            assertNull(vm.state.value.error)
            assertEquals((1L..120L).toList(), vm.ids)
            // The failed page is asked for again.
            assertEquals(listOf(call(1u), call(2u), call(2u)), backend.postsCalls)
        }

    @Test
    fun aFirstPageThatFailsStillCountsAsLoaded() =
        runTest {
            backend.posts = { throw CoreException.Unreachable("down") }
            val vm = gallery()
            advanceUntilIdle()
            assertTrue(vm.state.value.loaded)
            assertTrue(vm.state.value.posts.isEmpty())
            assertNotNull(vm.state.value.error)
        }

    @Test
    fun anExpiredSessionSignsOut() =
        runTest {
            backend.posts = { throw CoreException.Unauthorized("revoked") }
            val vm = gallery()
            advanceUntilIdle()
            assertTrue(vm.state.value.error is CoreException.Unauthorized)
            assertEquals(AccountState.SignedOut(SERVER, expired = true), account.state.value)
        }

    @Test
    fun reloadStartsOver() =
        runTest {
            val vm = gallery()
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()
            vm.reload()
            assertTrue(vm.state.value.posts.isEmpty())
            advanceUntilIdle()

            assertEquals((1L..60L).toList(), vm.ids)
            assertEquals(listOf(call(1u), call(2u), call(1u)), backend.postsCalls)
        }

    @Test
    fun correctionsComeFromTheFirstPage() =
        runTest {
            val fix = listOf(TagCorrection(from = "kat", to = "cat"))
            backend.posts = { c -> backend.slice(c).copy(corrected = if (c.page == 1u) fix else listOf(TagCorrection("x", "y"))) }
            val vm = gallery(query = "kat")
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()
            assertEquals(fix, vm.state.value.corrected)
        }

    // ---------- order:random ----------

    @Test
    fun aRandomOrderKeepsOneSeedForEveryPage() =
        runTest {
            val saved = SavedStateHandle()
            val vm = gallery(query = "order:random", saved = saved)
            val seed = vm.seed
            assertNotNull(seed)
            assertEquals(seed, saved.get<Long>("seed"))
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()

            assertEquals(
                listOf(call(1u, query = "order:random", seed = seed), call(2u, query = "order:random", seed = seed)),
                backend.postsCalls,
            )
        }

    @Test
    fun theSeedSurvivesTheProcess() =
        runTest {
            val saved = SavedStateHandle()
            val first = gallery(query = "cat order:random", saved = saved)
            val again = GalleryViewModel(account, "cat order:random", saved)
            assertEquals(first.seed, again.seed)
        }

    @Test
    fun otherOrdersHaveNoSeed() =
        runTest {
            val saved = SavedStateHandle()
            val vm = gallery(query = "cat order:score", saved = saved)
            assertNull(vm.seed)
            assertNull(saved.get<Long>("seed"))
            advanceUntilIdle()
            assertNull(backend.postsCalls.single().seed)
        }

    // ---------- coming back after the process died ----------

    @Test
    fun restoringLoadsInBigPagesThenGoesOnInTheUsualOnes() =
        runTest {
            backend.total = 1000
            val saved = SavedStateHandle(mapOf("loaded" to 200))
            val vm = gallery(saved = saved)
            advanceUntilIdle()

            assertEquals(listOf(call(1u, 180u), call(2u, 180u)), backend.postsCalls)
            assertEquals((1L..360L).toList(), vm.ids)
            assertTrue(vm.state.value.hasMore)
            assertTrue(vm.state.value.loaded)
            assertEquals(360, saved.get<Int>("loaded"))

            vm.loadMore()
            advanceUntilIdle()
            assertEquals(call(7u, 60u), backend.postsCalls.last())
            assertEquals((1L..420L).toList(), vm.ids)
        }

    @Test
    fun restoringASearchThatShrankKeepsWholePages() =
        runTest {
            backend.total = 250
            val vm = gallery(saved = SavedStateHandle(mapOf("loaded" to 200)))
            advanceUntilIdle()

            // 250 came back; the 240 of four whole pages stay, the rest comes with page 5.
            assertEquals((1L..240L).toList(), vm.ids)
            assertTrue(vm.state.value.hasMore)

            vm.loadMore()
            advanceUntilIdle()
            assertEquals(call(5u, 60u), backend.postsCalls.last())
            assertEquals((1L..250L).toList(), vm.ids)
            assertFalse(vm.state.value.hasMore)
        }

    @Test
    fun restoringShowsAPostThatComesTwiceOnce() =
        runTest {
            // A post uploaded after the first page pushes everything down by one.
            val before = (1L..1000L).toList()
            val after = listOf(5000L) + before
            backend.posts = { c ->
                val list = if (backend.postsCalls.size == 1) before else after
                val from = ((c.page - 1u) * c.limit).toInt()
                val ids = list.drop(from).take(c.limit.toInt())
                page(posts(ids), page = c.page, hasMore = from + ids.size < list.size)
            }
            val saved = SavedStateHandle().apply { set("loaded", 200) }
            val vm = gallery(saved = saved)
            advanceUntilIdle()

            // Post 180 ends page 1 and starts page 2: 359 posts, 300 of them in whole pages.
            assertEquals((1L..300L).toList(), vm.ids)
            assertEquals(300, saved.get<Int>("loaded"))
            assertTrue(vm.state.value.hasMore)

            vm.loadMore()
            advanceUntilIdle()
            assertEquals(call(6u, 60u), backend.postsCalls.last())
            assertEquals((1L..359L).toList(), vm.ids)
        }

    @Test
    fun restoringASearchShorterThanAPageKeepsItAll() =
        runTest {
            backend.total = 30
            val vm = gallery(saved = SavedStateHandle(mapOf("loaded" to 200)))
            advanceUntilIdle()
            assertEquals((1L..30L).toList(), vm.ids)
            assertFalse(vm.state.value.hasMore)
        }

    @Test
    fun restoringAPageOrLessLoadsNormally() =
        runTest {
            val vm = gallery(saved = SavedStateHandle(mapOf("loaded" to 60)))
            advanceUntilIdle()
            assertEquals(listOf(call(1u)), backend.postsCalls)
            assertEquals(60, vm.ids.size)
        }

    @Test
    fun restoringStopsAtThreeThousand() =
        runTest {
            backend.total = 10_000
            val vm = gallery(saved = SavedStateHandle(mapOf("loaded" to 9_000)))
            advanceUntilIdle()
            // 17 pages of 180 are the first to reach 3000.
            assertEquals(17, backend.postsCalls.size)
            assertTrue(backend.postsCalls.all { it.limit == 180u })
            assertEquals(3060, vm.ids.size)
        }

    @Test
    fun restoringWithARandomOrderUsesTheKeptSeed() =
        runTest {
            backend.total = 1000
            // Set one by one: in one mapOf, 200 would be read as a Long.
            val saved =
                SavedStateHandle().apply {
                    set("loaded", 200)
                    set("seed", 42L)
                }
            val vm = gallery(query = "order:random", saved = saved)
            advanceUntilIdle()
            assertEquals(42L, vm.seed)
            assertTrue(backend.postsCalls.all { it.seed == 42L })
        }

    // ---------- the foot of the gallery ----------

    @Test
    fun beforeTheFirstPageTheFootIsLoading() {
        // The moment before the first request, where it offered Load more.
        assertEquals(Foot.Loading, GalleryState().foot())
        assertEquals(Foot.Loading, GalleryState(loading = true).foot())
        assertEquals(Foot.Loading, GalleryState(posts = posts(1L..60L), loading = true, loaded = true).foot())
    }

    @Test
    fun theFootAfterAnAnswer() {
        val some = posts(1L..60L)
        assertEquals(Foot.More, GalleryState(posts = some, loaded = true).foot())
        assertEquals(Foot.End, GalleryState(posts = some, hasMore = false, loaded = true).foot())
        assertEquals(Foot.Empty, GalleryState(hasMore = false, loaded = true).foot())
        assertEquals(Foot.Empty, GalleryState(loaded = true).foot())
        assertEquals(Foot.Problem, GalleryState(error = CoreException.Unreachable("down"), loaded = true).foot())
        assertEquals(Foot.Problem, GalleryState(posts = some, error = CoreException.Unreachable("down"), loaded = true).foot())
    }

    @Test
    fun theFootIsLoadingBeforeTheRequestGoesOut() =
        runTest {
            // The request hasn't started yet, as when a spot is read first.
            val vm = gallery()
            assertFalse(vm.state.value.loading)
            assertEquals(Foot.Loading, vm.state.value.foot())
            advanceUntilIdle()
            assertEquals(Foot.More, vm.state.value.foot())
            vm.reload()
            assertEquals(Foot.Loading, vm.state.value.foot())
        }

    @Test
    fun theAnchorIsKept() =
        runTest {
            val saved = SavedStateHandle()
            val vm = gallery(saved = saved)
            assertNull(vm.anchor)
            vm.anchor = 17
            assertEquals(17L, saved.get<Long>("anchor"))
            assertEquals(17L, GalleryViewModel(account, "", saved).anchor)
        }
}
