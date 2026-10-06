import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BrandShowcase } from './brand-showcase';

describe('BrandShowcase', () => {
  let fixture: ComponentFixture<BrandShowcase>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BrandShowcase],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(BrandShowcase);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('shows only available brand logos and links to the server-side catalogue filter', () => {
    http.expectOne('/api/v1/brands').flush([
      { id: 'total-id', slug: 'sqes-total-ab2882c8', name: 'TOTAL', active: true, version: 0 },
      { id: 'bosch-id', slug: 'sqes-bosch-be05428c', name: 'BOSCH', active: true, version: 0 },
      { id: 'ufesa-id', slug: 'sqes-ufesa-44e099f5', name: 'UFESA', active: false, version: 0 },
    ]);
    fixture.detectChanges();

    const section = fixture.nativeElement.querySelector('.brand-showcase') as HTMLElement;
    expect(section.textContent).toContain('Outillage');
    expect(section.textContent).toContain('Jardinage');
    expect(section.textContent).not.toContain('Électroménager');
    expect(section.querySelectorAll('img').length).toBe(3); // TOTAL appears in two groups.
    expect(section.querySelectorAll('a[aria-label="Voir les produits TOTAL"]').length).toBe(1);
    const totalLink = section.querySelector('a[aria-label="Voir les produits TOTAL"]') as HTMLAnchorElement;
    expect(totalLink.getAttribute('href')).toBe('/catalogue?brandId=total-id');
    expect(totalLink.querySelector('img')?.getAttribute('src')).toBe('/brand-logos/total.webp');
    expect(section.querySelector('a[aria-label*="NOBLEX"]')).toBeNull();
  });

  it('does not claim brands are available when the API fails', () => {
    http.expectOne('/api/v1/brands').flush('error', { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.brand-showcase')).toBeNull();
  });
});
