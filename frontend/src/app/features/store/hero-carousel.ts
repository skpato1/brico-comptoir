import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AdminApi, HeroSlide } from '../../core/admin-api';
import { heroImageUrl, isPromotionalHeroImage } from './hero-images';

@Component({
  selector: 'app-hero-carousel',
  imports: [RouterLink],
  templateUrl: './hero-carousel.html',
  styleUrl: './hero-carousel.scss',
})
export class HeroCarousel implements OnInit {
  readonly heroImageUrl = heroImageUrl;
  readonly isPromotionalHeroImage = isPromotionalHeroImage;
  private readonly document = inject(DOCUMENT);
  private readonly destroy = inject(DestroyRef);
  private readonly api = inject(AdminApi);
  readonly slides = signal<HeroSlide[]>([
    {
      id: 'default-kits', visible: true,
      image: 'brico-projects',
      label: 'Tout pour vos projets',
      title: 'BricoComptoir, tout pour vos projets',
      description:
        'Explorez les produits et packs publiés pour préparer votre prochain projet.',
      alt: 'Visuel promotionnel BricoComptoir : atelier, outils et univers bricolage. Produits illustratifs.',
      link: '/catalogue',
      action: 'Découvrir les produits',
      detail: 'Visuel promotionnel · Produits illustratifs',
    },
    {
      id: 'default-hardware', visible: true,
      image: 'hardware',
      label: 'Fixations & quincaillerie',
      title: 'Chaque pièce\na son importance.',
      description:
        'Vis, chevilles, accessoires : consultez les références du catalogue pour compléter votre sélection.',
      alt: 'Illustration : vis, chevilles, rondelles et équerres sur un établi.',
      link: '/catalogue',
      action: 'Explorer la quincaillerie',
      detail: 'Références et caractéristiques dans chaque fiche',
    },
    {
      id: 'default-tools', visible: true,
      image: 'brico-workshop',
      label: 'Atelier & outillage',
      title: 'L’atelier BricoComptoir',
      description:
        'Retrouvez les références publiées pour bricoler et équiper votre atelier.',
      alt: 'Visuel promotionnel BricoComptoir : établi et outils de bricolage. Produits et marques illustratifs.',
      link: '/catalogue',
      action: 'Explorer le catalogue',
      detail: 'Visuel promotionnel · Produits illustratifs',
    },
  ]);
  readonly active = signal(0);
  readonly paused = signal(false);
  readonly hovered = signal(false);
  readonly reducedMotion = signal(false);
  readonly hidden = signal(false);
  readonly failedImages = signal<ReadonlySet<number>>(new Set());
  readonly rotating = computed(
    () => !this.paused() && !this.hovered() && !this.reducedMotion() && !this.hidden(),
  );
  private pointerStart?: { x: number; y: number };

  ngOnInit(): void {
    this.api.hero().pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: ({ slides }) => {
        if (slides.length) {
          this.slides.set(slides);
          this.active.set(0);
          this.failedImages.set(new Set());
        }
      },
      error: () => { /* Keep the bundled promotional visuals available. */ },
    });
    const view = this.document.defaultView;
    if (!view || typeof view.matchMedia !== 'function') return;
    const media = view.matchMedia('(prefers-reduced-motion: reduce)');
    const syncMotion = () => this.reducedMotion.set(media.matches);
    const syncVisibility = () => this.hidden.set(this.document.hidden);
    syncMotion();
    syncVisibility();
    media.addEventListener('change', syncMotion);
    this.document.addEventListener('visibilitychange', syncVisibility);
    const timer = view.setInterval(() => {
      if (this.rotating() && this.slides().length > 1)
        this.active.update((index) => (index + 1) % this.slides().length);
    }, 6500);
    this.destroy.onDestroy(() => {
      view.clearInterval(timer);
      media.removeEventListener('change', syncMotion);
      this.document.removeEventListener('visibilitychange', syncVisibility);
    });
  }

  select(index: number): void {
    this.paused.set(true);
    if (this.slides().length) this.active.set((index + this.slides().length) % this.slides().length);
  }
  toggleRotation(): void {
    this.paused.update((value) => !value);
  }
  onFocus(event: FocusEvent): void {
    // The rotation button must keep its action stable between focus and click.
    if (!(event.target as HTMLElement).closest('[data-rotation]')) this.paused.set(true);
  }
  onKey(event: KeyboardEvent): void {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    this.select(this.active() + (event.key === 'ArrowRight' ? 1 : -1));
  }
  pointerDown(event: PointerEvent): void {
    if (event.pointerType === 'mouse') return;
    this.pointerStart = { x: event.clientX, y: event.clientY };
  }
  pointerUp(event: PointerEvent): void {
    const start = this.pointerStart;
    this.pointerStart = undefined;
    if (!start) return;
    const dx = event.clientX - start.x;
    const dy = event.clientY - start.y;
    if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy) * 1.5)
      this.select(this.active() + (dx < 0 ? 1 : -1));
  }
  cancelPointer(): void {
    this.pointerStart = undefined;
  }
  imageFailed(index: number): void {
    this.failedImages.update((failed) => new Set([...failed, index]));
  }
}
