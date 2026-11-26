package eu.filipnone.starbooru.data.upload

import eu.filipnone.starbooru.core.CoreException
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferTest {
    /** As the web writes sizes, for the labels below. */
    private fun size(bytes: Long): String =
        when {
            bytes >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }

    @Test
    fun saysHowFarAndHowFast() {
        assertEquals("0%", transferLabel(0, 1000, 0, ::size))
        assertEquals("42% · 1.2 MB/s", transferLabel(420, 1000, 1_288_490, ::size))
        assertEquals("100% · 512 B/s", transferLabel(1000, 1000, 512, ::size))
        assertEquals("3.4 MB", transferLabel(3_565_158, 0, 0, ::size))
    }

    @Test
    fun aPercentNeedsATotal() {
        assertNull(percentOf(5, 0))
        assertEquals(50, percentOf(5, 10))
        assertEquals(100, percentOf(50, 10))
        assertEquals(0, percentOf(-1, 10))
    }

    @Test
    fun theSpeedSettlesWithEachReadingCountingForAThird() {
        val m = Meter(0)
        // Too soon to tell.
        assertEquals(0L, m.seen(1000, 100))
        assertEquals(2000L, m.seen(1000, 500))
        // 4000 B/s now: (2 × 2000 + 4000) / 3.
        assertEquals(2666L, m.seen(3000, 1000))
        // Started over: the last speed stands until it's measured again.
        assertEquals(2666L, m.seen(0, 1100))
        assertEquals(2666L, m.seen(10, 1200))
    }

    @Test
    fun aRefusalIsToldByItsStatusAndCode() {
        val dup = refusal(CoreException.Api(409u, "duplicate", "this file is post #7, in the recycle bin: restore it from there"))
        assertEquals(7L, (dup as Refusal.Duplicate).of?.postId)
        assertEquals(true, dup.of?.inRecycleBin)
        assertEquals(Refusal.Duplicate(null), refusal(CoreException.Api(409u, "duplicate", "this file was already uploaded")))
        assertEquals(Refusal.TooBig, refusal(CoreException.Api(413u, "too_large", "too big")))
        assertEquals(Refusal.Unsupported, refusal(CoreException.Api(415u, "unsupported", "no")))
        assertEquals(Refusal.Other, refusal(CoreException.Api(409u, "upload_gone", "gone")))
        assertEquals(Refusal.Unreachable, refusal(CoreException.Unreachable("down")))
        assertEquals(Refusal.Unreachable, refusal(CoreException.RateLimited(600u)))
        assertEquals(Refusal.Other, refusal(IllegalStateException()))
    }
}
