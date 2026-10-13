import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { App } from './app';
import { Category } from './core/catalog-api';
const category = (id: string, name: string, parentId: string | null = null): Category =>
  ({ id, name, parentId, slug: id, active: true, version: 0 });
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
    expect(fixture.nativeElement.querySelector('.main-nav').textContent).toContain('Trouver une solution');
    expect(fixture.nativeElement.querySelector('.main-nav').textContent).toContain('Tous les produits');
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
  it('offers matching published products from the public search endpoint', async () => {
    http.expectOne('/api/v1/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    fixture.componentInstance.query = 'perceuse';
    fixture.componentInstance.onSearchInput('perceuse');
    await new Promise(resolve => setTimeout(resolve, 330));
    const request = http.expectOne((r) => r.url === '/api/v1/products');
    expect(request.request.params.get('q')).toBe('perceuse');
    expect(request.request.params.get('size')).toBe('5');
    request.flush({ items: [{ id: 'drill', name: 'Perceuse test' }], page: 0, size: 5, totalElements: 1 });
    fixture.detectChanges();
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('.header-suggestions a');
    expect(link.textContent).toContain('Perceuse test');
    expect(link.getAttribute('href')).toBe('/produits/drill');
  });
  it('opens the mobile menu, loads categories once and groups real subcategories by rayon', () => {
    http.expectOne('/api/v1/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    const toggle: HTMLButtonElement = fixture.nativeElement.querySelector('.menu-toggle');
    toggle.click();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.nativeElement.querySelector('dialog').hasAttribute('open')).toBe(true);
    http.expectOne('/api/v1/categories').flush([
      category('child', 'Perceuses', 'parent'), category('parent', 'Outillage'),
    ]);
    fixture.detectChanges();
    const department: HTMLButtonElement = fixture.nativeElement.querySelector('.department-grid button');
    expect(department.textContent).toContain('Outillage');
    department.click();
    fixture.detectChanges();
    const group: HTMLDetailsElement = fixture.nativeElement.querySelector('.department-group');
    expect(group.querySelector('summary')?.textContent).toContain('catégories');
    group.querySelector('summary')?.click();
    expect(group.open).toBe(true);
    expect(group.textContent).toContain('Perceuses');
    expect(fixture.nativeElement.querySelector('a[href="/catalogue?categoryId=child"]')).not.toBeNull();
    fixture.componentInstance.closeMenu();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    toggle.click();
    http.expectNone('/api/v1/categories');
  });
  it('offers visual departments and accent-insensitive category search', () => {
    http.expectOne('/api/v1/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    fixture.componentInstance.openMenu();
    http.expectOne('/api/v1/categories').flush([
      category('paint', 'Peinture'), category('electric', 'Électricité'),
    ]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.department-grid button').length).toBe(2);
    fixture.componentInstance.menuQuery.set('electricite');
    fixture.detectChanges();
    const result = fixture.nativeElement.querySelector('.menu-search-results a');
    expect(result.textContent).toContain('Électricité');
    expect(result.getAttribute('href')).toBe('/catalogue?categoryId=electric');
  });
  it('explains a category API failure and retries on request', () => {
    http.expectOne('/api/v1/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    fixture.componentInstance.openMenu();
    http.expectOne('/api/v1/categories').flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.menu-categories [role=alert]').textContent)
      .toContain('pas disponibles');
    fixture.nativeElement.querySelector('.menu-categories button').click();
    http.expectOne('/api/v1/categories').flush([category('tools', 'Outillage')]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.department-grid')).not.toBeNull();
  });
});
