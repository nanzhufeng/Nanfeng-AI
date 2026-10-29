const siteTitleRules = [
  [/eastmoney\.com$/i, '东方财富'],
  [/^etf\.run$/i, 'ETF 数据'],
  [/whatsid\.me$/i, 'ETF 估值'],
  [/lixinger\.com$/i, '理杏仁'],
  [/goodmoney\.club$/i, '好买基金'],
  [/hangyan\.co$/i, '行研社'],
];

export function sourceHost(href) {
  try {
    return new URL(String(href || '')).hostname.replace(/^www\./i, '');
  } catch {
    return '';
  }
}

export function sourceWebsiteName(href) {
  const host = sourceHost(href);
  const matched = siteTitleRules.find(([rule]) => rule.test(host));
  if (matched) return matched[1];
  return ({ openai: 'OpenAI', anthropic: 'Anthropic', github: 'GitHub', google: 'Google' })[host.split('.')[0]?.toLowerCase()] || '外部网站';
}

export function sourceDisplayTitle({ href, label } = {}) {
  const candidate = String(label || '').trim();
  return candidate && !isWebsiteAddressLabel(candidate) ? candidate : sourceWebsiteName(href);
}

export function sourceWebsiteColor(href) {
  const family = sourceHost(href).split('.')[0]?.toLowerCase();
  return ({ openai: '#10A37F', anthropic: '#B86649', github: '#24292F', google: '#4285F4' })[family] || '#64748B';
}

function isWebsiteAddressLabel(value) {
  return /^https?:\/\//i.test(value) || /^[a-z0-9-]+(?:\.[a-z0-9-]+)+(?:\/.*)?$/i.test(value);
}
