package ru.rainedev.sirinmusic

import okio.Path.Companion.toPath
import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import ru.rainedev.sirinmusic.data.MusikApi
import ru.rainedev.sirinmusic.data.Settings
import ru.rainedev.sirinmusic.playback.PlaybackController

class SirinApp : Application(), SingletonImageLoader.Factory {

    internal val updates by lazy { AppUpdates(this) }

    override fun onCreate() {
        super.onCreate()
        TextResources.install(this)
        updates.initialize()
    }

    val imageCache by lazy { ru.rainedev.sirinmusic.data.ImageCache(this) }

    val settings: Settings by lazy { Settings(this) }
    val api: MusikApi by lazy { MusikApi(settings) }
    val playback: PlaybackController by lazy { PlaybackController(this, api, settings) }

    /**
     * Coil сам заголовки не подставит, а /api/artwork закрыт авторизацией —
     * поэтому отдаём ему наш OkHttp с тем же интерсептором, иначе все обложки 401.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                coil3.memory.MemoryCache.Builder().maxSizeBytes(minOf(24L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 10)).build()
            }
            .diskCache {
                coil3.disk.DiskCache.Builder().directory(cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizeBytes(64L * 1024 * 1024).build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { api.client }))
            }
            .build()
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            SingletonImageLoader.get(this).memoryCache?.clear()
        }
    }

}
