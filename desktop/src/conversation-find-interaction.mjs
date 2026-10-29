export const CONVERSATION_FIND_SETTLE_MS = 420;

export function createConversationFindInputController({
  onSettled,
  setTimer = globalThis.setTimeout,
  clearTimer = globalThis.clearTimeout,
  settleMs = CONVERSATION_FIND_SETTLE_MS,
} = {}) {
  let timer = null;
  let composing = false;
  let revision = 0;
  let scope = null;

  const cancelPending = () => {
    revision++;
    if (timer !== null) clearTimer(timer);
    timer = null;
  };
  const cancel = () => {
    cancelPending();
    composing = false;
  };

  const settle = value => {
    cancelPending();
    const query = String(value || '');
    const ownedRevision = revision;
    timer = setTimer(() => {
      if (ownedRevision !== revision) return;
      timer = null;
      onSettled?.(query);
    }, settleMs);
  };

  return {
    cancel,
    setScope(...identity) {
      if (scope && identity.length === scope.length && identity.every((value, index) => Object.is(value, scope[index]))) return;
      cancel();
      scope = identity;
    },
    onCompositionStart() {
      composing = true;
      cancelPending();
    },
    onCompositionEnd(value) {
      composing = false;
      settle(value);
    },
    onInput({ value, isComposing = false } = {}) {
      if (composing || isComposing) {
        cancelPending();
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

export function conversationFindTarget(message, matches, selectedIndex) {
  const selected = matches?.[selectedIndex];
  if (!message || !selected || message.dataset.messageId !== selected.messageId) return null;
  const occurrences = matches.filter(match => match.messageId === selected.messageId);
  const marks = [...message.querySelectorAll('.chat-message-body mark')];
  // Markdown source can contain occurrences without a corresponding rendered
  // mark (for example inside an href). Do not guess another occurrence then.
  if (marks.length !== occurrences.length) return message;
  const mark = marks[occurrences.indexOf(selected)];
  return mark?.getClientRects().length ? mark : message;
}
