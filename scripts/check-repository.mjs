import assert from 'node:assert/strict';
import { readFile, readdir } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
for (const name of ['.env.example', '.env.production.example']) {
  const text = await readFile(new URL('../' + name, import.meta.url), 'utf8');
  for (const line of text.split(/\r?\n/)) {
    if (/^(?:.*PASSWORD|.*SECRET_KEY|MAIL_OUTBOX_KEY|DATA_ARCHIVE_KEY)=/.test(line)) {
      assert.equal(line.split('=').slice(1).join('=').trim(), '', `${name}: secret must remain empty`);
    }
  }
}
const files = await readdir(new URL('../backend/src/main/resources/db/migration/', import.meta.url));
const versions = files.filter(name => /^V\d+__.*\.sql$/.test(name)).map(name => Number(name.match(/^V(\d+)/)[1])).sort((a,b) => a-b);
assert.equal(versions.length, files.filter(name => name.endsWith('.sql')).length, 'Unexpected migration filename');
assert.deepEqual(versions, Array.from({ length: versions.length }, (_, i) => i + 1), 'Migrations must be unique and consecutive');
const git = args => spawnSync('git', ['-c', `safe.directory=${root.replaceAll('\\', '/').replace(/\/$/, '')}`, ...args], { cwd: root, encoding: 'utf8' });
const tracked = git(['ls-files']);
assert.equal(tracked.status, 0, 'Git inventory failed');
assert.equal(tracked.stdout.split(/\r?\n/).filter(name => /(^|\/)\.env($|\.)/.test(name) && !name.endsWith('.example')).length, 0, 'Secret environment file tracked');
assert.equal(git(['check-ignore', '--no-index', '.env', '.local/e2e.env', 'e2e/test-results/junit.xml']).stdout.trim().split(/\r?\n/).length, 3, 'Generated credentials/reports must be ignored');
const diff = git(['diff', '--check']);
assert.equal(diff.status, 0, diff.stdout || diff.stderr);
console.log(`Repository checks passed: ${versions.length} migrations, empty secret templates, generated files ignored, whitespace.`);
