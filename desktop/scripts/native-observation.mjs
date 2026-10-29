import { setTimeout as delay } from 'node:timers/promises';

// Acceptance reads only. Await the result of every IPC before deciding whether a
// native state is ready; a Promise itself must never count as a successful read.
export async function observeNativeUntil({ read, accept, timeoutMs = 5000, intervalMs = 40 }) {
  const deadline = performance.now() + timeoutMs;
  const timeoutError = () => new Error('Native acceptance observation timed out before the expected state was confirmed');
  let timer;
  let timedOut = false;
  const expired = new Promise((_, reject) => {
    timer = setTimeout(() => {
      timedOut = true;
      reject(timeoutError());
    }, timeoutMs);
  });
  const observe = async () => {
    while (!timedOut && performance.now() < deadline) {
      const value = await read();
      if (timedOut || performance.now() >= deadline) break;
      const accepted = await accept(value);
      if (timedOut || performance.now() >= deadline) break;
      if (accepted === true) return value;
      await delay(Math.min(intervalMs, Math.max(0, deadline - performance.now())));
    }
    throw timeoutError();
  };
  try { return await Promise.race([observe(), expired]); }
  finally { clearTimeout(timer); }
}
