/** Local navigation metadata only. Conversation bodies and drafts remain in SQLite. */
export const APP_ENTRY_RETENTION_MS = 15 * 60 * 1000;
export function retainedEntry(saved, now = Date.now()) {
  if (!saved || typeof saved.workspaceId !== 'string' || !saved.workspaceId ||
      typeof saved.conversationId !== 'string' || !saved.conversationId ||
      !Number.isFinite(saved.backgroundAt) || saved.backgroundAt <= 0 || now < saved.backgroundAt) return null;
  if (!saved.hadRunningGeneration && now - saved.backgroundAt >= APP_ENTRY_RETENTION_MS) return null;
  return {workspaceId:saved.workspaceId,conversationId:saved.conversationId};
}
export class DesktopAppEntry {
  constructor(storage, clock = () => Date.now()) { this.storage=storage; this.clock=clock; this.background=null; }
  key(area) { return `nanfeng-ai.desktop.app-entry.v1.${area === 'WORK' ? 'WORK' : 'CHAT'}`; }
  read(area) {
    try { return retainedEntry(JSON.parse(this.storage.getItem(this.key(area)) || 'null'),this.clock()); }
    catch { return null; }
  }
  leave(area, workspaceId, conversationId, hadRunningGeneration = false) {
    if (this.background) return;
    const record={workspaceId:workspaceId || null,conversationId:conversationId || null,backgroundAt:this.clock(),hadRunningGeneration:Boolean(hadRunningGeneration)};
    this.background=record;
    try { this.storage.setItem(this.key(area),JSON.stringify(record)); } catch { /* optional navigation metadata */ }
  }
  enter(hadRunningGeneration = false) {
    const previous=this.background;
    this.background=null;
    return Boolean(previous && !previous.hadRunningGeneration && !hadRunningGeneration &&
      this.clock() - previous.backgroundAt >= APP_ENTRY_RETENTION_MS);
  }
}
