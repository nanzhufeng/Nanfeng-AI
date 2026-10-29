import { relayCoreModel } from './relaycore-models.mjs';
import { layerTabStops } from './modal-layer-owner.mjs';
export const MODEL_BRAND_NAMES = Object.freeze({
  OPENROUTER: 'openrouter', RELAYCORE: 'relaycore', DEEPSEEK: 'deepseek', ZHIPU: 'zhipu', QWEN: 'qwen',
  RELAYCORE_CLAUDE: 'claude', RELAYCORE_GPT: 'gpt', RELAYCORE_GEMINI: 'gemini',
});

export const MODEL_SETTINGS_PURPOSES = Object.freeze({
  CLAUDE_OPUS_5_5: '深度推理 · 复杂分析与代码',
  RELAYCORE_OPUS_5_5: '深度推理 · 复杂分析与代码',
  CLAUDE_SONNET_5_5: '日常对话 · 写作与代码',
  RELAYCORE_SONNET_5_5: '日常对话 · 写作与代码',
  CLAUDE_HAIKU_5_5: '快速回复 · 高频轻量任务',
  GPT_6_1_SOL: '专业分析 · 工程与复杂任务',
  RELAYCORE_GPT_6_1_SOL: '专业分析 · 工程与复杂任务',
  GPT_6_1_TERRA: '日常工作 · 图像理解与代码',
  MERCURY_3: '快速文字 · 代码与联网检索',
  GPT_6_LUNA: '轻量问答 · 成本与速度均衡',
  RELAYCORE_GPT_6_ASTRA: '高难推理 · 长程复杂任务',
  GEMINI_3_8_FLASH: '快速处理文字、图片与文件',
  RELAYCORE_GEMINI_3_8_FLASH: '快速处理文字、图片与文件',
  RELAYCORE_GEMINI_3_1_PRO: '复杂分析 · 多媒体理解',
  QWEN_3_7_PLUS: '日常问答 · 轻量多媒体任务',
  QWEN_3_8_MAX: '深度分析 · 复杂任务',
  QWEN_3_6_FLASH: '知识整理 · 快速检索',
  DEEPSEEK_V4_PRO: '深度推理 · 专业分析',
  DEEPSEEK_V4_FLASH: '快速问答 · 图片理解',
  GLM_5_3: '复杂分析 · 推理与 Agent',
  GLM_5_3_FLASH: '快速文本问答 · 高频任务',
});

export function modelSettingsProviderTarget(current, requested, platform = false, lastRelay = 'RELAYCORE_CLAUDE') {
  if (platform && requested === 'RELAYCORE_CLAUDE') {
    return current?.startsWith('RELAYCORE_') ? current : ['RELAYCORE_CLAUDE', 'RELAYCORE_GPT', 'RELAYCORE_GEMINI'].includes(lastRelay) ? lastRelay : 'RELAYCORE_CLAUDE';
  }
  return requested;
}

const modelSettingsGroupSelector = '[role=tablist], [role=radiogroup]';
function modelSettingsControlIdentity(control) {
  const attribute = control.dataset.presetId ? 'data-preset-id' : 'data-provider-id';
  return { attribute, value: control.getAttribute(attribute),
    platform: control.getAttribute('data-provider-platform'),
    role: control.closest(modelSettingsGroupSelector)?.getAttribute('role') };
}
function matchesModelSettingsControl(control, identity) {
  return control?.getAttribute?.(identity.attribute) === identity.value
    && control.getAttribute('data-provider-platform') === identity.platform
    && control.closest(modelSettingsGroupSelector)?.getAttribute('role') === identity.role;
}

export function installModelSettingsKeyboardNavigation(root = document, captureScope = () => () => true, wrapWindow = () => false) {
  const listener = event => {
    if (event.key === 'Tab') {
      if (!wrapWindow() || event.defaultPrevented || event.ctrlKey || event.altKey || event.metaKey || event.isComposing
        || event.target !== root.activeElement || !root.querySelector?.('.android-settings-model-configuration')
        || root.querySelector?.('.scrim, .android-settings-picker-scrim')) return;
      const controls = layerTabStops(root);
      const target = event.shiftKey && root.activeElement === controls[0] ? controls.at(-1)
        : !event.shiftKey && root.activeElement === controls.at(-1) ? controls[0] : null;
      if (target) { event.preventDefault(); target.focus({ preventScroll: true }); }
      return;
    }
    const control = event.target.closest?.('.model-settings-browser button');
    const group = control?.closest('[role=tablist], [role=radiogroup]');
    if (!control || !group || !['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key)) return;
    const choices = [...group.querySelectorAll('button')].filter(button => !button.disabled);
    if (!choices.length) return;
    const step = ['ArrowLeft', 'ArrowUp'].includes(event.key) ? -1 : 1;
    const index = choices.indexOf(control);
    const next = choices[event.key === 'Home' ? 0 : event.key === 'End' ? choices.length - 1 : (index + step + choices.length) % choices.length];
    const previousIdentity = modelSettingsControlIdentity(control);
    const targetIdentity = modelSettingsControlIdentity(next);
    event.preventDefault();
    next.click();
    const isCurrent = captureScope();
    requestAnimationFrame(() => {
      if (!isCurrent()) return;
      const focused = root.activeElement;
      if (focused && focused !== root.body && focused.isConnected
        && !matchesModelSettingsControl(focused, previousIdentity)
        && !matchesModelSettingsControl(focused, targetIdentity)) return;
      const targets = [...root.querySelectorAll('.model-settings-browser button')];
      targets.find(button => !button.disabled && button.isConnected
        && matchesModelSettingsControl(button, targetIdentity))?.focus();
    });
  };
  root.addEventListener('keydown', listener);
  return () => root.removeEventListener('keydown', listener);
}

/** Restore logical focus after a settings render, never after a new user focus. */
export function preserveModelSettingsFocus(root = document, isCurrent = () => true) {
  const control = root.activeElement;
  if (!control || control !== control.closest?.('.model-settings-browser button')) return;
  const identity = modelSettingsControlIdentity(control);
  const scroller = control.closest('.model-settings-model-list');
  const scroll = scroller ? { top: scroller.scrollTop, left: scroller.scrollLeft } : null;
  if (!identity.role || !identity.value) return;
  queueMicrotask(() => {
    if (!isCurrent() || control.isConnected) return;
    if (root.activeElement && root.activeElement !== root.body && root.activeElement.isConnected) return;
    const next = [...root.querySelectorAll('.model-settings-browser button')].find(button =>
      !button.disabled && button.isConnected && matchesModelSettingsControl(button, identity));
    const nextScroller = next?.closest('.model-settings-model-list');
    if (nextScroller && scroll) {
      nextScroller.scrollTop = scroll.top;
      nextScroller.scrollLeft = scroll.left;
    }
    next?.focus({ preventScroll: true });
  });
}

/** The model vendor is independent of its OpenRouter / RelayCore transport. */
export function composerModelBrand(modelId, providerId) {
  const preset = String(modelId || '').toUpperCase();
  const provider = String(providerId || relayCoreModel(preset)?.providerId || '').toUpperCase();
  if (provider.startsWith('RELAYCORE_')) return MODEL_BRAND_NAMES[provider] || 'relaycore';
  if (preset.startsWith('CLAUDE_')) return 'claude';
  if (preset.startsWith('GPT_')) return 'gpt';
  if (preset.startsWith('GEMINI_')) return 'gemini';
  if (preset.startsWith('DEEPSEEK_')) return 'deepseek';
  if (preset.startsWith('QWEN_')) return 'qwen';
  if (preset.startsWith('GLM_')) return 'zhipu';
  return MODEL_BRAND_NAMES[provider] || null;
}
