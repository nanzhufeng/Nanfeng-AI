const message = (id, text, createdAt) => ({
  id,
  parentId: null,
  ordinal: 0,
  role: 'user',
  delivery: 'COMPLETE',
  revision: 1,
  createdAt,
  blocks: [{ kind: 'TEXT', text }],
});

const conversation = ({ id, title, createdAt, updatedAt, revision, archived, deleted }) => ({
  id,
  projectId: null,
  title,
  currentLeafId: `message-${id}`,
  pinned: false,
  archived,
  deleted,
  revision,
  createdAt,
  updatedAt,
  messages: [message(`message-${id}`, `${title} local-only fixture`, createdAt)],
});

export const C13_FAVORITE_CONVERSATION_ID = 'conversation-c13-favorite';

export function createC13ConversationLifecyclePreview() {
  const conversations = [
    conversation({
      id: C13_FAVORITE_CONVERSATION_ID,
      title: 'C13 Favorite',
      createdAt: '2026-09-03T18:10:00+08:00',
      updatedAt: '2026-09-03T18:20:00+08:00',
      revision: 2,
      archived: false,
      deleted: false,
    }),
    conversation({
      id: 'conversation-c13-archived',
      title: 'C13 Archived',
      createdAt: '2026-09-03T18:21:00+08:00',
      updatedAt: '2026-09-03T18:31:00+08:00',
      revision: 3,
      archived: true,
      deleted: false,
    }),
    conversation({
      id: 'conversation-c13-recycle',
      title: 'C13 Recycle',
      createdAt: '2026-09-03T18:22:00+08:00',
      updatedAt: '2026-09-03T18:32:00+08:00',
      revision: 4,
      archived: true,
      deleted: true,
    }),
  ];
  return {
    summary: {
      id: 'workspace-c13-lifecycle-preview',
      title: 'C13 对话管理验收',
      semanticHash: 'c13-browser-read-only',
      packageHash: 'c13-browser-read-only',
      projectCount: 0,
      conversationCount: conversations.length,
      knowledgeCount: 0,
      memoryCount: 0,
      relationCount: 0,
      assetCount: 0,
      assetByteCount: 0,
      highSensitive: false,
    },
    exchange: { projects: [], conversations, knowledge: [], memory: [], relations: [] },
  };
}
