export function createSearchResultCache(limit = 12) {
  const pages = new Map();
  const key = args => JSON.stringify([args.query || '', args.category || 'all', args.sortMode || 'default', args.fileType || 'all']);
  return {
    set(args, page) { pages.delete(key(args)); pages.set(key(args), page); while (pages.size > limit) pages.delete(pages.keys().next().value); },
    get(args) {
      if (pages.has(key(args))) return pages.get(key(args));
      if ((args.query || '').trim() || (args.sortMode || 'default') !== 'default') return null;
      const all = pages.get(key({ category: 'all' }));
      if (!all) return null;
      const kind = { text: 'TEXT', image: 'IMAGE', video: 'VIDEO', audio: 'AUDIO', file: 'FILE' }[args.category];
      if (!kind) return null;
      const hits = all.hits.filter(hit => hit.contentKind === kind && (args.category !== 'file' || !args.fileType || args.fileType === 'all' || hit.fileType === args.fileType));
      return { hits, textCount: kind === 'TEXT' ? hits.length : 0, attachmentCount: kind === 'TEXT' ? 0 : hits.length };
    },
    clear() { pages.clear(); },
  };
}
