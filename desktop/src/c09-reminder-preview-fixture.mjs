const basePlan = {
  instruction: '只读公开状态，不外发通知。',
  scheduleKind: 'DAILY',
  anchorLocal: '2099-09-03T09:30',
  timezoneId: 'Asia/Shanghai',
  missedPolicy: 'SKIP',
  nextRunAtMs: 4092091800000,
  lastChargeMicros: null,
  updatedAtMs: 1,
};

export function createC09ReminderPreviewProjection(mode = 'empty') {
  if (mode !== 'states') return { drafts: [], plans: [], diagnostics: [] };
  return {
    drafts: [],
    diagnostics: [],
    plans: [
      { ...basePlan, planId: 'c09-active', title: '公开项目每日检查', status: 'ACTIVE', latestResult: null, lastSafeErrorCode: null },
      { ...basePlan, planId: 'c09-paused', title: '已暂停的长期跟踪', status: 'PAUSED', nextRunAtMs: null, latestResult: null, lastSafeErrorCode: null },
      { ...basePlan, planId: 'c09-failed', title: '配置待完成', status: 'FAILED', latestResult: null, lastSafeErrorCode: 'PROVIDER_NOT_CONFIGURED' },
      { ...basePlan, planId: 'c09-result', title: '已保存结果', status: 'COMPLETED', nextRunAtMs: null, latestResult: '公开状态已读取，结果保存在本机样本中。', lastSafeErrorCode: null },
    ],
  };
}

export function createC09ReminderPreviewDraft(timezoneId = 'Asia/Shanghai') {
  return {
    draftId: `c09-draft-${Date.now()}`,
    status: 'PENDING_REVIEW',
    title: '新的监控',
    instruction: '',
    scheduleKind: 'DAILY',
    anchorLocal: '2099-09-03T09:30',
    timezoneId,
    missedPolicy: 'RUN_ONCE',
    providerId: 'browser-fixture',
    actualModelId: null,
    requestedModelId: null,
    chargeMicros: null,
  };
}
