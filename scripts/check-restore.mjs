import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdir, readFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { loadEnvFile } from 'node:process';
import { setTimeout } from 'node:timers/promises';

// Restore only disposable E2E data to another project. No arbitrary target accepted.
const root = fileURLToPath(new URL('../', import.meta.url));
const envFile = fileURLToPath(new URL('../.local/e2e.env', import.meta.url));
loadEnvFile(envFile);
assert.equal(await readFile(new URL('../.local/e2e-initialized', import.meta.url), 'utf8'), 'bricocomptoir-e2e\n');
const proof = JSON.parse(await readFile(new URL('../.local/e2e-proof.json', import.meta.url), 'utf8'));
assert.match(proof.imageId, /^[0-9a-f-]{36}$/);
const backup = fileURLToPath(new URL('../.local/e2e-backup/', import.meta.url));
await mkdir(backup, { recursive: true });
const restoreProject = 'bricocomptoir-e2e-restore';
const isolatedPorts = { POSTGRES_PORT: '15532', API_PORT: '18180', FRONTEND_PORT: '14300', MINIO_API_PORT: '19100', MINIO_CONSOLE_PORT: '19101', MAILPIT_SMTP_PORT: '11125', MAILPIT_HTTP_PORT: '18125' };
function run(args, env = {}) {
  const result = spawnSync('docker', args, { cwd: root, env: { ...process.env, ...env }, encoding: 'utf8', timeout: 120000 });
  if (result.status !== 0) throw new Error(`Restore drill failed: docker ${args[0]} (credentials omitted)`);
  return result.stdout.trim();
}
const compose = (project, args, env = {}) => run(['compose', '--env-file', envFile, '-p', project,
  '-f', 'compose.yaml', ...(project === restoreProject ? ['-f', 'compose.e2e-restore.yaml'] : []), ...args], { ...(project === restoreProject ? isolatedPorts : {}), ...env });
const minioImage = 'bricocomptoir/minio:RELEASE.2025-10-15T17-29-55Z';
async function mediaArchive(restoring) {
  const volume = restoring ? `${restoreProject}_minio-data` : 'bricocomptoir-e2e_minio-data';
  const id = run(['create', '--label', 'bricocomptoir.e2e-restore-helper=true', '--user', '0', '--entrypoint', 'sh',
    '--mount', `type=volume,source=${volume},target=/data${restoring ? '' : ',readonly'}`,
    minioImage, '-c', restoring ? 'tar -xf /tmp/media.tar -C /data' : 'tar -cf /tmp/media.tar -C /data .']);
  assert.match(id, /^[a-f0-9]{64}$/);
  try {
    if (restoring) run(['cp', backup + 'media.tar', id + ':/tmp/media.tar']);
    const started = spawnSync('docker', ['start', id], { encoding: 'utf8', timeout: 15000 });
    assert.ok(started.status === 0 || started.error?.code === 'ETIMEDOUT', 'Cannot start media archive helper');
    const deadline = Date.now() + 120000;
    let state;
    do {
      state = JSON.parse(run(['inspect', '--format', '{{json .State}}', id]));
      assert.ok(Date.now() < deadline && state.Status !== 'dead', 'Media archive helper did not finish');
      if (state.Status !== 'exited') await setTimeout(1000);
    } while (state.Status !== 'exited');
    assert.equal(state.ExitCode, 0, 'Media archive helper failed');
    // docker cp transfers binary bytes; no host bind mount or shell text pipeline.
    if (!restoring) run(['cp', id + ':/tmp/media.tar', backup + 'media.tar']);
  } finally { run(['rm', '--force', id]); }
}
async function ready(project, services) {
  for (const service of services) {
    const id = compose(project, ['ps', '--all', '--quiet', service]);
    assert.match(id, /^[a-f0-9]{64}$/, `Expected one ${service} container in ${project}`);
    const state = () => JSON.parse(run(['inspect', '--format', '{{json .State}}', id]));
    if (!state().Running) {
      const started = spawnSync('docker', ['start', id], { encoding: 'utf8', timeout: 15000 });
      // Some Desktop proxies lose the start acknowledgement. Never infer readiness
      // from a timed-out command: the exact container must be running and healthy.
      assert.ok(started.status === 0 || started.error?.code === 'ETIMEDOUT', `Cannot start ${project}/${service}`);
      assert.equal(state().Running, true, `Start was not confirmed for ${project}/${service}`);
      if (started.status !== 0) console.log(`Start acknowledgement timed out; checking ${project}/${service} health.`);
    }
    const deadline = Date.now() + 240000;
    while (state().Health?.Status !== 'healthy') {
      assert.ok(state().Running && !state().Paused && Date.now() < deadline, `${project}/${service} is not healthy`);
      await setTimeout(1000);
    }
  }
}
const engine = spawnSync('docker', ['version', '--format', '{{.Server.Version}}'], { encoding: 'utf8', timeout: 15000 });
assert.equal(engine.status, 0, 'Docker Engine API unavailable: no backup source was stopped or modified');
for (const kind of ['container', 'volume']) {
  const existing = run([kind, 'ls', '--filter', `label=com.docker.compose.project=${restoreProject}`, '--format', kind === 'container' ? '{{.Names}}' : '{{.Name}}']);
  assert.equal(existing, '', 'Refusing to reuse an existing restore project');
}
await ready('bricocomptoir-e2e', ['postgres', 'minio', 'mailpit', 'backend', 'frontend']);
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const sourceImage = await fetch(`http://localhost:14200/api/v1/media/${proof.imageId}/detail`);
assert.equal(sourceImage.status, 200, 'Source image unavailable: no backup source was stopped');
const originalImage = hash(Buffer.from(await sourceImage.arrayBuffer()));
let stopped = false, created = false;
try {
  console.log('Quiescing E2E writers and taking PostgreSQL + cold MinIO backups.');
  stopped = true;
  compose('bricocomptoir-e2e', ['stop', 'frontend', 'backend', 'minio']);
  compose('bricocomptoir-e2e', ['exec', '-T', 'postgres', 'pg_dump', '-U', 'postgres', '-d', 'bricocomptoir', '-Fc', '-f', '/tmp/e2e.dump']);
  compose('bricocomptoir-e2e', ['cp', 'postgres:/tmp/e2e.dump', backup + 'database.dump']);
  await mediaArchive(false);
  const countSql = 'SELECT count(*) FROM bricocomptoir.sales_order';
  const originalCount = compose('bricocomptoir-e2e', ['exec', '-T', 'postgres', 'psql', '-U', 'postgres', '-d', 'bricocomptoir', '-Atc', countSql]);
  console.log('Backups ready; creating fresh PostgreSQL and MinIO restore volumes.');
  created = true;
  compose(restoreProject, ['create', '--no-build', 'postgres', 'minio']);
  await ready(restoreProject, ['postgres', 'minio']);
  // Fresh cluster roles, using the same hook as local initialization, without a host mount.
  compose(restoreProject, ['cp', fileURLToPath(new URL('../infra/postgres/10-create-roles.sh', import.meta.url)), 'postgres:/tmp/e2e-create-roles.sh']);
  compose(restoreProject, ['exec', '-T', 'postgres', 'sh', '/tmp/e2e-create-roles.sh']);
  compose(restoreProject, ['cp', backup + 'database.dump', 'postgres:/tmp/e2e.dump']);
  compose(restoreProject, ['exec', '-T', 'postgres', 'pg_restore', '-U', 'postgres', '-d', 'bricocomptoir', '--exit-on-error', '--single-transaction', '/tmp/e2e.dump']);
  compose(restoreProject, ['exec', '-T', 'postgres', 'psql', '-U', 'postgres', '-d', 'bricocomptoir', '-c', 'ANALYZE']);
  assert.equal(compose(restoreProject, ['exec', '-T', 'postgres', 'psql', '-U', 'postgres', '-d', 'bricocomptoir', '-Atc', countSql]), originalCount);
  console.log('PostgreSQL restored with the same order count; restoring MinIO objects.');
  compose(restoreProject, ['stop', 'minio']);
  await mediaArchive(true);
  // Same built application images, no additional source compilation during restoration.
  console.log('MinIO restored; starting the restored applications and checking the API.');
  compose(restoreProject, ['create', '--no-build']);
  await ready(restoreProject, ['postgres', 'minio', 'mailpit', 'backend', 'frontend']);
  // Exercise Nginx -> API -> PostgreSQL/MinIO on the clone network, independently
  // of Desktop host-port relays or localhost IPv6 behavior.
  const curl = (path, file, cookies = false) => compose(restoreProject, ['exec', '-T', 'minio', 'curl',
    '--connect-to', 'localhost:8080:frontend:8080', '--noproxy', '*',
    '--silent', '--show-error', '--output', file, '--write-out', '%{http_code}',
    '--cookie-jar', '/tmp/e2e-cookies', ...(cookies ? ['--cookie', '/tmp/e2e-cookies'] : []), 'http://localhost:8080' + path]);
  assert.equal(curl('/api/v1/health/readiness', '/tmp/e2e-health'), '200');
  assert.equal(curl('/api/v1/auth/csrf', '/tmp/e2e-csrf'), '204');
  const cookies = compose(restoreProject, ['exec', '-T', 'minio', 'cat', '/tmp/e2e-cookies']);
  const csrf = cookies.split('\n').map(line => line.split('\t')).find(fields => fields[5] === 'XSRF-TOKEN');
  assert.ok(csrf, 'Restored CSRF cookie missing');
  // Secrets go through the child environment, never command interpolation or output.
  const login = compose(restoreProject, ['exec', '-T', '-e', 'BRICO_RESTORE_CREDENTIALS', '-e', 'BRICO_RESTORE_CSRF', 'minio', 'sh', '-c',
    'printf "%s" "$BRICO_RESTORE_CREDENTIALS" | curl --connect-to localhost:8080:frontend:8080 --noproxy "*" --silent --show-error --output /tmp/e2e-login --write-out "%{http_code}" --cookie /tmp/e2e-cookies --cookie-jar /tmp/e2e-cookies --header "Content-Type: application/json" --header "X-XSRF-TOKEN: $BRICO_RESTORE_CSRF" --data-binary @- http://localhost:8080/api/v1/auth/login'], {
    BRICO_RESTORE_CREDENTIALS: JSON.stringify({ email: process.env.E2E_ADMIN_EMAIL, password: process.env.E2E_ADMIN_PASSWORD }),
    BRICO_RESTORE_CSRF: decodeURIComponent(csrf[6].trim()),
  });
  assert.equal(login, '200');
  assert.equal(curl('/api/v1/admin/orders/' + proof.orderId, '/tmp/e2e-order.json', true), '200');
  compose(restoreProject, ['cp', 'minio:/tmp/e2e-order.json', backup + 'restored-order.json']);
  const restored = JSON.parse(await readFile(backup + 'restored-order.json', 'utf8'));
  assert.equal(restored.summary.total.amount, proof.total);
  assert.equal(curl('/api/v1/media/' + proof.imageId + '/detail', '/tmp/e2e-image.jpeg', true), '200');
  compose(restoreProject, ['cp', 'minio:/tmp/e2e-image.jpeg', backup + 'restored-image.jpeg']);
  assert.equal(hash(await readFile(backup + 'restored-image.jpeg')), originalImage);
  console.log('Restore verified: order count, authenticated frozen order, Flyway startup, identical MinIO image.');
} finally {
  // Only resources created by this invocation are removed, never the original stack.
  try { if (created) compose(restoreProject, ['down', '--volumes']); }
  finally { if (stopped) await ready('bricocomptoir-e2e', ['postgres', 'minio', 'mailpit', 'backend', 'frontend']); }
}
