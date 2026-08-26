package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.AccentColor
import com.nanzhufeng.ai.domain.AppFontSize
import com.nanzhufeng.ai.domain.AppearanceMode
import com.nanzhufeng.ai.domain.AppearanceSettings
import com.nanzhufeng.ai.domain.AppearanceSettingsRepository

/** App-private preference owner for the settings appearance controls. */
class AndroidAppearanceSettingsRepository(context: Context) : AppearanceSettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): AppearanceSettings = AppearanceSettings(
        mode = preferences.getString(MODE, null).toEnumOrDefault(AppearanceMode.SYSTEM),
        accentColor = preferences.getString(ACCENT, null).toEnumOrDefault(AccentColor.ORANGE),
        fontSize = preferences.getString(FONT_SIZE, null).toEnumOrDefault(AppFontSize.STANDARD),
    )

    override fun save(settings: AppearanceSettings): AppearanceSettings {
        check(
            preferences.edit()
                .putString(MODE, settings.mode.name)
                .putString(ACCENT, settings.accentColor.name)
                .putString(FONT_SIZE, settings.fontSize.name)
                .commit(),
        ) { "外观设置无法写入本机。" }
        return load()
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
        runCatching { enumValueOf<T>(this ?: return default) }.getOrDefault(default)

    private companion object {
        const val FILE = "appearance_settings_v1"
        const val MODE = "appearance_mode"
        const val ACCENT = "accent_color"
        const val FONT_SIZE = "app_font_size"
    }
}
