import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import http from 'node:http';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv.slice(2).find(arg => !arg.startsWith('--')) || 'D:\\CodexTemp');
const pdfMode = process.argv.includes('--pdf-preview');
const docxMode = process.argv.includes('--docx-preview');
const zipMode = process.argv.includes('--zip-preview');
const xlsxMode = process.argv.includes('--xlsx-preview');
const pptxMode = process.argv.includes('--pptx-preview');
assert.ok(Number(pdfMode) + Number(docxMode) + Number(zipMode) + Number(xlsxMode) + Number(pptxMode) <= 1, '文件模式不能同时启用');
const docxText = 'W08 synthetic DOCX body';
const archiveText = 'W09A synthetic archive entry';
const xlsxText = 'W09 synthetic XLSX cell';
const pptxText = 'W09 synthetic PPTX slide';
function twoPagePdf() {
  const first = 'BT /F1 16 Tf 72 720 Td (W08 synthetic PDF page one) Tj ET';
  const second = 'BT /F1 16 Tf 72 720 Td (W08 synthetic PDF page two) Tj ET';
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 7 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 7 0 R >> >> /Contents 6 0 R >>',
    `<< /Length ${Buffer.byteLength(first)} >>\nstream\n${first}\nendstream`,
    `<< /Length ${Buffer.byteLength(second)} >>\nstream\n${second}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let body = '%PDF-1.4\n';
  const offsets = [];
  for (const [index, object] of objects.entries()) {
    offsets.push(Buffer.byteLength(body));
    body += `${index + 1} 0 obj\n${object}\nendobj\n`;
  }
  const xref = Buffer.byteLength(body);
  body += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (const offset of offsets) body += `${String(offset).padStart(10, '0')} 00000 n \n`;
  body += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(body);
}
let materialBytes = pdfMode ? twoPagePdf() : Buffer.from('W08 synthetic system picker\n');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runningExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const reservePort = async () => {
  const server = (await import('node:http')).createServer();
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const port = server.address().port;
  await new Promise(done => server.close(done));
  return port;
};
const requests = [];
const provider = http.createServer((request, response) => {
  const chunks = [];
  request.on('data', chunk => chunks.push(chunk));
  request.on('end', () => {
    try {
      const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      const messages = JSON.stringify(body.messages);
      requests.push({ model: body.model, materialPresent: messages.includes(pdfMode ? materialBytes.toString('base64') : docxMode ? docxText : xlsxMode ? xlsxText : pptxMode ? pptxText : 'W08 synthetic system picker'),
        rawOfficePresent: (docxMode || xlsxMode || pptxMode) && messages.includes(materialBytes.toString('base64')),
        idempotencyKey: request.headers['idempotency-key'] || '' });
      response.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8', 'cache-control': 'no-cache' });
      response.write(`data: ${JSON.stringify({ model: body.model, choices: [{ delta: { content: '合成选择器回答' } }] })}\n\n`);
      response.write(`data: ${JSON.stringify({ choices: [{ delta: {}, finish_reason: 'stop' }], usage: { prompt_tokens: 9, completion_tokens: 4, cost: 0.000005 } })}\n\n`);
      response.end('data: [DONE]\n\n');
    } catch (error) { response.writeHead(400); response.end(String(error)); }
  });
});
await new Promise(done => provider.listen(0, '127.0.0.1', done));
const providerPort = provider.address().port;
let launch = null;
let root = '';
const pids = [];
const startProcess = args => new Promise((done, fail) => {
  const child = spawn('pwsh', ['-NoProfile', '-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), ...args], {
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let out = '';
  let err = '';
  const timer = setTimeout(() => { child.kill(); fail(new Error(`启动超时：${err}`)); }, 30000);
  child.stdout.on('data', chunk => {
    out += chunk.toString();
    const line = out.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
    if (!line) return;
    clearTimeout(timer);
    try { done(JSON.parse(line)); } catch (error) { fail(error); }
    child.stdout.destroy();
    child.stderr.destroy();
    child.unref();
  });
  child.stderr.on('data', chunk => { err += chunk.toString(); });
  child.on('error', error => { clearTimeout(timer); fail(error); });
  child.on('exit', code => { if (!out.includes('"pid"')) { clearTimeout(timer); fail(new Error(`启动失败 ${code}：${err}`)); } });
});
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-RootParent', rootParent, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port),
    '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
  if (reopen) args.push('-Root', root);
  launch = await startProcess(args);
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), runningExecutable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(rootParent + sep));
  const marker = JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8'));
  assert.equal(marker.identifier, receipt.identifier);
  return port;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
  pids.push(pid);
  launch = null;
}
async function evaluate(port, expression) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((done, fail) => {
      socket.addEventListener('open', done, { once: true });
      socket.addEventListener('error', fail, { once: true });
    });
    return await new Promise((done, fail) => {
      const timer = setTimeout(() => fail(new Error('CDP evaluation timed out')), 30000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) fail(new Error(message.result?.exceptionDetails?.text || message.error?.message));
        else done(message.result?.result?.value);
      });
      socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: {
        expression, awaitPromise: true, returnByValue: true,
      } }));
    });
  } finally { socket.close(); }
}
async function invoke(port, area, command, args = {}) {
  const value = await evaluate(port, `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`);
  if (value?.__error) throw new Error(`${command}: ${value.__error}`);
  return value;
}
async function until(read, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch { /* Startup or redraw in progress. */ }
    await new Promise(done => setTimeout(done, 200));
  }
  throw new Error(`${label} 未在原生界面出现`);
}
const click = (port, selector) => evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
const hasSelector = (port, selector) => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(selector)}))`);

let result;
try {
  let port = await start();
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC');
  const workspaceId = (await invoke(port, 'CHAT', 'ensure_desktop_local_workspace')).summary.id;
  await until(() => evaluate(port, 'Boolean(document.querySelector("#chat-composer"))'), 'composer');
  assert.equal(await click(port, '[data-action="new-chat"]'), true);
  const fixtureDir = join(root, 'synthetic-picker-inputs');
  await mkdir(fixtureDir, { recursive: true });
  const selectedPath = join(fixtureDir, pdfMode ? 'picked.pdf' : docxMode ? 'picked.docx' : zipMode ? 'picked.zip' : xlsxMode ? 'picked.xlsx' : pptxMode ? 'picked.pptx' : 'picked.txt');
  if (docxMode) {
    const docxDir = join(fixtureDir, 'docx-package');
    await mkdir(join(docxDir, '_rels'), { recursive: true });
    await mkdir(join(docxDir, 'word'), { recursive: true });
    await writeFile(join(docxDir, '[Content_Types].xml'), '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>');
    await writeFile(join(docxDir, '_rels', '.rels'), '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>');
    await writeFile(join(docxDir, 'word', 'document.xml'), `<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>${docxText}</w:t></w:r></w:p></w:body></w:document>`);
    ps(`Add-Type -AssemblyName System.IO.Compression.FileSystem; [IO.Compression.ZipFile]::CreateFromDirectory(${quote(docxDir)}, ${quote(selectedPath)})`);
    materialBytes = await readFile(selectedPath);
  } else if (zipMode) {
    const archiveDir = join(fixtureDir, 'zip-package');
    await mkdir(join(archiveDir, 'folder'), { recursive: true });
    await writeFile(join(archiveDir, 'folder', 'readme.txt'), `${archiveText}\n`);
    await writeFile(join(archiveDir, 'folder', 'manual.pdf'), twoPagePdf());
    ps(`Add-Type -AssemblyName System.IO.Compression.FileSystem; [IO.Compression.ZipFile]::CreateFromDirectory(${quote(archiveDir)}, ${quote(selectedPath)})`);
    materialBytes = await readFile(selectedPath);
  } else if (xlsxMode) {
    const workbookDir = join(fixtureDir, 'xlsx-package');
    await mkdir(join(workbookDir, '_rels'), { recursive: true });
    await mkdir(join(workbookDir, 'xl', '_rels'), { recursive: true });
    await mkdir(join(workbookDir, 'xl', 'worksheets'), { recursive: true });
    await writeFile(join(workbookDir, '[Content_Types].xml'), '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/></Types>');
    await writeFile(join(workbookDir, '_rels', '.rels'), '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>');
    await writeFile(join(workbookDir, 'xl', 'workbook.xml'), '<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>');
    await writeFile(join(workbookDir, 'xl', '_rels', 'workbook.xml.rels'), '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/></Relationships>');
    await writeFile(join(workbookDir, 'xl', 'sharedStrings.xml'), `<?xml version="1.0" encoding="UTF-8"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="1" uniqueCount="1"><si><t>${xlsxText}</t></si></sst>`);
    await writeFile(join(workbookDir, 'xl', 'worksheets', 'sheet1.xml'), '<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1"><v>42</v></c></row></sheetData></worksheet>');
    ps(`Add-Type -AssemblyName System.IO.Compression.FileSystem; [IO.Compression.ZipFile]::CreateFromDirectory(${quote(workbookDir)}, ${quote(selectedPath)})`);
    materialBytes = await readFile(selectedPath);
  } else if (pptxMode) {
    const presentationDir = join(fixtureDir, 'pptx-package');
    await mkdir(join(presentationDir, '_rels'), { recursive: true });
    await mkdir(join(presentationDir, 'ppt', '_rels'), { recursive: true });
    await mkdir(join(presentationDir, 'ppt', 'slides'), { recursive: true });
    await writeFile(join(presentationDir, '[Content_Types].xml'), '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/><Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/></Types>');
    await writeFile(join(presentationDir, '_rels', '.rels'), '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/></Relationships>');
    await writeFile(join(presentationDir, 'ppt', 'presentation.xml'), '<?xml version="1.0" encoding="UTF-8"?><p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><p:sldIdLst><p:sldId id="256" r:id="rId1"/></p:sldIdLst></p:presentation>');
    await writeFile(join(presentationDir, 'ppt', '_rels', 'presentation.xml.rels'), '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/></Relationships>');
    await writeFile(join(presentationDir, 'ppt', 'slides', 'slide1.xml'), `<?xml version="1.0" encoding="UTF-8"?><p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>${pptxText}</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>`);
    ps(`Add-Type -AssemblyName System.IO.Compression.FileSystem; [IO.Compression.ZipFile]::CreateFromDirectory(${quote(presentationDir)}, ${quote(selectedPath)})`);
    materialBytes = await readFile(selectedPath);
  } else await writeFile(selectedPath, materialBytes);
  const dialogOpen = () => {
    const raw = ps(`& ${quote(join(desktopRoot, 'scripts/inspect-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)}`);
    const windows = raw ? JSON.parse(raw) : [];
    return windows.some(item => item.children?.some(child => child.class === '#32770'));
  };
  const openPicker = async () => {
    if (!await evaluate(port, 'Boolean(document.querySelector("[data-action=pick-composer-file]"))')) {
      assert.equal(await click(port, '[data-action="toggle-composer-add"]'), true);
    }
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=pick-composer-file]"))'), 'file picker action');
    assert.equal(await click(port, '[data-action="pick-composer-file"]'), true);
    await until(dialogOpen, 'system open dialog');
  };
  const operate = (action, path = '') => {
    const command = `& ${quote(join(desktopRoot, 'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action ${quote(action)}`
      + (path ? ` -SelectedPath ${quote(path)} -AcceptanceRoot ${quote(root)}` : '');
    return JSON.parse(ps(command));
  };
  await openPicker();
  assert.equal(operate('cancel').action, 'cancel');
  await until(() => !dialogOpen(), 'cancel closes system dialog');
  assert.equal(await evaluate(port, 'document.querySelectorAll(".chat-composer-attachments [data-action=remove-composer-attachment]").length'), 0);
  const cancelledRoute = await evaluate(port, 'document.querySelector(".chat-scroll")?.dataset.conversationId || null');
  assert.ok(!(await invoke(port, 'CHAT', 'read_desktop_conversation_draft', { workspaceId, conversationId: cancelledRoute }))?.attachments?.length);
  await openPicker();
  assert.equal(operate('select', selectedPath).action, 'select');
  await until(() => !dialogOpen(), 'selection closes system dialog');
  await until(() => evaluate(port, 'document.querySelectorAll(".chat-composer-attachments [data-action=remove-composer-attachment]").length === 1'), 'selected file card');
  const draftConversationId = await evaluate(port, 'document.querySelector(".chat-scroll")?.dataset.conversationId || null');
  const draft = await until(async () => {
    const value = await invoke(port, 'CHAT', 'read_desktop_conversation_draft', { workspaceId, conversationId: draftConversationId });
    return value?.attachments?.length === 1 ? value : null;
  }, 'selected file private draft');
  assert.equal(draft.attachments[0].displayName, pdfMode ? 'picked.pdf' : docxMode ? 'picked.docx' : zipMode ? 'picked.zip' : xlsxMode ? 'picked.xlsx' : pptxMode ? 'picked.pptx' : 'picked.txt');
  assert.equal(draft.attachments[0].sha256, createHash('sha256').update(materialBytes).digest('hex'));
  assert.equal(JSON.stringify(draft).includes(selectedPath), false);
  stop();
  port = await start(true);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), 'cold native IPC');
  const cold = await invoke(port, 'CHAT', 'read_desktop_conversation_draft', { workspaceId, conversationId: draftConversationId });
  assert.equal(cold?.attachments?.[0]?.sha256, draft.attachments[0].sha256);
  if (draftConversationId) {
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="select-chat"][data-id="${draftConversationId}"]'))`), 'cold draft conversation');
    assert.equal(await click(port, `[data-action="select-chat"][data-id="${draftConversationId}"]`), true);
  }
  await until(() => evaluate(port, 'document.querySelectorAll(".chat-composer-attachments [data-action=remove-composer-attachment]").length === 1'), 'cold picker attachment card');
  if (pdfMode) {
    const previewButton = `[data-action="open-pdf-preview"][data-attachment-id="${draft.attachments[0].id}"]`;
    await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(previewButton)}))`), 'PDF preview entry');
    assert.equal(await click(port, previewButton), true);
    await until(() => evaluate(port, 'document.querySelector(".pdf-preview-dialog .pdf-preview-frame")?.getAttribute("src")?.startsWith("data:image/png;base64,")'), 'PDF rendered page');
    assert.equal(await evaluate(port, 'document.querySelector(".pdf-preview-dialog .pdf-preview-tools")?.textContent.includes("第 1 / 2 页")'), true);
    const firstPage = await evaluate(port, 'document.querySelector(".pdf-preview-dialog .pdf-preview-frame")?.getAttribute("src")');
    assert.equal(await click(port, '[data-action="pdf-page-next"]'), true);
    await until(() => evaluate(port, 'document.querySelector(".pdf-preview-dialog .pdf-preview-tools")?.textContent.includes("第 2 / 2 页")'), 'PDF second page');
    const secondPage = await evaluate(port, 'document.querySelector(".pdf-preview-dialog .pdf-preview-frame")?.getAttribute("src")');
    assert.notEqual(secondPage, firstPage);
    assert.equal(await click(port, '[data-action="pdf-page-previous"]'), true);
    await until(() => evaluate(port, 'document.querySelector(".pdf-preview-dialog .pdf-preview-tools")?.textContent.includes("第 1 / 2 页")'), 'PDF previous page');
    assert.equal(await click(port, '[data-action="close-pdf-preview"]'), true);
    await until(() => evaluate(port, '!document.querySelector(".pdf-preview-dialog")'), 'PDF preview closed');
  }
  if (docxMode) {
    const previewButton = `[data-action="open-text-preview"][data-attachment-id="${draft.attachments[0].id}"]`;
    await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(previewButton)}))`), 'DOCX preview entry');
    assert.equal(await click(port, previewButton), true);
    await until(() => evaluate(port, `document.querySelector('.text-preview-surface .local-text-preview')?.textContent.includes(${JSON.stringify(docxText)})`), 'DOCX extracted text');
    assert.equal(await click(port, '[data-action="close-text-preview"]'), true);
    await until(() => evaluate(port, '!document.querySelector(".text-preview-surface")'), 'DOCX preview closed');
  }
  if (xlsxMode) {
    const previewButton = `[data-action="open-text-preview"][data-attachment-id="${draft.attachments[0].id}"]`;
    await until(() => hasSelector(port, previewButton), 'XLSX preview entry');
    assert.equal(await click(port, previewButton), true);
    await until(() => evaluate(port, `document.querySelector('.text-preview-surface .local-text-preview')?.textContent.includes(${JSON.stringify(xlsxText)})`), 'XLSX local text');
    assert.equal(await evaluate(port, 'document.querySelector(".text-preview-surface .local-text-preview")?.textContent.includes("42")'), true);
    assert.equal(await click(port, '[data-action="close-text-preview"]'), true);
    await until(() => evaluate(port, '!document.querySelector(".text-preview-surface")'), 'XLSX preview closed');
  }
  if (pptxMode) {
    const previewButton = `[data-action="open-text-preview"][data-attachment-id="${draft.attachments[0].id}"]`;
    await until(() => hasSelector(port, previewButton), 'PPTX preview entry');
    assert.equal(await click(port, previewButton), true);
    await until(() => evaluate(port, `document.querySelector('.text-preview-surface .local-text-preview')?.textContent.includes(${JSON.stringify(pptxText)})`), 'PPTX local text');
    assert.equal(await evaluate(port, 'document.querySelector(".text-preview-surface .local-text-preview")?.textContent.includes("第 1 页")'), true);
    assert.equal(await click(port, '[data-action="close-text-preview"]'), true);
    await until(() => evaluate(port, '!document.querySelector(".text-preview-surface")'), 'PPTX preview closed');
  }
  if (zipMode) {
    const previewButton = `[data-action="open-archive-preview"][data-attachment-id="${draft.attachments[0].id}"]`;
    await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(previewButton)}))`), 'ZIP preview entry');
    assert.equal(await click(port, previewButton), true);
    await until(() => hasSelector(port, '.archive-preview-surface .archive-directory-row[data-archive-path="folder/"]'), 'ZIP directory');
    assert.equal(await click(port, '[data-action="open-archive-directory"][data-archive-path="folder/"]'), true);
    await until(() => hasSelector(port, '.archive-preview-surface [data-archive-entry-path="folder/readme.txt"]'), 'ZIP child entry');
    assert.equal(await click(port, '[data-action="open-archive-entry-preview"][data-archive-entry-path="folder/readme.txt"]'), true);
    await until(() => evaluate(port, `document.querySelector('.archive-text-preview')?.textContent.includes(${JSON.stringify(archiveText)})`), 'ZIP entry content');
    assert.equal(await click(port, '[data-action="archive-preview-back"]'), true);
    await until(() => hasSelector(port, '.archive-preview-surface [data-archive-entry-path="folder/readme.txt"]'), 'ZIP directory return');
    assert.equal(await click(port, '[data-action="open-archive-entry-preview"][data-archive-entry-path="folder/manual.pdf"]'), true);
    await until(() => evaluate(port, 'document.querySelector(".archive-preview-surface .pdf-preview-frame")?.getAttribute("src")?.startsWith("data:image/png;base64,")'), 'ZIP PDF first page');
    assert.equal(await evaluate(port, 'document.querySelector(".archive-preview-surface .pdf-preview-tools")?.textContent.includes("第 1 / 2 页")'), true);
    const firstZipPdfPage = await evaluate(port, 'document.querySelector(".archive-preview-surface .pdf-preview-frame")?.getAttribute("src")');
    assert.equal(await click(port, '[data-action="archive-pdf-page-next"]'), true);
    await until(() => evaluate(port, 'document.querySelector(".archive-preview-surface .pdf-preview-tools")?.textContent.includes("第 2 / 2 页")'), 'ZIP PDF second page');
    const secondZipPdfPage = await evaluate(port, 'document.querySelector(".archive-preview-surface .pdf-preview-frame")?.getAttribute("src")');
    assert.notEqual(secondZipPdfPage, firstZipPdfPage);
    assert.equal(await click(port, '[data-action="archive-pdf-page-previous"]'), true);
    await until(() => evaluate(port, 'document.querySelector(".archive-preview-surface .pdf-preview-tools")?.textContent.includes("第 1 / 2 页")'), 'ZIP PDF previous page');
    assert.equal(await click(port, '[data-action="archive-preview-back"]'), true);
    await until(() => hasSelector(port, '.archive-preview-surface [data-archive-entry-path="folder/manual.pdf"]'), 'ZIP PDF directory return');
    assert.equal(await click(port, '[data-action="open-archive-directory"][data-archive-path=""]'), true);
    await until(() => hasSelector(port, '.archive-preview-surface .archive-directory-row[data-archive-path="folder/"]'), 'ZIP root return');
    assert.equal(await click(port, '[data-action="close-archive-preview"]'), true);
    await until(() => evaluate(port, '!document.querySelector(".archive-preview-surface")'), 'ZIP preview closed');
    result = { candidateSha256: receipt.sha256, root, workspaceId, attachmentId: draft.attachments[0].id,
      kind: 'ZIP', cancelledWithoutDraft: true, selectedPrivateCopy: true, coldReadback: true,
      directoryEntryReturnClose: true, pdfPageFlip: true, pids };
  }
  if (!zipMode) {
  assert.equal(await click(port, '[data-action="toggle-composer-add"]'), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=toggle-conversation-web-search]"))'), 'web search preference');
  const checked = await evaluate(port, 'document.querySelector("[data-action=toggle-conversation-web-search]")?.getAttribute("aria-checked")');
  assert.ok(checked === 'true' || checked === 'false');
  if (checked === 'true') {
    assert.equal(await click(port, '[data-action="toggle-conversation-web-search"]'), true);
    await until(() => evaluate(port, 'document.querySelector("[data-action=toggle-conversation-web-search]")?.getAttribute("aria-checked") === "false"'), 'search explicitly disabled');
  }
  assert.equal(await click(port, '[data-action="close-composer-add"]'), true);
  if (checked === 'true') {
    await until(async () => {
      const route = { workspaceId, conversationId: draftConversationId };
      const value = draftConversationId
        ? (await invoke(port, 'CHAT', 'read_desktop_p6g_selection', route)).conversationOverride
        : await invoke(port, 'CHAT', 'read_desktop_conversation_draft', route);
      return value?.webSearchOverride === false;
    }, 'search selection persisted in its owner');
  }
  const prompt = 'W08 选择器合成发送';
  await evaluate(port, `(()=>{const field=document.querySelector('#chat-composer');field.value=${JSON.stringify(prompt)};field.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'), 'send button');
  assert.equal(await click(port, '[data-action="save-local-message"]'), true);
  const conversation = await until(async () => {
    const current = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId });
    return current.exchange.conversations.find(item => item.messages?.some(message =>
      message.role === 'user' && message.blocks?.some(block => block.text === prompt)) &&
      item.messages?.find(message => message.id === item.currentLeafId)?.delivery === 'COMPLETE') || null;
  }, 'picker attachment answer');
  assert.equal(requests.length, 1);
  assert.ok(requests[0].materialPresent && requests[0].idempotencyKey);
  if (docxMode || xlsxMode || pptxMode) assert.equal(requests[0].rawOfficePresent, false);
  const user = conversation.messages.find(message => message.role === 'user' && message.blocks?.some(block => block.text === prompt));
  assert.ok(user.blocks.some(block => block.kind === 'ASSET_REF' && block.asset?.id === draft.attachments[0].id));
  if (docxMode || xlsxMode || pptxMode) {
    const hits = await until(async () => {
      const items = await invoke(port, 'CHAT', 'search_desktop_local_index', {
        workspaceId, query: draft.attachments[0].displayName,
      });
      return items.some(item => item.attachmentId === draft.attachments[0].id) ? items : null;
    }, 'Office search attachment hit');
    assert.ok(hits.some(item => item.attachmentId === draft.attachments[0].id));
    const preview = await invoke(port, 'CHAT', 'read_desktop_search_attachment_preview', {
      args: { workspaceId, attachmentId: draft.attachments[0].id },
    });
    assert.ok(preview?.text?.includes(docxMode ? docxText : xlsxMode ? xlsxText : pptxText));
    assert.ok([...preview.text].length <= 640);
  }
  await until(() => evaluate(port, 'document.querySelector(".chat-scroll")?.textContent.includes("合成选择器回答")'), 'visible picker attachment answer');
  if (docxMode || xlsxMode || pptxMode) {
    const fileName = draft.attachments[0].displayName;
    const fileCard = `.desktop-search-file-card[data-search-attachment-preview="${draft.attachments[0].id}"]`;
    const expectedFormat = docxMode ? 'DOCX' : xlsxMode ? 'XLSX' : 'PPTX';
    const expectedText = docxMode ? docxText : xlsxMode ? xlsxText : pptxText;
    assert.equal(await evaluate(port, '(()=>{const input=document.querySelector("#chat-search");if(!input)return false;input.focus();return true})()'), true);
    await until(() => hasSelector(port, '#full-search-input'), 'full search input');
    assert.equal(await evaluate(port, `(()=>{const input=document.querySelector('#full-search-input');input.value=${JSON.stringify(fileName)};input.dispatchEvent(new Event('input',{bubbles:true}));return true})()`), true);
    await until(() => evaluate(port, `document.querySelector(${JSON.stringify(fileCard)})?.querySelector('.desktop-search-text-file-preview pre')?.textContent.includes(${JSON.stringify(expectedText)})`), 'visible Office search card text');
    assert.equal(await evaluate(port, `document.querySelector(${JSON.stringify(fileCard)})?.querySelector('.desktop-search-text-file-preview b')?.textContent`), expectedFormat);
    assert.equal(await click(port, `${fileCard} [data-action="open-search-attachment"]`), true);
    await until(() => evaluate(port, `document.querySelector('.text-preview-surface .local-text-preview')?.textContent.includes(${JSON.stringify(expectedText)})`), 'Office search result preview');
    assert.equal(await click(port, '[data-action="close-text-preview"]'), true);
    await until(() => evaluate(port, '!document.querySelector(".text-preview-surface")'), 'Office search result preview closed');
  }
  assert.ok(!(await invoke(port, 'CHAT', 'read_desktop_conversation_draft', { workspaceId, conversationId: draftConversationId }))?.attachments?.length);
  const ledger = await invoke(port, 'CHAT', 'read_desktop_usage_ledger');
  assert.ok(ledger.records.some(item => item.conversationId === conversation.id && item.chargeMicros === 5));
  stop();
  port = await start(true);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), 'answer cold native IPC');
  const afterRestart = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId });
  const saved = afterRestart.exchange.conversations.find(item => item.id === conversation.id);
  assert.ok(saved?.messages.some(message => message.id === user.id && message.blocks?.some(block => block.asset?.id === draft.attachments[0].id)));
  assert.equal(requests.length, 1);
  result = { candidateSha256: receipt.sha256, root, workspaceId, attachmentId: draft.attachments[0].id,
    cancelledWithoutDraft: true, selectedPrivateCopy: true, coldReadback: true,
    kind: pdfMode ? 'PDF' : docxMode ? 'DOCX' : xlsxMode ? 'XLSX' : pptxMode ? 'PPTX' : 'TXT', renderedPdfPage: pdfMode, extractedDocxText: docxMode, extractedXlsxText: xlsxMode, extractedPptxText: pptxMode,
    visibleSend: { conversationId: conversation.id, requests: requests.length, chargeMicros: 5, coldReadback: true }, pids };
  }
} finally {
  stop();
  await new Promise(done => provider.close(done));
}
console.log(JSON.stringify(result));
