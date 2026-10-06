import {
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  OnInit,
  signal,
  ViewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Brand, CatalogApi } from '../../core/catalog-api';

interface Logo {
  name: string;
  brandSlug: string;
  file: string;
  dark?: boolean;
}

const logoGroups: { title: string; logos: Logo[] }[] = [
  {
    title: 'Outillage',
    logos: [
      { name: 'TOTAL', brandSlug: 'sqes-total-ab2882c8', file: 'total.webp' },
      { name: 'BOSCH', brandSlug: 'sqes-bosch-be05428c', file: 'bosch.webp' },
      { name: 'ACEM', brandSlug: 'sqes-acem-ad938da5', file: 'acem.jpg' },
      { name: 'WADFOW', brandSlug: 'sqes-wadfow-46c54bb1', file: 'wadfow.png', dark: true },
    ],
  },
  {
    title: 'Peinture',
    logos: [
      { name: 'DEUTSCHCOLOR', brandSlug: 'sqes-deutschcolor-32f6b59d', file: 'deutschcolor.svg' },
      { name: 'ASTRAL', brandSlug: 'sqes-astral-e7fa1961', file: 'astral.webp' },
      { name: 'FLASCH', brandSlug: 'sqes-flasch-44ceb785', file: 'flasch.webp' },
      { name: 'SARATOGA', brandSlug: 'sqes-saratoga-2b6bed02', file: 'saratoga.webp' },
    ],
  },
  {
    title: 'Jardinage',
    logos: [
      { name: 'GARDENA', brandSlug: 'sqes-gardena-d458c484', file: 'gardena.svg' },
      { name: 'BELLOTA', brandSlug: 'sqes-bellota-907c4e54', file: 'bellota.webp' },
      { name: 'SIROFLEX', brandSlug: 'sqes-siroflex-f033ed9d', file: 'siroflex.webp' },
      { name: 'TOTAL Jardinage', brandSlug: 'sqes-total-ab2882c8', file: 'total.webp' },
    ],
  },
  {
    title: 'Sanitaire',
    logos: [
      { name: 'SOPAL', brandSlug: 'sqes-sopal-05a6bf0f', file: 'sopal.webp' },
      { name: 'FABIA', brandSlug: 'sqes-fabia-67dddb17', file: 'fabia.webp' },
      { name: 'DURA', brandSlug: 'sqes-dura-6dc040d0', file: 'dura.webp' },
      { name: 'FLR', brandSlug: 'sqes-flr-d55eb644', file: 'flr.webp' },
    ],
  },
  {
    title: 'Adhésif',
    logos: [
      { name: 'BOSS TAPE', brandSlug: 'sqes-boss-tape-a7978265', file: 'boss-tape.webp' },
      { name: 'GEKO', brandSlug: 'sqes-geko-868c4494', file: 'geko.webp' },
    ],
  },
  {
    title: 'Électroménager',
    logos: [{ name: 'UFESA', brandSlug: 'sqes-ufesa-44e099f5', file: 'ufesa.webp' }],
  },
];

@Component({
  selector: 'app-brand-showcase',
  imports: [RouterLink],
  templateUrl: './brand-showcase.html',
  styleUrl: './brand-showcase.scss',
})
export class BrandShowcase implements OnInit {
  @ViewChild('logoRail') private logoRail?: ElementRef<HTMLElement>;

  private readonly api = inject(CatalogApi);
  private readonly destroy = inject(DestroyRef);
  private readonly availableBrands = signal<Brand[]>([]);
  readonly selectedCategory = signal('Toutes');

  readonly groups = computed(() => {
    const available = new Map(
      this.availableBrands()
        .filter((brand) => brand.active)
        .map((brand) => [brand.slug, brand]),
    );
    return logoGroups
      .map((group) => ({
        title: group.title,
        logos: group.logos.flatMap((logo) => {
          const brand = available.get(logo.brandSlug);
          return brand ? [{ ...logo, brandId: brand.id }] : [];
        }),
      }))
      .filter((group) => group.logos.length > 0);
  });

  readonly visibleLogos = computed(() => {
    const selected = this.selectedCategory();
    const groups =
      selected === 'Toutes'
        ? this.groups()
        : this.groups().filter((group) => group.title === selected);
    const unique = new Map<string, (typeof groups)[number]['logos'][number]>();
    for (const group of groups) {
      for (const logo of group.logos) {
        if (!unique.has(logo.brandId)) unique.set(logo.brandId, logo);
      }
    }
    return [...unique.values()];
  });

  selectCategory(category: string): void {
    this.selectedCategory.set(category);
    if (this.logoRail) this.logoRail.nativeElement.scrollLeft = 0;
  }

  scrollLogos(direction: -1 | 1): void {
    const rail = this.logoRail?.nativeElement;
    if (!rail) return;
    rail.scrollBy({
      left: direction * Math.max(rail.clientWidth * 0.8, 160),
      behavior: globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches
        ? 'auto'
        : 'smooth',
    });
  }

  ngOnInit(): void {
    this.api
      .brands()
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({ next: (brands) => this.availableBrands.set(brands), error: () => {} });
  }
}
