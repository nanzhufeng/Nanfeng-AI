// Attachment IDs are owned by a workspace; imported workspaces may reuse IDs.
// An encoded tuple avoids delimiter collisions without exposing local paths.
export function attachmentPreviewKey(workspaceId, attachmentId) {
  return JSON.stringify([String(workspaceId || ''), String(attachmentId || '')]);
}
