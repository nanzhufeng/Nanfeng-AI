// Measure the textarea itself so placeholder, wrapping and restored drafts share one height owner.
export function resizeComposer(input) {
  if (!input) return;
  input.style.height = '36px';
  const measuredHeight = input.scrollHeight;
  const height = Math.max(36, Math.min(190, measuredHeight));
  input.style.maxHeight = '190px';
  input.style.height = `${height}px`;
  input.style.overflowY = measuredHeight > height ? 'auto' : 'hidden';
}
