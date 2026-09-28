import { DestroyRef, effect, inject, Injectable, InjectionToken, signal } from '@angular/core';
import { IdentityApi } from './identity-api';
import { SessionState } from './session-state';
import { timeout } from 'rxjs';

export interface OrderEvent { orderId: string; createdAt: string; type: 'ORDER_CREATED' }
export interface EventConnection {
  addEventListener(name: string, listener: (event: MessageEvent) => void): void;
  onerror: (() => void) | null;
  close(): void;
}
export const ORDER_EVENT_SOURCE = new InjectionToken<(url: string) => EventConnection>('order event source', {
  providedIn: 'root', factory: () => url => new EventSource(url) as unknown as EventConnection,
});

@Injectable({ providedIn: 'root' })
export class OrderNotifications {
  private readonly session = inject(SessionState);
  private readonly identity = inject(IdentityApi);
  private readonly factory = inject(ORDER_EVENT_SOURCE);
  private readonly destroy = inject(DestroyRef);
  readonly events = signal<OrderEvent[]>([]);
  readonly status = signal('');
  private source: EventConnection | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private generation = 0;
  private delay = 3000;
  constructor() {
    effect(onCleanup => {
      const account = this.session.account();
      this.stop(); this.events.set([]); this.status.set('');
      if (!account?.roles.some(r => r === 'ADMIN' || r === 'ORDER_MANAGER')) return;
      const generation = this.generation;
      this.connect(account.id, generation);
      onCleanup(() => this.stop());
    });
    this.destroy.onDestroy(() => this.stop());
  }
  private connect(account: string, generation: number) {
    if (generation !== this.generation) return;
    this.status.set('Connexion aux notifications…');
    const key = 'brico-order-events:' + account;
    let cursor = '';
    try { cursor = sessionStorage.getItem(key) ?? ''; } catch { }
    if (!/^\d+$/.test(cursor)) cursor = '';
    const remember = (id: string) => { if (/^\d+$/.test(id)) {
      cursor = id; try { sessionStorage.setItem(key, id); } catch { }
    } };
    let source: EventConnection;
    try { source = this.factory('/api/v1/admin/order-events/stream' + (cursor ? '?after=' + cursor : '')); }
    catch { this.status.set('Notifications indisponibles dans ce navigateur. La liste des commandes reste accessible.'); return; }
    this.source = source;
    source.addEventListener('ready', event => {
      if (generation !== this.generation) return;
      remember(event.lastEventId); this.delay = 3000; this.status.set('Notifications connectées.');
    });
    source.addEventListener('order-created', event => {
      if (generation !== this.generation || !/^\d+$/.test(event.lastEventId)
        || cursor && BigInt(event.lastEventId) <= BigInt(cursor)) return;
      try {
        const data = JSON.parse(event.data) as OrderEvent;
        if (data.type !== 'ORDER_CREATED' || !/^[0-9a-f-]{36}$/i.test(data.orderId)) return;
        this.events.update(events => [data, ...events].slice(0, 10)); remember(event.lastEventId);
      } catch { }
    });
    source.onerror = () => {
      source.close(); if (this.source === source) this.source = null;
      if (generation !== this.generation) return;
      this.status.set('Connexion interrompue. Reprise des notifications en cours…');
      this.identity.me().pipe(timeout(5000)).subscribe({
        next: account => {
          if (generation !== this.generation) return;
          if (!account.roles.some(r => r === 'ADMIN' || r === 'ORDER_MANAGER')) {
            this.status.set('Notifications refusées avec votre rôle.'); return;
          }
          this.retry(account.id, generation);
        },
        error: error => {
          if (generation !== this.generation) return;
          if (error.status === 401 || error.status === 403) this.status.set('Session expirée ou accès refusé. Reconnectez-vous.');
          else this.retry(account, generation);
        },
      });
    };
  }
  private retry(account: string, generation: number) {
    this.timer = setTimeout(() => this.connect(account, generation), this.delay);
    this.delay = Math.min(30000, this.delay * 2);
  }
  private stop() {
    ++this.generation; this.source?.close(); this.source = null;
    if (this.timer) clearTimeout(this.timer); this.timer = null; this.delay = 3000;
  }
}
