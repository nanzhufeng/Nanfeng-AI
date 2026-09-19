package com.nanzhufeng.ai.ui

import com.nanzhufeng.ai.domain.AnswerContextSourceDisclosure
import com.nanzhufeng.ai.domain.AssistantResponseModelAttribution
import com.nanzhufeng.ai.domain.ContextSelectionAuditRecord
import com.nanzhufeng.ai.domain.answerContextDisclosure
import com.nanzhufeng.ai.domain.definition

internal data class AnswerInformationDisclosure(
    val styleLabel: String?,
    val webSearchUsed: Boolean?,
    val sources: List<AnswerContextSourceDisclosure>,
    val webSearchRequested: Boolean? = null,
) {
    val networkLabel: String get() = when {
        webSearchUsed == true -> "已实际使用"
        webSearchRequested == true -> "联网未完成"
        webSearchRequested == false && webSearchUsed == false -> "本次未使用"
        else -> "无法确认（旧记录）"
    }
}

/** One content-free projection owns every fact shown by the answer-level information dialog. */
internal fun List<ContextSelectionAuditRecord>.answerInformationDisclosure(
    responseAttributions: List<AssistantResponseModelAttribution>,
): AnswerInformationDisclosure {
    val durableStyleLabels = responseAttributions.mapNotNull { it.conversationStyle?.definition()?.label }.distinct()
    val contextStyleLabels = flatMap { record ->
        record.selectedSources.filter { it.kind == "对话风格" }.map { it.title }
    }.distinct()
    val sourceRows = flatMap { it.answerContextDisclosure().sources }
        .filterNot { it.kind == "对话风格" }
        .distinctBy { "${it.kind}\u0000${it.stableId}" }
    val requested = when {
        responseAttributions.any { it.webSearchRequested == true } -> true
        responseAttributions.isNotEmpty() && responseAttributions.all { it.webSearchRequested == false } -> false
        else -> null
    }
    val actualWebSearch = when {
        responseAttributions.any { it.webSearchUsed == true } -> true
        requested != null -> false
        any { it.webSearchUsed == true } -> true
        else -> null // Historical false conflated not-requested and unverified requests.
    }
    return AnswerInformationDisclosure(
        styleLabel = (durableStyleLabels.ifEmpty { contextStyleLabels }).takeIf { it.isNotEmpty() }?.joinToString("、"),
        webSearchUsed = actualWebSearch,
        webSearchRequested = requested,
        sources = sourceRows,
    )
}

internal data class AnswerContextSourceGroup(val label: String, val titles: List<String>)

internal fun List<AnswerContextSourceDisclosure>.answerContextSourceGroups(): List<AnswerContextSourceGroup> =
    filterNot { it.kind in setOf("对话风格", "STYLE") }
        .groupBy { source ->
            when (source.kind) {
                "记忆", "Memory", "MEMORY" -> "长期记忆"
                "知识库", "KNOWLEDGE" -> "资料库"
                "PERSONA" -> "个性化资料"
                "CURRENT_PATH" -> "当前对话路径"
                else -> source.kind
            }
        }.map { (label, sources) ->
            AnswerContextSourceGroup(label, sources.flatMap { source ->
                if (label == "个性化资料") source.title.split("、", "；") else listOf(source.title)
            }.map(String::trim).filter { it.isNotEmpty() && it != label }.distinct())
        }
