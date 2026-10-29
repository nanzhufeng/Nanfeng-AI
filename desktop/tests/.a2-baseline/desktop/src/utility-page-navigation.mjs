// Paint the destination before reading disk. A late read belongs to the area
// state that started it, even if another area now shows the same page.
export function createUtilityPageNavigation({ currentOwner, isCurrent, render, loading, failed }) {
  const pending = new WeakMap();
  return (page, read) => {
    const owner = currentOwner();
    let ownerPending = pending.get(owner);
    if (!ownerPending) {
      ownerPending = new Map();
      pending.set(owner, ownerPending);
    }
    if (ownerPending.has(page)) {
      render();
      return ownerPending.get(page);
    }
    loading(page, true, owner);
    const task = Promise.resolve()
      .then(() => isCurrent(page, owner) ? read(owner) : undefined)
      .catch(error => {
        if (isCurrent(page, owner) && error?.code !== 'STALE_DATA_AREA') failed(page, error, owner);
      })
      .finally(() => {
        ownerPending.delete(page);
        loading(page, false, owner);
        if (isCurrent(page, owner)) render();
      });
    ownerPending.set(page, task);
    render();
    return task;
  };
}
