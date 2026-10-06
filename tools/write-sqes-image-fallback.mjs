import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { photoDownloadUrl } from './sqes-brico-catalogue.mjs';

const directory = resolve(process.argv[2] || '.local/sqes-brico-export');
const output = resolve(process.argv[3] || 'frontend/public/sqes-image-fallback.json');
const galleryDirectory = resolve(process.argv[4] || 'frontend/public/sqes-galleries');
const [manifest, journal] = await Promise.all([
  readFile(join(directory, 'catalogue.json'), 'utf8').then(JSON.parse),
  readFile(join(directory, 'import-journal.json'), 'utf8').then(JSON.parse),
]);
if (manifest.sourceSnapshotAt !== journal.sourceSnapshotAt)
  throw new Error('Manifest and import journal do not match');
const images = {};
const galleryShards = Object.fromEntries(Array.from({ length: 256 }, (_, number) =>
  [number.toString(16).padStart(2, '0'), {}]));
let galleryPhotos = 0;
for (const product of manifest.products) {
  if (product.status !== 'PUBLISHED') continue;
  const id = journal.productIds[product.sourceId];
  const photos = product.photos.filter(item => item.path && item.uploadable).slice(0, 12);
  if (!id || !photos.length) continue;
  for (const photo of photos) photoDownloadUrl(photo.sourceUrl); // Reject unexpected hosts.
  const photo = photos[0];
  images[id] = [String(photo.sourceId), photo.sourceUrl, photo.width, photo.height];
  galleryShards[id.slice(0, 2)][id] = photos.map(item =>
    [String(item.sourceId), item.sourceUrl, item.width, item.height]);
  galleryPhotos += photos.length;
}
await writeFile(output, JSON.stringify(images));
await mkdir(galleryDirectory, { recursive: true });
for (const [shard, contents] of Object.entries(galleryShards))
  await writeFile(join(galleryDirectory, `${shard}.json`), JSON.stringify(contents));
console.log(JSON.stringify({ productsWithFallback: Object.keys(images).length,
  galleryPhotos, bytes: Buffer.byteLength(JSON.stringify(images)) }));
