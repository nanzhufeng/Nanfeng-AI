// Tracks already-authorized local saves without retaining their arguments or
// retrying operations. Each existing owner still handles state and errors.
export function createLocalWriteBarrier() {
  const pending = new Set();
  let version = 0;
  const unreportedFailures = [];
  return {
    get version() { return version; },
    track(operation, recoveryOwner = null) {
      const promise = Promise.resolve(operation);
      version += 1;
      const writeVersion = version;
      pending.add(promise);
      void promise.then(() => {
        pending.delete(promise);
        // Only a confirmed later save by the same owner acknowledges its old
        // failures. Independent writes and newer failures still block close.
        if (recoveryOwner) {
          for (let index = unreportedFailures.length - 1; index >= 0; index -= 1) {
            const failure = unreportedFailures[index];
            if (failure.recoveryOwner === recoveryOwner && failure.writeVersion < writeVersion) unreportedFailures.splice(index, 1);
          }
        }
      }, error => {
        pending.delete(promise);
        unreportedFailures.push({ error, recoveryOwner, writeVersion });
      });
      return promise;
    },
    async flush() {
      while (pending.size) await Promise.allSettled([...pending]);
      // A failure may settle while the close owner is draining a draft first.
      // Report it once even then; never retry a credential write implicitly.
      if (unreportedFailures.length) {
        const failures = unreportedFailures.splice(0);
        if (failures.length === 1) throw failures[0].error;
        throw new AggregateError(failures.map(failure => failure.error), '多项本地写入失败，窗口保持打开');
      }
    },
  };
}
