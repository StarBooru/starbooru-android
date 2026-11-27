package eu.filipnone.starbooru.ui.common

import eu.filipnone.starbooru.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** The web's post menu: "Liked", "Gave 5 stars" or "Like removed" once the vote is in. */
class LikedNoticeTest {
    @Test
    fun aLikeSaysLiked() {
        assertEquals(R.string.press_liked, likedNotice(stars = false, given = 1))
    }

    @Test
    fun fiveStarsSayStarred() {
        assertEquals(R.string.press_starred, likedNotice(stars = true, given = 5))
    }

    @Test
    fun aLikeTakenBackSaysRemoved() {
        assertEquals(R.string.press_unliked, likedNotice(stars = false, given = 0))
        assertEquals(R.string.press_unliked, likedNotice(stars = true, given = 0))
    }
}
