import { Component, inject, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Account, IdentityApi } from './identity-api';
import { SessionState } from './session-state';
import { EventConnection, ORDER_EVENT_SOURCE, OrderNotifications } from './order-notifications';

@Component({ template: '' })
class Harness { readonly notifications = inject(OrderNotifications); }
class FakeSource implements EventConnection {
  readonly listeners = new Map<string, (e: MessageEvent) => void>();
  onerror: (() => void) | null = null;
  closed = false;
  addEventListener(name: string, listener: (e: MessageEvent) => void) { this.listeners.set(name, listener); }
  close() { this.closed = true; }
  emit(name: string, id: string, data = '{}') {
    this.listeners.get(name)?.(new MessageEvent(name, { lastEventId: id, data }));
  }
}
const manager: Account = { id: 'manager', email: 'manager@test.invalid', roles: ['ORDER_MANAGER'], active: true };
describe('Order event notifications', () => {
  afterEach(() => { TestBed.resetTestingModule(); vi.useRealTimers(); sessionStorage.clear(); });
  async function start(account: Account | null = manager) {
    const session = { account: signal<Account | null>(account) };
    const sources: FakeSource[] = [], urls: string[] = [];
    const api = { me: vi.fn(() => of(manager)) };
    const factory = (url: string) => { urls.push(url); const source = new FakeSource(); sources.push(source); return source; };
    TestBed.configureTestingModule({ imports: [Harness], providers: [
      { provide: SessionState, useValue: session }, { provide: IdentityApi, useValue: api },
      { provide: ORDER_EVENT_SOURCE, useValue: factory },
    ] });
    const fixture = TestBed.createComponent(Harness); fixture.detectChanges(); await fixture.whenStable();
    return { fixture, session, api, sources, urls, notifications: fixture.componentInstance.notifications };
  }
  it('never subscribes for a customer or catalogue manager', async () => {
    const state = await start({ ...manager, roles: ['CATALOG_MANAGER'] });
    expect(state.sources).toHaveLength(0);
    state.session.account.set({ ...manager, roles: ['CUSTOMER'] });
    state.fixture.detectChanges(); await state.fixture.whenStable(); expect(state.sources).toHaveLength(0);
  });
  it('replays after the saved cursor and ignores duplicates', async () => {
    sessionStorage.setItem('brico-order-events:manager', '7');
    const state = await start(); expect(state.urls[0]).toContain('?after=7');
    const data = JSON.stringify({ orderId: '11111111-1111-4111-8111-111111111111', type: 'ORDER_CREATED', createdAt: '2026-09-28T12:00:00Z' });
    state.sources[0].emit('ready', '7'); state.sources[0].emit('order-created', '8', data); state.sources[0].emit('order-created', '8', data);
    expect(state.notifications.events()).toHaveLength(1);
    expect(sessionStorage.getItem('brico-order-events:manager')).toBe('8');
  });
  it('reconnects after interruption with the last event and closes on logout', async () => {
    const state = await start(); vi.useFakeTimers();
    state.sources[0].emit('ready', '12'); state.sources[0].onerror?.();
    expect(state.sources[0].closed).toBe(true); await vi.advanceTimersByTimeAsync(3000);
    expect(state.urls[1]).toContain('?after=12');
    state.session.account.set(null); state.fixture.detectChanges();
    expect(state.sources[1].closed).toBe(true); expect(state.notifications.events()).toHaveLength(0);
  });
  it('does not keep reconnecting after an authorization refusal', async () => {
    const state = await start(); vi.useFakeTimers();
    state.api.me.mockReturnValue(throwError(() => ({ status: 401 })));
    state.sources[0].onerror?.(); await vi.advanceTimersByTimeAsync(60000);
    expect(state.sources).toHaveLength(1); expect(state.notifications.status()).toContain('Session expirée');
  });
});
