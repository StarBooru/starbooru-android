package eu.filipnone.starbooru

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import eu.filipnone.starbooru.core.searchKey
import eu.filipnone.starbooru.data.Account
import eu.filipnone.starbooru.data.AccountState
import eu.filipnone.starbooru.data.CoreApis
import eu.filipnone.starbooru.data.History
import eu.filipnone.starbooru.data.KeystoreSecretStore
import eu.filipnone.starbooru.data.MediaCache
import eu.filipnone.starbooru.data.SavedSearches
import eu.filipnone.starbooru.data.SettingsStore
import eu.filipnone.starbooru.data.Spots
import eu.filipnone.starbooru.data.upload.UploadQueue
import eu.filipnone.starbooru.ui.nav.Route
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsData: DataStore<Preferences> by preferencesDataStore("settings")
private val Context.accountData: DataStore<Preferences> by preferencesDataStore("account")
private val Context.historyData: DataStore<Preferences> by preferencesDataStore("history")
private val Context.spotsData: DataStore<Preferences> by preferencesDataStore("spots")

/** What lives as long as the app: the stores and the session. */
class AppGraph(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Where the app was asked to go from outside (a share, a notification); the frame takes it once. */
    val arriving = MutableStateFlow<Route?>(null)
    val settings = SettingsStore(context.settingsData)
    val history = History(context.historyData)
    val spots = Spots(context.spotsData, ::searchKey)
    val apis = CoreApis
    val account =
        Account(
            store = context.accountData,
            secrets = KeystoreSecretStore(File(context.noBackupFilesDir, "session.bin")),
            apis = apis,
        )
    val savedSearches = SavedSearches(account, scope)

    /** Whose history and places these are: one list per account on each server. */
    fun owner(state: AccountState): String =
        when (state) {
            is AccountState.SignedIn -> "${state.server}#${state.user.id}"
            is AccountState.Guest -> "${state.server}#guest"
            else -> ""
        }

    /** Signs out, and forgets what the account looked at, as the privacy policy says. */
    suspend fun signOut() {
        val who = owner(account.state.value)
        UploadQueue.get(context).signedOut()
        account.signOut()
        if (who.isNotEmpty()) {
            history.clear(who)
            spots.clear(who)
        }
    }

    init {
        scope.launch { account.load() }
        // A new cache limit takes a new image loader, once it's saved.
        scope.launch {
            settings.settings
                .map { it.cacheMb }
                .distinctUntilChanged()
                .drop(1)
                .collect { MediaCache.limitChanged() }
        }
        // What the server is (private or public, likes or stars, guests or
        // not) can change under the app, so it asks again every two minutes
        // while the app is in front, as the web app does.
        scope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                account.api.collectLatest { api ->
                    while (api != null) {
                        account.refreshInfo()
                        delay(INFO_EVERY_MS)
                    }
                }
            }
        }
    }

    private companion object {
        const val INFO_EVERY_MS = 2 * 60 * 1000L
    }
}
