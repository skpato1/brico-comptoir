import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { switchMap } from 'rxjs';
import { IdentityApi, Account } from './identity-api';
import { Address } from './order-api';
export interface PrivacyView { account: Account; consent: { marketing: boolean; noticeVersion: string; updatedAt: string | null } }
export interface DataExport { schemaVersion: number; profile?: PrivacyView; orders: unknown[]; cart?: unknown[]; actions?: unknown[]; page: number; hasMore: boolean }
@Injectable({ providedIn: 'root' })
export class PrivacyApi {
  private readonly http = inject(HttpClient);
  private readonly identity = inject(IdentityApi);
  me() { return this.http.get<PrivacyView>('/api/v1/privacy/me'); }
  consent(marketing: boolean, noticeVersion: string) { return this.write<PrivacyView>('PUT', '/privacy/consent', { marketing, noticeVersion }); }
  export(password: string, guest = false, page = 0) { return this.write<DataExport>('POST', `/privacy/${guest ? 'guest/' : ''}export?page=${page}`, guest ? {} : { password }); }
  email(password: string, email: string) { return this.write<void>('POST', '/privacy/email/request', { password, email }); }
  confirm(token: string) { return this.write<void>('POST', '/privacy/email/confirm', { token }); }
  anonymize(password: string, guest = false) { return this.write<void>('POST', `/privacy/${guest ? 'guest/' : ''}anonymize`, guest ? { confirm: true } : { password, confirm: true }); }
  rectify(orderId: string, address: Address, guest = false) { return this.write<void>('PATCH', `/privacy/${guest ? 'guest/' : ''}orders/${encodeURIComponent(orderId)}/address`, address); }
  private write<T>(method: string, path: string, body: unknown) { return this.identity.csrf().pipe(switchMap(() => this.http.request<T>(method, `/api/v1${path}`, { body }))); }
}
