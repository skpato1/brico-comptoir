import { resolve } from 'node:path';
import { BricoApi } from './brico-api-client.mjs';

const api = new BricoApi(process.argv[2] || 'https://brico-comptoir.vercel.app');
const credentials = resolve(process.argv[3] || '.local/production-admin.env');
const health = await api.request('/api/v1/health/readiness');
const user = await api.login(credentials);
const products = await api.request('/api/v1/admin/catalog/products?size=1');
const categories = await api.request('/api/v1/admin/catalog/categories');
const brands = await api.request('/api/v1/admin/catalog/brands');
console.log(JSON.stringify({ health: health.status, roles: user.roles,
  existingProducts: products.totalElements, categories: categories.length, brands: brands.length }));
