'use strict';
// 설치 없이 실행하는 개발 서버: node server.cjs
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');

const HOST = '0.0.0.0';
const PORT = 5173;
// 프로젝트의 웹 파일만 공개합니다.
const files = new Set(['index.html', 'styles.css', 'data.js', 'course-data.js', 'map.js', 'app.js', 'data/courses/bucheon-cat-91min.json']);
const types = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8'
};

const server = http.createServer((request, response) => {
  const pathname = new URL(request.url, 'http://localhost').pathname;
  const file = pathname === '/' ? 'index.html' : pathname.slice(1);
  if (!files.has(file) || !['GET', 'HEAD'].includes(request.method)) {
    response.writeHead(404);
    response.end('Not found');
    return;
  }
  fs.readFile(path.join(__dirname, file), (error, content) => {
    if (error) {
      response.writeHead(500);
      response.end('Unable to read file');
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
  console.log(`Listening on ${HOST}:${PORT}`);
  console.log(`PC: http://localhost:${PORT}`);
  for (const [name, addresses] of Object.entries(os.networkInterfaces())) {
    for (const address of addresses) {
      if (address.family === 'IPv4' && !address.internal) {
        console.log(`${name}: http://${address.address}:${PORT}`);
      }
    }
  }
  console.log('Stop: Ctrl+C');
});
