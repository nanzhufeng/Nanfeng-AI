import { cp, mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
const root = resolve(import.meta.dirname, '..'); const dist = resolve(root, 'dist'); await mkdir(dist, { recursive: true });
for (const file of ['index.html', 'styles.css', 'chat-shell.css', 'image-preview.css', 'p8-inspect.css', 'app.mjs', 'chat-shell.mjs', 'icon-source.mjs', 'p8-inspect.mjs', 'pwa-boundaries.mjs', 'recycle-confirmation.mjs', 'desktop-compare-execution-owner.mjs']) await cp(resolve(root, 'src', file), resolve(dist, file));
await cp(resolve(root, '../app/src/main/res/drawable-nodpi/nanfeng_ai_icon_foreground_image.png'), resolve(dist, 'nanfeng-ai-icon.png'));
const report = JSON.parse((await readFile(resolve(root, '../protocol/fixtures/nfai.exchange.v1.golden.json'), 'utf8'))); await writeFile(resolve(dist, 'fixture-summary.json'), JSON.stringify({ format: report.format, version: report.version, semanticHash: report.export.semanticHash }));
console.log(`desktop static spike built: ${dist}`);
