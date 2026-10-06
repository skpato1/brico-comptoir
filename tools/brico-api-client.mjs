import { readFile } from 'node:fs/promises';

function readVariables(text) {
  const values = {};
  for (const line of text.split(/\r?\n/)) {
    const match = line.match(/^\s*([A-Z_]+)=(.*)$/);
    if (!match) continue;
    let value = match[2].trim();
    if (value.startsWith('"') && value.endsWith('"')) value = value.slice(1, -1);
    values[match[1]] = value;
  }
  return values;
}

export class BricoApi {
  constructor(baseUrl) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.cookies = new Map();
  }

  async request(path, { method = 'GET', body, form, timeout = 45000 } = {}) {
    const headers = { accept: 'application/json' };
    if (this.cookies.size) headers.cookie = [...this.cookies].map(([key, value]) => `${key}=${value}`).join('; ');
    if (!['GET', 'HEAD'].includes(method)) {
      const csrf = this.cookies.get('XSRF-TOKEN');
      if (csrf) headers['X-XSRF-TOKEN'] = decodeURIComponent(csrf);
    }
    if (body !== undefined) headers['content-type'] = 'application/json';
    const response = await fetch(`${this.baseUrl}${path}`, {
      method, headers, body: body === undefined ? form : JSON.stringify(body),
      signal: AbortSignal.timeout(timeout),
    });
    for (const cookie of response.headers.getSetCookie()) {
      const [pair] = cookie.split(';');
      const equal = pair.indexOf('=');
      if (equal <= 0) continue;
      const name = pair.slice(0, equal), value = pair.slice(equal + 1);
      if (value) this.cookies.set(name, value); else this.cookies.delete(name);
    }
    const text = await response.text();
    if (!response.ok) {
      const error = new Error(`HTTP ${response.status} ${method} ${path}: ${text.slice(0, 300).replace(/\s+/g, ' ')}`);
      error.status = response.status;
      throw error;
    }
    return text && response.headers.get('content-type')?.includes('json') ? JSON.parse(text) : text;
  }

  async login(credentialsFile) {
    const { ADMIN_EMAIL: email, ADMIN_PASSWORD: password } =
      readVariables(await readFile(credentialsFile, 'utf8'));
    if (!email || !password) throw new Error('Local admin credentials are unavailable');
    await this.request('/api/v1/auth/csrf');
    const user = await this.request('/api/v1/auth/login', { method: 'POST', body: { email, password } });
    if (!user.roles?.some(role => ['ADMIN', 'CATALOG_MANAGER'].includes(role)))
      throw new Error('Authenticated account lacks catalogue permission');
    return { roles: user.roles };
  }
}
