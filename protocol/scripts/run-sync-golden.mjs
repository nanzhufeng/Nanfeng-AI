import assert from 'node:assert/strict';
import { createCipheriv, createDecipheriv, pbkdf2Sync, createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const fixturePath = resolve(root, 'fixtures/nfai.sync.v1.golden.json');
const sha256 = (value) => createHash('sha256').update(value).digest('hex');
const canonical = (value) => {
  if (value === null) return 'null';
  if (typeof value === 'string') return JSON.stringify(value);
  if (typeof value === 'boolean') return value ? 'true' : 'false';
  if (typeof value === 'number') { assert(Number.isSafeInteger(value)); return String(value); }
  if (Array.isArray(value)) return `[${value.map(canonical).join(',')}]`;
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonical(value[key])}`).join(',')}}`;
};
const b64 = (value) => Buffer.from(value).toString('base64url');
const unb64 = (value) => Buffer.from(value, 'base64url');
const aadFor = (envelope) => Buffer.from(canonical({
  appId: envelope.appId, documentId: envelope.documentId, format: envelope.format,
  payloadHash: envelope.payloadHash, protocolVersion: envelope.protocolVersion,
  revision: envelope.revision, schemaVersion: envelope.schemaVersion,
}));
const aesGcm = (key, nonce, plain, aad) => {
  const cipher = createCipheriv('aes-256-gcm', key, nonce); cipher.setAAD(aad);
  return Buffer.concat([cipher.update(plain), cipher.final(), cipher.getAuthTag()]);
};
const decrypt = (key, nonce, ciphertext, aad) => {
  const decipher = createDecipheriv('aes-256-gcm', key, nonce); decipher.setAAD(aad);
  decipher.setAuthTag(ciphertext.subarray(-16)); return Buffer.concat([decipher.update(ciphertext.subarray(0, -16)), decipher.final()]);
};
const build = (fixture) => {
  const payloadText = canonical(fixture.payload); const payloadBytes = Buffer.from(payloadText);
  const envelope = {
    format: 'nfai.sync.envelope', protocolVersion: 1, schemaVersion: 1,
    appId: fixture.payload.appId, documentId: fixture.payload.documentId, revision: fixture.payload.revision,
    payloadHash: sha256(payloadBytes), payloadByteCount: payloadBytes.length,
    kdf: { algorithm: 'PBKDF2-HMAC-SHA256', version: 1, iterations: 210000, salt: fixture.material.salt },
    wrappedDataKey: { algorithm: 'AES-256-GCM', nonce: fixture.material.wrappingNonce, ciphertext: '' },
    payload: { algorithm: 'AES-256-GCM', nonce: fixture.material.payloadNonce, ciphertext: '' },
  };
  const wrappingKey = pbkdf2Sync(Buffer.from(fixture.recoveryCode), unb64(fixture.material.salt), 210000, 32, 'sha256');
  const dataKey = unb64(fixture.material.dataKey); const aad = aadFor(envelope);
  envelope.wrappedDataKey.ciphertext = b64(aesGcm(wrappingKey, unb64(envelope.wrappedDataKey.nonce), dataKey, aad));
  envelope.payload.ciphertext = b64(aesGcm(dataKey, unb64(envelope.payload.nonce), payloadBytes, aad));
  return { payloadText, envelope };
};
const fixture = JSON.parse(await readFile(fixturePath, 'utf8'));
const built = build(fixture);
if (process.argv.includes('--print-envelope')) { console.log(JSON.stringify(built.envelope, null, 2)); process.exit(0); }
assert.equal(canonical(built.envelope), canonical(fixture.envelope), 'golden envelope must be deterministic');
const wrapKey = pbkdf2Sync(Buffer.from(fixture.recoveryCode), unb64(fixture.envelope.kdf.salt), 210000, 32, 'sha256');
const dataKey = decrypt(wrapKey, unb64(fixture.envelope.wrappedDataKey.nonce), unb64(fixture.envelope.wrappedDataKey.ciphertext), aadFor(fixture.envelope));
const opened = decrypt(dataKey, unb64(fixture.envelope.payload.nonce), unb64(fixture.envelope.payload.ciphertext), aadFor(fixture.envelope));
assert.equal(opened.toString(), built.payloadText);
console.log(`sync golden payload=${fixture.envelope.payloadHash} envelope=${sha256(Buffer.from(canonical(fixture.envelope)))}`);
