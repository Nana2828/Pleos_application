'use strict';
// 설치 없이 실행하는 개발 서버: node server.cjs
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');

const HOST = '0.0.0.0';
const PORT = Number(process.env.PORT ?? 5173);
if (!Number.isInteger(PORT) || PORT < 0 || PORT > 65535) throw new Error('Invalid PORT');
// 프로젝트의 웹 파일만 공개합니다.
const files = new Set(['index.html', 'styles.css', 'data.js', 'course-data.js', 'map.js', 'qr-data.js', 'app.js', 'data/courses/bucheon-cat-91min.json']);
// assets 바로 아래의 SVG / PNG만 공개합니다. 디렉터리 이동과 다른 파일 형식은 허용하지 않습니다.
const isImageAsset = file => /^assets\/[A-Za-z0-9][A-Za-z0-9._-]*\.(?:svg|png)$/.test(file);
const types = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png'
};

const server = http.createServer((request, response) => {
  const pathname = new URL(request.url, 'http://localhost').pathname;
  const file = pathname === '/' ? 'index.html' : pathname.slice(1);
  if ((!files.has(file) && !isImageAsset(file)) || !['GET', 'HEAD'].includes(request.method)) {
    response.writeHead(404);
    response.end('Not found');
    return;
  }
  fs.readFile(path.join(__dirname, file), (error, content) => {
    if (error) {
      response.writeHead(error.code === 'ENOENT' ? 404 : 500);
      response.end(error.code === 'ENOENT' ? 'Not found' : 'Unable to read file');
      return;
    }
    response.writeHead(200, {
      'Content-Type': types[path.extname(file)],
      'Cache-Control': 'no-store'
    });
    response.end(request.method === 'HEAD' ? undefined : content);
  });
});

server.on('error', error => {
  console.error(`Server could not start (${error.code}): ${error.message}`);
  process.exitCode = 1;
});

server.listen(PORT, HOST, () => {
  const listeningPort = server.address().port;
  console.log(`Listening on ${HOST}:${listeningPort}`);
  console.log(`PC: http://localhost:${listeningPort}`);
  for (const [name, addresses] of Object.entries(os.networkInterfaces())) {
    for (const address of addresses) {
      if (address.family === 'IPv4' && !address.internal) {
        console.log(`${name}: http://${address.address}:${listeningPort}`);
      }
    }
  }
  console.log('Stop: Ctrl+C');
});
