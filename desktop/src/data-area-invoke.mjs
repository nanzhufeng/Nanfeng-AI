function staleDataAreaError() {
  return Object.assign(new Error('原数据区的操作结果已留在原区'), { code: 'STALE_DATA_AREA' });
}

// The request keeps the owner from invocation time. A late receipt cannot be
// interpreted using whichever area happens to be visible when it completes.
export async function invokeForDataArea(bridge, command, args, owner, currentOwner) {
  let result;
  try {
    result = await bridge.invoke(command, args, { headers: { 'x-nanfeng-data-area': owner.dataArea } });
  } catch (error) {
    if (currentOwner() !== owner) throw staleDataAreaError();
    throw error;
  }
  if (currentOwner() !== owner) throw staleDataAreaError();
  return result?.exchange ? { ...result, dataArea: owner.dataArea } : result;
}

// A user-confirmed operation may need to finish its ordered follow-up after
// navigation. Return its receipt to that operation's owner; the caller must
// guard every visible projection against the current area before rendering.
export function invokeForAuthorizedDataAreaOperation(bridge, command, args, owner) {
  return bridge.invoke(command, args, { headers: { 'x-nanfeng-data-area': owner.dataArea } });
}
