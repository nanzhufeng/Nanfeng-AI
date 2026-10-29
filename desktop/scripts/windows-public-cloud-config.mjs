import { readFile } from 'node:fs/promises';
import { homedir } from 'node:os';
import { resolve } from 'node:path';

const URL_ENV = 'NANFENG_DESKTOP_BUNDLED_SUPABASE_URL';
const KEY_ENV = 'NANFENG_DESKTOP_BUNDLED_SUPABASE_PUBLISHABLE_KEY';

export function validatedPublicCloudConfig(urlValue, keyValue) {
  const url = String(urlValue || '').trim();
  const key = String(keyValue || '').trim();
  if (!url || !key || /[\r\n]/u.test(url + key)) throw new Error('南枫云公开配置不完整，停止生产打包。');
  let parsed;
  try { parsed = new URL(url); } catch { throw new Error('南枫云地址无效，停止生产打包。'); }
  if (parsed.protocol !== 'https:' || parsed.username || parsed.password
      || ['localhost', '127.0.0.1', '[::1]'].includes(parsed.hostname) || parsed.search || parsed.hash) {
    throw new Error('南枫云地址必须是正式 HTTPS 服务地址。');
  }
  let publicKey = /^sb_publishable_[A-Za-z0-9_-]{16,}$/u.test(key);
  if (!publicKey) {
    try {
      const pieces = key.split('.');
      const claims = JSON.parse(Buffer.from(pieces[1], 'base64url').toString('utf8'));
      publicKey = pieces.length === 3 && claims.role === 'anon' && claims.iss === 'supabase';
      if (claims.ref && parsed.hostname.endsWith('.supabase.co')) {
        publicKey = publicKey && claims.ref === parsed.hostname.split('.')[0];
      }
    } catch { publicKey = false; }
  }
  if (!publicKey) throw new Error('生产包仅允许公开 publishable/anon 客户端参数，拒绝私密或管理员密钥。');
  return { [URL_ENV]: url.replace(/\/$/u, ''), [KEY_ENV]: key };
}

export async function loadWindowsPublicCloudConfig(desktopRoot, {
  env = process.env, userConfigPath = resolve(homedir(), '.config/nanfeng-ai/public-cloud-config.json'),
  required = true,
} = {}) {
  if (env[URL_ENV] || env[KEY_ENV]) return validatedPublicCloudConfig(env[URL_ENV], env[KEY_ENV]);
  const localProperties = resolve(desktopRoot, '../local.properties');
  try {
    const values = new Map();
    for (const line of (await readFile(localProperties, 'utf8')).split(/\r?\n/u)) {
      const marker = line.indexOf('=');
      const name = line.slice(0, marker).trim();
      if (marker >= 0 && ['nanfeng.ai.cloud.url', 'nanfeng.ai.cloud.publishableKey'].includes(name)) {
        values.set(name, line.slice(marker + 1).trim());
      }
    }
    if (values.size) return validatedPublicCloudConfig(values.get('nanfeng.ai.cloud.url'), values.get('nanfeng.ai.cloud.publishableKey'));
  } catch (error) { if (error.code !== 'ENOENT') throw error; }
  try {
    const raw = await readFile(userConfigPath, 'utf8');
    let config;
    try { config = JSON.parse(raw); } catch { throw new Error('用户级南枫云公开配置格式无效，停止生产打包。'); }
    return validatedPublicCloudConfig(config.supabaseUrl, config.supabasePublishableKey);
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
    if (!required) return {};
    throw new Error('未找到正式南枫云公开客户端配置，停止生产打包。');
  }
}
