import { Component, computed, DestroyRef, ElementRef, inject, OnInit, signal, ViewChild } from '@angular/core';
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
import { buildCategoryMenu, normalizeCategoryText, searchCategoryMenu } from '../../core/category-menu';
import { buildCategoryDepartments } from '../../core/category-departments';
import { DepartmentIcon } from '../../shared/department-icon';
@Component({
  selector: 'app-browse',
  imports: [FormsModule, RouterLink, OfferCard, DepartmentIcon],
  templateUrl: './browse.html',
  styleUrl: './browse.scss',
})
export class BrowsePage implements OnInit {
  @ViewChild('categoryDialog') categoryDialog?: ElementRef<HTMLDialogElement>;
  @ViewChild('brandDialog') brandDialog?: ElementRef<HTMLDialogElement>;
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
  readonly filtersOpen = signal(false);
  readonly categoryQuery = signal('');
  readonly departmentId = signal('');
  readonly brandQuery = signal('');
  readonly categoryMenu = computed(() => buildCategoryMenu(this.categories()));
  readonly departments = computed(() => buildCategoryDepartments(this.categoryMenu().searchable));
  readonly activeDepartment = computed(() => this.departments().find(item => item.id === this.departmentId()));
  readonly categoryMatches = computed(() => searchCategoryMenu(this.categoryMenu(), this.categoryQuery()));
  readonly brandMatches = computed(() => {
    const query = normalizeCategoryText(this.brandQuery());
    return this.brands().filter(brand => brand.active && normalizeCategoryText(brand.name).includes(query))
      .sort((a, b) => a.name.localeCompare(b.name, 'fr'));
  });
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
  readonly applied = signal<ProductFilters>({ ...this.filters });
  private generation = 0;
  private quickCategorySelection = false;
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
      this.applied.set({ ...this.filters });
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
    this.filtersOpen.set(false);
    const params = { ...this.filters, page, size: undefined };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: Object.fromEntries(
        Object.entries(params).filter(([, v]) => v !== '' && v !== undefined),
      ),
    });
  }
  reset(): void {
    this.filtersOpen.set(false);
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }
  openCategoryPicker(): void {
    this.quickCategorySelection = false;
    this.categoryQuery.set('');
    this.departmentId.set('');
    this.categoryDialog?.nativeElement.showModal();
  }
  openDepartmentPicker(id: string): void {
    this.openCategoryPicker();
    this.quickCategorySelection = true;
    this.departmentId.set(id);
  }
  openBrandPicker(): void {
    this.brandQuery.set('');
    this.brandDialog?.nativeElement.showModal();
  }
  selectCategory(id: string): void {
    this.filters.categoryId = id;
    this.categoryDialog?.nativeElement.close();
    if (this.quickCategorySelection) {
      this.quickCategorySelection = false;
      this.search();
    }
  }
  selectBrand(id: string): void {
    this.filters.brandId = id;
    this.brandDialog?.nativeElement.close();
  }
  currentCategoryName(): string {
    const category = this.categories().find(item => item.id === this.filters.categoryId);
    return category ? this.categoryName(category) : 'Toutes les catégories';
  }
  currentBrandName(): string {
    return this.brandName(this.filters.brandId) || 'Toutes les marques';
  }
  activeFilterCount(): number {
    const filters = this.applied();
    return [filters.categoryId, filters.brandId, filters.minPrice, filters.maxPrice]
      .filter(Boolean).length + (filters.sort !== 'name' ? 1 : 0);
  }
  brandName(id: string | null): string {
    return this.brands().find(brand => brand.id === id)?.name ?? '';
  }
  selectedCategoryName(): string {
    const category = this.categories().find(item => item.id === this.applied().categoryId);
    return category ? this.categoryName(category) : 'Catégorie sélectionnée';
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
