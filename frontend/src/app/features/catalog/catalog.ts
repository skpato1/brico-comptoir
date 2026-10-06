import { Component, DestroyRef, inject, Input, OnInit, signal } from '@angular/core';
import { KeyValuePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { AdminPager } from '../../shared/admin-pager';
import { adminError } from '../../core/admin-api';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable } from 'rxjs';
import { Account } from '../../core/identity-api';
import { CartState } from '../../core/cart-state';
import {
  Brand,
  BrandInput,
  CatalogApi,
  Category,
  CategoryInput,
  Page,
  Product,
  ProductFilters,
  ProductInput,
  Variant,
  VariantInput,
  ProductImage,
  ImportPreview,
} from '../../core/catalog-api';

@Component({
  selector: 'app-catalog',
  imports: [FormsModule, KeyValuePipe, AdminPager],
  templateUrl: './catalog.html',
  styleUrl: './catalog.scss',
})
export class CatalogComponent implements OnInit {
  @Input() account: Account | null = null;
  @Input() adminOnly = false;
  @Input() adminFocus = 'produits';
  readonly adminResult = signal<Page<Product> | null>(null);
  readonly categoryResult = signal<Page<Category> | null>(null);
  readonly brandResult = signal<Page<Brand> | null>(null);
  adminPage = 0;
  adminQuery = '';
  categoryPage = 0;
  categoryQuery = '';
  brandPage = 0;
  brandQuery = '';
  readonly listLoading = signal(false);
  private productGeneration = 0;
  private readonly api = inject(CatalogApi);
  readonly cart = inject(CartState);
  private readonly destroyRef = inject(DestroyRef);
  readonly categories = signal<Category[]>([]);
  readonly brands = signal<Brand[]>([]);
  readonly result = signal<Page<Product> | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly adminOpen = signal(false);
  readonly adminCategories = signal<Category[]>([]);
  readonly adminBrands = signal<Brand[]>([]);
  readonly adminProducts = signal<Product[]>([]);
  readonly adminBusy = signal(false);
  readonly adminMessage = signal('');
  readonly publicImages = signal<Record<string, ProductImage[]>>({});
  readonly adminImages = signal<ProductImage[]>([]);
  readonly importPreview = signal<ImportPreview | null>(null);
  readonly detailProductId = signal('');
  selectedImages: File[] = [];
  importFile: File | null = null;
  filters: ProductFilters = {
    q: '',
    categoryId: '',
    brandId: '',
    minPrice: '',
    maxPrice: '',
    sort: 'name',
    page: 0,
    size: 10,
  };
  categoryId = '';
  categoryForm: CategoryInput = { parentId: null, slug: '', name: '', active: true };
  brandId = '';
  brandForm: BrandInput = { slug: '', name: '', active: true };
  productId = '';
  productForm: ProductInput = {
    categoryId: '',
    brandId: null,
    name: '',
    description: '',
    characteristics: {},
    status: 'DRAFT',
  };
  characteristicsText = '';
  variantId = '';
  variantProductId = '';
  variantForm: VariantInput = {
    sku: '',
    label: '',
    unit: 'pièce',
    options: {},
    priceTnd: '',
    status: 'DRAFT',
  };
  optionsText = '';

  ngOnInit(): void {
    if (this.adminOnly && this.canManage) {
      this.adminOpen.set(true);
      this.refreshAdmin();
    } else if (!this.adminOnly) this.refreshPublic();
  }
  get canManage(): boolean {
    return !!this.account?.roles.some((role) => role === 'CATALOG_MANAGER' || role === 'ADMIN');
  }
  refreshPublic(): void {
    if (this.adminOnly) return;
    this.api
      .categories()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (categories) => this.categories.set(categories),
        error: () => this.error.set('Catégories indisponibles.'),
      });
    this.api
      .brands()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (brands) => this.brands.set(brands),
        error: () => this.error.set('Marques indisponibles.'),
      });
    this.search();
  }
  search(reset = false): void {
    if (reset) this.filters.page = 0;
    this.loading.set(true);
    this.error.set('');
    this.api
      .products(this.filters)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.result.set(page);
          this.loading.set(false);
          this.publicImages.set({});
          if (page.items.length)
            this.api
              .publicImages(page.items.map((item) => item.id))
              .pipe(takeUntilDestroyed(this.destroyRef))
              .subscribe({
                next: (images) => this.publicImages.set(images),
                error: () => this.error.set('Images momentanément indisponibles.'),
              });
        },
        error: () => {
          this.error.set('Catalogue indisponible. Réessayez.');
          this.loading.set(false);
        },
      });
  }
  changePage(delta: number): void {
    this.filters.page += delta;
    this.search();
  }
  toggleAdmin(): void {
    if (!this.canManage) return;
    this.adminOpen.update((value) => !value);
    if (this.adminOpen()) this.refreshAdmin();
  }
  refreshAdmin(): void {
    this.api
      .categories(true)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => this.adminCategories.set(data),
        error: () => this.adminMessage.set('Lecture des catégories impossible.'),
      });
    this.api
      .brands(true)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => this.adminBrands.set(data),
        error: () => this.adminMessage.set('Lecture des marques impossible.'),
      });
    this.loadAdminProducts();
    if (this.adminOnly) {
      this.loadCategories();
      this.loadBrands();
    }
  }
  loadCategories(page = this.categoryPage): void {
    this.categoryPage = page;
    this.api
      .categoryPage(this.categoryQuery, page)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (r) => this.categoryResult.set(r),
        error: (e) => this.adminMessage.set(adminError(e)),
      });
  }
  loadBrands(page = this.brandPage): void {
    this.brandPage = page;
    this.api
      .brandPage(this.brandQuery, page)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (r) => this.brandResult.set(r),
        error: (e) => this.adminMessage.set(adminError(e)),
      });
  }
  loadAdminProducts(page = this.adminPage): void {
    this.adminPage = page;
    this.listLoading.set(true);
    const generation = ++this.productGeneration;
    this.api
      .products(
        {
          ...this.filters,
          q: this.adminQuery,
          categoryId: '',
          brandId: '',
          minPrice: '',
          maxPrice: '',
          page,
          size: 20,
        },
        true,
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          if (generation !== this.productGeneration) return;
          this.listLoading.set(false);
          this.adminProducts.set(data.items);
          this.adminResult.set(data);
        },
        error: (e) => {
          if (generation !== this.productGeneration) return;
          this.listLoading.set(false);
          this.adminResult.set(null);
          this.adminProducts.set([]);
          this.adminMessage.set(adminError(e));
        },
      });
  }
  editCategory(category: Category): void {
    this.categoryId = category.id;
    this.categoryForm = {
      parentId: category.parentId,
      slug: category.slug,
      name: category.name,
      active: category.active,
      version: category.version,
    };
  }
  editBrand(brand: Brand): void {
    this.brandId = brand.id;
    this.brandForm = {
      slug: brand.slug,
      name: brand.name,
      active: brand.active,
      version: brand.version,
    };
  }
  editProduct(product: Product): void {
    if (this.adminBusy()) return;
    this.resetVariant();
    this.productId = product.id;
    this.productForm = {
      categoryId: product.categoryId,
      brandId: product.brandId,
      name: product.name,
      description: product.description,
      characteristics: product.characteristics,
      status: product.status,
      version: product.version,
    };
    this.characteristicsText = Object.entries(product.characteristics)
      .map(([key, value]) => `${key}: ${value}`)
      .join('\n');
    this.variantProductId = product.id;
    this.loadAdminImages();
  }
  primaryImage(productId: string): ProductImage | undefined {
    return this.publicImages()[productId]?.find((image) => image.primary);
  }
  imageSrcset(image: ProductImage): string {
    if (image.width <= 360) return `${image.cardUrl} ${image.width}w`;
    return `${image.cardUrl} 360w, ${image.detailUrl} ${Math.min(1200, image.width)}w`;
  }
  toggleDetail(productId: string): void {
    this.detailProductId.set(this.detailProductId() === productId ? '' : productId);
  }
  loadAdminImages(): void {
    if (!this.productId) {
      this.adminImages.set([]);
      return;
    }
    const productId = this.productId;
    this.api
      .adminImages(productId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (images) => {
          if (this.productId === productId) this.adminImages.set(images);
        },
        error: () => this.adminMessage.set('Lecture des photos impossible.'),
      });
  }
  selectImages(event: Event): void {
    this.selectedImages = Array.from((event.target as HTMLInputElement).files ?? []);
  }
  uploadImages(): void {
    if (!this.productId || !this.selectedImages.length || this.adminBusy()) return;
    this.adminBusy.set(true);
    this.adminMessage.set('');
    this.api
      .uploadImages(this.productId, this.selectedImages)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (images) => {
          this.adminBusy.set(false);
          this.adminImages.set(images);
          this.selectedImages = [];
          this.adminMessage.set('Photos enregistrées.');
          this.search();
        },
        error: (response) => {
          this.adminBusy.set(false);
          this.adminMessage.set(
            response.status === 403
              ? 'Accès refusé.'
              : 'Photos refusées : vérifiez type, taille et dimensions.',
          );
        },
      });
  }
  moveImage(index: number, delta: number): void {
    const images = this.adminImages();
    const next = index + delta;
    if (next < 0 || next >= images.length) return;
    const ids = images.map((image) => image.id);
    [ids[index], ids[next]] = [ids[next], ids[index]];
    this.orderImages(ids, images.find((image) => image.primary)!.id);
  }
  makePrimary(imageId: string): void {
    this.orderImages(
      this.adminImages().map((image) => image.id),
      imageId,
    );
  }
  private orderImages(ids: string[], primary: string): void {
    if (!this.productId || this.adminBusy()) return;
    this.adminBusy.set(true);
    this.api
      .orderImages(this.productId, ids, primary)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (images) => {
          this.adminBusy.set(false);
          this.adminImages.set(images);
          this.search();
        },
        error: () => {
          this.adminBusy.set(false);
          this.adminMessage.set('Ordre des photos refusé.');
        },
      });
  }
  selectImport(event: Event): void {
    this.importFile = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.importPreview.set(null);
  }
  previewCsv(): void {
    if (!this.importFile || this.adminBusy()) return;
    this.adminBusy.set(true);
    this.adminMessage.set('');
    this.api
      .previewImport(this.importFile)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (preview) => {
          this.adminBusy.set(false);
          this.importPreview.set(preview);
        },
        error: () => {
          this.adminBusy.set(false);
          this.adminMessage.set('CSV refusé : vérifiez le format et la taille.');
        },
      });
  }
  applyCsv(): void {
    const preview = this.importPreview();
    if (!this.importFile || !preview || preview.issues.length || this.adminBusy()) return;
    this.adminBusy.set(true);
    this.api
      .applyImport(this.importFile, preview.digest)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.adminBusy.set(false);
          this.importPreview.set(null);
          this.importFile = null;
          this.adminMessage.set(
            `${result.productCount} produits et ${result.variantCount} variantes importés.`,
          );
          this.refreshAdmin();
          this.refreshPublic();
        },
        error: () => {
          this.adminBusy.set(false);
          this.adminMessage.set('Import refusé : rechargez le CSV et son aperçu.');
        },
      });
  }
  editVariant(product: Product, variant: Variant): void {
    if (this.adminBusy()) return;
    this.variantProductId = product.id;
    this.variantId = variant.id;
    this.variantForm = {
      sku: variant.sku,
      label: variant.label,
      unit: variant.unit,
      options: variant.options,
      priceTnd: variant.price.amount,
      status: variant.status,
      version: variant.version,
    };
    this.optionsText = Object.entries(variant.options)
      .map(([key, value]) => `${key}: ${value}`)
      .join('\n');
  }
  resetCategory(): void {
    this.categoryId = '';
    this.categoryForm = { parentId: null, slug: '', name: '', active: true };
  }
  resetBrand(): void {
    this.brandId = '';
    this.brandForm = { slug: '', name: '', active: true };
  }
  resetProduct(): void {
    this.productId = '';
    this.productForm = {
      categoryId: '',
      brandId: null,
      name: '',
      description: '',
      characteristics: {},
      status: 'DRAFT',
    };
    this.characteristicsText = '';
    this.adminImages.set([]);
    this.selectedImages = [];
  }
  resetVariant(): void {
    this.variantId = '';
    this.variantForm = {
      sku: '',
      label: '',
      unit: 'pièce',
      options: {},
      priceTnd: '',
      status: 'DRAFT',
    };
    this.optionsText = '';
  }
  saveCategory(): void {
    this.save(this.api.category(this.categoryForm, this.categoryId || undefined), () =>
      this.resetCategory(),
    );
  }
  saveBrand(): void {
    this.save(this.api.brand(this.brandForm, this.brandId || undefined), () => this.resetBrand());
  }
  saveProduct(): void {
    try {
      this.productForm.characteristics = this.parseFacts(this.characteristicsText);
    } catch (error) {
      this.adminMessage.set(String(error));
      return;
    }
    this.save(this.api.product(this.productForm, this.productId || undefined), () =>
      this.resetProduct(),
    );
  }
  saveVariant(): void {
    if (!this.variantProductId) {
      this.adminMessage.set('Choisissez un produit.');
      return;
    }
    try {
      this.variantForm.options = this.parseFacts(this.optionsText);
    } catch (error) {
      this.adminMessage.set(String(error));
      return;
    }
    this.save(
      this.api.variant(this.variantProductId, this.variantForm, this.variantId || undefined),
      () => {
        this.resetVariant();
        this.resetProduct();
      },
    );
  }
  private parseFacts(input: string): Record<string, string> {
    const facts: Record<string, string> = {};
    for (const line of input
      .split('\n')
      .map((value) => value.trim())
      .filter(Boolean)) {
      const separator = line.indexOf(':');
      if (separator < 1 || !line.slice(separator + 1).trim())
        throw new Error('Utilisez une ligne « nom: valeur » par caractéristique.');
      const key = line.slice(0, separator).trim();
      if (Object.hasOwn(facts, key)) throw new Error('Une caractéristique est répétée.');
      facts[key] = line.slice(separator + 1).trim();
    }
    return facts;
  }
  valid(form: NgForm): boolean {
    if (!form.invalid) return true;
    form.control.markAllAsTouched();
    this.adminMessage.set('Vérifiez les champs requis, leurs formats et leurs limites.');
    return false;
  }
  private save<T>(operation: Observable<T>, done: () => void): void {
    if (this.adminBusy()) return;
    this.adminBusy.set(true);
    this.adminMessage.set('');
    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.adminBusy.set(false);
        this.adminMessage.set('Modification enregistrée.');
        done();
        this.refreshAdmin();
        this.refreshPublic();
      },
      error: (response) => {
        this.adminBusy.set(false);
        this.adminMessage.set(adminError(response));
      },
    });
  }
}
