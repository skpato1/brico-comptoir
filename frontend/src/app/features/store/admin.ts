import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SessionState } from '../../core/session-state';
import { CatalogComponent } from '../catalog/catalog';
import { PacksComponent } from '../packs/packs';
import { AdminOrders } from '../admin/orders';
import { AdminStock } from '../admin/stock';
import { AdminSettings } from '../admin/settings';
import { AdminHero } from '../admin/hero';
import { AdminContact } from '../admin/contact';
import { AdminSupport } from '../admin/support';
import { OrderNotifications } from '../../core/order-notifications';

interface AdminTab { id: string; label: string; description: string; group: string; allowed: boolean; }

@Component({
  selector: 'app-admin',
  imports: [RouterLink, CatalogComponent, PacksComponent, AdminOrders, AdminStock,
    AdminSettings, AdminHero, AdminContact, AdminSupport],
  styleUrl: '../admin/admin.scss',
  template: `
    <div class="admin-shell">
      <header class="admin-intro">
        <div><p class="eyebrow">BRICOCOMPTOIR · ESPACE DE GESTION</p>
          <h1>Bonjour, {{ roleLabel() }}.</h1>
          <p>Retrouvez vos tâches au même endroit. Les droits et les validations sont vérifiés par le serveur.</p>
        </div>
        <a class="store-link" routerLink="/">Voir la boutique <span aria-hidden="true">↗</span></a>
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
        <nav class="admin-nav" aria-label="Administration">
          @for (group of groups; track group) {
            @if (groupTabs(group).length) {
              <div class="admin-nav-group"><p>{{ group }}</p>
                @for (tab of groupTabs(group); track tab.id) {
                  <a routerLink="/gestion" [queryParams]="{section:tab.id}"
                    [class.active]="section() === tab.id"
                    [attr.aria-current]="section() === tab.id ? 'page' : null">{{ tab.label }}</a>
                }
              </div>
            }
          }
        </nav>
        <section class="admin-workspace" [attr.aria-label]="currentTab()?.label || 'Administration'">
          @if (allowed()) {
            @if (section() === 'dashboard') {
              <p class="eyebrow">VUE D’ENSEMBLE</p><h2>Que souhaitez-vous gérer ?</h2>
              <p class="muted">Sélectionnez une tâche. Les changements ne prennent effet qu’après enregistrement.</p>
              <div class="admin-shortcuts">
                @for (tab of tabs(); track tab.id) {
                  @if (tab.id !== 'dashboard') {
                    <a routerLink="/gestion" [queryParams]="{section:tab.id}">
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
                @case ('accueil') { <app-admin-settings kind="home" /> }
                @case ('bannieres') { <app-admin-hero /> }
                @case ('livraison') { <app-admin-settings kind="delivery" /> }
                @case ('contact') { <app-admin-contact [canEdit]="isAdmin()" /> }
                @case ('support') { <app-admin-support /> }
              }
            }
          } @else {
            <p role="alert" class="error">Cet espace n’est pas accessible avec votre rôle.</p>
            <a routerLink="/compte">Accéder à mon compte</a>
          }
        </section>
      </div>
    </div>
  `,
})
export class AdminPage implements OnInit {
  readonly orderNotifications = inject(OrderNotifications);
  readonly session = inject(SessionState);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  readonly section = signal('dashboard');
  readonly groups = ['Tableau de bord', 'Catalogue', 'Ventes', 'Boutique', 'Support'];
  readonly catalogSections = ['produits', 'photos', 'categories', 'marques', 'prix', 'import'];
  isAdmin() { return !!this.session.account()?.roles.includes('ADMIN'); }
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
      { id: 'stock', label: 'Stocks', description: 'Soldes physiques et ajustements', group: 'Ventes', allowed: admin },
      { id: 'livraison', label: 'Livraison', description: 'Forfait et gouvernorats desservis', group: 'Ventes', allowed: admin },
      { id: 'bannieres', label: 'Bannières', description: 'Diapositives de l’accueil', group: 'Boutique', allowed: catalog },
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
    });
  }
  allowed() { return this.tabs().some(tab => tab.id === this.section()); }
}
