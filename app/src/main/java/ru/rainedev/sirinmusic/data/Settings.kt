package ru.rainedev.sirinmusic.data

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Базовый URL и API-токен сервера. Токен — секрет, поэтому лежит в
 * EncryptedSharedPreferences (ключ в Keystore), а не в обычных префах.
 */
class Settings(context: Context) : ConnectionSettings {

    private val prefs: SharedPreferences = run {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "sirin_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override var baseUrl: String
        get() = prefs.getString(KEY_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_URL, value.trim().trimEnd('/')).apply()

    override var token: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).putBoolean(KEY_AUTH_DISABLED, false).apply()

    private val appearancePrefs = context.getSharedPreferences("sirin_appearance", Context.MODE_PRIVATE)
    private val _appearance = MutableStateFlow(Appearance(
        theme = runCatching { ThemeMode.valueOf(appearancePrefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = appearancePrefs.getBoolean("dynamic_color", true),
        customColor = appearancePrefs.getInt("custom_color", 0xFF6750A4.toInt()),
        palette = runCatching { Palette.valueOf(appearancePrefs.getString("palette", "SIRIN")!!) }.getOrDefault(Palette.SIRIN),
    ))
    val appearance = _appearance.asStateFlow()

    fun setAppearance(value: Appearance) {
        appearancePrefs.edit().putString("theme", value.theme.name)
            .putInt("custom_color", value.customColor).putBoolean("dynamic_color", value.dynamicColor).putString("palette", value.palette.name).apply()
        _appearance.value = value
    }

    fun setConnection(url: String, apiToken: String, authDisabled: Boolean = false) {
        prefs.edit().putString(KEY_URL, url.trim().trimEnd('/')).putString(KEY_TOKEN, apiToken.trim()).putBoolean(KEY_AUTH_DISABLED, authDisabled).apply()
    }

    /** Один и тот же id во всех событиях — по нему сервер отличает устройства. */
    override val clientId: String
        get() = prefs.getString(KEY_CLIENT, null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_CLIENT, it).apply()
        }

    val isConfigured: Boolean get() = baseUrl.isNotEmpty() && (token.isNotEmpty() || prefs.getBoolean(KEY_AUTH_DISABLED, false))

    fun clear() = prefs.edit().remove(KEY_URL).remove(KEY_TOKEN).remove(KEY_AUTH_DISABLED).apply()

    private companion object {
        const val KEY_URL = "base_url"
        const val KEY_TOKEN = "api_token"
        const val KEY_AUTH_DISABLED = "auth_disabled"
        const val KEY_CLIENT = "client_id"
    }
}


enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class Palette(private val labelRes: Int, val seed: Int) {
    SIRIN(R.string.settings_1, 0xFF9B4220.toInt()), FOREST(R.string.settings_2, 0xFF386A20.toInt()),
    OCEAN(R.string.settings_3, 0xFF00658E.toInt()), SUNSET(R.string.settings_4, 0xFF904B40.toInt()),
    LAVENDER(R.string.settings_5, 0xFF6750A4.toInt()), ROSE(R.string.settings_6, 0xFFAD1457.toInt()),
    TEAL(R.string.settings_7, 0xFF006A60.toInt()), AMBER(R.string.settings_8, 0xFF8A5800.toInt()),
    INDIGO(R.string.settings_9, 0xFF3949AB.toInt()), GRAPHITE(R.string.settings_10, 0xFF52616B.toInt()),
    CUSTOM(R.string.settings_11, 0xFF6750A4.toInt());
    val label: String get() = strings(labelRes)
}
data class Appearance(val theme: ThemeMode = ThemeMode.SYSTEM, val dynamicColor: Boolean = true,
    val palette: Palette = Palette.SIRIN, val customColor: Int = 0xFF6750A4.toInt())
