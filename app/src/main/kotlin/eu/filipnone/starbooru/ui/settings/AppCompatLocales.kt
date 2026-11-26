package eu.filipnone.starbooru.ui.settings

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The app's own language, kept by Android from version 13: the same choice
 * as in Android's settings for the app, which restarts the screen in it.
 * Older versions follow the system's language.
 */
object AppCompatLocales {
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private var manager: LocaleManager? = null

    /** The language picked for the app, `""` for the system's. */
    @Composable
    fun current(): String {
        if (!supported) return ""
        val context = LocalContext.current
        val m = context.getSystemService(LocaleManager::class.java) ?: return ""
        manager = m
        return m.applicationLocales.get(0)?.language.orEmpty()
    }

    fun set(tag: String) {
        if (!supported) return
        val m = manager ?: return
        m.applicationLocales = if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }
}
