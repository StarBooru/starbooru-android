package eu.filipnone.starbooru.ui.common

import android.content.res.Resources
import androidx.compose.runtime.Composable
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
    signingIn: Boolean = false
): String {
    val shown = runCatching { shownServer(server) }.getOrDefault(server)
    return when (error) {
        is CoreException.InvalidAddress -> res.getString(R.string.error_bad_address)

        is CoreException.Unreachable -> res.getString(R.string.error_unreachable, shown)

        is CoreException.Unauthorized ->
            res.getString(if (signingIn) R.string.login_wrong else R.string.error_session_expired)

        is CoreException.RateLimited -> {
            val minutes = error.retryAfterSecs?.let { ((it.toLong() + 59) / 60).toInt().coerceAtLeast(1) }
            if (minutes == null) {
                res.getString(R.string.error_rate_limited)
            } else {
                res.getQuantityString(R.plurals.error_rate_limited_minutes, minutes, minutes)
            }
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

@Composable
fun describe(
    error: Throwable,
    server: String,
    signingIn: Boolean = false
): String = describe(error, LocalResources.current, server, signingIn)
