import { randomBytes } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';

const root = new URL('../', import.meta.url);
const template = await readFile(new URL('.env.example', root), 'utf8');
const secretNames = ['POSTGRES_PASSWORD', 'DB_PASSWORD', 'DB_MIGRATION_PASSWORD', 'MINIO_ROOT_PASSWORD'];
let content = template;
for (const name of secretNames) {
  content = content.replace(new RegExp(`^${name}=$`, 'm'), `${name}=${randomBytes(32).toString('hex')}`);
}
content = content.replace(/^MAIL_OUTBOX_KEY=$/m, `MAIL_OUTBOX_KEY=${randomBytes(32).toString('base64')}`);
content = content.replace(/^DATA_ARCHIVE_KEY=$/m, `DATA_ARCHIVE_KEY=${randomBytes(32).toString('base64')}`);
try {
  await writeFile(new URL('.env', root), content, { flag: 'wx', mode: 0o600 });
  console.log('.env créé avec des secrets locaux aléatoires. Ne pas le committer.');
} catch (error) {
  if (error.code !== 'EEXIST') throw error;
  const envFile = new URL('.env', root);
  let existing = await readFile(envFile, 'utf8');
  let changed = false;
  for (const name of ['MAIL_OUTBOX_KEY', 'DATA_ARCHIVE_KEY']) {
    if (!new RegExp(`^${name}=.+$`, 'm').test(existing)) {
      const key = `${name}=${randomBytes(32).toString('base64')}`;
      existing = new RegExp(`^${name}=$`, 'm').test(existing)
        ? existing.replace(new RegExp(`^${name}=$`, 'm'), key) : `${existing.trimEnd()}\n${key}\n`;
      changed = true;
    }
  }
  if (changed) await writeFile(envFile, existing, { mode: 0o600 });
  console.log(changed ? 'Clés locales manquantes ajoutées ; secrets existants conservés.' : '.env existe déjà : aucune valeur modifiée.');
}
