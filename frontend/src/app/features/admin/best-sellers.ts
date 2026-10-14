import { Component, DestroyRef, Input, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, of, catchError } from 'rxjs';
import { AdminApi, adminError, BestSellersContent } from '../../core/admin-api';
import { CatalogApi, Product, ProductImage } from '../../core/catalog-api';
import { ProductPhoto } from '../../shared/product-photo';

@Component({
  selector: 'app-admin-best-sellers',
  imports: [FormsModule, ProductPhoto],
  template: `
    <div class="workspace-heading">
      <div><p class="eyebrow">VITRINE</p><h2>{{ title }}</h2>
        <p>Choisissez exactement {{ limit }} produits publiés. {{ placement }}</p></div>
      <button class="secondary" type="button" (click)="load()" [disabled]="busy()">Recharger</button>
    </div>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (success()) { <p role="status">{{ success() }}</p> }
    @if (busy()) { <p role="status">Chargement ou enregistrement…</p> }
    @if (value(); as selection) {
      <p class="selection-count" role="status">{{ selection.productIds.length }} produit(s) sur {{ limit }} sélectionné(s)</p>
      <div class="selected-list" aria-label="Produits sélectionnés dans l’ordre d’affichage">
        @for (id of selection.productIds; track id; let i = $index) {
          <article class="selected-product">
            <span class="position">{{ i + 1 }}</span>
            <app-product-photo [image]="image(id)" [alt]="selectedProduct(id)?.name ?? 'Photo du produit'" />
            <div class="selected-copy"><strong>{{ selectedProduct(id)?.name ?? 'Produit indisponible' }}</strong>
              <small>{{ selectedProduct(id)?.variants?.[0]?.sku ?? id }}</small></div>
            <div class="selected-actions">
              <button type="button" class="secondary" (click)="move(i, -1)" [disabled]="i === 0 || busy()" [attr.aria-label]="'Monter ' + (selectedProduct(id)?.name ?? id)">↑</button>
              <button type="button" class="secondary" (click)="move(i, 1)" [disabled]="i === selection.productIds.length - 1 || busy()" [attr.aria-label]="'Descendre ' + (selectedProduct(id)?.name ?? id)">↓</button>
              <button type="button" class="secondary" (click)="remove(i)" [disabled]="busy()" [attr.aria-label]="'Retirer ' + (selectedProduct(id)?.name ?? id)">Retirer</button>
            </div>
          </article>
        }
      </div>
      <form class="product-search" (ngSubmit)="search()">
        <label for="editorial-product-query">Rechercher un produit à ajouter</label>
        <div><input id="editorial-product-query" name="editorialProductQuery" [(ngModel)]="query" type="search" placeholder="Nom ou référence produit" maxlength="100" />
          <button type="submit" [disabled]="searching() || busy()">Rechercher</button></div>
      </form>
      @if (searching()) { <p role="status">Recherche en cours…</p> }
      @if (searched() && !searching() && !results().length) { <p>Aucun produit publié trouvé.</p> }
      @if (results().length) {
        <ul class="search-results" aria-label="Résultats de recherche">
          @for (product of results(); track product.id) {
            <li><span><strong>{{ product.name }}</strong><small>{{ product.variants[0]?.sku ?? '' }}</small></span>
              <button type="button" class="secondary" (click)="add(product)"
                [disabled]="selection.productIds.length === limit || selection.productIds.includes(product.id) || busy()">
                {{ selection.productIds.includes(product.id) ? 'Ajouté' : 'Ajouter' }}
              </button></li>
          }
        </ul>
      }
      <div class="selection-footer">
        <p>La section n’apparaît qu’avec {{ limit }} produits publiés. Le choix reste modifiable à tout moment.</p>
        <div class="actions">
          <button type="button" class="secondary" (click)="hide()" [disabled]="busy() || !selection.productIds.length">Masquer la section</button>
          <button type="button" (click)="save()" [disabled]="busy() || selection.productIds.length !== limit">Enregistrer la sélection</button>
        </div>
      </div>
    }
  `,
  styles: [`
    .selection-count { color: #666; font-weight: 650; margin: 20px 0 12px; }
    .selected-list { display: grid; gap: 10px; }
    .selected-product { display: grid; grid-template-columns: 32px 66px minmax(0,1fr) auto; align-items: center; gap: 12px; padding: 10px; border: 1px solid #e7e3df; border-radius: 10px; }
    .position { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 50%; background: #f7ebeb; color: #a51f24; font-weight: 800; }
    .selected-product app-product-photo { width: 66px; height: 66px; overflow: hidden; border-radius: 7px; }
    .selected-copy { display: grid; min-width: 0; gap: 4px; }
    .selected-copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .selected-copy small, .search-results small { color: #666; overflow-wrap: anywhere; }
    .selected-actions { display: flex; gap: 5px; }
    .selected-actions button { min-width: 40px; min-height: 40px; padding: 6px 9px; }
    .product-search { margin-top: 28px; }
    .product-search label { display: block; font-weight: 700; margin-bottom: 8px; }
    .product-search > div { display: flex; gap: 8px; }
    .product-search input { flex: 1; min-width: 0; min-height: 44px; padding: 10px 12px; border: 1px solid #bbb; border-radius: 7px; }
    .search-results { list-style: none; padding: 0; margin: 12px 0 0; border: 1px solid #e6e6e6; border-radius: 8px; overflow: hidden; }
    .search-results li { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 10px 14px; }
    .search-results li + li { border-top: 1px solid #eee; }
    .search-results li span { display: grid; gap: 4px; min-width: 0; }
    .selection-footer { margin-top: 24px; padding-top: 20px; border-top: 1px solid #eee; }
    .selection-footer p { color: #666; font-size: .9rem; }
    .selection-footer .actions { display: flex; flex-wrap: wrap; gap: 10px; }
    @media (max-width: 650px) {
      .selected-product { grid-template-columns: 30px 54px minmax(0,1fr); }
      .selected-product app-product-photo { width: 54px; height: 54px; }
      .selected-actions { grid-column: 2 / -1; }
      .product-search > div { display: grid; }
    }
  `],
})
export class AdminBestSellers implements OnInit {
  @Input() kind: 'bestSellers' | 'professionalFavorites' = 'bestSellers';
  get limit() { return this.kind === 'bestSellers' ? 3 : 4; }
  get title() { return this.kind === 'bestSellers' ? 'Meilleures ventes' : 'Favoris des professionnels'; }
  get placement() { return this.kind === 'bestSellers'
    ? 'Leur ordre sera celui de l’accueil.'
    : 'Les trois premières références admissibles apparaîtront sur chaque fiche produit ; la fiche consultée est exclue.'; }
  private readonly api = inject(AdminApi);
  private readonly catalog = inject(CatalogApi);
  private readonly destroy = inject(DestroyRef);
  readonly value = signal<BestSellersContent | null>(null);
  readonly selected = signal<Record<string, Product | null>>({});
  readonly images = signal<Record<string, ProductImage[]>>({});
  readonly results = signal<Product[]>([]);
  readonly busy = signal(false);
  readonly searching = signal(false);
  readonly searched = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  query = '';
  ngOnInit() { this.load(); }
  selectedProduct(id: string) { return this.selected()[id]; }
  image(id: string) { const images = this.images()[id] ?? []; return images.find(image => image.primary) ?? images[0]; }
  load() {
    this.busy.set(true); this.error.set(''); this.success.set('');
    const request = this.kind === 'bestSellers' ? this.api.bestSellers(true) : this.api.professionalFavorites(true);
    request.pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: value => { this.value.set(value); this.busy.set(false); this.loadDetails(value.productIds); },
      error: error => { this.busy.set(false); this.error.set(adminError(error)); },
    });
  }
  private loadDetails(ids: string[]) {
    if (!ids.length) { this.selected.set({}); this.images.set({}); return; }
    forkJoin(ids.map(id => this.catalog.detail(id).pipe(catchError(() => of(null)))))
      .pipe(takeUntilDestroyed(this.destroy)).subscribe(products => {
        this.selected.set(Object.fromEntries(ids.map((id, i) => [id, products[i]])));
      });
    this.catalog.publicImages(ids).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: images => this.images.set(images), error: () => this.images.set({}),
    });
  }
  search() {
    if (this.searching()) return;
    this.searching.set(true); this.searched.set(true); this.error.set('');
    this.catalog.products({ q: this.query.trim(), categoryId: '', brandId: '', minPrice: '', maxPrice: '', sort: 'name', page: 0, size: 12 })
      .pipe(takeUntilDestroyed(this.destroy)).subscribe({
        next: page => { this.results.set(page.items.filter(product => !product.demo)); this.searching.set(false); },
        error: error => { this.searching.set(false); this.error.set(adminError(error, 'Recherche indisponible.')); },
      });
  }
  add(product: Product) {
    const value = this.value();
    if (!value || value.productIds.length === this.limit || value.productIds.includes(product.id) || this.busy()) return;
    this.value.set({ ...value, productIds: [...value.productIds, product.id] });
    this.selected.update(current => ({ ...current, [product.id]: product }));
    this.catalog.publicImages([product.id]).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: images => this.images.update(current => ({ ...current, ...images })),
    });
    this.success.set('');
  }
  remove(index: number) {
    const value = this.value();
    if (value && !this.busy()) this.value.set({ ...value, productIds: value.productIds.filter((_, i) => i !== index) });
    this.success.set('');
  }
  move(index: number, direction: number) {
    const value = this.value();
    if (!value || this.busy() || index + direction < 0 || index + direction >= value.productIds.length) return;
    const productIds = [...value.productIds];
    [productIds[index], productIds[index + direction]] = [productIds[index + direction], productIds[index]];
    this.value.set({ ...value, productIds }); this.success.set('');
  }
  save() {
    const value = this.value();
    if (!value || value.productIds.length !== this.limit || this.busy()) return;
    this.persist(value, 'Sélection enregistrée.');
  }
  hide() {
    const value = this.value();
    if (!value || this.busy()) return;
    this.persist({ ...value, productIds: [] }, 'Section masquée.');
  }
  private persist(value: BestSellersContent, message: string) {
    this.busy.set(true); this.error.set(''); this.success.set('');
    const request = this.kind === 'bestSellers' ? this.api.saveBestSellers(value) : this.api.saveProfessionalFavorites(value);
    request.pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: saved => { this.value.set(saved); this.busy.set(false); this.success.set(message); },
      error: error => { this.busy.set(false); this.error.set(adminError(error, 'Sélection refusée : vérifiez que tous les produits sont publiés.')); },
    });
  }
}
