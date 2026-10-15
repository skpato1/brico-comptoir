import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AdminApi, adminError, SocialLinksContent } from '../../core/admin-api';

@Component({
  selector: 'app-admin-social-links',
  imports: [FormsModule],
  template: `
    <div class="workspace-heading">
      <div><p class="eyebrow">BOUTIQUE</p><h2>Réseaux sociaux</h2>
        <p>Ajoutez les pages officielles affichées en haut de la boutique. Un champ vide masque son icône.</p></div>
      <button type="button" class="secondary" (click)="load()" [disabled]="busy()">Recharger</button>
    </div>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (success()) { <p role="status">{{ success() }}</p> }
    @if (value(); as links) {
      <form #form="ngForm" (ngSubmit)="save(form)" class="social-form">
        <div class="social-card">
          <div class="social-card-heading">
            <div class="social-card-name"><span class="social-mark facebook" aria-hidden="true">f</span>
              <div><strong>Facebook</strong><small>Page officielle de la boutique</small></div></div>
            <span class="social-status" [class.configured]="links.facebookUrl.trim()">{{ links.facebookUrl.trim() ? 'Configuré' : 'Masqué' }}</span>
          </div>
          <label for="facebook-url">Adresse de la page</label>
          <input id="facebook-url" name="facebookUrl" type="url" [(ngModel)]="links.facebookUrl"
            pattern="https://(www[.])?facebook[.]com/.+" maxlength="300"
            placeholder="https://www.facebook.com/votre-page" #facebook="ngModel" />
          @if (facebook.invalid && facebook.touched) {
            <small class="field-error" role="alert">Indiquez une adresse HTTPS de page facebook.com.</small>
          } @else { <small>Laissez vide pour masquer l’icône Facebook.</small> }
        </div>
        <div class="social-card">
          <div class="social-card-heading">
            <div class="social-card-name"><span class="social-mark instagram" aria-hidden="true">◎</span>
              <div><strong>Instagram</strong><small>Profil officiel de la boutique</small></div></div>
            <span class="social-status" [class.configured]="links.instagramUrl.trim()">{{ links.instagramUrl.trim() ? 'Configuré' : 'Masqué' }}</span>
          </div>
          <label for="instagram-url">Adresse du profil</label>
          <input id="instagram-url" name="instagramUrl" type="url" [(ngModel)]="links.instagramUrl"
            pattern="https://(www[.])?instagram[.]com/.+" maxlength="300"
            placeholder="https://www.instagram.com/votre-profil/" #instagram="ngModel" />
          @if (instagram.invalid && instagram.touched) {
            <small class="field-error" role="alert">Indiquez une adresse HTTPS de profil instagram.com.</small>
          } @else { <small>Laissez vide pour masquer l’icône Instagram.</small> }
        </div>
        <button type="submit" [disabled]="busy()">{{ busy() ? 'Enregistrement…' : 'Enregistrer les liens' }}</button>
      </form>
    }
  `,
  styles: [`
    .social-form { display: grid; gap: 14px; max-width: 680px; margin-top: 24px; }
    .social-card { display: grid; gap: 9px; padding: 20px; border: 1px solid #e4e0da; border-radius: 12px; background: #fff; }
    .social-card-heading, .social-card-name { display: flex; align-items: center; gap: 12px; }
    .social-card-heading { justify-content: space-between; margin-bottom: 6px; }
    .social-card-name strong, .social-card-name small { display: block; }
    .social-card-name small, .social-card > small { color: #625f5b; }
    .social-mark { width: 40px; height: 40px; display: grid; place-items: center; flex: none; border-radius: 10px; color: #fff; font-size: 22px; font-weight: 800; }
    .social-mark.facebook { background: #1877f2; }
    .social-mark.instagram { background: linear-gradient(135deg,#7443c0,#d13c79,#f5a345); }
    .social-status { padding: 5px 9px; border-radius: 999px; background: #f0eeeb; color: #555; font-size: .75rem; font-weight: 750; }
    .social-status.configured { background: #e9f5ec; color: #21603d; }
    .social-form label { font-weight: 700; margin-top: 4px; }
    .social-form input { width: 100%; min-height: 46px; padding: 10px 12px; border: 1px solid #b9b5af; border-radius: 7px; }
    .social-form input:focus-visible { outline: 2px solid #b5252b; outline-offset: 2px; }
    .social-form .field-error { color: #a12028; }
    .social-form button { justify-self: start; margin-top: 8px; }
    @media (max-width: 480px) { .social-card { padding: 15px; } .social-card-name small { display: none; } }
  `],
})
export class AdminSocialLinks implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly destroy = inject(DestroyRef);
  readonly value = signal<SocialLinksContent | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  ngOnInit() { this.load(); }
  load() {
    this.busy.set(true); this.error.set(''); this.success.set('');
    this.api.socialLinks(true).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: value => { this.value.set(value); this.busy.set(false); },
      error: error => { this.value.set(null); this.busy.set(false); this.error.set(adminError(error)); },
    });
  }
  save(form: NgForm) {
    const value = this.value();
    if (!value || form.invalid || this.busy()) { form.control.markAllAsTouched(); return; }
    this.busy.set(true); this.error.set(''); this.success.set('');
    this.api.saveSocialLinks(value).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: saved => { this.value.set(saved); this.busy.set(false); this.success.set('Liens enregistrés. Rechargez la boutique pour voir le bandeau mis à jour.'); },
      error: error => { this.busy.set(false); this.error.set(adminError(error, 'Vérifiez les adresses Facebook et Instagram.')); },
    });
  }
}
