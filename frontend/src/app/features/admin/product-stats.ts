import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CatalogApi, ProductImage, ProductViewReport, ProductViewRow } from '../../core/catalog-api';

@Component({
  selector: 'app-admin-product-stats',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="product-stats">
      <div class="product-stats-intro">
        <div><p class="eyebrow">DÉCOUVRIR LES PRODUITS QUI ATTIRENT</p>
          <h2>Statistiques produits</h2>
          <p>Les ouvertures de fiches révèlent les produits consultés. Utilisez ce signal pour choisir vos prochaines publicités.</p>
        </div>
        <div class="product-stats-period" role="group" aria-label="Période des statistiques">
          <button type="button" [class.active]="days === 7" [attr.aria-pressed]="days === 7" (click)="selectDays(7)">7 jours</button>
          <button type="button" [class.active]="days === 30" [attr.aria-pressed]="days === 30" (click)="selectDays(30)">30 jours</button>
        </div>
      </div>

      @if (busy()) { <p class="product-stats-message" role="status">Chargement des statistiques…</p> }
      @else if (error()) {
        <div class="product-stats-message" role="alert"><strong>Statistiques indisponibles</strong>
          <p>{{ error() }}</p><button type="button" class="secondary" (click)="load()">Réessayer</button></div>
      } @else if (report(); as data) {
        <div class="product-stats-metrics" aria-label="Indicateurs de consultation">
          <div class="product-stats-metric product-stats-metric-primary"><span>Vues des fiches</span><strong>{{ format(data.totalViews) }}</strong><small>Sur les {{ days }} derniers jours</small></div>
          <div class="product-stats-metric"><span>Produits consultés</span><strong>{{ format(data.viewedProducts) }}</strong><small>Au moins une ouverture de fiche</small></div>
          <div class="product-stats-metric"><span>Produits publiés</span><strong>{{ format(data.totalElements) }}</strong><small>{{ appliedQuery ? 'Correspondant à la recherche' : 'Éligibles au classement' }}</small></div>
        </div>

        <div class="product-stats-panel">
          <div class="product-stats-panel-head"><div><p class="eyebrow">CLASSEMENT</p><h3>Produits les plus consultés</h3>
            <p>Classement selon les ouvertures de fiche, sans compter les consultations de l’équipe.</p></div>
            <form (ngSubmit)="search()" role="search"><label for="product-stats-search">Rechercher un produit ou SKU</label>
              <div><input id="product-stats-search" type="search" name="q" [(ngModel)]="query" maxlength="100" placeholder="Nom ou référence…" />
                <button type="submit">Rechercher</button></div></form>
          </div>
          @if (data.totalViews === 0) { <p class="product-stats-hint">Aucune vue enregistrée sur cette période. Le suivi débute avec les prochaines visites des fiches produit.</p> }
          @if (!data.totalElements) {
            <div class="product-stats-empty"><strong>Aucun produit trouvé</strong><p>Essayez un autre nom ou une autre référence.</p></div>
          } @else {
            <div class="product-stats-list" role="list" aria-label="Classement des produits">
              @for (item of data.items; track item.productId; let rank = $index) {
                <div class="product-stats-row" role="listitem">
                  <span class="product-stats-rank" aria-label="Position {{ data.page * data.size + rank + 1 }}">{{ data.page * data.size + rank + 1 }}</span>
                  <div class="product-stats-photo">
                    @if (image(item.productId); as photo) { <img [src]="photo.cardUrl" alt="" loading="lazy" /> }
                    @else { <span aria-hidden="true">▦</span> }
                  </div>
                  <div class="product-stats-product"><strong>{{ item.name }}</strong>
                    <span>{{ item.brandName || 'Sans marque' }} @if (item.sku) { · {{ item.sku }} }</span></div>
                  <div class="product-stats-count"><strong>{{ format(views(item)) }}</strong><small>vues / {{ days }} j</small></div>
                  <a [routerLink]="['/produits',item.productId]" target="_blank" rel="noopener" [attr.aria-label]="'Voir ' + item.name + ' dans la boutique'">Voir la fiche <span aria-hidden="true">↗</span></a>
                </div>
              }
            </div>
            <div class="product-stats-footer"><span>{{ data.page * data.size + 1 }}–{{ Math.min((data.page + 1) * data.size, data.totalElements) }} sur {{ format(data.totalElements) }}</span>
              <div><button type="button" class="secondary" [disabled]="page === 0" (click)="go(page - 1)">Précédent</button>
                <button type="button" class="secondary" [disabled]="(page + 1) * data.size >= data.totalElements" (click)="go(page + 1)">Suivant</button></div></div>
          }
        </div>
        <p class="product-stats-footnote">Une vue correspond à une ouverture de fiche produit, pas à un visiteur unique ni à une vente. Les comptes commencent à la mise en service de ce suivi.</p>
      }
    </div>`,
})
export class AdminProductStats implements OnInit {
  private readonly api = inject(CatalogApi);
  private readonly destroy = inject(DestroyRef);
  readonly report = signal<ProductViewReport | null>(null);
  readonly images = signal<Record<string, ProductImage[]>>({});
  readonly busy = signal(false);
  readonly error = signal('');
  readonly Math = Math;
  days: 7 | 30 = 7;
  query = '';
  appliedQuery = '';
  page = 0;
  private generation = 0;

  ngOnInit(): void { this.load(); }
  selectDays(days: 7 | 30): void { if (days !== this.days) { this.days = days; this.page = 0; this.load(); } }
  search(): void { this.appliedQuery = this.query.trim(); this.page = 0; this.load(); }
  go(page: number): void { this.page = page; this.load(); }
  views(item: ProductViewRow): number { return this.days === 7 ? item.views7 : item.views30; }
  image(id: string): ProductImage | undefined { const photos = this.images()[id] ?? []; return photos.find(photo => photo.primary) ?? photos[0]; }
  format(value: number): string { return new Intl.NumberFormat('fr-TN').format(value); }
  load(): void {
    const generation = ++this.generation;
    this.busy.set(true); this.error.set('');
    this.api.productViews(this.days, this.appliedQuery, this.page).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: data => {
        if (generation !== this.generation) return;
        this.report.set(data); this.busy.set(false);
        const ids = data.items.map(item => item.productId);
        if (!ids.length) { this.images.set({}); return; }
        this.api.publicImages(ids).pipe(takeUntilDestroyed(this.destroy)).subscribe({
          next: images => { if (generation === this.generation) this.images.set(images); },
          error: () => { if (generation === this.generation) this.images.set({}); },
        });
      },
      error: () => { if (generation === this.generation) { this.busy.set(false); this.error.set('Réessayez dans un instant.'); } },
    });
  }
}
