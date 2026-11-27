package eu.filipnone.starbooru.ui.share

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareFormTest {
    private val today = LocalDate.of(2026, 11, 27)

    private fun problem(
        posts: Int = 1,
        title: String = "",
        lifetime: Lifetime = Lifetime.UntilRemoved,
    ) = ShareForm.problem(posts, title, lifetime, today, maxDays = 365)

    @Test
    fun aLinkNeedsPostsAndNotTooMany() {
        assertEquals(ShareProblem.NoPosts, problem(posts = 0))
        assertEquals(ShareProblem.TooMany(500), problem(posts = 501))
        assertNull(problem(posts = 500))
    }

    @Test
    fun theTitleIsShortAndOnOneLine() {
        assertNull(problem(title = "Holiday photos"))
        assertEquals(ShareProblem.TitleLong(100), problem(title = "x".repeat(101)))
        assertNull(problem(title = "  ${"x".repeat(100)}  "))
        assertEquals(ShareProblem.TitleControl, problem(title = "a\tb"))
    }

    @Test
    fun aPickedDayIsTomorrowAtTheEarliestAndAYearAtTheLatest() {
        assertEquals(ShareProblem.DayMissing, problem(lifetime = Lifetime.UntilDay(null)))
        assertEquals(ShareProblem.DaySoon, problem(lifetime = Lifetime.UntilDay(today)))
        assertNull(problem(lifetime = Lifetime.UntilDay(today.plusDays(1))))
        assertNull(problem(lifetime = Lifetime.UntilDay(today.plusDays(365))))
        assertEquals(ShareProblem.DayLate(today.plusDays(365), 365), problem(lifetime = Lifetime.UntilDay(today.plusDays(366))))
    }

    @Test
    fun daysAreCountedFromToday() {
        assertNull(ShareForm.days(Lifetime.UntilRemoved, today))
        assertEquals(7, ShareForm.days(Lifetime.Days(7), today))
        assertEquals(3, ShareForm.days(Lifetime.UntilDay(today.plusDays(3)), today))
    }

    @Test
    fun aPublicPostHasItsOwnAddress() {
        assertEquals("https://b.example.org/post/7", ShareForm.postAddress("https://b.example.org/", 7))
    }
}
