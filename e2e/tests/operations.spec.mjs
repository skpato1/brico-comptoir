import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { adminApi, fixture, managerPage, addPack, checkout, mail, save, mutate, baseURL } from '../support.mjs';

test('SMTP interrompu : commande durable, reprise sans doublon et SSE après interruption', async ({ page, browser, playwright }) => {
  test.skip(process.env.BRICO_E2E_SKIP_DOCKER_CONTROL === 'true', 'Docker API inaccessible; only read/write application/browser checks can run. CI never sets this option.');
  const admin = await adminApi(playwright), data = await fixture(admin);
  const manager = await managerPage(browser, admin);
  const recipient = `e2e-outage-${randomUUID().slice(0, 8)}@example.invalid`;
  let paused = false;
  const helper = action => {
    const result = spawnSync(process.execPath, ['../scripts/e2e.mjs', action], { stdio: 'inherit' });
    expect(result.status).toBe(0);
  };
  try {
    const cursor = await manager.page.evaluate(() => Object.entries(sessionStorage).find(([key]) => key.startsWith('brico-order-events:')));
    expect(cursor).toBeDefined();
    await manager.context.setOffline(true);
    helper('mail-pause');
    paused = true;
    await addPack(page, data);
    const order = await checkout(page, recipient, '15.000');
    expect((await admin.get(`/api/v1/admin/orders/${order.id}`)).status()).toBe(200);
    await expect.poll(async () => {
      const outbox = await (await admin.get('/api/v1/admin/mail-outbox?size=100')).json();
      return outbox.items.some(entry => entry.key.includes(order.id) && entry.lastError === 'PROVIDER_UNAVAILABLE' && entry.attempts > 0);
    }, { timeout: 15000 }).toBe(true);
    await manager.context.setOffline(false);
    await expect(manager.page.getByText('1 nouvelle(s) commande(s) reçue(s).', { exact: true })).toBeVisible();
    await manager.page.reload();
    await expect(manager.page.getByText('Notifications connectées.', { exact: true })).toBeVisible();
    await expect(manager.page.getByText('1 nouvelle(s) commande(s) reçue(s).', { exact: true })).toHaveCount(0);
    helper('mail-resume');
    paused = false;
    await mail(admin, recipient, order.id);
    const messages = await (await admin.get('http://localhost:18025/api/v1/messages')).json();
    expect(messages.messages.filter(message => message.To.some(to => to.Address === recipient))).toHaveLength(1);
    await save(admin, `/admin/orders/${order.id}/cancel`, {});
  } finally {
    try { if (paused) helper('mail-resume'); }
    finally { await manager.context.close(); await admin.dispose(); }
  }
});

test('Rôles réels : catalogue refusé au gestionnaire commandes, stock et SSE refusés au catalogue', async ({ browser, playwright }) => {
  const admin = await adminApi(playwright);
  const orders = await managerPage(browser, admin);
  const catalog = await managerPage(browser, admin, 'CATALOG_MANAGER');
  const guest = await playwright.request.newContext({ baseURL });
  try {
    expect((await mutate(orders.page.request, '/admin/catalog/categories', { name: 'Refus', slug: 'refus', active: true })).status()).toBe(403);
    expect((await catalog.page.request.get('/api/v1/admin/stock/' + randomUUID())).status()).toBe(403);
    expect((await catalog.page.request.get('/api/v1/admin/order-events/stream')).status()).toBe(403);
    expect((await guest.get('/api/v1/admin/order-events/stream')).status()).toBe(401);
    await expect(catalog.page.getByRole('alert')).toContainText('Cet espace n’est pas accessible');
    await expect(catalog.page.getByRole('link', { name: 'Stocks', exact: true })).toHaveCount(0);
    await expect(orders.page.getByRole('link', { name: 'Produits, photos et catégories', exact: true })).toHaveCount(0);
    // Client-supplied forwarding headers must not bypass the public rate limit.
    for (let i = 0; i < 6; i++) {
      const reset = await mutate(guest, '/auth/password-reset/request', { email: 'absent@example.invalid' }, 'POST', {
        headers: { 'X-Forwarded-For': `198.51.100.${i + 1}` },
      });
      expect(reset.status()).toBe(i === 5 ? 429 : 202);
    }
  } finally { await orders.context.close(); await catalog.context.close(); await guest.dispose(); await admin.dispose(); }
});
