package com.nanzhufeng.ai.domain

import java.time.Clock

class CaptureDraftFactory(private val clock: Clock) {
    fun fromManualText(text: String): CaptureDraft = create(text = text, attachments = emptyList(), sourceType = CaptureSourceType.MANUAL_TEXT)

    fun fromAndroidTextShare(text: String, sourcePackage: String?): CaptureDraft = create(
        text = text,
        attachments = emptyList(),
        sourceType = CaptureSourceType.ANDROID_TEXT_SHARE,
        sourceReference = sourcePackage,
    )

    fun fromImage(attachment: AttachmentReference, sourceReference: String?): CaptureDraft = create(
        text = null,
        attachments = listOf(attachment),
        sourceType = CaptureSourceType.IMAGE,
        sourceReference = sourceReference,
    )

    private fun create(
        text: String?,
        attachments: List<AttachmentReference>,
        sourceType: CaptureSourceType,
        sourceReference: String? = null,
    ): CaptureDraft {
        val now = clock.instant()
        return CaptureDraft(
            id = CaptureDraftId.new(),
            text = text?.trim()?.takeIf(String::isNotEmpty),
            attachments = attachments,
            sourceEvidence = listOf(SourceEvidence(
                sourceType = sourceType,
                receivedAt = now,
                sourceReference = sourceReference,
                contributedFields = buildSet {
                    if (!text.isNullOrBlank()) add("text")
                    if (attachments.isNotEmpty()) add("attachments")
                },
            )),
            createdAt = now,
        )
    }
}
