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
  const markdown = item.match(/^\[([^\]]+)\]\s*\(\s*([^\s)]+)(?:\s+"[^"]*")?\s*\)$/);
  if (markdown) {
    const href = safeHref(markdown[2]);
    return href ? { href, label: markdown[1] } : null;
  }
  const href = safeHref(item);
  return href ? { href, label: sourceWebsiteName(href) } : null;
}

function highlightHtml(html, query) {
  const needle = String(query || '').trim();
  if (!needle) return html;
  const lowerNeedle = needle.toLocaleLowerCase();
  return html.split(/(<[^>]+>)/g).map(part => {
    if (part.startsWith('<')) return part;
    const lower = part.toLocaleLowerCase();
    const fragments = [];
    let offset = 0;
    while (fragments.length < 400) {
      const index = lower.indexOf(lowerNeedle, offset);
      if (index < 0) break;
      fragments.push(part.slice(offset, index), `<mark>${part.slice(index, index + needle.length)}</mark>`);
      offset = index + Math.max(needle.length, 1);
    }
    fragments.push(part.slice(offset));
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

function renderInlineMarkdown(value, query = '', inert = false) {
  const tokens = [];
  const sources = [];
  const token = html => {
    const key = `NFMARKDOWNTOKEN${tokens.length}X`;
    tokens.push(html);
    return key;
  };
  let source = String(value ?? '');
  source = source.replace(CHATGPT_PRIVATE_MARKER, (_all, kind, payload) => token(importedMarkerHtml(kind, payload)));
  source = source.replace(CHATGPT_LEGACY_MARKER, (_all, kind, payload) => token(importedMarkerHtml(kind, payload)));
  source = source.replace(/\\\(([^\n]*?)\\\)/g, (_all, math) => token(renderMathExpression(math)));
  source = source.replace(/(^|[^\\])\$([^$\n]+?)\$/g, (_all, prefix, math) => `${prefix}${token(renderMathExpression(math))}`);
  source = source.replace(/`([^`\n]+)`/g, (_, code) => token(`<code>${escapeHtml(code)}</code>`));
  source = source.replace(/!\[([^\]]*)\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g, (_, label, rawUrl) => {
    const href = safeHref(rawUrl);
    const text = escapeHtml(label || '图片');
    if (inert) return token(`<span>${text}</span>`);
    return token(href ? `<a class="chat-markdown-link" href="${escapeHtml(href)}" target="_blank" rel="noreferrer noopener">${icon(icons.globe, '链接')}<span>${text}</span></a>` : `<span>${text}</span>`);
  });
  source = source.replace(/\[([^\]]+)\]\s*\(\s*([^\s)]+)(?:\s+"[^"]*")?\s*\)/g, (_, label, rawUrl) => {
    const href = safeHref(rawUrl);
    const text = escapeHtml(label);
    if (inert) return token(`<span>${text}</span>`);
    if (href) {
      sources.push({ href, label });
      return token('');
    }
    return token(`<span>${text}</span>`);
  });
  let html = escapeHtml(source)
    .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
    .replace(/__([^_\n]+)__/g, '<strong>$1</strong>')
    .replace(/~~([^~\n]+)~~/g, '<del>$1</del>')
    .replace(/(^|[\s（(])\*([^*\n]+)\*(?=$|[\s，。！？、；：,.!?;)）])/g, '$1<em>$2</em>')
    .replace(/(^|[\s（(])_([^_\n]+)_(?=$|[\s，。！？、；：,.!?;)）])/g, '$1<em>$2</em>');
  html = stripMarkdownControlDebris(html);
  tokens.forEach((replacement, index) => { html = html.replaceAll(`NFMARKDOWNTOKEN${index}X`, replacement); });
  return highlightHtml(`${html}${sourceShortcutHtml(sources)}`, query);
}

function markdownBlockCopyButton(label, value) {
  const safeLabel = escapeHtml(label);
  return `<button class="chat-markdown-block-copy" type="button" data-action="copy-markdown-block" data-copy-action data-copy-label="${safeLabel}" data-copy-text="${escapeHtml(value)}" aria-label="复制${safeLabel}" title="复制${safeLabel}">${icon(icons.copy, `复制${label}`)}</button>`;
}

const tableCells = line => line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map(cell => cell.trim());
const tableDivider = line => /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line || '');
const tableRow = line => {
  const trimmed = String(line || '').trim();
  return trimmed.includes('|') && tableCells(trimmed).length >= 2;
};
const tableStart = (lines, index) => tableRow(lines[index]) && (tableDivider(lines[index + 1]) || tableRow(lines[index + 1]));
// Imported Chinese answers frequently omit the optional Markdown space in
// headings such as `###二、估值`.  Accept that only when the content begins
// with an unambiguous Chinese/numbered heading character; `#hash` remains
// ordinary text instead of becoming a heading.
const headingLine = line => String(line || '').match(/^\s{0,3}(#{1,6})(?:\s+|(?=[\p{Script=Han}0-9０-９（一二三四五六七八九十]))(.+?)\s*#*\s*$/u);

const blockStart = (lines, index) => {
  const line = lines[index] || '';
  return /^\s*```/.test(line) || /^\s*(?:\\\[|\$\$)\s*$/.test(line) || Boolean(headingLine(line)) || /^\s*>/.test(line)
    || /^\s*([-+*]|\d+[.)])\s+/.test(line) || /^\s*((\*\s*){3,}|(-\s*){3,}|(_\s*){3,})\s*$/.test(line)
    || tableStart(lines, index);
};

/**
 * Small, dependency-free Markdown projection for persisted Assistant and OCR text.
 * Raw HTML is always escaped; only allow-listed http(s)/mailto links become active.
 */
export function renderSafeMarkdown(value, { query = '', inert = false } = {}) {
  const inlineMarkdown = (text, needle) => renderInlineMarkdown(text, needle, inert);
  const blockCopy = (label, text) => inert ? '' : markdownBlockCopyButton(label, text);
  // Some historical Android transcripts split a Markdown destination onto the
  // following physical line.  Normalize only that harmless source form before
  // block parsing so it retains the same compact source affordance.
  const normalizedValue = String(value ?? '')
    .replaceAll('\r\n', '\n')
    .replaceAll('\r', '\n')
    .replace(/\[([^\]\n]+)\]\s*\n\s*\(\s*(https?:\/\/[^\s)]+)\s*\)/g, '[$1]($2)');
  const lines = normalizedValue.split('\n');
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
        const source = sourceListLink(lines[cursor]);
        if (!source) break;
        sources.push(source);
        cursor += 1;
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
    const fence = line.match(/^\s*```\s*([^\s`]*)\s*$/);
    if (fence) {
      const code = [];
      index += 1;
      while (index < lines.length && !/^\s*```\s*$/.test(lines[index])) code.push(lines[index++]);
      if (index < lines.length) index += 1;
      const language = fence[1] ? ` data-language="${escapeHtml(fence[1])}"` : '';
      const codeText = code.join('\n');
      output.push(`<div class="chat-markdown-copyable chat-markdown-code-copyable"><pre class="chat-markdown-code"${language}><code>${highlightHtml(escapeHtml(codeText), query)}</code></pre>${blockCopy('代码块', codeText)}</div>`);
      continue;
    }
    const heading = headingLine(line);
    if (heading) {
      const level = Math.min(6, heading[1].length + 1);
      output.push(`<h${level}>${inlineMarkdown(heading[2], query)}</h${level}>`);
      index += 1;
      continue;
    }
    if (tableStart(lines, index)) {
      const rawRows = [];
      while (index < lines.length && tableRow(lines[index]) && lines[index].trim()) rawRows.push(tableCells(lines[index++]));
      const hasHeader = rawRows.length > 1 && tableDivider(lines[index - rawRows.length + 1]);
      const rows = hasHeader ? rawRows.slice(2) : rawRows;
      const columnCount = Math.max(...rawRows.map(row => row.length));
      const cells = row => Array.from({ length: columnCount }, (_, cellIndex) => inlineMarkdown(row[cellIndex] || '', query));
      const header = hasHeader ? `<thead><tr>${cells(rawRows[0]).map(cell => `<th>${cell}</th>`).join('')}</tr></thead>` : '';
      const tableMarkdown = rawRows.map(row => `| ${row.join(' | ')} |`).join('\n');
      output.push(`<div class="chat-markdown-copyable chat-markdown-table-copyable"><div class="chat-markdown-table-wrap"><table>${header}<tbody>${rows.map(row => `<tr>${cells(row).map(cell => `<td>${cell}</td>`).join('')}</tr>`).join('')}</tbody></table></div>${blockCopy('表格（保留 Markdown 格式）', tableMarkdown)}</div>`);
      continue;
    }
    if (/^\s*>/.test(line)) {
      const quote = [];
      while (index < lines.length && /^\s*>/.test(lines[index])) quote.push(lines[index++].replace(/^\s*>\s?/, ''));
      output.push(`<blockquote>${quote.map(item => inlineMarkdown(item, query)).join('<br>')}</blockquote>`);
      continue;
    }
    const listMatch = line.match(/^(\s*)([-+*]|\d+[.)])\s+(.+)$/);
    if (listMatch) {
      const ordered = /^\d/.test(listMatch[2]);
      const items = [];
      while (index < lines.length) {
        const item = lines[index].match(/^(\s*)([-+*]|\d+[.)])\s+(.+)$/);
        if (!item || /^\d/.test(item[2]) !== ordered) break;
        const task = item[3].match(/^\[([ xX])\]\s+(.+)$/);
        items.push({ indent: item[1].replace(/\t/g, '    ').length, marker: item[2], task, text: task ? task[2] : item[3] });
        index += 1;
      }
      const indents = [...new Set(items.map(item => item.indent))].sort((left, right) => left - right);
      const tag = ordered ? 'ol' : 'ul';
      const listClass = `chat-markdown-list chat-markdown-${ordered ? 'ordered' : 'unordered'}`;
      const listItems = items.map(item => {
        const depth = indents.indexOf(item.indent);
        const start = ordered ? ` value="${escapeHtml(item.marker.replace(/[^0-9]/g, ''))}"` : '';
        return item.task
          ? `<li class="chat-markdown-task" data-depth="${depth}"${start}><input type="checkbox" disabled ${item.task[1].toLowerCase() === 'x' ? 'checked' : ''}><span>${inlineMarkdown(item.text, query)}</span></li>`
          : `<li data-depth="${depth}"${start}>${inlineMarkdown(item.text, query)}</li>`;
      }).join('');
      output.push(`<${tag} class="${listClass}">${listItems}</${tag}>`);
      continue;
    }
    if (/^\s*((\*\s*){3,}|(-\s*){3,}|(_\s*){3,})\s*$/.test(line)) {
      output.push('<hr>');
      index += 1;
      continue;
    }
    const paragraph = [line.trim()];
    index += 1;
    while (index < lines.length && lines[index].trim() && !blockStart(lines, index)) paragraph.push(lines[index++].trim());
    output.push(`<p>${paragraph.map(item => inlineMarkdown(item, query)).join('<br>')}</p>`);
  }
  return output.join('') || '<p>&nbsp;</p>';
}
