import { Component, DestroyRef, inject, Input, OnInit, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AdminApi, adminError, ContactDetails, ContactMessagePage } from '../../core/admin-api';
import { AdminPager } from '../../shared/admin-pager';

@Component({ selector: 'app-admin-contact', imports: [FormsModule, DatePipe, AdminPager], templateUrl: './contact.html' })
export class AdminContact implements OnInit {
  @Input() canEdit = false;
  private readonly api = inject(AdminApi);
  private readonly destroy = inject(DestroyRef);
  readonly details = signal<ContactDetails | null>(null);
  readonly messages = signal<ContactMessagePage | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  status: 'NEW' | 'RESOLVED' = 'NEW';
  ngOnInit() { this.loadMessages(); if (this.canEdit) this.loadDetails(); }
  loadDetails() {
    this.api.contact(true).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: value => this.details.set(value), error: e => this.error.set(adminError(e)),
    });
  }
  saveDetails(form: NgForm) {
    if (form.invalid || !this.details()) { form.control.markAllAsTouched(); return; }
    this.busy.set(true); this.error.set(''); this.success.set('');
    this.api.saveContact(this.details()!).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: value => { this.details.set(value); this.busy.set(false); this.success.set('Coordonnées enregistrées.'); },
      error: e => { this.busy.set(false); this.error.set(adminError(e)); },
    });
  }
  loadMessages(page = 0) {
    this.busy.set(true); this.error.set('');
    this.api.messages(page, this.status).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: value => { this.messages.set(value); this.busy.set(false); },
      error: e => { this.messages.set(null); this.busy.set(false); this.error.set(adminError(e)); },
    });
  }
  resolve(id: string) {
    this.busy.set(true); this.error.set(''); this.success.set('');
    this.api.resolveMessage(id).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: () => { this.busy.set(false); this.success.set('Message marqué comme traité.'); this.loadMessages(this.messages()?.page ?? 0); },
      error: e => { this.busy.set(false); this.error.set(adminError(e)); },
    });
  }
}
