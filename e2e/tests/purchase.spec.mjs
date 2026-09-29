import { test, expect } from '@playwright/test';
import { writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { adminApi, fixture, addPack, checkout, mail, managerPage, save, login, password } from '../support.mjs';

for (const connected of [false, true]) {
  test(connected ? 'Client : pack + produit commun, réservation cumulée et expédition' : 'Invité : pack, confirmation, email, notification et annulation', async ({ page, browser, playwright }) => {
    const admin = await adminApi(playwright);
    const data = await fixture(admin);
    const manager = await managerPage(browser, admin);
    const email = `e2e-buyer-${randomUUID().slice(0, 8)}@example.invalid`;
    try {
      // Capture real browser errors and media requests, never mock the API.
      const errors = [], mediaRequests = [];
      page.on('pageerror', error => errors.push(error.message));
      page.on('request', request => { if (request.url().includes('/api/v1/media/')) mediaRequests.push(request.url()); });
      if (connected) {
        // Fill a guest cart, then verify its persisted, idempotent recovery on login.
        await addPack(page, data);
        const secret = password();
        await save(page.request, '/auth/register', { email, password: secret });
        await login(page, email, secret);
        await page.goto('/panier');
        await expect(page.getByText('Panier enregistré sur votre compte.')).toBeVisible();
        await expect(page.locator('app-cart .items > li')).toHaveCount(1);
        const persisted = await (await page.request.get('/api/v1/cart')).json();
        expect(persisted.items).toHaveLength(1);
        expect(persisted.items[0].quantity).toBe(1);
      } else await addPack(page, data);

      // Cards request only compressed derivatives; the detail uses srcset/lazy loading.
      await page.goto('/catalogue');
      await page.getByLabel('Produit ou référence').fill(data.product.name);
      await page.locator('form.filters').getByRole('button', { name: 'Rechercher', exact: true }).click();
      const image = page.getByRole('img', { name: data.product.name });
      await image.scrollIntoViewIfNeeded();
      await expect.poll(() => image.evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true);
      await expect(image).toHaveAttribute('loading', 'lazy');
      await expect(image).toHaveAttribute('srcset', /\/card 360w.*\/detail 640w/);
      await page.getByRole('link').filter({ hasText: data.product.name }).click();
      await expect(page).toHaveURL(`/produits/${data.product.id}`);
      await expect(page.getByRole('heading', { name: data.product.name, exact: true })).toBeVisible();
      const detailImage = page.locator('app-detail').getByRole('img', { name: data.product.name });
      await detailImage.scrollIntoViewIfNeeded();
      await expect.poll(() => detailImage.evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true);
      expect(mediaRequests.filter(url => /\/media\/[0-9a-f-]+\//.test(url)).every(url => /\/(card|detail)$/.test(url))).toBe(true);
      expect((await page.request.get(`/api/v1/media/${data.image.id}/original`)).status()).toBeGreaterThanOrEqual(400);
      expect((await page.request.get('http://localhost:19000/bricocomptoir-media')).status()).toBe(403);
      if (connected) {
        // A full-page navigation cancels pending requests, including the CSRF
        // lookup before PUT. Wait for the real persisted cart before reloading.
        const savedCart = page.waitForResponse(response =>
          new URL(response.url()).pathname === '/api/v1/cart' && response.request().method() === 'PUT');
        await page.getByRole('button', { name: 'Ajouter au panier' }).click();
        const response = await savedCart;
        expect(response.status()).toBe(200);
        expect((await response.json()).items).toHaveLength(2);
      }
      await page.goto('/panier');
      await expect(page.locator('app-cart .items > li')).toHaveCount(connected ? 2 : 1);
      const order = await checkout(page, connected ? null : email, connected ? '20.000' : '15.000');
      expect(order.summary.items).toHaveLength(connected ? 2 : 1);
      expect(order.paymentMethod).toBe('CASH_ON_DELIVERY');
      expect(order.summary.items.flatMap(line => line.components).reduce((sum, component) => sum + component.quantity, 0)).toBe(connected ? 3 : 2);
      expect(await (await admin.get(`/api/v1/admin/stock/${data.sku.id}`)).json()).toMatchObject({ onHand: 20, reserved: connected ? 3 : 2 });

      await expect(manager.page.getByText('1 nouvelle(s) commande(s) reçue(s).', { exact: true })).toBeVisible();
      await manager.page.getByRole('link', { name: 'Consulter les commandes' }).click();
      await manager.page.getByRole('button', { name: 'Rechercher les commandes' }).click();
      const row = manager.page.getByRole('row').filter({ hasText: order.id });
      await expect(row).toBeVisible();
      await row.getByRole('button', { name: 'Ouvrir', exact: true }).click();
      await expect(manager.page.locator('.admin-detail')).toContainText(data.pack.name);
      if (connected) await expect(manager.page.locator('.admin-detail')).toContainText(data.product.name);
      const confirmationMail = await mail(admin, email, order.id);
      expect(confirmationMail.Text).toContain(order.summary.total.amount);

      if (connected) {
        await manager.page.getByRole('button', { name: 'Préparer', exact: true }).click();
        await expect(manager.page.getByText('Opération enregistrée.', { exact: true })).toBeVisible();
        await manager.page.locator('app-admin-orders').getByRole('combobox').selectOption('PREPARING');
        await manager.page.getByRole('button', { name: 'Rechercher les commandes' }).click();
        await manager.page.getByRole('row').filter({ hasText: order.id }).getByRole('button', { name: 'Ouvrir', exact: true }).click();
        await expect(manager.page.getByRole('button', { name: 'Expédier et sortir le stock' })).toBeVisible();
        await manager.page.getByRole('button', { name: 'Expédier et sortir le stock' }).click();
        await expect(manager.page.getByText('Opération enregistrée.', { exact: true })).toBeVisible();
        await manager.page.locator('app-admin-orders').getByRole('combobox').selectOption('SHIPPED');
        await manager.page.getByRole('button', { name: 'Rechercher les commandes' }).click();
        await manager.page.getByRole('row').filter({ hasText: order.id }).getByRole('button', { name: 'Ouvrir', exact: true }).click();
        await expect(manager.page.getByRole('button', { name: 'Marquer livrée' })).toBeVisible();
        expect(await (await admin.get(`/api/v1/admin/stock/${data.sku.id}`)).json()).toMatchObject({ onHand: 17, reserved: 0 });
        await mail(admin, email, `Commande ${order.id} : expédiée.`);
        expect((await save(manager.page.request, `/admin/orders/${order.id}/deliver`, {})).status).toBe('DELIVERED');
      } else {
        await manager.page.getByRole('button', { name: 'Annuler et libérer le stock' }).click();
        await expect(manager.page.getByText('Opération enregistrée.', { exact: true })).toBeVisible();
        await manager.page.locator('app-admin-orders').getByRole('combobox').selectOption('CANCELLED');
        await manager.page.getByRole('button', { name: 'Rechercher les commandes' }).click();
        await expect(manager.page.getByRole('row').filter({ hasText: order.id })).toContainText('Annulée');
        expect(await (await admin.get(`/api/v1/admin/stock/${data.sku.id}`)).json()).toMatchObject({ onHand: 20, reserved: 0 });
        await mail(admin, email, `Commande ${order.id} : annulée.`);
      }
      // Keep identifiers only for the automated restore drill; all contacts are synthetic.
      await writeFile(new URL('../../.local/e2e-proof.json', import.meta.url), JSON.stringify({ orderId: order.id, productId: data.product.id, imageId: data.image.id, total: order.summary.total.amount }));
      expect(errors).toEqual([]);
    } finally { await manager.context.close(); await admin.dispose(); }
  });
}
