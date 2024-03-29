package org.xtimms.tokusho.core.prefs

import android.os.Build
import androidx.annotation.DeprecatedSinceApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import com.google.android.material.color.DynamicColors
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.xtimms.tokusho.ui.theme.SEED
import org.xtimms.tokusho.R
import org.xtimms.tokusho.ui.monet.PaletteStyle
import org.xtimms.tokusho.utils.lang.processLifecycleScope
import org.xtimms.tokusho.utils.system.LocaleLanguageCodeMap
import java.util.Locale

private const val DYNAMIC_COLOR = "dynamic_color"
const val DARK_THEME_VALUE = "dark_theme_value"
private const val HIGH_CONTRAST = "high_contrast"
const val AUTO_UPDATE = "auto_update"
const val UPDATE_CHANNEL = "update_channel"
private const val THEME_COLOR = "theme_color"
const val PALETTE_STYLE = "palette_style"
const val LANGUAGE = "language"
const val READING_TIME = "reading_time"
const val GRID_COLUMNS = "grid_columns"

const val SYSTEM_DEFAULT = 0

const val STABLE = 0
const val PRE_RELEASE = 1

const val ACRA = "acra"
const val LOGGING = "logging"

const val SWIPE_TUTORIAL = "swipe_tutorial"
const val WSRV = "image_optimization"
const val SSL_BYPASS = "ssl_bypass"
const val NSFW = "nsfw"
const val TABS_MANGA_COUNT = "tabs_manga_count"
const val SUGGESTIONS = "suggestions"
const val TRACKER = "tracker"

val paletteStyles = listOf(
    PaletteStyle.TonalSpot,
    PaletteStyle.Spritz,
    PaletteStyle.FruitSalad,
    PaletteStyle.Vibrant,
    PaletteStyle.Monochrome
)

const val STYLE_TONAL_SPOT = 0
const val STYLE_SPRITZ = 1
const val STYLE_FRUIT_SALAD = 2
const val STYLE_VIBRANT = 3
const val STYLE_MONOCHROME = 4

private val kv: MMKV = MMKV.defaultMMKV()

private val StringPreferenceDefaults = mapOf(
    "test" to "default",
)

private val BooleanPreferenceDefaults = mapOf(
    "test" to false
)

private val IntPreferenceDefaults = mapOf(
    GRID_COLUMNS to 3,
    LANGUAGE to SYSTEM_DEFAULT,
    PALETTE_STYLE to 0,
    DARK_THEME_VALUE to DarkThemePreference.FOLLOW_SYSTEM,
    UPDATE_CHANNEL to STABLE,
)

object AppSettings {

    fun String.getInt(default: Int = IntPreferenceDefaults.getOrElse(this) { 0 }): Int =
        kv.decodeInt(this, default)

    fun String.getString(default: String = StringPreferenceDefaults.getOrElse(this) { "" }): String =
        kv.decodeString(this) ?: default

    fun String.getBoolean(default: Boolean = BooleanPreferenceDefaults.getOrElse(this) { false }): Boolean =
        kv.decodeBool(this, default)

    fun String.updateString(newString: String) = kv.encode(this, newString)

    fun String.updateInt(newInt: Int) = kv.encode(this, newInt)

    fun String.updateBoolean(newValue: Boolean) = kv.encode(this, newValue)
    fun updateValue(key: String, b: Boolean) = key.updateBoolean(b)
    fun encodeInt(key: String, int: Int) = key.updateInt(int)
    fun getValue(key: String): Boolean = key.getBoolean()
    fun encodeString(key: String, string: String) = key.updateString(string)
    fun containsKey(key: String) = kv.containsKey(key)

    fun isAutoUpdateEnabled() = AUTO_UPDATE.getBoolean(false)

    fun isACRAEnabled() = ACRA.getBoolean(true)

    fun isLoggingEnabled() = LOGGING.getBoolean(false)

    fun isReadingTimeEstimationEnabled() = READING_TIME.getBoolean(true)

    fun isNSFWEnabled() = NSFW.getBoolean(false)

    fun isSSLBypassEnabled() = SSL_BYPASS.getBoolean(false)

    fun isMangaCountInTabsEnabled() = TABS_MANGA_COUNT.getBoolean(false)

    fun isSuggestionsEnabled() = SUGGESTIONS.getBoolean(true)

    fun isTrackerEnabled() = TRACKER.getBoolean(true)

    fun isSwipeTutorialEnabled() = SWIPE_TUTORIAL.getBoolean(true)

    fun isImagesProxyEnabled() = WSRV.getBoolean(false)

    fun getGridColumnsCount(columns: Int = GRID_COLUMNS.getInt()): Float {
        return when (columns) {
            1 -> 1f
            2 -> 2f
            3 -> 3f
            4 -> 4f
            5 -> 5f
            else -> 3f
        }
    }

    @DeprecatedSinceApi(api = 33)
    fun getLocaleFromPreference(): Locale? {
        val languageCode = LANGUAGE.getInt()
        return LocaleLanguageCodeMap.entries.find { it.value == languageCode }?.key
    }

    fun saveLocalePreference(locale: Locale?) {
        if (Build.VERSION.SDK_INT >= 33) {
            // No op
        } else {
            LANGUAGE.updateInt(LocaleLanguageCodeMap[locale] ?: SYSTEM_DEFAULT)
        }
    }

    data class Settings(
        val darkTheme: DarkThemePreference = DarkThemePreference(),
        val isDynamicColorEnabled: Boolean = false,
        val seedColor: Int = SEED,
        val paletteStyleIndex: Int = 0
    )

    private val mutableAppSettingsStateFlow = MutableStateFlow(
        Settings(
            DarkThemePreference(
                darkThemeValue = kv.decodeInt(DARK_THEME_VALUE, DarkThemePreference.FOLLOW_SYSTEM),
                isHighContrastModeEnabled = kv.decodeBool(HIGH_CONTRAST, false)
            ),
            isDynamicColorEnabled = kv.decodeBool(DYNAMIC_COLOR, DynamicColors.isDynamicColorAvailable()),
            seedColor = kv.decodeInt(THEME_COLOR, SEED),
            paletteStyleIndex = kv.decodeInt(PALETTE_STYLE, 0)
        )
    )
    val AppSettingsStateFlow = mutableAppSettingsStateFlow.asStateFlow()

    fun modifyDarkThemePreference(
        darkThemeValue: Int = AppSettingsStateFlow.value.darkTheme.darkThemeValue,
        isHighContrastModeEnabled: Boolean = AppSettingsStateFlow.value.darkTheme.isHighContrastModeEnabled
    ) {
        processLifecycleScope.launch(Dispatchers.IO) {
            mutableAppSettingsStateFlow.update {
                it.copy(
                    darkTheme = AppSettingsStateFlow.value.darkTheme.copy(
                        darkThemeValue = darkThemeValue,
                        isHighContrastModeEnabled = isHighContrastModeEnabled
                    )
                )
            }
            kv.encode(DARK_THEME_VALUE, darkThemeValue)
            kv.encode(HIGH_CONTRAST, isHighContrastModeEnabled)
        }
    }

    fun modifyThemeSeedColor(colorArgb: Int, paletteStyleIndex: Int) {
        processLifecycleScope.launch(Dispatchers.IO) {
            mutableAppSettingsStateFlow.update {
                it.copy(seedColor = colorArgb, paletteStyleIndex = paletteStyleIndex)
            }
            kv.encode(THEME_COLOR, colorArgb)
            kv.encode(PALETTE_STYLE, paletteStyleIndex)
        }
    }

    fun switchDynamicColor(enabled: Boolean = !mutableAppSettingsStateFlow.value.isDynamicColorEnabled) {
        processLifecycleScope.launch(Dispatchers.IO) {
            mutableAppSettingsStateFlow.update {
                it.copy(isDynamicColorEnabled = enabled)
            }
            kv.encode(DYNAMIC_COLOR, enabled)
        }
    }
}

data class DarkThemePreference(
    val darkThemeValue: Int = FOLLOW_SYSTEM, val isHighContrastModeEnabled: Boolean = false
) {
    companion object {
        const val FOLLOW_SYSTEM = 1
        const val ON = 2
        const val OFF = 3
    }

    @Composable
    fun isDarkTheme(): Boolean {
        return if (darkThemeValue == FOLLOW_SYSTEM) isSystemInDarkTheme()
        else darkThemeValue == ON
    }

    @Composable
    fun getDarkThemeDesc(): String {
        return when (darkThemeValue) {
            FOLLOW_SYSTEM -> stringResource(R.string.follow_system)
            ON -> stringResource(R.string.on)
            else -> stringResource(R.string.off)
        }
    }

}