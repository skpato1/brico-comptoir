import { readFile, writeFile, rename } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { BricoApi } from './brico-api-client.mjs';

const sourceId = Number(process.argv[2]);
if (!Number.isSafeInteger(sourceId)) throw new Error('Pass exactly one source product ID');
const directory = resolve('.local/sqes-brico-export');
const manifest = JSON.parse(await readFile(join(directory, 'catalogue.json'), 'utf8'));
const journalPath = join(directory, 'import-journal.json');
const journal = JSON.parse(await readFile(journalPath, 'utf8'));
const product = manifest.products.find(item => item.sourceId === sourceId);
const productId = journal.productIds[sourceId];
if (!product || !productId || manifest.sourceSnapshotAt !== journal.sourceSnapshotAt)
  throw new Error('Product is not in the matching import journal');
const compatible = product.photos.filter(photo => photo.path && photo.uploadable);
if (compatible.length !== 1 || Object.keys(journal.imageIds[sourceId] || {}).length)
  throw new Error('Automatic reconciliation is limited to one unrecorded source photo');

const api = new BricoApi(journal.target);
await api.login(resolve('.local/production-admin.env'));
const actual = await api.request(`/api/v1/admin/catalog/products/${productId}/images`);
const photo = compatible[0];
if (actual.length !== 1 || actual[0].width !== photo.width || actual[0].height !== photo.height ||
    !actual[0].primary || actual[0].sortOrder !== 0)
  throw new Error(`Destination image cannot be matched safely to source ${sourceId}`);
journal.imageIds[sourceId] = { [photo.sourceId]: actual[0].id };
const temporary = `${journalPath}.tmp`;
await writeFile(temporary, JSON.stringify(journal, null, 2));
await rename(temporary, journalPath);
console.log(JSON.stringify({ reconciledSourceProduct: sourceId, images: 1 }));
