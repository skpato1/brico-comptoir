import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { localBackendEnv } from './local-dev-config.mjs';

const local = {
  POSTGRES_DB: 'bricocomptoir',
  POSTGRES_PORT: '5432',
  DB_USERNAME: 'brico_app',
  DB_PASSWORD: 'local-test-password',
  DB_MIGRATION_USERNAME: 'brico_migrator',
  DB_MIGRATION_PASSWORD: 'local-test-migration-password',
  MINIO_ROOT_USER: 'brico_local',
  MINIO_ROOT_PASSWORD: 'local-test-storage-password',
};

test('local backend uses local services even when inherited variables target production', () => {
  const env = localBackendEnv({ ...local,
    MEDIA_PROVIDER: 's3', MEDIA_ENDPOINT: 'https://storage.example.com',
    SMTP_HOST: 'smtp.example.com', MAIL_FROM: 'shop@example.com',
    APP_PUBLIC_URL: 'https://shop.example.com', SPRING_PROFILES_ACTIVE: 'prod',
    PRIVACY_RETENTION_ENABLED: 'true',
  });
  assert.equal(env.DB_URL, 'jdbc:postgresql://localhost:5432/bricocomptoir');
  assert.equal(env.SPRING_PROFILES_ACTIVE, 'local');
  assert.equal(env.MEDIA_PROVIDER, 'minio');
  assert.equal(env.MEDIA_ENDPOINT, 'http://localhost:9000');
  assert.equal(env.MEDIA_SECRET_KEY, local.MINIO_ROOT_PASSWORD);
  assert.equal(env.SMTP_HOST, 'localhost');
  assert.equal(env.MAIL_FROM, 'noreply@bricocomptoir.local');
  assert.equal(env.APP_PUBLIC_URL, 'http://localhost:4200');
  assert.equal(env.PRIVACY_RETENTION_ENABLED, 'false');
});

test('separate local catalog database remains allowed', () => {
  const env = localBackendEnv({ ...local,
    DB_URL: 'jdbc:postgresql://127.0.0.1:5432/bricocomptoir_prodlocal',
  });
  assert.equal(env.DB_URL, 'jdbc:postgresql://127.0.0.1:5432/bricocomptoir_prodlocal');
});

test('remote or mismatched database targets fail before Spring starts', () => {
  for (const DB_URL of [
    'jdbc:postgresql://prod.example.com:5432/bricocomptoir',
    'jdbc:postgresql://localhost:6432/bricocomptoir',
    'jdbc:postgresql://localhost:5432/bricocomptoir?sslmode=require',
  ]) assert.throws(() => localBackendEnv({ ...local, DB_URL }), /Refusing DB_URL/);
});

test('Angular development proxy rejects remote API origins', () => {
  const root = fileURLToPath(new URL('../', import.meta.url));
  const run = target => spawnSync(process.execPath,
    ['-e', "require('./frontend/proxy.conf.cjs')"],
    { cwd: root, env: { ...process.env, API_PROXY_TARGET: target }, encoding: 'utf8' });
  assert.equal(run('http://localhost:8081').status, 0);
  const remote = run('https://api.example.com');
  assert.notEqual(remote.status, 0);
  assert.match(remote.stderr, /loopback HTTP origin/);
});
