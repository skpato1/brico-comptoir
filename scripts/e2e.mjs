import { randomBytes } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { loadEnvFile } from 'node:process';

const root = fileURLToPath(new URL('../', import.meta.url));
const envFile = fileURLToPath(new URL('../.local/e2e.env', import.meta.url));
const marker = new URL('../.local/e2e-initialized', import.meta.url);
const action = process.argv[2];
const args = ['compose', '--env-file', envFile, '-p', 'bricocomptoir-e2e'];
function docker(extra, environment = {}, quiet = false) {
  const result = spawnSync('docker', [...args, ...extra], {
    cwd: root, env: { ...process.env, ...environment }, stdio: quiet ? 'pipe' : 'inherit', encoding: 'utf8',
    timeout: extra[0] === 'up' ? 900000 : extra[0] === 'run' ? 180000 : 60000,
  });
  if (result.status !== 0) throw new Error(`Docker E2E failed: ${extra[0]} (credentials omitted)`);
  return result.stdout?.trim();
}
if (!['up', 'test', 'down', 'mail-pause', 'mail-resume'].includes(action)) {
  throw new Error('Usage: node scripts/e2e.mjs up|test|down|mail-pause|mail-resume');
}
await mkdir(new URL('../.local/', import.meta.url), { recursive: true });
if (action === 'up') {
  let exists = false;
  try { await readFile(envFile); exists = true; } catch (error) { if (error.code !== 'ENOENT') throw error; }
  if (!exists) {
    // Refuse to adopt a pre-existing stack or volumes without our local marker.
    for (const kind of ['container', 'volume']) {
      const found = spawnSync('docker', [kind, 'ls', '--filter', 'label=com.docker.compose.project=bricocomptoir-e2e', '--format', kind === 'container' ? '{{.Names}}' : '{{.Name}}'], { encoding: 'utf8', timeout: 15000 });
      if (found.status !== 0 || found.stdout.trim()) throw new Error('Existing E2E Docker resources: inspect before initialization.');
    }
    let template = await readFile(new URL('../.env.example', import.meta.url), 'utf8');
    const values = {
      COMPOSE_PROJECT_NAME: 'bricocomptoir-e2e', POSTGRES_PORT: 15432, API_PORT: 18080,
      FRONTEND_PORT: 14200, MINIO_API_PORT: 19000, MINIO_CONSOLE_PORT: 19001,
      MAILPIT_SMTP_PORT: 11025, MAILPIT_HTTP_PORT: 18025,
    };
    for (const name of ['POSTGRES_PASSWORD', 'DB_PASSWORD', 'DB_MIGRATION_PASSWORD', 'MINIO_ROOT_PASSWORD']) values[name] = randomBytes(32).toString('hex');
    for (const name of ['MAIL_OUTBOX_KEY', 'DATA_ARCHIVE_KEY']) values[name] = randomBytes(32).toString('base64');
    for (const [key, value] of Object.entries(values)) template = template.replace(new RegExp(`^${key}=.*$`, 'm'), `${key}=${value}`);
    template += `\nE2E_ADMIN_EMAIL=e2e-admin@example.invalid\nE2E_ADMIN_PASSWORD=${randomBytes(24).toString('hex')}\n`;
    await writeFile(envFile, template, { flag: 'wx', mode: 0o600 });
  }
  loadEnvFile(envFile);
  docker(['config', '--quiet']);
  docker(['up', '--build', '--detach', '--wait', '--wait-timeout', '240']);
  let initialized = false;
  try { initialized = (await readFile(marker, 'utf8')) === 'bricocomptoir-e2e\n'; } catch (error) { if (error.code !== 'ENOENT') throw error; }
  if (!initialized) {
    docker(['run', '--rm', '--no-deps', '-e', 'BRICO_BOOTSTRAP_ADMIN_EMAIL', '-e', 'BRICO_BOOTSTRAP_ADMIN_PASSWORD',
      'backend', '--brico.bootstrap-admin=true', '--server.port=0'], {
      BRICO_BOOTSTRAP_ADMIN_EMAIL: process.env.E2E_ADMIN_EMAIL,
      BRICO_BOOTSTRAP_ADMIN_PASSWORD: process.env.E2E_ADMIN_PASSWORD,
    });
    await writeFile(marker, 'bricocomptoir-e2e\n', { mode: 0o600 });
  }
  console.log('Isolated E2E stack ready: http://localhost:14200 (not the development database).');
} else {
  if (await readFile(marker, 'utf8') !== 'bricocomptoir-e2e\n') throw new Error('Initialize the isolated stack first.');
  if (action === 'test') {
    // Each run gets a clean in-memory login budget; persisted fixtures stay isolated.
    // The production authentication limits are not changed.
    docker(['restart', '--no-deps', 'backend']);
    docker(['up', '--no-build', '--detach', '--wait', '--wait-timeout', '240', 'backend']);
    const cli = fileURLToPath(new URL('../e2e/node_modules/@playwright/test/cli.js', import.meta.url));
    const temporary = process.env.BRICO_E2E_TMPDIR;
    if (temporary) await mkdir(temporary, { recursive: true });
    const result = spawnSync(process.execPath, [cli, 'test'], {
      cwd: fileURLToPath(new URL('../e2e/', import.meta.url)), stdio: 'inherit',
      env: { ...process.env, ...(temporary ? { TEMP: temporary, TMP: temporary, TMPDIR: temporary } : {}) },
    });
    process.exitCode = result.status ?? 1;
  } else if (action === 'down') docker(['down']); // Volumes retained; never delete development data.
  // Freeze the actual SMTP provider: connections time out, the order stays durable.
  else if (action === 'mail-pause') docker(['pause', 'mailpit']);
  else docker(['unpause', 'mailpit']);
}
