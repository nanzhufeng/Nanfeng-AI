package com.nanzhufeng.ai.data

import android.content.Intent
import android.net.Uri
import com.nanzhufeng.ai.domain.CaptureSourceType
import com.nanzhufeng.ai.domain.CaptureTextInput
import java.security.MessageDigest

sealed interface AndroidTextShareReadResult {
    data class Accepted(val input: CaptureTextInput, val fingerprint: String) : AndroidTextShareReadResult
    data object Ignored : AndroidTextShareReadResult
}

/** Converts the Android share contract into a source-aware domain input without writing persistence. */
class AndroidTextShareAdapter {
    fun read(intent: Intent?): AndroidTextShareReadResult {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.lowercase() != "text/plain") {
            return AndroidTextShareReadResult.Ignored
        }
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: return AndroidTextShareReadResult.Ignored
        return AndroidTextShareReadResult.Accepted(
            input = CaptureTextInput(
                text = text,
                sourceType = CaptureSourceType.ANDROID_TEXT_SHARE,
                sourceReference = sourcePackage(intent),
            ),
            fingerprint = fingerprint(text, sourcePackage(intent)),
        )
    }

    private fun sourcePackage(intent: Intent): String? = intent
        .getStringExtra(Intent.EXTRA_REFERRER_NAME)
        ?.let(::packageFromAndroidAppReferrer)

    private fun packageFromAndroidAppReferrer(value: String): String? {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        val packageName = uri.takeIf { it.scheme == "android-app" }?.host ?: return null
        return packageName.takeIf { PACKAGE_NAME.matches(it) }
    }

    private fun fingerprint(text: String, sourcePackage: String?): String = MessageDigest.getInstance("SHA-256")
        .digest("ACTION_SEND|text/plain|${sourcePackage.orEmpty()}|$text".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
    }
}

/** Activity-scoped gate: Android can redeliver the same ACTION_SEND during recreation or onNewIntent. */
class AndroidTextShareIntentGate(initialFingerprint: String? = null) {
    private var handledFingerprint: String? = initialFingerprint

    fun consume(result: AndroidTextShareReadResult): CaptureTextInput? = when (result) {
        AndroidTextShareReadResult.Ignored -> null
        is AndroidTextShareReadResult.Accepted -> result.takeIf { it.fingerprint != handledFingerprint }?.also {
            handledFingerprint = it.fingerprint
        }?.input
    }

    fun savedFingerprint(): String? = handledFingerprint
}
