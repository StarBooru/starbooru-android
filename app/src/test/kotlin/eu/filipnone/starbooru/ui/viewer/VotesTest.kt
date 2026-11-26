package eu.filipnone.starbooru.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

/** The web's tests for `logic/votes.rs` and `logic/stars.rs`. */
class VotesTest {
    @Test
    fun aPressCastsOrTakesBackTheVote() {
        assertEquals(1, Votes.nextVote(0, 1))
        assertEquals(0, Votes.nextVote(1, 1))
        assertEquals(-1, Votes.nextVote(1, -1))
        assertEquals(0, Votes.nextVote(-1, -5))
    }

    @Test
    fun aPressGivesThatManyStarsOrTakesTheSameBack() {
        assertEquals(4, Votes.nextStars(0, 4))
        assertEquals(2, Votes.nextStars(4, 2))
        assertEquals(0, Votes.nextStars(4, 4))
        assertEquals(5, Votes.nextStars(0, 9))
        assertEquals(1, Votes.nextStars(0, 0))
        assertEquals(0, Votes.nextStars(1, 0))
    }

    @Test
    fun aBallotShowsAtOnceInTheTally() {
        val likes = Tally(score = 3, myVote = 0, stars = null, votes = 0, myStars = 0)
        assertEquals(4, likes.liked(1).score)
        assertEquals(1, likes.liked(1).myVote)
        assertEquals(likes, likes.liked(-1).liked(0))
        // 13 stars over 3 votes, 2 of them 4 or more.
        val stars = Tally(score = 2, myVote = 0, stars = 4.33, votes = 3, myStars = 0)
        val given = stars.starred(5)
        assertEquals(Tally(score = 3, myVote = 1, stars = 4.5, votes = 4, myStars = 5), given)
        val back = given.starred(0)
        assertEquals(Triple(2, 0, 3), Triple(back.score, back.myVote, back.votes))
        assertEquals(4.33, back.stars ?: 0.0, 0.0)
    }

    @Test
    fun theLastStarsGoneLeaveNoAverage() {
        assertEquals(null to 0, Votes.restarred(4.0, 1, 4, 0))
    }
}
