import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { loadWindowsPublicCloudConfig, validatedPublicCloudConfig } from '../scripts/windows-public-cloud-config.mjs';

const url = 'https://syntheticproject.supabase.co';
const key = 'sb_publishable_SYNTHETIC_PUBLIC_CLIENT_ONLY';
const jwt = claims => `eyJhbGciOiJIUzI1NiJ9.${Buffer.from(JSON.stringify(claims)).toString('base64url')}.synthetic`;

test('offline acceptance may omit public configuration but never ignores partial or invalid configuration', async () => {
  const root = await mkdtemp(join(tmpdir(), 'nfai-public-config-optional.'));
  try {
    const desktop = join(root, 'desktop'); await mkdir(desktop);
    const userConfigPath = join(root, 'user-public.json');
    const options = { env: {}, userConfigPath, required: false };
    assert.deepEqual(await loadWindowsPublicCloudConfig(desktop, options), {});
    await assert.rejects(loadWindowsPublicCloudConfig(desktop, { ...options, required: true }));
    await assert.rejects(loadWindowsPublicCloudConfig(desktop, { ...options, env: { NANFENG_DESKTOP_BUNDLED_SUPABASE_URL: url } }));
    await writeFile(userConfigPath, '{invalid');
    await assert.rejects(loadWindowsPublicCloudConfig(desktop, options));
    await writeFile(userConfigPath, JSON.stringify({ supabaseUrl: url, supabasePublishableKey: key }));
    assert.equal((await loadWindowsPublicCloudConfig(desktop, options)).NANFENG_DESKTOP_BUNDLED_SUPABASE_PUBLISHABLE_KEY, key);
    await writeFile(join(root, 'local.properties'), 'nanfeng.ai.cloud.url=' + url + '\n');
    await assert.rejects(loadWindowsPublicCloudConfig(desktop, options), 'optional mode must not mask a broken higher-priority source');
  } finally { await rm(root, { recursive: true, force: true }); }
});

test('production accepts public client parameters and refuses unsafe endpoints and privileged keys', () => {
  assert.equal(validatedPublicCloudConfig(url + '/', key).NANFENG_DESKTOP_BUNDLED_SUPABASE_URL, url);
  assert.ok(validatedPublicCloudConfig(url, jwt({ iss: 'supabase', role: 'anon', ref: 'syntheticproject' })));
  for (const args of [[url, ''], ['', key], ['http://localhost', key], ['https://u:p@example.com', key],
    [url, 'sb_secret_SYNTHETIC_PRIVILEGED_ONLY'], [url, jwt({ iss: 'supabase', role: 'service_role' })],
    [url, jwt({ iss: 'supabase', role: 'anon', ref: 'otherproject' })], [url, key + '\nINJECTED']]) {
    assert.throws(() => validatedPublicCloudConfig(...args));
  }
});

test('pair resolution prefers explicit environment, preserves partial-source failures and supports user configuration', async () => {
  const root = await mkdtemp(join(tmpdir(), 'nfai-public-config-test.'));
  try {
    const desktop = join(root, 'desktop'); await mkdir(desktop);
    const userConfigPath = join(root, 'user-public.json');
    await writeFile(userConfigPath, JSON.stringify({ supabaseUrl: url, supabasePublishableKey: key }));
    const options = { env: {}, userConfigPath };
    assert.equal((await loadWindowsPublicCloudConfig(desktop, options)).NANFENG_DESKTOP_BUNDLED_SUPABASE_PUBLISHABLE_KEY, key);
    await assert.rejects(loadWindowsPublicCloudConfig(desktop, { ...options, env: { NANFENG_DESKTOP_BUNDLED_SUPABASE_URL: url } }));
    await writeFile(join(root, 'local.properties'), 'nanfeng.ai.cloud.url=' + url + '\n');
    await assert.rejects(loadWindowsPublicCloudConfig(desktop, options), 'partial local source cannot silently mix another key');
    await writeFile(join(root, 'local.properties'), `nanfeng.ai.cloud.url=${url}\nnanfeng.ai.cloud.publishableKey=${key}\nunrelated.private.value=DO_NOT_USE\n`);
    assert.equal((await loadWindowsPublicCloudConfig(desktop, options)).NANFENG_DESKTOP_BUNDLED_SUPABASE_URL, url);
    await assert.rejects(loadWindowsPublicCloudConfig(join(root, 'missing', 'desktop'), { env: {}, userConfigPath: join(root, 'absent.json') }));
  } finally { await rm(root, { recursive: true, force: true }); }
});
