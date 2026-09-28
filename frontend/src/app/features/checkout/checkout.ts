import { Component, EventEmitter, Input, OnChanges, Output, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { Account } from '../../core/identity-api';
import { CartState } from '../../core/cart-state';
import { Address, checkoutItems, OrderApi, OrderView, PlaceInput, PreviewInput, Summary } from '../../core/order-api';

@Component({ selector: 'app-checkout', imports: [FormsModule], templateUrl: './checkout.html', styleUrl: './checkout.scss' })
export class CheckoutComponent implements OnChanges {
  @Input() account: Account | null = null;
  @Input() historyOnly = false;
  @Output() placed = new EventEmitter<OrderView>();
  readonly busy = signal(false);
  readonly error = signal('');
  readonly summary = signal<Summary | null>(null);
  readonly order = signal<OrderView | null>(null);
  readonly orders = signal<OrderView[]>([]);
  readonly uncertain = signal(false);
  readonly statuses: Record<string, string> = { CONFIRMED: 'Confirmée', PREPARING: 'En préparation', SHIPPED: 'Expédiée',
    DELIVERED: 'Livrée', CANCELLED: 'Annulée' };
  readonly governorates = ['ARIANA', 'BEJA', 'BEN_AROUS', 'BIZERTE', 'GABES', 'GAFSA', 'JENDOUBA', 'KAIROUAN',
    'KASSERINE', 'KEBILI', 'KEF', 'MAHDIA', 'MANOUBA', 'MEDENINE', 'MONASTIR', 'NABEUL', 'SFAX', 'SIDI_BOUZID',
    'SILIANA', 'SOUSSE', 'TATAOUINE', 'TOZEUR', 'TUNIS', 'ZAGHOUAN'];
  address: Address = this.emptyAddress();
  page = 0;
  filter = 'CONFIRMED';
  private input: PlaceInput | null = null;
  private key = '';
  private cartVersion: number | null = null;
  private generation = 0;
  constructor(readonly cart: CartState, private readonly api: OrderApi) { }
  ngOnChanges(): void {
    this.generation++; this.busy.set(false); this.uncertain.set(false); this.summary.set(null); this.order.set(null);
    this.orders.set([]); this.input = null; this.error.set(''); this.address = this.emptyAddress();
  }
  manager(): boolean { return !!this.account?.roles.some(r => r === 'ADMIN' || r === 'ORDER_MANAGER'); }
  buyer(): boolean { return !this.account || this.account.roles.includes('CUSTOMER'); }
  resetPreview(): void { if (!this.uncertain() && !this.busy()) { this.summary.set(null); this.input = null; } }
  submitAddress(form: NgForm): void {
    if (form.invalid) { form.control.markAllAsTouched(); this.error.set('Vérifiez les champs indiqués avant de continuer.');
      document.querySelector<HTMLElement>('app-checkout input.ng-invalid')?.focus(); return; }
    this.preview();
  }
  preview(): void {
    if (this.busy() || this.uncertain() || this.cart.busy() || this.cart.recoveryPending()) return;
    const items = checkoutItems(this.cart.view().items);
    if (!items) { this.error.set('Actualisez le panier et retirez les offres indisponibles avant de commander.'); return; }
    const input: PreviewInput = { items, address: { ...this.address } };
    this.cartVersion = this.cart.view().version;
    this.busy.set(true); this.error.set(''); this.summary.set(null); this.order.set(null);
    const generation = ++this.generation;
    this.api.preview(input).subscribe({
      next: summary => { if (generation === this.generation) {
        this.summary.set(summary); this.input = { ...input, quoteHash: summary.quoteHash };
        this.key = crypto.randomUUID(); this.busy.set(false);
      } },
      error: response => { if (generation === this.generation) { this.busy.set(false); this.error.set(this.message(response)); } },
    });
  }
  place(): void {
    if (!this.input || this.busy()) return;
    if (!this.uncertain() && (JSON.stringify(checkoutItems(this.cart.view().items)) !== JSON.stringify(this.input.items)
        || this.cart.view().version !== this.cartVersion)) {
      this.summary.set(null); this.input = null;
      this.error.set('Le panier a été modifié. Demandez un nouveau récapitulatif avant de confirmer.'); return;
    }
    this.busy.set(true); this.error.set('');
    const input = this.input;
    const generation = ++this.generation;
    this.api.place(input, this.key).subscribe({
      next: order => { if (generation === this.generation) {
        this.order.set(order); this.busy.set(false); this.uncertain.set(false); this.summary.set(null); this.input = null;
        this.cart.clearPurchased(input.items, this.cartVersion); this.address = this.emptyAddress();
        this.placed.emit(order);
      } },
      error: response => { if (generation === this.generation) {
        this.busy.set(false);
        const uncertain = !response.status || response.status >= 500;
        this.uncertain.set(uncertain);
        this.error.set(uncertain ? 'Réponse incertaine. Réessayez la même confirmation : elle ne créera pas une deuxième commande.'
          : this.message(response));
        if (!uncertain) { this.summary.set(null); this.input = null; }
      } },
    });
  }
  loadOrders(page = 0): void {
    if (this.busy() || this.uncertain()) return;
    this.page = page; this.busy.set(true); this.error.set(''); const generation = ++this.generation;
    this.api.history(this.manager(), page, this.manager() ? this.filter : '').subscribe({
      next: orders => { if (generation === this.generation) { this.orders.set(orders); this.busy.set(false); } },
      error: response => { if (generation === this.generation) { this.error.set(this.message(response)); this.busy.set(false); } },
    });
  }
  transition(order: OrderView, action: string, manager = false): void {
    if (this.busy() || this.uncertain()) return;
    this.busy.set(true); this.error.set(''); const generation = ++this.generation;
    this.api.transition(order.id, action, manager).subscribe({
      next: changed => { if (generation === this.generation) {
        if (this.order()?.id === changed.id) this.order.set(changed);
        this.orders.update(list => list.map(o => o.id === changed.id ? changed : o)); this.busy.set(false);
      } },
      error: response => { if (generation === this.generation) { this.error.set(this.message(response)); this.busy.set(false); } },
    });
  }
  private message(response: { error?: { code?: string } }): string {
    const messages: Record<string, string> = {
      INVALID_CHECKOUT: 'Vérifiez l’adresse tunisienne, le téléphone à huit chiffres et le code postal à quatre chiffres.',
      DELIVERY_ZONE_UNAVAILABLE: 'Ce gouvernorat n’est pas encore desservi.',
      CHECKOUT_UNAVAILABLE: 'La livraison n’est pas configurée. La commande est momentanément indisponible.',
      OFFER_CHANGED: 'Une offre, sa composition, son prix ou votre session a changé. Actualisez le panier et demandez un nouveau récapitulatif.',
      STOCK_UNAVAILABLE: 'Stock insuffisant pour cette sélection. Actualisez le panier et modifiez les quantités.',
      IDEMPOTENCY_CONFLICT: 'Cette confirmation a déjà été utilisée pour un autre achat.',
      INVALID_TRANSITION: 'Cette action n’est plus possible dans l’état actuel de la commande. Actualisez la liste.',
      ORDER_NOT_FOUND: 'Commande introuvable ou inaccessible dans cette session.',
    };
    return messages[response.error?.code ?? ''] ?? 'Action impossible. Vérifiez votre session puis réessayez.';
  }
  private emptyAddress(): Address { return { recipient: '', phone: '', street: '', city: '', postalCode: '', governorate: 'TUNIS', country: 'TN' }; }
}
