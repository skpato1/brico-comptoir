import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NgForm } from '@angular/forms';
import { AccountPage } from './account';
describe('Customer account', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [AccountPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  const valid = { invalid: false } as NgForm;
  it('registers customers without client supplied roles', () => {
    const fixture = TestBed.createComponent(AccountPage);
    const page = fixture.componentInstance;
    page.show('register');
    page.email = 'client@test.invalid';
    page.password = 'Correct-Horse-2026!';
    page.submit(valid);
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    const request = http.expectOne('/api/v1/auth/register');
    expect(request.request.body).toEqual({ email: page.email, password: page.password });
    request.flush({});
    expect(page.message()).toContain('Compte créé');
  });
  it('waits for CSRF renewal before restoring the customer cart', () => {
    const fixture = TestBed.createComponent(AccountPage);
    const page = fixture.componentInstance;
    page.email = 'client@test.invalid';
    page.password = 'Correct-Horse-2026!';
    page.submit(valid);
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    http
      .expectOne('/api/v1/auth/login')
      .flush({ id: 'customer', email: page.email, roles: ['CUSTOMER'], active: true });
    expect(page.session.account()).toBeNull();
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    http
      .expectOne('/api/v1/cart')
      .flush({ version: 0, items: [], subtotalEstimate: null, shortages: [] });
    expect(page.session.account()?.id).toBe('customer');
  });
});
