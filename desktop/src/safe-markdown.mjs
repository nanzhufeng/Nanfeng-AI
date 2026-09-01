const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[character]);

function safeHref(value) {
  try {
    const url = new URL(String(value || '').trim());
    return ['http:', 'https:', 'mailto:'].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
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

function inlineMarkdown(value, query = '') {
  const tokens = [];
  const token = html => {
    const key = `NFMARKDOWNTOKEN${tokens.length}X`;
    tokens.push(html);
    return key;
  };
  let source = String(value ?? '');
  source = source.replace(/`([^`\n]+)`/g, (_, code) => token(`<code>${escapeHtml(code)}</code>`));
  source = source.replace(/!\[([^\]]*)\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g, (_, label, rawUrl) => {
    const href = safeHref(rawUrl);
    const text = escapeHtml(label || '图片');
    return token(href ? `<a class="chat-markdown-link" href="${escapeHtml(href)}" target="_blank" rel="noreferrer noopener">${text}</a>` : `<span>${text}</span>`);
  });
  source = source.replace(/\[([^\]]+)\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g, (_, label, rawUrl) => {
    const href = safeHref(rawUrl);
    const text = escapeHtml(label);
    return token(href ? `<a class="chat-markdown-link" href="${escapeHtml(href)}" target="_blank" rel="noreferrer noopener">${text}</a>` : `<span>${text}</span>`);
  });
  let html = escapeHtml(source)
    .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
    .replace(/__([^_\n]+)__/g, '<strong>$1</strong>')
    .replace(/~~([^~\n]+)~~/g, '<del>$1</del>')
    .replace(/(^|[\s（(])\*([^*\n]+)\*(?=$|[\s，。！？、；：,.!?;)）])/g, '$1<em>$2</em>')
    .replace(/(^|[\s（(])_([^_\n]+)_(?=$|[\s，。！？、；：,.!?;)）])/g, '$1<em>$2</em>');
  tokens.forEach((replacement, index) => { html = html.replaceAll(`NFMARKDOWNTOKEN${index}X`, replacement); });
  return highlightHtml(html, query);
}

const blockStart = (lines, index) => {
  const line = lines[index] || '';
  const next = lines[index + 1] || '';
  return /^\s*```/.test(line) || /^\s{0,3}#{1,6}\s+/.test(line) || /^\s*>/.test(line)
    || /^\s*([-+*]|\d+[.)])\s+/.test(line) || /^\s*((\*\s*){3,}|(-\s*){3,}|(_\s*){3,})\s*$/.test(line)
    || (line.includes('|') && /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(next));
};

const tableCells = line => line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map(cell => cell.trim());

/**
 * Small, dependency-free Markdown projection for persisted Assistant and OCR text.
 * Raw HTML is always escaped; only allow-listed http(s)/mailto links become active.
 */
export function renderSafeMarkdown(value, { query = '' } = {}) {
  const lines = String(value ?? '').replaceAll('\r\n', '\n').replaceAll('\r', '\n').split('\n');
  const output = [];
  let index = 0;
  while (index < lines.length) {
    const line = lines[index];
    if (!line.trim()) { index += 1; continue; }
    const fence = line.match(/^\s*```\s*([^\s`]*)\s*$/);
    if (fence) {
      const code = [];
      index += 1;
      while (index < lines.length && !/^\s*```\s*$/.test(lines[index])) code.push(lines[index++]);
      if (index < lines.length) index += 1;
      const language = fence[1] ? ` data-language="${escapeHtml(fence[1])}"` : '';
      output.push(`<pre class="chat-markdown-code"${language}><code>${highlightHtml(escapeHtml(code.join('\n')), query)}</code></pre>`);
      continue;
    }
    const heading = line.match(/^\s{0,3}(#{1,6})\s+(.+?)\s*#*\s*$/);
    if (heading) {
      const level = Math.min(6, heading[1].length + 1);
      output.push(`<h${level}>${inlineMarkdown(heading[2], query)}</h${level}>`);
      index += 1;
      continue;
    }
    if (line.includes('|') && /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(lines[index + 1] || '')) {
      const headers = tableCells(line);
      index += 2;
      const rows = [];
      while (index < lines.length && lines[index].includes('|') && lines[index].trim()) rows.push(tableCells(lines[index++]));
      output.push(`<div class="chat-markdown-table-wrap"><table><thead><tr>${headers.map(cell => `<th>${inlineMarkdown(cell, query)}</th>`).join('')}</tr></thead><tbody>${rows.map(row => `<tr>${headers.map((_, cellIndex) => `<td>${inlineMarkdown(row[cellIndex] || '', query)}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`);
      continue;
    }
    if (/^\s*>/.test(line)) {
      const quote = [];
      while (index < lines.length && /^\s*>/.test(lines[index])) quote.push(lines[index++].replace(/^\s*>\s?/, ''));
      output.push(`<blockquote>${quote.map(item => inlineMarkdown(item, query)).join('<br>')}</blockquote>`);
      continue;
    }
    const listMatch = line.match(/^\s*([-+*]|\d+[.)])\s+(.+)$/);
    if (listMatch) {
      const ordered = /^\d/.test(listMatch[1]);
      const items = [];
      while (index < lines.length) {
        const item = lines[index].match(/^\s*([-+*]|\d+[.)])\s+(.+)$/);
        if (!item || /^\d/.test(item[1]) !== ordered) break;
        const task = item[2].match(/^\[([ xX])\]\s+(.+)$/);
        items.push(task ? `<li class="chat-markdown-task"><input type="checkbox" disabled ${task[1].toLowerCase() === 'x' ? 'checked' : ''}><span>${inlineMarkdown(task[2], query)}</span></li>` : `<li>${inlineMarkdown(item[2], query)}</li>`);
        index += 1;
      }
      output.push(`<${ordered ? 'ol' : 'ul'}>${items.join('')}</${ordered ? 'ol' : 'ul'}>`);
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
