import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { switchMap } from 'rxjs';
import { IdentityApi } from './identity-api';

export type CartKind = 'PRODUCT' | 'PACK';
export interface CartLine { kind: CartKind; offerId: string; quantity: number }
export interface CartItem extends CartLine {
  label: string | null; unitPrice: { amount: string; currency: 'TND' } | null;
  lineEstimate: { amount: string; currency: 'TND' } | null;
  offerVersion: number | null; parentVersion: number | null;
}
export interface CartView {
  version: number | null; items: CartItem[];
  subtotalEstimate: { amount: string; currency: 'TND' } | null;
  shortages: { skuId: string; required: number; available: number }[];
}

@Injectable({ providedIn: 'root' })
export class CartApi {
  private readonly http = inject(HttpClient);
  private readonly identity = inject(IdentityApi);
  private readonly root = '/api/v1/cart';

  estimate(items: CartLine[]) {
    return this.identity.csrf().pipe(switchMap(() => this.http.post<CartView>(
      `${this.root}/estimate`, { items })));
  }
  get() { return this.http.get<CartView>(this.root); }
  replace(version: number, items: CartLine[]) {
    return this.identity.csrf().pipe(switchMap(() => this.http.put<CartView>(
      this.root, { version, items })));
  }
  merge(mergeId: string, items: CartLine[]) {
    return this.identity.csrf().pipe(switchMap(() => this.http.post<CartView>(
      `${this.root}/merge`, { mergeId, items })));
  }
}
