import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { buildCatalogue, completedUploadImages, imageDimensions, newlyCreatedImages, photoDownloadUrl,
  recoverablePrimaryImage, sellingPrice } from './sqes-brico-catalogue.mjs';

const snapshot = {
  complete: true, origin: 'https://www.sqes.tn', completedAt: '2026-10-06T00:00:00Z',
  products: [{ id: 12, handle: 'item', sourceUrl: 'https://www.sqes.tn/products/item',
    title: 'Exemple', vendor: 'Marque', variants: [
      { id: 31, sku: 'ABC', title: 'Grand', priceTnd: '16.900' },
      { id: 32, sku: null, title: 'Petit', priceTnd: '0.000' },
    ], mediaReferences: [{ id: 99, url: 'https://cdn.shopify.com/a.jpg?v=1' }],
  }],
  collections: [{ id: 7, title: 'Quincaillerie', productIds: [12] }],
};

test('prices use exact millimes, 20% increase, and upward dinar rounding', () => {
  assert.equal(sellingPrice('16.900'), '21.000');
  assert.equal(sellingPrice('627.500'), '753.000');
  assert.equal(sellingPrice('0.001'), '1.000');
  assert.equal(sellingPrice('0.000'), null);
  assert.throws(() => sellingPrice('1.2345'));
});

test('a zero-price variant keeps the entire source product in draft', () => {
  const result = buildCatalogue(snapshot);
  const product = result.products[0];
  assert.equal(product.status, 'DRAFT');
  assert.equal(product.needsPriceReview, true);
  assert.deepEqual(product.variants.map(variant => variant.priceTnd), ['21.000', null]);
  assert.deepEqual(product.variants.map(variant => variant.sku), ['BC-ABC', 'BC-SQES-32']);
  assert.equal(product.variants[0].stock, 0);
  assert.deepEqual(product.categorySlugs, ['sqes-7']);
  assert.equal(product.photos[0].path, null);
});

test('duplicate supplier SKUs get distinct source-ID-based internal references', () => {
  const copy = structuredClone(snapshot);
  copy.products[0].variants[1].sku = 'ABC';
  copy.products[0].variants[1].priceTnd = '5.000';
  assert.deepEqual(buildCatalogue(copy).products[0].variants.map(variant => variant.sku),
    ['BC-SQES-31', 'BC-SQES-32']);
});

test('photo URLs accept only the expected CDN host and request bounded renditions', () => {
  assert.equal(photoDownloadUrl('https://cdn.shopify.com/a.png?v=1'),
    'https://cdn.shopify.com/a.png?v=1&width=1200&format=pjpg');
  assert.throws(() => photoDownloadUrl('https://other.example/a.png'));
});

test('image signatures and dimensions are checked before archiving', () => {
  const png = Buffer.alloc(24);
  Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]).copy(png);
  png.write('IHDR', 12, 'ascii');
  png.writeUInt32BE(640, 16); png.writeUInt32BE(480, 20);
  assert.deepEqual(imageDimensions(png, 'image/png'), { width: 640, height: 480 });
  const gif = Buffer.from('GIF89a0000', 'ascii');
  gif.writeUInt16LE(500, 6); gif.writeUInt16LE(400, 8);
  assert.deepEqual(imageDimensions(gif, 'image/gif'), { width: 500, height: 400 });
  assert.equal(imageDimensions(Buffer.from('fake'), 'image/jpeg'), null);
});

test('an interrupted primary upload can be reconciled only with one matching image', () => {
  const source = [{ sourceId: 41, width: 640, height: 480 }];
  const target = [{ id: 'image-1', width: 640, height: 480, sortOrder: 0, primary: true }];
  assert.deepEqual(recoverablePrimaryImage(target, 0, source), { sourceId: 41, imageId: 'image-1' });
  assert.equal(recoverablePrimaryImage(target, 1, source), null);
  assert.equal(recoverablePrimaryImage([{ ...target[0], width: 700 }], 0, source), null);
  assert.equal(recoverablePrimaryImage([...target, target[0]], 0, source), null);
});

test('gallery upload maps only new IDs from the full-gallery API response', () => {
  const response = [{ id: 'old' }, { id: 'new-1' }, { id: 'new-2' }];
  assert.deepEqual(newlyCreatedImages(response, new Set(['old']), 2), response.slice(1));
  assert.throws(() => newlyCreatedImages(response, new Set(['old']), 1));
  const photos = [{ width: 640, height: 480 }, { width: 800, height: 600 }];
  const complete = [{ id: 'old' },
    { id: 'new-1', width: 640, height: 480, sortOrder: 1 },
    { id: 'new-2', width: 800, height: 600, sortOrder: 2 }];
  assert.deepEqual(completedUploadImages(complete, new Set(['old']), photos), complete.slice(1));
  assert.throws(() => completedUploadImages(complete, new Set(['old']), [...photos].reverse()));
});
