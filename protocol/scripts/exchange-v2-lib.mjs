import { createHash } from 'node:crypto';

export const FORMAT = 'nfai.exchange';
export const VERSION = 2;
const forbidden = /(?:credential|api[_-]?key|authorization|provider[_-]?(?:raw|payload)|runtime[_-]?chunk|diagnostic|route[_-]?pref|\buri\b|\bpath\b)/i;
export const sha256 = value => createHash('sha256').update(value).digest('hex');
export const utf8 = value => Buffer.from(value, 'utf8');

export function canonicalJson(value) {
  if (value === null) return 'null';
  if (typeof value === 'string') return JSON.stringify(value);
  if (typeof value === 'boolean') return value ? 'true' : 'false';
  if (typeof value === 'number') { if (!Number.isSafeInteger(value)) throw new Error('only safe integer numbers are allowed'); return String(value); }
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  if (value && typeof value === 'object') return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(',')}}`;
  throw new Error('unsupported canonical JSON value');
}
export function semanticHash(exchange) { const copy = JSON.parse(JSON.stringify(exchange)); delete copy.export.semanticHash; return sha256(utf8(canonicalJson(copy))); }
function object(value, where) { if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`${where}: expected object`); return value; }
function exact(value, keys, where) { const actual = Object.keys(object(value, where)).sort(); const expected = [...keys].sort(); if (actual.length !== expected.length || actual.some((key, index) => key !== expected[index])) throw new Error(`${where}: unknown or missing fields`); }
function id(value, where) { if (typeof value !== 'string' || !/^[a-z0-9][a-z0-9_-]{1,63}$/.test(value)) throw new Error(`${where}: invalid stable id`); }
function hash(value, where) { if (typeof value !== 'string' || !/^[a-f0-9]{64}$/.test(value)) throw new Error(`${where}: invalid sha256`); }
function noForbidden(value, where = '') {
  if (Array.isArray(value)) return value.forEach((item, index) => noForbidden(item, `${where}[${index}]`));
  if (!value || typeof value !== 'object') return;
  for (const [key, item] of Object.entries(value)) { if (forbidden.test(key) || key === 'sourceReference') throw new Error(`${where}.${key}: forbidden v2 field`); noForbidden(item, `${where}.${key}`); }
}
function unique(items, where) { const values = new Set(); for (const item of items) { id(item.id, `${where}.id`); if (values.has(item.id)) throw new Error(`${where}: duplicate id`); values.add(item.id); } return values; }
function ensureArray(value, where) { if (!Array.isArray(value)) throw new Error(`${where}: expected array`); return value; }

/** Exact v2 IR guard. It deliberately has no ZIP, disk, UI, provider, or database side effect. */
export function validateExchangeV2(exchange) {
  exact(exchange, ['format', 'version', 'export', 'projects', 'conversations', 'knowledge', 'memory', 'relations', 'settings'], 'exchange');
  if (exchange.format !== FORMAT || exchange.version !== VERSION) throw new Error('unsupported exchange version');
  noForbidden(exchange);
  exact(exchange.export, ['id', 'createdAt', 'origin', 'semanticHash', 'sensitivity'], 'export'); id(exchange.export.id, 'export.id'); hash(exchange.export.semanticHash, 'export.semanticHash');
  if (!['ANDROID', 'DESKTOP'].includes(exchange.export.origin?.platform) || exchange.export.semanticHash !== semanticHash(exchange)) throw new Error('invalid export identity or semantic hash');
  const projects = ensureArray(exchange.projects, 'projects'); const conversations = ensureArray(exchange.conversations, 'conversations'); const knowledge = ensureArray(exchange.knowledge, 'knowledge'); const memory = ensureArray(exchange.memory, 'memory'); const relations = ensureArray(exchange.relations, 'relations');
  const ids = new Set(); for (const [name, items] of [['projects', projects], ['conversations', conversations], ['knowledge', knowledge], ['memory', memory], ['relations', relations]]) for (const item of items) { id(item.id, `${name}.id`); if (ids.has(item.id)) throw new Error('cross-owner stable ID collision'); ids.add(item.id); }
  for (const project of projects) { exact(project, ['id', 'title', 'description', 'appearance', 'pinned', 'archived', 'createdAt', 'updatedAt', 'schemaVersion', 'instructionHistory'], 'project'); exact(project.appearance, ['color', 'icon'], 'project.appearance'); const revisions = ensureArray(project.instructionHistory, 'project.instructionHistory'); unique(revisions, 'project.instructionHistory'); for (const revision of revisions) { exact(revision, ['id', 'revision', 'content', 'source', 'contentHash', 'createdAt', 'schemaVersion'], 'project.instruction'); if (revision.source !== 'USER' || sha256(utf8(revision.content)) !== revision.contentHash) throw new Error('invalid project instruction history'); } }
  for (const conversation of conversations) { exact(conversation, ['id', 'projectId', 'title', 'currentLeafId', 'pinned', 'archived', 'revision', 'createdAt', 'updatedAt', 'autoTitlePending', 'surface', 'schemaVersion', 'settings', 'messages'], 'conversation'); if (conversation.projectId !== null && !ids.has(conversation.projectId)) throw new Error('conversation project missing'); if (conversation.surface !== 'CHAT') throw new Error('WORK conversation is not portable'); const settings = conversation.settings; exact(settings, ['defaultProviderId', 'defaultModelId', 'harnessId', 'harnessVersion', 'contextPolicyVersion', 'memorySources', 'schemaVersion'], 'conversation.settings'); for (const source of ensureArray(settings.memorySources, 'conversation.settings.memorySources')) { exact(source, ['memoryId', 'sourceKind', 'sourceVersion'], 'memory source'); if (!memory.some(item => item.id === source.memoryId)) throw new Error('conversation memory source missing'); } if (!Array.isArray(conversation.messages) || conversation.messages.length === 0) throw new Error('conversation message tree missing'); }
  for (const item of knowledge) { exact(item, ['id', 'title', 'body', 'sourceEvidence', 'provenance', 'attachments', 'status', 'scope', 'projectId', 'tags', 'contentHash', 'createdAt', 'updatedAt', 'schemaVersion', 'history'], 'knowledge'); if (item.projectId !== null && !ids.has(item.projectId)) throw new Error('knowledge project missing'); hash(item.contentHash, 'knowledge.contentHash'); if (!Array.isArray(item.history) || !Array.isArray(item.sourceEvidence) || !Array.isArray(item.attachments)) throw new Error('knowledge owner history missing'); }
  for (const item of memory) { exact(item, ['id', 'title', 'body', 'scope', 'scopeId', 'source', 'sourceStableId', 'sourceSummary', 'status', 'contentHash', 'conceptHash', 'createdAt', 'updatedAt', 'lastConfirmedAt', 'deletedAt', 'schemaVersion', 'history'], 'memory'); if (item.scopeId !== null && !ids.has(item.scopeId)) throw new Error('memory scope missing'); hash(item.contentHash, 'memory.contentHash'); hash(item.conceptHash, 'memory.conceptHash'); if (!Array.isArray(item.history)) throw new Error('memory history missing'); }
  for (const item of relations) { exact(item, ['id', 'type', 'fromId', 'toId', 'scope', 'projectId', 'status', 'createdAt', 'updatedAt', 'createdByIntentId', 'latestIntentId', 'suggestionSource', 'history'], 'relation'); if (!knowledge.some(value => value.id === item.fromId) || !knowledge.some(value => value.id === item.toId)) throw new Error('relation endpoint missing'); if (item.projectId !== null && !projects.some(value => value.id === item.projectId)) throw new Error('relation project missing'); if (!Array.isArray(item.history)) throw new Error('relation history missing'); }
  exact(exchange.settings, ['uiLanguage', 'theme'], 'settings');
  return { semanticHash: exchange.export.semanticHash, hasHighSensitiveData: exchange.export.sensitivity === 'HIGH_SENSITIVE' || JSON.stringify(exchange).includes('HIGH_SENSITIVE') };
}
