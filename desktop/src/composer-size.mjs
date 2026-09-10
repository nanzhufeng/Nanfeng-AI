// Measure the textarea itself so placeholder, wrapping and restored drafts share one height owner.
export function resizeComposer(input) {
  if (!input) return;
  input.style.height = '36px';
  const height = Math.max(36, Math.min(190, input.scrollHeight));
  input.style.maxHeight = '190px';
  input.style.height = `${height}px`;
  input.style.overflowY = input.scrollHeight > height ? 'auto' : 'hidden';
}
