import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const rust = await readFile(resolve(import.meta.dirname, '../src-tauri/src/lib.rs'), 'utf8');

test('opening a large video validates a bounded header through a read-only owner path', () => {
  const commandStart = rust.indexOf('async fn read_desktop_video_preview(');
  const commandEnd = rust.indexOf('\n#[tauri::command]', commandStart + 1);
  const command = rust.slice(commandStart, commandEnd);
  assert.match(command, /run_desktop_store_paths_blocking/);
  assert.match(command, /read_desktop_video_preview_from_paths/);
  assert.doesNotMatch(command, /run_desktop_store_blocking\(/);

  const readerStart = rust.indexOf('fn read_desktop_video_preview_from_paths(');
  const readerEnd = rust.indexOf('\n#[tauri::command]', readerStart);
  const reader = rust.slice(readerStart, readerEnd);
  assert.match(reader, /open_desktop_workspace_readonly_connection/);
  assert.match(reader, /DESKTOP_VIDEO_PREVIEW_HEADER_BYTES/);
  assert.match(reader, /file\.read_exact\(&mut header\)/);
  assert.match(reader, /is_mp4_video_iso_bmff\(&header\)/);
  assert.doesNotMatch(reader, /fs::read\(/);
  assert.doesNotMatch(reader, /sha256\(&bytes\)/);
});

test('the media protocol never queues on the mutable store or reads an un-ranged movie in full', () => {
  const protocolStart = rust.indexOf('fn desktop_media_protocol_response(');
  const protocolEnd = rust.indexOf('\nfn read_desktop_video_thumbnail_from_paths', protocolStart);
  const protocol = rust.slice(protocolStart, protocolEnd);
  assert.match(protocol, /let paths = state\.store_paths\.clone\(\)/);
  assert.match(protocol, /open_desktop_workspace_readonly_connection/);
  assert.doesNotMatch(protocol, /state\.store\.lock\(\)/);
  assert.match(protocol, /byte_count\.min\(DESKTOP_MEDIA_RANGE_CHUNK_BYTES\) - 1/);
  assert.match(protocol, /static initial media range response/);
  assert.doesNotMatch(protocol, /fs::read\(asset_path\)/);
});
