package com.nanzhufeng.ai.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** Keeps surrounding metadata quiet while every concrete model name remains visually explicit. */
internal fun modelNameAnnotatedText(
    prefix: String = "",
    modelName: String,
    suffix: String = "",
): AnnotatedString = buildAnnotatedString {
    append(prefix)
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(modelName) }
    append(suffix)
}

internal fun modelNameAnnotatedTextIn(text: String, modelName: String): AnnotatedString {
    val start = text.indexOf(modelName)
    if (modelName.isBlank() || start < 0) return AnnotatedString(text)
    return modelNameAnnotatedText(
        prefix = text.substring(0, start),
        modelName = modelName,
        suffix = text.substring(start + modelName.length),
    )
}
