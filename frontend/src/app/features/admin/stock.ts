import {
  Component,
  DestroyRef,
  HostListener,
  inject,
  Injectable,
  OnInit,
  signal,
} from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AdminApi, Adjustment, Stock, adminError } from '../../core/admin-api';
import { CatalogApi, Page, Product, Variant } from '../../core/catalog-api';
import { AdminPager } from '../../shared/admin-pager';
@Injectable({ providedIn: 'root' })
export class AdjustmentState {
  readonly pending = signal<Adjustment | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  private readonly api = inject(AdminApi);
  submit(input: Adjustment) {
    if (this.busy()) return;
    this.pending.set(this.pending() ?? { ...input });
    this.busy.set(true);
    this.error.set('');
    this.success.set('');
    this.api.adjust(this.pending()!).subscribe({
      next: () => {
        this.pending.set(null);
        this.busy.set(false);
        this.success.set('Ajustement enregistré. Rechargez les soldes pour voir l’état actuel.');
      },
      error: (e) => {
        this.busy.set(false);
        if (e.status > 0 && e.status < 500) this.pending.set(null);
        this.error.set(
          adminError(e, 'Ajustement refusé : vérifiez le motif, la quantité et le stock réservé.'),
        );
      },
    });
  }
}
@Component({
  selector: 'app-admin-stock',
  imports: [FormsModule, AdminPager],
  templateUrl: './stock.html',
})
export class AdminStock implements OnInit {
  private readonly catalog = inject(CatalogApi);
  private readonly api = inject(AdminApi);
  private readonly destroy = inject(DestroyRef);
  readonly adjustment = inject(AdjustmentState);
  readonly result = signal<Page<Product> | null>(null);
  readonly balances = signal<Record<string, Stock>>({});
  readonly busy = signal(false);
  readonly error = signal('');
  readonly selected = signal<Variant | null>(null);
  q = '';
  page = 0;
  delta = 0;
  reason = '';
  ngOnInit() {
    this.load();
  }
  load(page = 0) {
    if (this.busy()) return;
    this.page = page;
    this.busy.set(true);
    this.error.set('');
    this.balances.set({});
    this.catalog
      .products(
        {
          q: this.q,
          categoryId: '',
          brandId: '',
          minPrice: '',
          maxPrice: '',
          sort: 'name',
          page,
          size: 10,
        },
        true,
      )
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (result) => {
          this.result.set(result);
          const ids = result.items.flatMap((p) => p.variants.map((v) => v.id));
          (ids.length ? forkJoin(ids.map((id) => this.api.stock(id))) : of([] as Stock[]))
            .pipe(takeUntilDestroyed(this.destroy))
            .subscribe({
              next: (stocks) => {
                this.balances.set(Object.fromEntries(stocks.map((s) => [s.variantId, s])));
                this.busy.set(false);
              },
              error: (e) => {
                this.busy.set(false);
                this.error.set(adminError(e));
              },
            });
        },
        error: (e) => {
          this.busy.set(false);
          this.result.set(null);
          this.error.set(adminError(e));
        },
      });
  }
  choose(v: Variant) {
    this.selected.set(v);
    this.delta = 0;
    this.reason = '';
  }
  submit(form: NgForm) {
    if (form.invalid || !Number.isSafeInteger(this.delta) || this.delta === 0 || !this.selected()) {
      form.control.markAllAsTouched();
      this.error.set('Choisissez un SKU, une quantité entière non nulle et un motif.');
      return;
    }
    this.error.set('');
    this.adjustment.submit({
      operationId: crypto.randomUUID(),
      variantId: this.selected()!.id,
      delta: this.delta,
      reason: this.reason,
    });
  }
  retry() {
    const input = this.adjustment.pending();
    if (input) this.adjustment.submit(input);
  }
  @HostListener('window:beforeunload', ['$event']) beforeUnload(event: BeforeUnloadEvent) {
    if (this.adjustment.pending()) {
      event.preventDefault();
      event.returnValue = '';
    }
  }
}
