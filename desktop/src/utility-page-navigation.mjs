// Paint navigation before disk work. One read per page; a late completion must
// not repaint another page or put its error into an unrelated workflow.
export function createUtilityPageNavigation({ isCurrent, render, loading, failed }) {
  const pending = new Map();
  return (page, read) => {
    if (pending.has(page)) {
      render();
      return pending.get(page);
    }
    loading(page, true);
    const task = Promise.resolve().then(read).catch(error => {
      if (isCurrent(page)) failed(page, error);
    }).finally(() => {
      pending.delete(page);
      loading(page, false);
      if (isCurrent(page)) render();
    });
    pending.set(page, task);
    render();
    return task;
  };
}
