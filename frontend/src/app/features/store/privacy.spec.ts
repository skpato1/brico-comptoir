import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PrivacyPage } from './privacy';
import { PrivacyApi } from '../../core/privacy-api';
import { SessionState } from '../../core/session-state';
import { Account } from '../../core/identity-api';
describe('Personal data and independent consent', () => {
  let http: HttpTestingController;
  const account: Account = { id: 'customer', email: 'test@example.invalid', roles: ['CUSTOMER'], active: true };
  let session: { account: ReturnType<typeof signal<Account | null>>; set: (value: Account | null) => void };
  const view = { account, consent: { marketing: false, noticeVersion: '2026-09-28', updatedAt: null } };
  beforeEach(() => {
    session = { account: signal<Account | null>(account), set(value) { this.account.set(value); } };
    TestBed.configureTestingModule({ imports: [PrivacyPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: SessionState, useValue: session }] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  async function page() {
    const fixture = TestBed.createComponent(PrivacyPage); fixture.detectChanges();
    if (session.account()) http.expectOne('/api/v1/privacy/me').flush(view);
    await fixture.whenStable(); fixture.detectChanges(); return fixture;
  }
  it('shows unchecked choices and never posts marketing automatically', async () => {
    const fixture = await page();
    expect(fixture.componentInstance.marketing).toBe(false);
    const checkboxes = fixture.nativeElement.querySelectorAll('input[type=checkbox]') as NodeListOf<HTMLInputElement>;
    expect(checkboxes.length).toBe(2); expect([...checkboxes].every(c => !c.checked)).toBe(true);
    http.expectNone('/api/v1/privacy/consent');
  });
  it('saves explicit consent through a separate CSRF protected API', async () => {
    const fixture = await page(); const component = fixture.componentInstance; component.marketing = true; component.saveConsent();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const request = http.expectOne('/api/v1/privacy/consent');
    expect(request.request.method).toBe('PUT'); expect(request.request.body).toEqual({ marketing: true, noticeVersion: '2026-09-28' });
    request.flush({ ...view, consent: { ...view.consent, marketing: true } }); await fixture.whenStable();
    expect(component.message()).toContain('enregistré'); http.expectNone('/api/v1/orders');
  });
  it('clears a reauthentication password and presents an authorization error', async () => {
    const fixture = await page(); const component = fixture.componentInstance; component.password = 'private-password'; component.email = 'new@example.invalid'; component.requestEmail();
    expect(component.password).toBe(''); http.expectOne('/api/v1/auth/csrf').flush(null);
    const request = http.expectOne('/api/v1/privacy/email/request'); expect(request.request.body.password).toBe('private-password');
    request.flush({}, { status: 403, statusText: 'Forbidden' }); await fixture.whenStable();
    expect(component.error()).toContain('refusée'); expect(component.busy()).toBe(false);
  });
  it('uses only the guest session and never sends an account or owner scope', () => {
    TestBed.inject(PrivacyApi).export('', true, 2).subscribe(); http.expectOne('/api/v1/auth/csrf').flush(null);
    const request = http.expectOne('/api/v1/privacy/guest/export?page=2'); expect(request.request.body).toEqual({});
    request.flush({ schemaVersion: 1, page: 2, orders: [], hasMore: false });
  });
  it('requires deliberate confirmation before removal and clears sensitive inputs on destruction', async () => {
    const fixture = await page(); const component = fixture.componentInstance; component.removePassword = 'private-password'; component.anonymize();
    http.expectNone('/api/v1/auth/csrf'); fixture.destroy(); expect(component.removePassword).toBe('');
  });
});
