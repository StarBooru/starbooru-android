package eu.filipnone.starbooru

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.filipnone.starbooru.data.Settings
import eu.filipnone.starbooru.data.ThemeMode
import eu.filipnone.starbooru.data.upload.UploadNotifications
import eu.filipnone.starbooru.data.upload.UploadQueue
import eu.filipnone.starbooru.ui.MainScreen
import eu.filipnone.starbooru.ui.nav.UploadRoute
import eu.filipnone.starbooru.ui.theme.StarBooruTheme

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        arrive(intent)
    }

    /** Files and links shared to the app wait on Upload; the upload notification opens it too. */
    private fun arrive(intent: Intent?) {
        if (intent == null) return
        val graph = (application as StarBooruApp).graph
        val shared = UploadQueue.get(this).receive(intent)
        if (shared || UploadNotifications.opensUploads(intent)) graph.arriving.value = UploadRoute
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as StarBooruApp).graph
        if (savedInstanceState == null) arrive(intent)
        setContent {
            val settings by graph.settings.settings.collectAsStateWithLifecycle(Settings())
            // The theme follows the system as it changes, with no restart:
            // the manifest keeps uiMode changes in the activity.
            val dark =
                when (settings.theme) {
                    ThemeMode.System -> isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
            LaunchedEffect(dark) {
                val style =
                    if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            StarBooruTheme(
                dark = dark,
                oled = settings.oled,
                dynamic = settings.wallpaperColours,
                seed = settings.seed,
            ) {
                MainScreen(graph, settings)
            }
        }
    }
}
