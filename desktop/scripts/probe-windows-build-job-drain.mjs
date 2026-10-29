import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';

const mode=process.argv[2];
assert.ok(['finite','persistent','failed-root'].includes(mode));
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const childCode="console.log('OWNED_BUILD_CHILD_READY');"+(mode==='persistent'?'setInterval(()=>{},1000)':'setTimeout(()=>process.exit(0),2500)');
const child=spawn(process.execPath,['--max-old-space-size=2048','-e',childCode],{stdio:['ignore','pipe','pipe'],detached:true,windowsHide:true});
let ready=false;
child.stdout.on('data',data=>{
 if(ready||!data.toString().includes('OWNED_BUILD_CHILD_READY'))return;
 ready=true;console.log(JSON.stringify({probe:'owned-build-drain',mode,childPid:child.pid}));
 child.stdout.destroy();child.stderr.destroy();child.unref();
 setTimeout(()=>process.exit(mode==='failed-root'?42:0),100);
});
child.on('error',()=>process.exit(3));
child.on('exit',code=>{if(!ready)process.exit(code||4)});
