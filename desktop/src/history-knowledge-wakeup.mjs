/** Wake the durable owner while the app remains open; SQLite owns eligibility and cost limits. */
export function createHistoryKnowledgeWakeup({ isEnabled, runDue, onError = () => {}, setTimer, clearTimer, intervalMs = 300_000 }) {
  let started = false;
  let running = false;
  let timer = null;
  const schedule = () => {
    if (started && timer === null) timer = setTimer(() => { timer = null; void wake(); }, intervalMs);
  };
  const wake = async () => {
    if (!started || running) return;
    if (timer !== null) { clearTimer(timer); timer = null; }
    running = true;
    try { if (isEnabled()) await runDue(); }
    catch (error) { onError(error); }
    finally { running = false; schedule(); }
  };
  return {
    start() { if (!started) { started = true; schedule(); } },
    stop() { started = false; if (timer !== null) clearTimer(timer); timer = null; },
    wake,
  };
}
