import { Component, DestroyRef, HostListener, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SessionState } from '../../core/session-state';
import { IdentityApi } from '../../core/identity-api';
import { CatalogComponent } from '../catalog/catalog';
import { PacksComponent } from '../packs/packs';
import { AdminOrders } from '../admin/orders';
import { AdminStock } from '../admin/stock';
import { AdminSettings } from '../admin/settings';
import { AdminHero } from '../admin/hero';
import { AdminBestSellers } from '../admin/best-sellers';
import { AdminSocialLinks } from '../admin/social-links';
import { AdminContact } from '../admin/contact';
import { AdminSupport } from '../admin/support';
import { AdminProductStats } from '../admin/product-stats';
import { OrderNotifications } from '../../core/order-notifications';
import { OrderApi, OrderView } from '../../core/order-api';

interface AdminTab { id: string; label: string; description: string; group: string; allowed: boolean; }

@Component({
  selector: 'app-admin',
  imports: [RouterLink, DatePipe, CatalogComponent, PacksComponent, AdminOrders, AdminStock,
    AdminSettings, AdminHero, AdminBestSellers, AdminSocialLinks, AdminContact, AdminSupport, AdminProductStats],
  styleUrl: '../admin/admin.scss',
  template: `
    <div class="admin-shell">
      <div class="admin-topbar">
        <div class="admin-brand"><span class="admin-brand-mark" aria-hidden="true">bc</span>
          <span><strong>BricoComptoir</strong><small>Console de gestion</small></span></div>
        <div class="admin-topbar-actions">
          <span class="admin-identity" [title]="session.account()?.email || ''">{{ session.account()?.email }}</span>
          <a class="store-link" routerLink="/">Voir la boutique <span aria-hidden="true">↗</span></a>
          <button class="admin-logout" type="button" (click)="logout()" [disabled]="logoutBusy()">Déconnexion</button>
        </div>
      </div>
      @if (logoutError()) { <p class="error" role="alert">{{ logoutError() }}</p> }
      <header class="admin-intro">
        <div><p class="eyebrow">ESPACE DE TRAVAIL · {{ roleLabel() }}</p>
          <h1>{{ section() === 'dashboard' ? 'Tableau de bord' : currentTab()?.label || 'Administration' }}</h1>
          <p>{{ section() === 'dashboard' ? 'Votre boutique, vos commandes et vos contenus au même endroit.' : currentTab()?.description || 'Gestion de la boutique' }}</p>
        </div>
        <button class="admin-menu-toggle secondary" type="button" (click)="navOpen.set(!navOpen())"
          [attr.aria-expanded]="navOpen()" aria-controls="admin-navigation">{{ navOpen() ? 'Fermer le menu' : 'Menu de gestion' }}</button>
      </header>
      @if (orderNotifications.status()) {
        <aside class="admin-notice" aria-label="Notifications de commandes">
          <p role="status">{{ orderNotifications.status() }}</p>
          @if (orderNotifications.events().length) {
            <a routerLink="/gestion" [queryParams]="{section:'commandes'}">{{ orderNotifications.events().length }} nouvelle(s) commande(s) · Ouvrir</a>
          }
        </aside>
      }
      <div class="admin-layout">
        @if (navOpen()) { <button class="admin-nav-backdrop" type="button" aria-label="Fermer le menu de gestion" (click)="navOpen.set(false)"></button> }
        <nav id="admin-navigation" class="admin-nav" [class.open]="navOpen()" aria-label="Administration">
          <div class="admin-nav-title">NAVIGATION <span>{{ tabs().length }} rubriques</span></div>
          @for (group of groups; track group) {
            @if (groupTabs(group).length) {
              <div class="admin-nav-group"><p>{{ group }}</p>
                @for (tab of groupTabs(group); track tab.id) {
                  <a routerLink="/gestion" [queryParams]="{section:tab.id}"
                    (click)="navOpen.set(false)"
                    [class.active]="section() === tab.id"
                    [attr.aria-current]="section() === tab.id ? 'page' : null">
                    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path [attr.d]="icon(tab.id)" /></svg>
                    <span>{{ tab.label }}</span></a>
                }
              </div>
            }
          }
        </nav>
        <section class="admin-workspace" [attr.aria-label]="currentTab()?.label || 'Administration'">
          @if (allowed()) {
            @if (section() === 'dashboard') {
              <div class="dashboard-welcome">
                <div><p class="eyebrow">VUE D’ENSEMBLE</p><h2>Bonjour, {{ roleLabel() }}.</h2>
                  <p>Choisissez une tâche ou consultez les commandes récentes à préparer.</p></div>
                @if (canManageOrders()) { <a routerLink="/gestion" [queryParams]="{section:'commandes'}">Gérer les commandes <span aria-hidden="true">→</span></a> }
              </div>
              @if (canManageOrders()) {
                <section class="dashboard-orders" aria-labelledby="dashboard-orders-title">
                  <div class="dashboard-section-heading"><div><p class="eyebrow">VENTES</p><h3 id="dashboard-orders-title">Commandes à traiter</h3></div>
                    <a routerLink="/gestion" [queryParams]="{section:'commandes'}">Voir les commandes →</a></div>
                  @if (dashboardBusy()) { <p role="status">Chargement des commandes…</p> }
                  @else if (dashboardError()) { <p role="alert">{{ dashboardError() }} <button type="button" class="secondary" (click)="loadDashboardOrders()">Réessayer</button></p> }
                  @else if (!dashboardOrders().length) { <p class="dashboard-empty">Aucune commande confirmée à traiter.</p> }
                  @else {
                    <div class="dashboard-order-list">
                      @for (order of dashboardOrders().slice(0, 5); track order.id) {
                        <div class="dashboard-order-row"><span class="order-avatar" aria-hidden="true">{{ order.summary.address.recipient.slice(0, 1).toUpperCase() }}</span>
                          <div><strong>{{ order.summary.address.recipient }}</strong><small>{{ order.createdAt | date:'dd/MM/yyyy · HH:mm' }} · {{ order.summary.address.city }}</small></div>
                          <span class="status-pill">À préparer</span><strong class="order-amount">{{ order.summary.total.amount }} TND</strong></div>
                      }
                    </div>
                  }
                </section>
              }
              <div class="dashboard-section-heading"><div><p class="eyebrow">RACCOURCIS</p><h3>Accès rapide</h3></div></div>
              <div class="admin-shortcuts">
                @for (tab of tabs(); track tab.id) {
                  @if (tab.id !== 'dashboard') {
                    <a routerLink="/gestion" [queryParams]="{section:tab.id}">
                      <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path [attr.d]="icon(tab.id)" /></svg>
                      <strong>{{ tab.label }}</strong><span>{{ tab.description }}</span><b aria-hidden="true">↗</b>
                    </a>
                  }
                }
              </div>
            } @else if (catalogSections.includes(section())) {
              <app-catalog [account]="session.account()" [adminOnly]="true" [adminFocus]="section()" />
            } @else {
              @switch (section()) {
                @case ('packs') { <app-packs [account]="session.account()" [adminOnly]="true" /> }
                @case ('stock') { <app-admin-stock /> }
                @case ('commandes') { <app-admin-orders /> }
                @case ('statistiques-produits') { <app-admin-product-stats /> }
                @case ('accueil') { <app-admin-settings kind="home" /> }
                @case ('bannieres') { <app-admin-hero /> }
                @case ('meilleures-ventes') { <app-admin-best-sellers /> }
                @case ('favoris-professionnels') { <app-admin-best-sellers kind="professionalFavorites" /> }
                @case ('reseaux-sociaux') { <app-admin-social-links /> }
                @case ('livraison') { <app-admin-settings kind="delivery" /> }
                @case ('contact') { <app-admin-contact [canEdit]="isAdmin()" /> }
                @case ('support') { <app-admin-support /> }
              }
            }
          } @else {
            <p role="alert" class="error">Cet espace n’est pas accessible avec votre rôle.</p>
            <a routerLink="/acces-equipe">Se connecter avec un accès équipe</a>
          }
        </section>
      </div>
    </div>
  `,
})
export class AdminPage implements OnInit {
  @HostListener('document:keydown.escape') closeNavigation() { this.navOpen.set(false); }
  readonly orderNotifications = inject(OrderNotifications);
  readonly session = inject(SessionState);
  private readonly identity = inject(IdentityApi);
  private readonly router = inject(Router);
  private readonly orders = inject(OrderApi);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  readonly section = signal('dashboard');
  readonly navOpen = signal(false);
  readonly dashboardOrders = signal<OrderView[]>([]);
  readonly dashboardBusy = signal(false);
  readonly dashboardError = signal('');
  readonly logoutBusy = signal(false);
  readonly logoutError = signal('');
  readonly groups = ['Tableau de bord', 'Catalogue', 'Ventes', 'Boutique', 'Support'];
  readonly catalogSections = ['produits', 'photos', 'categories', 'marques', 'prix', 'import'];
  isAdmin() { return !!this.session.account()?.roles.includes('ADMIN'); }
  canManageOrders() { return !!this.session.account()?.roles.some(role => role === 'ADMIN' || role === 'ORDER_MANAGER'); }
  icon(id: string): string {
    const paths: Record<string, string> = {
      dashboard: 'M3 3h8v8H3z M13 3h8v8h-8z M3 13h8v8H3z M13 13h8v8h-8z',
      produits: 'M3 7l9-4 9 4-9 4-9-4z M3 7v10l9 4 9-4V7 M12 11v10',
      photos: 'M3 4h18v16H3z M7 9h.01 M3 17l5-5 4 4 3-3 6 6',
      categories: 'M4 5h6v6H4z M14 5h6v6h-6z M4 15h6v6H4z M14 15h6v6h-6z',
      marques: 'M3 12l9-9h8v8l-9 9z M16 8h.01',
      prix: 'M12 2v20 M17 6H9a4 4 0 000 8h6a4 4 0 010 8H6',
      import: 'M12 3v12 m-5-5 5 5 5-5 M4 17v4h16v-4',
      packs: 'M3 7l9-4 9 4-9 4-9-4z M3 7v10l9 4 9-4V7 M12 11v10',
      commandes: 'M6 3h12v18l-3-2-3 2-3-2-3 2z M9 8h6 M9 12h6',
      'statistiques-produits': 'M4 20V11h4v9 M10 20V5h4v15 M16 20v-7h4v7 M3 20h18',
      stock: 'M3 7l9-4 9 4-9 4-9-4z M3 7v10l9 4 9-4V7',
      livraison: 'M3 6h11v11H3z M14 10h4l3 4v3h-7z M7 20a2 2 0 100-4 2 2 0 000 4z M18 20a2 2 0 100-4 2 2 0 000 4z',
      bannieres: 'M3 4h18v16H3z M7 9h.01 M3 17l5-5 4 4 3-3 6 6',
      'meilleures-ventes': 'M12 3l2.7 5.7 6.3.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.3-.9z',
      'favoris-professionnels': 'M12 21s-8-4.8-8-10.5A4.5 4.5 0 0112 7a4.5 4.5 0 018 3.5C20 16.2 12 21 12 21z',
      'reseaux-sociaux': 'M10 13a5 5 0 007 0l2-2a5 5 0 00-7-7l-1 1 M14 11a5 5 0 00-7 0l-2 2a5 5 0 007 7l1-1',
      accueil: 'M3 11l9-8 9 8 M5 10v11h14V10 M9 21v-7h6v7',
      contact: 'M3 5h18v14H3z M3 6l9 7 9-7',
      support: 'M12 3a9 9 0 100 18 9 9 0 000-18z M9 9a3 3 0 116 0c0 2-3 2-3 4 M12 17h.01',
    };
    return paths[id] ?? paths['dashboard'];
  }
  roleLabel() {
    const roles = this.session.account()?.roles ?? [];
    return roles.includes('ADMIN') ? 'administrateur' : roles.includes('CATALOG_MANAGER')
      ? 'gestionnaire de catalogue' : roles.includes('ORDER_MANAGER') ? 'gestionnaire de commandes' : 'visiteur';
  }
  tabs(): AdminTab[] {
    const roles = this.session.account()?.roles ?? [];
    const admin = roles.includes('ADMIN');
    const catalog = admin || roles.includes('CATALOG_MANAGER');
    const orders = admin || roles.includes('ORDER_MANAGER');
    return [
      { id: 'dashboard', label: 'Vue d’ensemble', description: 'Accès rapide à vos tâches', group: 'Tableau de bord', allowed: catalog || orders },
      { id: 'produits', label: 'Produits', description: 'Fiches et états de publication', group: 'Catalogue', allowed: catalog },
      { id: 'photos', label: 'Photos', description: 'Images des produits, ordre et principale', group: 'Catalogue', allowed: catalog },
      { id: 'categories', label: 'Catégories', description: 'Hiérarchie et visibilité', group: 'Catalogue', allowed: catalog },
      { id: 'marques', label: 'Marques', description: 'Référentiel des marques', group: 'Catalogue', allowed: catalog },
      { id: 'prix', label: 'Prix et SKU', description: 'Variantes et tarifs en TND', group: 'Catalogue', allowed: catalog },
      { id: 'import', label: 'Import CSV', description: 'Aperçu des erreurs puis application', group: 'Catalogue', allowed: catalog },
      { id: 'packs', label: 'Packs', description: 'Composition, variantes et guides', group: 'Catalogue', allowed: catalog },
      { id: 'commandes', label: 'Commandes', description: 'Préparation, expédition, annulation', group: 'Ventes', allowed: orders },
      { id: 'statistiques-produits', label: 'Statistiques produits', description: 'Vues des fiches et produits à promouvoir', group: 'Ventes', allowed: catalog },
      { id: 'stock', label: 'Stocks', description: 'Soldes physiques et ajustements', group: 'Ventes', allowed: admin },
      { id: 'livraison', label: 'Livraison', description: 'Forfait et gouvernorats desservis', group: 'Ventes', allowed: admin },
      { id: 'bannieres', label: 'Bannières', description: 'Diapositives de l’accueil', group: 'Boutique', allowed: catalog },
      { id: 'meilleures-ventes', label: 'Meilleures ventes', description: 'Trois produits mis en avant sur l’accueil', group: 'Boutique', allowed: catalog },
      { id: 'favoris-professionnels', label: 'Favoris des professionnels', description: 'Produits proposés sur les fiches produit', group: 'Boutique', allowed: catalog },
      { id: 'reseaux-sociaux', label: 'Réseaux sociaux', description: 'Liens Facebook et Instagram du bandeau', group: 'Boutique', allowed: catalog },
      { id: 'accueil', label: 'Textes d’accueil', description: 'Entrées solutions et produits', group: 'Boutique', allowed: catalog },
      { id: 'contact', label: 'Contact', description: 'Coordonnées et messages', group: 'Support', allowed: orders },
      { id: 'support', label: 'Comptes et paniers', description: 'Recherche exacte, consultation seule', group: 'Support', allowed: admin },
    ].filter(tab => tab.allowed);
  }
  groupTabs(group: string) { return this.tabs().filter(tab => tab.group === group); }
  currentTab() { return this.tabs().find(tab => tab.id === this.section()); }
  ngOnInit() {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroy)).subscribe(params => {
      const section = params.get('section') ?? 'dashboard';
      this.section.set(section === 'catalogue' ? 'produits' : section);
      this.navOpen.set(false);
      if (this.section() === 'dashboard' && this.canManageOrders()) this.loadDashboardOrders();
    });
  }
  allowed() { return this.tabs().some(tab => tab.id === this.section()); }
  loadDashboardOrders() {
    if (this.dashboardBusy()) return;
    this.dashboardBusy.set(true); this.dashboardError.set('');
    this.orders.history(true, 0, 'CONFIRMED').pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: orders => { this.dashboardOrders.set(orders); this.dashboardBusy.set(false); },
      error: () => { this.dashboardOrders.set([]); this.dashboardBusy.set(false);
        this.dashboardError.set('Les commandes sont momentanément indisponibles.'); },
    });
  }
  logout() {
    if (this.logoutBusy()) return;
    this.logoutBusy.set(true); this.logoutError.set('');
    this.identity.logout().pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: () => { this.session.set(null); void this.router.navigateByUrl('/acces-equipe'); },
      error: () => { this.logoutBusy.set(false); this.logoutError.set('Déconnexion impossible. Réessayez.'); },
    });
  }
}
