/** UI cadence only; provider receiving and durable checkpoints remain native. */
export function generationRefreshInterval(state, hidden = false) {
  if (hidden) return 1000;
  return ['imagePreview', 'pdfPreview', 'textPreview', 'archivePreview', 'videoPreview', 'audioPreview'].some(key => state?.[key]) ? 500 : 80;
}

export function hasRunningGeneration(state, activeRequests = 0) {
  if (activeRequests > 0 || state?.pendingChatSubmission || state?.ordinarySendPreparing) return true;
  return (state?.current?.exchange?.conversations || []).some(conversation =>
    (conversation.messages || []).some(message => message.delivery === 'PARTIAL' && message.runtimeState === 'RUNNING'));
}
