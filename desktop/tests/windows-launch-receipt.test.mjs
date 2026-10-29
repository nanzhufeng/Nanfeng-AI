import test from 'node:test';
import assert from 'node:assert/strict';
import { completeLaunchReceipt } from '../scripts/windows-launch-receipt.mjs';

test('a long escaped receipt split at the observed 1024-byte boundary waits for the complete record',()=>{
 const expected={launchReceipt:'C:\\'+ 'long-synthetic-path\\'.repeat(80)+'launch.json',pid:45764};
 const output='PowerShell startup\r\n'+JSON.stringify(expected)+'\r\n';
 assert.ok(output.length>1024);
 for(let end=1;end<output.length-1;end++)assert.equal(completeLaunchReceipt(output.slice(0,end)),null);
 assert.deepEqual(completeLaunchReceipt(output),expected);
});
test('incomplete PID-bearing output is never treated as an owned process or a successful launch',()=>{
 assert.equal(completeLaunchReceipt('{"launchReceipt":"path","pid":45764'),null);
 assert.equal(completeLaunchReceipt('unrelated output\n'),null);
 assert.throws(()=>completeLaunchReceipt('{"launchReceipt":"path","pid":-1}\n'),/Invalid/);
 assert.throws(()=>completeLaunchReceipt('x'.repeat(65537)),/limit/);
});
