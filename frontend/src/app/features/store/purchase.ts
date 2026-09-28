import {
  Component,
  DestroyRef,
  HostListener,
  inject,
  OnInit,
  signal,
  ViewChild,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SessionState } from '../../core/session-state';
import { CartState } from '../../core/cart-state';
import { OrderApi, OrderView } from '../../core/order-api';
import { CartComponent } from '../cart/cart';
import { CheckoutComponent } from '../checkout/checkout';
@Component({
  selector: 'app-cart-page',
  imports: [CartComponent, RouterLink],
  template: ` <div class="content-narrow">
    <nav class="breadcrumb" aria-label="Étapes d’achat">
      <strong aria-current="step">1. Panier</strong><span>→</span><span>2. Livraison</span
      ><span>→</span><span>3. Confirmation</span>
    </nav>
    <h1>Votre panier.</h1>
    <app-cart [account]="session.account()" [manageSession]="false" />
    <div class="actions purchase-actions">
      <a class="button secondary" routerLink="/catalogue">Continuer mes achats</a>
      @if (
        cart.view().items.length &&
        !cart.busy() &&
        !cart.recoveryPending() &&
        cart.view().subtotalEstimate &&
        !cart.view().shortages.length
      ) {
        <a class="button" routerLink="/commande">Passer à la livraison →</a>
      }
    </div>
  </div>`,
  styles: [
    `
      .purchase-actions {
        margin-top: 25px;
        justify-content: space-between;
      }
    `,
  ],
})
export class CartPage {
  readonly session = inject(SessionState);
  readonly cart = inject(CartState);
}
@Component({
  selector: 'app-checkout-page',
  imports: [CheckoutComponent, RouterLink],
  template: ` <div class="content-narrow">
    <nav class="breadcrumb" aria-label="Étapes d’achat">
      <a routerLink="/panier">1. Panier</a><span>→</span
      ><strong aria-current="step">2. Livraison</strong><span>→</span><span>3. Confirmation</span>
    </nav>
    <h1>On vous livre où ?</h1>
    @if (!session.account()) {
      <p>
        Commandez en invité, ou <a routerLink="/compte">connectez-vous</a> pour retrouver vos
        commandes.
      </p>
    }
    @if (!cart.view().items.length && !cart.busy()) {
      <div class="state-box">
        <p>Votre panier est vide.</p>
        <a class="button" routerLink="/catalogue">Choisir des articles</a>
      </div>
    }
    <app-checkout [account]="session.account()" (placed)="confirmed($event)" />
  </div>`,
})
export class CheckoutPage {
  readonly session = inject(SessionState);
  readonly cart = inject(CartState);
  private readonly router = inject(Router);
  @ViewChild(CheckoutComponent) checkout?: CheckoutComponent;
  confirmed(order: OrderView) {
    void this.router.navigate(['/confirmation', order.id], { replaceUrl: true });
  }
  canLeave(): boolean {
    return (
      !(this.checkout?.uncertain() || this.checkout?.busy()) ||
      window.confirm(
        'Une confirmation est en cours ou sa réponse est incertaine. Restez sur cette page pour réessayer sans créer de doublon. Quitter quand même ?',
      )
    );
  }
  @HostListener('window:beforeunload', ['$event']) beforeUnload(event: BeforeUnloadEvent) {
    if (this.checkout?.uncertain() || this.checkout?.busy()) {
      event.preventDefault();
      event.returnValue = '';
    }
  }
}
@Component({
  selector: 'app-confirmation',
  imports: [RouterLink],
  template: ` <div class="content-narrow">
    @if (loading()) {
      <p class="state-box" role="status">Lecture de votre commande…</p>
    } @else if (error()) {
      <div class="state-box" role="alert">
        <h1>Commande inaccessible</h1>
        <p>{{ error() }}</p>
        <button (click)="load()">Réessayer</button>
        <p><a routerLink="/compte">Se connecter à mon compte</a></p>
      </div>
    } @else if (order(); as order) {
      <div class="confirmation-heading">
        <span aria-hidden="true">✓</span>
        <p class="eyebrow">
          {{ order.status === 'CANCELLED' ? 'ANNULATION ENREGISTRÉE' : 'COMMANDE ENREGISTRÉE' }}
        </p>
        <h1>
          {{
            order.status === 'CANCELLED'
              ? 'Votre commande est annulée.'
              : 'Merci pour votre commande.'
          }}
        </h1>
        <p>
          Référence : <strong class="reference">{{ order.id }}</strong>
        </p>
      </div>
      <p>
        État : {{ statuses[order.status] }}.
        {{
          order.status === 'CANCELLED'
            ? 'Le stock réservé a été libéré.'
            : 'Paiement à la livraison.'
        }}
      </p>
      <section class="receipt">
        <h2>Votre récapitulatif</h2>
        <ul>
          @for (line of order.summary.items; track line.kind + line.offerId) {
            <li>
              <strong>{{ line.quantity }} × {{ line.label }}</strong
              ><span>{{ line.lineTotal.amount }} TND</span>
              <ul>
                @for (c of line.components; track c.skuId) {
                  <li>{{ c.quantity }} × {{ c.label }} par unité achetée</li>
                }
              </ul>
            </li>
          }
        </ul>
        <p>
          Articles : {{ order.summary.subtotal.amount }} TND<br />Livraison :
          {{ order.summary.delivery.amount }} TND
        </p>
        <p class="total">Total : {{ order.summary.total.amount }} TND</p>
        <h3>Adresse de livraison</h3>
        <p>
          {{ order.summary.address.recipient }} · {{ order.summary.address.phone }}<br />{{
            order.summary.address.street
          }}<br />{{ order.summary.address.postalCode }} {{ order.summary.address.city }} ·
          {{ order.summary.address.governorate }} · Tunisie
        </p>
      </section>
      @if (!session.account()) {
        <p>
          Conservez cette référence et cette session navigateur pour consulter votre commande. Une
          session expirée ne permet plus d’accéder à cette page.
        </p>
      }
      @if (message()) {
        <p role="alert">{{ message() }}</p>
      }
      <div class="actions">
        @if (order.status === 'CONFIRMED') {
          <button class="secondary" (click)="cancel()" [disabled]="busy()">
            Annuler la commande
          </button>
        }
        <a class="button" routerLink="/">Retour à l’accueil</a>
      </div>
    }
  </div>`,
  styles: [
    `
      .confirmation-heading {
        text-align: center;
        padding: 15px 0;
      }
      .confirmation-heading > span {
        display: grid;
        place-items: center;
        margin: 0 auto 22px;
        width: 64px;
        height: 64px;
        border-radius: 50%;
        background: #f5eded;
        color: #ac2729;
        font-size: 2rem;
      }
      .confirmation-heading h1 {
        font-size: 2.5rem;
      }
      .reference {
        overflow-wrap: anywhere;
      }
      .receipt {
        border: 1px solid #ddd;
        border-radius: 6px;
        padding: 25px;
        margin-block: 30px;
      }
      .receipt > ul {
        list-style: none;
        padding: 0;
      }
      .receipt > ul > li {
        padding-block: 16px;
        border-bottom: 1px solid #ddd;
      }
      .receipt li span {
        display: block;
        margin-top: 8px;
      }
      .receipt ul ul {
        font-size: 0.82rem;
        color: #666;
        line-height: 1.8;
        margin-top: 12px;
      }
      .total {
        font-weight: 750;
        font-size: 1.3rem;
      }
    `,
  ],
})
export class ConfirmationPage implements OnInit {
  readonly session = inject(SessionState);
  private readonly api = inject(OrderApi);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  readonly order = signal<OrderView | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly message = signal('');
  readonly busy = signal(false);
  readonly statuses: Record<string, string> = {
    CONFIRMED: 'Confirmée',
    PREPARING: 'En préparation',
    SHIPPED: 'Expédiée',
    DELIVERED: 'Livrée',
    CANCELLED: 'Annulée',
  };
  ngOnInit() {
    this.load();
  }
  load() {
    this.loading.set(true);
    this.error.set('');
    this.api
      .get(this.route.snapshot.paramMap.get('id')!)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (order) => {
          this.order.set(order);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.error.set(
            'Cette commande est introuvable ou inaccessible dans votre session actuelle.',
          );
        },
      });
  }
  cancel() {
    const order = this.order();
    if (!order || this.busy()) return;
    this.busy.set(true);
    this.api
      .transition(order.id, 'cancel', false)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (order) => {
          this.order.set(order);
          this.busy.set(false);
        },
        error: () => {
          this.message.set('Annulation impossible. Actualisez la commande pour vérifier son état.');
          this.busy.set(false);
        },
      });
  }
}
