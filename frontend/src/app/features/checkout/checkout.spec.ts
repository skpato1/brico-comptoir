import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { vi } from 'vitest';
import { CheckoutComponent } from './checkout';
import { CartState } from '../../core/cart-state';
import { CartView } from '../../core/cart-api';
import { Summary } from '../../core/order-api';

describe('Checkout', () => {
  let fixture: ComponentFixture<CheckoutComponent>;
  let http: HttpTestingController;
  const id = '550e8400-e29b-41d4-a716-446655440000';
  const cart = { view: signal<CartView>({ version: null, items: [], subtotalEstimate: null, shortages: [] }),
    busy: signal(false), recoveryPending: signal(false), clearPurchased: vi.fn() };
  const address = { recipient: 'Client test', phone: '20123456', street: 'Rue test', city: 'Tunis', postalCode: '1000', governorate: 'TUNIS', country: 'TN' as const };
  const money = { amount: '10.000', currency: 'TND' as const };
  const summary: Summary = { address, items: [{ kind: 'PACK', offerId: id, quantity: 1, label: 'Pack test',
    unitPrice: money, lineTotal: money, components: [{ skuId: id, sku: 'TEST', label: 'SKU test', quantity: 2 }] }],
    subtotal: money, delivery: { amount: '7.000', currency: 'TND' }, total: { amount: '17.000', currency: 'TND' }, quoteHash: 'a'.repeat(64) };
  beforeEach(() => {
    cart.clearPurchased.mockClear();
    cart.view.set({ version: null, items: [{ kind: 'PACK', offerId: id, quantity: 1, label: 'Pack test', unitPrice: money,
      lineEstimate: money, offerVersion: 0, parentVersion: 1 }], subtotalEstimate: money, shortages: [] });
    TestBed.configureTestingModule({ imports: [CheckoutComponent], providers: [provideHttpClient(), provideHttpClientTesting(),
      { provide: CartState, useValue: cart }] });
    fixture = TestBed.createComponent(CheckoutComponent); http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('account', null); fixture.detectChanges(); fixture.componentInstance.address = { ...address };
  });
  afterEach(() => http.verify());
  const csrf = () => http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
  it('shows exact server recap and retries uncertain double clicks with the same key and body', () => {
    const component = fixture.componentInstance;
    component.preview(); csrf();
    http.expectOne('/api/v1/checkout/preview').flush(summary); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('17.000 TND');
    expect(fixture.nativeElement.textContent).toContain('Récapitulatif final');
    component.place(); component.place(); csrf();
    const first = http.expectOne('/api/v1/orders'); const key = first.request.headers.get('Idempotency-Key');
    const body = first.request.body;
    expect(key).toBeTruthy(); expect(body.quoteHash).toBe(summary.quoteHash);
    first.flush({}, { status: 503, statusText: 'Unavailable' }); fixture.detectChanges();
    expect(component.uncertain()).toBe(true);
    expect(fixture.nativeElement.querySelector('fieldset').disabled).toBe(true);
    component.place(); csrf(); const retry = http.expectOne('/api/v1/orders');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    expect(retry.request.body).toEqual(body);
    retry.flush({ id, status: 'CONFIRMED', paymentMethod: 'CASH_ON_DELIVERY', createdAt: '', summary });
    expect(cart.clearPurchased).toHaveBeenCalledWith(body.items, null);
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('Commande Confirmée');
  });
  it('requires a new preview after an offer changed instead of silently accepting new prices', () => {
    const component = fixture.componentInstance;
    component.preview(); csrf(); http.expectOne('/api/v1/checkout/preview').flush(summary);
    component.place(); csrf(); http.expectOne('/api/v1/orders').flush({ code: 'OFFER_CHANGED' }, { status: 409, statusText: 'Conflict' });
    expect(component.summary()).toBeNull(); expect(component.error()).toContain('Actualisez le panier');
    expect(cart.clearPurchased).not.toHaveBeenCalled();
    component.place(); http.expectNone('/api/v1/orders');
  });
  it('uses explicit manager transitions and clears private results on a session change', () => {
    fixture.componentRef.setInput('account', { id, email: 'manager@test.invalid', roles: ['ORDER_MANAGER'], active: true }); fixture.detectChanges();
    const component = fixture.componentInstance;
    component.loadOrders(); http.expectOne('/api/v1/admin/orders?page=0&size=20&status=CONFIRMED').flush([]);
    const order = { id, status: 'PREPARING' as const, paymentMethod: 'CASH_ON_DELIVERY' as const, createdAt: '', summary };
    component.transition(order, 'ship', true); csrf();
    http.expectOne(`/api/v1/admin/orders/${id}/ship`).flush({ ...order, status: 'SHIPPED' });
    component.orders.set([order]);
    fixture.componentRef.setInput('account', null); fixture.detectChanges();
    expect(component.orders()).toEqual([]);
  });

  it('requires another recap if quantities changed in the cart before confirmation', () => {
    const component = fixture.componentInstance;
    component.preview(); csrf(); http.expectOne('/api/v1/checkout/preview').flush(summary);
    cart.view.update(view => ({ ...view, items: view.items.map(item => ({ ...item, quantity: 2 })) }));
    component.place(); http.expectNone('/api/v1/orders');
    expect(component.summary()).toBeNull(); expect(component.error()).toContain('panier a été modifié');
  });

  it('announces invalid address fields and blocks the preview request', async () => {
    fixture.componentInstance.address = { ...address, recipient: '', phone: '123', postalCode: '12' };
    fixture.detectChanges(); await fixture.whenStable();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges(); await fixture.whenStable();
    http.expectNone('/api/v1/auth/csrf'); http.expectNone('/api/v1/checkout/preview');
    expect(fixture.nativeElement.querySelector('[name=phone]').getAttribute('aria-invalid')).toBe('true');
    expect(fixture.nativeElement.querySelector('#phone-error').textContent).toContain('huit chiffres');
    expect(fixture.componentInstance.error()).toContain('Vérifiez les champs');
  });
});
