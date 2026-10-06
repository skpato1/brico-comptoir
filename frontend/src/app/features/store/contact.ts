import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AdminApi, ContactDetails } from '../../core/admin-api';
import { IdentityApi } from '../../core/identity-api';

@Component({ selector: 'app-contact', imports: [FormsModule], templateUrl: './contact.html', styleUrl: './contact.scss' })
export class ContactPage implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly http = inject(HttpClient);
  private readonly identity = inject(IdentityApi);
  private readonly destroy = inject(DestroyRef);
  readonly details = signal<ContactDetails | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly sent = signal(false);
  message = { name: '', email: '', subject: '', body: '', website: '' };
  ngOnInit() { this.api.contact().pipe(takeUntilDestroyed(this.destroy)).subscribe({ next: v => this.details.set(v) }); }
  submit(form: NgForm) {
    if (form.invalid) { form.control.markAllAsTouched(); return; }
    if (this.busy()) return;
    this.busy.set(true); this.error.set('');
    this.identity.csrf().pipe(switchMap(() => this.http.post('/api/v1/contact/messages', this.message)),
      takeUntilDestroyed(this.destroy)).subscribe({
      next: () => { this.busy.set(false); this.sent.set(true); this.message = { name: '', email: '', subject: '', body: '', website: '' }; form.resetForm(this.message); },
      error: e => { this.busy.set(false); this.error.set(e.status === 429 ? 'Trop de messages envoyés. Réessayez plus tard.' : 'Message non envoyé. Vérifiez votre connexion puis réessayez.'); },
    });
  }
}
