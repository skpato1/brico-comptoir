import { loadEnvFile } from 'node:process';
import { spawn } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { localBackendEnv } from './local-dev-config.mjs';

loadEnvFile(fileURLToPath(new URL('../.env', import.meta.url)));
const source = { ...process.env };
try {
  const tools = JSON.parse(await readFile(new URL('../.local/dev-tools.json', import.meta.url), 'utf8'));
  for (const name of ['JAVA_HOME', 'MAVEN_USER_HOME', 'MAVEN_OPTS']) {
    if (!source[name] && typeof tools[name] === 'string') source[name] = tools[name];
  }
} catch (error) {
  if (error.code !== 'ENOENT') throw error;
}
const env = localBackendEnv(source);
const windows = process.platform === 'win32';
const child = spawn(windows ? 'cmd.exe' : './mvnw',
  windows ? ['/d', '/c', 'mvnw.cmd -B spring-boot:run'] : ['-B', 'spring-boot:run'],
  { cwd: fileURLToPath(new URL('../backend/', import.meta.url)), env, stdio: 'inherit' });
child.on('error', (error) => { console.error(error.message); process.exitCode = 1; });
child.on('exit', (code) => { process.exitCode = code ?? 1; });
