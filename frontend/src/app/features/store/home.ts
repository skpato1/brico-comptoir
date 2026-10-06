import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CatalogApi, Product, ProductImage } from '../../core/catalog-api';
import { HeroCarousel } from './hero-carousel';
import { BrandShowcase } from './brand-showcase';
import { OfferCard } from '../../shared/offer-card';
import { AdminApi, HomeContent } from '../../core/admin-api';
@Component({
  selector: 'app-home',
  imports: [RouterLink, OfferCard, HeroCarousel, BrandShowcase],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class HomePage implements OnInit {
  private readonly api = inject(CatalogApi);
  private readonly contentApi = inject(AdminApi);
  readonly content = signal<HomeContent | null>(null);
  readonly contentError = signal(false);
  private readonly destroy = inject(DestroyRef);
  readonly products = signal<Product[]>([]);
  readonly images = signal<Record<string, ProductImage[]>>({});
  readonly loading = signal(true);
  readonly error = signal(false);
  ngOnInit(): void {
    this.loadContent();
    this.load();
  }
  loadContent(): void {
    this.contentError.set(false);
    this.contentApi
      .home()
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({ next: (v) => this.content.set(v), error: () => this.contentError.set(true) });
  }
  primaryImage(id: string): ProductImage | undefined {
    const images = this.images()[id] ?? [];
    return images.find((image) => image.primary) ?? images[0];
  }
  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.api
      .products({
        q: '',
        categoryId: '',
        brandId: '',
        minPrice: '',
        maxPrice: '',
        sort: 'newest',
        page: 0,
        size: 4,
      })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (page) => {
          this.products.set(page.items);
          this.loading.set(false);
          if (page.items.length)
            this.api
              .publicImages(page.items.map((p) => p.id))
              .pipe(takeUntilDestroyed(this.destroy))
              .subscribe({
                next: (images) => this.images.set(images),
                error: () => this.images.set({}),
              });
        },
        error: () => {
          this.loading.set(false);
          this.error.set(true);
        },
      });
  }
}
