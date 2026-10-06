import { readFile, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { BricoApi } from './brico-api-client.mjs';
import { sellingPrice } from './sqes-brico-catalogue.mjs';

const directory = resolve(process.argv[2] || '.local/sqes-brico-export');
const baseUrl = process.argv[3] || 'https://brico-comptoir.vercel.app';
const dryRun = process.argv.includes('--dry-run');
const [snapshot, manifest, drift, journal] = await Promise.all([
  readFile(resolve('.local/sqes-import/catalogue-facts.json'), 'utf8').then(JSON.parse),
  readFile(join(directory, 'catalogue.json'), 'utf8').then(JSON.parse),
  readFile(join(directory, 'source-drift.json'), 'utf8').then(JSON.parse),
  readFile(join(directory, 'import-journal.json'), 'utf8').then(JSON.parse),
]);
if (manifest.sourceSnapshotAt !== snapshot.completedAt || journal.sourceSnapshotAt !== snapshot.completedAt ||
    journal.target !== baseUrl || drift.snapshotAt !== snapshot.completedAt)
  throw new Error('Source, manifest, drift report and import journal do not match');

const oldProducts = new Map(snapshot.products.map(product => [product.id, product]));
const currentProducts = new Map(manifest.products.map(product => [product.sourceId, product]));
const api = new BricoApi(baseUrl);
await api.login(resolve('.local/production-admin.env'));
const report = { checkedAt: new Date().toISOString(), dryRun, updatedPrices: [], draftedProducts: [], alreadyCurrent: [] };

function checkIdentity(target, expected) {
  if (target.name !== expected.title || target.variants.length !== expected.variants.length)
    throw new Error(`Target product was edited independently: ${expected.sourceId}`);
  const expectedSkus = new Set(expected.variants.map(variant => variant.sku));
  if (target.variants.some(variant => !expectedSkus.has(variant.sku)))
    throw new Error(`Target SKU differs from source: ${expected.sourceId}`);
}

for (const sourceId of drift.changedIds) {
  const old = oldProducts.get(sourceId), expected = currentProducts.get(sourceId);
  if (!old || !expected || !journal.productIds[sourceId]) throw new Error(`Unmapped changed source ${sourceId}`);
  const target = await api.request(`/api/v1/admin/catalog/products/${journal.productIds[sourceId]}`);
  checkIdentity(target, expected);
  const oldVariants = new Map(old.variants.map(variant => [variant.id, variant]));
  for (const variant of expected.variants) {
    const oldVariant = oldVariants.get(variant.sourceId);
    const found = target.variants.find(item => item.sku === variant.sku);
    if (!oldVariant || !found || oldVariant.sku !== variant.supplierSku)
      throw new Error(`Variant identity changed for ${variant.sku}`);
    if (oldVariant.priceTnd === variant.sourcePriceTnd) continue;
    const oldPrice = sellingPrice(oldVariant.priceTnd);
    if (found.price.amount === variant.priceTnd) {
      report.alreadyCurrent.push(variant.sku);
      continue;
    }
    if (found.price.amount !== oldPrice)
      throw new Error(`Target price was edited independently for ${variant.sku}`);
    if (!dryRun) await api.request(`/api/v1/admin/catalog/products/${target.id}/variants/${found.id}`, {
      method: 'PUT', body: { sku: found.sku, label: found.label, unit: found.unit,
        options: found.options, priceTnd: variant.priceTnd, status: found.status, version: found.version },
    });
    report.updatedPrices.push({ sku: variant.sku, from: oldPrice, to: variant.priceTnd });
  }
}

for (const sourceId of drift.removedIds) {
  const expected = currentProducts.get(sourceId);
  if (!expected?.sourceRemoved || !journal.productIds[sourceId])
    throw new Error(`Unmapped retired source ${sourceId}`);
  const target = await api.request(`/api/v1/admin/catalog/products/${journal.productIds[sourceId]}`);
  checkIdentity(target, expected);
  if (target.status === 'DRAFT') { report.alreadyCurrent.push(String(sourceId)); continue; }
  if (target.status !== 'PUBLISHED') throw new Error(`Unexpected target status for ${sourceId}`);
  if (!dryRun) await api.request(`/api/v1/admin/catalog/products/${target.id}`, {
    method: 'PUT', body: { categoryId: target.categoryId, brandId: target.brandId, name: target.name,
      description: target.description, characteristics: target.characteristics,
      status: 'DRAFT', version: target.version },
  });
  report.draftedProducts.push(sourceId);
}

await writeFile(join(directory, dryRun ? 'reconciliation-plan.json' : 'reconciliation-result.json'),
  JSON.stringify(report, null, 2));
console.log(JSON.stringify({ dryRun, updatedPrices: report.updatedPrices.length,
  draftedProducts: report.draftedProducts.length, alreadyCurrent: report.alreadyCurrent.length }));
