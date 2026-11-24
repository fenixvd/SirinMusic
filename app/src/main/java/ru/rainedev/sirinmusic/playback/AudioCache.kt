package ru.rainedev.sirinmusic.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Read-through кеш очереди: проигранные и prefetch-треки оседают в cacheDir/audio_cache.
 * Пользовательский лимит применяется вытеснением по давлению (старое удаляется первым),
 * жёсткий потолок HARD_CAP_BYTES страхует на случай отключённого трима.
 * Очистка не трогает обложки, настройки и историю.
 */
@OptIn(UnstableApi::class)
class AudioCache(context: Context) {
    private val mutex = Mutex()

    /** SimpleCache обязан существовать в одном экземпляре на каталог — живёт в SirinApp. */
    val cache: Cache = SimpleCache(
        File(context.cacheDir, "audio_cache"),
        LeastRecentlyUsedCacheEvictor(HARD_CAP_BYTES),
        StandaloneDatabaseProvider(context),
    )

    suspend fun bytes(): Long = withContext(Dispatchers.IO) { cache.cacheSpace }

    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) {
            // Играющий сейчас трек держит спан — SimpleCache его не отдаст, остальное удалится.
            cache.keys.forEach { cache.removeResource(it) }
        }
    }

    /** Вытесняет самые старые ресурсы, пока кеш не впишется в лимит. Занятые ресурсы SimpleCache пропускает сам. */
    suspend fun trimTo(limit: Long) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (limit <= 0) return@withContext
            if (cache.cacheSpace <= limit) return@withContext
            val oldestFirst = cache.keys
                .map { key -> Triple(key, cache.getCachedSpans(key).sumOf { it.length }, cache.getCachedSpans(key).minOf { it.lastTouchTimestamp }) }
                .sortedBy { (_, _, touch) -> touch }
            for ((key, _, _) in oldestFirst) {
                if (cache.cacheSpace <= limit) break
                cache.removeResource(key)
            }
        }
    }

    companion object {
        /** Страховочный потолок поверх пользовательского лимита. */
        const val HARD_CAP_BYTES: Long = 2L * 1024 * 1024 * 1024
        const val DEFAULT_LIMIT_BYTES: Long = 300L * 1024 * 1024
    }
}
