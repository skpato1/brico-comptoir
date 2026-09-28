import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { PrivacyApi, PrivacyView, DataExport } from '../../core/privacy-api';
import { SessionState } from '../../core/session-state';
import { Address, OrderApi } from '../../core/order-api';

@Component({
  selector: 'app-privacy', imports: [FormsModule, RouterLink],
  template: `
    <div class="content-narrow">
      <p class="eyebrow">VOS DONNÉES</p><h1>Mes données personnelles</h1>
      <p>Le compte utilise votre e-mail. Une commande demande le nom du destinataire, un téléphone et une adresse en Tunisie pour la livraison. L’e-mail invité reste facultatif. Aucun numéro d’identité ni donnée bancaire n’est demandé.</p>
      <p>Commander ou recevoir un message de suivi ne vous inscrit pas au marketing. Le panier visiteur garde uniquement les références et les quantités dans ce navigateur. Les cookies de session et de sécurité permettent le compte et les commandes.</p>
      @if (session.account()) {
        @if (view(); as data) {
          <p>Compte : {{ data.account.email }}</p>
          <form (ngSubmit)="saveConsent()">
            <label><input type="checkbox" name="marketing" [(ngModel)]="marketing" /> Je souhaite recevoir des offres BricoComptoir par e-mail.</label>
            <p>Choix facultatif, révocable ici sans effet sur vos commandes. Aucun envoi promotionnel n’est activé actuellement. Notice {{ data.consent.noticeVersion }}.</p>
            <button [disabled]="busy()">Enregistrer mon choix</button>
          </form>
        }
        <form #emailForm="ngForm" (ngSubmit)="requestEmail()">
          <h2>Corriger mon e-mail</h2>
          <label>Nouvelle adresse<input type="email" name="email" [(ngModel)]="email" email required maxlength="254" autocomplete="email" /></label>
          <label>Mot de passe actuel<input type="password" name="emailPassword" [(ngModel)]="password" required maxlength="128" autocomplete="current-password" /></label>
          <button [disabled]="busy() || emailForm.invalid">Envoyer le lien de vérification</button>
        </form>
        @if (token) { <button (click)="confirmEmail()" [disabled]="busy()">Confirmer ma nouvelle adresse e-mail</button> }
      } @else {
        <p><a routerLink="/compte">Connectez-vous</a> pour accéder aux données de votre compte. Les opérations invitées ci-dessous concernent uniquement les commandes de votre session courante.</p>
      }
      <section><h2>Consulter et exporter</h2>
        <p>L’export JSON comprend les coordonnées encore nécessaires, les commandes, et le panier du compte. Les archives de conservation légale restent réservées à une procédure auprès de l’exploitant.</p>
        @if (session.account()) { <label>Mot de passe actuel<input type="password" [(ngModel)]="exportPassword" maxlength="128" autocomplete="current-password" /></label> }
        <button (click)="exportData()" [disabled]="busy() || (!!session.account() && !exportPassword)">Exporter mes données (JSON)</button>
      </section>
      <section><h2>Corriger une livraison avant préparation</h2>
        <label>Référence de ma commande<input [(ngModel)]="orderId" maxlength="36" /></label><button class="secondary" (click)="loadOrder()" [disabled]="busy() || !orderId">Consulter cette commande</button>
        @if (address) {
          <form #addressForm="ngForm" (ngSubmit)="rectify()">
            <label>Destinataire<input name="recipient" [(ngModel)]="address.recipient" required maxlength="120" autocomplete="name" /></label>
            <label>Téléphone<input name="phone" [(ngModel)]="address.phone" required maxlength="20" type="tel" /></label>
            <label>Rue<input name="street" [(ngModel)]="address.street" required maxlength="300" autocomplete="street-address" /></label>
            <label>Ville<input name="city" [(ngModel)]="address.city" required maxlength="100" /></label>
            <label>Code postal<input name="postal" [(ngModel)]="address.postalCode" required pattern="[0-9]{4}" maxlength="4" /></label>
            <p>Gouvernorat : {{ address.governorate }}. Un changement de zone demande un nouvel accord de livraison.</p>
            <button [disabled]="busy() || addressForm.invalid">Enregistrer la correction</button>
          </form>
        }
      </section>
      <section><h2>Retirer mes données usuelles</h2>
        <p>Le compte sera désactivé, les sessions révoquées et le panier supprimé. Les coordonnées des commandes terminées seront retirées des écrans usuels et placées dans une archive chiffrée jusqu’à l’échéance de conservation définie par l’exploitant. Les lignes, montants et traces de stock sont conservés. Cette opération attend la fin des commandes en cours.</p>
        @if (session.account()) { <label>Mot de passe actuel<input type="password" [(ngModel)]="removePassword" maxlength="128" autocomplete="current-password" /></label> }
        <label><input type="checkbox" [(ngModel)]="confirmed" /> Je comprends le retrait des données et la fermeture du compte, si je suis connecté.</label>
        <button class="secondary" (click)="anonymize()" [disabled]="busy() || !confirmed || (!!session.account() && !removePassword)">Confirmer le retrait</button>
      </section>
      @if (busy()) { <p role="status">Traitement en cours…</p> }
      @if (message()) { <p role="status">{{ message() }}</p> }
      @if (error()) { <p role="alert" class="error">{{ error() }}</p> }
    </div>`,
  styles: [`section, form { margin-block: 2rem; } label { display: block; margin-block: .8rem; } input[type=checkbox] { width: auto; margin-right: .5rem; }`],
})
export class PrivacyPage implements OnInit {
  readonly session = inject(SessionState);
  private readonly api = inject(PrivacyApi);
  private readonly orders = inject(OrderApi);
  readonly view = signal<PrivacyView | null>(null);
  readonly busy = signal(false); readonly error = signal(''); readonly message = signal('');
  marketing = false; confirmed = false;
  email = ''; password = ''; exportPassword = ''; removePassword = ''; orderId = '';
  address: Address | null = null;
  token = location.hash.startsWith('#email=') ? location.hash.slice(7) : '';
  constructor() {
    if (this.token) history.replaceState(null, '', location.pathname);
    inject(DestroyRef).onDestroy(() => { this.password = ''; this.exportPassword = ''; this.removePassword = ''; this.token = ''; this.address = null; });
  }
  ngOnInit() { if (this.session.account()) this.run(async () => { this.setView(await firstValueFrom(this.api.me())); }); }
  private setView(v: PrivacyView) { this.view.set(v); this.marketing = v.consent.marketing; }
  saveConsent() { const v = this.view(); if (v) this.run(async () => { this.setView(await firstValueFrom(this.api.consent(this.marketing, v.consent.noticeVersion))); this.message.set('Votre choix est enregistré.'); }); }
  requestEmail() { const password = this.password; this.password = ''; this.run(async () => { await firstValueFrom(this.api.email(password, this.email)); this.message.set('Un lien de vérification sera envoyé à la nouvelle adresse.'); }); }
  confirmEmail() { this.run(async () => { await firstValueFrom(this.api.confirm(this.token)); this.token = ''; this.view.set(null); this.session.set(null); this.message.set('Adresse corrigée. Reconnectez-vous avec la nouvelle adresse.'); }); }
  exportData() {
    const password = this.exportPassword; this.exportPassword = ''; const guest = !this.session.account();
    this.run(async () => {
      let page = 0, batch: DataExport; const all: unknown[] = []; const actions: unknown[] = []; let first: DataExport | undefined;
      do { batch = await firstValueFrom(this.api.export(password, guest, page++)); first ??= batch; all.push(...batch.orders); actions.push(...(batch.actions ?? [])); } while (batch.hasMore);
      const file = new Blob([JSON.stringify({ ...first, orders: all, actions, hasMore: false }, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(file); const link = document.createElement('a'); link.href = url; link.download = 'bricocomptoir-mes-donnees.json'; link.click(); URL.revokeObjectURL(url);
      this.message.set('Export préparé. Conservez ce fichier personnel dans un endroit sûr.');
    });
  }
  loadOrder() { this.run(async () => { const order = await firstValueFrom(this.orders.get(this.orderId)); this.address = { ...order.summary.address }; }); }
  rectify() { const address = this.address; if (address) this.run(async () => { await firstValueFrom(this.api.rectify(this.orderId, address, !this.session.account())); this.address = null; this.message.set('Coordonnées de livraison corrigées.'); }); }
  anonymize() { if (!this.confirmed) return; const password = this.removePassword; this.removePassword = ''; this.run(async () => { await firstValueFrom(this.api.anonymize(password, !this.session.account())); this.session.set(null); this.view.set(null); this.address = null; this.confirmed = false; this.message.set('Données usuelles retirées. Les données à conserver restent protégées.'); }); }
  private async run(action: () => Promise<void>) {
    if (this.busy()) return; this.busy.set(true); this.error.set(''); this.message.set('');
    try { await action(); } catch (failure: unknown) {
      const e = failure as { status?: number; error?: { code?: string } };
      this.error.set(e.status === 401 ? 'Session ou mot de passe invalide. Reconnectez-vous et réessayez.' : e.status === 403 ? 'Cette opération est refusée pour votre accès.' : e.error?.code === 'ACTIVE_ORDERS' ? 'Une commande est encore en cours. Terminez-la ou annulez-la avant le retrait.' : e.error?.code === 'LEGAL_RETENTION_NOT_CONFIGURED' ? 'L’exploitant doit définir la conservation avant de pouvoir retirer ces coordonnées.' : 'Opération impossible. Vérifiez les champs, la référence et l’état de la commande.');
    } finally { this.busy.set(false); }
  }
}
