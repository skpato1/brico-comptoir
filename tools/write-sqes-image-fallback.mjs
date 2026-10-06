import { readFile, writeFile } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { photoDownloadUrl } from './sqes-brico-catalogue.mjs';

const directory = resolve(process.argv[2] || '.local/sqes-brico-export');
const output = resolve(process.argv[3] || 'frontend/public/sqes-image-fallback.json');
const [manifest, journal] = await Promise.all([
  readFile(join(directory, 'catalogue.json'), 'utf8').then(JSON.parse),
  readFile(join(directory, 'import-journal.json'), 'utf8').then(JSON.parse),
]);
if (manifest.sourceSnapshotAt !== journal.sourceSnapshotAt)
  throw new Error('Manifest and import journal do not match');
const images = {};
for (const product of manifest.products) {
  if (product.status !== 'PUBLISHED') continue;
  const id = journal.productIds[product.sourceId];
  const photo = product.photos.find(item => item.path && item.uploadable);
  if (!id || !photo) continue;
  photoDownloadUrl(photo.sourceUrl); // Reject unexpected hosts before writing a public asset.
  images[id] = [String(photo.sourceId), photo.sourceUrl, photo.width, photo.height];
}
await writeFile(output, JSON.stringify(images));
console.log(JSON.stringify({ productsWithFallback: Object.keys(images).length,
  bytes: Buffer.byteLength(JSON.stringify(images)) }));
