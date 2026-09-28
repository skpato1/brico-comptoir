import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { KeyValuePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CatalogApi, Product, ProductImage } from '../../core/catalog-api';
import { Pack, PacksApi, PackVariant } from '../../core/packs-api';
import { CartState } from '../../core/cart-state';
import { SessionState } from '../../core/session-state';
import { ProductPhoto } from '../../shared/product-photo';
export function omittedComponents(
  pack: Pack,
  selected: PackVariant,
): { variantId: string; quantity: number }[] {
  const maxima = new Map<string, number>();
  for (const variant of pack.variants)
    for (const c of variant.components)
      maxima.set(c.variantId, Math.max(maxima.get(c.variantId) ?? 0, c.quantity));
  return [...maxima]
    .map(([variantId, quantity]) => ({
      variantId,
      quantity:
        quantity - (selected.components.find((c) => c.variantId === variantId)?.quantity ?? 0),
    }))
    .filter((c) => c.quantity > 0);
}
@Component({
  selector: 'app-detail',
  imports: [RouterLink, FormsModule, KeyValuePipe, ProductPhoto],
  templateUrl: './detail.html',
  styleUrl: './detail.scss',
})
export class DetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(CatalogApi);
  private readonly packsApi = inject(PacksApi);
  private readonly destroy = inject(DestroyRef);
  readonly cart = inject(CartState);
  readonly session = inject(SessionState);
  readonly packMode = this.route.snapshot.data['kind'] === 'packs';
  readonly product = signal<Product | null>(null);
  readonly pack = signal<Pack | null>(null);
  readonly images = signal<ProductImage[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly notice = signal('');
  selected = '';
  quantity = 1;
  photo = 0;
  private generation = 0;
  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroy)).subscribe(() => this.load());
  }
  load(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    const generation = ++this.generation;
    this.loading.set(true);
    this.error.set('');
    this.notice.set('');
    this.images.set([]);
    this.photo = 0;
    this.quantity = 1;
    if (this.packMode) {
      this.packsApi
        .detail(id)
        .pipe(takeUntilDestroyed(this.destroy))
        .subscribe({
          next: (p) => {
            if (generation === this.generation) {
              this.pack.set(p);
              this.selected = p.variants[0]?.id ?? '';
              this.loading.set(false);
            }
          },
          error: () => this.fail(generation),
        });
    } else {
      this.api
        .detail(id)
        .pipe(takeUntilDestroyed(this.destroy))
        .subscribe({
          next: (p) => {
            if (generation !== this.generation) return;
            this.product.set(p);
            this.selected = p.variants[0]?.id ?? '';
            this.loading.set(false);
            this.api
              .publicImages([p.id])
              .pipe(takeUntilDestroyed(this.destroy))
              .subscribe({
                next: (v) => {
                  if (generation === this.generation)
                    this.images.set(
                      [...(v[p.id] ?? [])].sort(
                        (a, b) =>
                          Number(b.primary) - Number(a.primary) || a.sortOrder - b.sortOrder,
                      ),
                    );
                },
                error: () => {},
              });
          },
          error: () => this.fail(generation),
        });
    }
  }
  variant() {
    return (this.packMode ? this.pack()?.variants : this.product()?.variants)?.find(
      (v) => v.id === this.selected,
    );
  }
  packVariant() {
    return this.pack()?.variants.find((v) => v.id === this.selected);
  }
  omitted() {
    const p = this.pack(),
      v = this.packVariant();
    return p && v ? omittedComponents(p, v) : [];
  }
  label(id: string): string {
    return this.pack()?.componentNames?.[id] ?? 'Détail de cet article indisponible';
  }
  canBuy(): boolean {
    return !this.session.account() || !!this.session.account()?.roles.includes('CUSTOMER');
  }
  add(): void {
    if (
      !this.selected ||
      this.cart.busy() ||
      !Number.isInteger(this.quantity) ||
      this.quantity < 1 ||
      this.quantity > 999
    )
      return;
    const kind = this.packMode ? 'PACK' : 'PRODUCT';
    const current =
      this.cart.view().items.find((i) => i.kind === kind && i.offerId === this.selected)
        ?.quantity ?? 0;
    this.cart.change(kind, this.selected, current + this.quantity);
    this.notice.set(
      'Votre sélection a été transmise au panier. Vérifiez les quantités avant de commander.',
    );
  }
  private fail(generation: number): void {
    if (generation === this.generation) {
      this.loading.set(false);
      this.error.set('Cette fiche est indisponible ou n’est plus publiée.');
    }
  }
}
