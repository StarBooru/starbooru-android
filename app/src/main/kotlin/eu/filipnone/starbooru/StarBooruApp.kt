package eu.filipnone.starbooru

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import eu.filipnone.starbooru.data.MediaCache
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class StarBooruApp :
    Application(),
    SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }

    /**
     * Pictures load straight from the signed `/media` addresses. Requests
     * name their disk key after the file's SHA-256, so a picture stays
     * cached when its address is signed again every six hours.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // The limit set in Settings, read once per loader; a change resets
        // the loader (MediaCache.limitChanged).
        val limitMb = runBlocking { graph.settings.settings.first().cacheMb }
        return ImageLoader
            .Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }.memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache {
                if (limitMb <= 0) {
                    null
                } else {
                    DiskCache
                        .Builder()
                        .directory(MediaCache.folder(this))
                        .maxSizeBytes(limitMb * 1024L * 1024L)
                        .build()
                }
            }.crossfade(true)
            .build()
    }
}
