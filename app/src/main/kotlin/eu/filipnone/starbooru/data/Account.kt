package eu.filipnone.starbooru.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.filipnone.starbooru.core.Api
import eu.filipnone.starbooru.core.ApiInterface
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.Role
import eu.filipnone.starbooru.core.ServerInfo
import eu.filipnone.starbooru.core.SignedIn
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Who is using the app, as the account the server knows. */
data class Identity(val id: Long, val username: String, val admin: Boolean)

sealed interface AccountState {
    /** Before the stored session has been read. */
    data object Starting : AccountState

    /** The sign-in screen. [expired] when the server ended the session. */
    data class SignedOut(val lastServer: String, val expired: Boolean = false) : AccountState

    /** Browsing a public server without an account. */
    data class Guest(val server: String) : AccountState

    data class SignedIn(val server: String, val user: Identity) : AccountState
}

val AccountState.server: String?
    get() =
        when (this) {
            is AccountState.Guest -> server
            is AccountState.SignedIn -> server
            else -> null
        }

/** Makes the core's [Api]; tests pass a fake. */
fun interface ApiFactory {
    fun create(
        server: String,
        token: String?,
    ): ApiInterface
}

val CoreApis = ApiFactory { server, token -> Api(server, token) }

/**
 * The session: kept across restarts (the token in [secrets], the rest in
 * [store]), the [ApiInterface] every screen calls through, and what the
 * server said of itself in `/info`.
 */
class Account(private val store: DataStore<Preferences>, private val secrets: SecretStore, private val apis: ApiFactory) {
    private val _state = MutableStateFlow<AccountState>(AccountState.Starting)
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private val _api = MutableStateFlow<ApiInterface?>(null)

    /** `null` while signed out. */
    val api: StateFlow<ApiInterface?> = _api.asStateFlow()

    private val _info = MutableStateFlow<ServerInfo?>(null)

    /** The last `/info` of the current server; `null` until it answers. */
    val info: StateFlow<ServerInfo?> = _info.asStateFlow()

    private val lock = Mutex()

    suspend fun load() =
        lock.withLock {
            val prefs = store.data.catch { emit(emptyPreferences()) }.first()
            val last = prefs[LAST_SERVER].orEmpty()
            val server = prefs[SESSION_SERVER]
            val token = secrets.read()
            val id = prefs[USER_ID]
            val name = prefs[USERNAME]
            val guest = prefs[GUEST_SERVER]
            // A token without its session (a write that failed on a full
            // disk) is never used again: forget it.
            if (token != null && server == null) secrets.write(null)
            val next =
                when {
                    server != null && token != null && id != null && name != null ->
                        AccountState.SignedIn(server, Identity(id, name, prefs[ROLE] == ADMIN))

                    guest != null -> AccountState.Guest(guest)

                    else -> AccountState.SignedOut(last)
                }
            enter(next, token)
        }

    suspend fun signedIn(
        server: String,
        signedIn: SignedIn,
    ) = lock.withLock {
        val user = signedIn.user
        secrets.write(signedIn.token)
        save {
            it[LAST_SERVER] = server
            it[SESSION_SERVER] = server
            it[USER_ID] = user.id
            it[USERNAME] = user.username
            it[ROLE] = if (user.role == Role.ADMIN) ADMIN else USER
            it.remove(GUEST_SERVER)
        }
        enter(AccountState.SignedIn(server, Identity(user.id, user.username, user.role == Role.ADMIN)), signedIn.token)
    }

    suspend fun browseAsGuest(server: String) =
        lock.withLock {
            save {
                it[LAST_SERVER] = server
                it[GUEST_SERVER] = server
            }
            enter(AccountState.Guest(server), null)
        }

    /** Back to the sign-in screen from browsing as a guest, keeping the server. */
    suspend fun toSignIn() =
        lock.withLock {
            val server = _state.value.server ?: return@withLock
            save { it.remove(GUEST_SERVER) }
            enter(AccountState.SignedOut(server), null)
        }

    suspend fun signOut() {
        val api = _api.value
        if (_state.value is AccountState.SignedIn && api != null) {
            // The server forgets the token; offline, it expires there by itself.
            runCatching { api.signOut() }
        }
        forget(expired = false)
    }

    /** The server no longer takes the token. */
    suspend fun expired() = forget(expired = true)

    private suspend fun forget(expired: Boolean) =
        lock.withLock {
            val server = _state.value.server ?: return@withLock
            secrets.write(null)
            save {
                it.remove(SESSION_SERVER)
                it.remove(USER_ID)
                it.remove(USERNAME)
                it.remove(ROLE)
                it.remove(GUEST_SERVER)
            }
            enter(AccountState.SignedOut(server, expired), null)
        }

    /** Asks the server what it is again. Failures keep what it said last. */
    suspend fun refreshInfo() {
        val api = _api.value ?: return
        try {
            _info.value = api.info()
        } catch (_: CoreException) {
        }
    }

    /**
     * Calls the server, and signs out when it no longer takes the session.
     * A guest's 401 means the server stopped taking guests.
     */
    suspend fun <T> call(block: suspend (ApiInterface) -> T): T {
        val api = _api.value ?: throw CoreException.Unauthorized("signed out")
        try {
            return block(api)
        } catch (e: CoreException.Unauthorized) {
            // A late answer to a session that has since been replaced says
            // nothing of the one in use.
            if (_api.value !== api) throw e
            when (_state.value) {
                is AccountState.SignedIn -> expired()
                is AccountState.Guest -> refreshInfo().also { if (_info.value?.guests != true) toSignIn() }
                else -> {}
            }
            throw e
        }
    }

    /**
     * Writes the stored session. A full or failing disk leaves the change
     * for this run only, rather than taking the app down.
     */
    private suspend fun save(change: (MutablePreferences) -> Unit) {
        try {
            store.edit(change)
        } catch (_: IOException) {
        }
    }

    private fun enter(
        next: AccountState,
        token: String?,
    ) {
        val server = next.server
        val api = server?.let { s -> runCatching { apis.create(s, token) }.getOrNull() }
        if (_state.value.server != server) _info.value = null
        _api.value = api
        _state.value = if (server != null && api == null) AccountState.SignedOut(server) else next
    }

    private companion object {
        val LAST_SERVER = stringPreferencesKey("last_server")
        val SESSION_SERVER = stringPreferencesKey("session_server")
        val USER_ID = longPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val ROLE = stringPreferencesKey("role")
        val GUEST_SERVER = stringPreferencesKey("guest_server")
        const val ADMIN = "admin"
        const val USER = "user"
    }
}
