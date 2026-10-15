import { Component, DestroyRef, ElementRef, ViewChild, inject, OnInit, signal } from '@angular/core';
import { KeyValuePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Brand, CatalogApi, Product, ProductImage } from '../../core/catalog-api';
import { Pack, PackImage, PacksApi, PackVariant } from '../../core/packs-api';
import { CartState } from '../../core/cart-state';
import { SessionState } from '../../core/session-state';
import { ProductPhoto } from '../../shared/product-photo';
import { variantLabel } from '../../shared/offer-label';
import { AdminApi } from '../../core/admin-api';
import { OfferCard } from '../../shared/offer-card';
import { catchError, forkJoin, of } from 'rxjs';
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
  imports: [RouterLink, FormsModule, KeyValuePipe, ProductPhoto, OfferCard],
  templateUrl: './detail.html',
  styleUrl: './detail.scss',
})
export class DetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(CatalogApi);
  private readonly contentApi = inject(AdminApi);
  private readonly packsApi = inject(PacksApi);
  private readonly destroy = inject(DestroyRef);
  readonly cart = inject(CartState);
  readonly session = inject(SessionState);
  readonly packMode = this.route.snapshot.data['kind'] === 'packs';
  readonly product = signal<Product | null>(null);
  readonly pack = signal<Pack | null>(null);
  readonly images = signal<(ProductImage | PackImage)[]>([]);
  readonly brands = signal<Brand[]>([]);
  readonly recommendations = signal<Product[]>([]);
  readonly recommendationImages = signal<Record<string, ProductImage[]>>({});
  @ViewChild('recommendationTrack') recommendationTrack?: ElementRef<HTMLElement>;
  readonly loading = signal(true);
  readonly error = signal('');
  readonly notice = signal('');
  readonly available = signal<number | null>(null);
  readonly availabilityError = signal(false);
  selected = '';
  quantity = 1;
  photo = 0;
  private generation = 0;
  private availabilityGeneration = 0;
  ngOnInit(): void {
    if (!this.packMode) {
      this.api.brands().pipe(takeUntilDestroyed(this.destroy)).subscribe({
        next: brands => this.brands.set(brands), error: () => this.brands.set([]),
      });
    }
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroy)).subscribe(() => this.load());
  }
  brandName(): string {
    return this.brands().find(brand => brand.id === this.product()?.brandId)?.name ?? '';
  }
  recommendationBrandName(id: string | null): string {
    return this.brands().find(brand => brand.id === id)?.name ?? '';
  }
  recommendationImage(id: string): ProductImage | undefined {
    const images = this.recommendationImages()[id] ?? [];
    return images.find(image => image.primary) ?? images[0];
  }
  scrollRecommendations(direction: -1 | 1): void {
    const track = this.recommendationTrack?.nativeElement;
    if (!track) return;
    const card = track.querySelector<HTMLElement>('app-offer-card');
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    track.scrollBy({ left: direction * ((card?.offsetWidth ?? 280) + 14), behavior: reduceMotion ? 'auto' : 'smooth' });
  }
  private loadRecommendations(currentId: string, generation: number): void {
    this.contentApi.professionalFavorites().pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: selection => {
        if (generation !== this.generation || selection.productIds.length !== 4) return;
        const ids = selection.productIds.filter(id => id !== currentId);
        if (ids.length < 3) return;
        forkJoin({
          products: forkJoin(ids.map(id => this.api.detail(id))),
          images: this.api.publicImages(ids).pipe(catchError(() => of({} as Record<string, ProductImage[]>))),
        }).pipe(takeUntilDestroyed(this.destroy)).subscribe({
          next: ({ products, images }) => {
            if (generation !== this.generation) return;
            forkJoin(products.map(product => product.variants.length
              ? forkJoin(product.variants.map(variant => this.api.availability(variant.id)
                  .pipe(catchError(() => of(null)))))
              : of([])))
              .pipe(takeUntilDestroyed(this.destroy)).subscribe(availability => {
                if (generation !== this.generation) return;
                const availableProducts = products.filter((_, index) =>
                  availability[index].some(stock => stock !== null && stock.available > 0)).slice(0, 3);
                this.recommendations.set(availableProducts.length === 3 ? availableProducts : []);
                this.recommendationImages.set(images);
              });
          },
          error: () => {
            if (generation === this.generation) this.recommendations.set([]);
          },
        });
      },
      error: () => {},
    });
  }
  load(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    const generation = ++this.generation;
    this.loading.set(true);
    this.error.set('');
    this.notice.set('');
    this.images.set([]);
    this.product.set(null);
    this.pack.set(null);
    this.recommendations.set([]);
    this.recommendationImages.set({});
    ++this.availabilityGeneration;
    this.available.set(null);
    this.availabilityError.set(false);
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
              this.packsApi
                .publicImages([p.id])
                .pipe(takeUntilDestroyed(this.destroy))
                .subscribe({
                  next: (images) => {
                    if (generation === this.generation)
                      this.images.set(
                        [...(images[p.id] ?? [])].sort(
                          (a, b) =>
                            Number(b.primary) - Number(a.primary) || a.sortOrder - b.sortOrder,
                        ),
                      );
                  },
                  error: () => {},
                });
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
            this.api.recordView(p.id).pipe(takeUntilDestroyed(this.destroy)).subscribe({ error: () => {} });
            this.selected = p.variants[0]?.id ?? '';
            this.loadAvailability();
            this.loadRecommendations(p.id, generation);
            this.loading.set(false);
            this.api
              .publicImages([p.id], true)
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
  variantLabel(label: string): string {
    return variantLabel(label, (this.packMode ? this.pack()?.variants : this.product()?.variants)?.length ?? 0);
  }
  selectedSku(): string | null {
    return this.product()?.variants.find(variant => variant.id === this.selected)?.sku ?? null;
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
  productCanAdd(): boolean {
    const available = this.available();
    return available !== null && available >= this.quantity;
  }
  canAdd(): boolean {
    return !!this.selected && this.canBuy() && !this.cart.busy() &&
      !this.cart.recoveryPending() && Number.isInteger(this.quantity) &&
      this.quantity >= 1 && this.quantity <= 999 &&
      (this.packMode ? (this.packVariant()?.available ?? 0) >= this.quantity : this.productCanAdd());
  }
  onVariantChanged(): void {
    this.notice.set('');
    if (!this.packMode) this.loadAvailability();
  }
  private loadAvailability(): void {
    const variantId = this.selected;
    const generation = ++this.availabilityGeneration;
    this.available.set(null);
    this.availabilityError.set(false);
    if (!variantId) return;
    this.api.availability(variantId).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: (result) => {
        if (generation === this.availabilityGeneration && result.variantId === variantId)
          this.available.set(result.available);
      },
      error: () => {
        if (generation === this.availabilityGeneration) this.availabilityError.set(true);
      },
    });
  }
  add(): void {
    if (!this.canAdd()) return;
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
