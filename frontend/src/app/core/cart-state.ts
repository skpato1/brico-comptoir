import { inject, Injectable, signal } from '@angular/core';
import { Account } from './identity-api';
import { CartApi, CartItem, CartKind, CartLine, CartView } from './cart-api';

interface GuestCart { mergeId: string; items: CartLine[] }
const KEY = 'bricocomptoir.guest-cart.v1';
const EMPTY: CartView = { version: null, items: [], subtotalEstimate: null, shortages: [] };

@Injectable({ providedIn: 'root' })
export class CartState {
  private readonly api = inject(CartApi);
  private guest = this.readGuest();
  private generation = 0;
  readonly connected = signal(false);
  readonly view = signal<CartView>(EMPTY);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly recoveryPending = signal(false);
  readonly storageUnavailable = signal(false);

  activate(account: Account | null): void {
    this.generation++;
    this.connected.set(!!account?.roles.includes('CUSTOMER'));
    this.error.set('');
    this.recoveryPending.set(false);
    if (this.connected()) { this.view.set(EMPTY); this.loadCustomer(); }
    else { this.busy.set(false); this.refreshGuest(); }
  }

  add(kind: CartKind, offerId: string): void {
    const current = this.lines().find(line => line.kind === kind && line.offerId === offerId)?.quantity ?? 0;
    this.change(kind, offerId, current + 1);
  }

  change(kind: CartKind, offerId: string, quantity: number): void {
    if (!Number.isInteger(quantity) || quantity < 0 || quantity > 999) {
      this.error.set('La quantité doit être entière entre 1 et 999. Utilisez « Retirer » pour supprimer une ligne.'); return;
    }
    const next = this.lines().filter(line => !(line.kind === kind && line.offerId === offerId));
    if (quantity) next.push({ kind, offerId, quantity });
    if (next.length > 100) { this.error.set('Le panier est limité à 100 lignes.'); return; }
    if (this.connected()) {
      const version = this.view().version;
      if (this.busy() || version === null) { this.error.set('Patientez pendant le chargement du panier.'); return; }
      this.busy.set(true); this.error.set('');
      const generation = ++this.generation;
      this.api.replace(version, next).subscribe({
        next: view => { if (generation === this.generation) {
          this.view.set(view); this.busy.set(false);
        } },
        error: response => { if (generation === this.generation) {
          this.busy.set(false);
          this.error.set(response.status === 409
            ? 'Le panier a changé dans un autre onglet. Rechargez-le avant de réessayer.'
            : 'Modification impossible. Réessayez.');
        } },
      });
    } else {
      this.guest = { mergeId: crypto.randomUUID(), items: next };
      try { localStorage.setItem(KEY, JSON.stringify(this.guest)); this.storageUnavailable.set(false); }
      catch { this.storageUnavailable.set(true);
        this.error.set('Impossible de conserver ce panier dans le navigateur. Il sera perdu au rechargement.'); }
      this.refreshGuest();
    }
  }

  retryRecovery(): void {
    if (this.connected() && this.guest.items.length && !this.busy()) this.loadCustomer();
  }

  refresh(): void { if (this.connected()) this.loadCustomer(); else this.refreshGuest(); }

  clearPurchased(purchased: CartLine[], version: number | null): void {
    const signature = (lines: CartLine[]) => JSON.stringify(lines.map(({ kind, offerId, quantity }) =>
      ({ kind, offerId, quantity })).sort((a, b) => (a.kind + a.offerId).localeCompare(b.kind + b.offerId)));
    if (signature(this.lines()) !== signature(purchased) || this.view().version !== version) return;
    if (this.connected()) {
      if (version === null) return;
      const generation = ++this.generation;
      this.busy.set(true);
      this.api.replace(version, []).subscribe({
        next: view => { if (generation === this.generation) { this.view.set(view); this.busy.set(false); } },
        error: () => { if (generation === this.generation) {
          this.busy.set(false); this.error.set('Commande créée. Le panier a été conservé ; actualisez-le avant un nouvel achat.');
        } },
      });
    } else {
      this.guest = { mergeId: crypto.randomUUID(), items: [] };
      try { localStorage.removeItem(KEY); } catch { this.storageUnavailable.set(true); }
      this.refreshGuest();
    }
  }

  private lines(): CartLine[] {
    return this.connected() ? this.view().items.map(({ kind, offerId, quantity }) => ({ kind, offerId, quantity }))
      : this.guest.items;
  }
  private loadCustomer(mergeGuest = true): void {
    const generation = ++this.generation;
    const shouldMerge = mergeGuest && this.guest.items.length > 0;
    this.busy.set(true);
    (shouldMerge ? this.api.merge(this.guest.mergeId, this.guest.items) : this.api.get()).subscribe({
      next: view => { if (generation === this.generation) {
        this.view.set(view); this.busy.set(false); this.error.set(''); this.recoveryPending.set(false);
        if (shouldMerge) {
          this.guest = { mergeId: crypto.randomUUID(), items: [] };
          this.storageUnavailable.set(false);
          try { localStorage.removeItem(KEY); } catch { /* in-memory state remains empty */ }
        }
      } },
      error: response => { if (generation === this.generation) {
        this.busy.set(false); this.recoveryPending.set(shouldMerge);
        this.error.set(shouldMerge ? response.status === 400
          ? 'Fusion refusée : limite de 100 lignes ou de 999 unités par ligne. Vos articles visiteurs sont conservés ; déconnectez-vous pour les modifier.'
          : 'Reprise du panier impossible. Vos articles visiteurs sont conservés ; réessayez avant de les modifier.'
          : 'Panier indisponible. Réessayez.');
      } },
    });
  }
  private refreshGuest(): void {
    const generation = ++this.generation;
    if (!this.guest.items.length) { this.busy.set(false); this.view.set({ ...EMPTY, subtotalEstimate: { amount: '0.000', currency: 'TND' } }); return; }
    this.busy.set(true);
    const previous = this.view().items;
    this.view.set({ ...EMPTY, items: this.guest.items.map(line => {
      const old = previous.find(item => item.kind === line.kind && item.offerId === line.offerId);
      return { ...line, label: old?.label ?? null, unitPrice: old?.unitPrice ?? null,
        lineEstimate: null, offerVersion: old?.offerVersion ?? null, parentVersion: old?.parentVersion ?? null } as CartItem;
    }) });
    this.api.estimate(this.guest.items).subscribe({
      next: view => { if (generation === this.generation) { this.view.set(view); this.busy.set(false);
        if (!this.storageUnavailable()) this.error.set(''); } },
      error: () => { if (generation === this.generation) { this.busy.set(false);
        this.error.set('Estimation indisponible. Vos articles restent dans le navigateur.'); } },
    });
  }
  private readGuest(): GuestCart {
    try {
      const raw = localStorage.getItem(KEY);
      if (raw) {
        const value = JSON.parse(raw) as GuestCart;
        if (typeof value.mergeId === 'string' && /^[0-9a-f-]{36}$/i.test(value.mergeId)
            && Array.isArray(value.items) && value.items.length <= 100
            && value.items.every(line => (line.kind === 'PRODUCT' || line.kind === 'PACK')
              && typeof line.offerId === 'string' && /^[0-9a-f-]{36}$/i.test(line.offerId)
              && Number.isInteger(line.quantity) && line.quantity > 0 && line.quantity <= 999)) return value;
      }
    } catch { /* corrupt or unavailable storage is treated as empty */ }
    return { mergeId: crypto.randomUUID(), items: [] };
  }
}
