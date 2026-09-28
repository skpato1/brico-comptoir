import assert from 'node:assert/strict';
import { readFile, access } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { sensitiveRules, privatePath } from './release-checks.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const git = args => {
  const result = spawnSync('git', ['-c', `safe.directory=${root.replaceAll('\\', '/').replace(/\/$/, '')}`, ...args],
    { cwd: root, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 });
  assert.equal(result.status, 0, 'Git inspection failed');
  return result.stdout;
};
const files = [...new Set(git(['ls-files', '-c', '-o', '--exclude-standard', '-z']).split('\0').filter(Boolean))];
const secrets = [];
for (const name of ['.env', '.local/e2e.env']) {
  try {
    for (const line of (await readFile(path.join(root, name), 'utf8')).split(/\r?\n/)) {
      const match = line.match(/^[A-Z0-9_]*(?:PASSWORD|SECRET_KEY|OUTBOX_KEY|ARCHIVE_KEY)=(.+)$/);
      if (match) secrets.push(match[1].replace(/^(['"])(.*)\1$/, '$2'));
    }
  } catch (error) { if (error.code !== 'ENOENT') throw error; }
}
let links = 0, bytes = 0;
const external = new Set();
const problems = [];
for (const name of files) {
  if (privatePath(name)) { problems.push(`${name}: private/generated path`); continue; }
  const buffer = await readFile(path.join(root, name));
  bytes += buffer.length;
  if (buffer.length > 10 * 1024 * 1024) problems.push(`${name}: file exceeds 10 MiB`);
  const text = buffer.toString('utf8');
  for (const rule of sensitiveRules(text, secrets)) problems.push(`${name}: ${rule}`);
  if (!name.endsWith('.md')) continue;
  for (const match of text.matchAll(/\[[^\]]*\]\(([^)\n]+)\)/g)) {
    let target = match[1].trim().replace(/^<|>$/g, '');
    if (/^https?:\/\//.test(target)) { external.add(target); continue; }
    if (/^(?:mailto:|app:|codex:)/.test(target)) continue;
    target = decodeURIComponent(target.split('#')[0]);
    if (!target) continue;
    const absolute = path.resolve(path.dirname(path.join(root, name)), target);
    const relative = path.relative(root, absolute).replaceAll('\\', '/');
    if (!files.includes(relative)) { problems.push(`${name}: link is outside the published tree (${target})`); continue; }
    try { await access(absolute); links++; } catch { problems.push(`${name}: missing link (${target})`); }
  }
}
for (const rule of sensitiveRules(git(['log', '--all', '-p', '--format=COMMIT:%H']), secrets)) problems.push(`Git history: ${rule}`);
assert.equal(problems.length, 0, problems.join('\n'));
console.log(`Release checks passed: ${files.length} files (${bytes} bytes), ${links} local documentation links, targeted credential/history scan.`);
console.log(`${external.size} external documentation links require a network check; this script does not claim to verify them.`);
if (process.argv.includes('--list-external')) console.log([...external].sort().join('\n'));
