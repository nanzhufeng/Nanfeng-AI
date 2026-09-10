const TEXT_MIME_TYPES = new Set(['text/plain', 'text/markdown', 'application/json', 'text/csv']);
const VIDEO_MIME_TYPES = new Set(['video/mp4', 'video/webm']);
const AUDIO_MIME_TYPES = new Set(['audio/mpeg', 'audio/wav', 'audio/mp4']);

function normalizedMime(value) {
  return String(value || '').trim().toLowerCase().split(';', 1)[0];
}

/**
 * C08 shared capability owner for conversation attachments and full-screen search.
 * Unsupported formats deliberately remain inside the app as an explicit fail-closed
 * boundary; callers must never substitute an external system opener.
 */
export function attachmentPreviewCapability(attachment = {}) {
  const mimeType = normalizedMime(attachment.mimeType);
  const contentKind = String(attachment.contentKind || '').toUpperCase();
  if (contentKind === 'IMAGE' || mimeType.startsWith('image/')) {
    return { kind: 'image', action: 'open-image-preview', label: '应用内图片预览', supported: true };
  }
  if (contentKind === 'VIDEO' || VIDEO_MIME_TYPES.has(mimeType)) {
    return { kind: 'video', action: 'open-video-preview', label: '应用内视频预览', supported: true };
  }
  if (contentKind === 'AUDIO' || AUDIO_MIME_TYPES.has(mimeType)) {
    return { kind: 'audio', action: 'open-audio-preview', label: '应用内音频预览', supported: true };
  }
  if (mimeType === 'application/pdf') {
    return { kind: 'pdf', action: 'open-pdf-preview', label: '应用内 PDF 阅读', supported: true };
  }
  if (TEXT_MIME_TYPES.has(mimeType)) {
    return { kind: 'text', action: 'open-text-preview', label: '应用内安全文本预览', supported: true };
  }
  return {
    kind: 'unsupported',
    action: 'open-preview-boundary',
    label: '应用内安全预览边界',
    supported: false,
    reason: mimeType
      ? '此文件类型当前没有经过验证的应用内解析器。文件未交给系统应用，也未上传或外发。'
      : '文件类型未知，已按安全边界停止。文件未交给系统应用，也未上传或外发。',
  };
}
