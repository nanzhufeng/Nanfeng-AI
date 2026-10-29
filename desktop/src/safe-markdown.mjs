import { isCautionHeading } from './reply-text-emphasis.mjs';
import { renderCodeSyntax } from './code-syntax.mjs';
import { markdownLinkAt, replaceMarkdownLinks } from './markdown-link.mjs';
import { icon, icons } from './icon-source.mjs';
import { sourceDisplayTitle, sourceWebsiteColor, sourceWebsiteName } from './source-link-presentation.mjs';

const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[character]);

const CHATGPT_PRIVATE_MARKER = /\uE200([A-Za-z][A-Za-z0-9_-]*)\uE202([\s\S]*?)\uE201/g;
const CHATGPT_LEGACY_MARKER = /([A-Za-z][A-Za-z0-9_-]*)([\s\S]*?)/g;
const markerLabels = {
  cite: '来源', filecite: '附件来源', i: '图片内容', image: '图片内容', image_group: '图片内容',
  navlist: '相关链接', url: '访问链接', entity: '相关信息', product: '商品信息', products: '商品信息',
  finance: '行情信息', weather: '天气信息', sports: '体育信息',
};

function importedMarkerHtml(kind, payload) {
  const normalized = String(kind || '').toLowerCase();
  const label = markerLabels[normalized] || '相关内容';
  const references = String(payload || '').match(/turn\d+[A-Za-z_-]*\d+/g) || [];
  const suffix = normalized === 'cite' || normalized === 'filecite'
    ? (references.length > 1 ? ` +${Math.min(references.length - 1, 8)}` : '')
    : '';
  return `<span class="chat-imported-marker" role="note" aria-label="${escapeHtml(`${label}${suffix}`)}">${icon(icons.globe, label)}<span>${escapeHtml(`${label}${suffix}`)}</span></span>`;
}

function importedAutomationHtml(value) {
  const source = String(value || '').trim();
  const matched = source.match(/^genui([\s\S]+)$/) || source.match(/^\uE200genui\uE202([\s\S]+)\uE201$/);
  if (!matched) return '';
  const labelMatch = matched[1].match(/"label"\s*:\s*"((?:\\.|[^"\\])*)"/);
  if (!labelMatch) return '';
  let label = labelMatch[1];
  try { label = JSON.parse(`"${label}"`); } catch { /* safe plain fallback */ }
  label = String(label).trim().slice(0, 160);
  return label ? `<aside class="chat-imported-automation" role="note">${icon(icons.history, '历史自动化建议')}<span>${escapeHtml(label)}</span></aside>` : '';
}

function stripMarkdownControlDebris(html) {
  return String(html || '')
    .replace(/\\(?=\*{1,3}|_{1,3}|`)/g, '')
    .replace(/\*{2,3}(?=[\p{L}\p{N}])/gu, '')
    .replace(/(?<=[\p{L}\p{N}])\*{2,3}/gu, '')
    .replace(/\*{2,3}(?=$|[\s，。！？、；：,.!?;）\]】])/gu, '')
    .replace(/_{2,3}(?=[\p{L}\p{N}])/gu, '')
    .replace(/(?<=[\p{L}\p{N}])_{2,3}/gu, '')
    .replace(/_{2,3}(?=$|[\s，。！？、；：,.!?;）\]】])/gu, '')
    .replace(/[\uE200-\uE202]/g, '');
}

function safeHref(value) {
  try {
    const url = new URL(String(value || '').trim());
    return ['http:', 'https:', 'mailto:'].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}

function sourceWebsiteGlyphHtml(source) {
  return `<span class="chat-source-site-glyph" style="--chat-source-site-color:${sourceWebsiteColor(source.href)}" aria-hidden="true">${icon(icons.globe, '')}</span>`;
}

// Android does not put source titles into the assistant prose.  It keeps one
// compact entry surface per rich-text run, then shows the full source list only
// after the user asks for it.  Preserve that interaction here instead of
// treating imported citations as orange inline links.
function sourceShortcutHtml(sources) {
  const unique = [];
  const seen = new Set();
  for (const source of sources) {
    if (!source?.href || seen.has(source.href)) continue;
    seen.add(source.href);
    unique.push({ href: source.href, label: String(source.label || '').trim().slice(0, 300) });
  }
  if (!unique.length) return '';
  const label = unique.length === 1 ? sourceDisplayTitle(unique[0]) : '来源';
  const payload = escapeHtml(JSON.stringify(unique));
  const glyphs = unique.slice(0, 3).map(sourceWebsiteGlyphHtml).join('');
  return `<button type="button" class="chat-source-shortcut" data-action="open-source-links" data-sources="${payload}" aria-label="查看${unique.length}个来源网站"><span class="chat-source-site-glyphs" aria-hidden="true">${glyphs}</span><span>${escapeHtml(label)}</span></button>`;
}

const sourceHeadingLine = value => /^\s*(?:来源(?:网站)?|sources?)\s*[:：]?\s*$/iu.test(String(value || ''));

function sourceListLink(value) {
  const item = String(value || '').trim().replace(/^(?:[-+*]|\d+[.)])\s+/, '');
  const markdown = markdownLinkAt(item, 0);
  if (markdown?.end === item.length) {
    const href = safeHref(markdown.href);
    const label = markdown.label.replace(/\\([\\\[\]<>`*_~])/g, '$1');
    return href ? { href, label } : null;
  }
  const href = safeHref(item);
  return href ? { href, label: sourceWebsiteName(href) } : null;
}

function highlightHtml(html, query) {
  const needle = String(query || '').trim();
  if (!needle) return html;
  const lowerNeedle = needle.toLocaleLowerCase();
  const entities = { amp: '&', lt: '<', gt: '>', quot: '"', '#039': "'", nbsp: '\u00a0' };
  const entityPattern = /&(amp|lt|gt|quot|#039|nbsp);/g;
  return html.split(/(<[^>]+>)/g).map(part => {
    if (part.startsWith('<')) return part;
    // Search displayed characters, but emit slices of the original safe HTML.
    // Decoding once also preserves authored entity-looking text such as &amp;.
    const plain = part.replace(entityPattern, (_entity, name) => entities[name]);
    const lower = plain.toLocaleLowerCase();
    const matches = [];
    let offset = 0;
    while (matches.length < 200) {
      const index = lower.indexOf(lowerNeedle, offset);
      if (index < 0) break;
      matches.push([index, index + lowerNeedle.length]);
      offset = index + lowerNeedle.length;
    }
    if (!matches.length) return part;
    const expanded = lower.length !== plain.length;
    if (expanded) {
      let length = 0;
      for (const character of plain) length += character.toLocaleLowerCase().length;
      // A contextual locale expansion without an exact mapping is left intact.
      if (length !== lower.length) return part;
    }
    let foldedAt = 0, sourceAt = 0;
    const sourceBoundary = (index, end) => {
      let result = index;
      if (expanded) {
        while (sourceAt < plain.length) {
          const character = String.fromCodePoint(plain.codePointAt(sourceAt));
          const size = character.toLocaleLowerCase().length;
          if (foldedAt + size > index) break;
          foldedAt += size; sourceAt += character.length;
        }
        result = foldedAt === index ? sourceAt
          : sourceAt + (end ? String.fromCodePoint(plain.codePointAt(sourceAt)).length : 0);
      }
      if (result > 0 && result < plain.length && /[\uDC00-\uDFFF]/.test(plain[result]) && /[\uD800-\uDBFF]/.test(plain[result - 1])) result += end ? 1 : -1;
      return result;
    };
    let htmlAt = 0, plainAt = 0, nextAmp = part.indexOf('&');
    const htmlBoundary = index => {
      while (plainAt < index) {
        const runEnd = nextAmp < 0 ? part.length : nextAmp;
        const count = Math.min(index - plainAt, runEnd - htmlAt);
        htmlAt += count; plainAt += count;
        if (plainAt === index) break;
        const entity = /^&(amp|lt|gt|quot|#039|nbsp);/.exec(part.slice(htmlAt, htmlAt + 8));
        htmlAt += entity ? entity[0].length : 1; plainAt++;
        nextAmp = part.indexOf('&', htmlAt);
      }
      return htmlAt;
    };
    const fragments = [];
    let emittedHtml = 0, emittedPlain = 0;
    for (const [start, end] of matches) {
      const from = sourceBoundary(start, false), to = sourceBoundary(end, true);
      if (from < emittedPlain) continue;
      const htmlStart = htmlBoundary(from), htmlEnd = htmlBoundary(to);
      fragments.push(part.slice(emittedHtml, htmlStart), `<mark>${part.slice(htmlStart, htmlEnd)}</mark>`);
      emittedHtml = htmlEnd; emittedPlain = to;
    }
    fragments.push(part.slice(emittedHtml));
    return fragments.join('');
  }).join('');
}

const mathCommands = {
  rightarrow: '→', to: '→', Rightarrow: '⇒', implies: '⇒',
  leftarrow: '←', Leftarrow: '⇐', leftrightarrow: '↔',
  leq: '≤', geq: '≥', neq: '≠', approx: '≈', sim: '∼',
  times: '×', cdot: '·', pm: '±', infty: '∞',
  alpha: 'α', beta: 'β', gamma: 'γ', delta: 'δ', theta: 'θ', lambda: 'λ', mu: 'μ', pi: 'π', sigma: 'σ', phi: 'φ', omega: 'ω',
};

function mathReadableText(value) {
  return String(value ?? '')
    .replace(/\\(?:left|right|,|;|!|quad|qquad)\b/g, ' ')
    .replace(/\\([A-Za-z]+)/g, (_all, command) => mathCommands[command] || command)
    .replace(/[{}]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

// This is a deliberately small, safe LaTex projection for persisted reply
// text. It never evaluates TeX or HTML: text is escaped before formatting.
function renderMathExpression(value, { display = false } = {}) {
  const readable = mathReadableText(value);
  const html = escapeHtml(value)
    .replace(/\\(?:left|right|,|;|!|quad|qquad)\b/g, ' ')
    .replace(/\\([A-Za-z]+)/g, (_all, command) => mathCommands[command] || `\\${command}`)
    .replace(/\\(?:text|mathrm|operatorname)\{([^{}]*)\}/g, '$1')
    .replace(/([_^])\{([^{}]+)\}/g, (_all, marker, content) => marker === '^' ? `<sup>${content}</sup>` : `<sub>${content}</sub>`)
    .replace(/([A-Za-z0-9)])\^([A-Za-z0-9+-])/g, '$1<sup>$2</sup>')
    .replace(/([A-Za-z0-9)])_([A-Za-z0-9+-])/g, '$1<sub>$2</sub>');
  return `<span class="chat-markdown-math${display ? ' chat-markdown-math-display' : ''}" role="math" aria-label="${escapeHtml(readable)}">${html}</span>`;
}

// Match equal backtick runs in linear work; unmatched/escaped openers remain text.
function replaceInlineCode(value, render) {
  const runs = [...value.matchAll(/`+/g)], next = new Array(runs.length), latest = new Map();
  for (let i = runs.length - 1; i >= 0; i -= 1) {
    next[i] = latest.get(runs[i][0].length);
    latest.set(runs[i][0].length, i);
  }
  const output = [];
  let cursor = 0;
  for (let i = 0; i < runs.length; i += 1) {
    const opening = runs[i];
    let slashes = 0;
    for (let j = opening.index - 1; j >= 0 && value[j] === '\\'; j -= 1) slashes += 1;
    if (slashes % 2 || next[i] === undefined) continue;
    const closingIndex = next[i], closing = runs[closingIndex];
    let content = value.slice(opening.index + opening[0].length, closing.index).replace(/\r\n?|\n/g, ' ');
    if (content.startsWith(' ') && content.endsWith(' ') && /[^ ]/.test(content)) content = content.slice(1, -1);
    output.push(value.slice(cursor, opening.index), render(content, value.slice(opening.index, closing.index + closing[0].length)));
    cursor = closing.index + closing[0].length;
    i = closingIndex;
  }
  output.push(value.slice(cursor));
  return output.join('');
}

function protectInlineCode(value) {
  let source = String(value ?? '');
  const authoredTokenNamespaces = new Set([...source.matchAll(/NFMARKDOWNTOKEN(\d+)Q/g)].map(match => match[1]));
  let namespace = 0;
  while (authoredTokenNamespaces.has(String(namespace))) namespace += 1;
  const tokenPrefix = `NFMARKDOWNTOKEN${namespace}Q`;
  const tokens = [], codeText = new Map(), codeRaw = new Map();
  source = replaceInlineCode(source, (content, authored) => {
    const key = `${tokenPrefix}${tokens.length}X`;
    tokens.push(`<code>${escapeHtml(content)}</code>`);
    codeText.set(key, content);
    codeRaw.set(key, authored);
    return key;
  });
  return { source, tokenPrefix, tokens, codeText, codeRaw };
}

function renderInlineMarkdown(value, query = '', inert = false, emphasis = null, protection = null) {
  const prepared = protection || protectInlineCode(value);
  let source = protection ? String(value ?? '') : prepared.source;
  const { tokenPrefix, codeText, codeRaw } = prepared;
  const tokenPattern = () => new RegExp(`${tokenPrefix}(\\d+)X`, 'g');
  const tokens = [], sources = [], base = prepared.tokens.length;
  const lookupToken = index => index < base ? prepared.tokens[index] : tokens[index - base];
  const token = html => {
    const key = `${tokenPrefix}${base + tokens.length}X`;
    tokens.push(html);
    return key;
  };
  const plainLabel = label => String(label).replace(tokenPattern(), key => codeText.get(key) ?? key);
  const rawCode = value => String(value).replace(tokenPattern(), key => codeRaw.get(key) ?? key);
  source = source.replace(CHATGPT_PRIVATE_MARKER, (whole, kind, payload) => payload.includes(tokenPrefix) ? whole : token(importedMarkerHtml(kind, payload)));
  source = source.replace(CHATGPT_LEGACY_MARKER, (whole, kind, payload) => payload.includes(tokenPrefix) ? whole : token(importedMarkerHtml(kind, payload)));
  source = source.replace(/\\\(([^\n]*?)\\\)/g, (whole, math) => math.includes(tokenPrefix) ? whole : token(renderMathExpression(math)));
  source = source.replace(/(^|[^\\])\$([^$\n]+?)\$/g, (whole, prefix, math) => math.includes(tokenPrefix) ? whole : `${prefix}${token(renderMathExpression(math))}`);
  // Literal HTML remains text, including authored underscores inside tags.
  source = source.replace(/<([A-Za-z][\w-]*)\b[^>\n]*>[^\n]*?<\/\1\s*>|<\/?[A-Za-z][^>\n]*>/g, value => token(escapeHtml(rawCode(value)).replace(/\n/g, '<br>')));
  source = source.replace(/!\[([^\]]*)\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g, (_, label, rawUrl) => {
    const href = safeHref(rawCode(rawUrl));
    const text = escapeHtml(plainLabel(label || '图片'));
    if (inert) return token(`<span>${text}</span>`);
    return token(href ? `<a class="chat-markdown-link" href="${escapeHtml(href)}" target="_blank" rel="noreferrer noopener">${icon(icons.globe, '链接')}<span>${text}</span></a>` : `<span>${text}</span>`);
  });
  source = replaceMarkdownLinks(source, ({ label, href: rawUrl }) => {
    const href = safeHref(rawCode(rawUrl));
    const readableLabel = plainLabel(label);
    const text = escapeHtml(readableLabel);
    if (inert) return token(`<span>${text}</span>`);
    if (href) {
      sources.push({ href, label: readableLabel });
      return token('');
    }
    return token(`<span>${text}</span>`);
  });
  // Only unwrap a token whose link was moved to the source control. Literal
  // empty parentheses and parenthesized code/math remain authored content.
  source = source.replace(new RegExp(`\\(\\s*(${tokenPrefix}(\\d+)X)\\s*\\)`, 'g'),
    (whole, key, index) => lookupToken(Number(index)) === '' ? key : whole);
  const strong = (_whole, stars, underscores) => {
    const content = stars ?? underscores;
    const visible = content.replace(/&(amp|lt|gt|quot|#039);/g, (_entity, name) => ({ amp: '&', lt: '<', gt: '>', quot: '"', '#039': "'" })[name]);
    const tone = emphasis?.take(visible);
    return `<strong${tone ? ` class="chat-text-${tone}"` : ''}>${content}</strong>`;
  };
  let html = escapeHtml(source)
    .replace(/\*\*([^*\n]+)\*\*|__([^_\n]+)__/g, strong)
    .replace(/~~([^~\n]+)~~/g, '<del>$1</del>')
    .replace(/(^|[\s（(，。！？、；：])\*([^*\n]+)\*(?=$|[\s，。！？、；：,.!?;)）])/g, '$1<em>$2</em>')
    .replace(/(^|[\s（(，。！？、；：])_([^_\n]+)_(?=$|[\s，。！？、；：,.!?;)）])/g, '$1<em>$2</em>');
  html = stripMarkdownControlDebris(html);
  html = html.replace(tokenPattern(), (whole, index) => lookupToken(Number(index)) ?? whole);
  return highlightHtml(`${html}${sourceShortcutHtml(sources)}`, query);
}

function markdownBlockCopyButton(label, value, showLabel = false) {
  const safeLabel = escapeHtml(label);
  return `<button class="chat-markdown-block-copy" type="button" data-action="copy-markdown-block" data-copy-action data-copy-label="${safeLabel}" data-copy-text="${escapeHtml(value)}" aria-label="复制${safeLabel}" title="复制${safeLabel}">${icon(icons.copy, `复制${label}`)}${showLabel ? '<span class="code-copy-label">复制</span>' : ''}</button>`;
}

const tableCells = line => line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map(cell => cell.trim());
const tableDivider = line => /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line || '');
const tableRow = line => {
  const trimmed = String(line || '').trim();
  return trimmed.includes('|') && tableCells(trimmed).length >= 2;
};
const tableStart = (lines, index) => tableRow(lines[index]) && (tableDivider(lines[index + 1]) || tableRow(lines[index + 1]));
function joinSplitSource(lines, index) {
  const line = lines[index], following = lines[index + 1];
  if (line.trimEnd().endsWith(']') && following?.trimStart().startsWith('(')
      && sourceListLink(`${line}\n${following}`)) {
    return { line: `${line.trimEnd()}${following.trimStart()}`, consumed: 2 };
  }
  return { line, consumed: 1 };
}

function renderInlineLines(lines, query, inert, emphasis, trimProse = true) {
  const output = [];
  for (let start = 0; start < lines.length;) {
    let end = start;
    while (end < lines.length && lines[end].trim()) end += 1;
    if (end === start) { output.push(''); start += 1; continue; }
    const prepared = protectInlineCode(lines.slice(start, end).join('\n'));
    const protectedLines = prepared.source.split('\n');
    for (let index = 0; index < protectedLines.length;) {
      const joined = joinSplitSource(protectedLines, index);
      output.push(renderInlineMarkdown(trimProse ? joined.line.trim() : joined.line, query, inert, emphasis, prepared));
      index += joined.consumed;
    }
    start = end;
  }
  return output.join('<br>');
}

const markdownColumns = value => [...value].reduce((column, char) => char === '\t' ? column + 4 - column % 4 : column + 1, 0);
function listContinuationText(line, width) {
  let column = 0, offset = 0;
  while (offset < line.length && column < width && /[ \t]/.test(line[offset])) {
    column += line[offset++] === '\t' ? 4 - column % 4 : 1;
  }
  return ' '.repeat(Math.max(0, column - width)) + line.slice(offset);
}

// Imported Chinese answers frequently omit the optional Markdown space in
// headings such as `###二、估值`.  Accept that only when the content begins
// with an unambiguous Chinese/numbered heading character; `#hash` remains
// ordinary text instead of becoming a heading.
const headingLine = line => String(line || '').match(/^\s{0,3}(#{1,6})(?:\s+|(?=[\p{Script=Han}0-9０-９（一二三四五六七八九十]))(.+?)\s*#*\s*$/u);

// Share fence identity between block boundaries and code rendering.
// Keep the existing indentation policy; this is not a full CommonMark parser.
const codeFenceStart = line => {
  const match = String(line || '').match(/^\s*(`{3,}|~{3,})(.*)$/);
  if (!match || (match[1][0] === '`' && match[2].includes('`'))) return null;
  return { marker: match[1][0], length: match[1].length, language: match[2].trim().split(/\s+/)[0] };
};
const codeFenceEnd = (line, fence) => {
  const match = String(line || '').match(/^\s*(`+|~+)[ \t]*$/);
  return Boolean(match && match[1][0] === fence.marker && match[1].length >= fence.length);
};

const blockStart = (lines, index) => {
  const line = lines[index] || '';
  return Boolean(codeFenceStart(line)) || /^\s*(?:\\\[|\$\$)\s*$/.test(line) || Boolean(headingLine(line)) || /^\s*>/.test(line)
    || /^\s*([-+*]|\d+[.)])\s+/.test(line) || /^\s*((\*\s*){3,}|(-\s*){3,}|(_\s*){3,})\s*$/.test(line)
    || tableStart(lines, index);
};

/**
 * Small, dependency-free Markdown projection for persisted Assistant and OCR text.
 * Raw HTML is always escaped; only allow-listed http(s)/mailto links become active.
 */
export function renderSafeMarkdown(value, options = {}) {
  return renderMarkdownBlocks(value, options, 0);
}

function renderMarkdownBlocks(value, { query = '', inert = false, emphasis = null } = {}, listDepth) {
  const replyEmphasis = inert ? null : emphasis;
  const inlineMarkdown = (text, needle, eligible = true) => renderInlineMarkdown(text, needle, inert, eligible ? replyEmphasis : null);
  const inlineLines = (lines, needle, eligible = true, trimProse = true) => renderInlineLines(lines, needle, inert, eligible ? replyEmphasis : null, trimProse);
  const blockCopy = (label, text) => inert ? '' : markdownBlockCopyButton(label, text);
  // Source continuation is normalized within its leaf block, after inline
  // code protection. Raw fenced and inline code never enter a global prepass.
  const normalizedValue = String(value ?? '')
    .replaceAll('\r\n', '\n')
    .replaceAll('\r', '\n');
  const lines = normalizedValue.split('\n');
  // Untrusted imported text can contain thousands of indentation levels. Keep
  // its literal content at the limit rather than recursing until a stack error.
  if (listDepth >= 64) return `<p>${inlineLines(lines, query, false, false)}</p>`;
  const output = [];
  let index = 0;
  while (index < lines.length) {
    const line = lines[index];
    if (!line.trim()) { index += 1; continue; }
    const displayMathClosing = line.trim() === '\\[' ? '\\]' : line.trim() === '$$' ? '$$' : null;
    if (displayMathClosing) {
      const openingIndex = index;
      const expression = [];
      index += 1;
      while (index < lines.length && lines[index].trim() !== displayMathClosing) expression.push(lines[index++].trim());
      if (index < lines.length) {
        index += 1;
        output.push(`<div class="chat-markdown-math-wrap">${renderMathExpression(expression.join(' ').trim(), { display: true })}</div>`);
      } else {
        // Preserve malformed historical input as text without swallowing the
        // rest of the transcript.
        index = openingIndex + 1;
        output.push(`<p>${inlineMarkdown(line, query)}</p>`);
      }
      continue;
    }
    if (!inert && sourceHeadingLine(line)) {
      const sources = [];
      let cursor = index + 1;
      while (cursor < lines.length && !lines[cursor].trim()) cursor += 1;
      while (cursor < lines.length) {
        const joined = joinSplitSource(lines, cursor);
        const source = sourceListLink(joined.line);
        if (!source) break;
        sources.push(source);
        cursor += joined.consumed;
      }
      if (sources.length) {
        output.push(`<p class="chat-source-section">${sourceShortcutHtml(sources)}</p>`);
        index = cursor;
        continue;
      }
    }
    const automation = importedAutomationHtml(line);
    if (automation) {
      output.push(automation);
      index += 1;
      continue;
    }
    const fence = codeFenceStart(line);
    if (fence) {
      const code = [];
      index += 1;
      while (index < lines.length && !codeFenceEnd(lines[index], fence)) code.push(lines[index++]);
      if (index < lines.length) index += 1;
      const language = fence.language ? ` data-language="${escapeHtml(fence.language)}"` : '';
      const codeText = code.join('\n');
      const codeHtml = query ? highlightHtml(escapeHtml(codeText), query) : renderCodeSyntax(codeText, fence.language, escapeHtml);
      output.push(`<div class="chat-markdown-copyable chat-markdown-code-copyable"><div class="chat-code-header"><span class="chat-code-language">${escapeHtml(fence.language || 'text')}</span>${inert ? '' : markdownBlockCopyButton('代码块', codeText, true)}</div><pre class="chat-markdown-code"${language}><code>${codeHtml}</code></pre></div>`);
      continue;
    }
    const heading = headingLine(line);
    if (heading) {
      const level = Math.min(6, heading[1].length + 1);
      if (replyEmphasis) replyEmphasis.cautionSection = isCautionHeading(heading[2]);
      output.push(`<h${level}>${inlineMarkdown(heading[2], query, false)}</h${level}>`);
      index += 1;
      continue;
    }
    if (tableStart(lines, index)) {
      const rawRows = [];
      while (index < lines.length && (tableRow(lines[index]) || /^\s*\|.*\|\s*$/.test(lines[index])) && lines[index].trim()) rawRows.push(tableCells(lines[index++]));
      const hasHeader = rawRows.length > 1 && tableDivider(lines[index - rawRows.length + 1]);
      const rows = hasHeader ? rawRows.slice(2) : rawRows;
      const columnCount = Math.max(...rawRows.map(row => row.length));
      const cells = row => Array.from({ length: columnCount }, (_, cellIndex) => inlineMarkdown(row[cellIndex] || '', query, false));
      const header = hasHeader ? `<thead><tr>${cells(rawRows[0]).map(cell => `<th>${cell}</th>`).join('')}</tr></thead>` : '';
      const tableMarkdown = rawRows.map(row => `| ${row.join(' | ')} |`).join('\n');
      output.push(`<div class="chat-markdown-copyable chat-markdown-table-copyable"><div class="chat-markdown-table-wrap"><table>${header}<tbody>${rows.map(row => `<tr>${cells(row).map(cell => `<td>${cell}</td>`).join('')}</tr>`).join('')}</tbody></table></div>${blockCopy('表格（保留 Markdown 格式）', tableMarkdown)}</div>`);
      continue;
    }
    if (/^\s*>/.test(line)) {
      const quote = [];
      while (index < lines.length && /^\s*>/.test(lines[index])) quote.push(lines[index++].replace(/^\s*>\s?/, ''));
      output.push(`<blockquote>${inlineLines(quote, query, false, false)}</blockquote>`);
      continue;
    }
    const listMatch = line.match(/^(\s*)([-+*]|\d+[.)])\s+(.+)$/);
    if (listMatch) {
      const ordered = /^\d/.test(listMatch[2]);
      const baseIndent = markdownColumns(listMatch[1]);
      const items = [];
      let loose = false;
      while (index < lines.length) {
        const item = lines[index].match(/^(\s*)([-+*]|\d+[.)])\s+(.+)$/);
        if (!item || markdownColumns(item[1]) !== baseIndent || /^\d/.test(item[2]) !== ordered) break;
        const task = item[3].match(/^\[([ xX])\]\s+(.+)$/);
        const width = markdownColumns(lines[index].slice(0, lines[index].length - item[3].length));
        const itemLines = [task ? task[2] : item[3]];
        index += 1;
        while (index < lines.length) {
          if (!lines[index].trim()) {
            let nextIndex = index;
            while (nextIndex < lines.length && !lines[nextIndex].trim()) nextIndex += 1;
            if (nextIndex >= lines.length) break;
            const nextItem = lines[nextIndex].match(/^(\s*)([-+*]|\d+[.)])\s+(.+)$/);
            if (nextItem && markdownColumns(nextItem[1]) === baseIndent && /^\d/.test(nextItem[2]) === ordered) {
              loose = true; index = nextIndex; break;
            }
            const leading = lines[nextIndex].match(/^[ \t]*/)[0];
            if (markdownColumns(leading) < width) break;
            loose = true;
            while (index < nextIndex) { itemLines.push(''); index += 1; }
            continue;
          }
          const continued = listContinuationText(lines[index], width);
          const next = listContinuationText(lines[index + 1] || '', width);
          const leading = lines[index].match(/^[ \t]*/)[0];
          // Indented blocks (including differently typed child lists and code
          // fences) belong to this item. Only outdented prose may be lazy.
          if (markdownColumns(leading) < width && (blockStart([continued, next], 0) || sourceHeadingLine(continued))) break;
          itemLines.push(continued);
          index += 1;
        }
        items.push({ marker: item[2], task, lines: itemLines });
      }
      const tag = ordered ? 'ol' : 'ul';
      const listClass = `chat-markdown-list chat-markdown-${ordered ? 'ordered' : 'unordered'}`;
      const listItems = items.map(item => {
        const start = ordered ? ` value="${escapeHtml(item.marker.replace(/[^0-9]/g, ''))}"` : '';
        let body = renderMarkdownBlocks(item.lines.join('\n'), { query, inert, emphasis: replyEmphasis }, listDepth + 1);
        if (!loose) body = body.replace(/^<p>([\s\S]*?)<\/p>/, '$1');
        return item.task
          ? `<li class="chat-markdown-task" data-depth="${listDepth}"${start}><input type="checkbox" disabled ${item.task[1].toLowerCase() === 'x' ? 'checked' : ''}><div class="chat-markdown-task-content">${body}</div></li>`
          : `<li data-depth="${listDepth}"${start}>${body}</li>`;
      }).join('');
      output.push(`<${tag} class="${listClass}">${listItems}</${tag}>`);
      continue;
    }
    if (/^\s*((\*\s*){3,}|(-\s*){3,}|(_\s*){3,})\s*$/.test(line)) {
      output.push('<hr>');
      index += 1;
      continue;
    }
    const paragraph = [line];
    index += 1;
    while (index < lines.length && lines[index].trim() && !blockStart(lines, index)) paragraph.push(lines[index++]);
    output.push(`<p>${inlineLines(paragraph, query)}</p>`);
  }
  return output.join('') || '<p>&nbsp;</p>';
}
