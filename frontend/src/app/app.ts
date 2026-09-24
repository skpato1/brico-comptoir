import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiHealth } from './core/api-health';

@Component({
  selector: 'app-root',
  imports: [DatePipe],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  private readonly api = inject(ApiHealth);
  private readonly destroyRef = inject(DestroyRef);
  readonly state = signal<'checking' | 'up' | 'down'>('checking');
  readonly checkedAt = signal<Date | null>(null);

  ngOnInit(): void {
    this.checkApi();
  }

  checkApi(): void {
    this.state.set('checking');
    this.api.check().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.finish('up'),
      error: () => this.finish('down'),
    });
  }

  private finish(state: 'up' | 'down'): void {
    this.state.set(state);
    this.checkedAt.set(new Date());
  }
}
