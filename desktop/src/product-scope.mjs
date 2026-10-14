// Product scope only; retained WORK storage and native services are unchanged.
export const WORKSPACES_VISIBLE = false;
export const canOpenDataArea = area => area === 'CHAT' || (WORKSPACES_VISIBLE && area === 'WORK');
