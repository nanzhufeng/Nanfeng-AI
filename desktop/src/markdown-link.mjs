// Read balanced delimiters rather than truncating a title at its first `]` or
// a Wikipedia-style destination at its first `)`. Never evaluates markup.
export function markdownLinkAt(text, start) {
  if (text[start] !== '[') return null;
  let cursor = start + 1;
  let depth = 1;
  for (; cursor < text.length; cursor += 1) {
    if (text[cursor] === '\\') { cursor += 1; continue; }
    if (text[cursor] === '[') depth += 1;
    if (text[cursor] === ']' && --depth === 0) break;
  }
  if (depth !== 0) return null;
  const label = text.slice(start + 1, cursor);
  cursor += 1;
  while (/\s/.test(text[cursor] || '') && cursor < text.length) cursor += 1;
  if (text[cursor++] !== '(') return null;
  const destinationStart = cursor;
  depth = 1;
  for (; cursor < text.length; cursor += 1) {
    if (text[cursor] === '\\') { cursor += 1; continue; }
    if (text[cursor] === '(') depth += 1;
    if (text[cursor] === ')' && --depth === 0) break;
  }
  if (depth !== 0) return null;
  const destination = text.slice(destinationStart, cursor).trim();
  const match = destination.match(/^(?:<([^<>\n]+)>|([^\s]+?))(?:\s+"[^"]*")?$/);
  return match ? { label, href: match[1] || match[2], end: cursor + 1 } : null;
}

export function replaceMarkdownLinks(text, replace) {
  let result = '';
  let cursor = 0;
  while (cursor < text.length) {
    const start = text.indexOf('[', cursor);
    if (start < 0) break;
    const link = markdownLinkAt(text, start);
    if (!link) { result += text.slice(cursor, start + 1); cursor = start + 1; continue; }
    result += text.slice(cursor, start) + replace(link);
    cursor = link.end;
  }
  return result + text.slice(cursor);
}
