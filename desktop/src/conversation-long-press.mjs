export function createConversationLongPressController({
  thresholdMs = 520,
  movementTolerancePx = 8,
  setTimer = globalThis.setTimeout?.bind(globalThis),
  clearTimer = globalThis.clearTimeout?.bind(globalThis),
  onOpen,
} = {}) {
  let active = null;
  let suppressClickFor = null;

  function clearActive() {
    if (active?.timerId != null) clearTimer(active.timerId);
    active = null;
  }

  function cancel() {
    clearActive();
    suppressClickFor = null;
  }

  function pointerDown(event, value) {
    clearActive();
    suppressClickFor = null;
    if (!value || event?.isPrimary === false || (event?.pointerType === 'mouse' && Number(event?.button || 0) !== 0)) return false;
    const pointerId = event.pointerId;
    active = {
      pointerId,
      value,
      startX: Number(event.clientX || 0),
      startY: Number(event.clientY || 0),
      opened: false,
      timerId: null,
    };
    active.timerId = setTimer(() => {
      if (!active || active.pointerId !== pointerId || active.opened) return;
      active.timerId = null;
      active.opened = true;
      suppressClickFor = active.value;
      onOpen?.(active.value);
    }, thresholdMs);
    return true;
  }

  function pointerMove(event) {
    if (!active || active.pointerId !== event.pointerId || active.opened) return false;
    const dx = Number(event.clientX || 0) - active.startX;
    const dy = Number(event.clientY || 0) - active.startY;
    if (Math.hypot(dx, dy) <= movementTolerancePx) return false;
    clearActive();
    return true;
  }

  function pointerUp(event) {
    if (!active || active.pointerId !== event.pointerId) return false;
    const opened = active.opened;
    clearActive();
    return opened;
  }

  function pointerCancel(event) {
    if (!active || active.pointerId !== event.pointerId) return false;
    clearActive();
    suppressClickFor = null;
    return true;
  }

  function consumeClick(value) {
    if (!value || suppressClickFor !== value) return false;
    suppressClickFor = null;
    return true;
  }

  return { pointerDown, pointerMove, pointerUp, pointerCancel, cancel, consumeClick };
}
