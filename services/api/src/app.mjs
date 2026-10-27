import { createServer } from 'node:http';

export function createApp() {
  return createServer((request, response) => {
    response.setHeader('Content-Type', 'application/json; charset=utf-8');
    response.setHeader('Cache-Control', 'no-store');
    const url = new URL(request.url ?? '/', 'http://localhost');
    if (request.method === 'GET' && url.pathname === '/health') {
      response.writeHead(200).end(JSON.stringify({ status: 'ok', service: 'plotus-api', version: '0.1.0' }));
      return;
    }
    if (url.pathname.startsWith('/v1/')) {
      response.writeHead(501).end(JSON.stringify({ error: { code: 'NOT_IMPLEMENTED', message: 'This API is reserved for the next milestone.' } }));
      return;
    }
    response.writeHead(404).end(JSON.stringify({ error: { code: 'NOT_FOUND', message: 'Route not found.' } }));
  });
}
