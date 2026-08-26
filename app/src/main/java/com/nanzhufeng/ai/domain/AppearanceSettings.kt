package com.nanzhufeng.ai.domain

/** Durable, local-only presentation preferences. They never affect conversation data. */
enum class AppearanceMode { SYSTEM, LIGHT, DARK }

enum class AccentColor { ORANGE, BLUE, BLACK, GREEN, YELLOW, PINK, PURPLE }

/** App-wide text and icon scale. Standard deliberately preserves the pre-setting visual baseline. */
enum class AppFontSize(val scale: Float, val iconScale: Float) {
    SMALL(0.80f, 0.80f),
    STANDARD(1.0f, 1.0f),
    LARGE(1.24f, 1.24f),
}

data class AppearanceSettings(
    val mode: AppearanceMode = AppearanceMode.SYSTEM,
    /** 南枫 AI keeps orange as its first-run brand default. */
    val accentColor: AccentColor = AccentColor.ORANGE,
    val fontSize: AppFontSize = AppFontSize.STANDARD,
)

interface AppearanceSettingsRepository {
    fun load(): AppearanceSettings
    fun save(settings: AppearanceSettings): AppearanceSettings
}

class LoadAppearanceSettingsUseCase(private val repository: AppearanceSettingsRepository) {
    fun execute(): AppearanceSettings = repository.load()
}

class SaveAppearanceSettingsUseCase(private val repository: AppearanceSettingsRepository) {
    fun execute(settings: AppearanceSettings): AppearanceSettings = repository.save(settings)
}
