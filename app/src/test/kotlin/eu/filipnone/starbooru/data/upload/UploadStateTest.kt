package eu.filipnone.starbooru.data.upload

import eu.filipnone.starbooru.core.Rating
import eu.filipnone.starbooru.core.TagProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The upload list's moves, as the web's `logic/queue.rs` tests them. */
class UploadStateTest {
    private fun file(
        id: String,
        size: Long = 10,
        tags: String = "",
    ) = UploadItem(id, UploadSource.Local("$id.png", size, "image/png"), tags = tags)

    private fun link(
        id: String,
        url: String,
        tags: String = "",
    ) = UploadItem(id, UploadSource.Link(url), tags = tags)

    private fun UploadState.adding(vararg new: UploadItem): UploadState = add(new.toList()).first

    private fun batch(tags: String = "") = Batch(tags = tags, rating = Rating.SENSITIVE, source = " https://src.example/1 ")

    private fun UploadState.started(tags: String = ""): UploadState = copy(batch = batch(tags)).start(explain = { it.word })

    private fun UploadState.statusOf(id: String): UploadStatus? = get(id)?.status

    @Test
    fun aDraftWaitsItsTurnThenGoesAndEndsDone() {
        var q = UploadState().adding(file("a", tags = "cat"))
        assertEquals(UploadStatus.Draft, q.statusOf("a"))
        assertEquals(1, q.drafts)
        q = q.started("sky")
        assertEquals(UploadStatus.Waiting, q.statusOf("a"))
        val plan = q.get("a")?.plan
        assertEquals(listOf("sky", "cat"), plan?.tags)
        assertEquals(Rating.SENSITIVE, plan?.rating)
        assertEquals("https://src.example/1", plan?.source)
        val (sending, claimed) = q.claim(AT_ONCE)
        assertEquals(listOf("a"), claimed.map { it.id })
        assertEquals(1, claimed.single().attempt)
        assertEquals(UploadStatus.Sending(total = 10), sending.statusOf("a"))
        assertEquals(1, sending.pending)
        val (done, counted) = sending.finish("a", 1, Outcome.Posted(70))
        assertTrue(counted)
        assertEquals(UploadStatus.Done(70), done.statusOf("a"))
        assertEquals(0, done.pending)
    }

    @Test
    fun noMoreThanTwoGoAtOnce() {
        var q = UploadState().adding(*(0 until 5).map { file("f$it") }.toTypedArray()).started()
        val (q1, first) = q.claim(AT_ONCE)
        assertEquals(listOf("f0", "f1"), first.map { it.id })
        // Nothing more while both are on their way.
        val (q2, none) = q1.claim(AT_ONCE)
        assertTrue(none.isEmpty())
        q = q2.finish("f0", 1, Outcome.Duplicate(3, inRecycleBin = true)).first
        val (_, then) = q.claim(AT_ONCE)
        assertEquals(listOf("f2"), then.map { it.id })
        assertEquals(UploadStatus.Duplicate(3, inRecycleBin = true), q.statusOf("f0"))
    }

    @Test
    fun aFailedOrCancelledFileCanGoAgainAndALateEndChangesNothing() {
        var q = UploadState().adding(file("a")).started()
        q = q.claim(1).first
        q = q.finish("a", 1, Outcome.Failed("network")).first
        assertEquals(UploadStatus.Failed("network"), q.statusOf("a"))
        assertTrue(q.get("a")?.retryable == true)
        q = q.retry("a")
        assertEquals(UploadStatus.Waiting, q.statusOf("a"))
        q = q.claim(1).first
        assertEquals(2, q.get("a")?.attempt)
        q = q.cancel("a")
        assertEquals(UploadStatus.Stopped, q.statusOf("a"))
        assertFalse(q.finish("a", 2, Outcome.Posted(1)).second)
        q = q.retry("a").claim(1).first
        // The second attempt ends late, after the third began.
        assertFalse(q.finish("a", 2, Outcome.Posted(1)).second)
        assertTrue(q.statusOf("a") is UploadStatus.Sending)
        q = q.finish("a", 3, Outcome.Posted(2)).first
        assertEquals(UploadStatus.Done(2), q.statusOf("a"))
        // Nothing to retry once it's done, nor a draft never sent.
        assertEquals(q, q.retry("a"))
        val b = q.adding(file("b"))
        assertEquals(b, b.retry("b"))
        assertEquals(b, b.cancel("b"))
    }

    @Test
    fun whatIsOnItsWayStaysOnTheList() {
        var q = UploadState().adding(file("a"), file("b"), file("c")).started()
        q = q.claim(1).first
        assertNull(q.remove("a").second)
        val (removed, gone) = q.remove("b")
        assertEquals("b", gone?.id)
        val (cleared, out) = removed.clear()
        assertEquals(listOf("a"), cleared.items.map { it.id })
        assertEquals(listOf("c"), out.map { it.id })
        // Taken off once cancelled, its late end no longer counts.
        val stopped = cleared.cancel("a")
        val (empty, _) = stopped.remove("a")
        assertFalse(empty.finish("a", 1, Outcome.Posted(1)).second)
    }

    @Test
    fun aNewBatchClearsWhatTheLastOneLeft() {
        var q = UploadState().adding(file("a"), file("b"), file("c")).started()
        q = q.claim(1).first
        q = q.finish("a", 1, Outcome.Posted(1)).first
        q = q.claim(1).first
        assertTrue(q.startsBatch)
        val (next, gone) = q.add(listOf(file("d")))
        // The finished file goes; those still to send stay.
        assertEquals(listOf("b", "c", "d"), next.items.map { it.id })
        assertEquals(listOf("a"), gone.map { it.id })
        // Files still being picked belong to the batch being made.
        assertFalse(next.startsBatch)
        assertEquals(4, next.adding(file("e")).items.size)
    }

    @Test
    fun clearFinishedKeepsFailuresAndWhatWaits() {
        var q = UploadState().adding(file("a"), file("b"), file("c")).started()
        q = q.claim(2).first
        q = q.finish("a", 1, Outcome.Posted(1)).first
        q = q.finish("b", 1, Outcome.Failed("x")).first
        val (kept, gone) = q.clearFinished()
        assertEquals(listOf("b", "c"), kept.items.map { it.id })
        assertEquals(listOf("a"), gone.map { it.id })
    }

    @Test
    fun badTagsFailTheirFileAndItsOwnRatingWins() {
        var q = UploadState().adding(file("a", tags = "-bad"), file("b", tags = "fine"))
        val bad = q.problem()
        assertEquals("-bad", bad?.word)
        assertEquals(TagProblem.BAD_PREFIX, bad?.problem)
        q = q.setRating("b", Rating.EXPLICIT).setTags("b", "fine better")
        q = q.started()
        assertEquals(UploadStatus.Failed("-bad"), q.statusOf("a"))
        val plan = q.get("b")?.plan
        assertEquals(Rating.EXPLICIT, plan?.rating)
        assertEquals(listOf("fine", "better"), plan?.tags)
        // Sent, its tags and rating no longer change.
        assertEquals(q, q.setTags("b", "other").setRating("b", null))
    }

    @Test
    fun sharedTagsAreCheckedWithEveryDraft() {
        val q = UploadState(batch = Batch(tags = "~sky")).adding(file("a"))
        assertEquals("~sky", q.problem()?.word)
        assertNull(UploadState(batch = Batch(tags = "sky")).adding(file("a")).problem())
        // Nothing waits for Upload: nothing is wrong.
        assertNull(UploadState(batch = Batch(tags = "~sky")).problem())
    }

    @Test
    fun aLinkIsItsOwnSource() {
        var q = UploadState().adding(link("u", "https://cdn.example/a.png", tags = "own")).started("shared")
        val plan = q.get("u")?.plan
        assertEquals(listOf("shared", "own"), plan?.tags)
        assertEquals(Rating.SENSITIVE, plan?.rating)
        assertNull(plan?.source)
        q = q.claim(AT_ONCE).first
        assertEquals(UploadStatus.Sending(total = 0), q.statusOf("u"))
    }

    @Test
    fun anUnreachableServerSendsItAgainInALaterRun() {
        var q = UploadState().adding(file("a"), file("b")).started()
        q = q.claim(1).first
        q = q.finish("a", 1, Outcome.Again).first
        assertEquals(UploadStatus.Waiting, q.statusOf("a"))
        assertEquals(1, q.get("a")?.tries)
        // Not again in this run.
        val (_, next) = q.claim(AT_ONCE, except = setOf("a"))
        assertEquals(listOf("b"), next.map { it.id })
        // Retry starts the count over.
        q = q.claim(1).first.finish("a", 2, Outcome.Failed("x")).first.retry("a")
        assertEquals(0, q.get("a")?.tries)
    }

    @Test
    fun progressCountsOnlyTheAttemptOnItsWay() {
        var q = UploadState().adding(file("a", size = 100)).started()
        q = q.claim(1).first
        q = q.sent("a", 1, 40, 100, 10)
        q = q.sent("a", 7, 90, 100, 10)
        assertEquals(UploadStatus.Sending(40, 100, 10), q.statusOf("a"))
        // On disk it's kept without the bytes so far.
        assertEquals(UploadStatus.Sending(total = 100), q.forDisk().statusOf("a"))
    }

    @Test
    fun theWholeBatchAddsUp() {
        var q =
            UploadState()
                .adding(file("a", 100), file("b", 300), file("c", 600), file("d", 50))
        assertNull(q.overall())
        q = q.started()
        q = q.claim(2).first
        q = q.finish("a", 1, Outcome.Posted(1)).first
        q = q.sent("b", 1, 150, 300, 40)
        q = q.cancel("d")
        var all = q.overall()
        assertEquals(1, all?.finished)
        // The cancelled file isn't part of it any more.
        assertEquals(3, all?.files)
        assertEquals(250L, all?.sent)
        assertEquals(1000L, all?.total)
        assertEquals(40L, all?.speed)
        assertEquals(25, all?.percent)
        // A failure counts as finished, and its bytes no longer do.
        q = q.finish("b", 1, Outcome.Failed("x")).first
        all = q.overall()
        assertEquals(2, all?.finished)
        assertEquals(700L, all?.total)
        q = q.claim(2).first
        q = q.finish("c", 1, Outcome.Posted(3)).first
        assertNull(q.overall())
    }

    @Test
    fun stopAndRequeueEndOrResetWhatWasSent() {
        var q = UploadState().adding(file("a"), file("b"), file("c")).started()
        q = q.claim(1).first
        val requeued = q.requeue()
        assertEquals(listOf(UploadStatus.Waiting, UploadStatus.Waiting, UploadStatus.Waiting), requeued.items.map { it.status })
        val stopped = q.stopPending()
        assertTrue(stopped.items.all { it.status == UploadStatus.Stopped })
        assertTrue(stopped.items.all { it.retryable })
    }

    @Test
    fun aRunIsTallied() {
        val t =
            Tally()
                .plus(Outcome.Posted(1))
                .plus(Outcome.Posted(2))
                .plus(Outcome.Duplicate(3))
                .plus(Outcome.Failed("x"))
                .plus(Outcome.Again)
        assertEquals(Tally(done = 2, duplicate = 1, failed = 1), t)
        assertTrue(Tally().empty)
    }
}
