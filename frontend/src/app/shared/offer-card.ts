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
      <span class="kind">{{ pack ? 'LA SOLUTION EN PACK' : 'À L’UNITÉ' }}</span>
      @if (offer.demo) {
        <span class="badge">DÉMO</span>
      }
      <h3>
        <a [routerLink]="[pack ? '/packs' : '/produits', offer.id]">{{ offer.name }}</a>
      </h3>
      <p>{{ description }}</p>
      <div class="price">
        <span
          ><small>À partir de</small><strong>{{ price() }} <small>TND</small></strong></span
        ><a
          class="open"
          [routerLink]="[pack ? '/packs' : '/produits', offer.id]"
          [attr.aria-label]="'Choisir ' + offer.name"
          >↗</a
        >
      </div>
    </div>
  </article>`,
  styles: [
    `
      article {
        height: 100%;
        border: 1px solid #e4e4e4;
        border-radius: 7px;
        overflow: hidden;
        background: #fff;
      }
      article > a {
        display: block;
        text-decoration: none;
      }
      .card-body {
        padding: 20px;
      }
      .kind {
        font-size: 0.6rem;
        letter-spacing: 1.4px;
        color: #777;
        font-weight: 650;
      }
      .badge {
        margin-left: 10px;
      }
      h3 {
        font-size: 1.15rem;
        margin: 12px 0 7px;
        line-height: 1.35;
      }
      h3 a {
        text-decoration: none;
      }
      h3 a:hover {
        text-decoration: underline;
      }
      p {
        font-size: 0.82rem;
        color: #666;
        margin: 0 0 20px;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }
      .price {
        display: flex;
        justify-content: space-between;
        align-items: end;
      }
      .price span > small {
        display: block;
        font-size: 0.65rem;
        color: #666;
        margin-bottom: 4px;
      }
      .price strong {
        font-size: 1.2rem;
      }
      .price strong small {
        font-size: 0.75rem;
        font-weight: 500;
      }
      .open {
        display: grid;
        place-items: center;
        min-width: 44px;
        min-height: 44px;
        border: 1px solid #ddd;
        border-radius: 4px;
        text-decoration: none;
        color: #b51f24;
      }
      .open:hover {
        background: #fff0f0;
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
  price(): string {
    return minimumPrice(this.offer);
  }
}
