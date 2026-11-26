package eu.filipnone.starbooru.ui.signin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.ServerInfo
import eu.filipnone.starbooru.core.normalizeServer
import eu.filipnone.starbooru.data.Account
import eu.filipnone.starbooru.data.ApiFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the sign-in screen knows of the server typed in. */
sealed interface ServerCheck {
    data object Empty : ServerCheck

    data object Checking : ServerCheck

    data class Known(val server: String, val info: ServerInfo) : ServerCheck

    data class Failed(val server: String, val error: Throwable) : ServerCheck
}

data class SignInState(
    val server: String = "",
    val username: String = "",
    val password: String = "",
    /** Making an account rather than signing in. */
    val registering: Boolean = false,
    val check: ServerCheck = ServerCheck.Empty,
    val busy: Boolean = false,
    /** Waiting for the browser tab of a provider sign-in to come back. */
    val viaProvider: Boolean = false,
    val error: Throwable? = null,
) {
    val known: ServerCheck.Known? get() = check as? ServerCheck.Known

    /** The first account on a server becomes its administrator. */
    val firstAccount: Boolean get() = known?.info?.hasUsers == false

    val canRegister: Boolean get() = known?.info?.let { it.registrationOpen || !it.hasUsers } == true

    val makingAccount: Boolean get() = firstAccount || (registering && canRegister)

    val canSubmit: Boolean get() = !busy && known != null && username.isNotBlank() && password.isNotEmpty()
}

class SignInViewModel(private val account: Account, private val apis: ApiFactory, private val saved: SavedStateHandle) : ViewModel() {
    private val _state =
        MutableStateFlow(
            SignInState(
                server = saved[SERVER] ?: "",
                username = saved[USERNAME] ?: "",
                registering = saved[REGISTERING] ?: false,
            ),
        )
    val state: StateFlow<SignInState> = _state.asStateFlow()

    /** A page to open in a browser tab, for a provider sign-in. */
    private val _openUrl = MutableStateFlow<String?>(null)
    val openUrl: StateFlow<String?> = _openUrl.asStateFlow()

    private var checking: Job? = null
    private var provider: Job? = null

    init {
        if (_state.value.server.isNotBlank()) check(immediately = true)
    }

    /**
     * The screen opens: after a sign-out, or with the server the last
     * session was on. [expired] when the server ended the session.
     */
    fun arrive(
        lastServer: String,
        expired: Boolean,
    ) {
        _state.update {
            it.copy(
                busy = false,
                viaProvider = false,
                password = "",
                error = if (expired) CoreException.Unauthorized("expired") else it.error,
            )
        }
        if (_state.value.server.isBlank() && lastServer.isNotBlank()) {
            saved[SERVER] = lastServer
            _state.update { it.copy(server = lastServer) }
            check(immediately = true)
        }
    }

    fun setServer(value: String) {
        saved[SERVER] = value
        val typed = normalizeServer(value)
        _state.update {
            // What was said of another address no longer holds: nothing
            // signs in there while this one is being checked.
            val stale =
                when (val c = it.check) {
                    is ServerCheck.Known -> c.server != typed
                    is ServerCheck.Failed -> c.server != typed
                    else -> false
                }
            it.copy(server = value, error = null, check = if (stale) ServerCheck.Empty else it.check)
        }
        check(immediately = false)
    }

    fun setUsername(value: String) {
        saved[USERNAME] = value
        _state.update { it.copy(username = value, error = null) }
    }

    fun setPassword(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    fun setRegistering(value: Boolean) {
        saved[REGISTERING] = value
        _state.update { it.copy(registering = value, error = null) }
    }

    /** Asks the typed server what it is, a moment after the typing stops. */
    private fun check(immediately: Boolean) {
        checking?.cancel()
        val server = normalizeServer(_state.value.server)
        if (server.isEmpty()) {
            _state.update { it.copy(check = ServerCheck.Empty) }
            return
        }
        checking =
            viewModelScope.launch {
                if (!immediately) delay(CHECK_AFTER_MS)
                _state.update { it.copy(check = ServerCheck.Checking) }
                val result =
                    try {
                        ServerCheck.Known(server, apis.create(server, null).info())
                    } catch (e: CoreException) {
                        ServerCheck.Failed(server, e)
                    }
                _state.update { it.copy(check = result) }
            }
    }

    fun retryCheck() = check(immediately = true)

    fun submit() {
        val s = _state.value
        val known = s.known ?: return
        if (!s.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val api = apis.create(known.server, null)
                val name = s.username.trim()
                val signedIn = if (s.makingAccount) api.register(name, s.password) else api.signIn(name, s.password)
                account.signedIn(known.server, signedIn)
            } catch (e: CoreException) {
                _state.update { it.copy(busy = false, error = e) }
            }
        }
    }

    fun signInWithProvider() {
        val known = _state.value.known ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, viaProvider = true, error = null) }
        provider =
            viewModelScope.launch {
                try {
                    val start = apis.create(known.server, null).beginProviderSignIn()
                    _openUrl.value = start.url()
                    // Cancelling this coroutine drops the core's listener.
                    val signedIn = start.finish()
                    account.signedIn(known.server, signedIn)
                } catch (e: CoreException) {
                    _state.update { it.copy(busy = false, viaProvider = false, error = e) }
                }
            }
    }

    fun urlOpened() {
        _openUrl.value = null
    }

    /** Back from a provider sign-in without finishing it. */
    fun cancelProvider() {
        provider?.cancel()
        provider = null
        _openUrl.value = null
        _state.update { it.copy(busy = false, viaProvider = false) }
    }

    fun browseAsGuest() {
        val known = _state.value.known ?: return
        viewModelScope.launch { account.browseAsGuest(known.server) }
    }

    private companion object {
        const val SERVER = "server"
        const val USERNAME = "username"
        const val REGISTERING = "registering"
        const val CHECK_AFTER_MS = 600L
    }
}
