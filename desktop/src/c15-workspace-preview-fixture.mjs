export const C15_PROJECT_ID = 'project-c15-local';

export const C15_WORKSPACE_COPY = Object.freeze({
  projectTitle: 'C15_Project',
  projectDescription: 'C15_local-only_fixture',
  knowledgeTitle: 'C15_Knowledge',
  knowledgeBody: 'C15_local-only_knowledge_fixture',
  emptyWorkTitle: '还没有项目工作对话',
  emptyWorkDetail: '从左侧项目中创建或打开一条对话。',
});

/**
 * Browser-only C15 fixture. It mirrors the isolated Android records captured
 * on 2026-09-03 and never reads or writes the Desktop SQLite data root.
 */
export function createC15WorkspacePreview() {
  return {
    summary: {
      id: 'workspace-c15-browser',
      title: 'C15 Android 同状态隔离样本',
      semanticHash: 'c15-browser-read-only',
      packageHash: 'c15-browser-read-only',
      projectCount: 1,
      conversationCount: 1,
      knowledgeCount: 1,
      memoryCount: 1,
      relationCount: 0,
      assetCount: 0,
      assetByteCount: 0,
      highSensitive: false,
    },
    exchange: {
      projects: [{
        id: C15_PROJECT_ID,
        title: C15_WORKSPACE_COPY.projectTitle,
        description: C15_WORKSPACE_COPY.projectDescription,
        pinned: true,
        archived: false,
        revision: 1,
      }],
      // The Android fixture has zero project work conversations. This one
      // unscoped conversation is deliberate: work mode must never leak it.
      conversations: [{
        id: 'conversation-c15-ordinary',
        projectId: null,
        title: 'C15 普通对话不可进入工作态',
        createdAt: '2026-09-03T06:25:00Z',
        updatedAt: '2026-09-03T06:25:00Z',
        archived: false,
        pinned: false,
        revision: 1,
        messages: [],
      }],
      knowledge: [{
        id: 'knowledge-c15-local',
        title: C15_WORKSPACE_COPY.knowledgeTitle,
        body: C15_WORKSPACE_COPY.knowledgeBody,
        tags: ['c15', 'local'],
        status: 'ACTIVE',
        scope: 'GLOBAL',
        projectId: null,
        revision: 1,
        classification: 'NORMAL',
      }],
      memory: [{
        id: 'memory-c15-local',
        body: 'C15_local-only_memory_fixture',
        scope: 'GLOBAL',
        status: 'ACTIVE',
        revision: 1,
      }],
      relations: [],
    },
  };
}
