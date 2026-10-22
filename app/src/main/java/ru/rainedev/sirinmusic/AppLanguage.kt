package ru.rainedev.sirinmusic

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.activity.ComponentActivity
import java.util.Locale

/** Uses Android's app-language preference on 13+, with an equivalent fallback on older devices. */
internal object AppLanguage {
    private const val PREFS = "app_language"
    fun selection(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(LocaleManager::class.java).applicationLocales.let { if (it.isEmpty) "" else it[0].language }
    } else context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("language", "").orEmpty()

    fun set(activity: ComponentActivity, language: String) {
        require(language in setOf("", "ru", "en"))
        if (selection(activity) == language) return
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("language", language).apply()
            activity.recreate()
        }
    }

    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val selected = selection(context)
        if (selected.isEmpty()) return context
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(selected))
        return context.createConfigurationContext(config)
    }
}

/** Every app screen gets the same locale, including camera and separate settings activities. */
open class SirinActivity : ComponentActivity() {
    private var languageAtCreation = ""
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        languageAtCreation = AppLanguage.selection(this)
        super.onCreate(savedInstanceState)
    }
    override fun onResume() {
        super.onResume()
        // A settings activity may have changed the language while this screen was stopped.
        if (Build.VERSION.SDK_INT < 33 && languageAtCreation != AppLanguage.selection(this)) recreate()
    }
}
