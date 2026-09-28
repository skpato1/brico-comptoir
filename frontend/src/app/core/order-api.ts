import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { switchMap } from 'rxjs';
import { IdentityApi } from './identity-api';
import { CartItem, CartKind } from './cart-api';

export interface Address {
  email?: string | null;
  recipient: string; phone: string; street: string; city: string; postalCode: string; governorate: string; country: 'TN';
}
export interface CheckoutItem {
  kind: CartKind; offerId: string; quantity: number; offerVersion: number; parentVersion: number;
}
export interface PreviewInput { items: CheckoutItem[]; address: Address }
export interface PlaceInput extends PreviewInput { quoteHash: string }
export interface Money { amount: string; currency: 'TND' }
export interface Summary {
  address: Address; items: { kind: CartKind; offerId: string; label: string; quantity: number;
    unitPrice: Money; lineTotal: Money; components: { skuId: string; sku: string; label: string; quantity: number }[] }[];
  subtotal: Money; delivery: Money; total: Money; quoteHash: string;
}
export type OrderStatus = 'CONFIRMED' | 'PREPARING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';
export interface OrderView { id: string; status: OrderStatus; paymentMethod: 'CASH_ON_DELIVERY'; createdAt: string; summary: Summary }
export function checkoutItems(items: CartItem[]): CheckoutItem[] | null {
  if (!items.length || items.some(i => i.offerVersion === null || i.parentVersion === null)) return null;
  return items.map(i => ({ kind: i.kind, offerId: i.offerId, quantity: i.quantity,
    offerVersion: i.offerVersion!, parentVersion: i.parentVersion! }));
}
@Injectable({ providedIn: 'root' })
export class OrderApi {
  private readonly http = inject(HttpClient);
  private readonly identity = inject(IdentityApi);
  preview(body: PreviewInput) { return this.mutate<Summary>('/checkout/preview', body); }
  get(id: string) { return this.http.get<OrderView>(`/api/v1/orders/${id}`); }
  adminGet(id: string) { return this.http.get<OrderView>(`/api/v1/admin/orders/${id}`); }
  place(body: PlaceInput, key: string) {
    return this.identity.csrf().pipe(switchMap(() => this.http.post<OrderView>('/api/v1/orders', body,
      { headers: { 'Idempotency-Key': key } })));
  }
  history(manager: boolean, page = 0, status = '') {
    return this.http.get<OrderView[]>(`/api/v1/${manager ? 'admin/' : ''}orders`,
      { params: { page, size: 20, ...(status ? { status } : {}) } });
  }
  transition(id: string, action: string, manager: boolean) {
    return this.mutate<OrderView>(`/${manager ? 'admin/' : ''}orders/${id}/${action}`, {});
  }
  private mutate<T>(path: string, body: unknown) {
    return this.identity.csrf().pipe(switchMap(() => this.http.post<T>(`/api/v1${path}`, body)));
  }
}
