const loopbackHosts = new Set(['localhost', '127.0.0.1', '[::1]']);

function port(value, name, fallback) {
  const text = String(value ?? fallback);
  const number = Number(text);
  if (!/^\d+$/.test(text) || number < 1 || number > 65535)
    throw new Error(`${name} must be a TCP port from 1 to 65535`);
  return text;
}

export function localBackendEnv(source) {
  const postgresPort = port(source.POSTGRES_PORT, 'POSTGRES_PORT', '5432');
  const apiPort = port(source.API_PORT, 'API_PORT', '8080');
  const frontendPort = port(source.FRONTEND_PORT, 'FRONTEND_PORT', '4200');
  const minioPort = port(source.MINIO_API_PORT, 'MINIO_API_PORT', '9000');
  const mailpitPort = port(source.MAILPIT_SMTP_PORT, 'MAILPIT_SMTP_PORT', '1025');
  const database = source.POSTGRES_DB || 'bricocomptoir';
  if (!/^[a-zA-Z][a-zA-Z0-9_]*$/.test(database))
    throw new Error('POSTGRES_DB must be a simple local database name');

  const dbUrl = source.DB_URL || `jdbc:postgresql://localhost:${postgresPort}/${database}`;
  let address;
  try {
    if (!dbUrl.startsWith('jdbc:')) throw new Error();
    address = new URL(dbUrl.slice(5));
  } catch {
    throw new Error('DB_URL must be a local JDBC PostgreSQL URL');
  }
  if (address.protocol !== 'postgresql:' || !loopbackHosts.has(address.hostname.toLowerCase())
      || address.username || address.password || address.search || address.hash
      || !/^\/[a-zA-Z][a-zA-Z0-9_]*$/.test(address.pathname)
      || (address.port || '5432') !== postgresPort)
    throw new Error('Refusing DB_URL outside the local Compose PostgreSQL port');

  for (const name of ['DB_USERNAME', 'DB_PASSWORD', 'DB_MIGRATION_USERNAME',
    'DB_MIGRATION_PASSWORD', 'MINIO_ROOT_PASSWORD']) {
    if (!source[name]) throw new Error(`${name} is missing; run node scripts/init-local-env.mjs`);
  }

  return {
    ...source,
    SPRING_PROFILES_ACTIVE: 'local',
    DB_URL: dbUrl,
    SERVER_PORT: apiPort,
    MEDIA_PROVIDER: 'minio',
    MEDIA_ENDPOINT: `http://localhost:${minioPort}`,
    MEDIA_REGION: 'us-east-1',
    MEDIA_BUCKET: 'bricocomptoir-media',
    MEDIA_ACCESS_KEY: source.MINIO_ROOT_USER || 'brico_local',
    MEDIA_SECRET_KEY: source.MINIO_ROOT_PASSWORD,
    SMTP_HOST: 'localhost',
    SMTP_PORT: mailpitPort,
    SMTP_USERNAME: '',
    SMTP_PASSWORD: '',
    SMTP_AUTH: 'false',
    SMTP_STARTTLS: 'false',
    SMTP_STARTTLS_REQUIRED: 'false',
    MAIL_FROM: 'noreply@bricocomptoir.local',
    APP_PUBLIC_URL: `http://localhost:${frontendPort}`,
    PRIVACY_RETENTION_ENABLED: 'false',
  };
}
