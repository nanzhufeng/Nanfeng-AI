import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
const config=JSON.parse(await readFile(new URL('../src-tauri/tauri.windows-acceptance.conf.json',import.meta.url),'utf8'));
const main=config.app.windows.find(w=>w.label==='main');
test('the isolated acceptance main window explicitly starts hidden',()=>assert.equal(main.visible,false));
test('the isolated acceptance main window explicitly declines initial focus',()=>assert.equal(main.focus,false));
test('the isolated acceptance main window explicitly disallows background focus',()=>assert.equal(main.focusable,false));
