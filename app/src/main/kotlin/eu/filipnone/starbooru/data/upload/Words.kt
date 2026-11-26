package eu.filipnone.starbooru.data.upload

import android.content.res.Resources
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.BadTag
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.DuplicatePost
import eu.filipnone.starbooru.core.SizeUnit
import eu.filipnone.starbooru.core.TagProblem
import eu.filipnone.starbooru.core.duplicateOf
import eu.filipnone.starbooru.core.fileSizeParts
import eu.filipnone.starbooru.core.maxTagChars
import eu.filipnone.starbooru.core.maxTagsPerPost
import eu.filipnone.starbooru.ui.common.describe
import java.text.DecimalFormatSymbols

/** Why the server turned an item away, as far as the app tells them apart. */
sealed interface Refusal {
    /** The same file is already a post: [of], when the server said which. */
    data class Duplicate(val of: DuplicatePost?) : Refusal

    /** Over the server's upload limit. */
    data object TooBig : Refusal

    /** Not a picture or a video the server keeps. */
    data object Unsupported : Refusal

    /** The server couldn't be reached, or asked to wait: worth sending again later. */
    data object Unreachable : Refusal

    /** Anything else: the error's own words say what. */
    data object Other : Refusal
}

/** What a failed upload or import means. */
fun refusal(error: Throwable): Refusal =
    when {
        error is CoreException.Api && error.code == "duplicate" -> Refusal.Duplicate(duplicateOf(error.reason))
        error is CoreException.Api && error.status.toInt() == 413 -> Refusal.TooBig
        error is CoreException.Api && error.status.toInt() == 415 -> Refusal.Unsupported
        error is CoreException.Unreachable || error is CoreException.RateLimited -> Refusal.Unreachable
        else -> Refusal.Other
    }

/** The words for what the app shows of an upload outside Compose: the worker and the notification. */
object Words {
    /** A size in bytes as the web writes it, in 1024s: 1.5 KB. */
    fun size(
        res: Resources,
        bytes: Long,
    ): String {
        val locale = res.configuration.locales[0]
        val parts = fileSizeParts(bytes, DecimalFormatSymbols.getInstance(locale).decimalSeparator.toString())
        val unit =
            when (parts.unit) {
                SizeUnit.B -> R.string.size_b
                SizeUnit.KB -> R.string.size_kb
                SizeUnit.MB -> R.string.size_mb
                SizeUnit.GB -> R.string.size_gb
                SizeUnit.TB -> R.string.size_tb
            }
        return res.getString(unit, parts.number)
    }

    fun tooBig(
        res: Resources,
        limit: Long,
    ): String = res.getString(R.string.upload_too_big, size(res, limit))

    /** Why a word can't be a tag. */
    fun badTag(
        res: Resources,
        bad: BadTag,
    ): String =
        when (bad.problem) {
            TagProblem.BAD_PREFIX -> res.getString(R.string.tag_error_prefix, bad.word)
            TagProblem.TOO_LONG -> res.getString(R.string.tag_error_long, bad.word, maxTagChars().toString())
            TagProblem.TOO_MANY -> res.getString(R.string.tag_error_many, maxTagsPerPost().toString())
            TagProblem.OTHER -> res.getString(R.string.tag_error_other, bad.word)
        }

    /** What the server's refusal of an item means for it; [limit] is its upload limit, 0 when unknown. */
    fun outcome(
        res: Resources,
        error: Throwable,
        server: String,
        limit: Long,
    ): Outcome =
        when (val r = refusal(error)) {
            is Refusal.Duplicate -> Outcome.Duplicate(r.of?.postId, r.of?.inRecycleBin == true)
            Refusal.TooBig -> Outcome.Failed(if (limit > 0) tooBig(res, limit) else describe(error, res, server))
            Refusal.Unsupported -> Outcome.Failed(res.getString(R.string.upload_unsupported))
            Refusal.Unreachable, Refusal.Other -> Outcome.Failed(describe(error, res, server))
        }

    /** What a finished run says: `3 files uploaded · 1 already here`. */
    fun summary(
        res: Resources,
        tally: Tally,
    ): String {
        val parts = ArrayList<String>()
        if (tally.done > 0 || tally.duplicate + tally.failed == 0) {
            parts += res.getQuantityString(R.plurals.activity_uploaded, tally.done, tally.done)
        }
        if (tally.duplicate > 0) parts += res.getQuantityString(R.plurals.upload_summary_known, tally.duplicate, tally.duplicate)
        if (tally.failed > 0) parts += res.getQuantityString(R.plurals.upload_summary_failed, tally.failed, tally.failed)
        return parts.joinToString(" · ")
    }

    /** The notice for files left out, or `null` when none were. */
    fun skipped(
        res: Resources,
        names: List<String>,
    ): String? {
        if (names.isEmpty()) return null
        val (shown, more) = Accept.skippedNames(names)
        return if (more == 0) {
            res.getString(R.string.upload_skipped_only, shown)
        } else {
            res.getQuantityString(R.plurals.upload_skipped, more, more, shown)
        }
    }
}
