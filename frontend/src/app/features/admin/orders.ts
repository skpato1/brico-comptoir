import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { OrderApi, OrderView } from '../../core/order-api';
import { adminError } from '../../core/admin-api';
@Component({
  selector: 'app-admin-orders',
  imports: [FormsModule, DatePipe],
  templateUrl: './orders.html',
})
export class AdminOrders implements OnInit {
  private readonly api = inject(OrderApi);
  private readonly destroy = inject(DestroyRef);
  readonly items = signal<OrderView[]>([]);
  readonly selected = signal<OrderView | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly statuses: Record<string, string> = {
    CONFIRMED: 'Confirmée',
    PREPARING: 'En préparation',
    SHIPPED: 'Expédiée',
    DELIVERED: 'Livrée',
    CANCELLED: 'Annulée',
  };
  filter = 'CONFIRMED';
  page = 0;
  private generation = 0;
  ngOnInit() {
    this.load();
  }
  load(page = 0) {
    if (this.busy()) return;
    this.page = page;
    this.busy.set(true);
    this.error.set('');
    this.selected.set(null);
    const g = ++this.generation;
    this.api
      .history(true, page, this.filter)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (v) => {
          if (g === this.generation) {
            this.items.set(v);
            this.busy.set(false);
          }
        },
        error: (e) => {
          if (g === this.generation) {
            this.items.set([]);
            this.busy.set(false);
            this.error.set(adminError(e));
          }
        },
      });
  }
  transition(entry: OrderView, action: string) {
    // Server remains responsible for authorizing every transition.
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.success.set('');
    this.api
      .transition(entry.id, action, true)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.success.set('Opération enregistrée.');
          this.load(this.page);
        },
        error: (e) => {
          this.busy.set(false);
          this.error.set(adminError(e, 'Transition refusée. Rechargez la commande.'));
        },
      });
  }
  open(entry: OrderView) {
    if (this.busy()) return;
    this.busy.set(true);this.error.set('');this.selected.set(null);
    this.api.adminGet(entry.id).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: v => { this.selected.set(v);this.busy.set(false); },
      error: e => { this.error.set(adminError(e));this.busy.set(false); },
    });
  }
}
