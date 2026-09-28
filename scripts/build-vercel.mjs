import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { apiOrigin, writeDeployment } from './vercel-output.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const origin = apiOrigin(process.env.BRICO_API_ORIGIN);
const build = spawnSync(process.execPath, [path.join(root, 'frontend/node_modules/@angular/cli/bin/ng.js'), 'build'], {
  cwd: path.join(root, 'frontend'), stdio: 'inherit', env: process.env,
});
if (build.error) throw build.error;
if (build.status !== 0) process.exit(build.status ?? 1);
await writeDeployment(root, origin);
console.log('Vercel Build Output API v3: Angular files and SPA/API routes generated.');
if (!origin) console.warn('BRICO_API_ORIGIN is missing: the interface can load, but every API request returns 503. The shop is not operational.');
