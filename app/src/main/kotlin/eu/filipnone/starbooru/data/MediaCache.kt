package eu.filipnone.starbooru.data

import android.content.Context
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The pictures kept on the device: Coil's disk cache, keyed by each file's
 * SHA-256 (see `ui/common/Media`), so it outlives the six-hour signed
 * addresses and opens offline.
 */
object MediaCache {
    fun folder(context: Context): File = context.cacheDir.resolve("media")

    /** The bytes kept now. */
    suspend fun size(context: Context): Long =
        withContext(Dispatchers.IO) {
            folder(context).walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        }

    /** Forgets every picture kept, in memory and on disk. */
    @OptIn(ExperimentalCoilApi::class)
    suspend fun clear(context: Context) {
        val loader = SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        withContext(Dispatchers.IO) { loader.diskCache?.clear() }
    }

    /**
     * A new limit takes effect with a new image loader; the one in use keeps
     * its own until then. Called when Settings changes it.
     */
    fun limitChanged() {
        SingletonImageLoader.reset()
    }
}
