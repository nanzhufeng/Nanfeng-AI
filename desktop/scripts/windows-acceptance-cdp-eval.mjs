// Read the expression from stdin so synthetic credential material never enters
// the helper process command line. Only the JSON result is printed.
const port = Number(process.argv[2]);
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('invalid CDP port');
let expression = '';
for await (const chunk of process.stdin) expression += chunk;
if (!expression.trim()) throw new Error('CDP expression required');

const tabs = await fetch(`http://127.0.0.1:${port}/json/list`, { signal: AbortSignal.timeout(5000) }).then(response => response.json());
const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
if (!tab) throw new Error('isolated Tauri page unavailable');
const socket = new WebSocket(tab.webSocketDebuggerUrl);
try {
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('CDP connection timed out')), 5000);
    socket.addEventListener('open', () => { clearTimeout(timer); resolve(); }, { once: true });
    socket.addEventListener('error', error => { clearTimeout(timer); reject(error); }, { once: true });
  });
  const result = await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('CDP evaluation timed out')), 20000);
    socket.addEventListener('message', event => {
      const message = JSON.parse(event.data);
      if (message.id !== 1) return;
      clearTimeout(timer);
      if (message.error || message.result?.exceptionDetails) reject(new Error('CDP evaluation failed'));
      else resolve(message.result?.result?.value);
    });
    socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: {
      expression, awaitPromise: true, returnByValue: true,
    } }));
  });
  process.stdout.write(JSON.stringify(result));
} finally {
  socket.close();
}
