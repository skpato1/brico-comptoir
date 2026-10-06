import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Account } from '../../core/identity-api';
import { adminError } from '../../core/admin-api';

interface SupportCart {
  customerId: string;
  version: number;
  lines: { kind: 'PRODUCT' | 'PACK'; offerId: string; quantity: number }[];
}

@Component({ selector: 'app-admin-support', imports: [FormsModule], templateUrl: './support.html' })
export class AdminSupport {
  private readonly http = inject(HttpClient);
  private readonly destroy = inject(DestroyRef);
  readonly account = signal<Account | null>(null);
  readonly cart = signal<SupportCart | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  email = '';
  search(form: NgForm) {
    if (form.invalid || this.busy()) { form.control.markAllAsTouched(); return; }
    this.busy.set(true); this.error.set(''); this.account.set(null); this.cart.set(null);
    this.http.get<Account>('/api/v1/admin/accounts/lookup', { params: { email: this.email.trim() } })
      .pipe(takeUntilDestroyed(this.destroy)).subscribe({
        next: account => {
          this.account.set(account);
          if (!account.roles.includes('CUSTOMER')) { this.busy.set(false); return; }
          this.http.get<SupportCart>(`/api/v1/admin/support/carts/${account.id}`)
            .pipe(takeUntilDestroyed(this.destroy)).subscribe({
              next: cart => { this.cart.set(cart); this.busy.set(false); },
              error: e => { this.busy.set(false); this.error.set(adminError(e)); },
            });
        },
        error: e => { this.busy.set(false); this.error.set(e.status === 400 ? 'Aucun compte pour cet email.' : adminError(e)); },
      });
  }
}
