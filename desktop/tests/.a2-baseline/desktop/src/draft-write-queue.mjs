// Local draft writes only: this queue must never retry Provider or sync requests.
export function createDraftWriteQueue({ prepare = async value => value, write,
  routeKey = value => JSON.stringify([value.workspaceId, value.conversationId ?? null]) }) {
  let tail = Promise.resolve();
  let sequence = 0;
  const pending = new Map();
  const latestByRoute = new Map();

  async function attempt(entry) {
    if (!pending.has(entry.sequence)) return;
    try {
      // A first-input initialization failure can initially have no workspace ID.
      // Compare routes only after the existing native owner resolves that identity.
      entry.target ??= await prepare(structuredClone(entry.snapshot));
      entry.key = routeKey(entry.target);
      if ((latestByRoute.get(entry.key) ?? 0) > entry.sequence) {
        pending.delete(entry.sequence);
        return;
      }
      latestByRoute.set(entry.key, entry.sequence);
      for (const older of pending.values()) {
        if (older.key === entry.key && older.sequence < entry.sequence) pending.delete(older.sequence);
      }
      const result = await write(entry.target);
      pending.delete(entry.sequence);
      return result;
    } catch (error) {
      entry.error = error;
      throw error;
    }
  }

  function enqueue(entry) {
    const operation = tail.then(() => attempt(entry));
    // The caller receives the error. The scheduling tail stays usable and pending
    // retains the snapshot; another route's success cannot conceal this failure.
    tail = operation.catch(() => {});
    return operation;
  }

  async function settle() {
    let observed;
    do { observed = tail; await observed; } while (observed !== tail);
  }

  return {
    save(args) {
      const entry = { sequence: ++sequence, snapshot: structuredClone(args) };
      pending.set(entry.sequence, entry);
      return enqueue(entry);
    },
    async flush() {
      await settle();
      // Retry retained local writes once per explicit flush. Persistent failure
      // stops this flush rather than spinning; another close may retry later.
      for (const entry of [...pending.values()]) await enqueue(entry);
      await settle();
      if (pending.size) throw pending.values().next().value.error ?? new Error('草稿仍未保存');
    },
    discardRoute(key) {
      // The native owner has confirmed that this route no longer exists. A
      // retained failed write must not block another route or window close.
      for (const entry of pending.values()) if (entry.key === key) pending.delete(entry.sequence);
      latestByRoute.delete(key);
    },
  };
}
