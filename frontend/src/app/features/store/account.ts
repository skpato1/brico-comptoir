import { Component, inject, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { Account, IdentityApi } from '../../core/identity-api';
import { SessionState } from '../../core/session-state';
import { CheckoutComponent } from '../checkout/checkout';
@Component({
  selector: 'app-account',
  imports: [FormsModule, RouterLink, CheckoutComponent],
  template: `
    <div class="content-narrow">
      <p class="eyebrow">VOTRE ESPACE</p>
      <h1>Mon compte.</h1>
      @if (session.account(); as account) {
        <p>Connecté à BricoComptoir : {{ account.email }}</p>
        <p><a routerLink="/mes-donnees">Mes données et mon choix marketing</a></p>
        <div class="actions">
          <button class="secondary" (click)="logout()" [disabled]="busy()">Se déconnecter</button>
          @if (
            account.roles.includes('ADMIN') ||
            account.roles.includes('CATALOG_MANAGER') ||
            account.roles.includes('ORDER_MANAGER')
          ) {
            <a class="button secondary" routerLink="/gestion">Ouvrir l’administration</a>
          }
        </div>
        <app-checkout [account]="account" [historyOnly]="true" />
      } @else {
        <p>
          Retrouvez votre panier et vos commandes. Vous pouvez aussi
          <a routerLink="/panier">commander sans compte</a>.
        </p>
        <h2>
          {{
            mode() === 'register'
              ? 'Créer un compte'
              : mode() === 'request'
                ? 'Mot de passe oublié'
                : mode() === 'complete'
                  ? 'Nouveau mot de passe'
                  : 'Se connecter'
          }}
        </h2>
        <form #form="ngForm" (ngSubmit)="submit(form)">
          @if (mode() !== 'complete') {
            <label for="account-email">Adresse e-mail</label
            ><input
              id="account-email"
              name="email"
              [(ngModel)]="email"
              type="email"
              email
              autocomplete="email"
              required
              maxlength="254"
              #emailField="ngModel"
              [attr.aria-invalid]="emailField.invalid && (emailField.touched || form.submitted)"
              aria-describedby="email-error"
            />
            @if (emailField.invalid && (emailField.touched || form.submitted)) {
              <p id="email-error" class="error">Saisissez une adresse e-mail valide.</p>
            }
          }
          @if (mode() !== 'request') {
            <label for="account-password">Mot de passe</label
            ><input
              id="account-password"
              name="password"
              [(ngModel)]="password"
              type="password"
              [attr.autocomplete]="mode() === 'login' ? 'current-password' : 'new-password'"
              required
              minlength="12"
              maxlength="128"
              #passwordField="ngModel"
              [attr.aria-invalid]="
                passwordField.invalid && (passwordField.touched || form.submitted)
              "
              aria-describedby="password-help"
            />
            <p
              id="password-help"
              [class.error]="passwordField.invalid && (passwordField.touched || form.submitted)"
            >
              De 12 à 128 caractères.
            </p>
          }
          <button type="submit" [disabled]="busy()">
            {{
              busy()
                ? 'Patientez…'
                : mode() === 'register'
                  ? 'Créer mon compte'
                  : mode() === 'request'
                    ? 'Envoyer le lien'
                    : mode() === 'complete'
                      ? 'Modifier le mot de passe'
                      : 'Se connecter'
            }}
          </button>
        </form>
        <div class="actions auth-links">
          @if (mode() !== 'login') {
            <button class="secondary" (click)="show('login')">Connexion</button>
          }
          @if (mode() !== 'register') {
            <button class="secondary" (click)="show('register')">Créer un compte</button>
          }
          @if (mode() !== 'request') {
            <button class="secondary" (click)="show('request')">Mot de passe oublié</button>
          }
        </div>
      }
      @if (message()) {
        <p role="status">{{ message() }}</p>
      }
    </div>
  `,
  styles: [
    `
      form {
        display: grid;
        gap: 10px;
        max-width: 460px;
      }
      form button {
        margin-top: 12px;
      }
      form p {
        margin: 0;
        font-size: 0.8rem;
      }
      .auth-links {
        margin-top: 25px;
      }
      .auth-links button {
        font-size: 0.8rem;
      }
    `,
  ],
})
export class AccountPage {
  readonly session = inject(SessionState);
  private readonly api = inject(IdentityApi);
  readonly mode = signal<'login' | 'register' | 'request' | 'complete'>(
    location.hash.startsWith('#reset=') ? 'complete' : 'login',
  );
  readonly busy = signal(false);
  readonly message = signal('');
  email = '';
  password = '';
  private token = location.hash.startsWith('#reset=') ? location.hash.slice(7) : '';
  show(mode: 'login' | 'register' | 'request' | 'complete') {
    this.mode.set(mode);
    this.password = '';
    this.message.set('');
  }
  submit(form: NgForm) {
    if (form.invalid) {
      form.control.markAllAsTouched();
      this.message.set('Vérifiez les champs indiqués.');
      return;
    }
    if (this.busy()) return;
    this.busy.set(true);
    this.message.set('');
    const mode = this.mode();
    const operation: Observable<Account | void> =
      mode === 'register'
        ? this.api.register(this.email, this.password)
        : mode === 'request'
          ? this.api.requestReset(this.email)
          : mode === 'complete'
            ? this.api.completeReset(this.token, this.password)
            : this.api.login(this.email, this.password);
    operation.subscribe({
      next: (value) => {
        this.busy.set(false);
        this.password = '';
        if (mode === 'login') this.session.set(value as Account);
        else if (mode === 'register') {
          this.mode.set('login');
          this.message.set('Compte créé. Connectez-vous.');
        } else if (mode === 'request')
          this.message.set('Si ce compte existe, un lien de récupération sera envoyé.');
        else {
          history.replaceState(null, '', location.pathname);
          this.token = '';
          this.session.set(null);
          this.mode.set('login');
          this.message.set('Mot de passe modifié. Connectez-vous.');
        }
      },
      error: () => {
        this.busy.set(false);
        this.message.set(
          mode === 'login'
            ? 'Connexion refusée. Vérifiez vos identifiants.'
            : 'Action impossible. Vérifiez les données ou réessayez plus tard.',
        );
      },
    });
  }
  logout() {
    if (this.busy()) return;
    this.busy.set(true);
    this.api.logout().subscribe({
      next: () => {
        this.session.set(null);
        this.busy.set(false);
      },
      error: () => {
        this.message.set('Déconnexion impossible. Réessayez.');
        this.busy.set(false);
      },
    });
  }
}
