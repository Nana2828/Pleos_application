'use strict';
// Test the real HTTP server on an ephemeral port without interrupting the dev server.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {spawn} = require('node:child_process');
const root = path.resolve(__dirname, '..');
const server = spawn(process.execPath, ['server.cjs'], {
  cwd: root, env: {...process.env, PORT: '0'}, windowsHide: true,
  stdio: ['ignore', 'pipe', 'pipe']
});
let errors = '';
server.stderr.on('data', chunk => { errors += chunk; });
const ready = new Promise((resolve, reject) => {
  let output = '';
  const timer = setTimeout(() => reject(new Error('Server start timed out: '+errors)), 10000);
  server.once('error', error => { clearTimeout(timer); reject(error); });
  server.once('exit', code => { clearTimeout(timer); reject(new Error('Server exited: '+code+' '+errors)); });
  server.stdout.on('data', chunk => {
    output += chunk;
    const match = output.match(/Listening on 0\.0\.0\.0:(\d+)/);
    if (match) { clearTimeout(timer); resolve('http://127.0.0.1:'+match[1]); }
  });
});
(async () => {
  try {
    const base = await ready;
    for (const [name, type] of [
      ['idid-icon-night.svg', 'image/svg+xml'],
      ['idid-icon-night-rounded-1024.png', 'image/png']
    ]) {
      const url = base+'/assets/'+name;
      const response = await fetch(url);
      assert.equal(response.status, 200);
      assert.equal(response.headers.get('content-type'), type);
      assert.deepEqual(Buffer.from(await response.arrayBuffer()), fs.readFileSync(path.join(root, 'assets', name)));
      const head = await fetch(url, {method:'HEAD'});
      assert.equal(head.status, 200);
      assert.equal(head.headers.get('content-type'), type);
      assert.equal((await head.arrayBuffer()).byteLength, 0);
      console.log('PASS: GET / HEAD 200, '+type+', original bytes: '+name);
    }
    for (const file of ['/', '/app.js', '/qr-data.js', '/map.js', '/data.js', '/data/courses/bucheon-cat-91min.json']) {
      assert.equal((await fetch(base+file)).status, 200, file);
    }
    for (const file of ['/server.cjs', '/assets/missing.svg', '/assets/test.js', '/assets/../server.cjs', '/assets/%2e%2e%2fserver.cjs']) {
      assert.equal((await fetch(base+file)).status, 404, file);
    }
    assert.equal((await fetch(base+'/assets/idid-icon-night.svg', {method:'POST'})).status, 404);
    console.log('PASS: existing web resources, missing assets, file restrictions and methods');
  } finally {
    if (server.exitCode === null) {
      const stopped = new Promise(resolve => server.once('exit', resolve));
      server.kill();
      await stopped;
    }
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
