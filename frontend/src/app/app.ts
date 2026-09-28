import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { SessionState } from './core/session-state';
import { CartState } from './core/cart-state';
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  readonly session = inject(SessionState);
  readonly cart = inject(CartState);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  query = '';
  ngOnInit(): void {
    this.session.start();
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() =>
        setTimeout(() => {
          document.getElementById('content')?.focus({ preventScroll: true });
          window.scrollTo({ top: 0, behavior: 'instant' });
        }),
      );
    if (location.hash.startsWith('#reset='))
      void this.router.navigateByUrl('/compte' + location.hash);
  }
  search(): void {
    void this.router.navigate(['/catalogue'], { queryParams: { q: this.query.trim() || null } });
  }
  count(): number {
    return this.cart.view().items.reduce((total, item) => total + item.quantity, 0);
  }
}
