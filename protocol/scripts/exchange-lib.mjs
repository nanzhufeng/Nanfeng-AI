import { createHash } from 'node:crypto';
import { deflateRawSync, inflateRawSync } from 'node:zlib';

export const FORMAT = 'nfai.exchange';
export const VERSION = 1;
export const PACKAGE_FORMAT = 'nfai.exchange.package';
export const MAX_ENTRIES = 100_000;
export const MAX_UNCOMPRESSED_BYTES = 128 * 1024 * 1024;
const forbidden = /(?:credential|api[_-]?key|authorization|provider[_-]?(?:raw|payload)|runtime[_-]?chunk|diagnostic|route[_-]?pref|\buri\b|\bpath\b)/i;

export const sha256 = value => createHash('sha256').update(value).digest('hex');
export const utf8 = value => Buffer.from(value, 'utf8');

/** RFC 8785-like canonical form restricted to the v1 JSON value set. */
export function canonicalJson(value) {
  if (value === null) return 'null';
  if (typeof value === 'string') return JSON.stringify(value);
  if (typeof value === 'boolean') return value ? 'true' : 'false';
  if (typeof value === 'number') {
    if (!Number.isSafeInteger(value)) throw new Error('only safe integer numbers are allowed');
    return String(value);
  }
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  if (typeof value === 'object') return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(',')}}`;
  throw new Error(`unsupported canonical JSON value: ${typeof value}`);
}

function clone(value) { return JSON.parse(JSON.stringify(value)); }
export function semanticHash(exchange) {
  const copy = clone(exchange);
  delete copy.export.semanticHash;
  return sha256(utf8(canonicalJson(copy)));
}

function required(object, keys, where) {
  if (!object || typeof object !== 'object' || Array.isArray(object)) throw new Error(`${where}: expected object`);
  for (const key of keys) if (!(key in object)) throw new Error(`${where}: missing ${key}`);
}
function id(value, where) { if (typeof value !== 'string' || !/^[a-z][a-z0-9_-]{1,63}$/.test(value)) throw new Error(`${where}: invalid stable id`); }
function hash(value, where) { if (typeof value !== 'string' || !/^[a-f0-9]{64}$/.test(value)) throw new Error(`${where}: invalid sha256`); }
function noForbidden(value, where = '') {
  if (Array.isArray(value)) return value.forEach((item, index) => noForbidden(item, `${where}[${index}]`));
  if (!value || typeof value !== 'object') return;
  for (const [key, item] of Object.entries(value)) {
    if (forbidden.test(key)) throw new Error(`${where}.${key}: forbidden exchange field`);
    noForbidden(item, `${where}.${key}`);
  }
}
function unique(items, select, where) {
  const seen = new Set();
  for (const item of items) { const key = select(item); if (seen.has(key)) throw new Error(`${where}: duplicate ${key}`); seen.add(key); }
}

/** The schema is authoritative; this mirrors the security-critical v1 constraints for both spike runtimes. */
export function validateExchange(exchange) {
  required(exchange, ['format', 'version', 'export', 'projects', 'conversations', 'knowledge', 'memory', 'relations', 'settings'], 'exchange');
  if (exchange.format !== FORMAT || exchange.version !== VERSION) throw new Error('unsupported exchange version');
  noForbidden(exchange);
  id(exchange.export.id, 'export.id'); hash(exchange.export.semanticHash, 'export.semanticHash');
  if (exchange.export.semanticHash !== semanticHash(exchange)) throw new Error('semantic hash mismatch');
  if (!['ANDROID', 'DESKTOP'].includes(exchange.export.origin?.platform)) throw new Error('invalid origin platform');
  if (!['NORMAL', 'HIGH_SENSITIVE'].includes(exchange.export.sensitivity)) throw new Error('invalid export sensitivity');
  for (const group of ['projects', 'conversations', 'knowledge', 'memory', 'relations']) if (!Array.isArray(exchange[group])) throw new Error(`${group} must be an array`);
  if (exchange.projects.length > 10_000 || exchange.conversations.length > 10_000 || exchange.knowledge.length > 50_000 || exchange.memory.length > 50_000 || exchange.relations.length > 100_000) throw new Error('entry limit exceeded');
  unique(exchange.projects, item => item.id, 'projects'); unique(exchange.conversations, item => item.id, 'conversations'); unique(exchange.knowledge, item => item.id, 'knowledge'); unique(exchange.memory, item => item.id, 'memory'); unique(exchange.relations, item => item.id, 'relations');
  const objectIds = new Set();
  for (const group of ['projects', 'conversations', 'knowledge', 'memory', 'relations']) for (const item of exchange[group]) { id(item.id, `${group}.id`); if (objectIds.has(item.id)) throw new Error(`cross-domain stable ID collision: ${item.id}`); objectIds.add(item.id); }
  for (const conversation of exchange.conversations) {
    if (conversation.projectId !== null && !objectIds.has(conversation.projectId)) throw new Error('conversation references missing project');
    if (!Array.isArray(conversation.messages) || conversation.messages.length === 0) throw new Error('conversation needs message tree');
    unique(conversation.messages, item => item.id, 'messages');
    const messages = new Set(conversation.messages.map(message => message.id));
    if (!messages.has(conversation.currentLeafId)) throw new Error('current leaf missing');
    for (const message of conversation.messages) {
      id(message.id, 'message.id'); if (message.parentId !== null && !messages.has(message.parentId)) throw new Error('message parent missing');
      if (message.delivery === 'PARTIAL' && message.role !== 'assistant') throw new Error('only assistant can be partial');
      if (!Array.isArray(message.blocks)) throw new Error('message blocks missing');
      for (const block of message.blocks) {
        if (!['TEXT', 'MARKDOWN', 'CODE', 'ASSET_REF'].includes(block.kind)) throw new Error('invalid block kind');
        if (block.kind === 'ASSET_REF') { required(block.asset, ['id', 'entry', 'byteCount', 'sha256'], 'asset'); id(block.asset.id, 'asset.id'); hash(block.asset.sha256, 'asset.sha256'); if (block.asset.entry !== `assets/${block.asset.sha256}`) throw new Error('asset must be content addressed'); }
        else if (typeof block.text !== 'string') throw new Error('text block missing text');
      }
    }
  }
  for (const item of exchange.knowledge) { hash(item.contentHash, 'knowledge.contentHash'); if (sha256(utf8(item.body)) !== item.contentHash) throw new Error('knowledge content hash mismatch'); }
  for (const item of exchange.memory) { hash(item.contentHash, 'memory.contentHash'); if (sha256(utf8(item.body)) !== item.contentHash) throw new Error('memory content hash mismatch'); }
  return { hasHighSensitiveData: exchange.export.sensitivity === 'HIGH_SENSITIVE' || JSON.stringify(exchange).includes('HIGH_SENSITIVE'), semanticHash: exchange.export.semanticHash };
}

function u16(value) { const out = Buffer.alloc(2); out.writeUInt16LE(value); return out; }
function u32(value) { const out = Buffer.alloc(4); out.writeUInt32LE(value >>> 0); return out; }
function crc32(buffer) { let crc = 0xffffffff; for (const byte of buffer) { crc ^= byte; for (let bit = 0; bit < 8; bit += 1) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1)); } return (crc ^ 0xffffffff) >>> 0; }
function zipRecord(path, bytes, offset) {
  const name = utf8(path); const compressed = deflateRawSync(bytes, { level: 9 }); const crc = crc32(bytes);
  const local = Buffer.concat([u32(0x04034b50), u16(20), u16(0), u16(8), u16(0), u16(0), u32(crc), u32(compressed.length), u32(bytes.length), u16(name.length), u16(0), name, compressed]);
  const central = Buffer.concat([u32(0x02014b50), u16(20), u16(20), u16(0), u16(8), u16(0), u16(0), u32(crc), u32(compressed.length), u32(bytes.length), u16(name.length), u16(0), u16(0), u16(0), u16(0), u32(0), u32(offset), name]);
  return { local, central };
}
export function makeZip(entries) {
  let offset = 0; const locals = []; const centrals = [];
  for (const [path, bytes] of entries) { const record = zipRecord(path, bytes, offset); locals.push(record.local); centrals.push(record.central); offset += record.local.length; }
  const central = Buffer.concat(centrals); return Buffer.concat([...locals, central, u32(0x06054b50), u16(0), u16(0), u16(entries.length), u16(entries.length), u32(central.length), u32(offset), u16(0)]);
}
export function readZip(buffer) {
  const entries = new Map(); let offset = 0; let total = 0;
  while (offset + 4 <= buffer.length && buffer.readUInt32LE(offset) === 0x04034b50) {
    if (entries.size >= MAX_ENTRIES) throw new Error('zip entry limit exceeded');
    const method = buffer.readUInt16LE(offset + 8); const compressedSize = buffer.readUInt32LE(offset + 18); const uncompressedSize = buffer.readUInt32LE(offset + 22); const nameLength = buffer.readUInt16LE(offset + 26); const extraLength = buffer.readUInt16LE(offset + 28);
    const name = buffer.subarray(offset + 30, offset + 30 + nameLength).toString('utf8'); const start = offset + 30 + nameLength + extraLength; const end = start + compressedSize;
    if (!name || name.startsWith('/') || name.includes('..') || name.includes('\\') || entries.has(name)) throw new Error('unsafe or duplicate zip entry');
    if (method !== 8 || end > buffer.length || uncompressedSize > MAX_UNCOMPRESSED_BYTES || compressedSize === 0 || uncompressedSize / compressedSize > 200) throw new Error('unsafe zip compression');
    const data = inflateRawSync(buffer.subarray(start, end)); if (data.length !== uncompressedSize) throw new Error('zip size mismatch'); total += data.length; if (total > MAX_UNCOMPRESSED_BYTES) throw new Error('zip total limit exceeded'); entries.set(name, data); offset = end;
  }
  if (entries.size === 0) throw new Error('empty zip'); return entries;
}
export function packageExchange(exchange, assets = new Map()) {
  validateExchange(exchange);
  const payload = new Map([
    ['payload/projects.json', utf8(canonicalJson(exchange.projects))], ['payload/conversations.json', utf8(canonicalJson(exchange.conversations))], ['payload/knowledge.json', utf8(canonicalJson(exchange.knowledge))], ['payload/memory.json', utf8(canonicalJson(exchange.memory))], ['payload/relations.json', utf8(canonicalJson(exchange.relations))], ['payload/settings.safe.json', utf8(canonicalJson(exchange.settings))]
  ]);
  for (const [path, value] of assets) payload.set(path, Buffer.from(value));
  const files = [...payload].map(([path, bytes]) => ({ path, byteCount: bytes.length, sha256: sha256(bytes) })).sort((a, b) => a.path.localeCompare(b.path));
  const manifest = { format: PACKAGE_FORMAT, packageVersion: 1, exchangeVersion: VERSION, export: exchange.export, files };
  return makeZip([['manifest.json', utf8(canonicalJson(manifest))], ...[...payload].sort(([a], [b]) => a.localeCompare(b))]);
}
export function preflightPackage(bytes) {
  const entries = readZip(bytes); const manifestBytes = entries.get('manifest.json'); if (!manifestBytes) throw new Error('manifest missing');
  const manifest = JSON.parse(manifestBytes); if (manifest.format !== PACKAGE_FORMAT || manifest.packageVersion !== 1 || manifest.exchangeVersion !== VERSION || !Array.isArray(manifest.files)) throw new Error('unsupported manifest');
  if (entries.size !== manifest.files.length + 1) throw new Error('unknown package entry');
  for (const file of manifest.files) { if (!entries.has(file.path) || sha256(entries.get(file.path)) !== file.sha256 || entries.get(file.path).length !== file.byteCount) throw new Error(`manifest integrity mismatch: ${file.path}`); }
  const exchange = { format: FORMAT, version: VERSION, export: manifest.export, projects: JSON.parse(entries.get('payload/projects.json')), conversations: JSON.parse(entries.get('payload/conversations.json')), knowledge: JSON.parse(entries.get('payload/knowledge.json')), memory: JSON.parse(entries.get('payload/memory.json')), relations: JSON.parse(entries.get('payload/relations.json')), settings: JSON.parse(entries.get('payload/settings.safe.json')) };
  const result = validateExchange(exchange);
  for (const conversation of exchange.conversations) for (const message of conversation.messages) for (const block of message.blocks) if (block.kind === 'ASSET_REF') { const asset = block.asset; const bytes = entries.get(asset.entry); if (!bytes || bytes.length !== asset.byteCount || sha256(bytes) !== asset.sha256) throw new Error('asset integrity mismatch'); }
  return { exchange, ...result, entryCount: entries.size };
}
