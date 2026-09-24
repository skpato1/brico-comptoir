import { randomBytes } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';

const root = new URL('../', import.meta.url);
const template = await readFile(new URL('.env.example', root), 'utf8');
const secretNames = ['POSTGRES_PASSWORD', 'DB_PASSWORD', 'DB_MIGRATION_PASSWORD', 'MINIO_ROOT_PASSWORD'];
let content = template;
for (const name of secretNames) {
  content = content.replace(new RegExp(`^${name}=$`, 'm'), `${name}=${randomBytes(32).toString('hex')}`);
}
try {
  await writeFile(new URL('.env', root), content, { flag: 'wx', mode: 0o600 });
  console.log('.env créé avec des secrets locaux aléatoires. Ne pas le committer.');
} catch (error) {
  if (error.code !== 'EEXIST') throw error;
  console.log('.env existe déjà : aucune valeur modifiée.');
}
