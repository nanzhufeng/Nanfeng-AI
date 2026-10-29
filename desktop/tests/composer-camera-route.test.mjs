import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const routes = source.slice(source.indexOf('function captureComposerAttachmentRoute('), source.indexOf('async function importPastedComposerClipboardFiles('));
const camera = source.slice(source.indexOf('let composerCameraStream = null;'), source.indexOf('async function pickComposerAttachment('));
const area = name => ({ dataArea: name, current: { summary: { id: 'same-workspace' } },
  selectedConversationId: 'same-conversation', composerAttachments: [], composerDraft: `${name} draft`, temporaryConversation: null });

test('closing while camera permission is pending stops a late stream', async () => {
  let releasePermission, stopped = false;
  const chat = area('CHAT');
  const context = vm.createContext({ state: chat, native: true, composerPresentationIdentity: {},
    navigator: { mediaDevices: { getUserMedia: () => new Promise(resolve => { releasePermission = resolve; }) } },
    render() {}, queueMicrotask() {}, document: { querySelector: () => null },
  });
  vm.runInContext(`${routes}\n${camera}`, context);
  const opening = vm.runInContext('openComposerCamera()', context);
  vm.runInContext('stopComposerCamera()', context);
  releasePermission({ getTracks: () => [{ stop() { stopped = true; } }] });
  await opening;
  assert.equal(stopped, true);
  assert.equal(chat.cameraCaptureOpen, false);
  assert.equal(chat.cameraCaptureReady, false);
});

test('a late camera import never joins the same-ID conversation in WORK', async () => {
  let releaseImport;
  const chat = area('CHAT'), work = area('WORK');
  const stream = { getTracks: () => [{ stop() {} }] };
  const video = { videoWidth: 8, videoHeight: 8, srcObject: null, play: async () => {} };
  const context = vm.createContext({ state: chat, native: true, composerPresentationIdentity: {},
    navigator: { mediaDevices: { getUserMedia: async () => stream } },
    document: { querySelector: () => video, createElement: () => ({
      getContext: () => ({ drawImage() {} }), toDataURL: () => 'data:image/png;base64,synthetic',
    }) },
    invoke: () => new Promise(resolve => { releaseImport = resolve; }),
    render() {}, queueMicrotask() {}, writeComposerAttachments() {},
  });
  vm.runInContext(`${routes}\n${camera}`, context);
  await vm.runInContext('openComposerCamera()', context);
  vm.runInContext('attachComposerCameraStream()', context);
  const capturing = vm.runInContext('captureComposerCamera()', context);
  context.state = work;
  releaseImport({ id: 'old-camera-asset', sha256: 'old-hash' });
  await capturing;
  assert.equal(work.composerAttachments.length, 0);
  assert.equal(work.status, undefined);
});

test('an old capture receipt cannot close a newly opened WORK camera', async () => {
  let releaseImport, workStopped = false;
  const chat = area('CHAT'), work = area('WORK');
  const chatStream = { getTracks: () => [{ stop() {} }] };
  const workStream = { getTracks: () => [{ stop() { workStopped = true; } }] };
  let nextStream = chatStream;
  const video = { videoWidth: 8, videoHeight: 8, srcObject: null, play: async () => {} };
  const context = vm.createContext({ state: chat, native: true, composerPresentationIdentity: {},
    navigator: { mediaDevices: { getUserMedia: async () => nextStream } },
    document: { querySelector: () => video, createElement: () => ({
      getContext: () => ({ drawImage() {} }), toDataURL: () => 'data:image/png;base64,synthetic',
    }) },
    invoke: () => new Promise(resolve => { releaseImport = resolve; }),
    render() {}, queueMicrotask() {}, writeComposerAttachments() {},
  });
  vm.runInContext(`${routes}\n${camera}`, context);
  await vm.runInContext('openComposerCamera()', context);
  vm.runInContext('attachComposerCameraStream()', context);
  const oldCapture = vm.runInContext('captureComposerCamera()', context);
  vm.runInContext('stopComposerCamera()', context);
  context.state = work;
  nextStream = workStream;
  await vm.runInContext('openComposerCamera()', context);
  vm.runInContext('attachComposerCameraStream()', context);
  releaseImport({ id: 'old-camera-asset', sha256: 'old-hash' });
  await oldCapture;
  assert.equal(work.cameraCaptureOpen, true);
  assert.equal(workStopped, false);
});

test('the current conversation still receives one private photo and releases the camera', async () => {
  let stopped = false, mediaRequest;
  const chat = area('CHAT');
  const stream = { getTracks: () => [{ stop() { stopped = true; } }] };
  const video = { videoWidth: 8, videoHeight: 8, srcObject: null, play: async () => {} };
  const context = vm.createContext({ state: chat, native: true, composerPresentationIdentity: {},
    navigator: { mediaDevices: { getUserMedia: async request => { mediaRequest = request; return stream; } } },
    document: { querySelector: () => video, createElement: () => ({
      getContext: () => ({ drawImage() {} }), toDataURL: () => 'data:image/png;base64,synthetic',
    }) },
    invoke: async () => ({ id: 'photo', sha256: 'photo-hash' }),
    render() {}, queueMicrotask() {}, writeComposerAttachments() {},
  });
  vm.runInContext(`${routes}\n${camera}`, context);
  await vm.runInContext('openComposerCamera()', context);
  vm.runInContext('attachComposerCameraStream()', context);
  await vm.runInContext('captureComposerCamera()', context);
  assert.equal(mediaRequest.audio, false);
  assert.equal(chat.composerAttachments[0].id, 'photo');
  assert.equal(chat.cameraCaptureOpen, false);
  assert.equal(stopped, true);
});

test('camera capture stays disabled until the current video has a real frame', async () => {
  const chat = area('CHAT');
  const stream = { getTracks: () => [{ stop() {} }] };
  const listeners = new Map();
  let pendingVisible = true;
  const pending = { remove() { pendingVisible = false; } };
  const video = { videoWidth: 0, videoHeight: 0, srcObject: null, play: async () => {},
    closest: () => ({ querySelector: () => pending }),
    addEventListener: (name, callback) => listeners.set(name, callback) };
  const button = { disabled: true };
  const context = vm.createContext({ state: chat, native: true, composerPresentationIdentity: {},
    navigator: { mediaDevices: { getUserMedia: async () => stream } },
    document: { querySelector: selector => selector.includes('capture-composer-camera') ? button : video },
    render() {}, queueMicrotask() {},
  });
  vm.runInContext(`${routes}\n${camera}`, context);
  await vm.runInContext('openComposerCamera()', context);
  vm.runInContext('attachComposerCameraStream()', context);
  assert.equal(chat.cameraCaptureReady, false);
  assert.equal(button.disabled, true);
  assert.equal(pendingVisible, true);
  video.videoWidth = 640;
  video.videoHeight = 480;
  listeners.get('loadedmetadata')();
  assert.equal(chat.cameraCaptureReady, true);
  assert.equal(button.disabled, false);
  assert.equal(pendingVisible, false, 'a ready live video no longer reports a pending request');
  assert.equal(video.srcObject, stream, 'readiness must retain the existing video stream');
  vm.runInContext('stopComposerCamera()', context);
  pendingVisible = true;
  listeners.get('resize')();
  assert.equal(chat.cameraCaptureReady, false);
  assert.equal(pendingVisible, true, 'late callbacks cannot clear a new camera request');
});

for (const [name, expected] of [
  ['NotAllowedError', /系统隐私设置/],
  ['NotFoundError', /未检测到可用相机/],
  ['NotReadableError', /其他应用占用/],
]) {
  test(`camera ${name} reports its actual boundary without changing the draft`, async () => {
    const chat = area('CHAT');
    const context = vm.createContext({ state: chat, native: true, composerPresentationIdentity: {},
      navigator: { mediaDevices: { getUserMedia: async () => { throw Object.assign(new Error(name), { name }); } } },
      document: { querySelector: () => null }, render() {}, queueMicrotask() {},
    });
    vm.runInContext(`${routes}\n${camera}`, context);
    await vm.runInContext('openComposerCamera()', context);
    assert.match(chat.cameraCaptureError, expected);
    assert.equal(chat.composerDraft, 'CHAT draft');
    assert.equal(chat.composerAttachments.length, 0);
  });
}
