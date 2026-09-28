import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CartComponent } from './cart';

describe('Panier', () => {
  let fixture: ComponentFixture<CartComponent>;
  let http: HttpTestingController;
  const offerId = '550e8400-e29b-41d4-a716-446655440000';

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [CartComponent], providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(CartComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('account', null);
    fixture.detectChanges();
  });
  afterEach(() => { http.verify(); localStorage.clear(); });

  it('keeps only references and quantities locally and requests a server estimate', () => {
    fixture.componentInstance.cart.add('PRODUCT', offerId);
    const saved = JSON.parse(localStorage.getItem('bricocomptoir.guest-cart.v1')!);
    expect(saved.items).toEqual([{ kind: 'PRODUCT', offerId, quantity: 1 }]);
    expect(JSON.stringify(saved)).not.toContain('price');
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    const estimate = http.expectOne('/api/v1/cart/estimate');
    expect(estimate.request.body.items).toEqual(saved.items);
    estimate.flush({ version: null, items: [{ ...saved.items[0], label: 'Produit test',
      unitPrice: { amount: '2.375', currency: 'TND' }, lineEstimate: { amount: '2.375', currency: 'TND' },
      offerVersion: 0, parentVersion: 0 }], subtotalEstimate: { amount: '2.375', currency: 'TND' }, shortages: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('2.375 TND');
    fixture.componentInstance.cart.change('PRODUCT', offerId, 0);
    expect(fixture.componentInstance.cart.view().items).toEqual([]);
  });

  it('clears the previous account view when a new session is activated', () => {
    const cart = fixture.componentInstance.cart;
    cart.activate({ id: 'first', email: 'first@example.tn', roles: ['CUSTOMER'], active: true });
    http.expectOne('/api/v1/cart').flush({ version: 1,
      items: [{ kind: 'PRODUCT', offerId, quantity: 2, label: 'Premier compte' }],
      subtotalEstimate: null, shortages: [] });
    expect(cart.view().items.length).toBe(1);
    cart.activate({ id: 'second', email: 'second@example.tn', roles: ['CUSTOMER'], active: true });
    expect(cart.view().items).toEqual([]);
    http.expectOne('/api/v1/cart').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(cart.view().items).toEqual([]);
    expect(cart.error()).toContain('indisponible');
  });

  it('retains a visitor cart after a failed merge and clears it only after success', () => {
    const cart = fixture.componentInstance.cart;
    cart.add('PACK', offerId);
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/v1/cart/estimate').flush({ version: null, items: [], subtotalEstimate: null, shortages: [] });
    const stored = JSON.parse(localStorage.getItem('bricocomptoir.guest-cart.v1')!);
    fixture.componentRef.setInput('account', { id: 'customer', email: 'client@example.tn',
      roles: ['CUSTOMER'], active: true });
    fixture.detectChanges();
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    const failed = http.expectOne('/api/v1/cart/merge');
    expect(failed.request.body.mergeId).toBe(stored.mergeId);
    failed.flush({}, { status: 503, statusText: 'Unavailable' });
    expect(cart.recoveryPending()).toBe(true);
    expect(localStorage.getItem('bricocomptoir.guest-cart.v1')).not.toBeNull();
    cart.retryRecovery();
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    const retried = http.expectOne('/api/v1/cart/merge');
    expect(retried.request.body.mergeId).toBe(stored.mergeId);
    retried.flush({ version: 1, items: [{ ...stored.items[0], label: 'Pack test', unitPrice: null,
      lineEstimate: null, offerVersion: 0, parentVersion: 0 }], subtotalEstimate: null, shortages: [] });
    expect(cart.recoveryPending()).toBe(false);
    expect(localStorage.getItem('bricocomptoir.guest-cart.v1')).toBeNull();
  });
});
