import { Component, Input, OnChanges, signal } from '@angular/core';
import { ProductImage } from '../core/catalog-api';
import { PackImage } from '../core/packs-api';
@Component({
  selector: 'app-product-photo',
  template: ` <div class="photo" [class.detail]="detail">
    @if (image && !failed()) {
      <img
        [src]="image.cardUrl"
        [attr.srcset]="srcset()"
        [attr.sizes]="
          detail
            ? '(min-width: 800px) 50vw, 100vw'
            : '(min-width: 1000px) 280px, (min-width: 600px) 45vw, 90vw'
        "
        [alt]="alt"
        [width]="image.width"
        [height]="image.height"
        loading="lazy"
        decoding="async"
        [class.loaded]="loaded()"
        (load)="loaded.set(true)"
        (error)="failed.set(true)"
      />
    } @else {
      <div class="placeholder">
        <span aria-hidden="true">◇</span
        ><small>{{ failed() ? 'Photo indisponible' : 'Photo à venir' }}</small>
      </div>
    }
  </div>`,
  styles: [
    `
      :host {
        display: block;
      }
      .photo {
        aspect-ratio: 4/3;
        background: #f7f7f5;
        border-radius: 5px;
        overflow: hidden;
        display: grid;
        place-items: center;
      }
      .photo.detail {
        aspect-ratio: 1;
      }
      .photo img {
        height: 100%;
        width: 100%;
        object-fit: contain;
        opacity: 0.35;
        transition: opacity 0.2s;
      }
      .photo img.loaded {
        opacity: 1;
      }
      .placeholder {
        text-align: center;
        color: #777;
      }
      .placeholder span {
        display: block;
        font-size: 4rem;
        font-weight: 200;
        line-height: 1;
      }
      .placeholder small {
        display: block;
        font-size: 0.72rem;
        margin-top: 12px;
      }
    `,
  ],
})
export class ProductPhoto implements OnChanges {
  @Input() image?: ProductImage | PackImage;
  @Input() alt = '';
  @Input() detail = false;
  readonly failed = signal(false);
  readonly loaded = signal(false);
  ngOnChanges(): void {
    this.failed.set(false);
    this.loaded.set(false);
  }
  srcset(): string {
    const image = this.image;
    if (!image) return '';
    return image.width <= 360
      ? `${image.cardUrl} ${image.width}w`
      : `${image.cardUrl} 360w, ${image.detailUrl} ${Math.min(1200, image.width)}w`;
  }
}
