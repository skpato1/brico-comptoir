import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { App } from './app';
describe('Public shell', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(App);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });
  afterEach(() => http.verify());
  it('restores the guest session and exposes keyboard navigation and both entry points', () => {
    http.expectOne('/api/v1/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.skip-link').getAttribute('href')).toBe('#content');
    expect(fixture.nativeElement.textContent).toContain('Je cherche une solution');
    expect(fixture.nativeElement.textContent).toContain('Je cherche un produit');
    expect(fixture.nativeElement.querySelector('main').tabIndex).toBe(-1);
  });
  it('does not silently switch an unavailable authenticated session to guest checkout', () => {
    http.expectOne('/api/v1/auth/me').flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain(
      'momentanément indisponible',
    );
    expect(fixture.nativeElement.querySelector('router-outlet')).toBeNull();
    fixture.componentInstance.session.start();
    http.expectOne('/api/v1/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('router-outlet')).not.toBeNull();
  });
  it('sends a header search to the catalogue URL', () => {
    http.expectOne('/api/v1/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.componentInstance.query = '  vis  ';
    fixture.componentInstance.search();
    expect(navigate).toHaveBeenCalledWith(['/catalogue'], { queryParams: { q: 'vis' } });
  });
});
