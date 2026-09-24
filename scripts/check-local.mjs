import assert from 'node:assert/strict';
import { loadEnvFile } from 'node:process';
import { fileURLToPath } from 'node:url';

loadEnvFile(fileURLToPath(new URL('../.env', import.meta.url)));
const api = `http://localhost:${process.env.API_PORT || '8080'}`;
const frontend = `http://localhost:${process.env.FRONTEND_PORT || '4200'}`;

async function check(url, expectedStatus, options = {}) {
  const response = await fetch(url, { ...options, signal: AbortSignal.timeout(10000) });
  assert.equal(response.status, expectedStatus, `${options.method || 'GET'} ${url}`);
  console.log(`OK ${response.status} ${options.method || 'GET'} ${url}`);
  return response;
}

for (const url of [api + '/api/v1/health', api + '/api/v1/health/readiness',
  api + '/api/v1/health/liveness', frontend + '/api/v1/health']) {
  assert.equal((await (await check(url, 200)).json()).status, 'UP');
}
assert.match(await (await check(frontend, 200)).text(), /<app-root/);
await check(api + '/api/v1/orders', 401);
await check(api + '/api/v1/health', 403, { method: 'POST' });
await check(`http://localhost:${process.env.MINIO_API_PORT || '9000'}/minio/health/live`, 200);
await check(`http://localhost:${process.env.MAILPIT_HTTP_PORT || '8025'}/readyz`, 200);
console.log('Socle local disponible : frontend, API, PostgreSQL, MinIO et Mailpit.');
