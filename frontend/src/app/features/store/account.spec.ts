import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NgForm } from '@angular/forms';
import { AccountPage } from './account';
describe('Secure access for existing accounts', () => {
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
  it('does not offer new customer account creation', () => {
    const fixture = TestBed.createComponent(AccountPage);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Connexion à l’administration');
    expect(fixture.nativeElement.textContent).not.toContain('Créer un compte');
    http.expectNone('/api/v1/auth/register');
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
