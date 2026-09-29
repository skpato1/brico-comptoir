import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { forkJoin, map, of, switchMap } from 'rxjs';
import { IdentityApi } from './identity-api';
import { Page, ProductImage } from './catalog-api';

export interface PackImage extends Omit<ProductImage, 'productId'> {
  packId: string;
}

export interface PackComponentLine {
  variantId: string;
  quantity: number;
}
export interface PackVariant {
  id: string;
  code: string;
  label: string;
  price: { amount: string; currency: 'TND' };
  status: 'DRAFT' | 'PUBLISHED';
  version: number;
  components: PackComponentLine[];
  available: number;
}
export interface Pack {
  id: string;
  code: string;
  name: string;
  slogan: string;
  guide: string;
  status: 'DRAFT' | 'PUBLISHED';
  demo: boolean;
  version: number;
  variants: PackVariant[];
  componentNames?: Record<string, string>;
}
export interface PackInput {
  code: string;
  name: string;
  slogan: string;
  guide: string;
  status: 'DRAFT' | 'PUBLISHED';
  version?: number;
}
export interface PackVariantInput {
  code: string;
  label: string;
  priceTnd: string;
  status: 'DRAFT' | 'PUBLISHED';
  components: PackComponentLine[];
  version?: number;
}

@Injectable({ providedIn: 'root' })
export class PacksApi {
  private readonly http = inject(HttpClient);
  private readonly identity = inject(IdentityApi);
  private readonly root = '/api/v1';
  detail(id: string) {
    return this.http.get<Pack>(`${this.root}/packs/${id}`);
  }
  page(q = '', page = 0, size = 20) {
    return this.http.get<Page<Pack>>(`${this.root}/admin/packs/page`, {
      params: { q, page, size },
    });
  }

  list(admin = false) {
    return this.http.get<Pack[]>(`${this.root}/${admin ? 'admin/' : ''}packs`);
  }
  publicImages(ids: string[]) {
    if (!ids.length) return of({} as Record<string, PackImage[]>);
    const requests = [];
    for (let i = 0; i < ids.length; i += 100)
      requests.push(
        this.http.get<Record<string, PackImage[]>>(`${this.root}/media/packs`, {
          params: { ids: ids.slice(i, i + 100).join(',') },
        }),
      );
    return forkJoin(requests).pipe(
      map((results) => Object.assign({}, ...results) as Record<string, PackImage[]>),
    );
  }
  adminImages(id: string) {
    return this.http.get<PackImage[]>(`${this.root}/admin/packs/${id}/images`);
  }
  uploadImages(id: string, files: File[]) {
    const form = new FormData();
    for (const file of files) form.append('files', file);
    return this.identity
      .csrf()
      .pipe(
        switchMap(() => this.http.post<PackImage[]>(`${this.root}/admin/packs/${id}/images`, form)),
      );
  }
  orderImages(id: string, imageIds: string[], primaryImageId: string) {
    return this.identity
      .csrf()
      .pipe(
        switchMap(() =>
          this.http.put<PackImage[]>(`${this.root}/admin/packs/${id}/images/order`, {
            imageIds,
            primaryImageId,
          }),
        ),
      );
  }
  savePack(input: PackInput, id?: string) {
    const url = `${this.root}/admin/packs${id ? '/' + id : ''}`;
    return this.identity
      .csrf()
      .pipe(
        switchMap(() => (id ? this.http.put<Pack>(url, input) : this.http.post<Pack>(url, input))),
      );
  }
  saveVariant(packId: string, input: PackVariantInput, id?: string) {
    const url = `${this.root}/admin/packs/${packId}/variants${id ? '/' + id : ''}`;
    return this.identity
      .csrf()
      .pipe(
        switchMap(() =>
          id ? this.http.put<PackVariant>(url, input) : this.http.post<PackVariant>(url, input),
        ),
      );
  }
}
