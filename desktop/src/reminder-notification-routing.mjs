function asRecord(value) {
  if (value && typeof value === 'object' && !Array.isArray(value)) return value;
  if (typeof value !== 'string') return {};
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

export function reminderNotificationExtra(event) {
  const payload = asRecord(event?.payload ?? event);
  const notification = asRecord(event?.notification);
  const extra = asRecord(notification.extra ?? event?.extra ?? payload);
  return {
    route: typeof extra.route === 'string' ? extra.route : '',
    planId: typeof extra.planId === 'string' ? extra.planId : '',
    workspaceId: typeof extra.workspaceId === 'string' ? extra.workspaceId : '',
    conversationId: typeof extra.conversationId === 'string' ? extra.conversationId : '',
  };
}

export function reminderNotificationAction(event) {
  const payload = asRecord(event?.payload ?? event);
  const extra = reminderNotificationExtra(payload);
  return {
    clickId: typeof payload.clickId === 'string' ? payload.clickId : '',
    ...extra,
  };
}
