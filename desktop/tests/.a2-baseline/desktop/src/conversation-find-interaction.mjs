export const CONVERSATION_FIND_SETTLE_MS = 420;

export function createConversationFindInputController({
  onSettled,
  setTimer = globalThis.setTimeout,
  clearTimer = globalThis.clearTimeout,
  settleMs = CONVERSATION_FIND_SETTLE_MS,
} = {}) {
  let timer = null;
  let composing = false;

  const cancel = () => {
    if (timer !== null) clearTimer(timer);
    timer = null;
  };

  const settle = value => {
    cancel();
    const query = String(value || '');
    timer = setTimer(() => {
      timer = null;
      onSettled?.(query);
    }, settleMs);
  };

  return {
    cancel,
    onCompositionStart() {
      composing = true;
      cancel();
    },
    onCompositionEnd(value) {
      composing = false;
      settle(value);
    },
    onInput({ value, isComposing = false } = {}) {
      if (composing || isComposing) {
        cancel();
        return;
      }
      settle(value);
    },
  };
}

export function conversationFindScrollTop({ scrollTop, ownerTop, ownerHeight, targetTop, targetHeight }) {
  const targetCenter = Number(scrollTop) + (Number(targetTop) - Number(ownerTop)) + Number(targetHeight) / 2;
  return Math.max(0, targetCenter - Number(ownerHeight) / 2);
}
