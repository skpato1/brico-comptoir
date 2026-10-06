import { createHash } from 'node:crypto';
import { readFile, writeFile, rename, mkdir, statfs } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { buildCatalogue, imageDimensions, MAX_UPLOAD_BYTES, photoDownloadUrl } from './sqes-brico-catalogue.mjs';

const source = resolve(process.argv[2] || '.local/sqes-import/catalogue-facts.json');
const directory = resolve(process.argv[3] || '.local/sqes-brico-export');
const manifestPath = join(directory, 'catalogue.json');
const photosDirectory = join(directory, 'photos');
const download = process.argv.includes('--download');
await mkdir(photosDirectory, { recursive: true });
const snapshot = JSON.parse(await readFile(source, 'utf8'));
const catalogue = buildCatalogue(snapshot);
const previous = await readFile(manifestPath, 'utf8').then(JSON.parse).catch(() => null);
if (previous?.sourceSnapshotAt === catalogue.sourceSnapshotAt) {
  const old = new Map(previous.products.flatMap(product => product.photos.map(photo => [photo.sourceId, photo])));
  for (const product of catalogue.products) for (const photo of product.photos) {
    const cached = old.get(photo.sourceId);
    if (cached?.sourceUrl === photo.sourceUrl) Object.assign(photo, cached);
  }
}
async function save() {
  const temporary = `${manifestPath}.tmp`;
  await writeFile(temporary, JSON.stringify(catalogue, null, 2));
  await rename(temporary, manifestPath);
}
await save();
const references = new Map();
for (const product of catalogue.products) for (const photo of product.photos) {
  if (!references.has(photo.sourceId)) references.set(photo.sourceId, []);
  references.get(photo.sourceId).push(photo);
}
console.log(`Manifest: ${catalogue.products.length} products, ${catalogue.categories.length} categories, ${catalogue.brands.length} brands, ${references.size} unique photos.`);
if (download) {
  const queue = [...references.entries()];
  let completed = 0, failed = 0, bytes = 0, cursor = 0;
  let saving = Promise.resolve();
  const errors = new Map();
  async function one([id, entries]) {
    let relativePath = entries[0].path;
    let path = relativePath ? join(directory, relativePath) : null;
    let content;
    try {
      content = path ? await readFile(path).catch(() => null) : null;
      if (!content) {
        const free = await statfs(directory);
        if (free.bavail * free.bsize < 1024 ** 3) throw new Error('Less than 1 GiB disk space remains');
        let type;
        for (const width of [1200, 800, 600]) {
          const response = await fetch(photoDownloadUrl(entries[0].sourceUrl, width), {
            signal: AbortSignal.timeout(30000), headers: { accept: 'image/jpeg, image/png' },
          });
          if (!response.ok) throw new Error(`Image HTTP ${response.status}`);
          type = response.headers.get('content-type')?.split(';')[0];
          if (!['image/jpeg', 'image/png', 'image/gif'].includes(type))
            throw new Error('CDN did not return a supported archive image');
          const limit = type === 'image/gif' ? 20 * 1024 * 1024 : MAX_UPLOAD_BYTES;
          const length = Number(response.headers.get('content-length') || 0);
          if (length > limit) continue;
          content = Buffer.from(await response.arrayBuffer());
          if (content.length <= limit) break;
        }
        if (!content || content.length > (type === 'image/gif' ? 20 * 1024 * 1024 : MAX_UPLOAD_BYTES))
          throw new Error('Image exceeds archive limit');
        if (!imageDimensions(content, type)) throw new Error('Invalid image bytes');
        relativePath = `photos/${id}.${type === 'image/png' ? 'png' : type === 'image/gif' ? 'gif' : 'jpg'}`;
        path = join(directory, relativePath);
        const temporary = `${path}.tmp`;
        await writeFile(temporary, content);
        await rename(temporary, path);
        bytes += content.length;
      }
      const type = relativePath.endsWith('.png') ? 'image/png'
        : relativePath.endsWith('.gif') ? 'image/gif' : 'image/jpeg';
      const dimensions = imageDimensions(content, type);
      if (!dimensions) throw new Error('Invalid existing JPEG bytes');
      const checksum = createHash('sha256').update(content).digest('hex');
      for (const entry of entries) Object.assign(entry, {
        path: relativePath, contentType: type, sha256: checksum, ...dimensions,
        uploadable: type !== 'image/gif' && dimensions.width >= 320 && dimensions.height >= 320 &&
          dimensions.width <= 6000 && dimensions.height <= 6000 &&
          dimensions.width * dimensions.height <= 24_000_000, error: null,
      });
      completed++;
    } catch (error) {
      failed++;
      errors.set(error.message, (errors.get(error.message) ?? 0) + 1);
      for (const entry of entries) entry.error = error.message;
    }
  }
  const workers = Array.from({ length: 4 }, async () => {
    while (cursor < queue.length) {
      const index = cursor++;
      await one(queue[index]);
      if ((completed + failed) % 200 === 0) {
        saving = saving.then(save);
        await saving;
        console.log(`Photos ${completed + failed}/${queue.length}; downloaded ${completed}, errors ${failed}, new bytes ${bytes}`);
      }
    }
  });
  await Promise.all(workers);
  await save();
  console.log(JSON.stringify({ total: queue.length, downloaded: completed, failed, newBytes: bytes,
    errors: Object.fromEntries(errors) }));
}
