import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';
import { canonicalJson, sha256, validateExchangeV2 } from './exchange-v2-lib.mjs';

const packagePath = process.argv[2];
assert.ok(packagePath, 'usage: node verify-v2-package.mjs <package.nfai-exchange>');
const zip = '/usr/bin/unzip';
const list = execFileSync(zip, ['-Z1', resolve(packagePath)], { encoding: 'utf8' })
  .split('\n').filter(Boolean);
assert.ok(list.length > 0 && new Set(list).size === list.length, 'ZIP entries must be unique');
assert.ok(list.every(path => path && !path.startsWith('/') && !path.includes('..') && !path.includes('\\')), 'ZIP entry path is unsafe');
const entries = new Map(list.map(path => [path, execFileSync(zip, ['-p', resolve(packagePath), path])]));
const manifest = JSON.parse(entries.get('manifest.json')?.toString('utf8') ?? '');
assert.deepEqual(Object.keys(manifest).sort(), ['exchangeVersion', 'export', 'files', 'format', 'packageVersion']);
assert.equal(manifest.format, 'nfai.exchange.package');
assert.equal(manifest.packageVersion, 2);
assert.equal(manifest.exchangeVersion, 2);
assert.equal(manifest.files.length + 1, entries.size);
const listed = new Set();
for (const file of manifest.files) {
  assert.deepEqual(Object.keys(file).sort(), ['byteCount', 'path', 'sha256']);
  const bytes = entries.get(file.path);
  assert.ok(file.path !== 'manifest.json' && !listed.has(file.path) && bytes, 'manifest entry is missing or duplicate');
  listed.add(file.path);
  assert.equal(bytes.length, file.byteCount);
  assert.equal(sha256(bytes), file.sha256);
}
assert.deepEqual([...listed].sort(), [...entries.keys()].filter(path => path !== 'manifest.json').sort());
const exchange = JSON.parse(entries.get('exchange.json')?.toString('utf8') ?? '');
const receipt = validateExchangeV2(exchange);
assert.equal(canonicalJson(manifest.export), canonicalJson(exchange.export));
const assets = new Map();
const record = asset => {
  assert.deepEqual(Object.keys(asset).sort(), ['byteCount', 'classification', 'displayName', 'entry', 'id', 'mimeType', 'sha256']);
  assert.equal(asset.entry, `assets/${asset.sha256}`);
  const previous = assets.get(asset.id);
  assert.ok(!previous || canonicalJson(previous) === canonicalJson(asset), 'same asset ID metadata must be identical');
  assets.set(asset.id, asset);
};
for (const conversation of exchange.conversations) for (const message of conversation.messages) for (const block of message.blocks) if (block.kind === 'ASSET_REF') record(block.asset);
for (const knowledge of exchange.knowledge) for (const asset of knowledge.attachments) record(asset);
for (const asset of assets.values()) {
  const bytes = entries.get(asset.entry);
  assert.ok(bytes, 'ledger asset missing');
  assert.equal(bytes.length, asset.byteCount);
  assert.equal(sha256(bytes), asset.sha256);
}
assert.deepEqual([...entries.keys()].filter(path => path.startsWith('assets/')).sort(), [...new Set([...assets.values()].map(asset => asset.entry))].sort());
console.log(JSON.stringify({ semanticHash: receipt.semanticHash, assets: assets.size, entries: entries.size }));
