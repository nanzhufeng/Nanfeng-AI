import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { packageExchange, preflightPackage, semanticHash, sha256 } from './exchange-lib.mjs';

const root = resolve(import.meta.dirname, '..');
const outputPath = resolve(root, 'artifacts/p6d-isolated-long-scroll-fixture.nfai-exchange');
const sentence = '这是 P6-D 紧凑态长列表滚动验证的合成非敏感文本；仅用于本机隔离工作区的可见性核验，不是用户数据、Provider 输出或联网内容。';
const messages = Array.from({ length: 64 }, (_, index) => ({
  id: `p6d-scroll-message-${String(index + 1).padStart(2, '0')}`,
  parentId: index === 0 ? null : `p6d-scroll-message-${String(index).padStart(2, '0')}`,
  ordinal: 0,
  role: index % 2 === 0 ? 'user' : 'assistant',
  delivery: 'COMPLETE',
  revision: 1,
  createdAt: `2026-08-13T00:${String(index).padStart(2, '0')}:00Z`,
  blocks: [{ kind: 'TEXT', ordinal: 0, text: `第 ${index + 1} 条。${sentence}\n${sentence}` }],
}));
const exchange = {
  format: 'nfai.exchange',
  version: 1,
  export: {
    id: 'p6d-scroll-export-01',
    createdAt: '2026-08-13T00:00:00Z',
    origin: { platform: 'DESKTOP', appVersion: '0.6.0-p6d-dev' },
    semanticHash: '',
    sensitivity: 'NORMAL',
  },
  projects: [],
  conversations: [{
    id: 'p6d-scroll-conversation-01',
    projectId: null,
    title: 'P6-D 隔离长列表验证会话',
    currentLeafId: messages.at(-1).id,
    pinned: false,
    archived: false,
    revision: 1,
    createdAt: '2026-08-13T00:00:00Z',
    updatedAt: '2026-08-13T01:03:00Z',
    messages,
  }],
  knowledge: [],
  memory: [],
  relations: [],
  settings: { uiLanguage: 'zh-CN', theme: 'SYSTEM' },
};
exchange.export.semanticHash = semanticHash(exchange);
const bytes = packageExchange(exchange);
const preflight = preflightPackage(bytes);
await mkdir(resolve(root, 'artifacts'), { recursive: true });
await writeFile(outputPath, bytes);
console.log(JSON.stringify({
  outputPath,
  semanticHash: preflight.semanticHash,
  packageHash: sha256(bytes),
  byteCount: bytes.length,
  entryCount: preflight.entryCount,
  messageCount: messages.length,
  hasHighSensitiveData: preflight.hasHighSensitiveData,
}));
