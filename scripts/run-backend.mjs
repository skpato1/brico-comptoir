import { loadEnvFile } from 'node:process';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

loadEnvFile(fileURLToPath(new URL('../.env', import.meta.url)));
const env = {
  ...process.env,
  SPRING_PROFILES_ACTIVE: 'local',
  DB_URL: process.env.DB_URL || `jdbc:postgresql://localhost:${process.env.POSTGRES_PORT || '5432'}/${process.env.POSTGRES_DB || 'bricocomptoir'}`,
  SERVER_PORT: process.env.API_PORT || '8080',
};
const windows = process.platform === 'win32';
const child = spawn(windows ? 'cmd.exe' : './mvnw',
  windows ? ['/d', '/c', 'mvnw.cmd -B spring-boot:run'] : ['-B', 'spring-boot:run'],
  { cwd: fileURLToPath(new URL('../backend/', import.meta.url)), env, stdio: 'inherit' });
child.on('error', (error) => { console.error(error.message); process.exitCode = 1; });
child.on('exit', (code) => { process.exitCode = code ?? 1; });
