import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  CatalogApi,
  Category,
  Brand,
  Product,
  ProductFilters,
  ProductImage,
  Page,
} from '../../core/catalog-api';
import { Pack, PackImage, PacksApi } from '../../core/packs-api';
import { OfferCard } from '../../shared/offer-card';
@Component({
  selector: 'app-browse',
  imports: [FormsModule, RouterLink, OfferCard],
  templateUrl: './browse.html',
  styleUrl: './browse.scss',
})
export class BrowsePage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(CatalogApi);
  private readonly packsApi = inject(PacksApi);
  private readonly destroy = inject(DestroyRef);
  readonly packMode = this.route.snapshot.data['kind'] === 'packs';
  readonly solutions = this.route.snapshot.data['solutions'] === true;
  readonly loading = signal(true);
  readonly error = signal('');
  readonly filterError = signal('');
  readonly categories = signal<Category[]>([]);
  readonly brands = signal<Brand[]>([]);
  readonly page = signal<Page<Product> | null>(null);
  readonly packs = signal<Pack[]>([]);
  readonly images = signal<Record<string, (ProductImage | PackImage)[]>>({});
  filters: ProductFilters = {
    q: '',
    categoryId: '',
    brandId: '',
    minPrice: '',
    maxPrice: '',
    sort: 'name',
    page: 0,
    size: 12,
  };
  private generation = 0;
  ngOnInit(): void {
    if (!this.packMode) {
      this.api
        .categories()
        .pipe(takeUntilDestroyed(this.destroy))
        .subscribe({
          next: (v) => this.categories.set(v),
          error: () =>
            this.filterError.set(
              'Les catégories ne sont pas disponibles. La recherche reste possible.',
            ),
        });
      this.api
        .brands()
        .pipe(takeUntilDestroyed(this.destroy))
        .subscribe({
          next: (v) => this.brands.set(v),
          error: () =>
            this.filterError.set(
              'Les marques ne sont pas disponibles. La recherche reste possible.',
            ),
        });
    }
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroy)).subscribe((params) => {
      const page = Number(params.get('page') ?? 0);
      this.filters = {
        q: (params.get('q') ?? '').slice(0, 120),
        categoryId: params.get('categoryId') ?? '',
        brandId: params.get('brandId') ?? '',
        minPrice: params.get('minPrice') ?? '',
        maxPrice: params.get('maxPrice') ?? '',
        sort: ['name', 'price_asc', 'price_desc', 'newest'].includes(params.get('sort') ?? '')
          ? (params.get('sort') as ProductFilters['sort'])
          : 'name',
        page: Number.isInteger(page) && page >= 0 && page < 100000 ? page : 0,
        size: 12,
      };
      this.load();
    });
  }
  load(): void {
    const generation = ++this.generation;
    this.loading.set(true);
    this.error.set('');
    this.images.set({});
    if (this.packMode) {
      this.packsApi
        .list()
        .pipe(takeUntilDestroyed(this.destroy))
        .subscribe({
          next: (packs) => {
            if (generation !== this.generation) return;
            const q = this.filters.q.toLocaleLowerCase('fr');
            this.packs.set(
              packs.filter((p) =>
                (p.name + ' ' + p.slogan + ' ' + p.guide).toLocaleLowerCase('fr').includes(q),
              ),
            );
            this.loading.set(false);
            this.packsApi
              .publicImages(this.packs().map((p) => p.id))
              .pipe(takeUntilDestroyed(this.destroy))
              .subscribe({
                next: (images) => {
                  if (generation === this.generation) this.images.set(images);
                },
                error: () => {},
              });
          },
          error: () => {
            if (generation === this.generation) {
              this.loading.set(false);
              this.error.set('Les packs sont momentanément indisponibles.');
            }
          },
        });
      return;
    }
    this.api
      .products({ ...this.filters })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (page) => {
          if (generation !== this.generation) return;
          this.page.set(page);
          this.loading.set(false);
          if (page.items.length)
            this.api
              .publicImages(page.items.map((p) => p.id))
              .pipe(takeUntilDestroyed(this.destroy))
              .subscribe({
                next: (v) => {
                  if (generation === this.generation) this.images.set(v);
                },
                error: () => {},
              });
        },
        error: (response) => {
          if (generation === this.generation) {
            this.loading.set(false);
            this.error.set(
              response.status === 400
                ? 'Vérifiez les filtres et les montants saisis.'
                : 'Le catalogue est momentanément indisponible.',
            );
          }
        },
      });
  }
  search(page = 0): void {
    const params = { ...this.filters, page, size: undefined };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: Object.fromEntries(
        Object.entries(params).filter(([, v]) => v !== '' && v !== undefined),
      ),
    });
  }
  reset(): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }
  primary(id: string): ProductImage | PackImage | undefined {
    const images = this.images()[id] ?? [];
    return images.find((i) => i.primary) ?? images[0];
  }
  categoryName(category: Category): string {
    const parents: string[] = [];
    let current: Category | undefined = category;
    const visited = new Set<string>();
    while (current && !visited.has(current.id)) {
      visited.add(current.id);
      parents.unshift(current.name);
      current = this.categories().find((c) => c.id === current?.parentId);
    }
    return parents.join(' / ');
  }
}
