const cautionPrefix = /^(?:(?:主要|潜在|关键|重要|安全|特别|核心)?(?:风险|注意|警告|限制|提醒)|避免|不要|不建议|谨慎|warning\b|caution\b|risks?\b|limitations?\b)/iu;
export function isCautionHeading(value) {
  return cautionPrefix.test(String(value || '').replaceAll('**','').replaceAll('__','').trim().replace(/^(?:[一二三四五六七八九十]+|[0-9]+)[、.）)]\s*/u,''));
}
export function createReplyEmphasisBudget() {
  let important=0, caution=0, characters=0;
  return { cautionSection:false, take(value) {
    const text=String(value || '').trim();
    if(text.length<2 || text.length>40 || !/\p{L}/u.test(text) || /[`\[\]<>$&\n]/u.test(text) || text.includes('NFMARKDOWNTOKEN') || characters+text.length>96) return null;
    const tone=this.cautionSection || isCautionHeading(text) ? 'caution' : 'important';
    if(tone==='important') {if(important>=2)return null;important++;} else {if(caution>=1)return null;caution++;}
    characters+=text.length;
    return tone;
  }};
}
