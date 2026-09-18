// Presentation only: offsets refer to the unchanged source; unknown languages stay plain.
const supported = new Set('toml ini yaml yml json jsonc js javascript ts typescript jsx tsx java kotlin kt python py bash sh shell zsh sql rust rs go c cpp csharp cs'.split(' '));
const hashComments = new Set('toml ini yaml yml python py bash sh shell zsh'.split(' '));
export function codeSyntaxSpans(source, language) {
  const lang = String(language || '').toLowerCase();
  if (!supported.has(lang) || source.length > 100_000) return [];
  const comments = hashComments.has(lang) ? '#[^\\n]*' : '\\/\\/[^\\n]*|\\/\\*[\\s\\S]*?(?:\\*\\/|$)';
  const section = ['toml','ini'].includes(lang) ? '^[ \\t]*\\[\\[?[^\\]\\n]+\\]\\]?' : '(?!)';
  const pattern = new RegExp(`("(?:\\\\.|[^"\\\\])*"|'(?:\\\\.|[^'\\\\])*')|(${comments})|(${section})|(\\b[A-Za-z_][\\w.-]*(?=[ \\t]*[=:]))|(\\b(?:0x[0-9a-fA-F]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\b)|(\\b(?:true|false|null|None|True|False|const|let|var|val|fun|function|class|def|return|if|else|for|while|import|from|export|async|await|try|catch|throw|new|public|private|SELECT|FROM|WHERE|INSERT|UPDATE|CREATE|TABLE)\\b)`, 'gm');
  return [...source.matchAll(pattern)].map(match => ({
    start: match.index, end: match.index + match[0].length,
    kind: match[1] ? (/^[ \t]*:/.test(source.slice(match.index + match[0].length)) ? 'key' : 'string')
      : match[2] ? 'comment' : match[3] ? 'section' : match[4] ? 'key' : match[5] ? 'number' : 'keyword',
  }));
}
export function renderCodeSyntax(source, language, escape) {
  let result = '', offset = 0;
  for (const span of codeSyntaxSpans(source, language)) {
    result += escape(source.slice(offset, span.start)) + `<span class="code-syntax-${span.kind}">${escape(source.slice(span.start, span.end))}</span>`;
    offset = span.end;
  }
  return result + escape(source.slice(offset));
}
