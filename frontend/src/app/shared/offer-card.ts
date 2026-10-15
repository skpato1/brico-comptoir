import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Product, ProductImage } from '../core/catalog-api';
import { Pack, PackImage } from '../core/packs-api';
import { ProductPhoto } from './product-photo';
export function minimumPrice(offer: Product | Pack): string {
  return offer.variants.reduce(
    (min, v) =>
      !min || BigInt(v.price.amount.replace('.', '')) < BigInt(min.replace('.', ''))
        ? v.price.amount
        : min,
    '',
  );
}
@Component({
  selector: 'app-offer-card',
  imports: [RouterLink, ProductPhoto],
  template: ` <article>
    <a
      [routerLink]="[pack ? '/packs' : '/produits', offer.id]"
      [attr.aria-label]="'Voir ' + offer.name"
    >
      @if (pack && !image) {
        <div class="pack-art" aria-hidden="true">
          <span>PACK</span><strong>+</strong><small>Un projet, plusieurs articles.</small>
        </div>
      } @else {
        <app-product-photo [image]="image" [alt]="offer.name" />
      }
    </a>
    <div class="card-body">
      <span class="kind">{{ brandName || (pack ? 'PACK' : 'PRODUIT') }}</span>
      @if (offer.demo) {
        <span class="badge">DÉMO</span>
      }
      <h3>
        <a [routerLink]="[pack ? '/packs' : '/produits', offer.id]">{{ offer.name }}</a>
      </h3>
      @if (description) { <p class="description">{{ description }}</p> }
      <div class="price">
        <span><small>À partir de</small><strong>{{ price() }} <small>TND</small></strong></span>
      </div>
      <a class="open" [routerLink]="[pack ? '/packs' : '/produits', offer.id]">
        Voir {{ pack ? 'le pack' : 'le produit' }} <span aria-hidden="true">→</span>
      </a>
    </div>
  </article>`,
  styles: [
    `
      article {
        height: 100%;
        border: 1px solid var(--line);
        border-radius: var(--radius);
        overflow: hidden;
        background: #fff;
        display: flex;
        flex-direction: column;
        transition: box-shadow .2s ease, transform .2s ease;
      }
      article:hover { box-shadow: var(--shadow); transform: translateY(-2px); }
      article > a {
        display: block;
        text-decoration: none;
      }
      .card-body {
        padding: 16px;
        display: flex;
        flex-direction: column;
        flex: 1;
      }
      .kind {
        font-size: 0.68rem;
        letter-spacing: .7px;
        color: var(--muted);
        font-weight: 750;
        text-transform: uppercase;
      }
      .badge {
        margin-left: 10px;
      }
      h3 {
        font-size: 1rem;
        margin: 12px 0 7px;
        line-height: 1.35;
        min-height: 2.7em;
      }
      h3 a {
        text-decoration: none;
      }
      h3 a:hover {
        text-decoration: underline;
      }
      .description {
        font-size: 0.82rem;
        color: var(--muted);
        margin: 0 0 14px;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }
      .price {
        display: flex;
        justify-content: space-between;
        align-items: end;
        margin-top: auto;
        padding-top: 12px;
      }
      .price span > small {
        display: block;
        font-size: 0.65rem;
        color: #666;
        margin-bottom: 4px;
      }
      .price strong {
        font-size: 1.25rem;
      }
      .price strong small {
        font-size: 0.75rem;
        font-weight: 500;
      }
      .open {
        display: flex;
        justify-content: space-between;
        align-items: center;
        min-height: 44px;
        margin-top: 14px;
        padding: 9px 12px;
        border: 1px solid var(--line);
        border-radius: 7px;
        text-decoration: none;
        color: var(--red);
        font-size: .84rem;
        font-weight: 750;
      }
      .open:hover {
        background: #fff0f0;
        border-color: var(--red);
      }
      .pack-art {
        aspect-ratio: 4/3;
        background: #f7f3ee;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        position: relative;
      }
      .pack-art > span {
        font-size: 0.65rem;
        letter-spacing: 4px;
      }
      .pack-art strong {
        font-weight: 200;
        line-height: 1;
        font-size: 6rem;
        color: #bd252b;
      }
      .pack-art small {
        font-size: 0.7rem;
        color: #665a50;
      }
    `,
  ],
})
export class OfferCard {
  @Input({ required: true }) offer!: Product | Pack;
  @Input() pack = false;
  @Input() image?: ProductImage | PackImage;
  @Input() description = '';
  @Input() brandName = '';
  price(): string {
    return minimumPrice(this.offer);
  }
}
