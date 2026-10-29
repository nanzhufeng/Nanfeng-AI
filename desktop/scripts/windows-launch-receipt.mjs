/** PowerShell emits one newline-terminated ASCII-escaped launch JSON record. */
export function completeLaunchReceipt(buffer) {
  if(buffer.length>65536)throw new Error('Launch output exceeds the receipt limit');
  const end=buffer.lastIndexOf('\n');
  if(end<0)return null;
  const line=buffer.slice(0,end).split(/\r?\n/).find(value=>value.trim().startsWith('{') && value.includes('"pid"') && value.includes('"launchReceipt"'));
  if(!line)return null;
  const record=JSON.parse(line);
  if(!Number.isSafeInteger(record.pid) || record.pid<=0 || typeof record.launchReceipt!=='string' || !record.launchReceipt)throw new Error('Invalid launch receipt envelope');
  return record;
}
