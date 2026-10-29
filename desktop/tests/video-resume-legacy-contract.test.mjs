import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';
// A nested Node runner must not inherit the parent's test-worker marker.
const env={...process.env};delete env.NODE_TEST_CONTEXT;
const output=execFileSync(process.execPath,[
 '--max-old-space-size=2048','--test','--test-reporter=tap','--test-name-pattern=P6-F2-D keeps MP4','tests/chat-first-ui.test.mjs',
],{cwd:new URL('../',import.meta.url),env,encoding:'utf8'});
assert.match(output,/ok \d+ - P6-F2-D keeps MP4/);
assert.doesNotMatch(output,/run\(\) is being called recursively/);
process.stdout.write(output);
