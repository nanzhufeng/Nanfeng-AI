/**
 * The single Desktop owner for the Compare execution state machine.
 *
 * This phase intentionally accepts only non-content request facts. It has no
 * credential store, persistence, Tauri command, endpoint client, or logging.
 */
export const DESKTOP_COMPARE_EXECUTION_PROVIDER = Object.freeze({
  id: 'openrouter',
  protocol: 'OPENAI_COMPATIBLE',
  endpoint: 'https://openrouter.ai/api/v1/chat/completions',
});

export const DesktopCompareBlocker = Object.freeze({
  EMPTY_DRAFT: 'EMPTY_DRAFT',
  ATTACHMENTS_NOT_SUPPORTED: 'ATTACHMENTS_NOT_SUPPORTED',
  CREDENTIAL_NOT_CONFIGURED: 'CREDENTIAL_NOT_CONFIGURED',
  MODEL_OR_PRICE_UNVERIFIED: 'MODEL_OR_PRICE_UNVERIFIED',
  EXECUTION_NOT_COMPOSED: 'EXECUTION_NOT_COMPOSED',
});

/** Safe Settings projection. It intentionally cannot trigger a Keychain probe or network call. */
export const DESKTOP_COMPARE_SETTINGS_PROJECTION = Object.freeze({
  provider: 'OpenRouter',
  protocol: DESKTOP_COMPARE_EXECUTION_PROVIDER.protocol,
  credentialPresence: 'NOT_CHECKED',
  executionState: 'BLOCKED',
  blocker: 'CREDENTIAL_CHECK_REQUIRED',
  presets: Object.freeze([
    Object.freeze({ logicalModel: 'ChatGPT', providerModel: '等待目录核验', price: '价格未知，禁止执行' }),
    Object.freeze({ logicalModel: 'Claude', providerModel: '等待目录核验', price: '价格未知，禁止执行' }),
  ]),
});

export class DesktopCompareExecutionOwner {
  #readiness;

  constructor({ credentialPresent = false, fixedModelsVerified = false, fixedPricesKnown = false, transportComposed = false } = {}) {
    this.#readiness = Object.freeze({ credentialPresent, fixedModelsVerified, fixedPricesKnown, transportComposed });
  }

  /** Returns safe configuration facts only; neither a key nor user content can enter this shape. */
  status() {
    const blocker = this.#blockerForReadiness();
    return Object.freeze({ provider: DESKTOP_COMPARE_EXECUTION_PROVIDER.id, protocol: DESKTOP_COMPARE_EXECUTION_PROVIDER.protocol, enabled: blocker === null, blocker });
  }

  /** Explicit Compare command; metadata prevents this owner from retaining user content. */
  requestDirectCompare({ hasText, attachmentCount = 0 } = {}) {
    if (!hasText) return blocked(DesktopCompareBlocker.EMPTY_DRAFT);
    if (attachmentCount > 0) return blocked(DesktopCompareBlocker.ATTACHMENTS_NOT_SUPPORTED);
    return blocked(this.#blockerForReadiness() || DesktopCompareBlocker.EXECUTION_NOT_COMPOSED);
  }

  #blockerForReadiness() {
    if (!this.#readiness.credentialPresent) return DesktopCompareBlocker.CREDENTIAL_NOT_CONFIGURED;
    if (!this.#readiness.fixedModelsVerified || !this.#readiness.fixedPricesKnown) return DesktopCompareBlocker.MODEL_OR_PRICE_UNVERIFIED;
    if (!this.#readiness.transportComposed) return DesktopCompareBlocker.EXECUTION_NOT_COMPOSED;
    return null;
  }
}

function blocked(blocker) { return Object.freeze({ outcome: 'BLOCKED', blocker }); }
