import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { isV2PickerDisplayName, semanticHash, validateExchangeV2 } from './exchange-v2-lib.mjs';

const root = resolve(import.meta.dirname, '..');
const exchange = JSON.parse(await readFile(resolve(root, 'fixtures/nfai.exchange.v2.golden.json'), 'utf8'));
assert.equal(exchange.export.semanticHash, semanticHash(exchange), 'v2 golden semantic hash must be canonical');
assert.equal(validateExchangeV2(exchange).semanticHash, exchange.export.semanticHash);
const forbidden = structuredClone(exchange); forbidden.knowledge[0].sourceEvidence[0].sourceReference = 'content://forbidden';
forbidden.export.semanticHash = semanticHash(forbidden);
assert.throws(() => validateExchangeV2(forbidden), 'source locators must reject rather than silently lose privacy scope');
const lossy = structuredClone(exchange); delete lossy.projects[0].instructionHistory; lossy.export.semanticHash = semanticHash(lossy);
assert.throws(() => validateExchangeV2(lossy), 'owner history must be required');
assert.ok(isV2PickerDisplayName('nanfeng-ai-workspace-v2.nfai-exchange'));
assert.ok(isV2PickerDisplayName('nanfeng-ai-workspace-v2.nfai-exchange.zip'));
for (const name of ['nanfeng-ai-workspace-v2.nfai-exchange.zip.exe', 'nanfeng-ai-workspace-v2.zip.nfai-exchange', 'nanfeng-ai-workspace-v2.nfai-exchange.nfai-exchange', '/tmp/nanfeng-ai-workspace-v2.nfai-exchange']) {
  assert.ok(!isV2PickerDisplayName(name), `v2 picker filename must reject ${name}`);
}
console.log(JSON.stringify({ semanticHash: exchange.export.semanticHash, hasHighSensitiveData: validateExchangeV2(exchange).hasHighSensitiveData }));
