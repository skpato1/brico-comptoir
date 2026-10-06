import { readFile, writeFile, rename } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { BricoApi } from './brico-api-client.mjs';
import { completedUploadImages, recoverablePrimaryImage } from './sqes-brico-catalogue.mjs';

const directory = resolve(process.argv[2] || '.local/sqes-brico-export');
const baseUrl = process.argv[3] || 'https://brico-comptoir.vercel.app';
const phase = process.argv.find(argument => argument.startsWith('--phase='))?.split('=')[1] || 'all';
const maxProducts = Number(process.argv.find(argument => argument.startsWith('--max-products='))?.split('=')[1] || 0);
const onlySourceId = Number(process.argv.find(argument => argument.startsWith('--source-id='))?.split('=')[1] || 0);
const workers = Number(process.argv.find(argument => argument.startsWith('--workers='))?.split('=')[1] || 3);
if (!['all', 'catalogue', 'photos', 'primary', 'gallery'].includes(phase) || maxProducts < 0 ||
    !Number.isInteger(maxProducts) || !Number.isSafeInteger(onlySourceId) || onlySourceId < 0 ||
    !Number.isInteger(workers) || workers < 1 || workers > 8 || (onlySourceId && maxProducts))
  throw new Error('Use --phase=all|catalogue|photos|primary|gallery, optional --max-products=N or --source-id=N and --workers=1..8');
const manifest = JSON.parse(await readFile(join(directory, 'catalogue.json'), 'utf8'));
if (manifest.schemaVersion !== 1 || !Array.isArray(manifest.products)) throw new Error('Unexpected catalogue manifest');
const selected = onlySourceId ? manifest.products.filter(product => product.sourceId === onlySourceId)
  : maxProducts ? manifest.products.slice(0, maxProducts) : manifest.products;
if (onlySourceId && selected.length !== 1) throw new Error(`Unknown source product ${onlySourceId}`);
const journalPath = join(directory, 'import-journal.json');
const journal = await readFile(journalPath, 'utf8').then(JSON.parse).catch(() => ({
  schemaVersion: 1, sourceSnapshotAt: manifest.sourceSnapshotAt, target: baseUrl,
  productIds: {}, imageIds: {}, errors: [],
}));
if (journal.sourceSnapshotAt !== manifest.sourceSnapshotAt || journal.target !== baseUrl)
  throw new Error('Existing import journal belongs to another snapshot or target');
let pendingWrite = Promise.resolve();
async function saveJournal() {
  pendingWrite = pendingWrite.then(async () => {
    const temporary = `${journalPath}.tmp`;
    await writeFile(temporary, JSON.stringify(journal, null, 2));
    await rename(temporary, journalPath);
  });
  return pendingWrite;
}
const api = new BricoApi(baseUrl);
await api.request('/api/v1/health/readiness');
const user = await api.login(resolve('.local/production-admin.env'));
console.log(`Authenticated with roles ${user.roles.join(', ')}; ${selected.length} products selected.`);

const categoriesBySlug = new Map();
const brandsBySlug = new Map();
async function ensureTaxonomy() {
  const categories = await api.request('/api/v1/admin/catalog/categories');
  const brands = await api.request('/api/v1/admin/catalog/brands');
  categories.forEach(category => categoriesBySlug.set(category.slug, category));
  brands.forEach(brand => brandsBySlug.set(brand.slug, brand));
  const neededCategories = maxProducts
    ? new Set(selected.map(product => product.primaryCategorySlug))
    : new Set(manifest.categories.map(category => category.slug));
  const neededBrands = new Set(selected.map(product => product.brandSlug).filter(Boolean));
  for (const category of manifest.categories.filter(value => neededCategories.has(value.slug))) {
    const existing = categoriesBySlug.get(category.slug);
    if (existing) {
      if (!existing.active) throw new Error(`Category ${category.slug} is inactive`);
      continue;
    }
    const created = await api.request('/api/v1/admin/catalog/categories', {
      method: 'POST', body: { parentId: null, slug: category.slug, name: category.name, active: true },
    });
    categoriesBySlug.set(category.slug, created);
  }
  for (const brand of manifest.brands.filter(value => neededBrands.has(value.slug))) {
    const existing = brandsBySlug.get(brand.slug);
    if (existing) {
      if (!existing.active) throw new Error(`Brand ${brand.slug} is inactive`);
      continue;
    }
    const created = await api.request('/api/v1/admin/catalog/brands', {
      method: 'POST', body: { slug: brand.slug, name: brand.name, active: true },
    });
    brandsBySlug.set(brand.slug, created);
  }
  console.log(`Taxonomy ready: ${categoriesBySlug.size} categories, ${brandsBySlug.size} brands.`);
}

async function reconcileProducts() {
  const existingSkus = new Map();
  const existingDrafts = new Map();
  for (let page = 0; ; page++) {
    const result = await api.request(`/api/v1/admin/catalog/products?page=${page}&size=100`);
    for (const product of result.items) {
      for (const variant of product.variants) existingSkus.set(variant.sku, product.id);
      if (product.characteristics?.source === 'SQES' && product.characteristics?.sourceId)
        existingDrafts.set(product.characteristics.sourceId, product.id);
    }
    if ((page + 1) * 100 >= result.totalElements) break;
  }
  for (const product of selected) {
    const ids = new Set(product.variants.map(variant => existingSkus.get(variant.sku)).filter(Boolean));
    if (ids.size > 1) throw new Error(`Variants of source ${product.sourceId} belong to multiple products`);
    const discovered = [...ids][0] || existingDrafts.get(String(product.sourceId));
    const journalId = journal.productIds[product.sourceId];
    if (journalId && discovered && journalId !== discovered)
      throw new Error(`Product ID conflict for source ${product.sourceId}`);
    if (discovered) journal.productIds[product.sourceId] = discovered;
  }
  await saveJournal();
  return existingSkus;
}

const cell = value => `"${String(value ?? '').replaceAll('"', '""')}"`;
function csv(products) {
  const rows = [['productKey', 'categorySlug', 'brandSlug', 'productName', 'description',
    'sku', 'variantLabel', 'unit', 'priceTnd', 'status']];
  for (const product of products) for (const variant of product.variants) rows.push([
    `sqes-${product.sourceId}`, product.primaryCategorySlug, product.brandSlug || '',
    product.title, '', variant.sku, variant.label || 'Unité', variant.unit,
    variant.priceTnd, product.status,
  ]);
  return rows.map(row => row.map(cell).join(';')).join('\r\n') + '\r\n';
}
function batches(products) {
  const output = [];
  let batch = [], rows = 0;
  for (const product of products) {
    if (product.variants.length > 200) throw new Error(`Too many variants in source ${product.sourceId}`);
    if (rows + product.variants.length > 200) { output.push(batch); batch = []; rows = 0; }
    batch.push(product);
    rows += product.variants.length;
  }
  if (batch.length) output.push(batch);
  return output;
}
function multipart(content, filename) {
  const form = new FormData();
  form.append('file', new Blob([Buffer.from(content)], { type: 'text/csv' }), filename);
  return form;
}

async function importCatalogue() {
  await ensureTaxonomy();
  const existingSkus = await reconcileProducts();
  const pending = selected.filter(product => !journal.productIds[product.sourceId]);
  const priced = pending.filter(product => !product.needsPriceReview);
  const drafts = pending.filter(product => product.needsPriceReview);
  const jobs = batches(priced);
  let done = 0, cursor = 0, stopped = false;
  const applyBatch = async batch => {
    for (const product of batch) for (const variant of product.variants) {
      if (existingSkus.has(variant.sku)) throw new Error(`SKU already exists: ${variant.sku}`);
    }
    const content = csv(batch);
    if (Buffer.byteLength(content) > 1024 * 1024) throw new Error('CSV batch exceeds 1 MiB');
    const preview = await api.request('/api/v1/admin/catalog/imports/preview', {
      method: 'POST', form: multipart(content, 'sqes-preview.csv'), timeout: 120000,
    });
    if (preview.issues?.length) throw new Error(`CSV validation failed: ${JSON.stringify(preview.issues.slice(0, 5))}`);
    const applied = await api.request(`/api/v1/admin/catalog/imports/apply?expectedDigest=${preview.digest}`, {
      method: 'POST', form: multipart(content, 'sqes-apply.csv'), timeout: 120000,
    });
    if (applied.productCount !== batch.length || applied.productIds.length !== batch.length)
      throw new Error('Unexpected CSV apply count; inspect target before retry');
    for (let i = 0; i < batch.length; i++) journal.productIds[batch[i].sourceId] = applied.productIds[i];
    await saveJournal();
    done += batch.length;
    console.log(`Imported ${done}/${priced.length} priced products.`);
  };
  const work = async () => {
    while (!stopped && cursor < jobs.length) {
      try { await applyBatch(jobs[cursor++]); }
      catch (error) { stopped = true; throw error; }
    }
  };
  const results = await Promise.allSettled(Array.from({ length: Math.min(workers, 3) }, work));
  const failure = results.find(result => result.status === 'rejected');
  if (failure) throw failure.reason;
  for (const product of drafts) {
    const created = await api.request('/api/v1/admin/catalog/products', { method: 'POST', body: {
      categoryId: categoriesBySlug.get(product.primaryCategorySlug).id,
      brandId: product.brandSlug ? brandsBySlug.get(product.brandSlug)?.id : null,
      name: product.title, description: '', status: 'DRAFT',
      characteristics: { source: 'SQES', sourceId: String(product.sourceId), priceReview: 'Required' },
    } });
    journal.productIds[product.sourceId] = created.id;
    await saveJournal();
  }
  console.log(`Catalogue phase complete: ${selected.filter(p => journal.productIds[p.sourceId]).length}/${selected.length} mapped.`);
}

async function importPhotos(passes) {
  let productsDone = 0, uploaded = 0, skipped = 0;
  for (const pass of passes) {
    let cursor = 0, stopped = false;
    const work = async () => {
      while (!stopped && cursor < selected.length) {
        const product = selected[cursor++];
        try { await importProductPhotos(product, pass); }
        catch (error) { stopped = true; throw error; }
      }
    };
    const results = await Promise.allSettled(Array.from({ length: workers }, work));
    const failure = results.find(result => result.status === 'rejected');
    if (failure) throw failure.reason;
    console.log(`Photo ${pass} pass complete: ${uploaded} uploaded in total.`);
  }
  console.log(`Photo phase complete: ${uploaded} uploaded, ${skipped} unmapped product passes.`);

  async function importProductPhotos(product, pass) {
    const productId = journal.productIds[product.sourceId];
    if (!productId) { skipped++; return; }
    const photos = product.photos.filter(photo => photo.path && photo.uploadable &&
      !journal.imageErrors?.[product.sourceId]?.[photo.sourceId]);
    if (!photos.length) return;
    const existing = await api.request(`/api/v1/admin/catalog/products/${productId}/images`);
    let mapped = Object.keys(journal.imageIds[product.sourceId] || {}).length;
    if (pass === 'primary' && existing.length !== mapped) {
      const recovered = recoverablePrimaryImage(existing, mapped, photos);
      if (recovered) {
        journal.imageIds[product.sourceId] = { [recovered.sourceId]: recovered.imageId };
        await saveJournal();
        mapped = 1;
      }
    }
    if (existing.length !== mapped) {
      // A prior POST may have committed before its response was lost. Never blindly duplicate it.
      throw new Error(`Unreconciled images for source ${product.sourceId}; inspect before retry`);
    }
    if (pass === 'primary') {
      if (mapped) return;
      for (const photo of photos) {
        await uploadGroup(product, productId, [photo]);
        if (Object.keys(journal.imageIds[product.sourceId] || {}).length) break;
      }
    } else {
      if (!mapped) return;
      const candidates = photos.filter(photo =>
        !journal.imageIds[product.sourceId]?.[photo.sourceId] &&
        !journal.imageErrors?.[product.sourceId]?.[photo.sourceId]);
      for (let i = 0; i < candidates.length;) {
        const remaining = 12 - Object.keys(journal.imageIds[product.sourceId] || {}).length;
        if (remaining <= 0) break;
        const group = candidates.slice(i, i + Math.min(4, remaining));
        i += group.length;
        await uploadGroup(product, productId, group);
      }
    }
    productsDone++;
    if (productsDone % 100 === 0) console.log(`Photo ${pass}: ${productsDone} products, ${uploaded} uploaded.`);
  }

  async function uploadGroup(product, productId, group) {
    const form = new FormData();
    const priorIds = new Set(Object.values(journal.imageIds[product.sourceId] || {}));
    for (const photo of group) {
      const bytes = await readFile(join(directory, photo.path));
      form.append('files', new Blob([bytes], { type: photo.contentType }),
        `${photo.sourceId}.${photo.contentType === 'image/png' ? 'png' : 'jpg'}`);
    }
    async function record(newImages) {
      journal.imageIds[product.sourceId] ??= {};
      for (let j = 0; j < group.length; j++) journal.imageIds[product.sourceId][group[j].sourceId] = newImages[j].id;
      await saveJournal();
      uploaded += group.length;
    }
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        const response = await api.request(`/api/v1/admin/catalog/products/${productId}/images`, {
          method: 'POST', form, timeout: 120000,
        });
        // The endpoint returns the complete gallery, including earlier photos.
        await record(completedUploadImages(response, priorIds, group));
        return;
      } catch (error) {
        if (error.status === 400 || error.status === 413) {
          if (group.length > 1) {
            for (const photo of group) await uploadGroup(product, productId, [photo]);
          } else {
            journal.imageErrors ??= {};
            journal.imageErrors[product.sourceId] ??= {};
            journal.imageErrors[product.sourceId][group[0].sourceId] = error.message;
            await saveJournal();
          }
          return;
        }
        if (error.status !== 500) throw error;
        const existing = await api.request(`/api/v1/admin/catalog/products/${productId}/images`);
        if (existing.length === priorIds.size + group.length) {
          await record(completedUploadImages(existing, priorIds, group));
          return;
        }
        if (existing.length !== priorIds.size || existing.some(image => !priorIds.has(image.id)) || attempt === 2)
          throw error;
        await new Promise(resolve => setTimeout(resolve, 1000 * (attempt + 1)));
      }
    }
  }
}

if (phase === 'all' || phase === 'catalogue') await importCatalogue();
if (phase === 'all' || phase === 'photos') await importPhotos(['primary', 'gallery']);
if (phase === 'primary' || phase === 'gallery') await importPhotos([phase]);
