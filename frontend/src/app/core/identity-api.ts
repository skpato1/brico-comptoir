import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, switchMap } from 'rxjs';

export type Role = 'CUSTOMER' | 'CATALOG_MANAGER' | 'ORDER_MANAGER' | 'ADMIN';
export interface Account { id: string; email: string; roles: Role[]; active: boolean }

@Injectable({ providedIn: 'root' })
export class IdentityApi {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/v1';

  csrf() { return this.http.get<void>(`${this.root}/auth/csrf`); }
  me() { return this.http.get<Account>(`${this.root}/auth/me`); }

  register(email: string, password: string) {
    return this.csrf().pipe(switchMap(() => this.http.post<Account>(`${this.root}/auth/register`, { email, password })));
  }

  login(email: string, password: string) {
    return this.csrf().pipe(switchMap(() => this.http.post<Account>(`${this.root}/auth/login`, { email, password })),
      switchMap((account) => this.csrf().pipe(map(() => account))));
  }

  logout() {
    return this.csrf().pipe(switchMap(() => this.http.post<void>(`${this.root}/auth/logout`, {})),
      switchMap(() => this.csrf()));
  }

  requestReset(email: string) {
    return this.csrf().pipe(switchMap(() =>
      this.http.post<void>(`${this.root}/auth/password-reset/request`, { email })));
  }

  completeReset(token: string, password: string) {
    return this.csrf().pipe(switchMap(() =>
      this.http.post<void>(`${this.root}/auth/password-reset/complete`, { token, password })));
  }
}
