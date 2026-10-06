import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, map, of, shareReplay, switchMap } from 'rxjs';
import { IdentityApi } from './identity-api';

export interface Category {
  id: string;
  parentId: string | null;
  slug: string;
  name: string;
  active: boolean;
  version: number;
}
export interface Brand {
  id: string;
  slug: string;
  name: string;
  active: boolean;
  version: number;
}
export interface Variant {
  id: string;
  sku: string;
  label: string;
  unit: string;
  options: Record<string, string>;
  price: { amount: string; currency: 'TND' };
  status: 'DRAFT' | 'PUBLISHED';
  version: number;
}
export interface Product {
  id: string;
  categoryId: string;
  brandId: string | null;
  name: string;
  description: string;
  characteristics: Record<string, string>;
  status: 'DRAFT' | 'PUBLISHED';
  demo: boolean;
  version: number;
  variants: Variant[];
}
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
}
export interface ProductImage {
  id: string;
  productId: string;
  cardUrl: string;
  detailUrl: string;
  width: number;
  height: number;
  sortOrder: number;
  primary: boolean;
}
type SqesImageFallback = Record<string, [string, string, number, number]>;
export interface Availability {
  variantId: string;
  available: number;
}
export interface ImportRow {
  line: number;
  productKey: string;
  productName: string;
  sku: string;
  priceTnd: string;
  status: string;
}
export interface ImportIssue {
  line: number;
  field: string;
  message: string;
}
export interface ImportPreview {
  digest: string;
  rowCount: number;
  productCount: number;
  issues: ImportIssue[];
  sample: ImportRow[];
}
export interface ImportApplied {
  productCount: number;
  variantCount: number;
  productIds: string[];
}
export interface ProductFilters {
  q: string;
  categoryId: string;
  brandId: string;
  minPrice: string;
  maxPrice: string;
  sort: 'name' | 'price_asc' | 'price_desc' | 'newest';
  page: number;
  size: number;
}
export interface CategoryInput {
  parentId: string | null;
  slug: string;
  name: string;
  active: boolean;
  version?: number;
}
export interface BrandInput {
  slug: string;
  name: string;
  active: boolean;
  version?: number;
}
export interface ProductInput {
  categoryId: string;
  brandId: string | null;
  name: string;
  description: string;
  characteristics: Record<string, string>;
  status: 'DRAFT' | 'PUBLISHED';
  version?: number;
}
export interface VariantInput {
  sku: string;
  label: string;
  unit: string;
  options: Record<string, string>;
  priceTnd: string;
  status: 'DRAFT' | 'PUBLISHED';
  version?: number;
}

@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);
  private readonly identity = inject(IdentityApi);
  private readonly root = '/api/v1';
  private readonly sqesFallback = this.http.get<SqesImageFallback>('/sqes-image-fallback.json').pipe(
    catchError(() => of({} as SqesImageFallback)),
    shareReplay(1),
  );
  detail(id: string) {
    return this.http.get<Product>(`${this.root}/products/${id}`);
  }
  availability(variantId: string) {
    return this.http.get<Availability>(`${this.root}/availability/${variantId}`);
  }
  categoryPage(q = '', page = 0, size = 20) {
    return this.http.get<Page<Category>>(`${this.root}/admin/catalog/categories/page`, {
      params: { q, page, size },
    });
  }
  brandPage(q = '', page = 0, size = 20) {
    return this.http.get<Page<Brand>>(`${this.root}/admin/catalog/brands/page`, {
      params: { q, page, size },
    });
  }

  categories(admin = false) {
    return this.http.get<Category[]>(`${this.root}/${admin ? 'admin/catalog/' : ''}categories`);
  }
  brands(admin = false) {
    return this.http.get<Brand[]>(`${this.root}/${admin ? 'admin/catalog/' : ''}brands`);
  }
  products(filters: ProductFilters, admin = false) {
    let params = new HttpParams()
      .set('page', filters.page)
      .set('size', filters.size)
      .set('sort', filters.sort);
    for (const key of ['q', 'categoryId', 'brandId', 'minPrice', 'maxPrice'] as const) {
      if (filters[key]) params = params.set(key, filters[key]);
    }
    return this.http.get<Page<Product>>(`${this.root}/${admin ? 'admin/catalog/' : ''}products`, {
      params,
    });
  }
  publicImages(ids: string[]) {
    return this.http.get<Record<string, ProductImage[]>>(`${this.root}/media/products`, {
      params: new HttpParams().set('ids', ids.join(',')),
    }).pipe(switchMap((native) => {
      const missing = ids.filter((id) => !native[id]?.length);
      if (!missing.length) return of(native);
      return this.sqesFallback.pipe(map((fallback) => {
        const result = { ...native };
        for (const id of missing) {
          const entry = fallback[id];
          if (!entry) continue;
          try {
            const url = new URL(entry[1]);
            if (url.protocol !== 'https:' || url.hostname !== 'cdn.shopify.com') continue;
            const card = new URL(url), detail = new URL(url);
            card.searchParams.set('width', '360'); card.searchParams.set('format', 'pjpg');
            detail.searchParams.set('width', '1200'); detail.searchParams.set('format', 'pjpg');
            result[id] = [{ id: `sqes-${entry[0]}`, productId: id,
              cardUrl: card.href, detailUrl: detail.href, width: entry[2], height: entry[3],
              sortOrder: 0, primary: true }];
          } catch { /* Invalid fallback URL is ignored; native media stays authoritative. */ }
        }
        return result;
      }));
    }));
  }
  adminImages(productId: string) {
    return this.http.get<ProductImage[]>(`${this.root}/admin/catalog/products/${productId}/images`);
  }
  uploadImages(productId: string, files: File[]) {
    const body = new FormData();
    files.forEach((file) => body.append('files', file));
    return this.identity
      .csrf()
      .pipe(
        switchMap(() =>
          this.http.post<ProductImage[]>(
            `${this.root}/admin/catalog/products/${productId}/images`,
            body,
          ),
        ),
      );
  }
  orderImages(productId: string, imageIds: string[], primaryImageId: string) {
    return this.identity
      .csrf()
      .pipe(
        switchMap(() =>
          this.http.put<ProductImage[]>(
            `${this.root}/admin/catalog/products/${productId}/images/order`,
            { imageIds, primaryImageId },
          ),
        ),
      );
  }
  previewImport(file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.identity
      .csrf()
      .pipe(
        switchMap(() =>
          this.http.post<ImportPreview>(`${this.root}/admin/catalog/imports/preview`, body),
        ),
      );
  }
  applyImport(file: File, digest: string) {
    const body = new FormData();
    body.append('file', file);
    return this.identity
      .csrf()
      .pipe(
        switchMap(() =>
          this.http.post<ImportApplied>(`${this.root}/admin/catalog/imports/apply`, body, {
            params: { expectedDigest: digest },
          }),
        ),
      );
  }
  category(input: CategoryInput, id?: string) {
    const url = `${this.root}/admin/catalog/categories${id ? '/' + id : ''}`;
    return this.identity
      .csrf()
      .pipe(
        switchMap(() =>
          id ? this.http.put<Category>(url, input) : this.http.post<Category>(url, input),
        ),
      );
  }
  brand(input: BrandInput, id?: string) {
    const url = `${this.root}/admin/catalog/brands${id ? '/' + id : ''}`;
    return this.identity
      .csrf()
      .pipe(
        switchMap(() =>
          id ? this.http.put<Brand>(url, input) : this.http.post<Brand>(url, input),
        ),
      );
  }
  product(input: ProductInput, id?: string) {
    const url = `${this.root}/admin/catalog/products${id ? '/' + id : ''}`;
    return this.identity
      .csrf()
      .pipe(
        switchMap(() =>
          id ? this.http.put<Product>(url, input) : this.http.post<Product>(url, input),
        ),
      );
  }
  variant(productId: string, input: VariantInput, id?: string) {
    const url = `${this.root}/admin/catalog/products/${productId}/variants${id ? '/' + id : ''}`;
    return this.identity
      .csrf()
      .pipe(
        switchMap(() =>
          id ? this.http.put<Variant>(url, input) : this.http.post<Variant>(url, input),
        ),
      );
  }
}
