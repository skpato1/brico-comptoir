import { Routes } from '@angular/router';
import { HomePage } from './features/store/home';
import { CheckoutPage } from './features/store/purchase';
export const routes: Routes = [
  { path: 'mes-donnees', loadComponent: () => import('./features/store/privacy').then(m => m.PrivacyPage), title: 'Mes données | BricoComptoir' },
  { path: '', component: HomePage, title: 'BricoComptoir — Vos projets, simplement' },
  {
    path: 'solutions',
    loadComponent: () => import('./features/store/browse').then((m) => m.BrowsePage),
    data: { kind: 'packs', solutions: true },
    title: 'Trouver une solution | BricoComptoir',
  },
  {
    path: 'packs',
    loadComponent: () => import('./features/store/browse').then((m) => m.BrowsePage),
    data: { kind: 'packs' },
    title: 'Les packs | BricoComptoir',
  },
  {
    path: 'packs/:id',
    loadComponent: () => import('./features/store/detail').then((m) => m.DetailPage),
    data: { kind: 'packs' },
    title: 'Votre pack | BricoComptoir',
  },
  {
    path: 'catalogue',
    loadComponent: () => import('./features/store/browse').then((m) => m.BrowsePage),
    title: 'Le catalogue | BricoComptoir',
  },
  {
    path: 'produits/:id',
    loadComponent: () => import('./features/store/detail').then((m) => m.DetailPage),
    title: 'Votre produit | BricoComptoir',
  },
  {
    path: 'panier',
    loadComponent: () => import('./features/store/purchase').then((m) => m.CartPage),
    title: 'Votre panier | BricoComptoir',
  },
  {
    path: 'commande',
    component: CheckoutPage,
    canDeactivate: [(component: CheckoutPage) => component.canLeave()],
    title: 'Livraison et paiement | BricoComptoir',
  },
  {
    path: 'confirmation/:id',
    loadComponent: () => import('./features/store/purchase').then((m) => m.ConfirmationPage),
    title: 'Votre commande | BricoComptoir',
  },
  {
    path: 'compte',
    loadComponent: () => import('./features/store/account').then((m) => m.AccountPage),
    title: 'Mon compte | BricoComptoir',
  },
  {
    path: 'gestion',
    loadComponent: () => import('./features/store/admin').then((m) => m.AdminPage),
    title: 'Gestion | BricoComptoir',
  },
  { path: '**', redirectTo: '' },
];
