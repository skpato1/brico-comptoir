import { readFile, writeFile } from 'node:fs/promises';
import { resolve, join } from 'node:path';

const directory = resolve(process.argv[2] || '.local/sqes-brico-export');
const original = JSON.parse(await readFile(resolve('.local/sqes-import/catalogue-facts.json'), 'utf8'));
const report = JSON.parse(await readFile(join(directory, 'source-drift.json'), 'utf8'));
const current = new Map(JSON.parse(await readFile(join(directory, 'source-current-products.json'), 'utf8'))
  .map(product => [product.id, product]));
if (original.products.length !== report.snapshotProducts || current.size !== report.currentProducts)
  throw new Error('Drift report and source snapshots differ');
const products = structuredClone(original.products);
const byId = new Map(products.map(product => [product.id, product]));
for (const id of report.removedIds) {
  const item = byId.get(id);
  if (!item || current.has(id)) throw new Error(`Removal conflict for ${id}`);
  item.sourceRemoved = true;
}
for (const id of report.changedIds) {
  const item = byId.get(id), latest = current.get(id);
  if (!item || !latest || report.changedFields[id].some(field => field !== 'variants'))
    throw new Error(`Unsupported source change for ${id}`);
  if (item.variants.length !== latest.variants.length) throw new Error(`Variant count changed for ${id}`);
  const latestVariants = new Map(latest.variants.map(variant => [variant.id, variant]));
  for (const variant of item.variants) {
    const found = latestVariants.get(variant.id);
    if (!found || found.sku !== variant.sku) throw new Error(`Variant identity changed for ${id}`);
    variant.priceTnd = found.price;
  }
}
for (const id of report.newIds) {
  const product = current.get(id);
  if (!product || byId.has(id)) throw new Error(`New product conflict for ${id}`);
  products.push({
    id: product.id, handle: product.handle, title: product.title, vendor: product.vendor,
    type: product.product_type, tags: product.tags,
    sourceUrl: `https://www.sqes.tn/products/${product.handle}`, options: product.options,
    variants: product.variants.map(variant => ({
      id: variant.id, title: variant.title, sku: variant.sku,
      option1: variant.option1, option2: variant.option2, option3: variant.option3,
      priceTnd: variant.price, availableAtSource: variant.available, grams: variant.grams,
    })),
    mediaReferences: product.images.map(image => ({ id: image.id, url: image.src })),
  });
}
const reconciled = { ...original, reconciledAt: report.checkedAt, products };
await writeFile(join(directory, 'source-reconciled.json'), JSON.stringify(reconciled));
console.log(JSON.stringify({ original: original.products.length, merged: products.length,
  new: report.newIds.length, removedAsDraft: report.removedIds.length,
  pricesChanged: report.changedIds.length }));
