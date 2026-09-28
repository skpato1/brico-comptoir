import { inject, Injectable, signal } from '@angular/core';
import { Account, IdentityApi } from './identity-api';
import { CartState } from './cart-state';
@Injectable({ providedIn: 'root' })
export class SessionState {
  private readonly api = inject(IdentityApi);
  private readonly cart = inject(CartState);
  readonly account = signal<Account | null>(null);
  readonly ready = signal(false);
  readonly error = signal('');
  start(): void {
    this.error.set('');
    this.ready.set(false);
    this.api.me().subscribe({
      next: (account) => this.set(account),
      error: (response) => {
        if (response.status === 401) this.set(null);
        else this.error.set('La boutique ne répond pas. Réessayez dans un instant.');
      },
    });
  }
  set(account: Account | null): void {
    this.account.set(account);
    this.cart.activate(account);
    this.ready.set(true);
    this.error.set('');
  }
}
