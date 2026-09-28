import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SessionState } from '../../core/session-state';
import { CatalogComponent } from '../catalog/catalog';
import { PacksComponent } from '../packs/packs';
import { AdminOrders } from '../admin/orders';
import { AdminStock } from '../admin/stock';
import { AdminSettings } from '../admin/settings';
import { OrderNotifications } from '../../core/order-notifications';
@Component({
  selector: 'app-admin',
  imports: [RouterLink, CatalogComponent, PacksComponent, AdminOrders, AdminStock, AdminSettings],
  styleUrl: '../admin/admin.scss',
  template: `
    <p class="eyebrow">BRICOCOMPTOIR · ADMINISTRATION</p>
    <h1>Votre espace de gestion.</h1>
    <p>
      Les opérations disponibles dépendent de votre rôle. Chaque action est vérifiée par le serveur.
    </p>
    <nav class="admin-nav" aria-label="Administration">
      @for (tab of tabs(); track tab.id) {
        <a
          routerLink="/gestion"
          [queryParams]="{ section: tab.id }"
          [class.active]="section() === tab.id"
          [attr.aria-current]="section() === tab.id ? 'page' : null"
          >{{ tab.label }}</a
        >
      }
    </nav>
    @if (orderNotifications.status()) {
      <aside aria-label="Notifications de commandes">
        <p role="status">{{ orderNotifications.status() }}</p>
        @if (orderNotifications.events().length) {
          <p><strong>{{ orderNotifications.events().length }} nouvelle(s) commande(s) reçue(s).</strong>
            <a routerLink="/gestion" [queryParams]="{section:'commandes'}">Consulter les commandes</a></p>
        }
      </aside>
    }
    @if (allowed()) {
      <section class="admin-workspace">
        @switch (section()) {
          @case ('catalogue') {
            <app-catalog [account]="session.account()" [adminOnly]="true" />
          }
          @case ('packs') {
            <app-packs [account]="session.account()" [adminOnly]="true" />
          }
          @case ('stock') {
            <app-admin-stock />
          }
          @case ('commandes') {
            <app-admin-orders />
          }
          @case ('accueil') {
            <app-admin-settings kind="home" />
          }
          @case ('livraison') {
            <app-admin-settings kind="delivery" />
          }
        }
      </section>
    } @else {
      <p role="alert" class="error">Cet espace n’est pas accessible avec votre rôle.</p>
      <a routerLink="/compte">Accéder à mon compte</a>
    }
  `,
})
export class AdminPage implements OnInit {
  readonly orderNotifications = inject(OrderNotifications);
  readonly session = inject(SessionState);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  readonly section = signal('');
  tabs() {
    const roles = this.session.account()?.roles ?? [];
    const admin = roles.includes('ADMIN'),
      catalog = admin || roles.includes('CATALOG_MANAGER'),
      orders = admin || roles.includes('ORDER_MANAGER');
    return [
      { id: 'catalogue', label: 'Produits, photos et catégories', allowed: catalog },
      { id: 'packs', label: 'Packs', allowed: catalog },
      { id: 'stock', label: 'Stocks', allowed: admin },
      { id: 'commandes', label: 'Commandes', allowed: orders },
      { id: 'livraison', label: 'Livraison', allowed: admin },
      { id: 'accueil', label: 'Accueil', allowed: catalog },
    ].filter((t) => t.allowed);
  }
  ngOnInit() {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe((p) => this.section.set(p.get('section') ?? this.tabs()[0]?.id ?? ''));
  }
  allowed() {
    return this.tabs().some((t) => t.id === this.section());
  }
}
