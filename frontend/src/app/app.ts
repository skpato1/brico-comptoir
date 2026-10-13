import {
  Component, computed, DestroyRef, ElementRef, HostListener, inject, OnInit, signal, ViewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { SessionState } from './core/session-state';
import { CartState } from './core/cart-state';
import { CatalogApi, Category } from './core/catalog-api';
import { buildCategoryMenu, searchCategoryMenu } from './core/category-menu';
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, NgTemplateOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  @ViewChild('mobileDialog') mobileDialog?: ElementRef<HTMLDialogElement>;
  readonly session = inject(SessionState);
  readonly cart = inject(CartState);
  private readonly catalog = inject(CatalogApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly menuOpen = signal(false);
  readonly categoryLoading = signal(false);
  readonly categoryLoaded = signal(false);
  readonly categoryError = signal(false);
  readonly categories = signal<Category[]>([]);
  readonly menuQuery = signal('');
  readonly categoryMenu = computed(() => buildCategoryMenu(this.categories()));
  readonly categoryMatches = computed(() => searchCategoryMenu(this.categoryMenu(), this.menuQuery()));
  query = '';
  ngOnInit(): void {
    this.session.start();
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        this.closeMenu(false);
        setTimeout(() => {
          document.getElementById('content')?.focus({ preventScroll: true });
          window.scrollTo({ top: 0, behavior: 'instant' });
        });
      });
    if (location.hash.startsWith('#reset='))
      void this.router.navigateByUrl('/compte' + location.hash);
  }
  search(): void {
    void this.router.navigate(['/catalogue'], { queryParams: { q: this.query.trim() || null } });
  }
  count(): number {
    return this.cart.view().items.reduce((total, item) => total + item.quantity, 0);
  }
  openMenu(): void {
    const dialog = this.mobileDialog?.nativeElement;
    if (!dialog || this.menuOpen()) return;
    if (dialog.showModal) dialog.showModal();
    else dialog.setAttribute('open', '');
    this.menuOpen.set(true);
    if (!this.categoryLoaded() && !this.categoryLoading()) this.loadCategories();
  }
  closeMenu(restoreFocus = true): void {
    const dialog = this.mobileDialog?.nativeElement;
    if (!dialog || !this.menuOpen()) return;
    if (dialog.close) dialog.close();
    else dialog.removeAttribute('open');
    this.menuOpen.set(false);
    this.menuQuery.set('');
    if (restoreFocus) document.getElementById('mobile-menu-toggle')?.focus();
  }
  onMenuClosed(): void {
    this.menuOpen.set(false);
    this.menuQuery.set('');
  }
  onDialogClick(event: MouseEvent): void {
    if (event.target === this.mobileDialog?.nativeElement) this.closeMenu();
  }
  @HostListener('window:resize')
  onResize(): void {
    if (window.innerWidth >= 800) this.closeMenu(false);
  }
  private loadCategories(): void {
    this.categoryLoading.set(true);
    this.categoryError.set(false);
    this.catalog.categories().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: categories => {
        this.categories.set(categories);
        this.categoryLoaded.set(true);
        this.categoryLoading.set(false);
      },
      error: () => {
        this.categoryError.set(true);
        this.categoryLoading.set(false);
      },
    });
  }
  retryCategories(): void {
    if (!this.categoryLoading()) this.loadCategories();
  }
}
