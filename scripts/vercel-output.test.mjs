import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { apiOrigin, deploymentConfig, writeDeployment } from './vercel-output.mjs';

test('accepts an HTTPS backend origin, rejects localhost and credentials without disclosing them', () => {
  assert.equal(apiOrigin(' https://API.example.com/ '), 'https://api.example.com');
  for (const value of ['http://api.example.com', 'https://localhost:8080', 'https://backend',
    'https://host.local', 'https://127.0.0.1', 'https://10.0.0.2', 'https://[::1]',
    'https://name:do-not-print@api.example.com', 'https://api.example.com/api',
    'https://api.example.com?token=do-not-print', 'https://api.example.com/#token']) {
    assert.throws(() => apiOrigin(value), error => !error.message.includes('do-not-print') && error.message.includes('BRICO_API_ORIGIN'));
  }
});

test('routes every API method before filesystem/SPA, preserving /api and disabling CDN caches', () => {
  const { routes } = deploymentConfig('https://api.example.com');
  const index = routes.findIndex(route => route.dest === 'https://api.example.com/api$1');
  const api = routes[index];
  assert.ok(index < routes.findIndex(route => route.handle === 'filesystem'));
  assert.equal(api.methods, undefined); // Includes POST/PUT/DELETE, not just public reads.
  for (const name of ['/api', '/api/v1/auth/login', '/api/v1/admin/products/123/images', '/api/v1/admin/order-events']) {
    assert.equal(name.replace(new RegExp(api.src), api.dest), 'https://api.example.com' + name);
  }
  for (const header of ['Cache-Control', 'CDN-Cache-Control', 'Vercel-CDN-Cache-Control']) assert.equal(api.headers[header], 'no-store');
  assert.equal(api.headers['x-vercel-enable-rewrite-caching'], '0');
});

test('serves direct Angular routes, but never masks API errors, missing assets or POST requests with index.html', () => {
  const spa = deploymentConfig().routes.at(-1);
  for (const name of ['/', '/catalogue', '/packs/123', '/confirmation/123', '/gestion']) assert.match(name, new RegExp(spa.src));
  for (const name of ['/api', '/api/v1/cart', '/missing.js', '/images/missing.jpg', '/_brico/missing']) assert.doesNotMatch(name, new RegExp(spa.src));
  assert.deepEqual(spa.methods, ['GET', 'HEAD']);
});

test('an absent backend returns an explicit unavailable response, never fictitious business data', () => {
  const api = deploymentConfig('').routes.find(route => route.src === '^/api(/.*)?$');
  assert.equal(api.status, 503);
  assert.equal(api.dest, '/_brico/api-unconfigured.json');
  assert.equal(api.headers['Content-Type'], 'application/json; charset=utf-8');
});

test('deploys only compiled browser assets, removes stale output and preserves linking metadata', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'brico-vercel-test-'));
  try {
    const browser = path.join(root, 'frontend/dist/frontend/browser');
    await mkdir(browser, { recursive: true });
    await writeFile(path.join(browser, 'index.html'), '<app-root></app-root>');
    await writeFile(path.join(browser, 'main-EXAMPLE.js'), 'console.log("compiled")');
    await mkdir(path.join(root, '.vercel/output/static'), { recursive: true });
    await writeFile(path.join(root, '.vercel/output/static/stale.js'), 'old');
    await writeFile(path.join(root, '.vercel/project.json'), '{"projectId":"link-must-stay"}');
    await writeFile(path.join(root, '.env'), 'PRIVATE_VALUE=should-not-be-uploaded');
    await writeDeployment(root);
    const output = path.join(root, '.vercel/output');
    assert.deepEqual((await readdir(path.join(output, 'static'))).sort(), ['_brico', 'index.html', 'main-EXAMPLE.js']);
    assert.equal(JSON.parse(await readFile(path.join(output, 'static/_brico/api-unconfigured.json'))).code, 'API_NOT_CONFIGURED');
    assert.equal(JSON.parse(await readFile(path.join(root, '.vercel/project.json'))).projectId, 'link-must-stay');
    await writeDeployment(root, 'https://api.example.com');
    assert.deepEqual((await readdir(path.join(output, 'static'))).sort(), ['index.html', 'main-EXAMPLE.js']);
    assert.equal(JSON.parse(await readFile(path.join(output, 'config.json'))).version, 3);
  } finally { await rm(root, { recursive: true, force: true }); }
});
