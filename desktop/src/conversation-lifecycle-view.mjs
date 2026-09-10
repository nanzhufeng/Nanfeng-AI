import { icon, icons } from './icon-source.mjs';

const escapeHtml = value => String(value ?? '').replace(
  /[&<>"']/g,
  char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[char],
);

const pad = value => String(value).padStart(2, '0');

export function formatConversationLifecycleTime(value) {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return '时间未记录';
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

const managementEntry = ({ glyph, title, summary, page }) => `
  <button class="conversation-lifecycle-entry" data-action="open-settings-page" data-page="${page}">
    <span class="conversation-lifecycle-entry-icon">${icon(glyph, title)}</span>
    <span><strong>${escapeHtml(title)}</strong><small>${escapeHtml(summary)}</small></span>
  </button>`;

export function renderConversationManagementRoot() {
  return `<div class="android-settings-page conversation-lifecycle-root"><section class="android-settings-card conversation-lifecycle-root-card">
    ${managementEntry({ glyph: icons.star, title: '收藏', summary: '查看并管理已收藏的本地对话。', page: 'favorites' })}
    <div class="android-settings-divider" aria-hidden="true"></div>
    ${managementEntry({ glyph: icons.archive, title: '已归档', summary: '查看并恢复暂时收起的会话；归档不会删除消息或附件。', page: 'archived' })}
    <div class="android-settings-divider" aria-hidden="true"></div>
    ${managementEntry({ glyph: icons.trash, title: '回收站', summary: '查看并恢复已移入回收站的会话；消息树仍保留在本机。', page: 'recycle' })}
  </section></div>`;
}

const lifecycleActionDisclosure = ({ item, page }) => {
  const id = escapeHtml(item.id);
  const revision = Number(item.revision || 0);
  const favorite = page === 'favorites';
  const recycle = page === 'recycle';
  const firstAction = favorite
    ? `<button data-action="toggle-conversation-favorite" data-id="${id}">${icon(icons.starOff, '取消收藏')}<span>取消收藏</span></button>`
    : `<button data-action="${recycle ? 'restore-deleted-conversation' : 'restore-conversation'}" data-id="${id}" data-revision="${revision}">${icon(icons.restore, '恢复')}<span>恢复</span></button>`;
  const deleteAction = favorite ? '' : `<button class="danger" data-action="${recycle ? 'open-conversation-permanent-delete' : 'open-archived-conversation-delete'}" data-id="${id}" data-revision="${revision}" data-title="${escapeHtml(item.title || '未命名会话')}">${icon(icons.trash, recycle ? '永久删除' : '移入回收站')}<span>删除</span></button>`;
  return `<details class="conversation-lifecycle-actions" data-lifecycle-action-disclosure="${id}">
    <summary aria-label="会话操作" title="会话操作">${icon(icons.more, '会话操作')}</summary>
    <span role="menu" aria-label="会话操作">${firstAction}${deleteAction}</span>
  </details>`;
};

const lifecycleRow = ({ item, page }) => {
  const prefix = page === 'favorites' ? '更新于' : '创建于';
  const time = page === 'favorites' ? item.updatedAt : item.createdAt;
  return `<div class="android-settings-list-row conversation-lifecycle-row">
    <button class="android-settings-lifecycle-open" data-action="select-chat" data-id="${escapeHtml(item.id)}">
      <strong>${escapeHtml(item.title || '未命名会话')}</strong>
      <small>${prefix} ${escapeHtml(formatConversationLifecycleTime(time))}</small>
    </button>
    ${lifecycleActionDisclosure({ item, page })}
  </div>`;
};

export function projectConversationLifecycle({ conversations = [], favoriteConversationIds = new Set() } = {}) {
  return {
    favorites: conversations.filter(item => favoriteConversationIds.has(item.id) && !item.archived && !item.deleted),
    archived: conversations.filter(item => item.archived && !item.deleted),
    recycle: conversations.filter(item => item.deleted),
  };
}

export function renderConversationLifecyclePage({ page, conversations = [], favoriteConversationIds = new Set() } = {}) {
  if (page === 'conversations') return renderConversationManagementRoot();
  const projection = projectConversationLifecycle({ conversations, favoriteConversationIds });
  const items = projection[page] || [];
  if (page === 'favorites') {
    return `<div class="android-settings-page android-settings-list android-settings-lifecycle">
      <p class="android-settings-helper conversation-lifecycle-helper">收藏的会话保存在本机；取消收藏不会删除消息或附件。</p>
      ${items.map(item => lifecycleRow({ item, page })).join('') || '<p class="android-settings-empty">暂无收藏会话。</p>'}
    </div>`;
  }
  const recycle = page === 'recycle';
  const summary = recycle ? '会话消息树尚未物理删除；恢复后会回到普通对话列表。' : '归档会话不会出现在日常列表；恢复后会回到普通对话列表。';
  const empty = recycle ? '暂无回收站会话。' : '暂无已归档会话。';
  return `<div class="android-settings-page android-settings-list android-settings-lifecycle">
    <div class="android-settings-lifecycle-head"><p class="android-settings-helper">${summary}</p>${items.length ? `<button class="${recycle ? 'danger' : ''}" data-action="open-conversation-bulk-cleanup" data-scope="${page}" data-count="${items.length}">${recycle ? '清空回收站' : '清空已归档'}</button>` : ''}</div>
    ${items.map(item => lifecycleRow({ item, page })).join('') || `<p class="android-settings-empty">${empty}</p>`}
  </div>`;
}
