import test from 'node:test';
import assert from 'node:assert/strict';
import { sourceDisplayTitle, sourceWebsiteName } from '../src/source-link-presentation.mjs';

test('source rows put a Chinese website name above the real URL', () => {
  for (const [url, expected] of [
    ['https://caifuhao.eastmoney.com/news/2024123014', '东方财富'],
    ['https://www.etf.run/index/SSE/000510', 'ETF 数据'],
    ['https://etf.whatsid.me/valuation', 'ETF 估值'],
    ['https://www.lixinger.com/equity/index/detail/sh/000510/510', '理杏仁'],
    ['https://www.goodmoney.club/uploads/a', '好买基金'],
    ['https://www.hangyan.co/charts/3502326101609284620', '行研社'],
  ]) {
    assert.equal(sourceWebsiteName(url), expected);
    assert.equal(sourceDisplayTitle({ href: url, label: url }), expected);
  }
});

test('a user remark stays above its real URL without duplicating an address', () => {
  assert.equal(sourceDisplayTitle({ href: 'https://example.com/path', label: '政策原文' }), '政策原文');
  assert.equal(sourceDisplayTitle({ href: 'https://example.com/path', label: 'example.com/path' }), '外部网站');
});
