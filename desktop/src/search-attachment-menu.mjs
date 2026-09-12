import { icon, icons } from './icon-source.mjs';

const escape = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
export function attachmentMenuAnchor(element) {
  const rect = element?.getBoundingClientRect?.();
  return rect ? { x: rect.left + 12, y: rect.top + Math.min(rect.height, 64) } : { x: 24, y: 96 };
}
export function renderChatAttachmentMenu(item, anchor = {}) {
  const x = Number.isFinite(anchor.x) ? anchor.x : 24;
  const y = Number.isFinite(anchor.y) ? anchor.y : 96;
  return `<div class="scrim search-attachment-menu-backdrop"><section class="dialog search-attachment-menu chat-attachment-menu" style="--menu-x:${x}px;--menu-y:${y}px" role="dialog" aria-modal="true" aria-labelledby="attachment-actions-title"><header><h2 id="attachment-actions-title">${escape(item.displayName)}</h2><button data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button></header><p>${escape(item.details)}</p><p>${escape(item.sentAt)}</p><div class="chat-attachment-menu-actions"><button data-action="chat-attachment-search">${icon(icons.search, '搜索定位')}<span>搜索定位</span></button><button data-action="chat-attachment-save">${icon(icons.download, '下载')}<span>下载</span></button><button data-action="chat-attachment-share">${icon(icons.share, '分享')}<span>分享</span></button></div></section></div>`;
}
export function renderSearchAttachmentMenu(hit, anchor = {}) {
  const x = Number.isFinite(anchor.x) ? anchor.x : 24;
  const y = Number.isFinite(anchor.y) ? anchor.y : 96;
  return `<div class="scrim search-attachment-menu-backdrop"><section class="dialog search-attachment-menu" style="--menu-x:${x}px;--menu-y:${y}px" role="dialog" aria-modal="true" aria-labelledby="attachment-actions-title"><h2 id="attachment-actions-title">${escape(hit.displayName || '附件')}</h2><button data-action="locate-search-attachment" data-entry-id="${escape(hit.entryId)}">${icon(icons.externalLink, '快速定位')}<span>快速定位</span></button><button class="danger" data-action="ask-delete-search-attachment" data-entry-id="${escape(hit.entryId)}">${icon(icons.trash, '删除')}<span>删除</span></button></section></div>`;
}
