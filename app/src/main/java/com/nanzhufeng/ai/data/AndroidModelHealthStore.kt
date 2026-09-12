package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.ModelHealth
import com.nanzhufeng.ai.domain.ModelHealthObservation
import com.nanzhufeng.ai.domain.ModelHealthStore
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ProviderId
import java.time.Instant

/** Local, content-free health observations. They hold neither prompts nor credentials. */
class AndroidModelHealthStore(context: Context) : ModelHealthStore {
    private val preferences = context.getSharedPreferences("model-health-v1", Context.MODE_PRIVATE)

    override fun observation(providerId: ProviderId, presetId: ModelPresetId): ModelHealthObservation? = runCatching {
        val value = preferences.getString(key(providerId, presetId), null) ?: return null
        val (health, epoch) = value.split('|', limit = 2)
        ModelHealthObservation(ModelHealth.valueOf(health), Instant.ofEpochMilli(epoch.toLong()))
    }.getOrNull()

    override fun record(providerId: ProviderId, presetId: ModelPresetId, observation: ModelHealthObservation) {
        preferences.edit().putString(key(providerId, presetId), "${observation.health.name}|${observation.checkedAt.toEpochMilli()}").apply()
    }

    private fun key(providerId: ProviderId, presetId: ModelPresetId) = "${providerId.name}:${presetId.name}" +
        if (providerId == ProviderId.DEEPSEEK && presetId == ModelPresetId.DEEPSEEK_V4_FLASH) ":deepseek-flash-v4.1" else ""
}
