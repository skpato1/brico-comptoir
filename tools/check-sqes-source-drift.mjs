import { readFile, writeFile } from 'node:fs/promises';
import { resolve, dirname, join } from 'node:path';

const sourcePath = resolve(process.argv[2] || '.local/sqes-import/catalogue-facts.json');
const reportPath = resolve(process.argv[3] || '.local/sqes-brico-export/source-drift.json');
const snapshot = JSON.parse(await readFile(sourcePath, 'utf8'));
const current = new Map();
const currentRaw = [];
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
for (let page = 1; page <= 200; page++) {
  let response;
  for (let attempt = 0; attempt < 5; attempt++) {
    response = await fetch(`https://www.sqes.tn/products.json?limit=250&page=${page}`, {
      headers: { accept: 'application/json' }, signal: AbortSignal.timeout(30000),
    });
    if (response.status !== 429) break;
    await sleep(1000 * (attempt + 1));
  }
  if (!response.ok) throw new Error(`Public product page ${page}: HTTP ${response.status}`);
  const products = (await response.json()).products;
  if (!Array.isArray(products)) throw new Error(`Unexpected product feed page ${page}`);
  for (const product of products) {
    if (current.has(product.id)) throw new Error(`Duplicate source product ${product.id}`);
    currentRaw.push(product);
    current.set(product.id, {
      title: product.title, vendor: product.vendor,
      variants: product.variants.map(v => ({ id: v.id, sku: v.sku, priceTnd: v.price })),
      photos: product.images.map(image => ({ id: image.id, url: image.src })),
    });
  }
  if (products.length < 250) break;
  await sleep(500);
}
const old = new Map(snapshot.products.map(product => [product.id, product]));
const newIds = [...current.keys()].filter(id => !old.has(id));
const removedIds = [...old.keys()].filter(id => !current.has(id));
const changed = [];
const changedFields = {};
for (const [id, product] of current) {
  const prior = old.get(id);
  if (!prior) continue;
  const fields = [];
  if (product.title !== prior.title) fields.push('title');
  if (product.vendor !== prior.vendor) fields.push('vendor');
  if (JSON.stringify(product.variants) !== JSON.stringify(prior.variants.map(v => ({
        id: v.id, sku: v.sku, priceTnd: v.priceTnd,
      })))) fields.push('variants');
  if (JSON.stringify(product.photos) !== JSON.stringify(prior.mediaReferences)) fields.push('photos');
  if (fields.length) { changed.push(id); changedFields[id] = fields; }
}
const report = { checkedAt: new Date().toISOString(), snapshotAt: snapshot.completedAt,
  snapshotProducts: old.size, currentProducts: current.size,
  newIds, removedIds, changedIds: changed, changedFields };
await writeFile(join(dirname(reportPath), 'source-current-products.json'), JSON.stringify(currentRaw));
await writeFile(reportPath, JSON.stringify(report, null, 2));
console.log(JSON.stringify({ snapshotProducts: old.size, currentProducts: current.size,
  new: newIds.length, removed: removedIds.length, changed: changed.length }));
