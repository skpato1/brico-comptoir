import { Component, DestroyRef, inject, Input, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, NgForm } from '@angular/forms';
import { AdminPager } from '../../shared/admin-pager';
import { adminError } from '../../core/admin-api';
import { Observable } from 'rxjs';
import { CatalogApi, Product, ProductFilters, Page } from '../../core/catalog-api';
import { Account } from '../../core/identity-api';
import { CartState } from '../../core/cart-state';
import {
  Pack,
  PackComponentLine,
  PackInput,
  PacksApi,
  PackVariant,
  PackVariantInput,
} from '../../core/packs-api';

@Component({
  selector: 'app-packs',
  imports: [FormsModule, AdminPager],
  templateUrl: './packs.html',
  styleUrl: './packs.scss',
})
export class PacksComponent implements OnInit {
  @Input() account: Account | null = null;
  @Input() adminOnly = false;
  readonly result = signal<Page<Pack> | null>(null);
  readonly skuResult = signal<Page<Product> | null>(null);
  query = '';
  page = 0;
  skuPage = 0;
  readonly listLoading = signal(false);
  private generation = 0;
  componentNames: Record<string, string> = {};
  private readonly api = inject(PacksApi);
  readonly cart = inject(CartState);
  private readonly catalog = inject(CatalogApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly publicPacks = signal<Pack[]>([]);
  readonly adminPacks = signal<Pack[]>([]);
  readonly skuProducts = signal<Product[]>([]);
  readonly error = signal('');
  readonly adminMessage = signal('');
  readonly adminOpen = signal(false);
  readonly busy = signal(false);
  packId = '';
  demo = false;
  packForm: PackInput = { code: '', name: '', slogan: '', guide: '', status: 'DRAFT' };
  variantId = '';
  variantForm: PackVariantInput = {
    code: '',
    label: '',
    priceTnd: '',
    status: 'DRAFT',
    components: [],
  };
  skuQuery = '';

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
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (packs) => {
          this.publicPacks.set(packs);
          this.error.set('');
        },
        error: () => this.error.set('Packs momentanément indisponibles.'),
      });
  }
  toggleAdmin(): void {
    if (!this.canManage) return;
    this.adminOpen.update((value) => !value);
    if (this.adminOpen()) this.refreshAdmin();
  }
  refreshAdmin(page = this.page): void {
    this.page = page;
    this.listLoading.set(true);
    const generation = ++this.generation;
    this.api
      .page(this.query, page)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          if (generation !== this.generation) return;
          this.listLoading.set(false);
          this.adminPacks.set(result.items);
          this.result.set(result);
        },
        error: (e) => {
          if (generation !== this.generation) return;
          this.listLoading.set(false);
          this.result.set(null);
          this.adminPacks.set([]);
          this.adminMessage.set(adminError(e));
        },
      });
  }
  editPack(pack: Pack): void {
    this.packId = pack.id;
    this.demo = pack.demo;
    this.componentNames = { ...pack.componentNames };
    this.packForm = {
      code: pack.code,
      name: pack.name,
      slogan: pack.slogan,
      guide: pack.guide,
      status: pack.status,
      version: pack.version,
    };
    this.resetVariant();
  }
  resetPack(): void {
    this.packId = '';
    this.demo = false;
    this.packForm = { code: '', name: '', slogan: '', guide: '', status: 'DRAFT' };
    this.resetVariant();
  }
  editVariant(pack: Pack, variant: PackVariant): void {
    this.editPack(pack);
    this.variantId = variant.id;
    this.variantForm = {
      code: variant.code,
      label: variant.label,
      priceTnd: variant.price.amount,
      status: variant.status,
      components: variant.components.map((line) => ({ ...line })),
      version: variant.version,
    };
  }
  resetVariant(): void {
    this.variantId = '';
    this.variantForm = { code: '', label: '', priceTnd: '', status: 'DRAFT', components: [] };
  }
  searchSkus(page = 0): void {
    this.skuPage = page;
    const filters: ProductFilters = {
      q: this.skuQuery,
      categoryId: '',
      brandId: '',
      minPrice: '',
      maxPrice: '',
      sort: 'name',
      page,
      size: 10,
    };
    this.catalog
      .products(filters, true)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.skuProducts.set(result.items);
          this.skuResult.set(result);
          for (const p of result.items)
            for (const v of p.variants) this.componentNames[v.id] = p.name + ' · ' + v.sku;
        },
        error: (e) => this.adminMessage.set(adminError(e)),
      });
  }
  addComponent(variantId: string): void {
    const existing = this.variantForm.components.find((line) => line.variantId === variantId);
    if (existing) existing.quantity++;
    else this.variantForm.components.push({ variantId, quantity: 1 });
  }
  removeComponent(index: number): void {
    this.variantForm.components.splice(index, 1);
  }
  valid(form: NgForm): boolean {
    if (!form.invalid) return true;
    form.control.markAllAsTouched();
    this.adminMessage.set('Vérifiez les champs requis et leurs formats.');
    return false;
  }
  savePack(): void {
    this.save(this.api.savePack(this.packForm, this.packId || undefined), () => this.resetPack());
  }
  saveVariant(): void {
    if (!this.packId) {
      this.adminMessage.set('Choisissez d’abord un pack.');
      return;
    }
    if (!this.variantForm.components.length) {
      this.adminMessage.set('Ajoutez au moins un SKU.');
      return;
    }
    const components: PackComponentLine[] = this.variantForm.components.map((line) => ({
      ...line,
    }));
    this.save(
      this.api.saveVariant(
        this.packId,
        { ...this.variantForm, components },
        this.variantId || undefined,
      ),
      () => this.resetVariant(),
    );
  }
  private save<T>(operation: Observable<T>, done: () => void): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.adminMessage.set('');
    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.busy.set(false);
        this.adminMessage.set('Modification enregistrée.');
        done();
        this.refreshAdmin();
        this.refreshPublic();
      },
      error: (response) => {
        this.busy.set(false);
        this.adminMessage.set(
          adminError(
            response,
            'Modification refusée. Vérifiez les champs et la publication des composants.',
          ),
        );
      },
    });
  }
}
