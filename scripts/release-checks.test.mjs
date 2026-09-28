import { test } from 'node:test';
import assert from 'node:assert/strict';
import { privatePath, sensitiveRules } from './release-checks.mjs';

test('rejects generated data, real environment files and private keys, retaining examples and migrations', () => {
  for (const name of ['.env', 'nested/.env.production', '.local/e2e.env', 'tls/server.key',
    'backups/customer.dump', 'e2e/test-results/junit.xml', 'frontend/node_modules/package.json',
    '.vercel/project.json', 'frontend/.vercel/.env.production.local']) assert.equal(privatePath(name), true, name);
  for (const name of ['.env.example', '.env.production.example', 'backend/src/main/resources/db/migration/V12__notification_privacy.sql']) assert.equal(privatePath(name), false, name);
});
test('detects credentials without returning their values', () => {
  const secret = 'synthetic-local-' + 'secret-for-test';
  assert.deepEqual(sensitiveRules(`value=${secret}`, [secret]), ['LOCAL_SECRET_VALUE']);
  assert.deepEqual(sensitiveRules('ghp_' + 'a'.repeat(36)), ['GITHUB_TOKEN']);
  assert.deepEqual(sensitiveRules(['-----BEGIN', 'PRIVATE KEY-----'].join(' ')), ['PRIVATE_KEY']);
  assert.deepEqual(sensitiveRules('AKIA' + 'B'.repeat(16)), ['AWS_ACCESS_KEY']);
  assert.deepEqual(sensitiveRules('postgresql://' + 'name:secret@host/db'), ['CREDENTIAL_URL']);
});
test('does not treat environment variable references and empty examples as credentials', () => {
  assert.deepEqual(sensitiveRules('DB_PASSWORD=\npassword: ${DB_PASSWORD}\nSMTP_PASSWORD=\n'), []);
});
