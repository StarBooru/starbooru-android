package eu.filipnone.starbooru

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
import eu.filipnone.starbooru.ui.MainScreen
import eu.filipnone.starbooru.ui.theme.StarBooruTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as StarBooruApp).graph
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
                seed = settings.seed
            ) {
                MainScreen(graph, settings)
            }
        }
    }
}
