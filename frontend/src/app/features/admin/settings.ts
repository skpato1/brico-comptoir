import { Component, DestroyRef, inject, Input, OnInit, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AdminApi,
  adminError,
  HomeContent,
  DeliverySettings,
  GOVERNORATES,
} from '../../core/admin-api';
@Component({
  selector: 'app-admin-settings',
  imports: [FormsModule],
  templateUrl: './settings.html',
})
export class AdminSettings implements OnInit {
  @Input() kind: 'home' | 'delivery' = 'home';
  private readonly api = inject(AdminApi);
  private readonly destroy = inject(DestroyRef);
  readonly home = signal<HomeContent | null>(null);
  readonly delivery = signal<DeliverySettings | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly zones = GOVERNORATES;
  ngOnInit() {
    this.load();
  }
  load() {
    this.busy.set(true);
    this.error.set('');
    this.success.set('');
    if (this.kind === 'home')
      this.api
        .home(true)
        .pipe(takeUntilDestroyed(this.destroy))
        .subscribe({
          next: (value) => {
            this.home.set(value);
            this.busy.set(false);
          },
          error: (e) => this.fail(e),
        });
    else
      this.api
        .delivery()
        .pipe(takeUntilDestroyed(this.destroy))
        .subscribe({
          next: (value) => {
            this.delivery.set(value);
            this.busy.set(false);
          },
          error: (e) => this.fail(e),
        });
  }
  toggleZone(zone: string, checked: boolean) {
    const value = this.delivery();
    if (value)
      value.governorates = checked
        ? [...value.governorates, zone]
        : value.governorates.filter((z) => z !== zone);
  }
  save(form: NgForm) {
    this.success.set('');
    this.error.set('');
    if (
      form.invalid ||
      (this.kind === 'delivery' &&
        this.delivery()?.enabled &&
        !this.delivery()?.governorates.length)
    ) {
      form.control.markAllAsTouched();
      this.error.set('Vérifiez les champs requis, les limites et les zones de livraison.');
      return;
    }
    if (this.busy()) return;
    this.busy.set(true);
    if (this.kind === 'home')
      this.api
        .saveHome({ ...this.home()! })
        .pipe(takeUntilDestroyed(this.destroy))
        .subscribe({
          next: (v) => {
            this.home.set(v);
            this.saved();
          },
          error: (e) => this.fail(e),
        });
    else
      this.api
        .saveDelivery({ ...this.delivery()!, governorates: [...this.delivery()!.governorates] })
        .pipe(takeUntilDestroyed(this.destroy))
        .subscribe({
          next: (v) => {
            this.delivery.set(v);
            this.saved();
          },
          error: (e) => this.fail(e),
        });
  }
  private saved() {
    this.busy.set(false);
    this.success.set('Réglages enregistrés.');
  }
  private fail(e: { status?: number }) {
    this.busy.set(false);
    this.error.set(adminError(e));
  }
}
