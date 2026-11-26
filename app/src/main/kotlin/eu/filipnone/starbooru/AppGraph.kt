package eu.filipnone.starbooru

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import eu.filipnone.starbooru.data.Account
import eu.filipnone.starbooru.data.CoreApis
import eu.filipnone.starbooru.data.KeystoreSecretStore
import eu.filipnone.starbooru.data.SettingsStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val Context.settingsData: DataStore<Preferences> by preferencesDataStore("settings")
private val Context.accountData: DataStore<Preferences> by preferencesDataStore("account")

/** What lives as long as the app: the stores and the session. */
class AppGraph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsStore(context.settingsData)
    val apis = CoreApis
    val account =
        Account(
            store = context.accountData,
            secrets = KeystoreSecretStore(File(context.noBackupFilesDir, "session.bin")),
            apis = apis
        )

    init {
        scope.launch { account.load() }
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
