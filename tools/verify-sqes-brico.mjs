import { createHash } from 'node:crypto';
import { readFile, stat, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { BricoApi } from './brico-api-client.mjs';

const directory = resolve(process.argv[2] || '.local/sqes-brico-export');
const baseUrl = process.argv[3] || 'https://brico-comptoir.vercel.app';
const manifest = JSON.parse(await readFile(join(directory, 'catalogue.json'), 'utf8'));
const journal = JSON.parse(await readFile(join(directory, 'import-journal.json'), 'utf8'));
if (manifest.sourceSnapshotAt !== journal.sourceSnapshotAt || journal.target !== baseUrl)
  throw new Error('Manifest and import journal do not match');

const errors = [];
const files = new Map();
const sourceIds = new Set(), skus = new Set();
for (const product of manifest.products) {
  if (sourceIds.has(product.sourceId)) errors.push(`duplicate source product ${product.sourceId}`);
  sourceIds.add(product.sourceId);
  if (!journal.productIds[product.sourceId]) errors.push(`not imported: ${product.sourceId}`);
  if (product.needsPriceReview && product.status !== 'DRAFT') errors.push(`zero-price product published: ${product.sourceId}`);
  if (!product.needsPriceReview && !product.sourceRemoved && product.status !== 'PUBLISHED')
    errors.push(`priced current product not published: ${product.sourceId}`);
  if (product.sourceRemoved && product.status !== 'DRAFT')
    errors.push(`retired source product published: ${product.sourceId}`);
  for (const variant of product.variants) {
    if (skus.has(variant.sku)) errors.push(`duplicate SKU ${variant.sku}`);
    skus.add(variant.sku);
    if (variant.stock !== 0) errors.push(`nonzero imported stock ${variant.sku}`);
    if (!product.needsPriceReview && !variant.priceTnd) errors.push(`missing sale price ${variant.sku}`);
  }
  for (const photo of product.photos) {
    if (photo.path) {
      if (!/^photos\/[0-9]+\.(jpg|png|gif)$/.test(photo.path)) errors.push(`unsafe photo path ${photo.path}`);
      files.set(photo.path, photo.sha256);
    }
  }
}
let missingFiles = 0, corruptFiles = 0, totalBytes = 0;
for (const [path, checksum] of files) {
  try {
    const full = join(directory, path);
    totalBytes += (await stat(full)).size;
    const actual = createHash('sha256').update(await readFile(full)).digest('hex');
    if (actual !== checksum) corruptFiles++;
  } catch { missingFiles++; }
}
if (missingFiles) errors.push(`${missingFiles} local photo files missing`);
if (corruptFiles) errors.push(`${corruptFiles} local photo checksums differ`);

const api = new BricoApi(baseUrl);
await api.login(resolve('.local/production-admin.env'));
const observed = new Map();
for (let page = 0; ; page++) {
  const result = await api.request(`/api/v1/admin/catalog/products?page=${page}&size=100`);
  for (const product of result.items) observed.set(product.id, product);
  if ((page + 1) * 100 >= result.totalElements) break;
}
let importedProducts = 0, importedVariants = 0, expectedImages = 0, missingPrimary = 0;
for (const product of manifest.products) {
  const target = observed.get(journal.productIds[product.sourceId]);
  if (!target) continue;
  importedProducts++;
  if (target.status !== product.status || target.name !== product.title)
    errors.push(`catalogue mismatch for ${product.sourceId}`);
  if (product.needsPriceReview) {
    if (target.variants.length) errors.push(`unpriced draft has variants ${product.sourceId}`);
  } else {
    if (target.variants.length !== product.variants.length)
      errors.push(`variant count mismatch for ${product.sourceId}`);
    const actual = new Map(target.variants.map(variant => [variant.sku, variant]));
    for (const variant of product.variants) {
      const found = actual.get(variant.sku);
      if (!found || found.price.amount !== variant.priceTnd || found.status !== 'PUBLISHED')
        errors.push(`variant mismatch for ${variant.sku}`);
      else importedVariants++;
    }
  }
  const imageMap = journal.imageIds[product.sourceId] || {};
  expectedImages += Object.keys(imageMap).length;
  if (product.photos.some(photo => photo.uploadable) && Object.keys(imageMap).length === 0) missingPrimary++;
  if (Object.keys(imageMap).length > 12) errors.push(`photo cap exceeded for ${product.sourceId}`);
}
if (missingPrimary) errors.push(`${missingPrimary} products with compatible source photos have no uploaded image`);
const result = {
  checkedAt: new Date().toISOString(), sourceSnapshotAt: manifest.sourceSnapshotAt,
  expectedProducts: manifest.products.length, importedProducts,
  expectedVariants: manifest.products.filter(p => !p.needsPriceReview).reduce((n, p) => n + p.variants.length, 0),
  verifiedVariants: importedVariants, sourcePhotoReferences: manifest.products.reduce((n, p) => n + p.photos.length, 0),
  locallyStoredPhotos: files.size, localPhotoBytes: totalBytes,
  uploadedPhotoReferences: expectedImages, missingFiles, corruptFiles,
  sourceProductsWithoutCompatiblePhoto: manifest.products.filter(p => !p.photos.some(photo => photo.uploadable)).length,
  missingPrimary,
  errors: errors.slice(0, 100), errorCount: errors.length,
};
await writeFile(join(directory, 'verification.json'), JSON.stringify(result, null, 2));
console.log(JSON.stringify(result));
if (errors.length) process.exitCode = 1;
