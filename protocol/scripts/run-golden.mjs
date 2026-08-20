import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { canonicalJson, packageExchange, preflightPackage, semanticHash, sha256, utf8 } from './exchange-lib.mjs';

const root = resolve(import.meta.dirname, '..');
const fixturePath = resolve(root, 'fixtures/nfai.exchange.v1.golden.json');
const assetPath = resolve(root, 'fixtures/assets/golden-note.txt');
const exchange = JSON.parse(await readFile(fixturePath, 'utf8'));
if (process.argv.includes('--print-semantic-hash')) { console.log(semanticHash(exchange)); process.exit(0); }
assert.equal(exchange.export.semanticHash, semanticHash(exchange), 'golden semantic hash must be canonical');
const asset = await readFile(assetPath); const assetHash = sha256(asset); assert.equal(assetHash, exchange.conversations[0].messages[1].blocks[0].asset.sha256);
const source = packageExchange(exchange, new Map([[`assets/${assetHash}`, asset]]));
const desktopImported = preflightPackage(source);
assert.equal(desktopImported.semanticHash, exchange.export.semanticHash);
const desktopRoundtrip = packageExchange(desktopImported.exchange, new Map([[`assets/${assetHash}`, asset]]));
assert.deepEqual(desktopRoundtrip, source, 'deterministic byte-for-byte desktop roundtrip');
assert.equal(canonicalJson(desktopImported.exchange), canonicalJson(exchange), 'semantic roundtrip must preserve IR');
const corrupted = Buffer.from(source); corrupted[48] ^= 1; assert.throws(() => preflightPackage(corrupted), 'corrupt manifest bytes must reject');
const dangerous = packageExchange(exchange, new Map([[`../credential.txt`, utf8('no')]])); assert.throws(() => preflightPackage(dangerous));
await mkdir(resolve(root, 'artifacts'), { recursive: true }); await writeFile(resolve(root, 'artifacts/nfai.exchange.v1.golden.nfai-exchange'), source);
console.log(JSON.stringify({ semanticHash: exchange.export.semanticHash, packageHash: sha256(source), bytes: source.length, entryCount: desktopImported.entryCount, hasHighSensitiveData: desktopImported.hasHighSensitiveData }));
