import { createHash } from 'node:crypto';

export const MAX_UPLOAD_BYTES = 6 * 1024 * 1024;
export const MAX_PRODUCT_PHOTOS = 12;

export function recoverablePrimaryImage(existing, mappedCount, compatiblePhotos) {
  if (mappedCount !== 0 || existing.length !== 1 || !compatiblePhotos.length) return null;
  const [image] = existing;
  const [source] = compatiblePhotos;
  return image.width === source.width && image.height === source.height &&
    image.primary === true && image.sortOrder === 0
    ? { sourceId: source.sourceId, imageId: image.id } : null;
}

export function newlyCreatedImages(response, priorIds, expectedCount) {
  const created = response.filter(image => !priorIds.has(image.id));
  if (response.length !== priorIds.size + expectedCount || created.length !== expectedCount)
    throw new Error('Unexpected photo upload count; inspect target before retry');
  return created;
}

export function completedUploadImages(response, priorIds, sourcePhotos) {
  const created = newlyCreatedImages(response, priorIds, sourcePhotos.length);
  for (let index = 0; index < sourcePhotos.length; index++) {
    if (created[index].width !== sourcePhotos[index].width ||
        created[index].height !== sourcePhotos[index].height ||
        created[index].sortOrder !== priorIds.size + index)
      throw new Error('Uploaded photo metadata differs from the source; inspect before retry');
  }
  return created;
}

export function millimes(value) {
  if (typeof value !== 'string' || !/^\d+(?:\.\d{1,3})?$/.test(value)) throw new Error('Invalid TND price');
  const [whole, fraction = ''] = value.split('.');
  return BigInt(whole) * 1000n + BigInt(fraction.padEnd(3, '0'));
}

export function sellingPrice(source) {
  const amount = millimes(source);
  if (amount <= 0n) return null;
  // An exact 20% increase, then round upward to a whole TND.
  return `${(amount * 6n + 4999n) / 5000n}.000`;
}

export function slug(text) {
  return String(text).normalize('NFKD').replace(/[\u0300-\u036f]/g, '')
    .toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 55) || 'sans-nom';
}

const hash = value => createHash('sha256').update(String(value)).digest('hex');
const plain = value => String(value ?? '').trim();

export function photoDownloadUrl(sourceUrl, width = 1200) {
  const url = new URL(sourceUrl);
  if (url.protocol !== 'https:' || url.hostname !== 'cdn.shopify.com') throw new Error('Unexpected photo host');
  url.searchParams.set('width', String(width));
  url.searchParams.set('format', 'pjpg');
  return url.href;
}

export function jpegDimensions(bytes) {
  if (bytes.length < 4 || bytes[0] !== 0xff || bytes[1] !== 0xd8) return null;
  let at = 2;
  while (at + 4 <= bytes.length) {
    if (bytes[at] !== 0xff) return null;
    while (bytes[at] === 0xff) at++;
    const marker = bytes[at++];
    if (marker === 0xd9 || marker === 0xda) break;
    if (marker === 0x01 || marker >= 0xd0 && marker <= 0xd7) continue;
    const length = bytes.readUInt16BE(at);
    if (length < 2 || at + length > bytes.length) return null;
    if ([0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf].includes(marker)) {
      if (length < 7) return null;
      return { width: bytes.readUInt16BE(at + 5), height: bytes.readUInt16BE(at + 3) };
    }
    at += length;
  }
  return null;
}

export function imageDimensions(bytes, type) {
  if (type === 'image/jpeg') return jpegDimensions(bytes);
  if (type === 'image/png' && bytes.length >= 24 &&
      bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])) &&
      bytes.toString('ascii', 12, 16) === 'IHDR') {
    return { width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20) };
  }
  if (type === 'image/gif' && bytes.length >= 10 &&
      ['GIF87a', 'GIF89a'].includes(bytes.toString('ascii', 0, 6))) {
    return { width: bytes.readUInt16LE(6), height: bytes.readUInt16LE(8) };
  }
  return null;
}

export function buildCatalogue(snapshot) {
  if (!snapshot?.complete || !Array.isArray(snapshot.products) || !Array.isArray(snapshot.collections))
    throw new Error('A complete factual source snapshot is required');
  const productIds = new Set(snapshot.products.map(product => product.id));
  if (productIds.size !== snapshot.products.length) throw new Error('Duplicate source product ID');
  const memberships = new Map([...productIds].map(id => [id, []]));
  const categories = snapshot.collections.map(collection => {
    const ids = [...new Set(collection.productIds)];
    for (const id of ids) {
      if (!memberships.has(id)) throw new Error(`Unknown collection member ${id}`);
      memberships.get(id).push(collection);
    }
    return { sourceId: collection.id, slug: `sqes-${collection.id}`, name: plain(collection.title).slice(0, 120), count: ids.length };
  });
  categories.unshift({ sourceId: null, slug: 'sqes-autres', name: 'Autres produits SQES', count: 0 });
  const brandNames = [...new Set(snapshot.products.map(product => plain(product.vendor)).filter(Boolean))];
  const brands = brandNames.map(name => ({ name, slug: `sqes-${slug(name)}-${hash(name).slice(0, 8)}` }));
  const brandByName = new Map(brands.map(brand => [brand.name, brand.slug]));
  const skuCounts = new Map();
  for (const product of snapshot.products) for (const variant of product.variants) {
    if (variant.sku) skuCounts.set(variant.sku, (skuCounts.get(variant.sku) ?? 0) + 1);
  }
  const skus = new Set();
  const products = snapshot.products.map(product => {
    const sourceCollections = memberships.get(product.id).sort((a, b) =>
      a.productIds.length - b.productIds.length || a.id - b.id);
    const categorySlugs = sourceCollections.map(collection => `sqes-${collection.id}`);
    const variants = product.variants.map(variant => {
      const sourceSku = plain(variant.sku);
      const sourceUnique = sourceSku && skuCounts.get(variant.sku) === 1;
      const suggested = sourceUnique ? `BC-${sourceSku}` : `BC-SQES-${variant.id}`;
      const sku = /^[A-Z0-9][A-Z0-9_-]{0,63}$/.test(suggested) ? suggested : `BC-SQES-${variant.id}`;
      if (skus.has(sku)) throw new Error(`Duplicate destination SKU ${sku}`);
      skus.add(sku);
      return {
        sourceId: variant.id, sku, supplierSku: sourceSku,
        label: plain(variant.title || 'Unité').slice(0, 120), unit: 'unité',
        options: [variant.option1, variant.option2, variant.option3].filter(Boolean).map(plain),
        sourcePriceTnd: variant.priceTnd, priceTnd: sellingPrice(variant.priceTnd),
        status: sellingPrice(variant.priceTnd) ? 'PUBLISHED' : 'DRAFT', stock: 0,
      };
    });
    const needsPriceReview = variants.length === 0 || variants.some(variant => !variant.priceTnd);
    return {
      sourceId: product.id, sourceUrl: product.sourceUrl, sourceHandle: product.handle,
      title: plain(product.title).slice(0, 180), sourceTitle: product.title,
      vendor: plain(product.vendor), brandSlug: brandByName.get(plain(product.vendor)) ?? null,
      primaryCategorySlug: categorySlugs[0] ?? 'sqes-autres', categorySlugs,
      status: needsPriceReview || product.sourceRemoved ? 'DRAFT' : 'PUBLISHED', needsPriceReview,
      sourceRemoved: Boolean(product.sourceRemoved),
      variants, photos: product.mediaReferences.map((photo, index) => ({
        sourceId: photo.id, sourceUrl: photo.url, path: null, sha256: null,
        position: index + 1, uploadable: false, width: null, height: null, error: null,
      })),
      destinationProductId: null,
    };
  });
  return {
    schemaVersion: 1, generatedAt: new Date().toISOString(), sourceSnapshotAt: snapshot.completedAt,
    sourceReconciledAt: snapshot.reconciledAt ?? null,
    source: snapshot.origin, pricePolicy: 'source × 1.20, round up to whole TND',
    stockPolicy: 'zero stock, never sell out of stock',
    categories, brands, products,
  };
}
