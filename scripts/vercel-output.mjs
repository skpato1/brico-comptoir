import { cp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { isIP } from 'node:net';
import path from 'node:path';

export function apiOrigin(value) {
  if (!value?.trim()) return null;
  let url;
  try { url = new URL(value.trim()); } catch { throw new Error('BRICO_API_ORIGIN must be an HTTPS origin.'); }
  const host = url.hostname;
  if (url.protocol !== 'https:' || url.username || url.password || url.pathname !== '/'
      || url.search || url.hash || isIP(host.replace(/^\[|\]$/g, ''))
      || !/^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z][a-z0-9-]*$/i.test(host)
      || /(?:^|\.)(?:localhost|local|internal|invalid|test)$/i.test(host)) {
    // Do not include the invalid value: it may contain accidentally pasted credentials.
    throw new Error('BRICO_API_ORIGIN must be a public HTTPS DNS origin without credentials, path, query or fragment. Localhost is unreachable from Vercel.');
  }
  return url.origin;
}

export function deploymentConfig(value) {
  const origin = apiOrigin(value);
  const api = {
    src: '^/api(/.*)?$',
    dest: origin ? `${origin}/api$1` : '/_brico/api-unconfigured.json',
    headers: {
      'Cache-Control': 'no-store',
      'CDN-Cache-Control': 'no-store',
      'Vercel-CDN-Cache-Control': 'no-store',
      'x-vercel-enable-rewrite-caching': '0',
    },
  };
  if (!origin) {
    api.status = 503;
    api.headers['Content-Type'] = 'application/json; charset=utf-8';
  }
  return {
    version: 3,
    routes: [
      { src: '^/.*$', headers: { 'Referrer-Policy': 'no-referrer', 'X-Content-Type-Options': 'nosniff' }, continue: true },
      api,
      { src: '^/index.html$', headers: { 'Cache-Control': 'no-cache' }, continue: true },
      { handle: 'filesystem' },
      // History navigation must work; a missing script/image or API must retain its error.
      { src: String.raw`^/(?!api(?:/|$)|_brico(?:/|$)|.*\.[^/]*$).*$`, dest: '/index.html', methods: ['GET', 'HEAD'], headers: { 'Cache-Control': 'no-cache' } },
    ],
  };
}

export async function writeDeployment(root, value) {
  const config = deploymentConfig(value);
  const projectRoot = path.resolve(root);
  const browser = path.join(projectRoot, 'frontend', 'dist', 'frontend', 'browser');
  const output = path.join(projectRoot, '.vercel', 'output');
  await readFile(path.join(browser, 'index.html')); // Check the build before replacing its deployment artifact.
  // Only this generated directory is removed, never .vercel/project.json or local environment files.
  if (path.relative(projectRoot, output) !== path.join('.vercel', 'output')) throw new Error('Unexpected output directory.');
  await rm(output, { recursive: true, force: true });
  await mkdir(output, { recursive: true });
  await cp(browser, path.join(output, 'static'), { recursive: true, dereference: false });
  if (!apiOrigin(value)) {
    await mkdir(path.join(output, 'static', '_brico'), { recursive: true });
    await writeFile(path.join(output, 'static', '_brico', 'api-unconfigured.json'), JSON.stringify({
      code: 'API_NOT_CONFIGURED',
      message: 'La boutique est indisponible : son API doit être hébergée et raccordée au déploiement.',
    }) + '\n');
  }
  await writeFile(path.join(output, 'config.json'), JSON.stringify(config, null, 2) + '\n');
}
