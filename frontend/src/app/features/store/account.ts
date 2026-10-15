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
      <p class="eyebrow">ACCÈS SÉCURISÉ</p>
      <h1>Connexion à l’administration</h1>
      @if (session.account(); as account) {
        <p class="account-status">Connecté à BricoComptoir : <strong>{{ account.email }}</strong></p>
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
        @if (account.roles.includes('CUSTOMER')) {
          <p>Ce compte client existant peut encore consulter ses commandes. Les nouveaux achats se font sans création de compte.</p>
          <app-checkout [account]="account" [historyOnly]="true" />
        }
      } @else {
        <p>Accès réservé aux membres de l’équipe. Pour acheter, <a routerLink="/panier">passez commande sans compte</a>.</p>
        <div class="auth-card">
        <h2>
          {{
            mode() === 'request'
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
          @if (mode() !== 'request') {
            <button class="secondary" (click)="show('request')">Mot de passe oublié</button>
          }
        </div>
        </div>
      }
      @if (message()) {
        <p role="status">{{ message() }}</p>
      }
    </div>
  `,
  styles: [
    `
      .account-status { padding: 14px 18px; border-radius: var(--radius); background: var(--surface); overflow-wrap: anywhere; }
      .auth-card { max-width: 520px; padding: 26px; border: 1px solid var(--line); border-radius: var(--radius); background: #fff; }
      .auth-card h2 { margin-top: 0; }
      form {
        display: grid;
        gap: 10px;
      }
      form input { width: 100%; }
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
      @media (max-width: 600px) { .auth-card { padding: 18px; } }
    `,
  ],
})
export class AccountPage {
  readonly session = inject(SessionState);
  private readonly api = inject(IdentityApi);
  readonly mode = signal<'login' | 'request' | 'complete'>(
    location.hash.startsWith('#reset=') ? 'complete' : 'login',
  );
  readonly busy = signal(false);
  readonly message = signal('');
  email = '';
  password = '';
  private token = location.hash.startsWith('#reset=') ? location.hash.slice(7) : '';
  show(mode: 'login' | 'request' | 'complete') {
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
      mode === 'request'
          ? this.api.requestReset(this.email)
          : mode === 'complete'
            ? this.api.completeReset(this.token, this.password)
            : this.api.login(this.email, this.password);
    operation.subscribe({
      next: (value) => {
        this.busy.set(false);
        this.password = '';
        if (mode === 'login') this.session.set(value as Account);
        else if (mode === 'request')
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
