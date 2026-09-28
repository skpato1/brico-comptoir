import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CatalogComponent } from './catalog';

describe('Catalogue', () => {
  let fixture: ComponentFixture<CatalogComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CatalogComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(CatalogComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http
      .expectOne('/api/v1/categories')
      .flush([
        { id: 'cat-1', parentId: null, slug: 'test', name: 'Test', active: true, version: 0 },
      ]);
    http.expectOne('/api/v1/brands').flush([]);
    http
      .expectOne((request) => request.url === '/api/v1/products')
      .flush({ items: [], page: 0, size: 10, totalElements: 0 });
  });
  afterEach(() => http.verify());

  it('sends search filters and pagination to the server and shows only returned products', () => {
    const component = fixture.componentInstance;
    component.filters.q = 'vis';
    component.filters.categoryId = 'cat-1';
    component.filters.minPrice = '1.000';
    component.filters.sort = 'price_asc';
    component.search(true);
    const request = http.expectOne((request) => request.url === '/api/v1/products');
    expect(request.request.params.get('q')).toBe('vis');
    expect(request.request.params.get('categoryId')).toBe('cat-1');
    expect(request.request.params.get('minPrice')).toBe('1.000');
    expect(request.request.params.get('sort')).toBe('price_asc');
    request.flush({
      items: [
        {
          id: 'p-1',
          categoryId: 'cat-1',
          brandId: null,
          name: 'Vis test',
          description: '',
          characteristics: {},
          status: 'PUBLISHED',
          demo: true,
          version: 1,
          variants: [
            {
              id: 'v-1',
              sku: 'DEMO-VIS',
              label: 'Format fictif',
              unit: 'pièce',
              options: {},
              price: { amount: '2.375', currency: 'TND' },
              status: 'PUBLISHED',
              version: 0,
            },
          ],
        },
      ],
      page: 0,
      size: 10,
      totalElements: 1,
    });
    http
      .expectOne(
        (request) =>
          request.url === '/api/v1/media/products' && request.params.get('ids') === 'p-1',
      )
      .flush({ 'p-1': [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Vis test');
    expect(fixture.nativeElement.textContent).toContain('2.375 TND');
    expect(fixture.nativeElement.textContent).toContain('Démonstration fictive');
  });

  it('shows management only for the appropriate role and sends CSRF before mutations', () => {
    const component = fixture.componentInstance;
    fixture.componentRef.setInput('account', {
      id: 'user',
      email: 'client@example.tn',
      roles: ['CUSTOMER'],
      active: true,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Gérer le catalogue');
    fixture.componentRef.setInput('account', {
      id: 'manager',
      email: 'manager@example.tn',
      roles: ['CATALOG_MANAGER'],
      active: true,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Gérer le catalogue');
    component.toggleAdmin();
    http.expectOne('/api/v1/admin/catalog/categories').flush([]);
    http.expectOne('/api/v1/admin/catalog/brands').flush([]);
    http
      .expectOne((request) => request.url === '/api/v1/admin/catalog/products')
      .flush({ items: [], page: 0, size: 100, totalElements: 0 });
    component.brandForm = { slug: 'demo', name: 'Démo', active: true };
    component.saveBrand();
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    const write = http.expectOne('/api/v1/admin/catalog/brands');
    expect(write.request.method).toBe('POST');
    expect(write.request.body).toEqual({ slug: 'demo', name: 'Démo', active: true });
    write.flush({ id: 'b-1', slug: 'demo', name: 'Démo', active: true, version: 0 });
    http.expectOne('/api/v1/admin/catalog/categories').flush([]);
    http.expectOne('/api/v1/admin/catalog/brands').flush([]);
    http
      .expectOne((request) => request.url === '/api/v1/admin/catalog/products')
      .flush({ items: [], page: 0, size: 100, totalElements: 0 });
    http.expectOne('/api/v1/categories').flush([]);
    http.expectOne('/api/v1/brands').flush([]);
    http
      .expectOne((request) => request.url === '/api/v1/products')
      .flush({ items: [], page: 0, size: 10, totalElements: 0 });
  });

  it('loads only current-page image references and renders responsive lazy images', () => {
    const component = fixture.componentInstance;
    component.search();
    http
      .expectOne((request) => request.url === '/api/v1/products')
      .flush({
        items: [
          {
            id: 'p-2',
            categoryId: 'cat-1',
            brandId: null,
            name: 'DÉMO produit',
            description: '',
            characteristics: {},
            status: 'PUBLISHED',
            demo: true,
            version: 0,
            variants: [],
          },
        ],
        page: 0,
        size: 10,
        totalElements: 1,
      });
    http
      .expectOne(
        (request) =>
          request.url === '/api/v1/media/products' && request.params.get('ids') === 'p-2',
      )
      .flush({
        'p-2': [
          {
            id: 'i-1',
            productId: 'p-2',
            cardUrl: '/api/v1/media/i-1/card',
            detailUrl: '/api/v1/media/i-1/detail',
            width: 640,
            height: 480,
            sortOrder: 0,
            primary: true,
          },
        ],
      });
    fixture.detectChanges();
    const image = fixture.nativeElement.querySelector('.product-photo') as HTMLImageElement;
    expect(image.getAttribute('loading')).toBe('lazy');
    expect(image.getAttribute('srcset')).toContain('360w');
    expect(image.getAttribute('srcset')).toContain('640w');
  });
});
