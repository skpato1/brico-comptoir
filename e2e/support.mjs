import { expect } from '@playwright/test';
import { randomUUID, randomBytes } from 'node:crypto';
import { deflateSync } from 'node:zlib';

export const baseURL = 'http://localhost:14200';
export const mailURL = 'http://localhost:18025';
export const password = () => randomBytes(20).toString('hex');
export async function mutate(api, path, data, method = 'POST', extra = {}) {
  expect((await api.get('/api/v1/auth/csrf')).status()).toBe(204);
  const cookie = (await api.storageState()).cookies.find(c => c.name === 'XSRF-TOKEN');
  expect(cookie).toBeDefined();
  const response = await api.fetch('/api/v1' + path, {
    method, ...(data === undefined ? {} : { data }), ...extra,
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(cookie.value), ...extra.headers },
  });
  return response;
}
export async function save(api, path, data, method = 'POST', extra = {}) {
  const response = await mutate(api, path, data, method, extra);
  expect(response.ok(), `${method} ${path}: HTTP ${response.status()}`).toBeTruthy();
  return response.status() === 204 || response.status() === 202 ? null : response.json();
}
export async function login(page, email, secret) {
  await page.goto('/compte');
  await page.getByLabel('Adresse e-mail', { exact: true }).fill(email);
  await page.getByLabel('Mot de passe', { exact: true }).fill(secret);
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page.getByText('Connecté à BricoComptoir : ' + email)).toBeVisible();
}
// A synthetic 640x480 PNG, no real product claims, no extra image dependency.
function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) { crc ^= byte; for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0); }
  return (crc ^ 0xffffffff) >>> 0;
}
export function demoPng(large = false) {
  const chunk = (type, content) => {
    const body = Buffer.concat([Buffer.from(type), content]);
    const length = Buffer.alloc(4), checksum = Buffer.alloc(4);
    length.writeUInt32BE(content.length); checksum.writeUInt32BE(crc32(body));
    return Buffer.concat([length, body, checksum]);
  };
  const height = large ? 640 : 480;
  const header = Buffer.alloc(13); header.writeUInt32BE(640); header.writeUInt32BE(height, 4); header[8] = 8; header[9] = 2;
  const pixels = large ? randomBytes((640 * 3 + 1) * height) : Buffer.alloc((640 * 3 + 1) * height);
  for (let y = 0; y < height; y++) pixels[y * (640 * 3 + 1)] = 0;
  if (!large) for (let y = 0; y < height; y++) for (let x = 0; x < 640; x++) {
    const at = y * (640 * 3 + 1) + 1 + x * 3;
    pixels[at] = x % 256; pixels[at + 1] = y % 256; pixels[at + 2] = 90;
  }
  return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]), chunk('IHDR', header), chunk('IDAT', deflateSync(pixels)), chunk('IEND', Buffer.alloc(0))]);
}
export async function fixture(admin) {
  const code = randomUUID().slice(0, 8).toUpperCase();
  const category = await save(admin, '/admin/catalog/categories', { slug: `e2e-${code.toLowerCase()}`, name: 'DÉMO E2E', active: true, parentId: null });
  const productInput = { categoryId: category.id, brandId: null, name: `DÉMO E2E composant ${code}`, description: 'Article fictif exclusivement destiné au test navigateur.', characteristics: {}, status: 'DRAFT' };
  const product = await save(admin, '/admin/catalog/products', productInput);
  const sku = await save(admin, `/admin/catalog/products/${product.id}/variants`, { sku: `E2E-${code}`, label: 'Composant fictif', unit: 'pièce', options: {}, priceTnd: '5.000', status: 'PUBLISHED' });
  const current = await (await admin.get(`/api/v1/admin/catalog/products/${product.id}`)).json();
  await save(admin, `/admin/catalog/products/${product.id}`, { ...productInput, status: 'PUBLISHED', version: current.version }, 'PUT');
  await save(admin, '/admin/stock/adjustments', { operationId: randomUUID(), variantId: sku.id, delta: 20, reason: 'DÉMO E2E — stock fictif de test' });
  // Exercise the gateway too: its body limit must agree with the server's image contract.
  const photo = demoPng(true);
  expect(photo.length).toBeGreaterThan(1024 * 1024);
  expect((await admin.get('/api/v1/auth/csrf')).status()).toBe(204);
  const csrf = (await admin.storageState()).cookies.find(c => c.name === 'XSRF-TOKEN');
  const uploaded = await admin.post(`${baseURL}/api/v1/admin/catalog/products/${product.id}/images`, {
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(csrf.value) },
    multipart: { files: { name: 'demo-e2e.png', mimeType: 'image/png', buffer: photo } },
  });
  expect(uploaded.status(), 'Valid >1 MiB image through Nginx').toBe(201);
  const images = await uploaded.json();
  const packInput = { code: `e2e-${code.toLowerCase()}`, name: `DÉMO E2E pack ${code}`, slogan: 'Exemple fictif de test', guide: 'Aucune caractéristique technique réelle.', status: 'DRAFT' };
  const pack = await save(admin, '/admin/packs', packInput);
  const variant = await save(admin, `/admin/packs/${pack.id}/variants`, { code: 'base', label: 'Sans outils', priceTnd: '8.000', status: 'PUBLISHED', components: [{ variantId: sku.id, quantity: 2 }] });
  const full = await (await admin.get(`/api/v1/admin/packs/${pack.id}`)).json();
  await save(admin, `/admin/packs/${pack.id}`, { ...packInput, status: 'PUBLISHED', version: full.version }, 'PUT');
  return { product, pack, sku, variant, image: images[0] };
}
export async function addPack(page, data) {
  await page.goto('/packs');
  await page.getByRole('link').filter({ hasText: data.pack.name }).click();
  await expect(page.getByRole('heading', { name: data.pack.name })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Ce qui n’est pas inclus' })).toBeVisible();
  await expect(page.locator('.composition')).toContainText('2 ×');
  await page.getByRole('button', { name: 'Ajouter au panier' }).click();
  await page.getByRole('link', { name: 'Voir mon panier' }).click();
}
export async function checkout(page, email, total) {
  await page.getByRole('link', { name: 'Passer à la livraison' }).click();
  if (email) await page.getByLabel('E-mail pour le suivi').fill(email);
  await page.getByLabel('Nom du destinataire').fill('DÉMO Client navigateur');
  await page.getByLabel('Téléphone tunisien').fill('20123456');
  await page.getByLabel('Rue et complément').fill('1 rue fictive E2E');
  await page.getByLabel('Ville', { exact: true }).fill('Tunis');
  await page.getByLabel('Code postal').fill('1000');
  await page.getByLabel('Gouvernorat').selectOption('TUNIS');
  await page.getByRole('button', { name: 'Vérifier le récapitulatif' }).click();
  await expect(page.getByRole('heading', { name: 'Récapitulatif final' })).toBeVisible();
  await expect(page.locator('.recap .total')).toContainText(`${total} TND`);
  const placed = page.waitForResponse(r => r.url().endsWith('/api/v1/orders') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Confirmer la commande' }).click();
  const response = await placed;
  expect(response.ok()).toBeTruthy();
  const order = await response.json();
  await expect(page).toHaveURL(new RegExp('/confirmation/' + order.id));
  await expect(page.getByRole('heading', { name: 'Merci pour votre commande.' })).toBeVisible();
  await expect(page.locator('.receipt .total')).toContainText(`${total} TND`);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Merci pour votre commande.' })).toBeVisible();
  return order;
}
export async function mail(api, recipient, contains) {
  let found;
  await expect.poll(async () => {
    const body = await (await api.get(mailURL + '/api/v1/messages')).json();
    for (const message of body.messages) {
      if (!message.To.some(to => to.Address === recipient)) continue;
      const detail = await (await api.get(mailURL + '/api/v1/message/' + message.ID)).json();
      if (detail.Text.includes(contains)) { found = detail; return true; }
    }
    return false;
  }, { timeout: 60000, intervals: [500, 1000, 2000] }).toBe(true);
  return found;
}
export async function managerPage(browser, admin, role = 'ORDER_MANAGER') {
  const email = `e2e-${role.toLowerCase()}-${randomUUID().slice(0, 8)}@example.invalid`, secret = password();
  await save(admin, '/admin/internal-accounts', { email, password: secret, roles: [role] });
  const context = await browser.newContext({ baseURL });
  const page = await context.newPage();
  await login(page, email, secret);
  await page.goto('/gestion?section=commandes');
  if (role === 'ORDER_MANAGER') await expect(page.getByText('Notifications connectées.', { exact: true })).toBeVisible();
  return { context, page };
}
export async function adminApi(playwright) {
  // Fixture provisioning calls the local API directly; all browser purchases use Nginx.
  const api = await playwright.request.newContext({ baseURL: 'http://localhost:18080' });
  await save(api, '/auth/login', { email: process.env.E2E_ADMIN_EMAIL, password: process.env.E2E_ADMIN_PASSWORD });
  return api;
}
