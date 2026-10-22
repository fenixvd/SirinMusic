package ru.rainedev.sirinmusic

import android.content.Context
import androidx.annotation.StringRes

/** Application resources also localize API validation and background notifications. */
internal object TextResources {
    @Volatile internal var resolver: ((Int, Array<out Any?>) -> String)? = null
    @Volatile internal var quantityResolver: ((Int, Int, Array<out Any?>) -> String)? = null
    @Volatile internal var locale: () -> java.util.Locale = { java.util.Locale.getDefault() }
    fun install(context: Context) {
        val app = context.applicationContext
        resolver = { id, args -> AppLanguage.wrap(app).getString(id, *args) }
        quantityResolver = { id, count, args -> AppLanguage.wrap(app).resources.getQuantityString(id, count, *args) }
        locale = { AppLanguage.wrap(app).resources.configuration.locales[0] }
    }
}

internal fun strings(@StringRes id: Int, vararg args: Any?): String =
    checkNotNull(TextResources.resolver) { "Application text resources are not initialized" }(id, args)

internal fun trackCount(count: Int): String = quantities(R.plurals.track_count, count, count)
internal fun dayCount(count: Int): String = quantities(R.plurals.day_count, count, count)
internal fun quantities(@androidx.annotation.PluralsRes id: Int, count: Int, vararg args: Any?): String =
    checkNotNull(TextResources.quantityResolver) { "Application quantity resources are not initialized" }(id, count, args)
internal fun appLocale(): java.util.Locale = TextResources.locale()
