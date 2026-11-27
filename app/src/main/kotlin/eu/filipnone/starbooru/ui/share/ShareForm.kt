package eu.filipnone.starbooru.ui.share

import eu.filipnone.starbooru.core.maxShareDays
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How long a share link works. */
sealed interface Lifetime {
    data object UntilRemoved : Lifetime

    data class Days(val days: Int) : Lifetime

    /** Until the end of [last], a day the user picked. */
    data class UntilDay(val last: LocalDate?) : Lifetime
}

/** What's wrong with a share link's form, as the web says it (`share-error-*`, `share-day-*`). */
sealed interface ShareProblem {
    data object NoPosts : ShareProblem

    data class TooMany(val max: Int) : ShareProblem

    data class TitleLong(val max: Int) : ShareProblem

    data object TitleControl : ShareProblem

    data object DayMissing : ShareProblem

    data object DaySoon : ShareProblem

    data class DayLate(val last: LocalDate, val max: Int) : ShareProblem
}

/** A share link's form, checked before it's sent, after starbooru-core's `share.rs`. */
object ShareForm {
    const val MAX_POSTS = 500
    const val MAX_TITLE = 100

    /** The lifetimes offered as chips, in days, besides until removed and a picked day. */
    val QUICK_DAYS = listOf(1, 7, 30)

    /** The days to send for [lifetime], from [today]: null keeps the link until removed. */
    fun days(
        lifetime: Lifetime,
        today: LocalDate,
    ): Int? =
        when (lifetime) {
            Lifetime.UntilRemoved -> null
            is Lifetime.Days -> lifetime.days
            is Lifetime.UntilDay -> lifetime.last?.let { ChronoUnit.DAYS.between(today, it).toInt() }
        }

    /** The first thing that keeps the link from being made, or null. */
    fun problem(
        posts: Int,
        title: String,
        lifetime: Lifetime,
        today: LocalDate,
        maxDays: Int = maxShareDays().toInt(),
    ): ShareProblem? {
        if (posts <= 0) return ShareProblem.NoPosts
        if (posts > MAX_POSTS) return ShareProblem.TooMany(MAX_POSTS)
        val t = title.trim()
        if (t.codePointCount(0, t.length) > MAX_TITLE) return ShareProblem.TitleLong(MAX_TITLE)
        if (t.any { it.isISOControl() }) return ShareProblem.TitleControl
        if (lifetime is Lifetime.UntilDay) {
            val last = lifetime.last ?: return ShareProblem.DayMissing
            val days = ChronoUnit.DAYS.between(today, last)
            if (days < 1) return ShareProblem.DaySoon
            if (days > maxDays) return ShareProblem.DayLate(today.plusDays(maxDays.toLong()), maxDays)
        }
        return null
    }

    /** A post's own address on a public server, which needs no link. */
    fun postAddress(
        server: String,
        id: Long,
    ): String = "${server.trimEnd('/')}/post/$id"
}
