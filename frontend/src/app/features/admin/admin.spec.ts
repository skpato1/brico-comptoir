import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AdminPage } from '../store/admin';
import { SessionState } from '../../core/session-state';
import { AdminSettings } from './settings';
import { AdminOrders } from './orders';
import { AdjustmentState } from './stock';
import { CatalogComponent } from '../catalog/catalog';
import { HomePage } from '../store/home';
import { HomeContent } from '../../core/admin-api';
import { AdminHero } from './hero';
import { AdminSupport } from './support';
import { AdminContact } from './contact';
import { ContactPage } from '../store/contact';

const home: HomeContent = {
  title: 'Titre API',
  accent: 'Suite',
  description: 'Texte API',
  solutionTitle: 'Solutions API',
  solutionDescription: 'Détail solution',
  productTitle: 'Produits API',
  productDescription: 'Détail produit',
  version: 3,
};
describe('Administration réelle', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'gestion', component: AdminPage }]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('offers only order management to ORDER_MANAGER and blocks a direct delivery URL', async () => {
    TestBed.inject(SessionState).account.set({
      id: 'manager',
      email: 'manager@example.invalid',
      roles: ['ORDER_MANAGER'],
      active: true,
    });
    const harness = await RouterTestingHarness.create('/gestion?section=livraison');
    expect(harness.routeNativeElement?.textContent).toContain('Cet espace n’est pas accessible');
    const links = Array.from(harness.routeNativeElement!.querySelectorAll('.admin-nav a')).map(
      (a) => a.textContent,
    );
    expect(links).toEqual(['Vue d’ensemble', 'Commandes', 'Contact']);
    http.expectNone('/api/v1/admin/delivery');
    await harness.navigateByUrl('/gestion?section=commandes');
    const request = http.expectOne((r) => r.url === '/api/v1/admin/orders');
    expect(request.request.params.get('page')).toBe('0');
    request.flush([]);
    harness.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('Aucune commande');
  });
  it('shows a server denial even when a local role displays the editor', () => {
    const fixture = TestBed.createComponent(AdminSettings);
    fixture.componentRef.setInput('kind', 'delivery');
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/delivery').flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain(
      'Accès refusé',
    );
    expect(fixture.nativeElement.textContent).not.toContain('Enregistrer les réglages');
  });
  it('keeps the edited content and version after a conflict and obtains CSRF before saving', () => {
    const f = TestBed.createComponent(AdminSettings);
    f.detectChanges();
    http.expectOne('/api/v1/admin/content/home').flush(home);
    f.componentInstance.home()!.title = 'Édition locale';
    f.componentInstance.save({ invalid: false } as never);
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    const write = http.expectOne('/api/v1/admin/content/home');
    expect(write.request.method).toBe('PUT');
    expect(write.request.body.version).toBe(3);
    write.flush({}, { status: 409, statusText: 'Conflict' });
    f.detectChanges();
    expect(f.componentInstance.home()?.title).toBe('Édition locale');
    expect(f.nativeElement.querySelector('[role=alert]').textContent).toContain('Conflit');
    expect(f.componentInstance.success()).toBe('');
  });
  it('does not submit enabled delivery without a served governorate', () => {
    const f = TestBed.createComponent(AdminSettings);
    f.componentRef.setInput('kind', 'delivery');
    f.detectChanges();
    http
      .expectOne('/api/v1/admin/delivery')
      .flush({ enabled: true, amountTnd: '7.000', governorates: [], version: 0 });
    f.componentInstance.save({ invalid: false, control: { markAllAsTouched: vi.fn() } } as never);
    expect(f.componentInstance.error()).toContain('zones');
    http.expectNone('/api/v1/auth/csrf');
  });
  it('reuses exactly the stock operation after an uncertain response across screens', () => {
    const state = TestBed.inject(AdjustmentState);
    const input = {
      operationId: 'same-operation',
      variantId: 'sku',
      delta: 3,
      reason: 'Réception test',
    };
    state.submit(input);
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    http
      .expectOne('/api/v1/admin/stock/adjustments')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    expect(state.pending()).toEqual(input);
    state.submit({ ...input, operationId: 'should-not-replace', delta: 9 });
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    const retry = http.expectOne('/api/v1/admin/stock/adjustments');
    expect(retry.request.body).toEqual(input);
    retry.flush({ variantId: 'sku', onHand: 3, reserved: 0 });
    expect(state.pending()).toBeNull();
  });
  it('paginates catalogue administration at the server instead of truncating to 100 entries', () => {
    const f = TestBed.createComponent(CatalogComponent);
    f.componentRef.setInput('adminOnly', true);
    f.componentRef.setInput('account', {
      id: 'cm',
      email: 'cm@example.invalid',
      roles: ['CATALOG_MANAGER'],
      active: true,
    });
    f.detectChanges();
    http.expectOne('/api/v1/admin/catalog/categories').flush([]);
    http.expectOne('/api/v1/admin/catalog/brands').flush([]);
    http
      .expectOne((r) => r.url === '/api/v1/admin/catalog/categories/page')
      .flush({ items: [], page: 0, size: 20, totalElements: 0 });
    http
      .expectOne((r) => r.url === '/api/v1/admin/catalog/brands/page')
      .flush({ items: [], page: 0, size: 20, totalElements: 0 });
    const first = http.expectOne((r) => r.url === '/api/v1/admin/catalog/products');
    expect(first.request.params.get('size')).toBe('20');
    first.flush({ items: [], page: 0, size: 20, totalElements: 41 });
    f.componentInstance.loadAdminProducts(2);
    const last = http.expectOne((r) => r.url === '/api/v1/admin/catalog/products');
    expect(last.request.params.get('page')).toBe('2');
    last.flush({ items: [], page: 2, size: 20, totalElements: 41 });
    expect(f.componentInstance.adminResult()?.page).toBe(2);
    http.expectNone('/api/v1/products');
  });
  it('keeps failed order transitions visible and does not mark an order shipped locally', () => {
    const f = TestBed.createComponent(AdminOrders);
    f.detectChanges();
    const entry = {
      id: 'order',
      status: 'PREPARING',
      summary: { total: { amount: '12.000' }, address: { recipient: 'Test' } },
    };
    http.expectOne((r) => r.url === '/api/v1/admin/orders').flush([entry]);
    f.componentInstance.transition(entry as never, 'ship');
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    http
      .expectOne('/api/v1/admin/orders/order/ship')
      .flush({}, { status: 403, statusText: 'Forbidden' });
    expect(f.componentInstance.items()[0].status).toBe('PREPARING');
    expect(f.componentInstance.error()).toContain('Accès refusé');
  });
  it('renders home content read from the backend as plain text', () => {
    const f = TestBed.createComponent(HomePage);
    f.detectChanges();
    http.expectOne('/api/v1/content/home').flush({ ...home, title: '<b>Texte</b>' });
    f.detectChanges();
    http.expectOne('/api/v1/content/hero').flush({ version: 0, slides: [] });
    http
      .expectOne((r) => r.url === '/api/v1/products')
      .flush({ items: [], page: 0, size: 4, totalElements: 0 });
    http.expectOne('/api/v1/brands').flush([]);
    f.detectChanges();
    expect(f.nativeElement.querySelector('#start-title').textContent).toContain('<b>Texte</b>');
    expect(f.nativeElement.querySelector('#start-title b')).toBeNull();
  });
  it('uploads selected photos with CSRF and displays a denied operation without replacing metadata', () => {
    const f = TestBed.createComponent(CatalogComponent);
    f.componentInstance.productId = 'product';
    f.componentInstance.selectedImages = [new File(['test'], 'test.png', { type: 'image/png' })];
    f.componentInstance.uploadImages();
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    const upload = http.expectOne('/api/v1/admin/catalog/products/product/images');
    expect((upload.request.body as FormData).getAll('files')).toHaveLength(1);
    upload.flush({}, { status: 403, statusText: 'Forbidden' });
    expect(f.componentInstance.adminBusy()).toBe(false);
    expect(f.componentInstance.adminImages()).toEqual([]);
    expect(f.componentInstance.adminMessage()).toContain('Accès refusé');
  });
  it('saves editable hero slides with CSRF and keeps the server as authority', () => {
    const f = TestBed.createComponent(AdminHero);
    f.detectChanges();
    const original = { version: 3, slides: [{
      id: '11111111-1111-4111-8111-111111111111', visible: true, image: 'kits',
      label: 'Kits', title: 'Un projet', description: 'Description', alt: 'Illustration IA',
      link: '/packs', action: 'Découvrir', detail: 'Détail',
    }] };
    http.expectOne('/api/v1/admin/content/hero').flush(original);
    f.detectChanges();
    expect(f.nativeElement.querySelector('option[value="brico-projects"]')).not.toBeNull();
    expect(f.nativeElement.querySelector('option[value="brico-workshop"]')).not.toBeNull();
    f.componentInstance.value()!.slides[0].title = 'Un projet modifié';
    f.componentInstance.save({ invalid: false, control: { markAllAsTouched: vi.fn() } } as never);
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    const write = http.expectOne('/api/v1/admin/content/hero');
    expect(write.request.method).toBe('PUT');
    expect(write.request.body.slides[0].title).toBe('Un projet modifié');
    write.flush({}, { status: 403, statusText: 'Forbidden' });
    expect(f.componentInstance.error()).toContain('Accès refusé');
    expect(f.componentInstance.value()?.version).toBe(3);
  });
  it('looks up support data by exact email and never sends cart mutations', () => {
    const f = TestBed.createComponent(AdminSupport);
    f.detectChanges();
    f.componentInstance.email = 'client@example.invalid';
    f.componentInstance.search({ invalid: false, control: { markAllAsTouched: vi.fn() } } as never);
    const lookup = http.expectOne(r => r.url === '/api/v1/admin/accounts/lookup');
    expect(lookup.request.params.get('email')).toBe('client@example.invalid');
    lookup.flush({ id: 'customer-id', email: 'client@example.invalid', roles: ['CUSTOMER'], active: true });
    const cart = http.expectOne('/api/v1/admin/support/carts/customer-id');
    expect(cart.request.method).toBe('GET');
    cart.flush({ customerId: 'customer-id', version: 2, lines: [{ kind: 'PACK', offerId: 'offer-id', quantity: 3 }] });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('3');
    expect(f.nativeElement.textContent).toContain('lecture seule');
  });
  it('lets an order manager read contact messages but does not load contact editing', () => {
    const f = TestBed.createComponent(AdminContact);
    f.componentRef.setInput('canEdit', false);
    f.detectChanges();
    const list = http.expectOne(r => r.url === '/api/v1/admin/contact/messages');
    list.flush({ items: [], page: 0, size: 20, totalElements: 0 });
    http.expectNone('/api/v1/admin/contact');
    expect(f.nativeElement.textContent).not.toContain('Enregistrer les coordonnées');
  });
  it('submits the public contact form with CSRF and reports a rate limit', () => {
    const f = TestBed.createComponent(ContactPage);
    f.detectChanges();
    http.expectOne('/api/v1/contact').flush({ email: '', phone: '', address: '', version: 0 });
    f.componentInstance.message = { name: 'Client', email: 'client@example.invalid', subject: 'Question', body: 'Bonjour', website: '' };
    f.componentInstance.submit({ invalid: false, control: { markAllAsTouched: vi.fn() } } as never);
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    const send = http.expectOne('/api/v1/contact/messages');
    expect(send.request.method).toBe('POST');
    send.flush({}, { status: 429, statusText: 'Too Many Requests' });
    f.detectChanges();
    expect(f.nativeElement.querySelector('[role=alert]').textContent).toContain('Trop de messages');
  });
});
