/** PWA remains a capability-gated fallback: no implicit folder authority and no native app-data assumption. */
export async function pickPwaExchangeFile() {
  if ('showOpenFilePicker' in window) {
    const [handle] = await window.showOpenFilePicker({ multiple: false, types: [{ description: 'Nanfeng AI exchange', accept: { 'application/zip': ['.nfai-exchange'] } }] });
    return handle.getFile();
  }
  return new Promise(resolve => { const input = document.createElement('input'); input.type = 'file'; input.accept = '.nfai-exchange,application/zip'; input.onchange = () => resolve(input.files?.[0] ?? null); input.click(); });
}
export async function pwaPrivateWorkspace() { return navigator.storage?.getDirectory ? navigator.storage.getDirectory() : null; }
export function downloadPwaFallback(bytes, name) { const url = URL.createObjectURL(new Blob([bytes], { type: 'application/zip' })); const anchor = document.createElement('a'); anchor.href = url; anchor.download = name; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 0); }
