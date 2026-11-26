package eu.filipnone.starbooru.ui.common

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalResources
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.shownServer

/**
 * What went wrong, in words the user can act on. [server] is the address
 * the request went to; [signingIn] reads a 401 as a wrong password rather
 * than an ended session.
 */
fun describe(
    error: Throwable,
    res: Resources,
    server: String,
    signingIn: Boolean = false,
    guest: Boolean = false,
): String {
    val shown = runCatching { shownServer(server) }.getOrDefault(server)
    return when (error) {
        is CoreException.InvalidAddress -> res.getString(R.string.error_bad_address)

        is CoreException.Unreachable -> res.getString(R.string.error_unreachable, shown)

        is CoreException.Unauthorized ->
            res.getString(if (signingIn) R.string.login_wrong else R.string.error_session_expired)

        is CoreException.RateLimited ->
            when (val wait = Wait.of(error.retryAfterSecs?.toLong(), guest)) {
                is Wait.Seconds -> res.getQuantityString(R.plurals.error_slow_down_seconds, wait.n, wait.n)

                is Wait.Minutes ->
                    if (guest) {
                        res.getQuantityString(R.plurals.error_slow_down_minutes, wait.n, wait.n)
                    } else {
                        res.getQuantityString(R.plurals.error_rate_limited_minutes, wait.n, wait.n)
                    }

                Wait.Unknown -> res.getString(if (guest) R.string.error_slow_down else R.string.error_rate_limited)
            }

        is CoreException.Api ->
            when {
                error.code == "not_starbooru" -> res.getString(R.string.error_not_a_server, shown)
                error.code == "weak_password" -> res.getString(R.string.login_weak_password)
                error.status.toInt() == 507 -> res.getString(R.string.error_storage_full, shown)
                error.status.toInt() >= 500 -> res.getString(R.string.error_server_fault, shown)
                error.reason.isNotBlank() -> error.reason
                else -> res.getString(R.string.error_unexpected_answer, shown, error.status.toString())
            }

        is CoreException.TooLarge -> res.getString(R.string.error_proxy_too_large, shown)

        is CoreException.Io -> error.detail

        is CoreException.SignInCancelled ->
            when (error.reason) {
                "denied" -> res.getString(R.string.oidc_denied)
                "timeout" -> res.getString(R.string.oidc_expired)
                else -> res.getString(R.string.oidc_failed)
            }

        else -> res.getString(R.string.error_unexpected_answer, shown, error.javaClass.simpleName)
    }
}

/** Whether the app is browsing as a guest, for the words of what went wrong. */
val LocalGuest = staticCompositionLocalOf { false }

@Composable
fun describe(
    error: Throwable,
    server: String,
    signingIn: Boolean = false,
): String = describe(error, LocalResources.current, server, signingIn, LocalGuest.current)

/**
 * How long to wait after a rate limit the client couldn't wait out, as the
 * web says it: a guest hears seconds under a minute (and that an account
 * goes on at once); an account, minutes.
 */
sealed interface Wait {
    data class Seconds(val n: Int) : Wait

    data class Minutes(val n: Int) : Wait

    data object Unknown : Wait

    companion object {
        fun of(
            retryAfterSecs: Long?,
            guest: Boolean,
        ): Wait =
            when {
                retryAfterSecs == null -> Unknown
                guest && retryAfterSecs < 60 -> Seconds(retryAfterSecs.coerceAtLeast(1).toInt())
                else -> Minutes(((retryAfterSecs + 59) / 60).coerceIn(1, Int.MAX_VALUE.toLong()).toInt())
            }
    }
}
