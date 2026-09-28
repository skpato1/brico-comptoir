import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { switchMap } from 'rxjs';
import { IdentityApi } from './identity-api';
export interface HomeContent {
  title: string;
  accent: string;
  description: string;
  solutionTitle: string;
  solutionDescription: string;
  productTitle: string;
  productDescription: string;
  version: number;
}
export interface DeliverySettings {
  enabled: boolean;
  amountTnd: string;
  governorates: string[];
  version: number;
}
export interface Stock {
  variantId: string;
  onHand: number;
  reserved: number;
}
export interface Adjustment {
  operationId: string;
  variantId: string;
  delta: number;
  reason: string;
}
export const GOVERNORATES = [
  'ARIANA',
  'BEJA',
  'BEN_AROUS',
  'BIZERTE',
  'GABES',
  'GAFSA',
  'JENDOUBA',
  'KAIROUAN',
  'KASSERINE',
  'KEBILI',
  'KEF',
  'MAHDIA',
  'MANOUBA',
  'MEDENINE',
  'MONASTIR',
  'NABEUL',
  'SFAX',
  'SIDI_BOUZID',
  'SILIANA',
  'SOUSSE',
  'TATAOUINE',
  'TOZEUR',
  'TUNIS',
  'ZAGHOUAN',
];
export function adminError(
  error: { status?: number },
  fallback = 'Opération refusée. Vérifiez les champs et leurs limites.',
): string {
  if (error.status === 401) return 'Session expirée. Reconnectez-vous avant de réessayer.';
  if (error.status === 403)
    return 'Accès refusé : votre rôle ou votre session ne permet pas cette opération.';
  if (error.status === 409)
    return 'Conflit : les données ont changé ou cette opération contredit les règles métier. Rechargez avant de réessayer.';
  if (error.status === 0 || (error.status ?? 0) >= 500)
    return 'Réponse indisponible. Vérifiez les données avant de réessayer.';
  return fallback;
}
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);
  private readonly identity = inject(IdentityApi);
  home(admin = false) {
    return this.http.get<HomeContent>('/api/v1/' + (admin ? 'admin/' : '') + 'content/home');
  }
  saveHome(value: HomeContent) {
    return this.write<HomeContent>('/content/home', value);
  }
  delivery() {
    return this.http.get<DeliverySettings>('/api/v1/admin/delivery');
  }
  saveDelivery(value: DeliverySettings) {
    return this.write<DeliverySettings>('/delivery', value);
  }
  stock(id: string) {
    return this.http.get<Stock>('/api/v1/admin/stock/' + id);
  }
  adjust(value: Adjustment) {
    return this.identity
      .csrf()
      .pipe(switchMap(() => this.http.post<Stock>('/api/v1/admin/stock/adjustments', value)));
  }
  private write<T>(path: string, body: unknown) {
    return this.identity
      .csrf()
      .pipe(switchMap(() => this.http.put<T>('/api/v1/admin' + path, body)));
  }
}
